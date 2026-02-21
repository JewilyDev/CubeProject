// ImmersiveCiv — Проверка обязательных блоков по типу здания
// Реестр требований: для каждого типа здания задаётся список групп.
// Группа = { any: [...ids], min: N } — нужно минимум N блоков из списка.

/** @type {Record<string, Array<{label: string, any: string[], min: number}>>} */
const BUILDING_REQUIREMENTS = {
    // ── Мастер продовольствия ────────────────────────────────────────────────
    well: [
        { label: 'стены из камня', any: ['minecraft:stone', 'minecraft:cobblestone', 'minecraft:stone_bricks', 'minecraft:mossy_stone_bricks'], min: 16 },
        { label: 'вода', any: ['minecraft:water'], min: 1 },
        { label: 'ведро/сундук', any: ['minecraft:bucket', 'minecraft:chest'], min: 1 },
    ],
    farm: [
        { label: 'обрабатываемая земля', any: ['minecraft:farmland'], min: 9 },
        { label: 'семена/посевы', any: ['minecraft:wheat', 'minecraft:carrots', 'minecraft:potatoes', 'minecraft:beetroots'], min: 4 },
        { label: 'вода рядом', any: ['minecraft:water'], min: 1 },
    ],
    barn: [
        { label: 'стены из дерева', any: ['minecraft:oak_planks','minecraft:spruce_planks','minecraft:birch_planks','minecraft:jungle_planks','minecraft:acacia_planks','minecraft:dark_oak_planks'], min: 20 },
        { label: 'сундуки для хранения', any: ['minecraft:chest', 'minecraft:barrel'], min: 2 },
        { label: 'дверь', any: ['minecraft:oak_door','minecraft:spruce_door','minecraft:birch_door','minecraft:jungle_door','minecraft:acacia_door','minecraft:dark_oak_door'], min: 1 },
    ],
    bakery: [
        { label: 'печь', any: ['minecraft:furnace', 'minecraft:smoker'], min: 2 },
        { label: 'верстак', any: ['minecraft:crafting_table'], min: 1 },
        { label: 'сундук', any: ['minecraft:chest', 'minecraft:barrel'], min: 1 },
        { label: 'стены', any: ['minecraft:stone_bricks','minecraft:brick','minecraft:bricks'], min: 12 },
    ],
    tavern: [
        { label: 'стойки (заборы)', any: ['minecraft:oak_fence','minecraft:spruce_fence','minecraft:birch_fence'], min: 3 },
        { label: 'котёл или чан', any: ['minecraft:cauldron'], min: 1 },
        { label: 'кровати', any: ['minecraft:red_bed','minecraft:white_bed','minecraft:yellow_bed','minecraft:blue_bed','minecraft:green_bed','minecraft:brown_bed','minecraft:black_bed','minecraft:cyan_bed','minecraft:gray_bed','minecraft:light_blue_bed','minecraft:lime_bed','minecraft:magenta_bed','minecraft:orange_bed','minecraft:pink_bed','minecraft:purple_bed','minecraft:light_gray_bed'], min: 2 },
        { label: 'дверь', any: ['minecraft:oak_door','minecraft:spruce_door','minecraft:birch_door'], min: 1 },
    ],
    greenhouse: [
        { label: 'стекло', any: ['minecraft:glass','minecraft:glass_pane','minecraft:white_stained_glass','minecraft:light_blue_stained_glass','minecraft:lime_stained_glass'], min: 20 },
        { label: 'земля/ферма', any: ['minecraft:dirt','minecraft:farmland','minecraft:grass_block'], min: 6 },
        { label: 'вода', any: ['minecraft:water'], min: 1 },
    ],

    // ── Мастер инженерии ─────────────────────────────────────────────────────
    windmill: [
        { label: 'паруса (шерсть)', any: ['minecraft:white_wool','minecraft:gray_wool','minecraft:light_gray_wool'], min: 12 },
        { label: 'дерево (ось)', any: ['minecraft:oak_log','minecraft:spruce_log','minecraft:birch_log'], min: 4 },
        { label: 'шестерни Create', any: ['create:cogwheel','create:large_cogwheel'], min: 1 },
    ],
    foundry: [
        { label: 'кузнечный горн (Create)', any: ['create:blaze_burner','minecraft:blast_furnace'], min: 1 },
        { label: 'наковальня', any: ['minecraft:anvil','minecraft:chipped_anvil','minecraft:damaged_anvil'], min: 1 },
        { label: 'камень/кирпич', any: ['minecraft:stone_bricks','minecraft:bricks','minecraft:deepslate_bricks'], min: 24 },
        { label: 'сундук', any: ['minecraft:chest'], min: 1 },
    ],
    press: [
        { label: 'пресс Create', any: ['create:mechanical_press'], min: 1 },
        { label: 'конвейер Create', any: ['create:belt','create:mechanical_belt'], min: 2 },
        { label: 'шестерня', any: ['create:cogwheel','create:large_cogwheel'], min: 2 },
    ],
    assembly: [
        { label: 'деплойер Create', any: ['create:deployer'], min: 2 },
        { label: 'конвейер Create', any: ['create:belt','create:mechanical_belt'], min: 4 },
        { label: 'наблюдатель/читатель', any: ['create:sequenced_gearshift','create:redstone_contact'], min: 1 },
    ],
    depot: [
        { label: 'склад (сундуки)', any: ['minecraft:chest','minecraft:barrel','create:vault'], min: 4 },
        { label: 'воронки', any: ['minecraft:hopper'], min: 2 },
        { label: 'рельсы', any: ['minecraft:rail','minecraft:powered_rail','create:track'], min: 4 },
    ],

    // ── Мастер обороны ───────────────────────────────────────────────────────
    watchtower: [
        { label: 'камень/кирпич (стены)', any: ['minecraft:stone_bricks','minecraft:deepslate_bricks','minecraft:cobblestone'], min: 30 },
        { label: 'зубцы (плиты наверху)', any: ['minecraft:stone_brick_slab','minecraft:cobblestone_slab','minecraft:deepslate_brick_slab'], min: 4 },
        { label: 'высота (лестница)', any: ['minecraft:ladder','minecraft:stone_stairs','minecraft:cobblestone_stairs'], min: 4 },
    ],
    barracks: [
        { label: 'кровати', any: ['minecraft:red_bed','minecraft:white_bed','minecraft:blue_bed'], min: 3 },
        { label: 'стойки для оружия (заборы)', any: ['minecraft:oak_fence','minecraft:spruce_fence','minecraft:iron_bars'], min: 4 },
        { label: 'стены', any: ['minecraft:stone_bricks','minecraft:bricks'], min: 20 },
        { label: 'дверь', any: ['minecraft:iron_door','minecraft:oak_door'], min: 1 },
    ],
    armory: [
        { label: 'стойки для брони (заборы)', any: ['minecraft:oak_fence','minecraft:iron_bars'], min: 4 },
        { label: 'кузнечный стол', any: ['minecraft:smithing_table'], min: 1 },
        { label: 'сундуки', any: ['minecraft:chest','minecraft:barrel'], min: 2 },
        { label: 'точильный камень', any: ['minecraft:grindstone'], min: 1 },
    ],
    outpost: [
        { label: 'стены', any: ['minecraft:cobblestone','minecraft:stone_bricks'], min: 16 },
        { label: 'бойницы (заборы)', any: ['minecraft:cobblestone_wall','minecraft:stone_brick_wall'], min: 4 },
        { label: 'факелы', any: ['minecraft:torch','minecraft:lantern','minecraft:wall_torch'], min: 2 },
    ],

    // ── Мастер магии ─────────────────────────────────────────────────────────
    library: [
        { label: 'книжные полки', any: ['minecraft:bookshelf'], min: 6 },
        { label: 'стол зачарований', any: ['minecraft:enchanting_table'], min: 1 },
        { label: 'свечи/фонари', any: ['minecraft:candle','minecraft:lantern','minecraft:soul_lantern'], min: 3 },
    ],
    alchemylab: [
        { label: 'стойки для зелий', any: ['minecraft:brewing_stand'], min: 2 },
        { label: 'котёл', any: ['minecraft:cauldron','minecraft:water_cauldron'], min: 1 },
        { label: 'сундуки', any: ['minecraft:chest','minecraft:barrel'], min: 1 },
        { label: 'камень', any: ['minecraft:stone_bricks','minecraft:deepslate_bricks'], min: 16 },
    ],
    mage_tower: [
        { label: 'обсидиан', any: ['minecraft:obsidian','minecraft:crying_obsidian'], min: 8 },
        { label: 'стекло (магия)', any: ['minecraft:purple_stained_glass','minecraft:blue_stained_glass','minecraft:cyan_stained_glass'], min: 6 },
        { label: 'стол зачарований', any: ['minecraft:enchanting_table'], min: 1 },
        { label: 'высота (лестница)', any: ['minecraft:ladder'], min: 6 },
    ],
    observatory: [
        { label: 'стекло (купол)', any: ['minecraft:glass','minecraft:glass_pane'], min: 16 },
        { label: 'телескоп (труба)', any: ['minecraft:spyglass','minecraft:iron_block','create:brass_block'], min: 1 },
        { label: 'камень', any: ['minecraft:stone_bricks'], min: 20 },
    ],
    archive: [
        { label: 'книжные полки', any: ['minecraft:bookshelf'], min: 12 },
        { label: 'сундуки', any: ['minecraft:chest','minecraft:barrel'], min: 4 },
        { label: 'лекторн', any: ['minecraft:lectern'], min: 1 },
    ],

    // ── Мэр ──────────────────────────────────────────────────────────────────
    town_hall: [
        { label: 'камень (стены)', any: ['minecraft:stone_bricks','minecraft:polished_deepslate','minecraft:deepslate_bricks'], min: 40 },
        { label: 'флаг (баннер)', any: ['minecraft:white_banner','minecraft:yellow_banner','minecraft:blue_banner','minecraft:red_banner'], min: 1 },
        { label: 'лекторн (кафедра)', any: ['minecraft:lectern'], min: 1 },
        { label: 'сундук/хранилище', any: ['minecraft:chest','minecraft:barrel'], min: 2 },
    ],
    bank: [
        { label: 'железо (хранилище)', any: ['minecraft:iron_block','minecraft:iron_bars'], min: 8 },
        { label: 'сундуки', any: ['minecraft:chest'], min: 4 },
        { label: 'прилавок (плиты)', any: ['minecraft:stone_brick_slab','minecraft:smooth_stone_slab'], min: 4 },
    ],
    market: [
        { label: 'прилавки (заборы/плиты)', any: ['minecraft:oak_fence','minecraft:spruce_fence','minecraft:oak_slab','minecraft:spruce_slab'], min: 6 },
        { label: 'картографический стол', any: ['minecraft:cartography_table'], min: 1 },
        { label: 'навес (плиты/ступени)', any: ['minecraft:oak_slab','minecraft:spruce_slab','minecraft:oak_stairs','minecraft:spruce_stairs'], min: 8 },
    ],
}

/**
 * Проверяет наличие обязательных блоков для типа здания.
 *
 * @param {string} type
 * @param {{ id: string }[]} blocks
 * @returns {{ name: string, ok: boolean, message: string }}
 */
function checkRequiredBlocks(type, blocks) {
    const requirements = BUILDING_REQUIREMENTS[type]
    console.info(blocks)
    if (!requirements) {
        return {
            name: 'required_blocks',
            ok: false,
            message: `Неизвестный тип здания: «${type}». Доступны: ${Object.keys(BUILDING_REQUIREMENTS).join(', ')}`
        }
    }

    const blockIdSet = {}
    for (const b of blocks) {
        blockIdSet[b.id] = (blockIdSet[b.id] || 0) + 1
    }

    const failed = []
    for (const req of requirements) {
        const count = req.any.reduce((sum, id) => sum + (blockIdSet[id] || 0), 0)
        if (count < req.min) {
            failed.push(`«${req.label}» — нужно ${req.min}, найдено ${count}`)
        }
    }

    if (failed.length > 0) {
        return {
            name: 'required_blocks',
            ok: false,
            message: `Не хватает обязательных блоков:\n  • ${failed.join('\n  • ')}`
        }
    }

    return {
        name: 'required_blocks',
        ok: true,
        message: `Все обязательные блоки присутствуют (${requirements.length} групп проверено).`
    }
}
