package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.util.VoidArmorDiag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>诡厄「效果施加／移除」的全程监视</b>（§754 起，§756 起改为<b>无条件记录</b>）——
 * 用来定性"虚空之蚀被反复移除 ⇒ {@code goety:void_touched_deactivate} 一直响"这件事 ✓
 *
 * <h2>为什么不再依赖音效嗅探</h2>
 * 音效嗅探（{@code SoundManagerMixin} / {@code SoundEngineHookMixin}）是**通用**手段 ✓，
 * 但它只能告诉我们"在放哪个音效" ✓，**说不出"是谁把效果清掉的"** ✗。
 * 这个 mixin 走的是**模组自己的类** ✓：
 * {@code com.Polarice3.Goety.common.events.PotionEvents} 的四个 **Forge 事件处理函数**
 * （诡厄自有方法名 ⇒ 不会被 reobf 改名 ✓ 与 {@code GoetyCastSpeedMixin} 同一套路 ✓ 已实机验证有效 ✓）。
 *
 * <h2>诡厄自己怎么放这三个音效（javap 实核 ✓ 2.5.57.3）</h2>
 * <ul>
 *   <li>{@code PotionAddedEvents}（{@code MobEffectEvent.Added}）→ {@code ModSounds.VOID_TOUCHED_ACTIVATE} ✓</li>
 *   <li>{@code PotionRemoveEvents}（{@code MobEffectEvent.Remove}）→ {@code ModSounds.VOID_TOUCHED_DEACTIVATE} ✓</li>
 *   <li>{@code PotionExpiredEvents}（{@code MobEffectEvent.Expired}）→ {@code ModSounds.VOID_TOUCHED_DEACTIVATE} ✓</li>
 *   <li>{@code PotionApplicationEvents}（{@code MobEffectEvent.Applicable}）→ 诡厄自己的免疫标签判定 ✓</li>
 * </ul>
 *
 * <h2>⚠ §756：为什么从"只看虚空之蚀"改成"**全都记**"</h2>
 * §755 那一轮实测：<b>四个注入点全部匹配成功</b>（{@code require = 1} 没报错 ✓），
 * 但**一条「虚空之蚀」记录都没有** ✗ —— 与此同时客户端日志里
 * {@code goety:void_touched_deactivate} 却在**每秒响一次** ✗✗。
 * ⇒ 说明我把 {@code isTouched(...)} 当作"唯一入账条件"这件事本身就是盲点 ✗：
 * 只要判断不成立（例如 {@code getEffectInstance()} 为空 ✗、或效果 id 与预期不同 ✗），
 * 就会**一行都不留** ✗ ⇒ 又变成"什么都看不见" ✗。
 * <b>⇒ 现在改成：无条件记录每个事件 ＋ 效果 id ＋ 实体 ＋ 是不是我们要盯的那个</b> ✓。
 * 每个 {@code 事件|效果id|实体} 组合仍由 {@link VoidArmorDiag} 每 5 秒限流一行 ✓，不会刷屏 ✓。
 */
@Mixin(targets = "com.Polarice3.Goety.common.events.PotionEvents", remap = false)
public class GoetyVoidTouchedMixin {

    /** 我们要盯的效果 id ✓（诡厄本体注册的就是这个 ✓） */
    private static final String TOUCHED = "goety:void_touched";

    /** 空值安全的"效果 id" ✓（§756：绝不允许"取不到"变成"什么都不记" ✗） */
    private static String effectId(MobEffectInstance instance) {
        if (instance == null) return "（无效果实例 ✗）";
        MobEffect effect = instance.getEffect();
        if (effect == null) return "（效果对象为空 ✗）";
        try {
            return String.valueOf(BuiltInRegistries.MOB_EFFECT.getKey(effect));
        } catch (Throwable t) {
            return "（取 id 抛异常：" + t.getClass().getSimpleName() + "）";
        }
    }

    private static boolean isTouched(MobEffectInstance instance) {
        return TOUCHED.equals(effectId(instance));
    }

    private static String who(MobEffectEvent event) {
        try {
            Entity e = event.getEntity();
            return e == null ? "?" : (e.getName().getString() + "[" + e.getType() + "]");
        } catch (Throwable t) {
            return "（取实体抛异常：" + t.getClass().getSimpleName() + "）";
        }
    }

    /** 有人**正要**被挂上某个效果 ⇒ 我们的免疫应该在这里拒绝它（{@code PotionEvents} 自己也有免疫标签 ✓） */
    @Inject(method = "PotionApplicationEvents", at = @At("HEAD"), remap = false, require = 1)
    private static void tinkersnewlife$watchApplicable(MobEffectEvent.Applicable event, CallbackInfo ci) {
        try {
            if (!VoidArmorDiag.ENABLED) return;
            String id = effectId(event.getEffectInstance());
            VoidArmorDiag.log("potion:applicable:" + id + ":" + who(event),
                    "👁 效果【正要施加】{} ✓ 目标={}{}", id, who(event),
                    isTouched(event.getEffectInstance()) ? " ⭐ 这是我们要盯的虚空之蚀 ✓" : "");
        } catch (Throwable t) {
            VoidArmorDiag.log("sniff:error:potion", "🐞 嗅探异常(PotionEvents.Applicable) {}", String.valueOf(t));
        }
    }

    /** 某个效果**挂上了** ⇒ 诡厄这一刻播 {@code VOID_TOUCHED_ACTIVATE}（若是虚空之蚀 ✓） */
    @Inject(method = "PotionAddedEvents", at = @At("HEAD"), remap = false, require = 1)
    private static void tinkersnewlife$watchAdded(MobEffectEvent.Added event, CallbackInfo ci) {
        try {
            if (!VoidArmorDiag.ENABLED) return;
            MobEffectInstance ins = event.getEffectInstance();
            String id = effectId(ins);
            String extra = "";
            try {
                if (ins != null) extra = "（时长 " + ins.getDuration() + " tick ／ 等级 " + (ins.getAmplifier() + 1) + "）";
            } catch (Throwable ignored) {
            }
            VoidArmorDiag.log("potion:added:" + id + ":" + who(event),
                    "➕ 效果【挂上了】{} ✓ 目标={} {}{}", id, who(event), extra,
                    isTouched(ins) ? " ⭐ 诡厄会播「激活音」✓（反复出现＝有东西在反复挂它）" : "");
        } catch (Throwable t) {
            VoidArmorDiag.log("sniff:error:potion", "🐞 嗅探异常(PotionEvents.Added) {}", String.valueOf(t));
        }
    }

    /** ⭐ 某个效果**被移除** ⇒ 诡厄这一刻播 {@code VOID_TOUCHED_DEACTIVATE}（若是虚空之蚀 ✓） */
    @Inject(method = "PotionRemoveEvents", at = @At("HEAD"), remap = false, require = 1)
    private static void tinkersnewlife$watchRemoved(MobEffectEvent.Remove event, CallbackInfo ci) {
        try {
            if (!VoidArmorDiag.ENABLED) return;
            MobEffectInstance ins = event.getEffectInstance();
            String id = effectId(ins);
            String fromType = "";
            // §757：⚠ "按类型移除"（removeAllEffects 那条路）**实例是 null** ✗ ——
            //   这正是 §756 里那 205 条「（无效果实例 ✗）」的真相 ✓ ⇒ 这里补读 getEffect() ✓
            if (ins == null) {
                try {
                    MobEffect onlyType = event.getEffect();
                    fromType = "（按类型移除 → " + (onlyType == null ? "类型也取不到 ✗"
                            : String.valueOf(BuiltInRegistries.MOB_EFFECT.getKey(onlyType))) + " ✓）";
                } catch (Throwable t) {
                    fromType = "（取类型抛异常：" + t.getClass().getSimpleName() + "）";
                }
            }
            VoidArmorDiag.log("potion:removed:" + id + fromType + ":" + who(event),
                    "➖ 效果【被移除】{} {} ✓ 目标={}{}", id, fromType, who(event),
                    isTouched(ins)
                            ? " ⭐⭐ 诡厄会播「取消音」✓（若每秒一行＝那个一直在响的声音就是它 ✓）" : "");
            // §757：第一次见到"按类型移除" ⇒ 打一条调用栈，直接点名**是谁在批量清效果** ✓
            if (ins == null) {
                VoidArmorDiag.log("potion:remove:type-stack",
                        "🧭 「按类型移除效果」的**发起方**调用栈（首次出现）{}", VoidArmorDiag.shortStack(8));
            }
        } catch (Throwable t) {
            VoidArmorDiag.log("sniff:error:potion", "🐞 嗅探异常(PotionEvents.Remove) {}", String.valueOf(t));
        }
    }

    /** 某个效果**自然过期** ⇒ 若为虚空之蚀同样播取消音 ✓ */
    @Inject(method = "PotionExpiredEvents", at = @At("HEAD"), remap = false, require = 1)
    private static void tinkersnewlife$watchExpired(MobEffectEvent.Expired event, CallbackInfo ci) {
        try {
            if (!VoidArmorDiag.ENABLED) return;
            String id = effectId(event.getEffectInstance());
            VoidArmorDiag.log("potion:expired:" + id + ":" + who(event),
                    "⌛ 效果【自然过期】{} ✓ 目标={}{}", id, who(event),
                    isTouched(event.getEffectInstance())
                            ? " ⭐⭐ 诡厄会播「取消音」✓（短时长被反复续＝也会一直响 ✓）" : "");
        } catch (Throwable t) {
            VoidArmorDiag.log("sniff:error:potion", "🐞 嗅探异常(PotionEvents.Expired) {}", String.valueOf(t));
        }
    }
}
