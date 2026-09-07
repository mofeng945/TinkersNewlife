package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.YouYunItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 咒具「游云」攻击管线：手持游云攻击活物时，取消原版攻击流程，改结算一段<b>无敌甲的物理伤害</b>：
 * <ul>
 *   <li>基础伤害 = 玩家当前近战伤害（{@code ATTACK_DAMAGE}，含游云 10 点与力量/锋利等加成）</li>
 *   <li>固定增伤 120%（×2.2）</li>
 *   <li>对亡灵生物额外 +10%（×1.1）</li>
 *   <li>破甲：目标正在格挡（举盾）时立即使其盾失效（类似原版斧头破盾格挡，非无视护甲）</li>
 *   <li>额外击退：本次伤害 / 100 格（沿攻击者→目标水平方向）</li>
 * </ul>
 * 游云<b>不能</b>穿透无下限·无限（{@code ignoresInfinity=false}），因此打在开启无限的玩家身上会被其减免。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class YouYunAttackHandler {

    private YouYunAttackHandler() {}

    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof YouYunItem)) return;

        // 获取玩家当前近战伤害（含游云基础 10 与力量/锋利等）
        float dmg = (float) player.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (dmg <= 0) return;
        // 对亡灵生物额外 +10%
        if (target.getMobType() == MobType.UNDEAD) {
            dmg *= 1.1f;
        }
        // 固定增伤 120%
        dmg *= 2.2f;

        // 取消原版攻击，改结算破盾物理伤害（确保"每一击破盾格挡"且总量为放大后的值）
        event.setCanceled(true);
        player.swing(InteractionHand.MAIN_HAND);

        target.hurt(player.level().damageSources().playerAttack(player), dmg);

        // 破甲 = 突破盾牌格挡（类似原版斧头）：目标正在格挡则立即使其盾失效
        if (target.isBlocking()) {
            try {
                LivingEntity.class.getMethod("disableShield").invoke(target);
            } catch (Throwable ignored) {
                // 方法签名/映射差异时静默：至少本次伤害已结算
            }
        }

        // 额外击退：本次伤害 / 100 格（水平方向）
        double kb = dmg / 100.0;
        if (kb > 0 && target.isAlive()) {
            Vec3 dir = target.position().subtract(player.position());
            double len = Math.hypot(dir.x, dir.z);
            if (len > 0.001) {
                target.knockback(kb, dir.x / len, dir.z / len);
                if (target instanceof ServerPlayer sp) {
                    sp.hurtMarked = true;   // 玩家目标需显式标记（同堕落词条）
                }
            }
        }

        // 命中反馈
        if (target.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    8, 0.3, 0.3, 0.3, 0.02);
            sl.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 1.0F, 1.2F);
        }
    }
}
