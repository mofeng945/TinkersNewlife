package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.ManaShieldTrait;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 巫师套装特性·<b>魔力护盾</b>的"效果时长减半"注入点 ✓。
 *
 * <p>思路：在 {@code LivingEntity#addEffect(MobEffectInstance)} 的 HEAD 上<b>换掉传入的效果实例</b> ✓
 * —— 换成时长减半的副本 ✓（{@code MobEffectInstance.duration} 是 private 且<b>没有 setter</b> ✗，
 * 好在 {@code @ModifyVariable} 可以直接替换参数对象 ✓ 不需要碰私有字段 ✓）。
 *
 * <p>⚠ 为什么不是在 {@code MobEffectEvent.Added} 里做：实测 Forge 1.20.1 的该事件在
 * {@code activeEffects.put(...)} <b>之前</b>触发（{@code LivingEntity} 源码第 929/931 行 ✓）⇒
 * 事件里"移除再重加"会被外层随后的 {@code put} 用原实例<b>覆盖回去</b> ✗（白做 ✓）。
 * 改参数则一定会被写进去 ✓。
 *
 * <p>⚠ 三个入口都要盖 ✓（少一个就漏一部分来源 ✗）：
 * <pre>
 *   m_7292_(MobEffectInstance)Z                     = addEffect(instance)              药水 / 绝大多数 mod ✓
 *   m_147207_(MobEffectInstance, Entity)Z           = addEffect(instance, source)      区域效果云 / 指令来源 ✓
 *   m_147215_(MobEffectInstance, Entity)V           = forceAddEffect(...)              强制施加（绕过 canBeAffected）✓
 * </pre>
 * 三条 SRG 名与描述符均由 {@code srg_to_official_1.20.1.tsrg} <b>逐一核对</b> ✓
 * （{@code m_7292_ (Lnet/minecraft/world/effect/MobEffectInstance;)Z addEffect} ✓ 等 ✓）。
 *
 * <p>本模组没启用 Mixin 注解处理器、refmap 是手写的，而游戏里跑的就是 SRG 名 ✓
 * （与 {@code SuperTierEffectMixin} 同一套做法 ✓ 注入失败也只会退回"时长不减半"✓
 * 配置 {@code defaultRequire: 0} ✓ 不会崩 ✓）。
 */
@Mixin(LivingEntity.class)
public class ManaShieldEffectMixin {

    @ModifyVariable(method = "m_7292_(Lnet/minecraft/world/effect/MobEffectInstance;)Z",
            at = @At("HEAD"), argsOnly = true, remap = false)
    private MobEffectInstance tnManaShieldHalveDuration(MobEffectInstance instance) {
        return ManaShieldTrait.halveDurationIfShielded((LivingEntity) (Object) this, instance);
    }

    /** 区域效果云 / 带来源的施加路径 ✓ */
    @ModifyVariable(method = "m_147207_(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), argsOnly = true, remap = false)
    private MobEffectInstance tnManaShieldHalveDurationSourced(MobEffectInstance instance) {
        return ManaShieldTrait.halveDurationIfShielded((LivingEntity) (Object) this, instance);
    }

    /** 强制施加（绕过 canBeAffected ✓ 指令等走这条 ✓） */
    @ModifyVariable(method = "m_147215_(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), argsOnly = true, remap = false)
    private MobEffectInstance tnManaShieldHalveDurationForced(MobEffectInstance instance) {
        return ManaShieldTrait.halveDurationIfShielded((LivingEntity) (Object) this, instance);
    }
}
