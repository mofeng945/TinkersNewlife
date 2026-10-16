package com.mofengbaizhi.tinkersnewlife.content.entity.ktulu;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TNL867 ★★ 最终限伤（★两道事件都截 ✓）
 *
 * <p>★为何要两道：★某些模组会直接调 {@code actuallyHurt}/写血 ✗
 * ⇒ ★只截一道可能被跳过 ✓。
 * <p>★优先级都是 LOWEST ⇒ 保证在自家群星之子（1.3^级）之后 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class d {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        try {
            if (!(event.getEntity() instanceof KtuluMomo boss) || boss.level().isClientSide) {
                return;
            }
            float in = event.getAmount();
            float out = a.qA(boss, in);
            a.qB("HURT", event.getSource().getMsgId(), in, "->", out, a.qC(boss));
            event.setAmount(out);
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        try {
            if (!(event.getEntity() instanceof KtuluMomo boss) || boss.level().isClientSide) {
                return;
            }
            float in = event.getAmount();
            float out = a.q9(boss, in, boss.tickCount);
            a.qB("DAMAGE", event.getSource().getMsgId(), in, "->", out, a.qC(boss));
            event.setAmount(out);
            if (out > 0.0F) {
                boss.invulnerableTime = a.V0;
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * TNL910 ★★ 事件级检测（★用户提示：
     * ★"你是不是免疫窗有写取消受击事件和阻止改血的逻辑，检测不到啊" ✓）
     *
     * <p>★对：★若上游把 {@code LivingAttackEvent} 取消了 ✗
     * （★比如无下限/绝对防御那类逻辑 ✗），
     * ★{@code hurt()} **根本不会跑** ✗ ⇒ ★我埋在 hurt/q2 里的检测
     * ★永远不会亮 ✓。
     * ★现在：★直接听事件 ✓（★LOWEST 保证看到最终结果 ✓）
     *   · ★``LivingAttackEvent`` 被取消 ✓
     *   · ★``LivingHurtEvent`` 量被压到 &lt;= 0 ✓
     * ⇒ ★都算"被挡下"✗ ⇒ ★发闪色包 ✓
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        try {
            if (!(event.getEntity() instanceof KtuluMomo boss) || boss.level().isClientSide) {
                return;
            }
            if (event.isCanceled()) {
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL910 attack cancelled upstream: {}", event.getSource().getMsgId());
                boss.tnl$shieldFlash();
            }
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurtZero(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        try {
            if (!(event.getEntity() instanceof KtuluMomo boss) || boss.level().isClientSide) {
                return;
            }
            if (event.getAmount() <= 0.0F) {
                boss.tnl$shieldFlash();
            }
        } catch (Throwable ignored) {
        }
    }
}
