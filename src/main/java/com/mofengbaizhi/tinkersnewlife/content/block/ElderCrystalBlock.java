package com.mofengbaizhi.tinkersnewlife.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;

/**
 * <b>古老者水晶方块</b>：容量 4000 EE 的方块形态（= 4 个水晶）。
 *
 * <ul>
 *   <li>存的是 {@link ElderCrystalBlockEntity} 里的 {@code EE} ✓；</li>
 *   <li><b>挖掉掉自己，EE 一起带走</b> ✓ —— 走原版方块物品的通用机制（潜影盒同款）：
 *       战利品表 {@code minecraft:copy_nbt}（{@code source = block_entity}）把方块实体整包 NBT
 *       写进掉落物的 {@code BlockEntityTag} ✓；再放下时
 *       {@code BlockItem#updateCustomBlockEntityTag} 又把它灌回方块实体 ✓ ——
 *       <b>没有</b>我们自己的搬运代码 ✗ 也就不会出现"物品与方块两套账"✓；</li>
 *   <li>放置/破坏都只认 loot table + 原版机制 ⇒ 精准采集/时运都不影响它（它本来就不是矿石 ✓）。</li>
 * </ul>
 *
 * <p>发光等级固定 7（占位观感 ✓）：本轮不做"越满越亮"（那需要把存量同步进方块状态 ✗ 属台座 P2 的活）。
 */
public class ElderCrystalBlock extends BaseEntityBlock {

    public ElderCrystalBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(3.0F, 6.0F)
                .sound(SoundType.AMETHYST)
                .requiresCorrectToolForDrops()
                .lightLevel(state -> 7)
                .mapColor(MapColor.COLOR_PURPLE));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // 普通模型（方块自己贴图 ✓ 不走实体渲染器 ✗）
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElderCrystalBlockEntity(pos, state);
    }
}
