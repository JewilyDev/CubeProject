package com.example.immersiveciv.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Клиентская копия реестра зданий.
 * Заполняется из S2C-пакетов SYNC_BUILDINGS / BUILDING_REGISTERED / BUILDING_REMOVED.
 * Используется CityMapScreen и world-overlay рендерером.
 */
public final class ClientBuildingRegistry {

    private static final Map<String, BuildingRecord> buildings = new LinkedHashMap<>();

    /** Добавить/обновить запись (из BUILDING_REGISTERED). */
    public static void put(BuildingRecord r) {
        buildings.put(r.id, r);
    }

    /** Удалить запись (из BUILDING_REMOVED). */
    public static void remove(String id) {
        buildings.remove(id);
    }

    /** Полностью заменить реестр (из SYNC_BUILDINGS). */
    public static void replaceAll(Collection<BuildingRecord> incoming) {
        buildings.clear();
        for (BuildingRecord r : incoming) {
            buildings.put(r.id, r);
        }
    }

    /** Очистить при выходе из мира. */
    public static void clear() {
        buildings.clear();
    }

    public static Collection<BuildingRecord> getAll() {
        return Collections.unmodifiableCollection(buildings.values());
    }

    public static BuildingRecord get(String id) {
        return buildings.get(id);
    }

    public static boolean isEmpty() {
        return buildings.isEmpty();
    }

    private ClientBuildingRegistry() {}
}
