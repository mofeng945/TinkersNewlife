package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.curse.technique.ConstructTechnique;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

/**
 * 构筑术式·拟造方块「<b>永不掉落</b>」兜底拦截。
 *
 * <p>为什么需要它：拟造方块放下后只是<b>登记了坐标</b>（{@code ConstructTechnique.PLACED_TEMPS}），
 * 方块本体没有任何标记。此前只堵住了"玩家挖掘（BreakEvent）""爆炸（Detonate）"和
 * "掉落物落在地上（认领窗口）"三条路，而像 <b>AE2 破坏面板 / 各类矿机 / 机械动力钻头</b> 这类
 * 机器是<b>直接算出掉落表塞进自己内部库存</b>的——既没有 BreakEvent，也不一定有掉落物实体，
 * 于是拟造方块就被"洗"成了真材料。
 *
 * <p>所以这里在**掉落表生成的源头**（{@code Block.getDrops}）拦一刀：命中已登记的拟造方块位置
 * 直接返回空列表。这样一来无论谁来破坏（挖掘/爆炸/机器/其它模组 API），
 * <b>都不可能从拟造方块里拿到真物品</b>。
 *
 * <p>注意：
 * <ul>
 *   <li>只拦"该位置登记过、且方块类型与登记一致"的情况——正常方块零影响；</li>
 *   <li>服务端专用（客户端 {@code PLACED_TEMPS} 恒为空，等同 no-op）；</li>
 *   <li>注入器<b>只用 SRG 名</b>（{@code remap = false}）：本模组未启用 Mixin 注解处理器、refmap 是手写的，
 *       而游戏里跑的就是 SRG 名；万一注入失败也只是退回原逻辑（{@code defaultRequire: 0}），不会崩。</li>
 * </ul>
 */
@Mixin(value = Block.class, priority = 900)
public abstract class BlockDropsMixin {

    /** {@code Block.getDrops(BlockState, ServerLevel, BlockPos, BlockEntity)} */
    @Inject(method = "m_49869_", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tinkersnewlife$noDropsForConstructed4(BlockState state, ServerLevel level, BlockPos pos,
                                                              BlockEntity blockEntity,
                                                              CallbackInfoReturnable<List<ItemStack>> cir) {
        tinkersnewlife$deny(state, level, pos, cir);
    }

    /** {@code Block.getDrops(BlockState, ServerLevel, BlockPos, BlockEntity, Entity, ItemStack)} */
    @Inject(method = "m_49874_", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tinkersnewlife$noDropsForConstructed6(BlockState state, ServerLevel level, BlockPos pos,
                                                              BlockEntity blockEntity, Entity entity, ItemStack tool,
                                                              CallbackInfoReturnable<List<ItemStack>> cir) {
        tinkersnewlife$deny(state, level, pos, cir);
    }

    private static void tinkersnewlife$deny(BlockState state, ServerLevel level, BlockPos pos,
                                            CallbackInfoReturnable<List<ItemStack>> cir) {
        try {
            if (ConstructTechnique.isConstructedBlockAt(level, pos, state)) {
                cir.setReturnValue(Collections.emptyList());
            }
        } catch (Throwable ignored) {
            // 任何异常都退回原掉落逻辑，绝不影响正常方块
        }
    }
}
