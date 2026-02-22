// ImmersiveCiv — Утилиты валидации здания
// Функции этого файла используются из click_check_handler.js (Blueprint).
// Все check-функции определены в папке checks/ и подгружаются KubeJS автоматически.
// ─────────────────────────────────────────────────────────────────────────────

// ─── Отладочная команда /validatebuilding <type> [radius] ────────────────────
// Только для операторов (permission level 2). Игроки не используют.

ServerEvents.commandRegistry(function(event) {
    var Commands  = event.commands
    var Arguments = event.arguments

    event.register(
        Commands.literal('validatebuilding')
            .requires(function(src) { return src.hasPermission(2) })
            .then(
                Commands.argument('type', Arguments.STRING.create(event))
                    .suggests(function(ctx, builder) {
                        var types = ['free', 'forge', 'farm', 'barn', 'bakery', 'tavern', 'greenhouse',
                                     'windmill', 'foundry', 'press', 'assembly', 'depot',
                                     'watchtower', 'barracks', 'armory', 'outpost',
                                     'library', 'alchemylab', 'mage_tower', 'observatory', 'archive',
                                     'town_hall', 'bank', 'market']
                        for (var i = 0; i < types.length; i++) { builder.suggest(types[i]) }
                        return builder.buildFuture()
                    })
                    .executes(function(ctx) {
                        var player = ctx.source.player
                        var type   = Arguments.STRING.getResult(ctx, 'type')
                        triggerValidation(player, type, 12)
                        return 1
                    })
                    .then(
                        Commands.argument('radius', Arguments.INTEGER.create(event))
                            .executes(function(ctx) {
                                var player = ctx.source.player
                                var type   = Arguments.STRING.getResult(ctx, 'type')
                                var radius = Arguments.INTEGER.getResult(ctx, 'radius')
                                triggerValidation(player, type, radius)
                                return 1
                            })
                    )
            )
    )
})

// ─── Точка входа для /validatebuilding (центр + радиус) ───────────────────────

function triggerValidation(player, type, radius) {
    player.tell('§e[ImmersiveCiv] Начинаем проверку здания «' + type + '»…')

    var level  = player.level
    var cx     = Math.floor(player.x)
    var cy     = Math.floor(player.y)
    var cz     = Math.floor(player.z)

    var blocks     = scanRegion(level, cx, cy, cz, radius)
    var techResult = runTechChecks(player, type, blocks, cx, cy, cz, radius)

    reportTechResult(player, type, techResult)
    sendToMiddleware(player, type, techResult, cx, cy, cz, radius)
}

// ─── Отправка в Middleware через Java /scan ───────────────────────────────────

function sendToMiddleware(player, type, techResult, cx, cy, cz, radius) {
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

    var x1 = cx - radius, y1 = cy - radius, z1 = cz - radius
    var x2 = cx + radius, y2 = cy + radius, z2 = cz + radius

    var cmd = 'scan ' + x1 + ' ' + y1 + ' ' + z1
             + ' ' + x2 + ' ' + y2 + ' ' + z2
             + ' ' + meta

    player.server.runCommandSilent(cmd)
}

// ─── Сканирование региона (центр + радиус) ────────────────────────────────────

function scanRegion(level, cx, cy, cz, radius) {
    var blocks = []
    for (var dx = -radius; dx <= radius; dx++) {
        for (var dy = -radius; dy <= radius; dy++) {
            for (var dz = -radius; dz <= radius; dz++) {
                var bx = cx + dx, by = cy + dy, bz = cz + dz
                var block = level.getBlock(bx, by, bz)
                if (block.id === 'minecraft:air'
                    || block.id === 'minecraft:cave_air'
                    || block.id === 'minecraft:void_air') continue
                blocks.push({ x: bx, y: by, z: bz, id: block.id, props: block.properties })
            }
        }
    }
    return blocks
}

// ─── Технические проверки ─────────────────────────────────────────────────────
// checkPerimeter    → checks/perimeter_check.js
// checkRequiredBlocks → checks/required_blocks_check.js  (читает из buildings.json)
// checkSUGeneration → checks/su_check.js

function runTechChecks(player, type, blocks, cx, cy, cz, radius) {
    var checks = []

    // ── 1. Периметр стен ─────────────────────────────────────────────────────
    // Читаем флаг check_perimeter из конфига здания
    var doCheckPerimeter = true  // по умолчанию включена
    try {
        var GameConfig = Java.type('com.example.immersiveciv.config.GameConfig')
        var def = GameConfig.getBuilding(type)
        if (def != null) {
            doCheckPerimeter = def.checkPerimeter
        }
    } catch (e) {
        // Если конфиг не загружен — пропускаем периметр, не ломаем всё
        doCheckPerimeter = false
        player.tell('§7[ImmersiveCiv] Конфиг не загружен, проверка периметра пропущена.')
    }

    if (doCheckPerimeter) {
        checks.push(checkPerimeter(blocks, cx, cy, cz, radius))
    } else {
        checks.push({
            name:    'perimeter',
            ok:      true,
            message: 'Проверка периметра отключена для «' + type + '».'
        })
    }

    // ── 2. Обязательные блоки ─────────────────────────────────────────────────
    if (type === 'free') {
        checks.push({ name: 'required_blocks', ok: true, message: 'Свободная постройка — блоки не проверяются.' })
    } else {
        checks.push(checkRequiredBlocks(type, blocks))
    }

    // ── 3. Мощность SU (только для инженерных построек) ─────────────────────
    var engineeringTypes = ['windmill', 'foundry', 'press', 'assembly', 'depot']
    if (engineeringTypes.indexOf(type) !== -1) {
        checks.push(checkSUGeneration(type, blocks))
    }

    // ── Итог ──────────────────────────────────────────────────────────────────
    var passed = true
    for (var i = 0; i < checks.length; i++) {
        if (!checks[i].ok) { passed = false; break }
    }

    return { passed: passed, checks: checks }
}

// ─── Вывод результата игроку ──────────────────────────────────────────────────

function reportTechResult(player, type, techResult) {
    var header = techResult.passed
        ? '§a[ImmersiveCiv] ✔ Техническая проверка «' + type + '» пройдена!'
        : '§c[ImmersiveCiv] ✘ Техническая проверка «' + type + '» провалена.'
    player.tell(header)

    for (var i = 0; i < techResult.checks.length; i++) {
        var check = techResult.checks[i]
        var icon  = check.ok ? '§a✔' : '§c✘'
        var name  = check.name.replace(/_/g, ' ')
        player.tell('  ' + icon + ' §7' + name + '§r: ' + check.message)
    }

    if (techResult.passed) {
        player.tell('§7Ожидаем визуальную оценку от VLM… (результат через несколько секунд)')
    } else {
        player.tell('§7Исправь проблемы и проверь здание снова.')
    }
}
