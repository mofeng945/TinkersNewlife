package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 特性·<b>寒霜刺骨</b>（材料「冰封骨头」自带，<b>有等级</b>）：
 * 命中后使敌人获得本模组的<b>霜冻</b>效果（{@code tinkersnewlife:frost}），
 * 持续 <b>3 × 等级 秒</b>，冷却 <b>6 秒</b>。
 *
 * <p>霜冻效果的行为（见 {@code content.effect.FrostEffect}）：持续期间每 tick 累加目标的
 * "冻结刻度"（目标会真的被冻住），效果结束时再补 100 tick 寒霜 ✓。
 *
 * <h2>⚠ 为什么不用匠魂的 {@code MELEE_HIT} 钩子（踩过的坑）</h2>
 * 第一版用了 {@code MeleeHitModifierHook#afterMeleeHit} ✗ —— <b>用户实测完全没效果</b> ✗。
 * 排查发现：<b>普通挥砍时匠魂并不会调用这个钩子</b> ✗（本模组里 {@code CurseCoreTraitHelper}
 * 甚至是<b>手动</b>调 {@code hook.afterMeleeHit(...)} 的 ✓），
 * 而现有的命中类特性（炽热 / 冷酷 / 冰龙血注魔…）**一律走 Forge 事件** ✓：
 * {@code LivingHurtEvent} + {@link ToolHelper#getCombatToolWith}（同时覆盖近战与弹射物 ✓）。
 * 所以这里也照那套来，结算在 {@code content.modifier.events.BitingFrostHandler} ✓。
 */
public class BitingFrostModifier extends Modifier implements TooltipModifierHook {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "biting_frost"));

    /** 最高 3 级（匠魂没有 getMaxLevel 覆写点，在查询处夹住） */
    private static final int MAX_LEVEL = 3;

    /** 每级持续时间（秒） */
    public static final int SECONDS_PER_LEVEL = 3;

    /** 触发冷却（tick）= 6s */
    public static final int COOLDOWN = 6 * 20;

    public static int clampLevel(int level) {
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("modifier.tinkersnewlife.biting_frost.tip",
                String.valueOf(SECONDS_PER_LEVEL * clampLevel(modifier.getLevel())),
                String.valueOf(COOLDOWN / 20)));
    }

    /** 该物品是否带寒霜刺骨 */
    public static boolean has(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var tool = ToolHelper.getToolStack(stack);
        return ToolHelper.getActiveModifierLevel(tool, ID) > 0;
    }
}
