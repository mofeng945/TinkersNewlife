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
 * 手持天逆鉾攻击【凋灵】（无视出生无敌）或【诡厄巫法受限 Boss（亚波伦/使徒类）】时，
 * 取消原版攻击流程，直接结算伤害：
 * <ul>
 *   <li>受限 Boss：黑曜石柱保护被【直接碎掉】（命中即碎 64 格内其归属柱，走诡厄死亡流程
 *       → 使徒 1 分钟召柱冷却）；随后走全额结算（首段 hurt 事件 + 差额直补 + 低血处决兜底），
 *       单次 20 上限/各类免伤窗不再能减少总伤害，主世界亚波伦/使徒可全额击穿限伤；</li>
 *   <li>凋灵等非诡厄目标：多段 ≤19 直伤。</li>
 * </ul>
 * 全程以正常 hurt 管线为主（受击事件/阶段照常），致死死亡流程/掉落正常。
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

        // 取消原版攻击，改由天逆鉾直接结算（无视无敌帧/凋灵出生无敌/诡厄巫法限伤与柱保护）
        event.setCanceled(true);
        player.swing(InteractionHand.MAIN_HAND);

        // 基础伤害 = 玩家攻击力属性（含天逆鉾 24 点与力量等）；亡灵额外 +6（凋灵为亡灵）
        float dmg = (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (target.getMobType() == MobType.UNDEAD) {
            dmg += 6.0F;
        }
        pierceDamage(player, target, dmg);
    }

    /**
     * 受限 Boss 走 {@link GoetyBridge#pierceFullDamage}（hurt 首段保事件 + 差额直补 + 低血处决，
     * 单次上限/免伤窗不影响总伤害，主世界可全额击穿限伤；下界 30tick 免疫窗为启示录硬设计，
     * 该阶段伤害按窗口节奏结算但 Boss 最终可被击杀）。
     * 其他目标（凋灵等）：多段 ≤19 直伤。
     */
    private static final float PIERCE_CHUNK = 19.0F;

    private static void pierceDamage(ServerPlayer player, LivingEntity target, float dmg) {
        bypassWitherInvuln(target);
        int obsidianInvulBackup = GoetyBridge.readObsidianInvul(target);
        boolean pillarShattered = false;
        if (obsidianInvulBackup > 0) {
            // 天逆鉾穿透黑曜石柱保护：保护柱直接碎掉（柱死 → 使徒 1 分钟内召不出新柱）
            pillarShattered = GoetyBridge.shatterProtectingPillars(target);
        }
        if (GoetyBridge.isGoetyApostle(target)) {
            // 诡厄受限 Boss：全额穿透（hurt 事件 + 差额直补，总量精确全额、不受免疫窗/单次上限影响）
            GoetyBridge.pierceFullDamage(target, dmg);
        } else {
            // 其他（如凋灵）：多段 hurt 直伤
            net.minecraft.world.damagesource.DamageSource src = GoetyBridge.truePierceSource(target.level());
            if (src == null) src = target.damageSources().genericKill();
            float remaining = dmg;
            int guard = 0;
            while (remaining > 0 && target.isAlive() && !target.isRemoved() && guard++ < 64) {
                float part = Math.min(remaining, PIERCE_CHUNK);
                target.invulnerableTime = 0;
                target.hurt(src, part);
                remaining -= part;
            }
        }
        // 柱已碎 → 保持保护计时清零（使徒要等召柱冷却，Boss 对全员敞开）；
        // 未碎柱（没被保护 / 残余保护但无柱可碎）→ 还原保护计时，避免同 tick 误伤窗口
        if (!pillarShattered && target.isAlive() && !target.isRemoved()) {
            GoetyBridge.setObsidianInvul(target, obsidianInvulBackup);
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
