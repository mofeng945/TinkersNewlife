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
 * 取消原版攻击流程，直接结算伤害——无视无敌帧/伤害上限；
 * 对受限 Boss 额外穿透【黑曜石柱保护】：与墨默"必须手动破柱"不同，天逆鉾命中受保护
 * Boss 时会把保护柱【直接碎掉】（走诡厄正常死亡流程 → 使徒 1 分钟召柱冷却），Boss
 * 本次同时吃满伤害；另穿透【使徒受击无敌帧】与【启示录下界 Apollyon 受击冷却】，
 * 并补偿抵消【下界减伤(50%)】与【附近玩家时非玩家伤害减半】，落血即名义伤害。
 * 全程走正常 hurt 管线（受击事件/阶段照常触发），致死时死亡流程/掉落正常。
 * 诡厄巫法未安装时受限 Boss 判定为空，此钩子只对凋灵生效，不影响其他目标。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TianNiHuoPierceHandler {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TinkersNewlife");

    private TianNiHuoPierceHandler() {}

    /** 诊断：三层伤害事件在哪一层被挡（仅对受限 Boss，最低优先级观察取消状态） */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void diagAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (GoetyBridge.isDamageLimitedBoss(event.getEntity())) {
            LOGGER.info("[DIAG] LivingAttack 目标Boss amount={} 已被取消={}", event.getAmount(), event.isCanceled());
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void diagHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (GoetyBridge.isDamageLimitedBoss(event.getEntity())) {
            var src = event.getSource();
            String st = "?";
            try {
                st = src.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) ? "fire" : src.typeHolder().unwrapKey()
                        .map(k -> k.location().toString()).orElse("unkeyed");
            } catch (Throwable ignored) {
            }
            String direct = src.getDirectEntity() != null ? src.getDirectEntity().getType().toString() : "null";
            String owner = src.getEntity() != null ? src.getEntity().getType().toString() : "null";
            LOGGER.info("[DIAG] LivingHurt 目标Boss amount={} 类型={} direct={} owner={} 取消={}",
                    event.getAmount(), st, direct, owner, event.isCanceled());
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void diagDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (GoetyBridge.isDamageLimitedBoss(event.getEntity())) {
            var src = event.getSource();
            String st = "?";
            try {
                st = src.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) ? "fire" : src.typeHolder().unwrapKey()
                        .map(k -> k.location().toString()).orElse("unkeyed");
            } catch (Throwable ignored) {
            }
            LOGGER.info("[DIAG] LivingDamage 目标Boss amount={} 类型={} 取消={}",
                    event.getAmount(), st, event.isCanceled());
        }
    }

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
        LOGGER.info("[天逆鉾] 攻击目标 {} 手持={} limitedBoss={} wither={}",
                GoetyBridge.debugDescribe(target), held.getItem(), limitedBoss, wither);
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
     * 突破"每次伤害上限"（亚波伦 apollyon_hurt_limit=20 等；主 Goety apostleDamageCap=20
     * 因 genericKill 带 bypasses_invulnerability 天然绕过）：
     * 把伤害拆成 ≤19 的多段连续 hurt（每段走完整伤害管线、受击事件照常，总和突破单次上限）。
     * Boss 处于黑曜石柱保护时：保护柱被天逆鉾【直接碎掉】（正常受伤死亡，触发诡厄
     * 碎裂特效与使徒 1 分钟召柱冷却），Boss 本次照常吃满穿透伤害且不再还原保护计时；
     * 未碎柱时（未被保护/残余保护无柱可碎）才还原 obsidianInvul。
     * 每段前穿透：使徒受击无敌帧 moddedInvul、启示录下界 Apollyon 受击冷却 hitCooldown；
     * 目标为使徒时按 apostleDamageCompensation 放大送出量，抵消下界减伤(50%)与
     * 附近玩家时非玩家伤害减半——落血仍是名义伤害。
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
        // 真伤伤害类型：带 bypasses_cooldown/armor/invulnerability 等 tag，
        // 绕原版伤害冷却闸门；找不到时回退 genericKill
        net.minecraft.world.damagesource.DamageSource src = GoetyBridge.truePierceSource(target.level());
        if (src == null) src = target.damageSources().genericKill();
        float comp = GoetyBridge.apostleDamageCompensation(target);
        LOGGER.info("[天逆鉾] 穿透开始 dmg={} obsidianInvul={} 碎柱={} comp={} 血量={}/{}",
                dmg, obsidianInvulBackup, pillarShattered, comp,
                target.getHealth(), target.getMaxHealth());
        float remaining = dmg;
        int guard = 0;
        int landed = 0;
        int blocked = 0;
        while (remaining > 0 && target.isAlive() && !target.isRemoved() && guard++ < 64) {
            GoetyBridge.clearModdedInvul(target);
            GoetyBridge.setObsidianInvul(target, 0); // 天逆鉾无视黑曜石柱保护
            GoetyBridge.clearApollyonHitCooldown(target);
            GoetyBridge.clearApollyonCooldownDirect(target);
            float part = Math.min(remaining, PIERCE_CHUNK);
            target.invulnerableTime = 0;
            boolean ok = target.hurt(src, part * comp);
            if (ok) landed++; else blocked++;
            remaining -= part;
        }
        LOGGER.info("[天逆鉾] 穿透结束 命中段={} 被挡段={} 血量={}/{}", landed, blocked,
                target.getHealth(), target.getMaxHealth());
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
