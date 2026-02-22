/// Подключаем нативный Java-класс для "жадной" строки
const StringArgumentType = Java.loadClass('com.mojang.brigadier.arguments.StringArgumentType')

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

    // /civresult <json>
    event.register(
        Commands.literal('civresult')
            .requires(src => src.hasPermission(2))  // только сервер
            .then(
                // ИСПОЛЬЗУЕМ GREEDY STRING
                Commands.argument('payload', StringArgumentType.greedyString())
                    .executes(ctx => {
                        const raw = StringArgumentType.getString(ctx, 'payload')
                        handleValidateResult(raw)
                        return 1
                    })
            )
    )
})

function handleValidateResult(raw) {
    let report
    try {
        report = JSON.parse(raw)
    } catch (e) {
        console.error('[ImmersiveCiv] Ошибка парсинга civresult: ' + e)
        return
    }

    const playerName = report.player
    // КРИТИЧНОЕ ИСПРАВЛЕНИЕ: получаем игрока через Utils KubeJS
    const player = Utils.server.getPlayer(playerName) 
    
    if (!player) {
        console.log(`[ImmersiveCiv] validate_result для оффлайн-игрока: ${playerName}`)
        return
    }

    const lines = report.summary_lines || []
    for (const line of lines) {
        player.tell(line)
    }

    if (!report.overall_passed && lines.length === 0) {
        player.tell('§c[ImmersiveCiv] Здание не принято.')
    }

    if (report.overall_passed) {
        grantBuildingSuccess(player, report)
    }

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
