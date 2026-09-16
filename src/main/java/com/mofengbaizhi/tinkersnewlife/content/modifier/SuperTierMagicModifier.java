package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks.IronSpellsSpellAccess;
import com.mofengbaizhi.tinkersnewlife.util.IronSpellsReflector;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
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
import java.util.ArrayList;
import java.util.List;

/**
 * 铁魔法联动特性·<b>超位魔法</b>（材料「魔金」工具自带，<b>无等级</b>）：
 *
 * <ol>
 *   <li><b>工具内可以注入法术</b>：背包 tick 给工具补一个<b>空法术容器</b>
 *       （{@link IronSpellsReflector#ensureSpellContainer}），这样铁魔法的
 *       <b>奥术铁砧</b>才肯接受它、玩家能把卷轴里的法术刻进去 ✓；</li>
 *   <li><b>刻印的法术强度提升至 50 级</b>：走 {@code ModifySpellLevelEvent}
 *       （见 {@code IronSpellsArcaneHandler}）：把等级<b>抬到</b> {@link #INSCRIBED_LEVEL}
 *       （不是 +50），已经更高的就保持原样 ✓；</li>
 *   <li><b>范围 ×5、持续 ×3</b>：铁魔法里这两项都从 {@code AbstractSpell#getSpellPower} 派生
 *       （例：深渊庇佑时长 = 强度 × 20 秒），所以做法是——
 *       <ul>
 *         <li>{@code mixin.SuperTierPowerMixin} 把该法术的 {@code getSpellPower} 结果 <b>×5</b> → 范围 ×5 ✓；</li>
 *         <li>但这样一来<b>伤害/治疗/状态时长也一起 ×5</b> ✗，于是：</li>
 *         <li>伤害在 {@code SpellDamageEvent} 里 <b>÷5</b> 还原 ✓、治疗同理 ✓；</li>
 *         <li>状态时长用 {@code mixin.SpellDurationMixin} 再 <b>×3/5</b> → 净得 <b>×3</b> ✓。</li>
 *       </ul>
 *       「当前这一发是不是超位魔法」靠 {@code mixin.SuperTierCastContextMixin} 在
 *       {@code castSpell}/{@code onServerCastComplete} 两端打标记（<b>选这两个是因为各法术会重写
 *       {@code onCast}，甚至先上效果再调 {@code super.onCast}</b> ✗，挂在 onCast 上会漏）✓。</li>
 * </ol>
 *
 * <p>铁魔法不在场时本特性不存在（材料本身带 {@code forge:mod_loaded} 条件）✓。
 */
public class SuperTierMagicModifier extends Modifier implements TooltipModifierHook, InventoryTickModifierHook {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "super_tier_magic"));

    /** 刻印的法术被"提升至"的等级（取 max，不叠加） */
    public static final int INSCRIBED_LEVEL = 50;
    /** 范围倍数（借道 getSpellPower） */
    public static final float RANGE_MULTIPLIER = 5.0F;
    /** 期望的状态持续倍数 */
    public static final float DURATION_MULTIPLIER = 3.0F;
    /** 状态时长补偿系数：getSpellPower 已经 ×5，这里再 ×(3/5) 才能净得 ×3 ✓ */
    public static final float EFFECT_DURATION_FACTOR = DURATION_MULTIPLIER / RANGE_MULTIPLIER;
    /** 给工具预留的刻印位 */
    public static final int SPELL_SLOTS = 3;

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
        tooltip.add(Component.translatable("modifier.tinkersnewlife.super_tier_magic.tip",
                String.valueOf(INSCRIBED_LEVEL)));
    }

    @Override
    public void onInventoryTick(IToolStackView tool, ModifierEntry modifier, net.minecraft.world.level.Level world,
                                LivingEntity holder, int itemSlot,
                                boolean isSelected, boolean isCorrectSlot, ItemStack stack) {
        if (world.isClientSide) return;
        if (holder.tickCount % 20 != 0) return;
        IronSpellsReflector.ensureSpellContainer(stack, SPELL_SLOTS);
    }

    // ============================================================
    //  查询工具（mixin / 结算器用）
    // ============================================================

    /** 该物品是否带超位魔法 */
    public static boolean has(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var tool = ToolHelper.getToolStack(stack);
        return tool != null && tool.getModifierLevel(ID) > 0;
    }

    /** 身上的超位魔法物品（主手/副手/护甲） */
    public static List<ItemStack> itemsWith(LivingEntity entity) {
        List<ItemStack> out = new ArrayList<>(6);
        if (entity == null) return out;
        addIf(out, entity.getMainHandItem());
        addIf(out, entity.getOffhandItem());
        for (ItemStack armor : entity.getArmorSlots()) addIf(out, armor);
        return out;
    }

    private static void addIf(List<ItemStack> out, ItemStack stack) {
        if (has(stack)) out.add(stack);
    }

    /** 这个法术是否刻印在此生物身上的某件超位魔法物品里 */
    public static boolean inscribedFor(LivingEntity entity, String spellId) {
        if (entity == null || spellId == null || spellId.isEmpty()) return false;
        for (ItemStack stack : itemsWith(entity)) {
            if (IronSpellsSpellAccess.inscribedSpellIds(stack).contains(spellId)) return true;
        }
        return false;
    }

    /** 法术强度要乘的倍数（1.0 = 不生效） */
    public static float powerMultiplier(Object spell, Entity caster) {
        if (!(caster instanceof LivingEntity living)) return 1.0F;
        String id = IronSpellsSpellAccess.spellId(spell);
        return inscribedFor(living, id) ? RANGE_MULTIPLIER : 1.0F;
    }

    // ============================================================
    //  「当前这一发是超位魔法」的上下文（由 mixin 打标记）
    // ============================================================

    /** 正在结算的法术 id（不在超位魔法施法过程中则为 null） */
    private static final ThreadLocal<String> CAST_SPELL = new ThreadLocal<>();
    /** 正在施法的人 */
    private static final ThreadLocal<LivingEntity> CAST_CASTER = new ThreadLocal<>();

    /** 施法开始（mixin 在 castSpell / onServerCastComplete 的 HEAD 调用） */
    public static void beginCast(Object spell, LivingEntity caster) {
        try {
            if (caster == null) return;
            String id = IronSpellsSpellAccess.spellId(spell);
            if (!inscribedFor(caster, id)) return;
            CAST_SPELL.set(id);
            CAST_CASTER.set(caster);
        } catch (Throwable ignored) {
        }
    }

    /** 施法结束（mixin 在两处 RETURN 调用） */
    public static void endCast() {
        CAST_SPELL.remove();
        CAST_CASTER.remove();
    }

    /** 当前是否正在结算一次超位魔法施法 */
    public static boolean inSuperTierCast() {
        return CAST_SPELL.get() != null;
    }

    /** 当前这一发的法术 id（不在超位魔法施法中则 null） */
    public static String castingSpell() {
        return CAST_SPELL.get();
    }

    /** 当前这一发的施法者 */
    public static LivingEntity castingCaster() {
        return CAST_CASTER.get();
    }
}
