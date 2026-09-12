package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 无槽位强化·回响：<b>让缄默手套不再拦截声音</b>。
 *
 * <p>缄默手套（{@link com.mofengbaizhi.tinkersnewlife.content.item.SilentGloveItem}）佩戴时会静默掉
 * 玩家听到的几乎所有声音（客户端 {@code SilentGloveSoundHandler} 的白名单机制）。
 * 打上「回响」之后，那层静默被解除——佩戴期间一切声音照常播放，其余功能（武器收纳等）不受影响。
 *
 * <p>安装不占槽（配方里没有 {@code slots} 字段即为无槽位强化），单级、不可洗不可剥。
 * 本类只是"可安装的强化空壳"，实际判定在 {@code SilentGloveSoundHandler.isMuting(...)}：
 * 那里会读手套上的这个强化等级，等级 &gt; 0 就不静默。
 */
public class ResonanceModifier extends Modifier {

    /** 强化 id（客户端声音处理器也用它做判定） */
    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "resonance"));

    /** 该手套是否装了「回响」（读不到工具数据 → false） */
    public static boolean hasResonance(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            ToolStack tool = ToolStack.from(stack);
            return tool != null && tool.getModifierLevel(ID) > 0;
        } catch (Throwable t) {
            return false;
        }
    }
}
