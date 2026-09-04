package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.curse.TechniqueHandler;
import com.mofengbaizhi.tinkersnewlife.content.curse.domain.DomainRegistry;
import com.mofengbaizhi.tinkersnewlife.content.curse.shikigami.ShikigamiHandler;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BlackBirdTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.PlantManipulationTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.ProjectionTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.PuppetTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangCangTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangWuxianTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.ZaoKaiTechnique;
import com.mofengbaizhi.tinkersnewlife.content.entity.BlackBirdEntity;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetGolemMob;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetIronGolem;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetSnowGolem;
import com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiMob;
import com.mofengbaizhi.tinkersnewlife.content.item.TianNiHuoItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 天逆鉾 · 术式中断：
 * <ul>
 *   <li>右键式神 / 傀儡 / 黑鸟 → 直接击杀该召唤物（视角/式神逻辑正常收尾）；</li>
 *   <li>右键草木操术长出的甜浆果丛 → 立即消除该树根场（还原方块、停止后续减速/伤害）；</li>
 *   <li>攻击 / 右键正在发动术式的玩家 → 中断其全部进行中的术式：
 *       黑鸟、傀儡、草木（蓄力+树根场）、灶·开/无量·苍蓄力、无下限·无限、领域、投射罚站、场上式神全部击杀；</li>
 *   <li>每次中断冒黑红色粒子（烟尘 + 绯红孢子）。</li>
 * </ul>
 * 只对服务器侧生效；未命中任何进行中状态时正常攻击照旧。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TianNiHuoInterruptHandler {

    private TianNiHuoInterruptHandler() {}

    private static boolean holding(Player player) {
        ItemStack held = player.getMainHandItem();
        return held.getItem() instanceof TianNiHuoItem;
    }

    /** 攻击玩家：攻击结算照常，同时中断其进行中的术式 */
    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer attacker)) return;
        if (!holding(attacker)) return;
        if (!(event.getTarget() instanceof ServerPlayer victim)) return;
        if (interruptPlayer(victim)) {
            attacker.displayClientMessage(
                    Component.translatable("message.tinkersnewlife.tian_ni_huo.interrupt"), true);
        }
    }

    /** 右键式神/傀儡/黑鸟 → 直接击杀；右键玩家 → 中断其术式 */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!holding(player)) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;

        if (target instanceof ShikigamiMob
                || target instanceof PuppetIronGolem
                || target instanceof PuppetSnowGolem
                || target instanceof BlackBirdEntity) {
            event.setCanceled(true);
            player.swing(InteractionHand.MAIN_HAND);
            killSummon(player, target);
            return;
        }
        if (target instanceof ServerPlayer victim) {
            event.setCanceled(true);
            player.swing(InteractionHand.MAIN_HAND);
            if (interruptPlayer(victim)) {
                player.displayClientMessage(
                        Component.translatable("message.tinkersnewlife.tian_ni_huo.interrupt"), true);
            }
        }
    }

    /** 右键草木操术的甜浆果丛：消除该树根场（后续伤害/减速逻辑停止、方块还原） */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!holding(player)) return;
        if (event.getLevel() instanceof ServerLevel sl
                && PlantManipulationTechnique.removeFieldAt(sl, event.getPos())) {
            event.setCanceled(true);
            player.swing(InteractionHand.MAIN_HAND);
            spawnInterruptParticles(sl,
                    event.getPos().getX() + 0.5, event.getPos().getY() + 0.5, event.getPos().getZ() + 0.5);
            player.displayClientMessage(
                    Component.translatable("message.tinkersnewlife.tian_ni_huo.interrupt_bush"), true);
        }
    }

    /** 击杀式神/傀儡/黑鸟（巨大伤害走正常死亡结算，召唤主视角/回收逻辑正常收尾） */
    private static void killSummon(ServerPlayer player, LivingEntity target) {
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().playerAttack(player), 1.0E9F);
        if (target.isAlive()) {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().magic(), 1.0E9F);
        }
        ServerLevel sl = (ServerLevel) target.level();
        spawnInterruptParticles(sl, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
    }

    /** 中断一名玩家的全部进行中术式；返回是否有中断到任何东西 */
    private static boolean interruptPlayer(ServerPlayer victim) {
        ServerLevel level = victim.serverLevel();
        boolean any = false;

        // 黑鸟操术：黑鸟消散（视角回归）
        BlackBirdEntity bird = BlackBirdTechnique.findActiveBird(victim);
        if (bird != null) {
            any = true;
            bird.finish(false);
        }
        // 傀儡操术：傀儡消散
        PuppetGolemMob puppet = PuppetTechnique.findActivePuppet(victim);
        if (puppet != null) {
            any = true;
            puppet.puppetFinish(false);
        }
        // 草木操术：蓄力取消 + 树根场全部还原
        if (PlantManipulationTechnique.interruptAll(victim)) {
            any = true;
        }
        // 灶·开 / 无量·苍 蓄力（无条件取消，恢复原主手）
        ZaoKaiTechnique.cancelCharge(victim);
        WuliangCangTechnique.cancelCharge(victim);
        // 无下限·无限（切换型持续术式）关闭
        if (WuliangWuxianTechnique.isActive(victim)) {
            any = true;
            WuliangWuxianTechnique.deactivate(victim);
        }
        // 领域强制关闭
        if (DomainRegistry.isActive(victim.getUUID())) {
            any = true;
            DomainRegistry.close(victim, "message.tinkersnewlife.domain.interrupted");
        }
        // 投射咒法罚站解除
        if (ProjectionTechnique.isStunned(victim)) {
            any = true;
            ProjectionTechnique.endStun(victim);
        }
        // 十影术式：场上式神全部击杀
        List<Entity> shikigami = ShikigamiHandler.findActiveFor(victim);
        for (Entity e : shikigami) {
            if (e instanceof LivingEntity le && le.isAlive()) {
                any = true;
                le.invulnerableTime = 0;
                le.hurt(level.damageSources().magic(), 1.0E9F);
            }
        }
        if (any) {
            spawnInterruptParticles(level, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ());
        }
        return any;
    }

    /** 黑红色粒子（烟尘 + 绯红孢子） + 低沉音效 */
    private static void spawnInterruptParticles(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.SMOKE, x, y, z, 18, 0.6, 0.6, 0.6, 0.02);
        level.sendParticles(ParticleTypes.CRIMSON_SPORE, x, y, z, 14, 0.6, 0.6, 0.6, 0.02);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 1.6F);
    }
}
