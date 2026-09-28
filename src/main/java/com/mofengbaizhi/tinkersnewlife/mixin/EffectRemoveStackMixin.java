package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.util.VoidArmorDiag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>「谁在移除夜视」的**最后一层**点名</b>（§767，纯诊断 ✓）
 *
 * <h2>为什么加它（§766 的未解之谜 ✗）</h2>
 * §766 的调用栈显示夜视是被这样清掉的 ✓：
 * <pre>
 * LivingEntity#m_21195_  (removeEffect)  ⇐  LivingEntity#m_8119_  (tick)  ⇐  Player#m_8119_  (tick)
 * </pre>
 * 可是：<br>
 * ① Forge 源码（{@code build/tmp-mcsrc/mcfull} ✓）里 {@code LivingEntity}/{@code Player} **根本没有调用
 * {@code removeEffect} 的地方** ✗；<br>
 * ② 383 个模组全扫一遍（"同时含 {@code removeEffect} ＋ tick ＋ LivingEntity/Player"的类 ✓）**没有**这样的 mixin ✗；
 * <p>⇒ 结论只能是：**有模组直接改字节码**（不是 Mixin ✓）✗ —— 这个包里确实有这种
 * （启动日志里 {@code EndingLibrary} 就写着 {@code Modified Util#getMillis()J in class …} ✓）。
 *
 * <h2>这一层怎么抓</h2>
 * 钩**方法本身**（{@code LivingEntity#removeEffect(MobEffect)} ＝ SRG {@code m_21195_} ✓）而不是某个调用点 ✓
 * ⇒ 不管那段调用是**谁在什么时候插进去的**（Mixin ✓ / 直接 ASM ✓ / CoreMod ✓），只要它最终落到这个方法上，
 * 我们就一定能抓到 ✓✓。
 * <p>命中条件：只记 **{@code minecraft:night_vision}** ✓（别的效果一律不记 ✗ 免得刷屏 ✓），
 * 并且用 {@link VoidArmorDiag#fullStack(int)} 打**什么都不跳过的完整栈** ✓
 * ⇒ {@code handler$…} 帧 ✓、{@code TransformStore} 帧 ✓、直接改字节码那家的类名 ✓ 全都在里面 ✓。
 *
 * <p>⚠ 纯诊断：只在 {@code VoidArmorDiag.ENABLED} 为 true 时工作 ✓，**不改任何行为** ✓；
 * 排查完和 §750～§766 那批一起清掉 ✓。
 */
@Mixin(LivingEntity.class)
public class EffectRemoveStackMixin {

    @Inject(method = "m_21195_", at = @At("HEAD"), remap = false, require = 1)
    private void tinkersnewlife$stackOnRemoveEffect(MobEffect effect, CallbackInfo ci) {
        try {
            if (!VoidArmorDiag.ENABLED || effect == null) return;
            ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            if (id == null || !"minecraft:night_vision".equals(id.toString())) return;
            VoidArmorDiag.log("stack:removeEffect:night_vision",
                    "🧱 removeEffect(minecraft:night_vision) 的**完整调用栈**（含 mixin/ASM 痕迹 ✓）{}",
                    VoidArmorDiag.fullStack(26));
        } catch (Throwable ignored) {
            // 诊断本身绝不许影响游戏 ✗
        }
    }
}
