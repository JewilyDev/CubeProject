package com.example.immersiveciv.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Серверный реестр вручную исследованных технологий.
 * Хранится в world/data/immersiveciv_technologies.dat.
 *
 * Технология считается "открытой" если она есть ЗДЕСЬ (ручной ресёрч)
 * ИЛИ если в BuildingRegistry есть здание, которое её открывает.
 * {@link BuildingRegistry#getUnlockedTechIds} учитывает оба источника.
 */
public class TechRegistry extends SavedData {

    private static final String DATA_NAME = "immersiveciv_technologies";

    private final Set<String> researched = new HashSet<>();

    // ── Получение синглтона ───────────────────────────────────────────────────

    public static TechRegistry get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(TechRegistry::load, TechRegistry::new, DATA_NAME);
    }

    // ── Операции ──────────────────────────────────────────────────────────────

    /** Отмечает технологию как исследованную (ручной ресёрч). */
    public void markResearched(String techId) {
        if (researched.add(techId)) setDirty();
    }

    public boolean isResearched(String techId) {
        return researched.contains(techId);
    }

    /** Возвращает все вручную исследованные технологии (неизменяемый вид). */
    public Set<String> getAll() {
        return Collections.unmodifiableSet(researched);
    }

    // ── SavedData ─────────────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (String id : researched) {
            list.add(StringTag.valueOf(id));
        }
        tag.put("researched", list);
        return tag;
    }

    public static TechRegistry load(CompoundTag tag) {
        TechRegistry reg = new TechRegistry();
        ListTag list = tag.getList("researched", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            reg.researched.add(list.getString(i));
        }
        return reg;
    }
}
