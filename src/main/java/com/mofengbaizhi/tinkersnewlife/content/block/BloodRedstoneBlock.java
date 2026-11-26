package com.mofengbaizhi.tinkersnewlife.content.block;

import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * 血液（红石粉外观）：伏魔御厨子领域斩击在实体脚下留下的"血液"。
 * <p>
 * 复用红石线的外观与物理行为（贴地、可踩踏、可被挖掘），
 * 但没有战利品表 → 挖掉不掉落任何东西；且不注册物品形态（无法拾取/放置）。
 */
public class BloodRedstoneBlock extends RedStoneWireBlock {

    public BloodRedstoneBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_RED)
                .noCollission()
                .instabreak()
                .sound(SoundType.WOOL));
    }
}
