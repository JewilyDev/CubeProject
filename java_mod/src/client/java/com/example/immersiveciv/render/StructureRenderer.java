package com.example.immersiveciv.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4f;

import java.util.Base64;

public class StructureRenderer {

    public static String renderToB64(VirtualBlockView view, float rotateX, float rotateY, int radius) {
        Minecraft mc = Minecraft.getInstance();
        int size = 512; // 256×256 — баланс качества и скорости (≈4× быстрее 512)

        RenderTarget fbo = new TextureTarget(size, size, true, Minecraft.ON_OSX);
        fbo.setClearColor(0, 0, 0, 0); // Прозрачный фон
        fbo.clear(Minecraft.ON_OSX);

        RenderTarget oldTarget = mc.getMainRenderTarget();
        fbo.bindWrite(true);

        RenderSystem.viewport(0, 0, size, size);
        RenderSystem.clear(512, Minecraft.ON_OSX); // Очистка Depth buffer

        // Настройка ортографической камеры (Изометрия)
        float scale = radius * 1.5f + 2f;
        Matrix4f projection = new Matrix4f().setOrtho(-scale, scale, -scale, scale, -1000f, 3000f);
        RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);

        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.pushPose();
        poseStack.setIdentity();

        // Углы изометрии
        poseStack.mulPose(Axis.XP.rotationDegrees(rotateX));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotateY));

        RenderSystem.applyModelViewMatrix();
        // Затенение граней обеспечивает renderSingleBlock через модельный рендерер;
        // статическая настройка Lighting не нужна при использовании 0xF000F0 (FULL_BRIGHT)

        PoseStack worldPose = new PoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();

        // Вычисляем центр постройки по всем трём осям
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : view.blocks.keySet()) {
            minX = Math.min(minX, pos.getX()); maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY()); maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ()); maxZ = Math.max(maxZ, pos.getZ());
        }
        float cx = (minX + maxX) / 2f;
        float cy = (minY + maxY) / 2f;
        float cz = (minZ + maxZ) / 2f;

        for (BlockPos pos : view.blocks.keySet()) {
            worldPose.pushPose();
            worldPose.translate(pos.getX() - cx, pos.getY() - cy, pos.getZ() - cz);

            // Рендер простого блока
            blockRenderer.renderSingleBlock(
                    view.getBlockState(pos),
                    worldPose,
                    buffers,
                    0xF000F0, // Максимальный свет
                    OverlayTexture.NO_OVERLAY
            );

            BlockEntity be = view.getBlockEntity(pos);
            if (be != null) {
                try {
                    // Получаем диспетчер из инстанса Minecraft
                    Minecraft.getInstance().getBlockEntityRenderDispatcher().render(be, 0, worldPose, buffers);
                } catch (Exception ignored) {
                    // Create может ругаться на виртуальный мир, игнорируем падения конкретного блока
                }
            }
            worldPose.popPose();
        }

        buffers.endBatch();
        poseStack.popPose();
        RenderSystem.applyModelViewMatrix();

        // Выгружаем пиксели в массив
        NativeImage image = new NativeImage(size, size, false);
        RenderSystem.bindTexture(fbo.getColorTextureId());
        image.downloadTexture(0, false);
        image.flipY(); // OpenGL рендерит вверх ногами

        // Восстанавливаем экран игрока
        oldTarget.bindWrite(true);
        RenderSystem.viewport(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight());
        fbo.destroyBuffers();

        try {
            byte[] pngBytes = image.asByteArray();
            image.close();

            // ── DEBUG: сохраняем PNG на диск для визуальной проверки ──────────
            // Файлы появятся в папке .minecraft/render_debug/
            // Удали этот блок перед релизом или оберни в System.getProperty("immersiveciv.debug")
            try {
                java.nio.file.Path debugDir = mc.gameDirectory.toPath().resolve("render_debug");
                java.nio.file.Files.createDirectories(debugDir);
                String stamp = String.valueOf(System.currentTimeMillis());
                String label = rotateX == 90f ? "top" : (rotateY == 45f ? "iso_ne" : "iso_sw");
                java.nio.file.Files.write(debugDir.resolve(stamp + "_" + label + ".png"), pngBytes);
            } catch (Exception ignored) {}
            // ── END DEBUG ─────────────────────────────────────────────────────

            return Base64.getEncoder().encodeToString(pngBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}