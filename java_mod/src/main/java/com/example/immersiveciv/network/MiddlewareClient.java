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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Singleton WebSocket client — поддерживает постоянное соединение с Middleware.
 * Автоматически переподключается при обрыве.
 *
 * Устойчив к паузе одиночной игры: сообщения, отправленные пока соединение
 * отсутствовало, кладутся в статическую очередь и досылаются при reconnect.
 */
public class MiddlewareClient extends WebSocketClient {

    private static final String DEFAULT_URI = "ws://localhost:8765/ws";

    /**
     * Таймаут потери соединения (сек). Java-WebSocket пингует Middleware каждые N/2 секунд.
     * 120 с — достаточно для длинной паузы в одиночной игре.
     */
    private static final int CONNECTION_LOST_TIMEOUT_SEC = 120;

    private static MiddlewareClient instance;

    /**
     * Статическая очередь отложенных сообщений.
     * Живёт дольше отдельного экземпляра — не теряется при reconnect.
     */
    private static final ConcurrentLinkedQueue<String> pendingMessages =
            new ConcurrentLinkedQueue<>();

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
        // Увеличенный таймаут: сервер не ответит на ping во время паузы,
        // но 120 с достаточно, чтобы не дропать соединение при обычной паузе.
        setConnectionLostTimeout(CONNECTION_LOST_TIMEOUT_SEC);
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
        flushPending();
    }

    @Override
    public void onMessage(String message) {
        Immerviseciv.LOGGER.debug("[ImmersiveCiv] WS ← {}", message);
        dispatch(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        Immerviseciv.LOGGER.warn("[ImmersiveCiv] WS закрыт (code={}, reason='{}', remote={}). " +
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
            Immerviseciv.LOGGER.warn("[ImmersiveCiv] Не-JSON от Middleware: {}",
                    raw.length() > 200 ? raw.substring(0, 200) : raw);
            return;
        }

        String type = json.has("type") ? json.get("type").getAsString() : "unknown";

        switch (type) {
            case "validate_progress" -> handleProgress(server, json);
            case "validate_result"   -> handleResult(server, json, raw);
            case "scan_ack"          -> Immerviseciv.LOGGER.info("[ImmersiveCiv] scan_ack получен");
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
        String cmd = "civresult " + raw;

        server.execute(() -> {
            int success = server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withMaximumPermission(2),
                    cmd
            );

            if (success == 0) {
                Immerviseciv.LOGGER.error("[ImmersiveCiv] Ошибка выполнения /civresult! Проверьте логи KubeJS.");
            }
        });
    }

    // ── Публичный API ─────────────────────────────────────────────────────────

    /**
     * Отправляет JSON-объект на Middleware.
     *
     * Если соединение сейчас отсутствует (пауза, кратковременный разрыв),
     * сообщение кладётся в очередь и будет отправлено при следующем onOpen.
     */
    public void sendJson(JsonObject payload) {
        String msg = payload.toString();
        if (isOpen()) {
            send(msg);
        } else {
            Immerviseciv.LOGGER.warn("[ImmersiveCiv] WS не подключён — сообщение в очереди " +
                    "(всего в очереди: {})", pendingMessages.size() + 1);
            pendingMessages.offer(msg);
        }
    }

    /** Завершает работу клиента (вызывается при остановке сервера). */
    public void shutdown() {
        reconnectScheduler.shutdownNow();
        pendingMessages.clear();
        try {
            closeBlocking();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Вспомогательные ──────────────────────────────────────────────────────

    /** Отправляет все накопленные сообщения из очереди. Вызывается из onOpen. */
    private void flushPending() {
        if (pendingMessages.isEmpty()) return;

        int count = 0;
        String msg;
        while ((msg = pendingMessages.poll()) != null) {
            send(msg);
            count++;
        }
        Immerviseciv.LOGGER.info("[ImmersiveCiv] Очередь досылки: {} сообщений отправлено.", count);
    }

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
                synchronized (MiddlewareClient.class) {
                    instance = new MiddlewareClient(new URI(DEFAULT_URI));
                    instance.connectAsync();
                }
            } catch (Exception e) {
                Immerviseciv.LOGGER.error("[ImmersiveCiv] Ошибка переподключения: {}", e.getMessage());
            }
        }, 5, TimeUnit.SECONDS);
    }
}
