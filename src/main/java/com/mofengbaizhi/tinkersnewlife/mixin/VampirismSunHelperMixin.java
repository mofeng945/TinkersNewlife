package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.events.HardenedSkinHandler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 「硬化皮肤」的**根因修复**：把血族的"是否正在受阳光伤害"判定直接压成 {@code false} ✓。
 *
 * <p>为什么要动这里（反编译血族 1.10.17 确认 ✓）：
 * <ul>
 *   <li>{@code VampirePlayer#isGettingSundamage} 的值来自
 *       {@code Helper#gettingSundamge(entity, world, profiler)}（每 8 tick 刷新一次缓存 ✓）；</li>
 *   <li>阳光那一整套后果**只有一个入口**：{@code VampirePlayer#onUpdate} 里
 *       {@code if (isGettingSundamage(...)) handleSunDamage(...)}；</li>
 *   <li>而 {@code handleSunDamage} 里同时干了三件事 —— 阳光伤害、**反胃**
 *       （{@code MobEffects.CONFUSION}）、**虚弱**（{@code MobEffects.WEAKNESS}）✓。
 *       ⇒ 只在事件里取消伤害（§705 的做法）挡不住后面两个 ✗，必须从判定这层掐掉 ✓。</li>
 * </ul>
 *
 * <p>只改**玩家**：{@code Helper#gettingSundamge} 对血族怪物也生效，这里用
 * {@code instanceof Player} 把它们排除在外 ✓（怪物该怕太阳还是怕 ✗）。
 *
 * <p>写法口径与 {@code JadeObjectNameMixin} 一致：字符串 {@code targets} 指向第三方类 ✓
 * （血族类名/方法名都不混淆，一份就能适配开发与生产 ✓）；没装血族时因
 * {@code required:false} + {@code defaultRequire:0} 静默不生效 ✓。
 */
@Mixin(targets = "de.teamlapen.vampirism.util.Helper")
public class VampirismSunHelperMixin {

    /** 压制日志节流：同一玩家每 200 tick 最多打一条 ✓（只进 debug.log ✓） */
    private static final int LOG_INTERVAL = 200;

    @Inject(method = "gettingSundamge", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tinkersnewlife$hardenedSkin(LivingEntity entity, LevelAccessor world,
                                                    ProfilerFiller profiler,
                                                    CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Player player)) {
            return;
        }
        if (!HardenedSkinHandler.isProtectedFromSun(player)) {
            return;
        }
        cir.setReturnValue(false);
        if (player.tickCount % LOG_INTERVAL == 0) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.debug(
                    "[硬化皮肤] {}：血族阳光判定已被压制 ⇒ 无阳光伤害、无阳光反胃、无阳光虚弱",
                    player.getName().getString());
        }
    }
}
