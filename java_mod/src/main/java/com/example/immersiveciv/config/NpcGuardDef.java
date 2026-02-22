package com.example.immersiveciv.config;

import com.google.gson.annotations.SerializedName;

/**
 * Описание одного «хранителя технологии» из npc_guards.json.
 *
 * Если именованный NPC погибает, связанная с ним технология блокируется
 * (перестаёт считаться открытой) до явного разблокирования через
 * /research или повторной постройки здания-источника.
 *
 * Идентификация NPC: по типу сущности И подстроке в CustomName/displayName.
 * Пример: сущность типа "minecraft:villager" с именем "Кузнец Григорий".
 */
public class NpcGuardDef {

    /** Namespaced ID сущности (например "minecraft:villager"). */
    @SerializedName("entity_type")
    public String entityType = "minecraft:villager";

    /**
     * Подстрока, которую должно содержать displayName NPC (без учёта регистра).
     * Если пустая строка — подходит любой NPC данного типа.
     */
    @SerializedName("name_contains")
    public String nameContains = "";

    /** Идентификатор технологии, которая блокируется при смерти. */
    @SerializedName("blocks_technology")
    public String blocksTechnology = "";

    /** Сообщение в чат всем игрокам при гибели. Поддерживает §-коды. */
    @SerializedName("death_message")
    public String deathMessage = "§c☠ Хранитель технологии погиб!";
}
