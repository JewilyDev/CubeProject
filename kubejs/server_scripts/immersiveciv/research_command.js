// ImmersiveCiv — Команда /research
// Позволяет игроку вручную исследовать технологию, потратив предметы.
//
// Использование: /research <tech_id>
// Пример:        /research engineering
//
// Технология открывается если:
//   а) Уже построено здание, которое её открывает (автоматически), ИЛИ
//   б) Игрок выполняет /research и отдаёт предметы из research_cost
//
// Если у технологии research_cost пустой — значит она открывается
// только через здание (ручной ресёрч для неё не нужен).
// ─────────────────────────────────────────────────────────────────────────────

var ALL_TECH_IDS = [
    'agriculture', 'metallurgy', 'engineering',
    'military', 'education', 'commerce', 'governance'
]

ServerEvents.commandRegistry(function(event) {
    var Commands  = event.commands
    var Arguments = event.arguments

    event.register(
        Commands.literal('research')
            .then(
                Commands.argument('tech', Arguments.STRING.create(event))
                    .suggests(function(ctx, builder) {
                        for (var i = 0; i < ALL_TECH_IDS.length; i++) {
                            builder.suggest(ALL_TECH_IDS[i])
                        }
                        return builder.buildFuture()
                    })
                    .executes(function(ctx) {
                        var player = ctx.source.player
                        if (player == null) {
                            ctx.source.sendFailure(
                                net.minecraft.network.chat.Component.literal('Команда только для игроков.'))
                            return 0
                        }
                        doResearch(player, Arguments.STRING.getResult(ctx, 'tech'))
                        return 1
                    })
            )
    )
})

// ─── Логика ресёрча ───────────────────────────────────────────────────────────

function doResearch(player, techId) {
    var GameConfig    = Java.type('com.example.immersiveciv.config.GameConfig')
    var TechRegistry  = Java.type('com.example.immersiveciv.registry.TechRegistry')
    var BuildingReg   = Java.type('com.example.immersiveciv.registry.BuildingRegistry')

    var techDef = GameConfig.getTech(techId)

    // ── Существует ли такая технология? ──────────────────────────────────────
    if (techDef == null) {
        player.tell('§c[Ресёрч] Неизвестная технология: «' + techId + '».')
        player.tell('§7Доступные: §e' + ALL_TECH_IDS.join('§7, §e'))
        return
    }

    var techName = techDef.displayName

    // ── Уже открыта? ─────────────────────────────────────────────────────────
    var unlocked = BuildingReg.getUnlockedTechIds(player.server)
    if (unlocked.contains(techId)) {
        player.tell('§a[Ресёрч] Технология «§e' + techName + '§a» уже открыта!')
        return
    }

    // ── Есть ли вообще стоимость ресёрча? ────────────────────────────────────
    var cost = techDef.researchCost
    if (cost == null || cost.size() === 0) {
        player.tell('§c[Ресёрч] «' + techName + '» нельзя исследовать вручную.')
        player.tell('§7Эта технология открывается только через постройку нужного здания.')
        return
    }

    // ── Показать стоимость при вызове команды ─────────────────────────────────
    // Проверяем нехватку ДО изъятия предметов
    var lacking = []
    for (var ci = 0; ci < cost.size(); ci++) {
        var entry   = cost.get(ci)
        var inInv   = countItem(player, entry.item)
        if (inInv < entry.amount) {
            lacking.push('§e' + entry.label + '§c: ' + inInv + '/' + entry.amount)
        }
    }

    if (lacking.length > 0) {
        player.tell('§c[Ресёрч] ✘ Не хватает предметов для «§e' + techName + '§c»:')
        for (var li = 0; li < lacking.length; li++) {
            player.tell('  §c• ' + lacking[li])
        }
        return
    }

    // ── Изымаем предметы ─────────────────────────────────────────────────────
    for (var ci = 0; ci < cost.size(); ci++) {
        var entry = cost.get(ci)
        consumeItem(player, entry.item, entry.amount)
    }

    // ── Отмечаем технологию как исследованную ────────────────────────────────
    TechRegistry.get(player.server).markResearched(techId)

    // ── Успех ────────────────────────────────────────────────────────────────
    player.tell('§a[Ресёрч] ✔ Технология «§e' + techName + '§a» успешно исследована!')

    var unlocks = techDef.unlocksBuildingsIds
    if (unlocks != null && unlocks.size() > 0) {
        var names = []
        for (var ui = 0; ui < unlocks.size(); ui++) {
            var bDef = GameConfig.getBuilding(unlocks.get(ui))
            names.push(bDef != null ? '§e' + bDef.displayName + '§7' : unlocks.get(ui))
        }
        player.tell('§7Теперь доступны здания: ' + names.join('§7, '))
    }
}

// ─── Вспомогательные функции для работы с инвентарём ─────────────────────────

/** Считает суммарное количество предмета во всём инвентаре игрока. */
function countItem(player, itemId) {
    var inv   = player.inventory
    var total = 0
    for (var i = 0; i < inv.containerSize; i++) {
        var stack = inv.getItem(i)
        if (stack != null && stack.id === itemId) {
            total += stack.count
        }
    }
    return total
}

/**
 * Изымает нужное количество предмета из инвентаря.
 * Проходит по слотам и уменьшает стаки пока не наберёт needed.
 */
function consumeItem(player, itemId, needed) {
    var inv = player.inventory
    for (var i = 0; i < inv.containerSize && needed > 0; i++) {
        var stack = inv.getItem(i)
        if (stack != null && stack.id === itemId) {
            var take = Math.min(stack.count, needed)
            stack.shrink(take)
            needed -= take
        }
    }
}
