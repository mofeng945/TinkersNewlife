package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.curse.KnockbackHelper;
import com.mofengbaizhi.tinkersnewlife.content.item.SpearItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>长矛的冲锋与"近身打不到"</b>（§835）—— 逻辑全在这里，物品类只管接线。
 *
 * <h2>冲锋（右键长按）怎么判</h2>
 * <ol>
 *   <li>按住期间每 tick 由 {@code SpearItem#onUseTick} 调进来 ✓（服务端 ✓）；</li>
 *   <li>按按住时长分三阶段 ✓（阈值在 {@link SpearItem} 里 ✓ <b>自定</b> ✗ 见 §835 说明）；</li>
 *   <li>阶段内每 tick 找"正前方锥角内、最小～最大距离之间、而且<b>双方正在快速接近</b>"的生物 ✓
 *       （接近速度 = 玩家与目标速度差在视线方向上的投影 ✓ 照 wiki："damage depends on … the velocity
 *        of both the player and the target" ✓）；</li>
 *   <li>每个目标**一次冲锋只吃一次** ✓（{@code hit} 集合 ✓ 正好对应原版成就"一次冲锋内命中五只生物" ✓）；</li>
 *   <li>结算：{@code playerAttack} 伤害源（⇒ 匠魂命中类特性照常触发 ✓）＋ 强击退 ✓
 *       ＋ Engaged 阶段够快时<b>把骑乘者打下马</b> ✓ ＋ 暴击粒子与三叉戟音效 ✓。</li>
 * </ol>
 *
 * <h2>"近身打不到"</h2>
 * 普通攻击走 {@link AttackEntityEvent}：手持长矛且目标离得太近 ⇒ <b>直接取消这一下</b> ✓
 * （照原版长矛的"最小距离"特征 ✓；阈值 {@link SpearItem#MIN_RANGE} 取 1.5 格，原版写 2 格 ⇒ 自定 ✓）。
 *
 * <p>⚠ 音效**引用的是原版已有的 {@code minecraft:item.trident.*}** ✓ —— 原版长矛自己的音效
 * （{@code item.spear.*}）**不能**打包进我们的模组（版权 ✗，见 §835）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpearChargeHandler {

    /** 每位玩家当前的冲锋状态 */
    private static final Map<UUID, ChargeState> ACTIVE = new ConcurrentHashMap<>();

    private SpearChargeHandler() {}

    private static final class ChargeState {
        int lastStage = -1;
        final Set<UUID> hit = new HashSet<>();
    }

    /** 开始一次冲锋（每次重新按住右键都会清空"已命中"名单 ✓） */
    public static void begin(ServerPlayer player) {
        ACTIVE.put(player.getUUID(), new ChargeState());
    }

    /** 结束冲锋（松手 / 切物品 / 死亡 / 下线 ✓） */
    public static void end(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
    }

    /**
     * 冲锋期间每 tick 的推进：分阶段 ＋ 命中判定。
     *
     * @param chargeTicks 已经按住了多少 tick
     */
    public static void tick(ServerPlayer player, ItemStack stack, int chargeTicks) {
        int stage = stageFor(chargeTicks);
        if (stage < 0) {                       // 超过 Disengaged ⇒ 回到 idle，必须松手重来 ✓
            ACTIVE.remove(player.getUUID());
            return;
        }
        ChargeState state = ACTIVE.computeIfAbsent(player.getUUID(), k -> new ChargeState());
        if (state.lastStage != stage) {
            state.lastStage = stage;
            if (stage > 0) {                   // 进入疲劳/脱力阶段：给一声"力气不济"的提示音 ✓
                player.level().playSound(null, player.blockPosition(),
                        SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.6F, 0.7F + stage * 0.2F);
            }
        }

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        double maxRange = player.getAttributeValue(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());
        if (maxRange < SpearItem.MIN_RANGE + 0.5D) maxRange = SpearItem.MIN_RANGE + 0.5D;

        AABB box = player.getBoundingBox().inflate(maxRange + 1.0D);
        List<LivingEntity> candidates = player.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && !state.hit.contains(e.getUUID()));

        for (LivingEntity target : candidates) {
            if (!player.canAttack(target)) continue;                 // 同队/友军不误伤 ✓
            Vec3 toTarget = target.getBoundingBox().getCenter().subtract(eye);
            double distance = toTarget.length();
            if (distance < SpearItem.MIN_RANGE || distance > maxRange) continue;
            Vec3 dir = toTarget.scale(1.0D / distance);
            if (dir.dot(look) < SpearItem.AIM_CONE_COS) continue;     // 不在矛尖指向的锥角里 ⇒ 不命中 ✓

            double closing = player.getDeltaMovement().subtract(target.getDeltaMovement()).dot(dir);
            if (closing < SpearItem.STAGE_MIN_CLOSING[stage]) continue;

            state.hit.add(target.getUUID());
            applyChargeHit(player, target, stage, closing, dir);
        }
    }

    /** 阶段：0=Engaged 1=Tired 2=Disengaged；-1=已回到 idle */
    private static int stageFor(int chargeTicks) {
        if (chargeTicks < SpearItem.ENGAGED_END) return 0;
        if (chargeTicks < SpearItem.TIRED_END) return 1;
        if (chargeTicks < SpearItem.DISENGAGED_END) return 2;
        return -1;
    }

    private static void applyChargeHit(ServerPlayer player, LivingEntity target, int stage,
                                      double closing, Vec3 dir) {
        float base = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float damage = base * SpearItem.STAGE_DAMAGE_MULT[stage] * (float) (1.0D + closing * 1.5D);

        target.invulnerableTime = 0;                                  // 冲锋命中不受无敌帧吞掉 ✓
        boolean hurt = target.hurt(player.damageSources().playerAttack(player), damage);
        if (!hurt) return;

        // 强击退：Engaged 最猛，之后递减 ✓（用全模组统一的 KnockbackHelper ✓）
        double strength = (1.2D + closing * 1.5D) * (1.0D - stage * 0.25D);
        KnockbackHelper.applyStrongKnockback(target, player, strength, 0.35D);

        // 打下马：只有 Engaged 阶段且接近速度够快 ✓（照 wiki ✓）
        if (stage == 0 && closing >= SpearItem.DISMOUNT_MIN_CLOSING) {
            target.stopRiding();
        }

        if (player.level() instanceof ServerLevel server) {
            Vec3 mid = target.getBoundingBox().getCenter();
            server.sendParticles(ParticleTypes.CRIT, mid.x, mid.y, mid.z, 12, 0.25D, 0.25D, 0.25D, 0.15D);
            server.sendParticles(ParticleTypes.ENCHANTED_HIT, mid.x, mid.y, mid.z, 8, 0.2D, 0.2D, 0.2D, 0.1D);
            server.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_HIT,
                    SoundSource.PLAYERS, 1.0F, 0.9F + stage * 0.1F);
        }
    }

    // ============================================================
    //  普通攻击：近身打不到（原版长矛的最小距离 ✓）
    // ============================================================

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (!(player.getMainHandItem().getItem() instanceof SpearItem)) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        double distance = player.getEyePosition().distanceTo(target.getBoundingBox().getCenter());
        if (distance < SpearItem.MIN_RANGE) {
            event.setCanceled(true);                                  // 贴太近 ⇒ 这一下不生效 ✓
        }
    }

    // ============================================================
    //  状态清理：玩家不按了/挂了/下线了 ⇒ 别把状态留在表里
    // ============================================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (ACTIVE.isEmpty()) return;
        var server = event.getServer();
        ACTIVE.keySet().removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            return player == null || !player.isUsingItem()
                    || !(player.getUseItem().getItem() instanceof SpearItem);
        });
    }
}
