package com.mofengbaizhi.tinkersnewlife.content.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;

/**
 * 狱门疆视觉方块（纯渲染载体）：
 * <ul>
 *   <li>仅用于狱门疆实体的模型渲染（方块模型 + 可绘制贴图），无物品形态、不可获得</li>
 *   <li>sealed 属性切换 空闲 / 已封印 两种贴图（用户可自行绘制贴图）</li>
 *   <li>无碰撞、无战利品，不会在世界中真实生成</li>
 * </ul>
 */
public class GourdJailVisualBlock extends Block {

    public static final BooleanProperty SEALED = BooleanProperty.create("sealed");

    public GourdJailVisualBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .noCollission()
                .noOcclusion()
                .noLootTable()
                .instabreak()
                .noParticlesOnBreak()
        );
        this.registerDefaultState(this.stateDefinition.any().setValue(SEALED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SEALED);
    }
}
