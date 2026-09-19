package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
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
import slimeknights.tconstruct.library.modifiers.hook.armor.ModifyDamageModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.context.EquipmentContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 铁魔法联动特性·<b>远古庇护</b>（材料「炽金」盔甲自带，<b>无等级</b>，盔甲特性）：
 * <ul>
 *   <li><b>常驻 5% 受到伤害减免</b> —— 走护甲 {@link ModifierHooks#MODIFY_DAMAGE}，
 *       多件穿戴时 TCon 会逐件链乘 → 4 件约 18.5%，与"可根据装备数量叠加"一致 ✓；</li>
 *   <li><b>炽焰学派法术强度 +50%</b> —— 只要穿着任意一件带此特性的装备即生效（**固定 +50%，不叠加**，
 *       因为用户只把"可叠加"写在减伤上 ✓）。属性维持见 {@code content.modifier.events.PyriumHandler}。</li>
 * </ul>
 */
public class AncientSanctuaryModifier extends Modifier implements ModifyDamageModifierHook, TooltipModifierHook {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "ancient_sanctuary"));

    /** 每件 5% 减伤 → 乘 0.95 */
    public static final float DAMAGE_TAKEN_MULTIPLIER = 0.95F;
    /** 炽焰法强加成：+50%（固定） */
    public static final double FIRE_POWER_BONUS = 0.5;

    @Override
    public Component getDisplayName(int level) {
        return this.getDisplayName();
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
        if (amount > 0) {
            amount *= (float) Math.pow(DAMAGE_TAKEN_MULTIPLIER, modifier.getLevel());
        }
        return amount;
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("modifier.tinkersnewlife.ancient_sanctuary.tip"));
    }

    /** 该物品是否带远古庇护 */
    public static boolean has(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var tool = ToolHelper.getToolStack(stack);
        return ToolHelper.getActiveModifierLevel(tool, ID) > 0;
    }

    /** 是否穿着/持有任意一件带此特性的装备（护甲槽 + 主副手，盾牌在副手） */
    public static boolean wornBy(LivingEntity entity) {
        if (entity == null) return false;
        if (has(entity.getMainHandItem()) || has(entity.getOffhandItem())) return true;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (has(armor)) return true;
        }
        return false;
    }
}
