package com.example.immersiveciv.config;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Описание одного типа здания из buildings.json.
 */
public class BuildingDef {

    @SerializedName("display_name")
    public String displayName = "Unknown";

    /** Уровень здания: 1 = базовые, 2 = средние, 3 = продвинутые. */
    @SerializedName("tier")
    public int tier = 1;

    /** Доходность: список предметов, которые здание производит. */
    @SerializedName("income")
    public List<IncomeItem> income = List.of();

    /** Сколько новых жителей можно нанять при наличии этого здания. */
    @SerializedName("resident_capacity")
    public int residentCapacity = 0;

    /** Идентификаторы технологий, которые разблокирует это здание. */
    @SerializedName("unlocks_technologies")
    public List<String> unlocksTechnologies = List.of();

    /** Идентификаторы технологий, которые должны быть изучены для постройки. */
    @SerializedName("requires_technologies")
    public List<String> requiresTechnologies = List.of();

    /**
     * Максимальное число экземпляров на всё поселение. -1 = без ограничений.
     */
    @SerializedName("max_instances")
    public int maxInstances = -1;

    /**
     * Требования к составу блоков региона.
     * Каждая запись — одна группа; нужно минимум {@code min} блоков из списка {@code any}.
     */
    @SerializedName("block_requirements")
    public List<BlockRequirement> blockRequirements = List.of();

    /**
     * Нужно ли проверять замкнутость периметра стен.
     * Отключайте для открытых построек (ферма, аванпост, башня).
     */
    @SerializedName("check_perimeter")
    public boolean checkPerimeter = true;

    // ── Вложенные классы ──────────────────────────────────────────────────────

    public static class IncomeItem {
        @SerializedName("item")
        public String item;

        @SerializedName("amount")
        public int amount;

        /** Период начисления в минутах. */
        @SerializedName("period_minutes")
        public int periodMinutes;

        public IncomeItem() {}

        public IncomeItem(String item, int amount, int periodMinutes) {
            this.item = item;
            this.amount = amount;
            this.periodMinutes = periodMinutes;
        }
    }

    public static class BlockRequirement {
        /** Название группы для сообщений игроку (например «Печь/Горн»). */
        @SerializedName("label")
        public String label = "";

        /** Допустимые блоки — нужен хотя бы {@code min} штук суммарно. */
        @SerializedName("any")
        public List<String> any = List.of();

        /** Минимальное суммарное количество. */
        @SerializedName("min")
        public int min = 1;

        public BlockRequirement() {}

        public BlockRequirement(String label, int min, List<String> any) {
            this.label = label;
            this.min   = min;
            this.any   = any;
        }
    }
}
