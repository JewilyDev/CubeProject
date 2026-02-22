package com.example.immersiveciv;

import com.example.immersiveciv.network.ClientNetworking;
import com.example.immersiveciv.network.ModMessages;
import com.example.immersiveciv.registry.BuildingRecord;
import com.example.immersiveciv.registry.ClientBuildingRegistry;
import com.example.immersiveciv.screen.CityMapScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public class ImmervisecivClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        // ── Прокси: открыть CityMapScreen из CityMapItem.use() ───────────────
        Immerviseciv.openCityMapScreen = () ->
                Minecraft.getInstance().setScreen(new CityMapScreen());

        // ── S2C: запрос на рендер ─────────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(ModMessages.REQUEST_RENDER,
                (client, handler, buf, responseSender) -> {
                    String jsonStr = buf.readUtf(1024 * 1024 * 5);
                    client.execute(() -> ClientNetworking.handleRenderRequest(jsonStr));
                });

        // ── S2C: открыть CityMapScreen ────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(ModMessages.OPEN_CITY_MAP,
                (client, handler, buf, responseSender) ->
                        client.execute(() -> client.setScreen(new CityMapScreen())));

        // ── S2C: полный синк реестра (при логине) ─────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(ModMessages.SYNC_BUILDINGS,
                (client, handler, buf, responseSender) -> {
                    int count = buf.readVarInt();
                    List<BuildingRecord> list = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        list.add(BuildingRecord.decode(buf));
                    }
                    client.execute(() -> ClientBuildingRegistry.replaceAll(list));
                });

        // ── S2C: новое здание ─────────────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(ModMessages.BUILDING_REGISTERED,
                (client, handler, buf, responseSender) -> {
                    BuildingRecord record = BuildingRecord.decode(buf);
                    client.execute(() -> {
                        ClientBuildingRegistry.put(record);
                        Immerviseciv.LOGGER.debug("[ImmersiveCiv] +здание {} ({})", record.type, record.id);
                    });
                });

        // ── S2C: здание снесено ───────────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(ModMessages.BUILDING_REMOVED,
                (client, handler, buf, responseSender) -> {
                    String id = buf.readUtf(64);
                    client.execute(() -> {
                        ClientBuildingRegistry.remove(id);
                        Immerviseciv.LOGGER.debug("[ImmersiveCiv] -здание {}", id);
                    });
                });

        // ── Очистка реестра при выходе из мира ───────────────────────────────
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ClientBuildingRegistry.clear());
    }
}
