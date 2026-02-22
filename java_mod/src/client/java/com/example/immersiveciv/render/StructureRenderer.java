package com.example.immersiveciv.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Base64;

public class StructureRenderer {

    public static String renderToB64(VirtualBlockView view, float rotateX, float rotateY, int radius) {
        Minecraft mc = Minecraft.getInstance();
        int size = 512;

        RenderTarget fbo = new TextureTarget(size, size, true, Minecraft.ON_OSX);
        fbo.setClearColor(0, 0, 0, 0);
        fbo.clear(Minecraft.ON_OSX);

        RenderTarget oldTarget = mc.getMainRenderTarget();
        fbo.bindWrite(true);

        RenderSystem.viewport(0, 0, size, size);
        RenderSystem.clear(512, Minecraft.ON_OSX);

        // Ортографическая камера (изометрия)
        float scale = radius * 1.5f + 2f;
        Matrix4f projection = new Matrix4f().setOrtho(-scale, scale, -scale, scale, -1000f, 3000f);
        RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);

        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.pushPose();
        poseStack.setIdentity();

        poseStack.mulPose(Axis.XP.rotationDegrees(rotateX));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotateY));

        // Направленное освещение — имитирует солнечный свет сверху-спереди
        RenderSystem.setShaderLights(
                new Vector3f(0.2f,  1.0f, -0.7f).normalize(),
                new Vector3f(-0.2f, 0.3f,  0.2f).normalize()
        );

        // Вычисляем границы постройки
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : view.blocks.keySet()) {
            minX = Math.min(minX, pos.getX()); maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY()); maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ()); maxZ = Math.max(maxZ, pos.getZ());
        }

        // Целочисленное смещение — гарантирует точный перевод координат туда-обратно
        // без потерь от округления дробного центра.
        final int offsetX = minX;
        final int offsetY = minY;
        final int offsetZ = minZ;

        // Центрируем всю сцену на экране один раз через poseStack.
        // Так камера смотрит в середину постройки, а не в её угол.
        float halfX = (maxX - minX) / 2f;
        float halfY = (maxY - minY) / 2f;
        float halfZ = (maxZ - minZ) / 2f;
        poseStack.translate(-halfX, -halfY, -halfZ);
        RenderSystem.applyModelViewMatrix();

        PoseStack worldPose = new PoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();

        // Прокси-вью для жидкостей: переводит локальные координаты обратно в оригинальные.
        // renderLiquid генерирует вершины по localPos и запрашивает соседей у этого view —
        // оба должны быть в одной системе координат, поэтому offsetView транслирует запросы.
        BlockAndTintGetter offsetView = new BlockAndTintGetter() {
            @Override
            public BlockState getBlockState(BlockPos p) {
                return view.getBlockState(new BlockPos(
                        p.getX() + offsetX,
                        p.getY() + offsetY,
                        p.getZ() + offsetZ
                ));
            }

            @Override
            public FluidState getFluidState(BlockPos p) {
                return getBlockState(p).getFluidState();
            }

            @Override
            public BlockEntity getBlockEntity(BlockPos p) {
                return null;
            }

            @Override
            public int getHeight() {
                return view.getHeight();
            }

            @Override
            public int getMinBuildHeight() {
                return view.getMinBuildHeight();
            }

            @Override
            public LevelLightEngine getLightEngine() {
                return view.getLightEngine();
            }

            @Override
            public int getBrightness(LightLayer lightLayer, BlockPos p) {
                return 15;
            }

            @Override
            public float getShade(Direction direction, boolean shaded) {
                return 1.0f;
            }

            @Override
            public int getBlockTint(BlockPos p, ColorResolver colorResolver) {
                return 0xFFFFFF;
            }
        };

        for (BlockPos pos : view.blocks.keySet()) {
            // Локальные координаты — целочисленное смещение, без округления
            int lx = pos.getX() - offsetX;
            int ly = pos.getY() - offsetY;
            int lz = pos.getZ() - offsetZ;

            // ── Твёрдый блок ──────────────────────────────────────────────
            worldPose.pushPose();
            worldPose.translate(lx, ly, lz);

            blockRenderer.renderSingleBlock(
                    view.getBlockState(pos),
                    worldPose,
                    buffers,
                    0xF000F0,
                    OverlayTexture.NO_OVERLAY
            );

            // ── BlockEntity ───────────────────────────────────────────────
            BlockEntity be = view.getBlockEntity(pos);
            if (be != null) {
                try {
                    mc.getBlockEntityRenderDispatcher().render(be, 0, worldPose, buffers);
                } catch (Exception ignored) {
                    // Виртуальный мир может вызвать исключение у некоторых BE (напр. Create)
                }
            }

            worldPose.popPose();

            // ── Жидкость ──────────────────────────────────────────────────
            FluidState fluidState = view.getBlockState(pos).getFluidState();
            if (!fluidState.isEmpty()) {
                BlockPos localPos = new BlockPos(lx, ly, lz);
                VertexConsumer liquidConsumer = buffers.getBuffer(RenderType.translucent());
                blockRenderer.renderLiquid(
                        localPos,
                        offsetView,              // знает о локальных координатах
                        liquidConsumer,
                        view.getBlockState(pos), // оригинальный BlockState
                        fluidState
                );
            }
        }

        // Сначала сбрасываем translucent (жидкости), затем всё остальное
        buffers.endBatch(RenderType.translucent());
        buffers.endBatch();

        poseStack.popPose();
        RenderSystem.applyModelViewMatrix();

        // Выгружаем пиксели
        NativeImage image = new NativeImage(size, size, false);
        RenderSystem.bindTexture(fbo.getColorTextureId());
        image.downloadTexture(0, false);
        image.flipY(); // OpenGL рендерит вверх ногами

        // Восстанавливаем основной экран
        oldTarget.bindWrite(true);
        RenderSystem.viewport(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight());
        fbo.destroyBuffers();

        try {
            byte[] pngBytes = image.asByteArray();
            image.close();

            // ── DEBUG: сохраняем PNG на диск ──────────────────────────────
            // Удали этот блок перед релизом или оберни в флаг System.getProperty
            try {
                java.nio.file.Path debugDir = mc.gameDirectory.toPath().resolve("render_debug");
                java.nio.file.Files.createDirectories(debugDir);
                String stamp = String.valueOf(System.currentTimeMillis());
                String label = rotateX == 90f ? "top" : (rotateY == 45f ? "iso_ne" : "iso_sw");
                java.nio.file.Files.write(debugDir.resolve(stamp + "_" + label + ".png"), pngBytes);
            } catch (Exception ignored) {}
            // ── END DEBUG ─────────────────────────────────────────────────

            return Base64.getEncoder().encodeToString(pngBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}