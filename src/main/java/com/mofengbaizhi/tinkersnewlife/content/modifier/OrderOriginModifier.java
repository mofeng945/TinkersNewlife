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
 * 铁魔法联动特性·<b>秩序之初</b>（材料「源钻合金」盔甲自带，<b>无等级</b>）：
 * <b>你受到的任何伤害都会被重新计算为「物理伤害」再落到你身上</b>，
 * 因此可以被你的护甲 / 饰品 / 属性正常减免 ✓。
 *
 * <p>实现见 {@code content.modifier.events.OrderOriginHandler}：拦 {@code LivingHurtEvent}，
 * 把伤害来源换成原版<b>物理</b>伤害类型（保留攻击者，击杀归属不丢 ✓）后重新施加一次 ✓。
 *
 * <p>⚠ 口径说明（与用户确认过）：这里覆盖的是**走伤害事件的伤害**（含魔法、真实伤害等几乎全部实战伤害）✓；
 * <b>"直接改血"（绕过伤害事件直接写血量）不拦</b> ✗ —— 那需要 mixin 到 {@code LivingEntity#setHealth}，
 * 会和死亡流程、治疗效果、{@code /kill} 等纠缠，风险远大于收益 ✓。
 */
public class OrderOriginModifier extends Modifier implements TooltipModifierHook {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "order_origin"));

    /** 效果固定 → 显示名不带等级 */
    @Override
    public Component getDisplayName(int level) {
        return this.getDisplayName();
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
        tooltip.add(Component.translatable("modifier.tinkersnewlife.order_origin.tip"));
    }

    /** 该物品是否带秩序之初 */
    public static boolean has(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var tool = ToolHelper.getToolStack(stack);
        return tool != null && tool.getModifierLevel(ID) > 0;
    }

    /** 身上（主手/副手/护甲）是否带着秩序之初 */
    public static boolean wornBy(LivingEntity entity) {
        if (entity == null) return false;
        if (has(entity.getMainHandItem()) || has(entity.getOffhandItem())) return true;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (has(armor)) return true;
        }
        return false;
    }
}
