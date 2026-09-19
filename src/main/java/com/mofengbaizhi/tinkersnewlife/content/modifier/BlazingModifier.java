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
 * 铁魔法联动特性·<b>炽热</b>（材料「炽金」工具自带，有等级）：
 * <ul>
 *   <li>每次攻击有 <b>5% × 等级</b> 的概率点燃目标 <b>5 秒</b>；</li>
 *   <li>每次攻击伤害结算后，追加一段 <b>炽焰学派法术伤害</b> = {@code 0.1 × (等级+1) × 本次伤害}。</li>
 * </ul>
 * 结算在 {@code content.modifier.events.PyriumHandler}。
 */
public class BlazingModifier extends Modifier implements TooltipModifierHook {

    public static final ModifierId ID = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "blazing"));

    private static final int MAX_LEVEL = 3;

    /** 每级点燃概率：5% */
    public static final float IGNITE_CHANCE_PER_LEVEL = 0.05F;
    /** 点燃时长（秒） */
    public static final int IGNITE_SECONDS = 5;
    /** 追加伤害系数：0.1 × (等级 + 1) */
    public static final double EXTRA_RATIO_BASE = 0.1;
    /** 追加伤害的炽焰学派伤害类型（铁魔法） */
    public static final String FIRE_DAMAGE_TYPE = "irons_spellbooks:fire_magic";

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
        int level = modifier.getLevel();
        tooltip.add(Component.translatable("modifier.tinkersnewlife.blazing.tip",
                String.format("%.0f", IGNITE_CHANCE_PER_LEVEL * level * 100),
                String.format("%.0f", EXTRA_RATIO_BASE * (level + 1) * 100)));
    }

    /** 该物品上的炽热等级（没有则 0） */
    public static int levelOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        var tool = ToolHelper.getToolStack(stack);
        return clampLevel(ToolHelper.getActiveModifierLevel(tool, ID));
    }

    /** 玩家身上（主手/副手/护甲）最高的炽热等级 */
    public static int bestLevel(LivingEntity entity) {
        if (entity == null) return 0;
        int best = levelOf(entity.getMainHandItem());
        best = Math.max(best, levelOf(entity.getOffhandItem()));
        for (ItemStack armor : entity.getArmorSlots()) best = Math.max(best, levelOf(armor));
        return best;
    }
}
