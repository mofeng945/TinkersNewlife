package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
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
 * 巫师套装特性·<b>魔力涌动</b>（<b>无等级</b> ✓ 按件叠加 ✓）—— 内建在四件巫师套上
 * （走工具定义的 {@code tconstruct:traits} 模块 ✓ 与材料无关 ✓）。
 *
 * <p>每 1 件：铁魔法<b>法力上限 +125</b>、<b>法术强度 +5%</b>、<b>法术伤害 +7%</b>；
 * 诡厄巫法全学派强度 +5%（后续接 ✓）。
 *
 * <p>提示行**动态**：数值取"当前穿着几件"实时算 ✓（用户要求"和模块化魔杖一样动态加 ✓
 * 别一大串静态描述 ✗"）；规则细则写在帕秋莉手册里 ✓。
 *
 * <p>属性的维持在 {@code WizardArmorSetHandler}（每秒重算 ✓ 脱件即移除 ✓）——
 * 与「刻印」{@link InscriptionModifier} 用的是同一套做法 ✓，但各用各的修饰符 UUID ✓ 彼此叠加 ✓。
 */
public class ManaSurgeTrait extends Modifier implements TooltipModifierHook, InventoryTickModifierHook {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "mana_surge"));

    /** 每件的数值（用户定案 ✓） */
    public static final int MANA_PER_PIECE = 125;
    public static final double SPELL_POWER_PER_PIECE = 0.05D;
    public static final double SPELL_DAMAGE_PER_PIECE = 0.07D;

    /** 每件给 1 格刻印位（用户：有魔力涌动的盔甲可以注入一个法术 ✓） */
    private static final int SPELL_SLOTS = 1;

    /** 效果固定 ⇒ 显示名不带等级 ✓ */
    @Override
    public Component getDisplayName(int level) {
        return this.getDisplayName();
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP, ModifierHooks.INVENTORY_TICK);
    }

    /**
     * 背包 tick：给带魔力涌动的这件甲<b>补一个空法术容器</b> ✓ ——
     * 这样铁魔法的<b>奥术铁砧</b>才会收它（铁砧只认"已经是法术容器"的物品 ✗），
     * 玩家就能把卷轴里的法术刻进这套巫师甲 ✓。
     * 与「魔导」{@link ArcaneConductionModifier} 用的是同一套注入 ✓（每件各 1 格 ✓）。
     */
    @Override
    public void onInventoryTick(IToolStackView tool, ModifierEntry modifier, net.minecraft.world.level.Level world,
                                LivingEntity holder, int itemSlot,
                                boolean isSelected, boolean isCorrectSlot, ItemStack stack) {
        if (world.isClientSide) return;
        if (holder.tickCount % 20 != 0) return;
        com.mofengbaizhi.tinkersnewlife.util.IronSpellsReflector.ensureSpellContainer(stack, SPELL_SLOTS);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        int pieces = countWorn(player);
        tooltip.add(Component.translatable("modifier.tinkersnewlife.mana_surge.tip",
                pieces, pieces * MANA_PER_PIECE,
                Math.round(pieces * SPELL_POWER_PER_PIECE * 100),
                Math.round(pieces * SPELL_DAMAGE_PER_PIECE * 100)));
    }

    // ============================================================
    //  查询工具（结算器用 ✓ 与 InscriptionModifier 同款 ✓）
    // ============================================================

    /** 该物品是否带魔力涌动 */
    public static boolean has(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var tool = ToolHelper.getToolStack(stack);
        return ToolHelper.getActiveModifierLevel(tool, ID) > 0;
    }

    /** 身上穿了几件带魔力涌动的盔甲（0~4 ✓ 按件叠加 ✓） */
    public static int countWorn(@Nullable LivingEntity entity) {
        if (entity == null) return 0;
        int n = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            if (has(entity.getItemBySlot(slot))) n++;
        }
        return n;
    }
}