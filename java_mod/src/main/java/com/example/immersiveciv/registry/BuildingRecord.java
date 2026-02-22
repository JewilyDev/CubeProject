package com.example.immersiveciv.registry;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * Запись о здании в реестре.
 * Иммутабельный data-класс; сериализуется в NBT (сервер) и FriendlyByteBuf (сеть).
 */
public final class BuildingRecord {

    public final String id;            // UUID.randomUUID().toString()
    public final String type;          // "forge", "farm", …
    public final int pos1x, pos1y, pos1z;
    public final int pos2x, pos2y, pos2z;
    public final String owner;         // имя игрока
    public final long timestamp;       // System.currentTimeMillis()
    public final int vlmScore;         // 0–100, или -1 если оценки не было
    public final String vlmSummary;    // краткий отзыв от VLM
    public final boolean overallPassed;

    // ── Конструктор ──────────────────────────────────────────────────────────

    public BuildingRecord(String id, String type,
                          int pos1x, int pos1y, int pos1z,
                          int pos2x, int pos2y, int pos2z,
                          String owner, long timestamp,
                          int vlmScore, String vlmSummary,
                          boolean overallPassed) {
        this.id           = id;
        this.type         = type;
        this.pos1x        = pos1x; this.pos1y = pos1y; this.pos1z = pos1z;
        this.pos2x        = pos2x; this.pos2y = pos2y; this.pos2z = pos2z;
        this.owner        = owner;
        this.timestamp    = timestamp;
        this.vlmScore     = vlmScore;
        this.vlmSummary   = vlmSummary;
        this.overallPassed = overallPassed;
    }

    // ── Фабрика из validate_result (WebSocket → Java) ────────────────────────

    /**
     * Создаёт BuildingRecord из JSON-пакета validate_result.
     *
     * Ожидаемые поля:
     *   building_type, player, overall_passed, vlm_score, vlm_summary
     *   pos1: {x, y, z}  pos2: {x, y, z}  (добавляются в ScanCommand.buildPayload)
     */
    public static BuildingRecord fromValidateResult(JsonObject json) {
        String type         = getString(json, "building_type", "unknown");
        String owner        = getString(json, "player", "unknown");
        boolean passed      = json.has("overall_passed") && json.get("overall_passed").getAsBoolean();
        int score           = json.has("vlm_score") && !json.get("vlm_score").isJsonNull()
                              ? json.get("vlm_score").getAsInt() : -1;
        String summary      = getString(json, "vlm_summary", "");

        int p1x = 0, p1y = 0, p1z = 0, p2x = 0, p2y = 0, p2z = 0;
        if (json.has("pos1")) {
            JsonObject p = json.getAsJsonObject("pos1");
            p1x = p.get("x").getAsInt(); p1y = p.get("y").getAsInt(); p1z = p.get("z").getAsInt();
        }
        if (json.has("pos2")) {
            JsonObject p = json.getAsJsonObject("pos2");
            p2x = p.get("x").getAsInt(); p2y = p.get("y").getAsInt(); p2z = p.get("z").getAsInt();
        }

        return new BuildingRecord(
                UUID.randomUUID().toString(),
                type, p1x, p1y, p1z, p2x, p2y, p2z,
                owner, System.currentTimeMillis(),
                score, summary, passed
        );
    }

    // ── Вспомогательный BlockPos ──────────────────────────────────────────────

    public BlockPos getPos1() { return new BlockPos(pos1x, pos1y, pos1z); }
    public BlockPos getPos2() { return new BlockPos(pos2x, pos2y, pos2z); }

    // ── NBT (сохранение на сервере) ───────────────────────────────────────────

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id",       id);
        tag.putString("type",     type);
        tag.putInt("p1x", pos1x); tag.putInt("p1y", pos1y); tag.putInt("p1z", pos1z);
        tag.putInt("p2x", pos2x); tag.putInt("p2y", pos2y); tag.putInt("p2z", pos2z);
        tag.putString("owner",    owner);
        tag.putLong("timestamp",  timestamp);
        tag.putInt("vlmScore",    vlmScore);
        tag.putString("vlmSummary", vlmSummary);
        tag.putBoolean("passed",  overallPassed);
        return tag;
    }

    public static BuildingRecord fromNbt(CompoundTag tag) {
        return new BuildingRecord(
                tag.getString("id"),
                tag.getString("type"),
                tag.getInt("p1x"), tag.getInt("p1y"), tag.getInt("p1z"),
                tag.getInt("p2x"), tag.getInt("p2y"), tag.getInt("p2z"),
                tag.getString("owner"),
                tag.getLong("timestamp"),
                tag.getInt("vlmScore"),
                tag.getString("vlmSummary"),
                tag.getBoolean("passed")
        );
    }

    // ── Сеть (S2C синхронизация) ──────────────────────────────────────────────

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(type);
        buf.writeInt(pos1x); buf.writeInt(pos1y); buf.writeInt(pos1z);
        buf.writeInt(pos2x); buf.writeInt(pos2y); buf.writeInt(pos2z);
        buf.writeUtf(owner);
        buf.writeLong(timestamp);
        buf.writeInt(vlmScore);
        buf.writeUtf(vlmSummary);
        buf.writeBoolean(overallPassed);
    }

    public static BuildingRecord decode(FriendlyByteBuf buf) {
        return new BuildingRecord(
                buf.readUtf(), buf.readUtf(),
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readUtf(), buf.readLong(),
                buf.readInt(), buf.readUtf(), buf.readBoolean()
        );
    }

    // ── Вспомогательный метод ─────────────────────────────────────────────────

    private static String getString(JsonObject json, String key, String fallback) {
        return (json.has(key) && !json.get(key).isJsonNull()) ? json.get(key).getAsString() : fallback;
    }
}
