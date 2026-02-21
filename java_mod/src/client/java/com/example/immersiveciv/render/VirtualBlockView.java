package com.example.immersiveciv.render;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class VirtualBlockView implements BlockAndTintGetter {
    public final Map<BlockPos, BlockState> blocks;
    public final Map<BlockPos, BlockEntity> blockEntities;
    private final int minY, maxY;

    public VirtualBlockView(Map<BlockPos, BlockState> blocks, Map<BlockPos, BlockEntity> blockEntities) {
        this.blocks = blocks;
        this.blockEntities = blockEntities;
        int currentMin = 0, currentMax = 0;
        if (!blocks.isEmpty()) {
            currentMin = blocks.keySet().stream().mapToInt(BlockPos::getY).min().orElse(0);
            currentMax = blocks.keySet().stream().mapToInt(BlockPos::getY).max().orElse(0);
        }
        this.minY = currentMin;
        this.maxY = currentMax;
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) { return blockEntities.get(pos); }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
    }

    @Override
    public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }

    @Override
    public int getMinBuildHeight() { return minY; }

    @Override
    public int getHeight() { return Math.max(1, maxY - minY + 1); }

    @Override
    public float getShade(Direction direction, boolean shaded) { return 1.0f; }

    @Override
    public LevelLightEngine getLightEngine() { return null; }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
        return 0xFFFFFF; // Дефолтный белый цвет (для листвы и травы в GUI)
    }
}