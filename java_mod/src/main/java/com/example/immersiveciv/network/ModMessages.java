package com.example.immersiveciv.network;

import net.minecraft.resources.ResourceLocation;

public class ModMessages {

    // ── Рендеринг ─────────────────────────────────────────────────────────────
    // S2C: сервер просит клиента отрендерить здание
    public static final ResourceLocation REQUEST_RENDER =
            new ResourceLocation("immervise-civ", "request_render");

    // C2S: клиент отправляет готовые base64-PNG на сервер
    public static final ResourceLocation RENDER_RESULT =
            new ResourceLocation("immervise-civ", "render_result");

    // ── Реестр зданий ─────────────────────────────────────────────────────────
    // S2C: полный синк реестра (при логине)
    public static final ResourceLocation SYNC_BUILDINGS =
            new ResourceLocation("immervise-civ", "sync_buildings");

    // S2C: новое здание зарегистрировано (broadcast всем онлайн)
    public static final ResourceLocation BUILDING_REGISTERED =
            new ResourceLocation("immervise-civ", "building_registered");

    // S2C: здание снесено
    public static final ResourceLocation BUILDING_REMOVED =
            new ResourceLocation("immervise-civ", "building_removed");

    // C2S: снос здания (клиент → сервер, из CityMapScreen)
    public static final ResourceLocation DEMOLISH_BUILDING =
            new ResourceLocation("immervise-civ", "demolish_building");

    // S2C: открыть экран городской карты
    public static final ResourceLocation OPEN_CITY_MAP =
            new ResourceLocation("immervise-civ", "open_city_map");
}
