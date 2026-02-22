package com.example.immersiveciv.config;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Описание одной технологии из technologies.json.
 */
public class TechDef {

    @SerializedName("display_name")
    public String displayName = "Unknown";

    @SerializedName("description")
    public String description = "";

    /** Namespaced ID предмета-иконки (для GUI). */
    @SerializedName("icon_item")
    public String iconItem = "minecraft:book";

    /**
     * Идентификаторы зданий, которые становятся доступны после открытия.
     */
    @SerializedName("unlocks_buildings")
    public List<String> unlocksBuildingsIds = List.of();

    /**
     * Стоимость ручного исследования.
     * Если список пустой — технология открывается автоматически
     * при постройке нужного здания и не требует ручного ресёрча.
     * Если список заполнен — нужно выполнить /research <id> с нужными
     * предметами в инвентаре (items изымаются).
     *
     * Это дополнительный способ разблокировки: оба пути (здание ИЛИ
     * ресёрч) считаются полноценным открытием технологии.
     */
    @SerializedName("research_cost")
    public List<ResearchCost> researchCost = List.of();

    // ── Вложенный класс ───────────────────────────────────────────────────────

    public static class ResearchCost {
        /** Namespaced ID предмета (например "minecraft:iron_ingot"). */
        @SerializedName("item")
        public String item;

        /** Требуемое количество. */
        @SerializedName("amount")
        public int amount;

        /** Название для сообщений игроку (например «Слитки железа»). */
        @SerializedName("label")
        public String label;

        public ResearchCost() {}

        public ResearchCost(String item, int amount, String label) {
            this.item   = item;
            this.amount = amount;
            this.label  = label;
        }
    }
}
