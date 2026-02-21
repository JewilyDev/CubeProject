// ImmersiveCiv — Фаза 1.4: Обработка validate_result от Middleware
// Middleware отправляет validate_result обратно через WebSocket.
// Java-мод получает пакет и выполняет серверную команду /civresult <json>
// (см. ResultCommand.java), которую перехватывает этот скрипт.

// ─── Команда /freebuild ───────────────────────────────────────────────────────
// Триггер свободной постройки: не требует типа, отправляет building_type="free"

ServerEvents.commandRegistry(event => {
    const { commands: Commands, arguments: Arguments } = event

    // /freebuild [radius]
    event.register(
        Commands.literal('freebuild')
            .requires(src => src.hasPermission(0))
            .executes(ctx => {
                triggerValidation(ctx.source.player, 'free', 12)
                return 1
            })
            .then(
                Commands.argument('radius', Arguments.INTEGER.create(event))
                    .executes(ctx => {
                        const radius = Arguments.INTEGER.getResult(ctx, 'radius')
                        triggerValidation(ctx.source.player, 'free', radius)
                        return 1
                    })
            )
    )

    // /civresult <json> — внутренняя команда, вызывается Java-модом при получении
    // пакета validate_result от Middleware. Игроки не используют её напрямую.
    event.register(
        Commands.literal('civresult')
            .requires(src => src.hasPermission(2))  // только сервер
            .then(
                Commands.argument('payload', Arguments.STRING.create(event))
                    .executes(ctx => {
                        const raw = Arguments.STRING.getResult(ctx, 'payload')
                        handleValidateResult(ctx.source.server, raw)
                        return 1
                    })
            )
    )
})

// ─── Обработчик результата от Middleware ──────────────────────────────────────

/**
 * Разбирает JSON пакет validate_result и выводит детальный feedback
 * нужному игроку.
 *
 * @param {Internal.MinecraftServer} server
 * @param {string} raw - JSON строка ValidationReport
 */
function handleValidateResult(server, raw) {
    let report
    try {
        report = JSON.parse(raw)
    } catch (e) {
        console.error('[ImmersiveCiv] Ошибка парсинга civresult: ' + e)
        return
    }

    const playerName = report.player
    const player     = server.playerList.getPlayerByName(playerName)
    if (!player) {
        // Игрок оффлайн — просто логируем
        console.log(`[ImmersiveCiv] validate_result для оффлайн-игрока: ${playerName}`)
        return
    }

    // Выводим все строки feedback от агрегатора
    const lines = report.summary_lines || []
    for (const line of lines) {
        player.tell(line)
    }

    // Если прогресс-бар уже в summary_lines — дополнительный вывод не нужен.
    // Но если нет VLM (провал по техпроверкам) — краткий итог
    if (!report.overall_passed && lines.length === 0) {
        player.tell('§c[ImmersiveCiv] Здание не принято.')
    }

    // При успехе — эффекты (звук, частицы)
    if (report.overall_passed) {
        grantBuildingSuccess(player, report)
    }

    // Бонус за свободную постройку
    if (report.is_free_build && report.free_bonus) {
        grantFreeBonus(player, report)
    }
}

// ─── Награды ─────────────────────────────────────────────────────────────────

/**
 * Выдаёт эффекты и уведомления при успешной валидации обычного здания.
 */
function grantBuildingSuccess(player, report) {
    const type  = report.building_type
    const score = report.vlm_score

    // Звуковой эффект одобрения
    player.playNotifySound('minecraft:ui.toast.challenge_complete', 'master', 1.0, 1.0)

    // Заголовок на экране (title + subtitle)
    player.server.runCommandSilent(
        `title ${player.name.string} title {"text":"Здание принято!","color":"green","bold":true}`
    )
    const subtitle = score !== null
        ? `{"text":"${prettifyType(type)} — оценка ${score}/100","color":"yellow"}`
        : `{"text":"${prettifyType(type)} — зарегистрировано","color":"yellow"}`
    player.server.runCommandSilent(
        `title ${player.name.string} subtitle ${subtitle}`
    )

    // TODO Фаза 1.6: разблокировать рецепты через CityState
}

/**
 * Малый бонус за свободную постройку: опыт.
 */
function grantFreeBonus(player, report) {
    const score    = report.vlm_score || 0
    const xpAmount = Math.floor(score / 10) * 5   // 5–50 XP в зависимости от оценки
    player.giveExperiencePoints(xpAmount)
    player.tell(`§6[ImmersiveCiv] Бонус за творчество: +${xpAmount} XP`)
    player.playNotifySound('minecraft:entity.experience_orb.pickup', 'master', 1.0, 1.2)
}

// ─── Вспомогательные ─────────────────────────────────────────────────────────

const TYPE_NAMES = {
    well: 'Колодец', farm: 'Ферма', barn: 'Амбар', bakery: 'Пекарня',
    tavern: 'Таверна', greenhouse: 'Теплица',
    windmill: 'Мельница', foundry: 'Кузница', press: 'Пресс',
    assembly: 'Сборочная линия', depot: 'Депо',
    watchtower: 'Сторожевая башня', barracks: 'Казарма',
    armory: 'Оружейная', outpost: 'Застава',
    library: 'Библиотека', alchemylab: 'Лаборатория', mage_tower: 'Башня мага',
    observatory: 'Обсерватория', archive: 'Архив',
    town_hall: 'Ратуша', bank: 'Банк', market: 'Рыночная площадь',
    free: 'Свободная постройка',
}

function prettifyType(type) {
    return TYPE_NAMES[type] || type
}
