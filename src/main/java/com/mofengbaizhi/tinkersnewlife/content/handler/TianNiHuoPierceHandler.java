package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.TianNiHuoItem;
import com.mofengbaizhi.tinkersnewlife.util.GoetyBridge;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 天逆鉾穿透攻击：
 * 手持天逆鉾攻击【凋灵】（无视出生无敌）或【诡厄巫法受限 Boss（亚波伦/使徒类）】（无视其伤害限制）时，
 * 取消原版攻击流程，直接结算伤害——不受无敌帧/伤害上限影响；致死时通过正常 hurt 触发死亡流程（事件/掉落/击败逻辑正常）。
 * 诡厄巫法未安装时受限 Boss 判定为空，此钩子只对凋灵生效，不影响其他目标。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TianNiHuoPierceHandler {

    private TianNiHuoPierceHandler() {}

    /** 凋灵无敌字段（反射清零，映射兼容） */
    private static java.lang.reflect.Field WITHER_INVULN_FIELD = null;

    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof TianNiHuoItem)) return;

        boolean limitedBoss = GoetyBridge.isDamageLimitedBoss(target);
        boolean wither = target instanceof WitherBoss;
        if (!limitedBoss && !wither) return;

        // 取消原版攻击，改由天逆鉾直接结算（无视无敌帧/凋灵出生无敌/诡厄巫法限伤）
        event.setCanceled(true);
        player.swing(InteractionHand.MAIN_HAND);

        // 基础伤害 = 玩家攻击力属性（含天逆鉾 24 点与力量等）；亡灵额外 +6（凋灵为亡灵）
        float dmg = (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (target.getMobType() == MobType.UNDEAD) {
            dmg += 6.0F;
        }
        pierceDamage(player, target, dmg);
    }

    /** 无视无敌与限伤直接造成伤害 */
    private static void pierceDamage(ServerPlayer player, LivingEntity target, float dmg) {
        bypassWitherInvuln(target);
        float hp = target.getHealth();
        float next = Math.max(0.0F, hp - dmg);
        if (next <= 0.0F && target.isAlive()) {
            // 致死：留 1 血后走正常 hurt，触发死亡流程（事件/掉落/该 Boss 的击败逻辑）
            target.setHealth(1.0F);
            DamageSource src = player.damageSources().playerAttack(player);
            target.hurt(src, 1.0E9F);
        } else {
            target.setHealth(next);
        }
        // 命中反馈（粒子 + 受击音）
        if (target.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    10, 0.3, 0.3, 0.3, 0.02);
            sl.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    /** 清零凋灵出生无敌（反射找字段，找不到静默） */
    private static void bypassWitherInvuln(LivingEntity target) {
        if (!(target instanceof WitherBoss w)) return;
        target.invulnerableTime = 0;
        try {
            if (WITHER_INVULN_FIELD == null) {
                try {
                    WITHER_INVULN_FIELD = WitherBoss.class.getDeclaredField("invulnerableTime");
                } catch (NoSuchFieldException e) {
                    WITHER_INVULN_FIELD = WitherBoss.class.getDeclaredField("invulnTime");
                }
                if (WITHER_INVULN_FIELD != null) {
                    WITHER_INVULN_FIELD.setAccessible(true);
                }
            }
            if (WITHER_INVULN_FIELD != null) {
                WITHER_INVULN_FIELD.setInt(w, 0);
            }
        } catch (Throwable ignored) {
        }
        try {
            w.setInvulnerable(false);
        } catch (Throwable ignored) {
        }
    }
}
