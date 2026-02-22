// ImmersiveCiv — Проверка мощности SU (Stress Units, мод Create)
// Используется для инженерных построек: windmill, foundry, press, assembly, depot.
//
// Create не предоставляет KubeJS API для чтения SU-сети, поэтому делаем
// структурную оценку по наличию источников и потребителей в регионе.
//
// Примечание: min_su_balance для каждого здания можно вынести в buildings.json
//             при необходимости — сейчас хранится здесь для простоты.
// ─────────────────────────────────────────────────────────────────────────────

// Ориентировочные SU от источников (Create 0.5, упрощённые значения)
var SU_SOURCES = {
    'create:water_wheel':       256,
    'create:large_water_wheel': 768,
    'create:windmill_bearing':  0,    // зависит от паруса — считаем отдельно
    'create:hand_crank':        32,
    'create:furnace_engine':    1024,
    'create:steam_engine':      1024,
    'create:flywheel':          0     // накопитель, не источник
}

// Потребление SU у машин
var SU_CONSUMERS = {
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
    'create:mechanical_belt':      2
}

// Минимальный баланс SU (генерация − потребление) для каждого типа здания
var MIN_SU_BALANCE = {
    windmill:  128,
    foundry:   256,
    press:     512,
    assembly:  384,
    depot:     128
}

/**
 * Проверяет структурную мощность SU для инженерного здания.
 *
 * @param {string} type
 * @param {Array}  blocks — массив { id: string }
 * @returns {{ name: string, ok: boolean, message: string }}
 */
function checkSUGeneration(type, blocks) {
    // ── Подсчёт блоков ────────────────────────────────────────────────────────
    var countById = {}
    for (var bi = 0; bi < blocks.length; bi++) {
        var id = blocks[bi].id
        countById[id] = (countById[id] || 0) + 1
    }

    // ── Генерация SU ──────────────────────────────────────────────────────────
    var generated = 0
    var srcKeys = Object.keys(SU_SOURCES)

    for (var si = 0; si < srcKeys.length; si++) {
        var srcId = srcKeys[si]
        var srcSU = SU_SOURCES[srcId]

        if (!countById[srcId]) continue

        if (srcId === 'create:windmill_bearing') {
            // Грубая оценка: кол-во паруса × 16 SU (max 1024)
            var sails = (countById['minecraft:white_wool']      || 0)
                      + (countById['minecraft:gray_wool']       || 0)
                      + (countById['minecraft:light_gray_wool'] || 0)
            generated += Math.min(sails * 16, 1024)
        } else {
            generated += countById[srcId] * srcSU
        }
    }

    // ── Потребление SU ────────────────────────────────────────────────────────
    var consumed = 0
    var conKeys = Object.keys(SU_CONSUMERS)

    for (var ci = 0; ci < conKeys.length; ci++) {
        var conId = conKeys[ci]
        var conSU = SU_CONSUMERS[conId]
        if (countById[conId]) {
            consumed += countById[conId] * conSU
        }
    }

    // ── Результат ─────────────────────────────────────────────────────────────
    var balance    = generated - consumed
    var minBalance = MIN_SU_BALANCE[type] || 0
    var summary    = 'Генерация: ~' + generated + ' SU | Потребление: ~' + consumed
                   + ' SU | Баланс: ~' + balance + ' SU (минимум ' + minBalance + ')'

    if (balance < minBalance) {
        return {
            name:    'su_generation',
            ok:      false,
            message: 'Недостаточно мощности. ' + summary + '.\n'
                   + 'Добавь источник SU (водяное колесо, паровой двигатель и т.д.).'
        }
    }

    return {
        name:    'su_generation',
        ok:      true,
        message: 'Мощность достаточна. ' + summary + '.'
    }
}
