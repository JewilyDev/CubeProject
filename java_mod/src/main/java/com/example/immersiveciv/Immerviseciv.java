package com.example.immersiveciv;

import com.example.immersiveciv.command.CivResultCommand;
import com.example.immersiveciv.command.ScanCommand;
import com.example.immersiveciv.network.MiddlewareClient;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Immerviseciv implements ModInitializer {
    public static final String MOD_ID = "immervise-civ";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[ImmersiveCiv] Инициализация мода…");

        // Регистрируем команды
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ScanCommand.register(dispatcher, registryAccess);
            CivResultCommand.register(dispatcher, registryAccess);
        });

        // Запускаем WebSocket-клиент при старте сервера
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("[ImmersiveCiv] Сервер запущен, подключаемся к Middleware…");
            MiddlewareClient.setServer(server);
            MiddlewareClient.getInstance();
        });

        // Закрываем WebSocket при остановке сервера
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("[ImmersiveCiv] Сервер останавливается, закрываем WebSocket…");
            MiddlewareClient.setServer(null);
            MiddlewareClient.getInstance().shutdown();
        });

        LOGGER.info("[ImmersiveCiv] Мод инициализирован.");
    }
}
