package com.example.immersiveciv.config;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * Описание одного тира города из city_tiers.json.
 *
 * Тиры проверяются сверху вниз (от максимального). Игрок получает наивысший
 * тир, требованиям которого соответствует текущий набор зданий и технологий.
 */
public class CityTierDef {

    /** Порядковый номер (1 = минимальный). */
    @SerializedName("tier")
    public int tier = 1;

    /** Отображаемое название (например «Деревня»). */
    @SerializedName("name")
    public String name = "Лагерь";

    /** Цветовой код Minecraft (§a, §e, §6 и т.д.). */
    @SerializedName("color")
    public String color = "§7";

    /**
     * Обязательные типы зданий и их минимальное количество.
     * Ключ = building_type, значение = минимальное число экземпляров.
     */
    @SerializedName("required_buildings")
    public Map<String, Integer> requiredBuildings = Map.of();

    /**
     * Идентификаторы технологий, которые должны быть открыты.
     */
    @SerializedName("required_technologies")
    public List<String> requiredTechnologies = List.of();

    /** Минимальное суммарное количество любых зданий. */
    @SerializedName("min_total_buildings")
    public int minTotalBuildings = 0;
}
