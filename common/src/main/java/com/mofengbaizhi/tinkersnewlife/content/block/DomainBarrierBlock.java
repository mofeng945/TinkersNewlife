package com.mofengbaizhi.tinkersnewlife.content.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * 领域阻挡方块（隐形物理墙）
 * <p>
 * - 完全隐形（空模型，无纹理渲染）
 * - 具有完整碰撞（物理阻挡：领域内外生物无法穿过）
 * - 不可破坏、无掉落，仅由领域系统生成与移除
 */
public class DomainBarrierBlock extends Block {

    public DomainBarrierBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE)
                .noOcclusion()          // 不参与渲染剔除（隐形）
                .noLootTable()          // 无掉落
                .strength(-1.0F, 3600000.0F) // 不可破坏
                .noParticlesOnBreak()
        );
    }
}
