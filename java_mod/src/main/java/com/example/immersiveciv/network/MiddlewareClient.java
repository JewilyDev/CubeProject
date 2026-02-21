package com.example.immersiveciv.network;

import com.example.immersiveciv.Immerviseciv;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Singleton WebSocket client — поддерживает постоянное соединение с Middleware.
 * Автоматически переподключается при обрыве.
 *
 * При получении пакетов от Middleware диспетчеризует их на серверный поток:
 *   - validate_progress → сообщение игроку
 *   - validate_result   → вызов /civresult <json> (KubeJS перехватывает и показывает)
 *   - scan_ack          → логируем
 */
public class MiddlewareClient extends WebSocketClient {

    private static final String DEFAULT_URI = "ws://localhost:8765/ws";
    private static MiddlewareClient instance;

    private final ScheduledExecutorService reconnectScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "immersiveciv-ws-reconnect");
                t.setDaemon(true);
                return t;
            });

    /** Ссылка на активный сервер (устанавливается при старте). */
    private static final AtomicReference<MinecraftServer> serverRef = new AtomicReference<>();

    private MiddlewareClient(URI uri) {
        super(uri);
    }

    public static void setServer(MinecraftServer server) {
        serverRef.set(server);
    }

    /** Инициализирует и подключает клиент. Вызывается из onInitialize(). */
    public static synchronized MiddlewareClient getInstance() {
        if (instance == null) {
            try {
                instance = new MiddlewareClient(new URI(DEFAULT_URI));
                instance.connectAsync();
            } catch (Exception e) {
                Immerviseciv.LOGGER.error("[ImmersiveCiv] Неверный URI WebSocket: {}", e.getMessage());
            }
        }
        return instance;
    }

    // ── WebSocketClient callbacks ─────────────────────────────────────────────

    @Override
    public void onOpen(ServerHandshake handshake) {
        Immerviseciv.LOGGER.info("[ImmersiveCiv] WebSocket подключён к Middleware (HTTP {})",
                handshake.getHttpStatus());
    }

    @Override
    public void onMessage(String message) {
        Immerviseciv.LOGGER.debug("[ImmersiveCiv] WS ← {}", message);
        dispatch(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        Immerviseciv.LOGGER.warn("[ImmersiveCiv] WS соединение закрыто (code={}, reason={}, remote={}). " +
                "Переподключение через 5 с…", code, reason, remote);
        scheduleReconnect();
    }

    @Override
    public void onError(Exception ex) {
        Immerviseciv.LOGGER.error("[ImmersiveCiv] WS ошибка: {}", ex.getMessage());
    }

    // ── Диспетчер входящих пакетов ────────────────────────────────────────────

    private void dispatch(String raw) {
        MinecraftServer server = serverRef.get();
        if (server == null) return;

        JsonObject json;
        try {
            json = JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception e) {
            Immerviseciv.LOGGER.warn("[ImmersiveCiv] Не-JSON от Middleware: {}", raw.length() > 200 ? raw.substring(0, 200) : raw);
            return;
        }

        String type = json.has("type") ? json.get("type").getAsString() : "unknown";

        switch (type) {
            case "validate_progress" -> handleProgress(server, json);
            case "validate_result"   -> handleResult(server, json, raw);
            case "scan_ack"          -> Immerviseciv.LOGGER.info("[ImmersiveCiv] scan_ack: {}", json);
            default                  -> Immerviseciv.LOGGER.debug("[ImmersiveCiv] Неизвестный тип: {}", type);
        }
    }

    /** Промежуточное сообщение — "VLM думает…" */
    private void handleProgress(MinecraftServer server, JsonObject json) {
        String playerName = jsonStr(json, "player");
        String msg        = jsonStr(json, "message");
        if (playerName.isEmpty() || msg.isEmpty()) return;

        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
            if (player != null) {
                player.sendSystemMessage(Component.literal("§7[ImmersiveCiv] " + msg));
            }
        });
    }

    /**
     * Финальный результат — передаём в KubeJS через /civresult.
     * Весь payload JSON экранируем как один greedyString-аргумент.
     */
    private void handleResult(MinecraftServer server, JsonObject json, String raw) {
        // Экранируем кавычки внутри строки для Brigadier
        String escaped = raw.replace("\\", "\\\\").replace("\"", "\\\"");
        String cmd = "civresult \"" + escaped + "\"";

        server.execute(() -> server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withMaximumPermission(2),
                cmd));
    }

    // ── Публичный API ─────────────────────────────────────────────────────────

    /**
     * Отправляет JSON-объект на Middleware.
     */
    public void sendJson(JsonObject payload) {
        if (isOpen()) {
            send(payload.toString());
        } else {
            Immerviseciv.LOGGER.warn("[ImmersiveCiv] WS не подключён, сообщение отброшено: {}", payload);
        }
    }

    /** Завершает работу клиента (вызывается при остановке сервера). */
    public void shutdown() {
        reconnectScheduler.shutdownNow();
        try {
            closeBlocking();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Вспомогательные ──────────────────────────────────────────────────────

    private static String jsonStr(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
    }

    private void connectAsync() {
        Thread t = new Thread(() -> {
            try {
                connectBlocking(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "immersiveciv-ws-connect");
        t.setDaemon(true);
        t.start();
    }

    private void scheduleReconnect() {
        reconnectScheduler.schedule(() -> {
            try {
                instance = new MiddlewareClient(new URI(DEFAULT_URI));
                instance.connectAsync();
            } catch (Exception e) {
                Immerviseciv.LOGGER.error("[ImmersiveCiv] Ошибка переподключения: {}", e.getMessage());
            }
        }, 5, TimeUnit.SECONDS);
    }
}
