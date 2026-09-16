package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks.IronSpellsSpellAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.armor.ModifyDamageModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.context.EquipmentContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 铁魔法联动特性·<b>魔力铠甲</b>（材料「魔金」盔甲自带，<b>有等级、可叠加</b>）：
 * 每 <b>100 点法力上限</b>提供 <b>0.5 点伤害减免</b>。
 *
 * <p>实现走 TCon 的护甲钩子 {@link ModifyDamageModifierHook#modifyDamageTaken}（和「导魔」同一套 ✓）：
 * 每件盔甲各自扣掉 {@code 0.5 × 等级 × floor(法力上限 / 100)} 点 →
 * 多件<b>自然叠加</b> ✓，且扣完不小于 0 ✓。
 *
 * <p>法力上限取铁魔法的 {@code MAX_MANA} 属性（默认 100）✓ —— 所以「万法归一」给的法力上限
 * 也会反过来抬高这里的减伤（设计上就是配套的 ✓）。
 *
 * <p>铁魔法不在场时属性取不到 → 不减伤（返回原值）✓，不报错 ✓。
 */
public class ManaArmorModifier extends Modifier implements ModifyDamageModifierHook, TooltipModifierHook {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "mana_armor"));

    /** 最高 3 级 */
    private static final int MAX_LEVEL = 3;

    /** 每 100 点法力上限的减伤点数 */
    public static final float REDUCTION_PER_100_MANA = 0.5F;

    public static int clampLevel(int level) {
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.MODIFY_DAMAGE, ModifierHooks.TOOLTIP);
    }

    @Override
    public float modifyDamageTaken(IToolStackView tool, ModifierEntry modifier, EquipmentContext context,
                                   EquipmentSlot slotType, DamageSource source, float amount,
                                   boolean isDirectDamage) {
        if (amount <= 0.0F) return amount;
        int units = manaUnits(context.getEntity());
        if (units <= 0) return amount;
        float reduction = REDUCTION_PER_100_MANA * modifier.getLevel() * units;
        return Math.max(0.0F, amount - reduction);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("modifier.tinkersnewlife.mana_armor.tip",
                String.format("%.1f", REDUCTION_PER_100_MANA * modifier.getLevel())));
    }

    // ============================================================
    //  查询工具（结算器 / tooltip 用）
    // ============================================================

    /** 该生物的法力上限有多少个"100 点"（取不到返回 0） */
    public static int manaUnits(LivingEntity entity) {
        if (entity == null) return 0;
        Attribute attr = IronSpellsSpellAccess.attribute("MAX_MANA");
        if (attr == null) return 0;
        try {
            double max = entity.getAttributeValue(attr);
            return max <= 0.0D ? 0 : (int) (max / 100.0D);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 该物品上的等级（没有则 0） */
    public static int levelOf(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        var tool = com.mofengbaizhi.tinkersnewlife.util.ToolHelper.getToolStack(stack);
        return tool == null ? 0 : clampLevel(tool.getModifierLevel(ID));
    }
}
