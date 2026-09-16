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
 * 铁魔法联动特性·<b>专注</b>（材料「秘银」工具自带，<b>有等级</b>）：
 * <ul>
 *   <li>手持时<b>吟唱不会被打断</b> —— 见 {@code com.mofengbaizhi.tinkersnewlife.mixin.SpellInterruptFocusMixin}
 *       （拦在铁魔法 {@code AbstractSpell#canBeInterrupted} 的入口 ✓，ISS 不在场时该 mixin 只是未应用，无副作用 ✓）；</li>
 *   <li>每级 <b>+5% 吟唱速度</b> —— 走铁魔法 {@code CAST_TIME_REDUCTION}（法术吟唱缩减）属性 ✓。</li>
 * </ul>
 */
public class FocusModifier extends Modifier implements TooltipModifierHook {

    public static final ModifierId ID = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "focus"));

    private static final int MAX_LEVEL = 3;

    /** 每级吟唱缩减：+5% */
    public static final double CAST_SPEED_PER_LEVEL = 0.05;

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
        tooltip.add(Component.translatable("modifier.tinkersnewlife.focus.tip",
                String.format("%.0f", CAST_SPEED_PER_LEVEL * modifier.getLevel() * 100)));
    }

    /** 该物品上的专注等级（没有则 0） */
    public static int levelOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        var tool = ToolHelper.getToolStack(stack);
        return tool == null ? 0 : clampLevel(tool.getModifierLevel(ID));
    }

    /** 是否手持（主手/副手）带专注的工具 */
    public static boolean heldBy(LivingEntity entity) {
        if (entity == null) return false;
        return levelOf(entity.getMainHandItem()) > 0 || levelOf(entity.getOffhandItem()) > 0;
    }

    /** 手持时取最高等级（供属性加成用） */
    public static int heldLevel(LivingEntity entity) {
        if (entity == null) return 0;
        return Math.max(levelOf(entity.getMainHandItem()), levelOf(entity.getOffhandItem()));
    }
}
