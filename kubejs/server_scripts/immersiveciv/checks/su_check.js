// ImmersiveCiv — Проверка генерации SU (Stress Units) для инженерных построек
// Используется для зданий: windmill, foundry, press, assembly, depot.
//
// Create мод не предоставляет прямого KubeJS API для чтения SU сети,
// поэтому делаем структурную проверку: наличие и комбинация блоков,
// которые при нормальной установке дают достаточно SU.

/** Ориентировочные SU от источников (упрощённые значения Create 0.5) */
const SU_SOURCES = {
    'create:water_wheel':          256,
    'create:large_water_wheel':    768,
    'create:windmill_bearing':     0,   // SU зависит от размера паруса — считаем отдельно
    'create:hand_crank':           32,
    'create:furnace_engine':       1024,
    'create:steam_engine':         1024,
    'create:flywheel':             0,   // накопитель, не источник
}

/** Потребление SU у машин */
const SU_CONSUMERS = {
    'create:mechanical_press':     256,
    'create:mechanical_mixer':     256,
    'create:mechanical_saw':       128,
    'create:mechanical_drill':     128,
    'create:mechanical_harvester': 256,
    'create:mechanical_plough':    128,
    'create:deployer':             64,
    'create:millstone':            64,
    'create:encased_fan':          64,
    'create:mechanical_pump':      64,
    'create:belt':                 2,
    'create:mechanical_belt':      2,
}

/** Минимальный баланс SU (генерация − потребление) для каждого типа здания */
const MIN_SU_BALANCE = {
    windmill:  128,
    foundry:   256,
    press:     512,
    assembly:  384,
    depot:     128,
}

/**
 * @param {string} type
 * @param {{ id: string }[]} blocks
 * @returns {{ name: string, ok: boolean, message: string }}
 */
function checkSUGeneration(type, blocks) {
    const countById = {}
    for (const b of blocks) {
        countById[b.id] = (countById[b.id] || 0) + 1
    }

    // Подсчёт генерации SU
    let generated = 0
    for (const [id, su] of Object.entries(SU_SOURCES)) {
        if (countById[id]) {
            if (id === 'create:windmill_bearing') {
                // Грубая оценка: 1 подшипник + количество белой шерсти рядом
                const sailBlocks = (countById['minecraft:white_wool'] || 0)
                                 + (countById['minecraft:gray_wool'] || 0)
                                 + (countById['minecraft:light_gray_wool'] || 0)
                generated += Math.min(sailBlocks * 16, 1024) // max 1024 SU
            } else {
                generated += countById[id] * su
            }
        }
    }

    // Подсчёт потребления SU
    let consumed = 0
    for (const [id, su] of Object.entries(SU_CONSUMERS)) {
        if (countById[id]) {
            consumed += countById[id] * su
        }
    }

    const balance = generated - consumed
    const minBalance = MIN_SU_BALANCE[type] || 0

    const lines = [
        `Генерация: ~${generated} SU`,
        `Потребление: ~${consumed} SU`,
        `Баланс: ~${balance} SU (требуется минимум ${minBalance})`,
    ]

    if (balance < minBalance) {
        return {
            name: 'su_generation',
            ok: false,
            message: `Недостаточно мощности. ${lines.join(' | ')}.\n` +
                     `Добавь источник SU (водяное колесо, паровой двигатель и т.д.).`
        }
    }

    return {
        name: 'su_generation',
        ok: true,
        message: `Мощность достаточна. ${lines.join(' | ')}.`
    }
}
