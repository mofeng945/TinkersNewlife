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
import slimeknights.tconstruct.library.modifiers.hook.interaction.InventoryTickModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 铁魔法联动特性·<b>刻印</b>（材料「疣猪皮」自带，<b>无等级</b>）：
 * <b>手持此工具 / 穿戴此盔甲时，法术强度属性 +10%</b>。
 *
 * <p>实现：铁魔法的{@code SPELL_POWER}属性默认是 <b>1.0</b>
 * （而且它是**所有学派共用的乘数** ✓，见 {@code ArcaneDominationModifier} 的说明），
 * 所以每件 +0.1 就是"+10%" ✓。多件（主手/副手/护甲）各算一份，<b>叠加</b> ✓。
 *
 * <p>属性的维持在 {@code content.modifier.events.InscriptionHandler}（每 10 tick 重算一次，
 * 脱手/脱下立即移除 ✓）—— 与「奥法支配」「万法归一」用的是同一套做法，
 * 但各用各的修饰符 UUID，所以彼此**叠加而不覆盖** ✓。
 */
public class InscriptionModifier extends Modifier implements TooltipModifierHook, InventoryTickModifierHook {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "inscription"));

    /** 每件提供的法术强度：+0.1 = +10%（属性默认 1.0） */
    public static final double POWER_PER_ITEM = 0.03D;

    /** 效果固定 → 显示名不带等级 */
    @Override
    public Component getDisplayName(int level) {
        return this.getDisplayName();
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP, ModifierHooks.INVENTORY_TICK);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("modifier.tinkersnewlife.inscription.tip",
                String.format("%.0f", POWER_PER_ITEM * 100)));
    }

    // ============================================================
    //  查询工具（结算器用）
    // ============================================================

    /** 该物品是否带刻印 */
    public static boolean has(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var tool = ToolHelper.getToolStack(stack);
        return ToolHelper.getActiveModifierLevel(tool, ID) > 0;
    }

    /** 身上（主手/副手/护甲）带刻印的件数 —— 刻印按件叠加 ✓ */
    public static int countWorn(LivingEntity entity) {
        if (entity == null) return 0;
        int n = 0;
        if (has(entity.getMainHandItem())) n++;
        if (has(entity.getOffhandItem())) n++;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (has(armor)) n++;
        }
        return n;
    }

    /**
     * 背包 tick：给带刻印的工具/盔甲<b>补 1 个空法术容器位</b>（用户口径 2026-09-20 ✓）。
     * <p>⚠ 只在<b>还没有容器</b>时补 ✓（{@code ensureSpellContainer} 语义如此 ✓ 与「魔导」完全同款 ✓）；
     * 已经是容器（例如已经用奥术铁砧刻过、或魔导补过）⇒ 原样不动 ✓。
     */
    @Override
    public void onInventoryTick(IToolStackView tool, ModifierEntry modifier, net.minecraft.world.level.Level world,
                                LivingEntity holder, int itemSlot,
                                boolean isSelected, boolean isCorrectSlot, ItemStack stack) {
        if (world.isClientSide) return;
        if (holder.tickCount % 20 != 0) return;
        com.mofengbaizhi.tinkersnewlife.util.IronSpellsReflector.ensureSpellContainer(stack, SPELL_SLOTS);
    }

    /** 刻印给 1 格刻印位 ✓（用户口径：增加 1 个法术容器位 ✓） */
    private static final int SPELL_SLOTS = 1;
}
