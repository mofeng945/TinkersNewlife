package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.effect.DisarmEffect;
import com.mofengbaizhi.tinkersnewlife.content.effect.FrostEffect;
import com.mofengbaizhi.tinkersnewlife.content.effect.DamageLimitEffect;
import com.mofengbaizhi.tinkersnewlife.content.effect.UnnameableEffect;
import com.mofengbaizhi.tinkersnewlife.content.effect.CharmEffect;
import com.mofengbaizhi.tinkersnewlife.content.effect.SeedParasiteEffect;
import com.mofengbaizhi.tinkersnewlife.content.effect.StunEffect;
import com.mofengbaizhi.tinkersnewlife.content.effect.AntiHealEffect;
import com.mofengbaizhi.tinkersnewlife.content.effect.WhipWeakenEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, TinkersNewlife.MOD_ID);

    public static final RegistryObject<DisarmEffect> DISARM =
            EFFECTS.register("disarm", () -> new DisarmEffect(MobEffectCategory.HARMFUL, 0xFFAA00));

    public static final RegistryObject<FrostEffect> FROST =
            EFFECTS.register("frost", () -> new FrostEffect(MobEffectCategory.HARMFUL, 0x00BFFF));

    public static final RegistryObject<DamageLimitEffect> DAMAGE_LIMIT =
            EFFECTS.register("damage_limit", () -> new DamageLimitEffect(MobEffectCategory.BENEFICIAL, 0x66FF66));

    /** 禁疗（莱万汀命中附加）：LivingHealEvent 拦截任何治疗 */
    public static final RegistryObject<AntiHealEffect> ANTI_HEAL =
            EFFECTS.register("anti_heal", () -> new AntiHealEffect());

    public static final RegistryObject<UnnameableEffect> UNNAMEABLE =
            // §823 用户口径：不可名状改成**中性**效果 ✓ ——
            //   它不是"可以被净化/免疫/减半"的普通负面状态，而是一种"世界本身不对劲"的状态 ✗
            //   （同时它也因此不再被本模组「魔力护盾」的"增益时长减半"之外的任何减益逻辑盯上 ✓）。
            EFFECTS.register("unnameable", () -> new UnnameableEffect(MobEffectCategory.NEUTRAL, 0x4A0E4E));

    public static final RegistryObject<CharmEffect> CHARM =
            EFFECTS.register("charm", () -> new CharmEffect(MobEffectCategory.HARMFUL, 0xFF69B4));

    /** 静止（无量空处）：完全定身 */
    public static final RegistryObject<StunEffect> STUN =
            EFFECTS.register("stun", () -> new StunEffect(MobEffectCategory.HARMFUL, 0x000000));

    /** 咒种寄生（草木操术·咒种）：攻击 -40%，咒力总量/输出 -1 级、亲和 -60 */
    public static final RegistryObject<SeedParasiteEffect> SEED_PARASITE =
            EFFECTS.register("seed_parasite", () -> new SeedParasiteEffect(MobEffectCategory.HARMFUL, 0x3CB371));

    /**
     * 鞭痕（§1064 鞭子专属）✓ —— 用户口径：
     * 「被鞭子抽中的敌人会随着被抽中的次数逐渐降低速度和攻击伤害，最高降低80%，也就是被抽8次，每次降低10%」✓
     * <p>每层 −10% 移动速度与攻击伤害 ✓（{@link WhipWeakenEffect} ✓ 属性修改器实现 ✓），最多
     * {@link WhipWeakenEffect#MAX_STACKS} 层 ✓；颜色取皮革棕 ✓。
     */
    public static final RegistryObject<WhipWeakenEffect> WHIP_WEAKEN =
            EFFECTS.register("whip_weaken", () -> new WhipWeakenEffect(MobEffectCategory.HARMFUL, 0x8B5A2B));

    /**
     * §1064 <b>鞭子抽中时叠加"鞭痕"</b> ✓ ——
     * 每命中一次 ＋1 层 ✓（layer ＝ amplifier ＋ 1 ✓），到 {@link WhipWeakenEffect#MAX_STACKS} 层封顶 ✓
     * （＝ <b>−80%</b> ✓）；每次命中都把时长刷新为 {@link WhipWeakenEffect#DURATION_TICKS} ✓。
     *
     * <p>调用点：① 鞭身逐段扫掠命中（{@code WhipLashEntity#tickLashDamage} ✓）；
     * ② 完美格挡的挥鞭反射（{@code WhipBlockHandler} ✓）—— 都是"被鞭子抽中" ✓。
     */
    public static void applyWhipWeaken(LivingEntity target) {
        if (target == null || target.level().isClientSide) {
            return;
        }
        MobEffectInstance current = target.getEffect(WHIP_WEAKEN.get());
        int layer = current == null ? 0 : Math.min(current.getAmplifier() + 1, WhipWeakenEffect.MAX_STACKS - 1);
        // visible=false（不要每一下都冒粒子 ✓）、showIcon=true（界面上看得见层数 ✓）
        target.addEffect(new MobEffectInstance(
                WHIP_WEAKEN.get(), WhipWeakenEffect.DURATION_TICKS, layer, false, false, true));
    }
}
