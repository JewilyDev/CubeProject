// ImmersiveCiv — Blueprint Handler
// ─────────────────────────────────────────────────────────────────────────────
//  ЛКМ по блоку     → Угол 1
//  ПКМ по блоку     → Угол 2 → авто-скан (если Угол 1 уже задан)
//  Shift + ПКМ      → Смена типа здания (цикл по BUILDING_TYPES)
// ─────────────────────────────────────────────────────────────────────────────

const BLUEPRINT = 'immersiveciv:blueprint'

const BUILDING_TYPES = [
    'free',
    'forge', 'farm', 'barn', 'bakery', 'tavern', 'greenhouse',
    'windmill', 'foundry', 'press', 'assembly', 'depot',
    'watchtower', 'barracks', 'armory', 'outpost',
    'library', 'alchemylab', 'mage_tower', 'observatory', 'archive',
    'town_hall', 'bank', 'market'
]

// ─── Вспомогательные ─────────────────────────────────────────────────────────

function getTypeIndex(player) {
    var idx = player.persistentData.getInt('civTypeIdx')
    if (idx < 0 || idx >= BUILDING_TYPES.length) return 0
    return idx
}

function getCurrentType(player) {
    return BUILDING_TYPES[getTypeIndex(player)]
}

function showCurrentType(player) {
    var idx = getTypeIndex(player)
    var type = BUILDING_TYPES[idx]
    player.tell('§e[Чертёж] Тип: §b' + type + ' §7(' + (idx + 1) + '/' + BUILDING_TYPES.length + ')  §8Shift+ПКМ — следующий')
}

// ─── ЛКМ по блоку: Угол 1 ────────────────────────────────────────────────────

BlockEvents.leftClicked(function(event) {
    if (event.item.id !== BLUEPRINT) return
    event.cancel()

    var pos = event.block.pos
    event.player.persistentData.putIntArray('civPos1', [pos.x, pos.y, pos.z])
    event.player.persistentData.remove('civPos2')

    var type = getCurrentType(event.player)
    event.player.tell('§a◆ Угол 1: §f' + pos.x + ', ' + pos.y + ', ' + pos.z + '  §7| Тип: §e' + type + '  §8(ПКМ — Угол 2)')
})

// ─── ПКМ по блоку: Угол 2 + запуск (только без Shift) ───────────────────────

BlockEvents.rightClicked(function(event) {
    if (event.item.id !== BLUEPRINT) return
    if (event.player.isCrouching()) return  // Shift+ПКМ → обрабатывается в ItemEvents
    event.cancel()

    var player = event.player
    var pos = event.block.pos

    if (!player.persistentData.contains('civPos1')) {
        player.tell('§c[Чертёж] Сначала установите Угол 1 (ЛКМ по блоку)!')
        return
    }

    player.persistentData.putIntArray('civPos2', [pos.x, pos.y, pos.z])
    player.tell('§a◆ Угол 2: §f' + pos.x + ', ' + pos.y + ', ' + pos.z + '  §7Запускаю…')

    triggerBlueprintScan(player)
})

// ─── Shift + ПКМ: смена типа здания ─────────────────────────────────────────

ItemEvents.rightClicked(BLUEPRINT, function(event) {
    if (!event.player.isCrouching()) return
    event.cancel()

    var pData = event.player.persistentData
    var next = (getTypeIndex(event.player) + 1) % BUILDING_TYPES.length
    pData.putInt('civTypeIdx', next)
    showCurrentType(event.player)
})

// ─── Запуск сканирования ──────────────────────────────────────────────────────

function triggerBlueprintScan(player) {
    var pData = player.persistentData
    var p1 = pData.getIntArray('civPos1')
    var p2 = pData.getIntArray('civPos2')
    var type = getCurrentType(player)

    // ── Проверка requires_technologies ───────────────────────────────────────
    if (type !== 'free') {
        var GameConfig      = Java.type('com.example.immersiveciv.config.GameConfig')
        var BuildingRegistry = Java.type('com.example.immersiveciv.registry.BuildingRegistry')
        var def = GameConfig.getBuilding(type)

        if (def != null && def.requiresTechnologies.size() > 0) {
            var unlocked = BuildingRegistry.getUnlockedTechIds(player.server)
            var missing  = []

            for (var ti = 0; ti < def.requiresTechnologies.size(); ti++) {
                var techId = def.requiresTechnologies.get(ti)
                if (!unlocked.contains(techId)) {
                    var techDef  = GameConfig.getTech(techId)
                    var techName = (techDef != null) ? techDef.displayName : techId
                    missing.push('§e' + techName + '§c')
                }
            }

            if (missing.length > 0) {
                player.tell('§c[ImmersiveCiv] ✘ Нельзя строить «§e' + type + '§c»!')
                player.tell('§cНе открыты технологии: ' + missing.join('§7, '))
                player.tell('§7Постройте нужные здания или используйте §f/research <tech>§7.')
                // Не сбрасываем углы — игрок может сменить тип здания и попробовать снова
                return
            }
        }
    }

    player.tell('§e[ImmersiveCiv] Проверяем «' + type + '»…  §7(' + p1[0] + ',' + p1[1] + ',' + p1[2] + ') → (' + p2[0] + ',' + p2[1] + ',' + p2[2] + ')')

    // Сканируем блоки для технических проверок
    var level = player.level
    var blocks = scanRegionBox(level, p1[0], p1[1], p1[2], p2[0], p2[1], p2[2])

    // Центр и радиус для runTechChecks (checkPerimeter использует их)
    var cx = Math.floor((p1[0] + p2[0]) / 2)
    var cy = Math.floor((p1[1] + p2[1]) / 2)
    var cz = Math.floor((p1[2] + p2[2]) / 2)
    var radius = Math.max(
        Math.abs(p2[0] - p1[0]),
        Math.abs(p2[1] - p1[1]),
        Math.abs(p2[2] - p1[2])
    ) / 2 + 1

    // Технические проверки (функции из validate_trigger.js)
    var techResult = runTechChecks(player, type, blocks, cx, cy, cz, radius)
    reportTechResult(player, type, techResult)

    // Формируем meta для Java-команды /scan
    var techChecksMapped = []
    for (var i = 0; i < techResult.checks.length; i++) {
        var c = techResult.checks[i]
        techChecksMapped.push({ name: c.name, ok: c.ok, message: c.message })
    }

    var meta = JSON.stringify({
        building_type: type,
        tech_passed:   techResult.passed,
        player:        player.username,
        tech_checks:   techChecksMapped
    })

    // /scan <x1> <y1> <z1> <x2> <y2> <z2> <label>
    var cmd = 'scan ' + p1[0] + ' ' + p1[1] + ' ' + p1[2]
             + ' ' + p2[0] + ' ' + p2[1] + ' ' + p2[2]
             + ' ' + meta

    player.server.runCommandSilent(cmd)

    // Сброс выделения (игрок должен заново выбрать регион для следующей проверки)
    pData.remove('civPos1')
    pData.remove('civPos2')
}

// ─── Сканирование блоков в боксе ─────────────────────────────────────────────

function scanRegionBox(level, x1, y1, z1, x2, y2, z2) {
    var blocks = []
    var minX = Math.min(x1, x2), maxX = Math.max(x1, x2)
    var minY = Math.min(y1, y2), maxY = Math.max(y1, y2)
    var minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2)

    for (var x = minX; x <= maxX; x++) {
        for (var y = minY; y <= maxY; y++) {
            for (var z = minZ; z <= maxZ; z++) {
                var block = level.getBlock(x, y, z)
                if (block.id === 'minecraft:air'
                    || block.id === 'minecraft:cave_air'
                    || block.id === 'minecraft:void_air') continue
                blocks.push({ x: x, y: y, z: z, id: block.id, props: block.properties })
            }
        }
    }
    return blocks
}
