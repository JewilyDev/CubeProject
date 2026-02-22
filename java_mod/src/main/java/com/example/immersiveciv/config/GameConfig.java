package com.example.immersiveciv.config;

import com.example.immersiveciv.Immerviseciv;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Загружает buildings.json и technologies.json из config/immersiveciv/.
 * При первом запуске создаёт файлы с дефолтными значениями.
 *
 * Вызов: GameConfig.load() при SERVER_STARTING.
 *
 * Типы зданий совпадают с BUILDING_TYPES в click_check_handler.js:
 *   free, forge, farm, barn, bakery, tavern, greenhouse,
 *   windmill, foundry, press, assembly, depot,
 *   watchtower, barracks, armory, outpost,
 *   library, alchemylab, mage_tower, observatory, archive,
 *   town_hall, bank, market
 */
public final class GameConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Map<String, BuildingDef> buildings    = new LinkedHashMap<>();
    private static Map<String, TechDef>    technologies = new LinkedHashMap<>();

    private GameConfig() {}

    // ── Публичный API ─────────────────────────────────────────────────────────

    public static BuildingDef getBuilding(String type) {
        return buildings.get(type);
    }

    public static TechDef getTech(String id) {
        return technologies.get(id);
    }

    public static Map<String, BuildingDef> getBuildings() {
        return Collections.unmodifiableMap(buildings);
    }

    public static Map<String, TechDef> getTechnologies() {
        return Collections.unmodifiableMap(technologies);
    }

    // ── Загрузка ──────────────────────────────────────────────────────────────

    public static void load() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("immersiveciv");
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            Immerviseciv.LOGGER.error("[ImmersiveCiv] Не удалось создать директорию конфигов: {}", e.getMessage());
            return;
        }

        buildings    = loadOrCreate(configDir.resolve("buildings.json"),
                new TypeToken<LinkedHashMap<String, BuildingDef>>() {}.getType(),
                GameConfig::defaultBuildings);

        technologies = loadOrCreate(configDir.resolve("technologies.json"),
                new TypeToken<LinkedHashMap<String, TechDef>>() {}.getType(),
                GameConfig::defaultTechnologies);

        Immerviseciv.LOGGER.info("[ImmersiveCiv] Конфигурация загружена: {} зданий, {} технологий",
                buildings.size(), technologies.size());
    }

    @SuppressWarnings("unchecked")
    private static <T> T loadOrCreate(Path file, Type type, Supplier<T> defaults) {
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                T loaded = GSON.fromJson(r, type);
                if (loaded != null) return loaded;
            } catch (Exception e) {
                Immerviseciv.LOGGER.error("[ImmersiveCiv] Ошибка чтения {}: {}", file.getFileName(), e.getMessage());
            }
        }
        T def = defaults.get();
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(def, type, w);
            Immerviseciv.LOGGER.info("[ImmersiveCiv] Создан дефолтный конфиг: {}", file.getFileName());
        } catch (IOException e) {
            Immerviseciv.LOGGER.error("[ImmersiveCiv] Не удалось записать {}: {}", file.getFileName(), e.getMessage());
        }
        return def;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Дефолтные здания
    // Типы зданий совпадают с BUILDING_TYPES в click_check_handler.js
    // ══════════════════════════════════════════════════════════════════════════

    private static Map<String, BuildingDef> defaultBuildings() {
        Map<String, BuildingDef> m = new LinkedHashMap<>();

        // ── Особый тип ────────────────────────────────────────────────────────
        BuildingDef free = def("Свободная постройка", 0,
                List.of(), 0, List.of(), List.of(), -1, List.of(), false);
        m.put("free", free);

        // ── Tier 1: Базовые (технологии не нужны) ─────────────────────────────

        BuildingDef forge = def("Кузница", 1,
                List.of(income("minecraft:iron_ingot", 4, 60),
                        income("minecraft:coal",        8, 60)),
                2, List.of("metallurgy"), List.of(), -1,
                List.of(
                        req("Горн / Плавильня",    1, "minecraft:furnace", "minecraft:blast_furnace"),
                        req("Наковальня",           1, "minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil"),
                        req("Каменные стены",      16, "minecraft:stone_bricks", "minecraft:bricks", "minecraft:cobblestone", "minecraft:stone"),
                        req("Сундук для заготовок", 1, "minecraft:chest", "minecraft:barrel")
                ), true);
        m.put("forge", forge);

        BuildingDef farm = def("Ферма", 1,
                List.of(income("minecraft:wheat",   8, 60),
                        income("minecraft:carrot",  6, 60),
                        income("minecraft:potato",  6, 60)),
                3, List.of("agriculture"), List.of(), -1,
                List.of(
                        req("Обрабатываемая земля",  9, "minecraft:farmland"),
                        req("Семена / Посевы",        4, "minecraft:wheat", "minecraft:carrots", "minecraft:potatoes", "minecraft:beetroots"),
                        req("Вода рядом",             1, "minecraft:water")
                ), false);
        m.put("farm", farm);

        BuildingDef watchtower = def("Сторожевая башня", 1,
                List.of(), 2, List.of("military"), List.of(), -1,
                List.of(
                        req("Каменные стены (башня)", 30, "minecraft:stone_bricks", "minecraft:deepslate_bricks", "minecraft:cobblestone"),
                        req("Зубцы / Плиты наверху",   4, "minecraft:stone_brick_slab", "minecraft:cobblestone_slab", "minecraft:deepslate_brick_slab"),
                        req("Лестница / Ступени",       4, "minecraft:ladder", "minecraft:stone_stairs", "minecraft:cobblestone_stairs")
                ), true);
        m.put("watchtower", watchtower);

        BuildingDef market = def("Рынок", 1,
                List.of(income("minecraft:emerald",    2, 60),
                        income("minecraft:gold_ingot", 3, 60)),
                4, List.of("commerce"), List.of(), 2,
                List.of(
                        req("Прилавки (заборы / плиты)", 6, "minecraft:oak_fence", "minecraft:spruce_fence", "minecraft:oak_slab", "minecraft:spruce_slab"),
                        req("Картографический стол",     1, "minecraft:cartography_table"),
                        req("Навес (плиты / ступени)",   8, "minecraft:oak_slab", "minecraft:spruce_slab", "minecraft:oak_stairs", "minecraft:spruce_stairs")
                ), false);
        m.put("market", market);

        BuildingDef library = def("Библиотека", 1,
                List.of(income("minecraft:book",  4, 120),
                        income("minecraft:paper", 16, 120)),
                2, List.of("education"), List.of(), 1,
                List.of(
                        req("Книжные полки",    6, "minecraft:bookshelf"),
                        req("Стол зачарований", 1, "minecraft:enchanting_table"),
                        req("Свечи / Фонари",   3, "minecraft:candle", "minecraft:lantern", "minecraft:soul_lantern")
                ), true);
        m.put("library", library);

        // ── Tier 2: Требуют agriculture ───────────────────────────────────────

        BuildingDef barn = def("Амбар", 2,
                List.of(income("minecraft:bread",        12, 60),
                        income("minecraft:wheat_seeds",   8, 60)),
                2, List.of(), List.of("agriculture"), 2,
                List.of(
                        req("Деревянные стены", 20, "minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks",
                                "minecraft:jungle_planks", "minecraft:acacia_planks", "minecraft:dark_oak_planks"),
                        req("Сундуки / Бочки",  2, "minecraft:chest", "minecraft:barrel"),
                        req("Дверь",            1, "minecraft:oak_door", "minecraft:spruce_door", "minecraft:birch_door",
                                "minecraft:jungle_door", "minecraft:acacia_door", "minecraft:dark_oak_door")
                ), true);
        m.put("barn", barn);

        BuildingDef bakery = def("Пекарня", 2,
                List.of(income("minecraft:bread",  16, 60),
                        income("minecraft:cookie",  8, 60)),
                2, List.of(), List.of("agriculture"), 2,
                List.of(
                        req("Печь / Коптильня",   2, "minecraft:furnace", "minecraft:smoker"),
                        req("Верстак",             1, "minecraft:crafting_table"),
                        req("Кирпичные стены",    12, "minecraft:stone_bricks", "minecraft:brick", "minecraft:bricks"),
                        req("Сундук",              1, "minecraft:chest", "minecraft:barrel")
                ), true);
        m.put("bakery", bakery);

        BuildingDef greenhouse = def("Теплица", 2,
                List.of(income("minecraft:melon_slice",  10, 60),
                        income("minecraft:pumpkin",       4, 60),
                        income("minecraft:sugar_cane",    8, 60)),
                2, List.of(), List.of("agriculture"), 2,
                List.of(
                        req("Стеклянные стены / Крыша", 20, "minecraft:glass", "minecraft:glass_pane",
                                "minecraft:white_stained_glass", "minecraft:light_blue_stained_glass", "minecraft:lime_stained_glass"),
                        req("Земля / Ферма",             6, "minecraft:dirt", "minecraft:farmland", "minecraft:grass_block"),
                        req("Вода",                      1, "minecraft:water")
                ), true);
        m.put("greenhouse", greenhouse);

        // ── Tier 2: Требуют metallurgy ────────────────────────────────────────

        BuildingDef foundry = def("Плавильня", 2,
                List.of(income("minecraft:iron_ingot", 8, 60),
                        income("minecraft:gold_ingot", 2, 60)),
                3, List.of("engineering"), List.of("metallurgy"), -1,
                List.of(
                        req("Кузнечный горн (Create) / Доменная печь", 1, "create:blaze_burner", "minecraft:blast_furnace"),
                        req("Наковальня",                               1, "minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil"),
                        req("Каменные / Кирпичные стены",              24, "minecraft:stone_bricks", "minecraft:bricks", "minecraft:deepslate_bricks"),
                        req("Сундук",                                   1, "minecraft:chest")
                ), true);
        m.put("foundry", foundry);

        BuildingDef armory = def("Арсенал", 2,
                List.of(income("minecraft:iron_sword",   1, 120),
                        income("minecraft:iron_helmet",  1, 120)),
                2, List.of("military"), List.of("metallurgy"), 1,
                List.of(
                        req("Стойки для брони (заборы / решётки)", 4, "minecraft:oak_fence", "minecraft:iron_bars"),
                        req("Кузнечный стол",                      1, "minecraft:smithing_table"),
                        req("Сундуки",                             2, "minecraft:chest", "minecraft:barrel"),
                        req("Точильный камень",                    1, "minecraft:grindstone")
                ), true);
        m.put("armory", armory);

        // ── Tier 2: Требуют engineering (windmill → engineering) ──────────────

        BuildingDef windmill = def("Ветряная мельница", 1,
                List.of(income("minecraft:wheat", 6, 60)),
                2, List.of("engineering"), List.of(), -1,
                List.of(
                        req("Паруса (шерсть)",  12, "minecraft:white_wool", "minecraft:gray_wool", "minecraft:light_gray_wool"),
                        req("Дерево (ось)",      4, "minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log"),
                        req("Шестерни (Create)", 1, "create:cogwheel", "create:large_cogwheel")
                ), false);
        m.put("windmill", windmill);

        BuildingDef press = def("Механический пресс", 2,
                List.of(income("minecraft:iron_nugget", 16, 60)),
                3, List.of(), List.of("engineering"), -1,
                List.of(
                        req("Пресс (Create)",      1, "create:mechanical_press"),
                        req("Конвейер (Create)",   2, "create:belt", "create:mechanical_belt"),
                        req("Шестерни (Create)",   2, "create:cogwheel", "create:large_cogwheel")
                ), true);
        m.put("press", press);

        BuildingDef assembly = def("Цех сборки", 2,
                List.of(income("minecraft:piston", 1, 120)),
                4, List.of(), List.of("engineering"), -1,
                List.of(
                        req("Деплойер (Create)",         2, "create:deployer"),
                        req("Конвейер (Create)",          4, "create:belt", "create:mechanical_belt"),
                        req("Переключатель / Контакт",   1, "create:sequenced_gearshift", "create:redstone_contact")
                ), true);
        m.put("assembly", assembly);

        BuildingDef depot = def("Депо", 2,
                List.of(income("minecraft:emerald", 2, 60)),
                3, List.of(), List.of("engineering"), -1,
                List.of(
                        req("Склад (сундуки / хранилище)", 4, "minecraft:chest", "minecraft:barrel", "create:vault"),
                        req("Воронки",                     2, "minecraft:hopper"),
                        req("Рельсы",                      4, "minecraft:rail", "minecraft:powered_rail", "create:track")
                ), true);
        m.put("depot", depot);

        // ── Tier 2: Требуют commerce ──────────────────────────────────────────

        BuildingDef tavern = def("Таверна", 2,
                List.of(income("minecraft:emerald",    3, 60),
                        income("minecraft:cooked_beef", 8, 60)),
                4, List.of(), List.of("commerce"), 1,
                List.of(
                        req("Стойки (заборы)",   3, "minecraft:oak_fence", "minecraft:spruce_fence", "minecraft:birch_fence"),
                        req("Котёл",             1, "minecraft:cauldron"),
                        req("Кровати",           2, "minecraft:red_bed", "minecraft:white_bed", "minecraft:yellow_bed",
                                "minecraft:blue_bed", "minecraft:green_bed", "minecraft:brown_bed", "minecraft:black_bed",
                                "minecraft:cyan_bed", "minecraft:gray_bed", "minecraft:light_blue_bed",
                                "minecraft:lime_bed", "minecraft:magenta_bed", "minecraft:orange_bed",
                                "minecraft:pink_bed", "minecraft:purple_bed", "minecraft:light_gray_bed"),
                        req("Дверь",             1, "minecraft:oak_door", "minecraft:spruce_door", "minecraft:birch_door")
                ), true);
        m.put("tavern", tavern);

        BuildingDef bank = def("Банк", 2,
                List.of(income("minecraft:gold_ingot", 4, 60),
                        income("minecraft:emerald",    2, 60)),
                3, List.of(), List.of("commerce"), 1,
                List.of(
                        req("Железо (хранилище)", 8, "minecraft:iron_block", "minecraft:iron_bars"),
                        req("Сундуки",            4, "minecraft:chest"),
                        req("Прилавок (плиты)",   4, "minecraft:stone_brick_slab", "minecraft:smooth_stone_slab")
                ), true);
        m.put("bank", bank);

        // ── Tier 2: Требуют military ──────────────────────────────────────────

        BuildingDef barracks = def("Казармы", 2,
                List.of(income("minecraft:leather_helmet",     1, 120),
                        income("minecraft:leather_chestplate", 1, 120)),
                5, List.of(), List.of("military"), 1,
                List.of(
                        req("Кровати",                    3, "minecraft:red_bed", "minecraft:white_bed", "minecraft:blue_bed"),
                        req("Стойки для оружия (заборы)", 4, "minecraft:oak_fence", "minecraft:spruce_fence", "minecraft:iron_bars"),
                        req("Каменные стены",            20, "minecraft:stone_bricks", "minecraft:bricks"),
                        req("Дверь",                      1, "minecraft:iron_door", "minecraft:oak_door")
                ), true);
        m.put("barracks", barracks);

        BuildingDef outpost = def("Аванпост", 2,
                List.of(), 3, List.of(), List.of("military"), -1,
                List.of(
                        req("Каменные стены",      16, "minecraft:cobblestone", "minecraft:stone_bricks"),
                        req("Бойницы (стены)",      4, "minecraft:cobblestone_wall", "minecraft:stone_brick_wall"),
                        req("Факелы / Фонари",      2, "minecraft:torch", "minecraft:lantern", "minecraft:wall_torch")
                ), false);
        m.put("outpost", outpost);

        // ── Tier 3: Требуют education ─────────────────────────────────────────

        BuildingDef alchemylab = def("Алхимическая лаборатория", 3,
                List.of(income("minecraft:experience_bottle", 4, 120)),
                2, List.of(), List.of("education"), 1,
                List.of(
                        req("Стойки для зелий",   2, "minecraft:brewing_stand"),
                        req("Котёл",              1, "minecraft:cauldron", "minecraft:water_cauldron"),
                        req("Сундуки",            1, "minecraft:chest", "minecraft:barrel"),
                        req("Каменные стены",    16, "minecraft:stone_bricks", "minecraft:deepslate_bricks")
                ), true);
        m.put("alchemylab", alchemylab);

        BuildingDef mage_tower = def("Башня мага", 3,
                List.of(income("minecraft:ender_pearl", 2, 120),
                        income("minecraft:experience_bottle", 4, 120)),
                1, List.of(), List.of("education"), 1,
                List.of(
                        req("Обсидиан",             8, "minecraft:obsidian", "minecraft:crying_obsidian"),
                        req("Магическое стекло",    6, "minecraft:purple_stained_glass", "minecraft:blue_stained_glass", "minecraft:cyan_stained_glass"),
                        req("Стол зачарований",     1, "minecraft:enchanting_table"),
                        req("Лестница (высота)",    6, "minecraft:ladder")
                ), true);
        m.put("mage_tower", mage_tower);

        BuildingDef observatory = def("Обсерватория", 3,
                List.of(income("minecraft:experience_bottle", 6, 120),
                        income("minecraft:spyglass",         1, 480)),
                2, List.of(), List.of("education"), 1,
                List.of(
                        req("Стеклянный купол",  16, "minecraft:glass", "minecraft:glass_pane"),
                        req("Телескоп (труба)",   1, "minecraft:spyglass", "minecraft:iron_block", "create:brass_block"),
                        req("Каменная кладка",   20, "minecraft:stone_bricks")
                ), true);
        m.put("observatory", observatory);

        BuildingDef archive = def("Архив", 3,
                List.of(income("minecraft:written_book", 1, 480),
                        income("minecraft:paper",        8, 120)),
                3, List.of(), List.of("education"), 1,
                List.of(
                        req("Книжные полки", 12, "minecraft:bookshelf"),
                        req("Сундуки",        4, "minecraft:chest", "minecraft:barrel"),
                        req("Лекторн",        1, "minecraft:lectern")
                ), true);
        m.put("archive", archive);

        // ── Tier 4: Требуют несколько технологий ─────────────────────────────

        BuildingDef town_hall = def("Ратуша", 4,
                List.of(income("minecraft:emerald", 4, 60)),
                5, List.of("governance"), List.of("commerce", "military"), 1,
                List.of(
                        req("Камень / Полированный сланец (стены)", 40,
                                "minecraft:stone_bricks", "minecraft:polished_deepslate", "minecraft:deepslate_bricks"),
                        req("Флаг (баннер)",         1, "minecraft:white_banner", "minecraft:yellow_banner",
                                "minecraft:blue_banner", "minecraft:red_banner"),
                        req("Лекторн (кафедра)",     1, "minecraft:lectern"),
                        req("Сундук / Хранилище",    2, "minecraft:chest", "minecraft:barrel")
                ), true);
        m.put("town_hall", town_hall);

        return m;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Дефолтные технологии
    // ══════════════════════════════════════════════════════════════════════════

    private static Map<String, TechDef> defaultTechnologies() {
        Map<String, TechDef> m = new LinkedHashMap<>();

        // ── agriculture: автоматически (ферма) ───────────────────────────────
        m.put("agriculture", tech(
                "Земледелие",
                "Основы выращивания культур и хранения урожая. Открывается при постройке фермы.",
                "minecraft:wheat",
                List.of("barn", "bakery", "greenhouse"),
                List.of()   // нет research_cost — только через здание
        ));

        // ── metallurgy: автоматически (кузница) ──────────────────────────────
        m.put("metallurgy", tech(
                "Металлургия",
                "Работа с металлами: ковка, плавка, обработка. Открывается при постройке кузницы.",
                "minecraft:iron_ingot",
                List.of("foundry", "armory"),
                List.of()
        ));

        // ── engineering: здание (мельница) ИЛИ ресёрч ────────────────────────
        m.put("engineering", tech(
                "Инженерия",
                "Механические системы и автоматизация. Открывается при постройке мельницы "
                + "или через ресёрч (нужны шестерни Create + железо).",
                "minecraft:piston",
                List.of("press", "assembly", "depot"),
                List.of(
                        rCost("minecraft:iron_ingot",    32, "Слитки железа"),
                        rCost("create:cogwheel",          8, "Шестерни (Create)"),
                        rCost("minecraft:redstone",       16, "Красный камень")
                )
        ));

        // ── military: автоматически (сторожевая башня) ───────────────────────
        m.put("military", tech(
                "Военное дело",
                "Организация защиты поселения. Открывается при постройке сторожевой башни.",
                "minecraft:iron_sword",
                List.of("barracks", "armory", "outpost"),
                List.of()
        ));

        // ── education: здание (библиотека) ИЛИ ресёрч ────────────────────────
        m.put("education", tech(
                "Просвещение",
                "Накопление и передача знаний. Открывается при постройке библиотеки "
                + "или через ресёрч (книги + бумага).",
                "minecraft:book",
                List.of("alchemylab", "mage_tower", "observatory", "archive"),
                List.of(
                        rCost("minecraft:book",  16, "Книги"),
                        rCost("minecraft:paper", 32, "Бумага"),
                        rCost("minecraft:ink_sac", 8, "Чернила")
                )
        ));

        // ── commerce: автоматически (рынок) ──────────────────────────────────
        m.put("commerce", tech(
                "Торговля",
                "Основы рыночной экономики и обмена товарами. Открывается при постройке рынка.",
                "minecraft:emerald",
                List.of("tavern", "bank"),
                List.of()
        ));

        // ── governance: здание (ратуша) ИЛИ ресёрч ───────────────────────────
        m.put("governance", tech(
                "Управление",
                "Государственное управление и дипломатия. Открывается при постройке ратуши "
                + "или через ресёрч (золото + писаные книги).",
                "minecraft:writable_book",
                List.of(),
                List.of(
                        rCost("minecraft:gold_ingot",    32, "Золотые слитки"),
                        rCost("minecraft:writable_book",  4, "Книга с пером"),
                        rCost("minecraft:emerald",        8, "Изумруды")
                )
        ));

        return m;
    }

    // ── Вспомогательные фабрики ───────────────────────────────────────────────

    private static BuildingDef def(
            String displayName, int tier,
            List<BuildingDef.IncomeItem> income,
            int residentCapacity,
            List<String> unlocksTech,
            List<String> requiresTech,
            int maxInstances,
            List<BuildingDef.BlockRequirement> blockReqs,
            boolean checkPerimeter
    ) {
        BuildingDef d = new BuildingDef();
        d.displayName          = displayName;
        d.tier                 = tier;
        d.income               = income;
        d.residentCapacity     = residentCapacity;
        d.unlocksTechnologies  = unlocksTech;
        d.requiresTechnologies = requiresTech;
        d.maxInstances         = maxInstances;
        d.blockRequirements    = blockReqs;
        d.checkPerimeter       = checkPerimeter;
        return d;
    }

    private static BuildingDef.IncomeItem income(String item, int amount, int periodMinutes) {
        return new BuildingDef.IncomeItem(item, amount, periodMinutes);
    }

    private static BuildingDef.BlockRequirement req(String label, int min, String... ids) {
        return new BuildingDef.BlockRequirement(label, min, Arrays.asList(ids));
    }

    private static TechDef tech(
            String displayName, String description,
            String iconItem,
            List<String> unlocksBuildingIds,
            List<TechDef.ResearchCost> researchCost
    ) {
        TechDef t = new TechDef();
        t.displayName         = displayName;
        t.description         = description;
        t.iconItem            = iconItem;
        t.unlocksBuildingsIds = unlocksBuildingIds;
        t.researchCost        = researchCost;
        return t;
    }

    private static TechDef.ResearchCost rCost(String item, int amount, String label) {
        return new TechDef.ResearchCost(item, amount, label);
    }
}
