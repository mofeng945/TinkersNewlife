package com.mofengbaizhi.tinkersnewlife.util;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * <b>万能穿透</b>：本模组所有"真伤/穿透"路径的**唯一实现**。
 *
 * <p>以前有三套各写各的：穿透EX（自有 {@code true_pierce} 源 + 两段式）、
 * 天逆鉾（Goety 真伤源 + 分块连打 / 差额直补）、墨默（直接调 {@code GoetyBridge.pierceFullDamage}）。
 * 现在统一到这里，任何武器（天逆鉾 / 游云 / 带穿透EX 的工具 / 墨默的挥击）都调 {@link #apply}。
 *
 * <h2>为什么"穿透"要分两层做</h2>
 * <ol>
 *   <li><b>{@code hurt()} 内部</b>（无敌、护甲、抗性、无敌帧、盾牌…）→ 靠伤害源的
 *       {@code #minecraft:bypasses_*} 标签穿。本类默认用 Goety 的真伤源（拿不到时用本模组的
 *       {@code tinkersnewlife:true_pierce}，两者都带 {@code bypasses_invulnerability}）。</li>
 *   <li><b>事件层</b>（别的 mod 在 {@code LivingHurtEvent}/{@code LivingDamageEvent} 里改数值，
 *       例如 akaishi 把监守者单次受击压到 24、启示录给亚波伦加单次 20 上限）→ 标签**完全无用**。
 *       对策是两条：① 每段打击前 {@link #markIntent 登记"想造成多少"}，在 LOWEST 优先级把数值放回去；
 *       ② 打完按"应打 − 已打"的**差额直接 setHealth 补齐**。</li>
 * </ol>
 *
 * <h2>做法（顺序即优先级）</h2>
 * <pre>
 *   ⓪ 绝对防御穿透：把"不看伤害标签、只看攻击者属性"的免疫临时失效
 *      （{@link AbsoluteDefense}：潘多拉之咒·现实压制在 LivingAttackEvent 里直接取消打击，
 *       标签与事件层顶开都够不着 → 只能把攻击者的"现实指数"临时抬过阈值）
 *   ① 命灯指轮共存：攻击者戴着命灯 → 本次最多打到"只剩一点血"（不杀死）
 *   ② 先禁疗 + 清掉本模组自己的"伤害限幅"效果（否则会把多段伤害吞掉）
 *   ③ 下界亚波伦这类"hurt() 管线全被取消"的 Boss → 直接 setHealth 扣血 + 主动 die()
 *   ④ 其余目标：分块连打（每段 ≤19，上限 64 段），每段登记意图；
 *      若"带攻击者的源"完全打不动 → 换**无主源**再打一遍（Mowzie 钢铁守护者那类"有攻击者即免疫"）
 *   ⑤ 差额直补：被免伤窗/限伤吃掉的部分直接按血量差补上；致死时补 die()，让掉落与击败逻辑正常走
 * </pre>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TruePierce {

    private TruePierce() {
    }

    /** 本模组的真伤类型（带 7 个 bypasses_* 标签，见 data/minecraft/tags/damage_type/） */
    private static final ResourceKey<DamageType> PIERCE_TYPE_KEY = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(TinkersNewlife.MOD_ID, "true_pierce"));

    private static volatile Holder<DamageType> cachedPierceType = null;

    /** 单段伤害上限：低于常见的"单次限伤"阈值，保证每段都能落地 */
    private static final float CHUNK = 19.0F;
    /** 分块段数上限（19 × 64 ≈ 1200 点，足够任何 Boss） */
    private static final int MAX_CHUNKS = 64;

    /** 一次穿透"想造成多少"（key = 目标 UUID，带 tick 防残留） */
    private record Intent(float amount, long tick) {
    }

    private static final Map<UUID, Intent> INTENT = new ConcurrentHashMap<>();

    // ============================================================
    //  对外入口
    // ============================================================

    /**
     * 万能穿透：{@code attacker} 对 {@code target} 造成 {@code damage} 点"真伤"。
     *
     * @param attacker 攻击者（可为 null：无主穿透，来源归属会丢失，但能穿"有攻击者即免疫"的目标）
     * @param target   受击者
     * @param damage   期望造成的伤害（会被逐段结算 + 差额补齐）
     */
    public static void apply(@Nullable LivingEntity attacker, LivingEntity target, float damage) {
        if (target == null || damage <= 0.0F) return;
        if (target.level().isClientSide || target.isRemoved()) return;
        // ⭐ 同心戒：互为同伴的两名玩家互相免疫术式与领域效果 —— 穿透（真伤）也不例外。
        //    必须挡在这里：本方法是"差额直接 setHealth 补齐"的，事件层（LivingAttackEvent）拦不住它 ✗
        if (com.mofengbaizhi.tinkersnewlife.content.curse.TwinRingLink.arePaired(attacker, target)) return;

        // ⓪ 绝对防御穿透：把"根本不看伤害标签、只看攻击者属性"的免疫（潘多拉之咒·现实压制）
        //    临时失效 —— 它是在 LivingAttackEvent 里直接取消打击的，标签和事件层顶开都够不着。
        //    必须包住整个打击过程，且无论从哪个分支返回都要摘掉临时修饰符。
        boolean transcend = AbsoluteDefense.begin(attacker);
        AbsoluteDefense.noteSunblockTarget(target);
        try {
            applyInner(attacker, target, damage);
        } finally {
            if (transcend) AbsoluteDefense.end(attacker);
        }
    }

    private static void applyInner(@Nullable LivingEntity attacker, LivingEntity target, float damage) {
        // ① 穿透是"真伤"：**不受命灯指轮的慈悲效果约束** —— 带穿透的近战/投射物可以照常杀死目标。
        //    命灯那边也有对应的早退（LifeLampRingHandler 见到真伤源直接放行），两端一致、与事件顺序无关。
        float want = damage;


        // ② 禁疗（否则再生会把伤害吃回去）+ 清掉本模组自己的"伤害限幅"效果（它会取消多段伤害）
        suppressRegen(target);
        clearOwnDamageLimit(target);

        DamageSource withAttacker = source(target.level(), attacker);
        DamageSource anonymous = source(target.level(), null);

        // ③ 下界亚波伦：hurt() 管线被它的免疫窗整个取消 → 直接扣血
        if (GoetyBridge.isNetherApollyon(target)) {
            directDamage(target, want, withAttacker);
            return;
        }

        // ④ 分块连打（带攻击者的源）
        float startHp = target.getHealth();
        float dealt = chunkedHurt(target, withAttacker, want);
        // 带攻击者的源完全打不动 → 换无主源再打一遍
        if (dealt <= 0.01F && target.isAlive() && !target.isRemoved()) {
            dealt = chunkedHurt(target, anonymous, want);
        }


        // ⑤ 差额直补
        float shortfall = want - Math.max(dealt, 0.0F);
        if (shortfall <= 0.01F) return;
        if (!target.isAlive() || target.isRemoved()) return;
        float hp = target.getHealth() - shortfall;
        if (hp <= 0.0F) {
            target.setHealth(0.0F);
            if (target.isAlive() && !target.isRemoved()) target.die(withAttacker);
        } else {
            target.setHealth(hp);
        }
        // 顺带：低血阶段的"受击全额回血"免伤（启示录使徒那类），若差额直补也吃不动 → 走处决兜底
        if (GoetyBridge.isGoetyApostle(target) && target.isAlive() && !target.isRemoved()
                && target.getHealth() <= target.getMaxHealth() * 0.18F
                && (startHp - target.getHealth()) < 5.0F) {
            target.setHealth(0.0F);
            if (!target.isRemoved()) target.die(withAttacker);
        }
    }

    // ============================================================
    //  内部实现
    // ============================================================

    /** 分块连打，返回实际造成的血量差（每段都登记意图供事件层顶开） */
    private static float chunkedHurt(LivingEntity target, DamageSource src, float want) {
        float startHp = target.getHealth();
        float remaining = want;
        int guard = 0;
        while (remaining > 0.0F && target.isAlive() && !target.isRemoved() && guard++ < MAX_CHUNKS) {
            float part = Math.min(remaining, CHUNK);
            target.invulnerableTime = 0;
            markIntent(target, part);
            target.hurt(src, part);
            remaining -= part;
        }
        return startHp - target.getHealth();
    }

    /** 不经 hurt() 的直伤（下界亚波伦）：扣血 + 打空后主动 die()，让击败状态机正常结算 */
    private static void directDamage(LivingEntity target, float dmg, DamageSource src) {
        float hp = target.getHealth() - dmg;
        if (hp <= 0.0F) {
            target.setHealth(0.0F);
            if (target.isAlive() && !target.isRemoved()) target.die(src);
        } else {
            target.setHealth(hp);
        }
    }

    private static void suppressRegen(LivingEntity target) {
        try {
            GoetyBridge.suppressApostleRegen(target);
        } catch (Throwable ignored) {
        }
    }

    /** 清掉本模组"伤害限幅"效果：它会把多段穿透伤害整段取消 */
    private static void clearOwnDamageLimit(LivingEntity target) {
        try {
            var dl = com.mofengbaizhi.tinkersnewlife.content.ModEffects.DAMAGE_LIMIT.get();
            if (dl != null && target.hasEffect(dl)) target.removeEffect(dl);
        } catch (Throwable ignored) {
        }
    }

    /** 真伤源：优先用 Goety 那支（部分 Boss 认它），拿不到时退回本模组的 true_pierce */
    private static DamageSource source(net.minecraft.world.level.Level level, @Nullable LivingEntity attacker) {
        if (attacker != null) {
            DamageSource goety = GoetyBridge.truePierceSource(level);
            if (goety != null) {
                return new DamageSource(goety.typeHolder(), attacker, attacker);
            }
            return new DamageSource(pierceType(level), attacker, attacker);
        }
        DamageSource goety = GoetyBridge.truePierceSource(level);
        if (goety != null) return new DamageSource(goety.typeHolder(), null, null);
        return new DamageSource(pierceType(level), null, null);
    }

    private static Holder<DamageType> pierceType(net.minecraft.world.level.Level level) {
        Holder<DamageType> cached = cachedPierceType;
        if (cached == null) {
            cached = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(PIERCE_TYPE_KEY);
            cachedPierceType = cached;
        }
        return cached;
    }

    /** 登记"这一击想造成多少"（供事件层顶开；也可给别的穿透路径复用） */
    public static void markIntent(LivingEntity target, float amount) {
        if (target == null || target.level().isClientSide) return;
        INTENT.put(target.getUUID(), new Intent(amount, target.level().getGameTime()));
    }

    // ============================================================
    //  事件层顶开：在最后一刻把数值按原意放回去
    // ============================================================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPierceHurt(LivingHurtEvent event) {
        force(event.getEntity(), event.getSource(), event::setAmount);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPierceDamage(LivingDamageEvent event) {
        force(event.getEntity(), event.getSource(), event::setAmount);
    }

    private static void force(LivingEntity target, DamageSource source, Consumer<Float> setter) {
        if (target == null || target.level().isClientSide) return;
        Intent it = INTENT.remove(target.getUUID());
        if (it == null || it.tick() != target.level().getGameTime()) return;   // 不是"刚刚那一击"
        if (!isPierceLike(source)) return;
        setter.accept(it.amount());
    }

    /** 真伤源判定：本模组 true_pierce，或任何带 {@code bypasses_invulnerability} 的源（含 Goety 那支） */
    private static boolean isPierceLike(DamageSource source) {
        if (source == null) return false;
        return source.is(PIERCE_TYPE_KEY) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /** 便于外部检查"某个源是不是真伤源" */
    public static boolean isTruePierce(DamageSource source) {
        return isPierceLike(source);
    }
}
