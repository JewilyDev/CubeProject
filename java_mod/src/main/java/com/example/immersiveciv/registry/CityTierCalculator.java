package com.example.immersiveciv.registry;

import com.example.immersiveciv.config.CityTierDef;
import com.example.immersiveciv.config.GameConfig;
import net.minecraft.server.MinecraftServer;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Вычисляет текущий тир города по состоянию BuildingRegistry и TechRegistry.
 *
 * Использование:
 *   CityTierDef tier = CityTierCalculator.getCurrent(server);
 *   int tierNum       = CityTierCalculator.getCurrentTierNumber(server);
 *
 * Из KubeJS:
 *   var Calc = Java.type('com.example.immersiveciv.registry.CityTierCalculator')
 *   var tierNum = Calc.getCurrentTierNumber(player.server)
 */
public final class CityTierCalculator {

    private CityTierCalculator() {}

    /**
     * Возвращает определение текущего тира города.
     * Проверяет тиры от максимального к минимальному и возвращает первый подходящий.
     */
    public static CityTierDef getCurrent(MinecraftServer server) {
        List<CityTierDef> tiers = GameConfig.getCityTiers();
        if (tiers.isEmpty()) return fallbackTier();

        // Сортируем по убыванию tier (максимальный сначала)
        List<CityTierDef> sorted = tiers.stream()
                .sorted(Comparator.comparingInt((CityTierDef t) -> t.tier).reversed())
                .toList();

        Map<String, Integer> buildingCounts = getBuildingCounts(server);
        Set<String>          unlockedTechs  = BuildingRegistry.getUnlockedTechIds(server);
        int totalBuildings = buildingCounts.values().stream().mapToInt(Integer::intValue).sum();

        for (CityTierDef def : sorted) {
            if (meets(def, buildingCounts, unlockedTechs, totalBuildings)) {
                return def;
            }
        }

        // Возвращаем минимальный тир из конфига (или заглушку)
        return sorted.getLast();
    }

    /** Возвращает только номер тира (удобно для KubeJS). */
    public static int getCurrentTierNumber(MinecraftServer server) {
        return getCurrent(server).tier;
    }

    // ── Вспомогательные ───────────────────────────────────────────────────────

    private static boolean meets(CityTierDef def,
                                  Map<String, Integer> counts,
                                  Set<String> techs,
                                  int total) {
        // Минимум зданий
        if (total < def.minTotalBuildings) return false;

        // Обязательные типы зданий
        for (Map.Entry<String, Integer> req : def.requiredBuildings.entrySet()) {
            if (counts.getOrDefault(req.getKey(), 0) < req.getValue()) return false;
        }

        // Обязательные технологии
        for (String tech : def.requiredTechnologies) {
            if (!techs.contains(tech)) return false;
        }

        return true;
    }

    private static Map<String, Integer> getBuildingCounts(MinecraftServer server) {
        Map<String, Integer> counts = new HashMap<>();
        for (BuildingRecord r : BuildingRegistry.get(server).getAll()) {
            counts.merge(r.type, 1, Integer::sum);
        }
        return counts;
    }

    private static CityTierDef fallbackTier() {
        CityTierDef t = new CityTierDef();
        t.tier  = 1;
        t.name  = "Лагерь";
        t.color = "§7";
        return t;
    }
}
