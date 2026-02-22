package com.example.immersiveciv.network;

import com.example.immersiveciv.render.StructureRenderer;
import com.example.immersiveciv.render.VirtualBlockView;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.Map;

public class ClientNetworking {

    public static void handleRenderRequest(String jsonStr) {
        JsonObject payload = JsonParser.parseString(jsonStr).getAsJsonObject();
        JsonArray blocksArr = payload.getAsJsonArray("blocks");
        int radius = 10; // Значение по умолчанию
        if (payload.has("radius")) {
            radius = payload.get("radius").getAsInt();
        }

        Map<BlockPos, BlockState> blocks = new HashMap<>();
        Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();

        for (JsonElement el : blocksArr) {
            JsonObject bObj = el.getAsJsonObject();
            BlockPos pos = new BlockPos(bObj.get("x").getAsInt(), bObj.get("y").getAsInt(), bObj.get("z").getAsInt());
            ResourceLocation id = new ResourceLocation(bObj.get("id").getAsString());
            Block block = BuiltInRegistries.BLOCK.get(id);
            BlockState state = block.defaultBlockState();

            // Восстанавливаем ориентацию блоков (направление ступенек, сундуков и т.д.)
            if (bObj.has("props")) {
                JsonObject props = bObj.getAsJsonObject("props");
                for (Property<?> prop : state.getProperties()) {
                    if (props.has(prop.getName())) {
                        state = parseProperty(state, prop, props.get(prop.getName()).getAsString());
                    }
                }
            }
            blocks.put(pos, state);
            // BlockEntity (сундуки) можно инстанцировать здесь, если нужно,
            // но для GPT обычно хватает BlockState.
        }

        VirtualBlockView view = new VirtualBlockView(blocks, blockEntities);

        // Магия рендера: три ракурса!
        String topDown = StructureRenderer.renderToB64(view, 90f, 0f, radius);
        String isoNe = StructureRenderer.renderToB64(view, 35.264f, 45f, radius);
        String isoSw = StructureRenderer.renderToB64(view, 35.264f, 225f, radius);

        payload.addProperty("image_top_down", topDown);
        payload.addProperty("image_iso_ne", isoNe);
        payload.addProperty("image_iso_sw", isoSw);

        // Отправляем обратно на сервер
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(payload.toString(), 1024 * 1024 * 10);
        ClientPlayNetworking.send(ModMessages.RENDER_RESULT, buf);
    }

    private static <T extends Comparable<T>> BlockState parseProperty(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }
}