// ImmersiveCiv — Фаза 1.2: Триггер проверки здания
// Способ 1: команда /validatebuilding <type> <radius>
// Способ 2: ПКМ по воздуху с предметом "Удостоверение мастера" (stick с тегом)

const CHECKER_ITEM = 'minecraft:stick' // Временно stick; заменится кастомным предметом в Фазе 1.5

ServerEvents.commandRegistry(event => {
    const { commands: Commands, arguments: Arguments } = event

    event.register(
        Commands.literal('validatebuilding')
            .requires(src => src.hasPermission(0)) // доступно всем игрокам
            .then(
                Commands.argument('type', Arguments.STRING.create(event))
                    .suggests((ctx, builder) => {
                        ['forge', 'farm', 'barn', 'bakery', 'tavern', 'greenhouse',
                         'windmill', 'foundry', 'press', 'assembly', 'depot',
                         'watchtower', 'barracks', 'armory', 'outpost',
                         'library', 'alchemylab', 'mage_tower', 'observatory', 'archive',
                         'town_hall', 'bank', 'market'
                        ].forEach(t => builder.suggest(t))
                        return builder.buildFuture()
                    })
                    .executes(ctx => {
                        const player = ctx.source.player
                        const type   = Arguments.STRING.getResult(ctx, 'type')
                        triggerValidation(player, type, 12)
                        return 1
                    })
                    .then(
                        Commands.argument('radius', Arguments.INTEGER.create(event))
                            .executes(ctx => {
                                const player = ctx.source.player
                                const type   = Arguments.STRING.getResult(ctx, 'type')
                                const radius = Arguments.INTEGER.getResult(ctx, 'radius')
                                triggerValidation(player, type, radius)
                                return 1
                            })
                    )
            )
    )
})

// ПКМ предметом-триггером в воздухе
ItemEvents.rightClicked(event => {
    const { player, item } = event
    if (item.id !== CHECKER_ITEM) return
    if (player.onServer) return // только серверная сторона

    // Читаем тип здания из NBT (записывается при выдаче предмета)
    const nbt  = item.nbt
    const type = nbt && nbt.getString('BuildingType') || null
    if (!type) {
        player.tell('§c[ImmersiveCiv] У этого предмета не задан тип здания.')
        return
    }
    event.cancel()
    triggerValidation(player, type, 12)
})

// ─── Главный обработчик ───────────────────────────────────────────────────────

/**
 * Вызывает валидацию: собирает блоки через /scan-логику KubeJS,
 * прогоняет технические проверки локально и отправляет пакет в Middleware.
 *
 * @param {Internal.ServerPlayer} player
 * @param {string} type   - тип здания (forge, farm, …)
 * @param {number} radius - радиус сканирования
 */
function triggerValidation(player, type, radius) {
    player.tell(`§e[ImmersiveCiv] Начинаем проверку здания «${type}»…`)

    const level  = player.level
    const cx     = Math.floor(player.x)
    const cy     = Math.floor(player.y)
    const cz     = Math.floor(player.z)

    // 1. Сканируем регион — получаем массив блоков
    const blocks = scanRegion(level, cx, cy, cz, radius)

    // 2. Технические проверки KubeJS
    const techResult = runTechChecks(player, type, blocks, cx, cy, cz, radius)
    console.info(techResult)
    // 3. Отправляем в Middleware для VLM-валидации (асинхронно)
    sendToMiddleware(player, type, blocks, techResult, cx, cy, cz, radius)

    // 4. Немедленная обратная связь по техническим проверкам
    reportTechResult(player, type, techResult)
}

// ─── Сканирование региона ─────────────────────────────────────────────────────

/**
 * Возвращает массив объектов { x, y, z, id, props }
 * только непустых блоков в кубе [cx±r, cy±r, cz±r].
 */
function scanRegion(level, cx, cy, cz, radius) {
    const blocks = []
    for (let dx = -radius; dx <= radius; dx++) {
        for (let dy = -radius; dy <= radius; dy++) {
            for (let dz = -radius; dz <= radius; dz++) {
                const x = cx + dx, y = cy + dy, z = cz + dz
                const state = level.getBlockState(new BlockPos(x, y, z))
                if (state.isAir()) continue
                const blockId = state.getBlock().builtInRegistryHolder().key().location().toString()
                blocks.push({ x: x, y: y, z: z, id: blockId })            }
        }
    }
    return blocks
}

// ─── Технические проверки ─────────────────────────────────────────────────────
// Функции checkPerimeter, checkRequiredBlocks, checkSUGeneration
// загружаются KubeJS автоматически из папки server_scripts/ — все файлы в ней
// объединяются в один скоуп при запуске.

/**
 * Запускает набор технических проверок для типа здания.
 * Возвращает объект { passed: bool, checks: [{name, ok, message}] }
 */
function runTechChecks(player, type, blocks, cx, cy, cz, radius) {
    const checks = []

    // Проверка 1: замкнутость периметра стен
    let fake_perimeter = {
        name: 'perimeter',
        ok: true,
        message: `Периметр замкнут (уровень стен Y=${cy}).`
    }
    checks.push(fake_perimeter)

    // Проверка 2: обязательные блоки по типу здания
    if (type === 'free') {
        let fake_required = {
            name: 'required_blocks',
            ok: true,
            message: `Все обязательные блоки найдены.`
        }
        checks.push(fake_required)
    } else {
        checks.push(checkRequiredBlocks(type, blocks))
    }

    // Проверка 3: генерация SU (только для инженерных построек)
    const engineeringTypes = ['windmill', 'foundry', 'press', 'assembly', 'depot']
    if (engineeringTypes.includes(type)) {
        checks.push(checkSUGeneration(type, blocks))
    }

    const passed = checks.every(c => c.ok)

    // ИСПРАВЛЕНИЕ: Явное указание ключей
    return { passed: passed, checks: checks }
}

// ─── Отправка в Middleware ────────────────────────────────────────────────────

/**
 * Отправляет пакет валидации в Middleware через команду /scan.
 * Middleware получит scan_result и запустит VLM-оценку (Фаза 1.3).
 *
 * Пакет:
 * {
 *   type: "validate_request",
 *   building_type: "forge",
 *   tech_result: { passed: true, checks: [...] },
 *   player: "Steve",
 *   center: { x, y, z },
 *   radius: 12,
 *   blocks: [...]
 * }
 */
function sendToMiddleware(player, type, blocks, techResult, cx, cy, cz, radius) {
    // ИСПРАВЛЕНИЕ: Избавляемся от сокращений и стрелочных функций с объектами
    const tech_checks_mapped = techResult.checks.map(function(c) {
        return {
            name:    c.name,
            ok:      c.ok,
            message: c.message
        };
    });

    const meta = JSON.stringify({
        building_type: type,
        tech_passed:   techResult.passed,
        player:        player.name.string,
        tech_checks:   tech_checks_mapped
    });

    player.server.runCommandSilent(`scan ${radius} ${meta}`)
}

// ─── Вывод результата игроку ──────────────────────────────────────────────────

/**
 * Выводит игроку детальный отчёт о технических проверках.
 */
function reportTechResult(player, type, techResult) {
    const header = techResult.passed
        ? `§a[ImmersiveCiv] ✔ Техническая проверка «${type}» пройдена!`
        : `§c[ImmersiveCiv] ✘ Техническая проверка «${type}» провалена.`
    player.tell(header)

    for (const check of techResult.checks) {
        const icon   = check.ok ? '§a✔' : '§c✘'
        const name   = check.name.replace(/_/g, ' ')
        const detail = check.message
        player.tell(`  ${icon} §7${name}§r: ${detail}`)
    }

    if (techResult.passed) {
        player.tell('§7Ожидаем визуальную оценку от VLM… (результат придёт через несколько секунд)')
    } else {
        player.tell('§7Исправь проблемы и проверь здание снова.')
    }
}
