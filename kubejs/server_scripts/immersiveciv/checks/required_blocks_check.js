// ImmersiveCiv — Проверка обязательных блоков по типу здания
// Требования читаются из buildings.json через GameConfig (Java.type).
// Никакого хардкода здесь нет — добавляй / правь блоки только в конфиге.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Проверяет наличие обязательных блоков для типа здания.
 * Данные берутся из GameConfig (buildings.json → block_requirements).
 *
 * @param {string} type   — идентификатор здания ('forge', 'farm', ...)
 * @param {Array}  blocks — массив { id: string } из scanRegionBox
 * @returns {{ name: string, ok: boolean, message: string }}
 */
function checkRequiredBlocks(type, blocks) {
    // ── Получаем конфиг из Java ───────────────────────────────────────────────
    var GameConfig = Java.type('com.example.immersiveciv.config.GameConfig')
    var def = GameConfig.getBuilding(type)

    if (def == null) {
        return {
            name:    'required_blocks',
            ok:      false,
            message: 'Неизвестный тип здания: «' + type + '». Проверь BUILDING_TYPES и buildings.json.'
        }
    }

    var reqs = def.blockRequirements

    // Нет требований — считаем пройденным
    if (reqs == null || reqs.size() === 0) {
        return {
            name:    'required_blocks',
            ok:      true,
            message: 'Нет требований к блокам для «' + type + '».'
        }
    }

    // ── Подсчитываем блоки в регионе ─────────────────────────────────────────
    var blockCounts = {}
    for (var bi = 0; bi < blocks.length; bi++) {
        var id = blocks[bi].id
        blockCounts[id] = (blockCounts[id] || 0) + 1
    }

    // ── Проверяем каждую группу требований ───────────────────────────────────
    var failed = []

    for (var ri = 0; ri < reqs.size(); ri++) {
        var req   = reqs.get(ri)
        var anyList = req.any
        var total = 0

        for (var ai = 0; ai < anyList.size(); ai++) {
            var blockId = anyList.get(ai)
            total += (blockCounts[blockId] || 0)
        }

        if (total < req.min) {
            failed.push('«' + req.label + '» — нужно ' + req.min + ', найдено ' + total)
        }
    }

    // ── Результат ─────────────────────────────────────────────────────────────
    if (failed.length > 0) {
        return {
            name:    'required_blocks',
            ok:      false,
            message: 'Не хватает блоков:\n  • ' + failed.join('\n  • ')
        }
    }

    return {
        name:    'required_blocks',
        ok:      true,
        message: 'Все обязательные блоки присутствуют (' + reqs.size() + ' групп проверено).'
    }
}
