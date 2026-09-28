package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.util.VoidArmorDiag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>「虚空之蚀」施加／移除的直接监视</b>（§754）—— 用来定性"那个一直在响的声音" ✓
 *
 * <h2>为什么不再依赖音效嗅探</h2>
 * 音效嗅探（{@code SoundManagerMixin} / {@code SoundEngineHookMixin}）是**通用**手段 ✓，
 * 但它依赖 SRG 名注入 ✓ 万一又出岔子就白测一轮 ✗。
 * 这个 mixin 走的是**模组自己的类** ✓：
 * {@code com.Polarice3.Goety.common.events.PotionEvents} 的四个 **Forge 事件处理函数**（诡厄自有方法名 ⇒
 * 不会被 reobf 改名 ✓ 与 {@code GoetyCastSpeedMixin} 同一套路 ✓ 已实机验证有效 ✓）。
 *
 * <h2>诡厄自己怎么放这三个音效（javap 实核 ✓ 2.5.57.3）</h2>
 * <ul>
 *   <li>{@code PotionAddedEvents}（{@code MobEffectEvent.Added}）→ {@code ModSounds.VOID_TOUCHED_ACTIVATE} ✓</li>
 *   <li>{@code PotionRemoveEvents}（{@code MobEffectEvent.Remove}）→ {@code ModSounds.VOID_TOUCHED_DEACTIVATE} ✓</li>
 *   <li>{@code PotionExpiredEvents}（{@code MobEffectEvent.Expired}）→ {@code ModSounds.VOID_TOUCHED_DEACTIVATE} ✓</li>
 *   <li>{@code PotionApplicationEvents}（{@code MobEffectEvent.Applicable}）→ 诡厄自己的免疫标签判定 ✓</li>
 * </ul>
 * <p>⇒ <b>如果"挂在身上 → 被清掉 → 再挂上"在反复发生 ✓，这里就会一行一行地记下来</b> ✓✓，
 * 那就直接证明"声音＝我们每 40 tick 清一次虚空之蚀 ⇒ 诡厄每次都播一次取消音" ✓。
 * <p>⚠ 只记 {@code goety:void_touched} 这一个效果 ✓（其它效果一律不记 ✗ 免得刷屏 ✓）；
 * 同一个 key 仍由 {@link VoidArmorDiag} 每 5 秒限流一行 ✓。
 */
@Mixin(targets = "com.Polarice3.Goety.common.events.PotionEvents", remap = false)
public class GoetyVoidTouchedMixin {

    /** 我们要盯的效果 id ✓（诡厄本体注册的就是这个 ✓） */
    private static final String TOUCHED = "goety:void_touched";

    private static boolean isTouched(MobEffectInstance instance) {
        if (instance == null) return false;
        MobEffect effect = instance.getEffect();
        if (effect == null) return false;
        return TOUCHED.equals(String.valueOf(BuiltInRegistries.MOB_EFFECT.getKey(effect)));
    }

    /** 有人**正要**被挂上虚空之蚀 ⇒ 我们的免疫应该在这里拒绝它（{@code PotionEvents} 自己也有免疫标签 ✓） */
    @Inject(method = "PotionApplicationEvents", at = @At("HEAD"), remap = false, require = 1)
    private static void tinkersnewlife$watchApplicable(MobEffectEvent.Applicable event, CallbackInfo ci) {
        try {
            if (!isTouched(event.getEffectInstance())) return;
            VoidArmorDiag.log("touched:applicable", "👁 虚空之蚀 正在被施加 ✓ 目标={}",
                    event.getEntity() == null ? "?" : event.getEntity().getName().getString());
        } catch (Throwable t) {
            VoidArmorDiag.log("sniff:error:potion", "🐞 嗅探异常(PotionEvents.Applicable) {}", String.valueOf(t));
        }
    }

    /** ⭐ 虚空之蚀**挂上了** ⇒ 诡厄这一刻播 {@code VOID_TOUCHED_ACTIVATE} ✓ */
    @Inject(method = "PotionAddedEvents", at = @At("HEAD"), remap = false, require = 1)
    private static void tinkersnewlife$watchAdded(MobEffectEvent.Added event, CallbackInfo ci) {
        try {
            if (!isTouched(event.getEffectInstance())) return;
            VoidArmorDiag.log("touched:added", "⭐ 虚空之蚀 已挂上 ⇒ 诡厄播「激活音」✓ 目标={}（⭐ 反复出现＝有东西在反复挂它）",
                    event.getEntity() == null ? "?" : event.getEntity().getName().getString());
        } catch (Throwable t) {
            VoidArmorDiag.log("sniff:error:potion", "🐞 嗅探异常(PotionEvents.Added) {}", String.valueOf(t));
        }
    }

    /** ⭐ 虚空之蚀**被移除** ⇒ 诡厄这一刻播 {@code VOID_TOUCHED_DEACTIVATE} ✓（我们每 40 tick 清一次就走这里 ✓） */
    @Inject(method = "PotionRemoveEvents", at = @At("HEAD"), remap = false, require = 1)
    private static void tinkersnewlife$watchRemoved(MobEffectEvent.Remove event, CallbackInfo ci) {
        try {
            if (!isTouched(event.getEffectInstance())) return;
            VoidArmorDiag.log("touched:removed", "⭐ 虚空之蚀 被移除 ⇒ 诡厄播「取消音」✓ 目标={}（⭐ 若每 2 秒一行＝声音就是它 ✓）",
                    event.getEntity() == null ? "?" : event.getEntity().getName().getString());
        } catch (Throwable t) {
            VoidArmorDiag.log("sniff:error:potion", "🐞 嗅探异常(PotionEvents.Remove) {}", String.valueOf(t));
        }
    }

    /** 虚空之蚀**自然过期** ⇒ 同样播取消音 ✓ */
    @Inject(method = "PotionExpiredEvents", at = @At("HEAD"), remap = false, require = 1)
    private static void tinkersnewlife$watchExpired(MobEffectEvent.Expired event, CallbackInfo ci) {
        try {
            if (!isTouched(event.getEffectInstance())) return;
            VoidArmorDiag.log("touched:expired", "⭐ 虚空之蚀 自然过期 ⇒ 诡厄播「取消音」✓ 目标={}",
                    event.getEntity() == null ? "?" : event.getEntity().getName().getString());
        } catch (Throwable t) {
            VoidArmorDiag.log("sniff:error:potion", "🐞 嗅探异常(PotionEvents.Expired) {}", String.valueOf(t));
        }
    }
}
