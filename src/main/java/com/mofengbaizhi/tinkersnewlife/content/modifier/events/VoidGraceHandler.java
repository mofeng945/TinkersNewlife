package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.util.ArmorModifierHelper;
import com.mofengbaizhi.tinkersnewlife.integration.goety_ladder.GoetyLadderCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.List;

/**
 * <b>虚无恩宠</b>（虚空金属盔甲自带，无等级）：免疫虚空块/液态虚空、免疫虚空之蚀与缓慢、虚空伤害 −20%。
 *
 * <h2>四条规格怎么落地（用户口径逐条对应）</h2>
 * <ol>
 *   <li><b>免疫虚空块 &amp; 液态虚空的伤害</b>：{@link #onHurt} —— 只要穿戴者<b>正处在</b>
 *       {@code goety:void_block} / {@code goety:void_fluid} 里（脚下方块或所在方块算 ✓），
 *       而这一发又是虚空系（{@link GoetyLadderCompat#isVoidDamage} ✓）⇒ <b>直接取消</b> ✓；</li>
 *   <li><b>免疫虚空之蚀与缓慢</b>：{@link #onEffectApplicable} 用原版/Forge 的
 *       {@code MobEffectEvent.Applicable} <b>拒绝施加</b> ✓（比"先挂上再每 tick 清掉"干净：
 *       连"挂上那一瞬间的显示/音效"都没有 ✓），{@link #onTick} 再兜底清一次残留 ✓；</li>
 *   <li><b>虚空法术灵魂消耗减半</b>：不在本类 —— 挂在诡厄的 {@code ISpell#SoulCalculation}
 *       （见 {@code mixin/GoetyVoidSoulMixin} ✓），本类只提供 {@link #wears} / {@link #isVoidSpell} 两个判定 ✓；</li>
 *   <li><b>+20% 虚空系伤害抗性</b>：{@link #onHurt} 里把虚空系伤害 ×0.8 ✓（不是免疫，是抗性 ✓）。</li>
 * </ol>
 *
 * <p>虚空系判定优先问阶梯自己（{@link GoetyLadderCompat#isVoidDamage} ✓）；没装阶梯时退回 msgId 兜底 ✓。
 * 护甲损坏时特性不生效（{@link ArmorModifierHelper} 的口径 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VoidGraceHandler {

    /** 特性 id（{@link ArmorModifierHelper} 按字符串查 ✓） */
    public static final String MODIFIER = "void_grace";
    /** 虚空系伤害抗性：20% ✓（用户口径） */
    private static final float VOID_RESISTANCE = 0.20f;

    private VoidGraceHandler() {}

    /** 穿戴者身上有没有「虚无恩宠」（护甲损坏不算 ✓） */
    public static boolean wears(LivingEntity entity) {
        return entity != null && ArmorModifierHelper.hasModifierOnArmor(entity, MODIFIER);
    }

    // ============================================================
    //  ① 免疫虚空之蚀 / 缓慢：拒绝施加
    // ============================================================

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        LivingEntity wearer = event.getEntity();
        if (wearer.level().isClientSide) return;
        if (!wears(wearer)) return;
        if (isImmuneEffect(event.getEffectInstance().getEffect())) {
            event.setResult(Event.Result.DENY);
            // §750 诊断：免疫生效（拒绝施加）✓
            com.mofengbaizhi.tinkersnewlife.util.VoidArmorDiag.log("deny",
                    "虚无恩宠：拒绝施加 {} ✓（免疫生效）",
                    event.getEffectInstance().getEffect().getDescriptionId());
        }
    }

    /** 虚空之蚀（诡厄本体 {@code goety:void_touched}）与缓慢（原版）✓ */
    private static boolean isImmuneEffect(MobEffect effect) {
        if (effect == MobEffects.MOVEMENT_SLOWDOWN) return true;
        MobEffect touched = GoetyLadderCompat.effect(GoetyLadderCompat.VOID_TOUCHED);
        return touched != null && effect == touched;
    }

    /** 兜底：每 40 tick 把已经挂在身上的这两种效果清掉（防止"施加时我们不在场"之类的边角 ✓） */
    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        // ⚠ 这里不能用 `instanceof LivingEntity wearer`：LivingTickEvent#getEntity() 本来就返回 LivingEntity，
        //    写成模式匹配会被 javac 判成"无条件模式"直接报错 ✗（2026-09-27 实测）
        LivingEntity wearer = event.getEntity();
        if (wearer.level().isClientSide) return;
        if (wearer.tickCount % 40 != 0) return;
        if (!wears(wearer)) return;
        // §750 诊断：⭐ 这两条才是"声音来源"的头号嫌疑 —— 我们每清一次，
        //   诡厄就会播一次 VOID_TOUCHED_DEACTIVATE ✓（它自带 activate/loop/deactivate 三个音效 ✓）
        boolean hadSlowness = wearer.hasEffect(MobEffects.MOVEMENT_SLOWDOWN);
        MobEffect touched = GoetyLadderCompat.effect(GoetyLadderCompat.VOID_TOUCHED);
        boolean hadTouched = touched != null && wearer.hasEffect(touched);
        wearer.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (touched != null) wearer.removeEffect(touched);
        if (hadSlowness) {
            com.mofengbaizhi.tinkersnewlife.util.VoidArmorDiag.log("clean:slow",
                    "虚无恩宠：清掉了残留的「缓慢」✓ 玩家={}", wearer.getName().getString());
        }
        if (hadTouched) {
            com.mofengbaizhi.tinkersnewlife.util.VoidArmorDiag.log("clean:touched",
                    "虚无恩宠：清掉了残留的「虚空之蚀」✓ ⭐ 每清一次诡厄就播一次取消音 ⇒ 若这行每 2 秒出现一次，声音就是它 ✓ 玩家={}",
                    wearer.getName().getString());
        }
    }

    // ============================================================
    //  ② 虚空块/液态虚空免疫 ＋ ③ 虚空系减伤 20%
    // ============================================================

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity wearer = event.getEntity();
        if (wearer.level().isClientSide) return;
        if (!wears(wearer)) return;
        if (!GoetyLadderCompat.isVoidDamage(event.getSource())) return;

        // 站在虚空块 / 液态虚空里 ⇒ 完全免疫（用户口径"完全免疫虚空块 & 液态虚空的伤害"✓）
        if (inVoidBlockOrFluid(wearer)) {
            event.setCanceled(true);
            com.mofengbaizhi.tinkersnewlife.util.VoidArmorDiag.log("hurt:cancel",
                    "虚无恩宠：取消了虚空块/液态虚空伤害 ✓ 玩家={} 伤害={} 来源={}",
                    wearer.getName().getString(), event.getAmount(),
                    event.getSource().getMsgId());
            return;
        }
        // 其余虚空系伤害 ⇒ 抗性 20%（不是免疫 ✓）
        event.setAmount(event.getAmount() * (1.0f - VOID_RESISTANCE));
        com.mofengbaizhi.tinkersnewlife.util.VoidArmorDiag.log("hurt:resist",
                "虚无恩宠：虚空系伤害 −20% ✓ 玩家={} 伤害={}→{} 来源={}",
                wearer.getName().getString(), event.getAmount() / (1.0f - VOID_RESISTANCE),
                event.getAmount(), event.getSource().getMsgId());
    }

    /** 脚下那一格或身体所在的那一格是不是虚空块/液态虚空 ✓（两个方块都查，覆盖"站在上面"与"泡在里面"✓） */
    private static boolean inVoidBlockOrFluid(LivingEntity entity) {
        Block voidBlock = GoetyLadderCompat.block(GoetyLadderCompat.VOID_BLOCK);
        Block voidFluid = GoetyLadderCompat.block(GoetyLadderCompat.VOID_FLUID_BLOCK);
        if (voidBlock == null && voidFluid == null) return false;
        Level level = entity.level();
        BlockPos below = entity.blockPosition().below();
        BlockPos at = entity.blockPosition();
        return matches(level.getBlockState(at), voidBlock, voidFluid)
                || matches(level.getBlockState(below), voidBlock, voidFluid);
    }

    private static boolean matches(BlockState state, Block voidBlock, Block voidFluid) {
        return (voidBlock != null && state.is(voidBlock)) || (voidFluid != null && state.is(voidFluid));
    }

    // ============================================================
    //  ④ 给 GoetyVoidSoulMixin 用的判定（mixin 里不写业务逻辑 ✓）
    // ============================================================

    /** 反射缓存：{@code ISpell#getSpellTypes()}（诡厄不硬依赖 ⇒ 反射 ✓） */
    private static Method getSpellTypes;
    private static boolean spellTypesResolved = false;

    /**
     * 这个法术是不是"虚空系"——读诡厄自己的 {@code ISpell#getSpellTypes()} ✓
     * （阶梯的 {@code DamageDetector#isVoidSpellDamage} 也是先看 {@code SpellType.VOID} ✓）。
     * <p>取不到就返回 false（宁可少减，也不要给非虚空法术白减灵魂 ✓）。
     */
    public static boolean isVoidSpell(Object spell) {
        if (spell == null) return false;
        if (!spellTypesResolved) {
            spellTypesResolved = true;
            try {
                getSpellTypes = spell.getClass().getMethod("getSpellTypes");
            } catch (Throwable t) {
                // 接口默认方法在实现类上一定找得到；找不到就说明版本变了 ⇒ 放弃判定 ✓
                try {
                    getSpellTypes = Class.forName("com.Polarice3.Goety.api.magic.ISpell")
                            .getMethod("getSpellTypes");
                } catch (Throwable ignored) {
                    getSpellTypes = null;
                }
            }
        }
        if (getSpellTypes == null) return false;
        try {
            Object result = getSpellTypes.invoke(spell);
            if (result instanceof List<?> types) {
                for (Object type : types) {
                    if (type != null && type.toString().contains("VOID")) return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 虚空法术的灵魂消耗减半系数 ✓（供 {@code GoetyVoidSoulMixin} 用：把 cost 乘这个数 ✓） */
    public static final double VOID_SOUL_COST_FACTOR = 0.5D;
}
