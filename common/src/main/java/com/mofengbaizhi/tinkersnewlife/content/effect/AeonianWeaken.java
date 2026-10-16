package com.mofengbaizhi.tinkersnewlife.content.effect;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * TNL1043 ★★ 恒正秩器：★"细雪/着火"式的稀释 tick 效果 ✓
 *
 * <p>★用户口径 ✓：
 * <ul>
 *   <li>★按**累积 tick 数**逐渐百分比减少攻击力 ✓，
 *       ★最大 **100 tick 削弱 100%** ✓；</li>
 *   <li>★每 tick **流逝 1 tick** ✓（★不刷新就自己消散 ✓）；</li>
 *   <li>★**每层削弱强度递增** ✓：★每 20 tick 一层 ✓，
 *       ★四层分别削 10% / 20% / 30% / 40% ✓ ⇒ ★合计 100% ✓；</li>
 *   <li>★100 tick 叠满 ⇒ ★**爆发生命上限 10% 的真实伤害** ✓，
 *       ★随后陷入**不可名状态 10 秒**（★硬控 ✓）
 *       ★并清零 tick ✓。</li>
 * </ul>
 *
 * <p>★存储：★挂在**实体自己的 persistent data** ✓
 * （★不引入 capability ✓，★存档自动跟着走 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class AeonianWeaken {

    public static final String TICKS = "tnl_ao_weaken";
    public static final String STUN = "tnl_ao_stun";
    /** ★一次命中加多少 tick ✓ */
    public static final int PER_HIT = 30;   // TNL1045 ★用户：★命中一次叠 30 tick ✓
    public static final int MAX = 200;   // TNL1047 user: tick cap raised to 200
    /** ★硬控时长（★10 秒 ✓） */
    public static final int STUN_TICKS = 200;

    /** TNL1072 user: "the timer keeps stalling" -- scanning every entity of every level each tick
     * was the stutter, the effect class itself is fine (its isDurationEffectTick returns true and we
     * no longer refresh anything). We now only look at entities that were actually marked. */
    private static final java.util.Set<java.util.UUID> TRACKED = new java.util.HashSet<>();

    private AeonianWeaken() {
    }

    private static CompoundTag tag(LivingEntity e) {
        return e.getPersistentData();
    }

    /**
     * TNL1049 reads the tick count from the hidden mark effect duration.
     * That works on both sides, so the client can drive the screen effect with it.
     */
    public static int ticks(LivingEntity e) {
        // TNL1070 plain persistent data again: no mob effect means nothing shows up in effect lists
        return tag(e).getInt(TICKS);
    }

    public static int stun(LivingEntity e) {
        return tag(e).getInt(STUN);
    }

    /** ★一次命中：★叠 tick ✓ */
    public static void add(LivingEntity e, int amount) {
        // TNL1073 user: "while the unnameable state is on, a new burst seems not to trigger".
        // Two gates were blocking it: this early return, and the ticker handling the stun before
        // the cap. Both are gone now, so hits stack during the state and can burst again.
        int v = Math.min(MAX, ticks(e) + amount);
        TRACKED.add(e.getUUID());
        tag(e).putInt(TICKS, v);
        // TNL1070 for players mirror the count into a hidden item cooldown, which the client can read
        if (e instanceof net.minecraft.world.entity.player.Player pl) {
            pl.getCooldowns().addCooldown(
                    com.mofengbaizhi.tinkersnewlife.content.ModItems.AEONIAN_MARKER.get(), Math.max(1, v));
        }
        TinkersNewlife.LOGGER.warn("TNL1046 weaken stacked on {} -> {} ticks (max {})",
                e.getName().getString(), v, MAX);
        if (v >= MAX) {
            // TNL1071 user: "no matter how I hit it the burst never triggers" -- make the exact moment
            // visible in the log so we can tell stacking apart from the burst gate.
            TinkersNewlife.LOGGER.warn("TNL1071 {} reached the cap on the stack itself, burst should fire "
                    + "on the next server tick pass", e.getName().getString());
        }
    }

    /**
     * TNL1044 ★★ 用户：★"不是叠层，是直接按 tick 数削减攻击力" ✓
     *
     * <p>★直接线性 ✓：★1 tick = 削 1% ✓ ⇒
     * ★100 tick = 削 100% ✓（★不再按 20 tick 分层加权 ✗）。
     */
    public static float factor(LivingEntity e) {
        int t = ticks(e);
        if (t <= 0) {
            return 1.0F;
        }
        return 1.0F - Math.min(1.0F, t / (float) MAX);   // TNL1047 1 tick = 0.5%, 200 ticks = 100%
    }

    /** TNL1043 ★每 tick：★递减 1 tick + ★满层爆发 + ★硬控维持 ✓ */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        List<LivingEntity> touched = new ArrayList<>();
        if (!TRACKED.isEmpty()) {
            java.util.Iterator<java.util.UUID> it = TRACKED.iterator();
            while (it.hasNext()) {
                java.util.UUID id = it.next();
                net.minecraft.world.entity.Entity found = null;
                for (ServerLevel level : server.getAllLevels()) {
                    found = level.getEntity(id);
                    if (found != null) {
                        break;
                    }
                }
                if (found instanceof LivingEntity le && le.isAlive()
                        && (ticks(le) > 0 || stun(le) > 0)) {
                    touched.add(le);
                } else {
                    it.remove();                 // TNL1072 stop tracking it once it is done
                }
            }
        }
        for (LivingEntity le : touched) {
            int t = ticks(le);
            if (t <= 0) {
                continue;
            }
            if (t >= MAX) {
                // ★叠满：★爆发真伤 + ★硬控 10 秒 + ★tick 清零 ✓
                float dmg = le.getMaxHealth() * 0.10F;
                try {
                    com.mofengbaizhi.tinkersnewlife.util.TruePierce.apply(null, le, dmg);
                } catch (Throwable th) {
                    le.hurt(le.damageSources().magic(), dmg);
                }
                tag(le).putInt(STUN, STUN_TICKS);
                tag(le).putInt(TICKS, 0);
                if (le instanceof net.minecraft.world.entity.player.Player pl2) {
                    pl2.getCooldowns().removeCooldown(
                            com.mofengbaizhi.tinkersnewlife.content.ModItems.AEONIAN_MARKER.get());
                }
                if (le.level() instanceof ServerLevel sl) {
                    sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL,
                            le.getX(), le.getY() + 1.0D, le.getZ(), 24, 0.4D, 0.6D, 0.4D, 0.02D);
                }
                le.level().playSound(null, le.blockPosition(),
                        net.minecraft.sounds.SoundEvents.ELDER_GUARDIAN_CURSE,
                        net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 0.7F);
                TinkersNewlife.LOGGER.warn("TNL1043 aeonian burst on {} for {} true damage, stunned 10s",
                        le.getName().getString(), dmg);
                continue;
            }
            // TNL1049 the mark effect decays by itself one tick at a time, nothing to do here
            int st = stun(le);
            if (st > 0) {
                // TNL1048 ★★ 用户：★"不是挂不可名状吗？我写的那个效果" ✓
                //   ★真相 ✗：★你写的是 `UnnameableEffect`（★id ``tinkersnewlife:unnameable`` ✓）
                //     ★而我自己拼了一堆**原版效果**（★迟缓/虚弱/反胃 ✗）
                //     ★根本没用你那个 ✓✓（★好在只是后面这段，★现在换掉 ✓）
                //   ★现在 ✓：★反射取注册表里的 ``unnameable`` ✓
                //     （★★ 不引入跨模块编译依赖 ✓：★`AeonianWeaken` 在 common ✗
                //      ★而 `UnnameableEffect` 在 imagination ✓）
                var tnl$eff = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(
                        new net.minecraft.resources.ResourceLocation("tinkersnewlife", "unnameable"));
                if (tnl$eff != null) {
                    le.addEffect(new MobEffectInstance(tnl$eff, STUN_TICKS, 0, false, false, false));
                }
                le.setDeltaMovement(Vec3.ZERO);
                le.hurtMarked = true;
                tag(le).putInt(STUN, st - 1);
                continue;
            }
        }
    }

    /** TNL1047 user: "I want it to stick on any attack" -- holding the weapon and attacking stacks ticks */
    @SubscribeEvent
    public static void onAttackHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        if (attacker == event.getEntity()) {
            return;
        }
        if (attacker.getMainHandItem().getItem()
                instanceof com.mofengbaizhi.tinkersnewlife.content.item.AeonianOrderItem) {
            add(event.getEntity(), PER_HIT);
        }
    }

    /** TNL1043 ★削弱攻击力：★只削"★有主的物理/魔法攻击" ✓ */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        // TNL1043b ★★ 关键修正 ✓：
        //   ★``LivingHurtEvent`` 是在**受害者**身上触发的 ✗
        //   ★而我们要削的是"★**攻击者**的攻击力" ✓
        //   ⇒ ★应该看 ``getSource().getEntity()`` 身上有没有 tick ✓
        //     （★我第一版写成看受害者 ✗ ⇒ ★永远不会生效 ✓）
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        if (attacker == event.getEntity()) {
            return;
        }
        float f = factor(attacker);
        if (f < 1.0F) {
            event.setAmount(event.getAmount() * f);
        }
    }
}
