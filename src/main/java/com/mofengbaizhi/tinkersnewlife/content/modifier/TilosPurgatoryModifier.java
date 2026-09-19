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
 * 铁魔法联动特性·<b>提洛斯炼狱</b>（材料「圣灵」自带，<b>无等级</b>）：
 *
 * <p>佩戴者生命降到一半以下时，在身旁召唤<b>两只远古骑士</b>（铁魔法 {@code citadel_keeper}，
 * 套用咒灵操术那套<b>仆从类</b>处理：只摘掉它的目标选择、目标由外部每 tick 指派，
 * <b>骑士原生 AI 完整保留</b>），存在 <b>150 秒</b>；
 * 同时<b>无吟唱</b>释放一次<b>5 级地狱浮现</b>（{@code irons_spellbooks:raise_hell}），
 * 以玩家为中心、<b>不伤害这两只仆从</b>。冷却 <b>150 秒</b>。
 *
 * <p>结算在 {@code content.modifier.events.TilosPurgatoryHandler}。
 */
public class TilosPurgatoryModifier extends Modifier implements TooltipModifierHook {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "tilos_purgatory"));

    /** 召唤数量 */
    public static final int KNIGHT_COUNT = 2;
    /** 仆从存在时间（tick）= 150s */
    public static final int KNIGHT_LIFETIME = 150 * 20;
    /** 冷却（tick）= 150s */
    public static final int COOLDOWN = 150 * 20;
    /** 触发血量比例：半血 */
    public static final float TRIGGER_RATIO = 0.5F;
    /** 地狱浮现等级 */
    public static final int HELL_LEVEL = 5;
    /** 远古骑士实体 id */
    public static final String KNIGHT_ENTITY = "irons_spellbooks:citadel_keeper";
    /** 地狱浮现法术 id */
    public static final String HELL_SPELL = "irons_spellbooks:raise_hell";

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
        tooltip.add(Component.translatable("modifier.tinkersnewlife.tilos_purgatory.tip"));
    }

    // ============================================================
    //  查询工具（结算器用）
    // ============================================================

    /** 该物品是否带提洛斯炼狱 */
    public static boolean has(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var tool = ToolHelper.getToolStack(stack);
        return ToolHelper.getActiveModifierLevel(tool, ID) > 0;
    }

    /** 玩家身上（主手/副手/护甲）是否带着提洛斯炼狱 */
    public static boolean wornBy(LivingEntity entity) {
        if (entity == null) return false;
        if (has(entity.getMainHandItem()) || has(entity.getOffhandItem())) return true;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (has(armor)) return true;
        }
        return false;
    }
}
