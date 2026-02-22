package com.example.immersiveciv.command;

import com.example.immersiveciv.Immerviseciv;
import com.example.immersiveciv.network.ModMessages;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.MapColor;

import java.util.Map;

/**
 * /scan [radius] [label]
 * Сканирует кубический регион вокруг игрока, сериализует в JSON и отправляет в Middleware.
 */
public class ScanCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext buildContext) {
        dispatcher.register(
                Commands.literal("scan")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                        .then(Commands.argument("label", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    // Используем getBlockPos без проверки загрузки чанка
                                                    // (getLoadedBlockPos бросает исключение при вызове от имени сервера)
                                                    BlockPos p1 = ctx.getArgument("pos1", Coordinates.class)
                                                            .getBlockPos(ctx.getSource());
                                                    BlockPos p2 = ctx.getArgument("pos2", Coordinates.class)
                                                            .getBlockPos(ctx.getSource());
                                                    return execute(ctx, p1, p2,
                                                            StringArgumentType.getString(ctx, "label"));
                                                }))))
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx, BlockPos pos1, BlockPos pos2, String label) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();

        src.sendSuccess(() -> Component.literal(
                "[ImmersiveCiv] Сканирование выделенного региона…"), false);

        Thread scanThread = new Thread(() -> {
            try {
                String playerName = "unknown";
                try {
                    JsonObject meta = JsonParser.parseString(label).getAsJsonObject();
                    if (meta.has("player")) playerName = meta.get("player").getAsString();
                } catch (Exception ignored) {}

                ServerPlayer targetPlayer = level.getServer().getPlayerList().getPlayerByName(playerName);
                if (targetPlayer == null && src.getPlayer() != null) {
                    targetPlayer = src.getPlayer();
                }

                if (targetPlayer == null) {
                    Immerviseciv.LOGGER.error("[ImmersiveCiv] Игрок для рендера не найден! playerName={}", playerName);
                    return;
                }

                // Передаем pos1 и pos2 в buildPayload
                JsonObject payload = buildPayload(level, pos1, pos2, label);

                FriendlyByteBuf buf = PacketByteBufs.create();
                buf.writeUtf(payload.toString(), 1024 * 1024 * 5);
                ServerPlayNetworking.send(targetPlayer, ModMessages.REQUEST_RENDER, buf);

            } catch (Exception e) {
                Immerviseciv.LOGGER.error("[ImmersiveCiv] Ошибка сканирования: {}", e.getMessage());
            }
        }, "immersiveciv-scan");
        scanThread.setDaemon(true);
        scanThread.start();

        return 1;
    }

    /**
     * Строит JSON-пакет:
     * {
     *   "type": "scan_result",
     *   "label": "...",
     *   "center": {"x":0,"y":64,"z":0},
     *   "radius": 8,
     *   "blocks": [
     *     {"x":0,"y":64,"z":0,"id":"minecraft:stone","props":{"variant":"granite"}},
     *     ...
     *   ]
     * }
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    private static JsonObject buildPayload(ServerLevel level, BlockPos pos1, BlockPos pos2, String label) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "scan_result");
        root.addProperty("label", label);

        // Вычисляем примерный центр
        JsonObject centerObj = new JsonObject();
        centerObj.addProperty("x", (pos1.getX() + pos2.getX()) / 2);
        centerObj.addProperty("y", (pos1.getY() + pos2.getY()) / 2);
        centerObj.addProperty("z", (pos1.getZ() + pos2.getZ()) / 2);
        root.add("center", centerObj);

        // Добавляем pos1/pos2 — нужны для BuildingRegistry после валидации
        JsonObject pos1Obj = new JsonObject();
        pos1Obj.addProperty("x", pos1.getX());
        pos1Obj.addProperty("y", pos1.getY());
        pos1Obj.addProperty("z", pos1.getZ());
        root.add("pos1", pos1Obj);

        JsonObject pos2Obj = new JsonObject();
        pos2Obj.addProperty("x", pos2.getX());
        pos2Obj.addProperty("y", pos2.getY());
        pos2Obj.addProperty("z", pos2.getZ());
        root.add("pos2", pos2Obj);

        // --- ДОБАВИТЬ ЭТОТ БЛОК ---
        // Вычисляем самую длинную сторону выделения
        int sizeX = Math.abs(pos1.getX() - pos2.getX());
        int sizeY = Math.abs(pos1.getY() - pos2.getY());
        int sizeZ = Math.abs(pos1.getZ() - pos2.getZ());
        int maxDim = Math.max(sizeX, Math.max(sizeY, sizeZ));

        // Радиус = половина самой длинной стороны + 2 блока отступа (padding),
        // чтобы постройка не прилипала к краям на рендере
        int radius = (maxDim / 2) + 2;
        root.addProperty("radius", radius);
        // --------------------------

        JsonArray blocks = new JsonArray();

        // betweenClosed сам разбирается, где мин. и макс. координаты
        for (BlockPos currentPos : BlockPos.betweenClosed(pos1, pos2)) {
            BlockState state = level.getBlockState(currentPos);

            if (state.isAir()) continue;

            JsonObject blockObj = new JsonObject();
            blockObj.addProperty("x", currentPos.getX());
            blockObj.addProperty("y", currentPos.getY());
            blockObj.addProperty("z", currentPos.getZ());
            blockObj.addProperty("id", state.getBlock().builtInRegistryHolder()
                    .key().location().toString());

            MapColor mapColor = state.getMapColor(level, currentPos);
            if (mapColor != MapColor.NONE && mapColor.col != 0) {
                blockObj.addProperty("map_color", mapColor.col);
            }

            JsonObject props = new JsonObject();
            for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
                Property prop = entry.getKey();
                props.addProperty(prop.getName(), prop.getName(entry.getValue()));
            }
            if (!props.isEmpty()) {
                blockObj.add("props", props);
            }

            blocks.add(blockObj);
        }

        root.add("blocks", blocks);
        return root;
    }
}
