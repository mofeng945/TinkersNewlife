package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import com.mofengbaizhi.tinkersnewlife.content.entity.ktulu.a;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * TNL874 ★★ 从**源头**拦"改血" ✓
 *
 * <p>★用户口径：★"我不是写 dll 就是为了阻止这种事情吗" ✓
 * ⇒ ★说得对：★光靠 DLL 监控，恢复发生在**下一 tick** ✗（★血条会闪 ✗）；
 * ★混入 {@code LivingEntity#setHealth} 则是在**写的那一瞬间**就把值换回权威值 ✓✓。
 *
 * <p>★为何能靠：★{@code setHealth} 是 {@code LivingEntity} **自己声明**的方法 ✓
 * （★不是接口/继承来的 ✗）⇒ ★refmap 能正常生成 ✓。
 * ★写法直接照抄 {@code SuperTierEffectMixin}（★已在用的现成例子 ✓）。
 */
@Mixin(LivingEntity.class)
public class KtuluVitalsMixin {

    // TNL1138 真凶 ✓：原来注入的是 `m_5634_(F)V` ✗ 而 **`setHealth` 是 `m_21153_(F)V`** ✓
    //   （用 javap 在 1.20.1 的 srg jar 上核对过：`m_21153_(float)` ＝ setHealth ✓
    //     而 `m_5634_(float)` 是另一个方法 ✗）⇒ 所以这段保护**从来没跑过** ✓✓
    //   寰宇支配剑正是走 `entity.setHealth(0)`（见其反编译 ✓）⇒ 必须拦在 `m_21153_` 上 ✓
    @ModifyVariable(method = "m_21153_(F)V", at = @At("HEAD"), argsOnly = true, remap = false)
    private float tinkersnewlife$vitalsFloor(float value) {
        try {
            LivingEntity self = (LivingEntity) (Object) this;
            if (!(self instanceof KtuluMomo momo)) {
                return value;
            }
            // TNL1283 分身（拉莱耶之影）**不是 Boss**：血量内核没有它的槽位 ✗
            //   ⇒ 让它走 `a.qD` 会把每次 setHealth 都顶成内核值（= 满血）✗
            //   ⇒ 表现就是"分身掉血但血量永远满" ✓ 所以这里必须跳过 ✓
            if (!momo.tnl$isBossForm()) {
                return value;
            }
            if (self.level().isClientSide) {
                return value;
            }
            return a.qD((net.minecraft.world.entity.Mob) self, value);
        } catch (Throwable ignored) {
            return value;
        }
    }
}
