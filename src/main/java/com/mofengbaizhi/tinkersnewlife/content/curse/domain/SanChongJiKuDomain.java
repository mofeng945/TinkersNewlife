package com.mofengbaizhi.tinkersnewlife.content.curse.domain;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpiritTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.ConstructTechnique;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.UUID;

/**
 * 领域·三重疾苦（必中领域）
 * <ul>
 *   <li>领域开启期间，施术者的攻击<b>必然命中领域内目标</b>：</li>
 *   <li><b>弹射物导引</b>：施术者射出的弹射物（弓/弩/匠魂弓弩/枪械/拟造武器射出物等，
 *       任意 {@link net.minecraft.world.entity.projectile.Projectile} 且 owner 为施术者）
 *       每 tick 被修正朝向领域内最近的敌对目标——即使射偏也会拐弯命中。</li>
 *   <li><b>空挥必中</b>：施术者手持构筑术式拟造物或远程武器左键挥击（即便打在空气上、隔着距离），
 *       也会对领域内最近的敌对目标结算一次必中攻击（按玩家当前攻击力，无视距离与方向）。</li>
 *   <li>目标 = 领域内除施术者阵营（本人/式神/咒灵/守护/驯养宠物）外的所有活物。</li>
 * </ul>
 */
public class SanChongJiKuDomain extends BaseDomain {

    private SanChongJiKuDomain(UUID owner, Vec3 center, int radius) {
        super(owner, center, radius, radius * 45.0);
    }

    /** 工厂：以施术者为中心展开（通用领域展开键） */
    public static SanChongJiKuDomain tryCreate(ServerPlayer player) {
        int radius = CursePowerHelper.getCurseOutputLevel(player) * 5;
        if (radius <= 0) radius = 8;
        return new SanChongJiKuDomain(player.getUUID(), player.position(), radius);
    }

    @Override
    public String getDomainNameKey() {
        return "modifier.tinkersnewlife.san_chong_ji_ku";
    }

    @Override
    public boolean isValid(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(core);
        return tool != null && tool.getModifierLevel(Modifiers.SAN_CHONG_JI_KU.getId()) > 0;
    }

    @Override
    public void onOpen(ServerPlayer player) {
        player.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.san_chong_ji_ku.open", radius), true);
        TinkersNewlife.LOGGER.info("[三重疾苦] {} 展开：半径 {}（攻击必中）",
                player.getName().getString(), radius);
    }

    @Override
    public void onTick(ServerPlayer player, long now) {
        ServerLevel level = player.serverLevel();
        // 弹射物导引：每 tick 修正施术者射出的弹射物朝领域内最近敌对目标
        steerProjectiles(level, player);
        // 必中流光粒子（每 4 tick，owner 弹道尾迹）
        if (now % 4 == 0) {
            double r = radius;
            level.sendParticles(ParticleTypes.END_ROD,
                    center.x + level.random.nextGaussian() * r * 0.6,
                    center.y + level.random.nextGaussian() * r * 0.6,
                    center.z + level.random.nextGaussian() * r * 0.6,
                    2, 0.1, 0.1, 0.1, 0.01);
        }
    }

    // ============================================================
    //  弹射物导引（登记制：高速弹一帧可飞出扫描盒，必须按 id 追踪）
    // ============================================================

    /** 待导引的弹射物实体 id（施术者射出的；含 TACZ 等高速子弹） */
    private final java.util.Set<Integer> trackedProjectiles = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 登记一颗子弹（EntityJoinLevel 事件里调用） */
    private void trackProjectile(int entityId) {
        trackedProjectiles.add(entityId);
    }

    /** 每 tick：按 id 追踪导引施术者射出的弹射物朝领域内最近敌对目标 */
    private void steerProjectiles(ServerLevel level, ServerPlayer owner) {
        java.util.Iterator<Integer> it = trackedProjectiles.iterator();
        while (it.hasNext()) {
            int id = it.next();
            Entity e = level.getEntity(id);
            if (!(e instanceof net.minecraft.world.entity.projectile.Projectile proj) || e.isRemoved()) {
                it.remove();
                continue;
            }
            // owner 失配（异常复用/他人实体）→ 停止追踪
            if (proj.getOwner() != owner) {
                it.remove();
                continue;
            }
            Vec3 vel = proj.getDeltaMovement();
            double speed = vel.length();
            if (speed < 1e-3) {
                it.remove(); // 已停（插地/力竭）
                continue;
            }
            LivingEntity target = nearestEnemy(level, owner, e.position());
            if (target == null) continue; // 暂无目标：保持登记，出现目标即导引
            Vec3 to = target.getEyePosition(1.0F).subtract(e.position());
            if (to.lengthSqr() < 1e-4) continue;
            proj.setDeltaMovement(to.normalize().scale(speed));
            // 轻微尾迹
            if (level.random.nextInt(4) == 0) {
                level.sendParticles(ParticleTypes.CRIT,
                        e.getX(), e.getY() + 0.3, e.getZ(), 1, 0.05, 0.05, 0.05, 0);
            }
        }
    }

    // ============================================================
    //  空挥必中（事件订阅：左键挥击 / 子弹登记）
    // ============================================================

    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class StrikeEvents {

        /** 施术者射出弹射物（含 TACZ 子弹等高速弹）→ 登记进其领域做导引追踪 */
        @SubscribeEvent
        public static void onProjectileJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
            if (event.getLevel().isClientSide) return;
            if (!(event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile proj)) return;
            if (!(proj.getOwner() instanceof ServerPlayer sp)) return;
            if (!sp.isAlive()) return;
            if (!(DomainRegistry.get(sp.getUUID()) instanceof SanChongJiKuDomain domain)) return;
            domain.trackProjectile(event.getEntity().getId());
        }

        /** 玩家左键空挥（打在空气上）：手持拟造物或远程武器 → 必中领域内最近敌对目标 */
        @SubscribeEvent
        public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
            if (event.getEntity().level().isClientSide) return;
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            if (!player.isAlive()) return;
            if (!DomainRegistry.isActive(player.getUUID())) return;
            if (!(DomainRegistry.get(player.getUUID()) instanceof SanChongJiKuDomain domain)) return;

            // 主手（或副手）须为构筑术式拟造物 / 远程武器
            ItemStack main = player.getMainHandItem();
            boolean qual = ConstructTechnique.isConstructTemp(main)
                    || ConstructTechnique.isRangedWeapon(main);
            if (!qual) {
                ItemStack off = player.getOffhandItem();
                qual = ConstructTechnique.isConstructTemp(off)
                        || ConstructTechnique.isRangedWeapon(off);
            }
            if (!qual) return;

            ServerLevel level = player.serverLevel();
            LivingEntity target = domain.nearestEnemy(level, player, player.position());
            if (target == null) return;
            // 必中一击：按玩家当前攻击力（含手持武器），无视距离/方向结算
            double dmg = player.getAttribute(
                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) != null
                    ? player.getAttribute(
                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).getValue()
                    : 1.0;
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), (float) dmg);
            // 命中的横扫粒子
            level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    1, 0.2, 0.3, 0.2, 0);
            level.sendParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    8, 0.3, 0.4, 0.3, 0.1);
            // 挥击动画（服务端触发给自身）
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        }
    }

    // ============================================================
    //  敌对目标判定
    // ============================================================

    /** 领域内距 from 最近的敌对目标（含玩家/生物；不含施术者阵营） */
    private LivingEntity nearestEnemy(ServerLevel level, ServerPlayer owner, Vec3 from) {
        LivingEntity best = null;
        double bestSq = Double.MAX_VALUE;
        double r = radius;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center.x - r, center.y - r, center.z - r,
                        center.x + r, center.y + r, center.z + r))) {
            if (e == owner || !e.isAlive()) continue;
            if (e.position().distanceToSqr(center) > r * r) continue;
            if (isFriendlyTo(owner, e)) continue;
            double d = from.distanceToSqr(e.position());
            if (d < bestSq) {
                bestSq = d;
                best = e;
            }
        }
        return best;
    }

    /** 是否属于施术者阵营（本人/驯养宠物/式神/咒灵/守护） */
    private static boolean isFriendlyTo(ServerPlayer owner, LivingEntity e) {
        if (e == owner) return true;
        if (e instanceof Player) return false;
        // ⭐ getUUID 在 key 缺失时抛 NPE——必须先 contains 再取值
        if (e instanceof TamableAnimal tame && owner.getUUID().equals(tame.getOwnerUUID())) return true;
        if (e instanceof com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiMob sm
                && owner.getUUID().equals(sm.getOwnerId())) return true;
        var tag = e.getPersistentData();
        if (tag.contains(com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler.KEY_GUARD_OWNER)
                && owner.getUUID().equals(tag.getUUID(
                com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler.KEY_GUARD_OWNER))) {
            return true;
        }
        return CursedSpiritTechnique.isSpiritTeam(e, owner);
    }
}
