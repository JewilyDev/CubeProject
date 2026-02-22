package com.example.immersiveciv;

import com.example.immersiveciv.command.CivResultCommand;
import com.example.immersiveciv.command.ScanCommand;
import com.example.immersiveciv.config.GameConfig;
import com.example.immersiveciv.item.CityMapItem;
import com.example.immersiveciv.network.MiddlewareClient;
import com.example.immersiveciv.network.ModMessages;
import com.example.immersiveciv.registry.BuildingRecord;
import com.example.immersiveciv.registry.BuildingRegistry;
import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class Immerviseciv implements ModInitializer {

    public static final String MOD_ID = "immervise-civ";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Прокси для открытия экрана городской карты на клиенте.
     * Устанавливается в ImmervisecivClient.onInitializeClient().
     */
    public static Runnable openCityMapScreen = null;

    @Override
    public void onInitialize() {
        LOGGER.info("[ImmersiveCiv] Инициализация мода…");

        // ── Регистрация предметов ─────────────────────────────────────────────
        Registry.register(BuiltInRegistries.ITEM,
                new ResourceLocation(MOD_ID, "city_map"),
                CityMapItem.INSTANCE);

        // ── Регистрация команд ────────────────────────────────────────────────
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ScanCommand.register(dispatcher, registryAccess);
        });

        // ── Загрузка конфигурации (buildings.json, technologies.json) ─────────
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            GameConfig.load();
        });

        // ── WebSocket ─────────────────────────────────────────────────────────
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("[ImmersiveCiv] Сервер запущен, подключаемся к Middleware…");
            MiddlewareClient.setServer(server);
            MiddlewareClient.getInstance();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("[ImmersiveCiv] Сервер останавливается, закрываем WebSocket…");
            MiddlewareClient.setServer(null);
            MiddlewareClient.getInstance().shutdown();
        });

        // ── C2S: готовые рендеры от клиента → Middleware ─────────────────────
        ServerPlayNetworking.registerGlobalReceiver(ModMessages.RENDER_RESULT,
                (server, player, handler, buf, responseSender) -> {
                    String jsonPayload = buf.readUtf(1024 * 1024 * 10);
                    server.execute(() ->
                            MiddlewareClient.getInstance().sendJson(
                                    JsonParser.parseString(jsonPayload).getAsJsonObject()));
                });

        // ── C2S: снос здания из CityMapScreen ────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(ModMessages.DEMOLISH_BUILDING,
                (server, player, handler, buf, responseSender) -> {
                    String buildingId = buf.readUtf(64);
                    server.execute(() -> {
                        BuildingRegistry registry = BuildingRegistry.get(server);
                        BuildingRecord existing = registry.get(buildingId);
                        if (existing == null) return;

                        // TODO: проверить права (только владелец или оп)
                        registry.remove(buildingId);
                        LOGGER.info("[ImmersiveCiv] Снесено здание {} ({})", existing.type, buildingId);

                        // Broadcast удаления
                        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                            FriendlyByteBuf removeBuf = PacketByteBufs.create();
                            removeBuf.writeUtf(buildingId);
                            ServerPlayNetworking.send(online, ModMessages.BUILDING_REMOVED, removeBuf);
                        }
                    });
                });

        // ── S2C: синк реестра при логине игрока ──────────────────────────────
        ServerPlayConnectionEvents.JOIN.register((networkHandler, sender, server) -> {
            ServerPlayer player = networkHandler.getPlayer();
            BuildingRegistry registry = BuildingRegistry.get(server);
            Collection<BuildingRecord> all = registry.getAll();

            if (all.isEmpty()) return;

            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(all.size());
            for (BuildingRecord r : all) {
                r.encode(buf);
            }
            ServerPlayNetworking.send(player, ModMessages.SYNC_BUILDINGS, buf);
            LOGGER.debug("[ImmersiveCiv] Отправлен SYNC_BUILDINGS ({} зданий) → {}", all.size(), player.getName().getString());
        });

        LOGGER.info("[ImmersiveCiv] Мод инициализирован.");
    }
}
