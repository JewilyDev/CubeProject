// ImmersiveCiv — регистрация предметов (startup, выполняется один раз при запуске JVM)
// Для смены текстуры — положи PNG в kubejs/assets/immersiveciv/textures/item/blueprint.png

StartupEvents.registry('item', e => {
    e.create('immersiveciv:blueprint')
        .maxStackSize(1)
        .rarity('uncommon')
        .displayName('§6Чертёж постройки')
})
