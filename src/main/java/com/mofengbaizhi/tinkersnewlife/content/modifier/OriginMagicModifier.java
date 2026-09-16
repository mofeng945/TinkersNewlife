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
import java.util.ArrayList;
import java.util.List;

/**
 * 铁魔法联动特性·<b>奥术始源</b>（材料「源钻合金」通用自带，<b>有等级</b>）：
 *
 * <ol>
 *   <li><b>你释放的所有法术 +2 × 等级 级</b> —— 走铁魔法的 {@code ModifySpellLevelEvent}
 *       （见 {@code IronSpellsArcaneHandler}）。与「魔导」的区别：魔导只强化<b>刻印在物品里</b>的法术，
 *       而这里对所有法术生效 ✓；</li>
 *   <li><b>无需学习即可使用未学会的邪术</b>（{@code irons_spellbooks:eldritch} 学派）——
 *       走 {@code mixin.EldritchLearningMixin} 拦 {@code AbstractSpell#isLearned} ✓。</li>
 * </ol>
 *
 * <p><b>只有等级最高的单件生效</b> ✓（不叠加），见 {@link #bestLevel(LivingEntity)}。
 */
public class OriginMagicModifier extends Modifier implements TooltipModifierHook {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "origin_magic"));

    /** 最高 3 级 */
    private static final int MAX_LEVEL = 3;

    /** 每级给所有法术加的等级 */
    public static final int SPELL_LEVEL_PER_LEVEL = 2;

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
        tooltip.add(Component.translatable("modifier.tinkersnewlife.origin_magic.tip",
                String.valueOf(SPELL_LEVEL_PER_LEVEL * clampLevel(modifier.getLevel()))));
    }

    // ============================================================
    //  查询工具（结算器 / mixin 用）
    // ============================================================

    /** 该物品上的等级（没有则 0） */
    public static int levelOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        var tool = ToolHelper.getToolStack(stack);
        return tool == null ? 0 : clampLevel(tool.getModifierLevel(ID));
    }

    /** 身上（主手/副手/护甲）带此特性的物品 */
    public static List<ItemStack> itemsWith(LivingEntity entity) {
        List<ItemStack> out = new ArrayList<>(6);
        if (entity == null) return out;
        addIf(out, entity.getMainHandItem());
        addIf(out, entity.getOffhandItem());
        for (ItemStack armor : entity.getArmorSlots()) addIf(out, armor);
        return out;
    }

    /** 身上最高的等级（0 = 没有）—— "只有等级最高的单件生效" ✓ */
    public static int bestLevel(LivingEntity entity) {
        int best = 0;
        for (ItemStack stack : itemsWith(entity)) best = Math.max(best, levelOf(stack));
        return best;
    }

    /** 身上是否带着此特性 */
    public static boolean wornBy(LivingEntity entity) {
        return bestLevel(entity) > 0;
    }

    private static void addIf(List<ItemStack> out, ItemStack stack) {
        if (!stack.isEmpty() && levelOf(stack) > 0) out.add(stack);
    }
}
