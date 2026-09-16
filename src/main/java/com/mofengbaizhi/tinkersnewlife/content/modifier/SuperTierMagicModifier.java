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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 铁魔法联动特性·<b>超位魔法</b>（材料「魔金」工具自带，<b>无等级</b>）：
 *
 * <ol>
 *   <li><b>工具内可以注入法术</b>：背包 tick 给工具补一个<b>空法术容器</b> ✓；</li>
 *   <li><b>刻印的法术强度提升到"该法术自身等级上限 ×2"</b>（{@code ModifySpellLevelEvent}）✓；</li>
 *   <li><b>范围 ×3、持续 ×2</b>：铁魔法里这两项都从 {@code AbstractSpell#getSpellPower} 派生，
 *       所以做一个"放大器 + 三处还原"：
 *       <ul>
 *         <li>{@code mixin.SuperTierPowerMixin} 拦 {@code getSpellPower} ×{@value #RANGE_MULTIPLIER} → <b>范围 ×3</b> ✓；</li>
 *         <li>伤害在 {@code SpellDamageEvent} 里 ÷3 还原 ✓（法术 id 从 {@code SpellDamageSource.spell()} 取 ✓）；</li>
 *         <li>治疗与状态时长在 {@code mixin.SuperTierEffectMixin} 里还原：
 *             治疗 ÷3 ✓、状态时长 ×(2/3) → 净得 <b>×2</b> ✓。</li>
 *       </ul></li>
 * </ol>
 *
 * <h2>"当前这一发是不是超位魔法"怎么判定（不再用 mixin）</h2>
 * 原方案是 mixin {@code AbstractSpell#castSpell}/{@code onServerCastComplete} 打 {@code ThreadLocal} 标记 ✗ ——
 * <b>实测注入失败</b> ✗：Mixin <b>要求回调声明完整且精确的参数表</b>，
 * 而那两处带着铁魔法的 {@code CastSource}/{@code MagicData} 类型（我们没有编译期依赖 ✗），
 * 用 {@code Object} 接也不被接受 ✗：
 * <pre>
 *   InvalidInjectionException: Invalid descriptor …
 *   Expected (Level;I;ServerPlayer;CastSource;Z;CallbackInfo)V
 * </pre>
 * 改成<b>纯事件方案</b> ✓：挂在铁魔法的 {@code SpellOnCastEvent}（Forge 事件，参数全是 MC 类型 ✓）上，
 * 记下"谁、正在放哪个法术、2 tick 内有效" ✓。即时法术的效果就在这次事件之后同一次调用里结算 ✓，
 * 所以 2 tick 足够覆盖 ✓；读条法术的效果发生在之后的 tick ✗ —— 那种情况恢复成"只放大不还原"（范围/伤害照旧 ✓，
 * 状态时长会是 ×3 而不是 ×2，属可接受偏差 ✓）。
 */
public class SuperTierMagicModifier extends Modifier implements TooltipModifierHook, InventoryTickModifierHook {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "super_tier_magic"));

    /** 刻印法术的强度 = 该法术自身等级上限 × 本值（"超位"= 突破上限） */
    public static final int LEVEL_MULTIPLIER = 2;
    /** 读不到法术上限时的兜底等级 */
    public static final int FALLBACK_LEVEL = 20;
    /** 范围倍数（借道 getSpellPower） */
    public static final float RANGE_MULTIPLIER = 3.0F;
    /** 期望的状态持续倍数 */
    public static final float DURATION_MULTIPLIER = 2.0F;
    /** 状态时长补偿系数：getSpellPower 已经 ×3，这里再 ×(2/3) 才能净得 ×2 ✓ */
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
        tooltip.add(Component.translatable("modifier.tinkersnewlife.super_tier_magic.tip"));
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
    //  「当前这一发是超位魔法」的上下文（事件驱动，不用 mixin）
    // ============================================================

    /** 施法者 UUID → 到期 gameTime；配套记下法术 id */
    private static final Map<UUID, Long> CAST_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, String> CAST_SPELL = new ConcurrentHashMap<>();

    /** 标记窗口（tick）：即时法术的效果紧随事件结算，2 tick 足够 ✓ */
    private static final int CAST_WINDOW = 2;

    /** 由 {@code IronSpellsArcaneHandler} 在铁魔法的 SpellOnCastEvent 上调用 ✓ */
    public static void markCast(LivingEntity caster, String spellId) {
        try {
            if (caster == null || spellId == null || spellId.isEmpty()) return;
            if (!inscribedFor(caster, spellId)) return;
            CAST_UNTIL.put(caster.getUUID(), caster.level().getGameTime() + CAST_WINDOW);
            CAST_SPELL.put(caster.getUUID(), spellId);
        } catch (Throwable ignored) {
        }
    }

    /** 该生物此刻是否正在结算一次超位魔法（用于治疗 ÷3、状态时长 ×2/3） */
    public static boolean isSuperTierCastActive(LivingEntity entity) {
        if (entity == null) return false;
        Long until = CAST_UNTIL.get(entity.getUUID());
        if (until == null) return false;
        if (entity.level().getGameTime() > until) {
            CAST_UNTIL.remove(entity.getUUID());
            CAST_SPELL.remove(entity.getUUID());
            return false;
        }
        return true;
    }

    /** 是否还有任何超位魔法施法在窗口内（伤害补偿的兜底判定用） */
    /**
     * 给施法者挂上"法术强度 ×3"：给 {@code SPELL_POWER} 属性加 <b>+2.0</b>
     * （默认 1.0 → 3.0 = ×3 ✓），transient 修饰符、用独立 UUID ✓，幂等（先移除再加 ✓）。
     *
     * <p>为什么用属性而不是 mixin {@code getSpellPower}：范围/持续时间在铁魔法里都经由
     * {@code getSpellPower → getRadius()/getDuration()} 派生（反汇编确认：火球/黑洞/冰浪都是 ✓），
     * 而 {@code getSpellPower} 读的就是 {@code SPELL_POWER} × 学派法术强度属性 ✓；
     * 改属性是"从源头改"，比拦方法更可靠 ✓（而且能在日志里验证 ✓）。
     */
    public static void applyCastPower(LivingEntity caster) {
        try {
            var attr = IronSpellsSpellAccess.attribute("SPELL_POWER");
            if (attr == null) return;
            var inst = caster.getAttribute(attr);
            if (inst == null) return;
            inst.removeModifier(POWER_MODIFIER_ID);
            inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    POWER_MODIFIER_ID, "super_tier_cast_power", CAST_POWER_BONUS,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
            POWER_APPLIED.put(caster.getUUID(), Boolean.TRUE);
        } catch (Throwable ignored) {
        }
    }

    /** 撤掉"法术强度 ×3"（只对挂过的对象动手，避免每 tick 白扫一遍 ✓） */
    public static void clearCastPower(LivingEntity caster) {
        try {
            if (caster == null || POWER_APPLIED.remove(caster.getUUID()) == null) return;
            var attr = IronSpellsSpellAccess.attribute("SPELL_POWER");
            if (attr == null) return;
            var inst = caster.getAttribute(attr);
            if (inst != null) inst.removeModifier(POWER_MODIFIER_ID);
        } catch (Throwable ignored) {
        }
    }

    /** 续期（读条法术每 tick 调用：把窗口推迟到"读完那一刻还在" ✓） */
    public static void extendCast(LivingEntity caster) {
        try {
            if (caster == null) return;
            if (CAST_UNTIL.containsKey(caster.getUUID())) {
                CAST_UNTIL.put(caster.getUUID(), caster.level().getGameTime() + CAST_WINDOW);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 施法期间的 SPELL_POWER 加成：+2.0 = ×3（与 RANGE_MULTIPLIER 一致 ✓） */
    public static final double CAST_POWER_BONUS = RANGE_MULTIPLIER - 1.0D;
    private static final UUID POWER_MODIFIER_ID = UUID.fromString("c4e1a8d2-6b35-4f70-9a2d-5e8c1f3b7d09");
    private static final Map<UUID, Boolean> POWER_APPLIED = new ConcurrentHashMap<>();

    /** 该生物当前标记的法术 id（没有则 null） */
    public static String currentSpell(LivingEntity caster) {
        return caster == null ? null : CAST_SPELL.get(caster.getUUID());
    }

    public static boolean inSuperTierCast() {
        long now = Long.MIN_VALUE;
        for (Map.Entry<UUID, Long> e : CAST_UNTIL.entrySet()) {
            if (e.getValue() != null && (now == Long.MIN_VALUE || e.getValue() > now)) now = e.getValue();
        }
        return now != Long.MIN_VALUE;
    }
}
