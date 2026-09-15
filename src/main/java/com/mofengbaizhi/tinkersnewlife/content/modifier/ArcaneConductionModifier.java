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
 * 铁魔法联动特性·<b>魔导</b>（材料「圣灵」自带，有等级）：
 *
 * <ol>
 *   <li><b>强化物品内刻印的法术</b>：持有/穿着的「圣灵」物品里刻印了某个铁魔法法术时，
 *       施放该法术会按魔导等级<b>提升法术等级</b>（不是整体法强）。见
 *       {@code integration.irons_spellbooks.IronSpellsArcaneHandler}；</li>
 *   <li><b>施法增伤</b>：手持/身穿带此特性的物品施放法术（<b>兼容诡厄巫法法术</b>）时，
 *       伤害 ×(1 + 0.4 × 等级)。</li>
 * </ol>
 *
 * <p>铁魔法未安装时本特性不存在（材料本身带 {@code forge:mod_loaded} 条件），
 * 因此这里不需要额外的软依赖处理。
 */
public class ArcaneConductionModifier extends Modifier implements TooltipModifierHook {

    public static final ModifierId ID = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "arcane_conduction"));

    /** 最高 3 级 */
    private static final int MAX_LEVEL = 3;

    /** 每级施法增伤倍率（0.4 / 级） */
    public static final double DAMAGE_BONUS_PER_LEVEL = 0.4;

    /** 等级上限（TCon 的 Modifier 没有 getMaxLevel 覆写点，这里在查询处夹住） */
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
        tooltip.add(Component.translatable("modifier.tinkersnewlife.arcane_conduction.tip",
                String.format("%.1f", DAMAGE_BONUS_PER_LEVEL * modifier.getLevel())));
    }

    // ============================================================
    //  查询工具（结算器用）
    // ============================================================

    /** 该物品上的魔导等级（没有则 0） */
    public static int levelOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        var tool = ToolHelper.getToolStack(stack);
        return tool == null ? 0 : clampLevel(tool.getModifierLevel(ID));
    }

    /** 玩家身上（主手/副手/护甲）所有带魔导的物品 */
    public static List<ItemStack> itemsWith(LivingEntity entity) {
        List<ItemStack> out = new ArrayList<>(6);
        if (entity == null) return out;
        addIf(out, entity.getMainHandItem());
        addIf(out, entity.getOffhandItem());
        for (ItemStack armor : entity.getArmorSlots()) addIf(out, armor);
        return out;
    }

    /** 玩家身上最高的魔导等级（0 = 没有） */
    public static int bestLevel(LivingEntity entity) {
        int best = 0;
        for (ItemStack stack : itemsWith(entity)) best = Math.max(best, levelOf(stack));
        return best;
    }

    private static void addIf(List<ItemStack> out, ItemStack stack) {
        if (!stack.isEmpty() && levelOf(stack) > 0) out.add(stack);
    }
}
