package com.example.immersiveciv.command;

import com.example.immersiveciv.Immerviseciv;
import com.example.immersiveciv.network.ModMessages;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
                // /scan
                .executes(ctx -> execute(ctx, 8, "unnamed"))
                // /scan <radius>
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                    .executes(ctx -> execute(ctx,
                            IntegerArgumentType.getInteger(ctx, "radius"), "unnamed"))
                    // /scan <radius> <label>
                    .then(Commands.argument("label", StringArgumentType.word())
                        .executes(ctx -> execute(ctx,
                                IntegerArgumentType.getInteger(ctx, "radius"),
                                StringArgumentType.getString(ctx, "label")))))
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx, int radius, String label) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        BlockPos center = BlockPos.containing(src.getPosition());

        src.sendSuccess(() -> Component.literal(
                "[ImmersiveCiv] Сканирование региона и подготовка к рендеру…"), false);

        Thread scanThread = new Thread(() -> {
            try {
                JsonObject payload = buildPayload(level, center, radius, label);

                // Достаем имя игрока из label (передано из KubeJS)
                String playerName = "unknown";
                try {
                    JsonObject meta = JsonParser.parseString(label).getAsJsonObject();
                    if (meta.has("player")) playerName = meta.get("player").getAsString();
                } catch (Exception ignored) {}

                // Ищем игрока и отправляем ему пакет S2C для рендера
                ServerPlayer targetPlayer = level.getServer().getPlayerList().getPlayerByName(playerName);
                if (targetPlayer == null && src.getPlayer() != null) {
                    targetPlayer = src.getPlayer(); // Fallback на того, кто ввел команду
                }

                if (targetPlayer != null) {
                    FriendlyByteBuf buf = PacketByteBufs.create();
                    // Максимальный размер пакета - используем writeUtf
                    buf.writeUtf(payload.toString(), 1024 * 1024 * 5);
                    ServerPlayNetworking.send(targetPlayer, ModMessages.REQUEST_RENDER, buf);
                } else {
                    Immerviseciv.LOGGER.error("[ImmersiveCiv] Игрок для рендера не найден!");
                }

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
    private static JsonObject buildPayload(ServerLevel level, BlockPos center, int radius, String label) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "scan_result");
        root.addProperty("label", label);

        JsonObject centerObj = new JsonObject();
        centerObj.addProperty("x", center.getX());
        centerObj.addProperty("y", center.getY());
        centerObj.addProperty("z", center.getZ());
        root.add("center", centerObj);
        root.addProperty("radius", radius);

        JsonArray blocks = new JsonArray();

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutable.set(
                        center.getX() + dx,
                        center.getY() + dy,
                        center.getZ() + dz
                    );
                    BlockState state = level.getBlockState(mutable);

                    // Пропускаем воздух — уменьшает размер пакета
                    if (state.isAir()) continue;

                    JsonObject blockObj = new JsonObject();
                    blockObj.addProperty("x", mutable.getX());
                    blockObj.addProperty("y", mutable.getY());
                    blockObj.addProperty("z", mutable.getZ());
                    blockObj.addProperty("id", state.getBlock().builtInRegistryHolder()
                            .key().location().toString());

                    // MapColor — точный цвет блока из Minecraft (тот же, что на картах)
                    // col — int 0xRRGGBB; MapColor.NONE.col == 0 (пропускаем)
                    MapColor mapColor = state.getMapColor(level, mutable);
                    if (mapColor != MapColor.NONE && mapColor.col != 0) {
                        blockObj.addProperty("map_color", mapColor.col);
                    }

                    // Сериализуем BlockState properties (orientation, waterlogged и т.д.)
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
            }
        }

        root.add("blocks", blocks);
        return root;
    }
}
