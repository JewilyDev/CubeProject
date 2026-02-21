package com.example.immersiveciv;

import com.example.immersiveciv.network.ClientNetworking;
import com.example.immersiveciv.network.ModMessages;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class ImmervisecivClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ModMessages.REQUEST_RENDER, (client, handler, buf, responseSender) -> {
            String jsonStr = buf.readUtf(1024 * 1024 * 5);
            // Выполняем в главном потоке клиента (иначе OpenGL крашнется)
            client.execute(() -> {
                ClientNetworking.handleRenderRequest(jsonStr);
            });
        });
    }
}