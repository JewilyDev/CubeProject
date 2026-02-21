package com.example.immersiveciv.network;

import net.minecraft.resources.ResourceLocation;

public class ModMessages {
    // S2C (Сервер -> Клиент): запрос на рендер картинок
    public static final ResourceLocation REQUEST_RENDER = new ResourceLocation("immervise-civ", "request_render");

    // C2S (Клиент -> Сервер): ответ с готовыми картинками Base64
    public static final ResourceLocation RENDER_RESULT = new ResourceLocation("immervise-civ", "render_result");
}