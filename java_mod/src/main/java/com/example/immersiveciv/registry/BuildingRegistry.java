package com.example.immersiveciv.registry;

import com.example.immersiveciv.config.BuildingDef;
import com.example.immersiveciv.config.GameConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Серверный реестр зарегистрированных зданий.
 * Хранится в world/data/immersiveciv_buildings.dat через SavedData.
 * Переживает перезагрузки сервера.
 */
public class BuildingRegistry extends SavedData {

    private static final String DATA_NAME = "immersiveciv_buildings";

    /** LinkedHashMap сохраняет порядок добавления (новые здания — в конец). */
    private final Map<String, BuildingRecord> buildings = new LinkedHashMap<>();

    // ── Получение синглтона для текущего мира ────────────────────────────────

    public static BuildingRegistry get(MinecraftServer server) {
        // В MC 1.20.1 API: computeIfAbsent(deserializer, factory, key)
        // SavedData.Factory появился только в 1.20.2+
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(BuildingRegistry::load, BuildingRegistry::new, DATA_NAME);
    }

    // ── Мутирующие операции ───────────────────────────────────────────────────

    /** Добавляет здание в реестр и помечает данные как изменённые. */
    public void register(BuildingRecord record) {
        buildings.put(record.id, record);
        setDirty();
    }

    /** Удаляет здание из реестра (снос). */
    public void remove(String id) {
        if (buildings.remove(id) != null) setDirty();
    }

    // ── Чтение ────────────────────────────────────────────────────────────────

    /** Возвращает немодифицируемое view всех зданий. */
    public Collection<BuildingRecord> getAll() {
        return Collections.unmodifiableCollection(buildings.values());
    }

    public BuildingRecord get(String id) {
        return buildings.get(id);
    }

    public int size() { return buildings.size(); }

    // ── Технологии ────────────────────────────────────────────────────────────

    /**
     * Возвращает множество идентификаторов открытых технологий.
     * Учитывает ОБА источника:
     *  1. Здание в реестре → его unlocks_technologies (автоматически)
     *  2. Ручной ресёрч через /research → TechRegistry
     *
     * Используется из KubeJS: BuildingRegistry.getUnlockedTechIds(server)
     */
    public static Set<String> getUnlockedTechIds(MinecraftServer server) {
        Set<String> unlocked = new HashSet<>();

        // Из построенных зданий
        for (BuildingRecord r : get(server).getAll()) {
            BuildingDef def = GameConfig.getBuilding(r.type);
            if (def != null) {
                unlocked.addAll(def.unlocksTechnologies);
            }
        }

        // Из ручного ресёрча
        unlocked.addAll(TechRegistry.get(server).getAll());

        return unlocked;
    }

    // ── SavedData: сохранение и загрузка ─────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (BuildingRecord r : buildings.values()) {
            list.add(r.toNbt());
        }
        tag.put("buildings", list);
        return tag;
    }

    public static BuildingRegistry load(CompoundTag tag) {
        BuildingRegistry reg = new BuildingRegistry();
        ListTag list = tag.getList("buildings", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            BuildingRecord r = BuildingRecord.fromNbt(list.getCompound(i));
            reg.buildings.put(r.id, r);
        }
        return reg;
    }
}
