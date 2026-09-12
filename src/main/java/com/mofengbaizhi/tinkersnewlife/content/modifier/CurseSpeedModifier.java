package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 升级槽强化「咒速输出」：<b>只可装在咒力核心上</b>。
 * <p>
 * 提升咏唱速度——「咒言术」的读条时长按 {@code max(0.1, 1/(等级+1))} 缩放：
 * 1 级 0.50x（读条减半）、2 级 0.33x、3 级 0.25x、4 级 0.20x、5 级 0.17x，
 * 最低不少于 0.10x（即 10 倍速）。
 * <p>
 * 槽位规则：只有 1 级占用 1 个升级槽，2~5 级为免费升级（后续等级配方无 {@code slots} 字段），
 * 满 5 级。数值计算由本类静态方法提供，{@code CursedSpeechTechnique} 在按下术式键时读取。
 */
public class CurseSpeedModifier extends Modifier {

    /** 强化 id */
    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "curse_speed"));

    /** 读条时长缩放下限（10 倍速） */
    public static final double MIN_CHANT_SCALE = 0.1;
    /** 最高等级 */
    public static final int MAX_LEVEL = 5;

    /** 该咒力核心当前的咒速等级（读不到工具数据 → 0） */
    public static int levelOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        try {
            ToolStack tool = ToolStack.from(stack);
            return tool == null ? 0 : tool.getModifierLevel(ID);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 玩家佩戴的咒力核心上的咒速等级（未佩戴 → 0） */
    public static int levelOf(Player player) {
        return CursePowerHelper.getModifierLevel(CursePowerHelper.findEquippedCurseCore(player), ID);
    }

    /** 咏唱读条时长倍率 = max(0.1, 1/(等级+1))；0 级 → 1.0（无变化） */
    public static double chantScale(Player player) {
        int level = levelOf(player);
        if (level <= 0) return 1.0;
        return Math.max(MIN_CHANT_SCALE, 1.0 / (level + 1));
    }
}
