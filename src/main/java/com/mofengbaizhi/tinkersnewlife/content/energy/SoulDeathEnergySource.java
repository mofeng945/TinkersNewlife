package com.mofengbaizhi.tinkersnewlife.content.energy;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import com.mofengbaizhi.tinkersnewlife.content.block.ElderManaPedestalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>来源 ④「灵魂死亡」：身边有东西死掉就攒一笔 EE</b>（用户口径 §545 ✓）。
 *
 * <h2>规则</h2>
 * <ul>
 *   <li><b>任何死亡都算</b> ✓（{@link LivingDeathEvent} ⇒ 敌对/被动/首领/<b>玩家</b>全都包含 ✓）；</li>
 *   <li><b>范围 5 格</b>（球半径 ✓ 与其它条一致 —— 用死亡位置到台座的<b>方块中心</b>距离判定 ✓）；</li>
 *   <li>换算：<b>20 血 = 0.5 EE</b> ⇒ <b>{@code EE = maxHealth / 40}</b> ✓
 *       （僵尸 10 血 = 0.25、凋灵 300 血 = 7.5、玩家 20 血 = 0.5 ✓）；</li>
 *   <li>攒成"待结算 EE"缓冲，<b>由台座每秒发放</b> ✓（死亡是离散事件、不能直接把 EE 灌进水晶 ✗）。</li>
 * </ul>
 *
 * <h2>为什么"攒"在台座自己身上（而不是一张全局表）</h2>
 * <ol>
 *   <li>位置天然正确：死亡那一刻就按<b>距离</b>找台座（{@link #awardNearby}）⇒ 之后台座挪没挪、
 *       水晶满没满都不会算错 ✓；</li>
 *   <li>生命周期天然正确：缓冲跟台座方块实体在一起 ⇒ 台座被拆/区块卸载 <b>不会</b>留下孤儿条目
 *       （全局 {位置→EE} 表就必须额外做清理 ✗ 容易漏 ✗ 也容易内存泄漏 ✗）；</li>
 *   <li>不会被"每秒一次的总量查询"重复扣 ⇒ 只有 {@code simulate == false} 那次才清池 ✓。</li>
 * </ol>
 * <p>⚠ 诚实记一笔：缓冲<b>不持久化</b>（重启丢 ≤ 1 秒的量 ✓ 与"凋灵度内存态"同一口径 ✓）。
 *
 * <h2>多个台座同时在范围内会怎样</h2>
 * <b>各拿一份全额</b> ✓ —— 用户口径是"范围 5 格内的台座充能"，
 * 没有"只能有一个台座吃到"的意思 ✓（想只给一个台座就拉开 6 格以上 ✓）。这是<b>我做的取舍</b>，
 * 若要改成"按距离只算最近的台座"，改点只在 {@link #awardNearby} 一处 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SoulDeathEnergySource implements AmbientEnergySource {

    /** 配置允许清单里写的 id */
    public static final String ID = "soul_death";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String shortName() {
        return "灵魂死亡";
    }

    @Override
    public String summary() {
        return "sphere r=5 around the pedestal; ANY death (players included) banks maxHealth/40 EE"
                + " (20 HP = 0.5 EE); the pedestal pays the bank out on its next one-second settle";
    }

    /**
     * 本来源的"每秒产出" = <b>把台座上攒着的待结算灵魂一次性领走</b> ✓。
     * <p>⚠ 只有 {@code simulate == false} 会真的清空缓冲 ✓ —— 查速率（tooltip/调试）绝不吞掉这笔账 ✗。
     * <p>⚠ 注意：台座 {@code settle()} 里<b>另有一处</b>直接读这个缓冲（{@link ElderManaPedestalBlockEntity#drainPendingEnergy()}）——
     * 那是因为"来源清单里没勾选 soul_death"时，已经攒下的账也该照发 ✓（详见台座类注释 ✓）。
     */
    @Override
    public double eePerSecond(Level level, BlockPos pos, boolean simulate) {
        if (!(level instanceof ServerLevel) || pos == null) return 0.0D;
        if (!(level.getBlockEntity(pos) instanceof ElderManaPedestalBlockEntity pedestal)) return 0.0D;
        return simulate ? pedestal.peekPendingEnergy() : pedestal.drainPendingEnergy();
    }

    // ============================================================
    //  死亡事件 → 找范围内的台座 → 记一笔
    // ============================================================

    /** 死亡事件（任何 {@link LivingEntity} 死亡都会走到这里 ✓ 包括玩家 ✓） */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead == null || dead.level().isClientSide) return;
        if (!ModConfig.soulEnabled()) return;

        double perHp = ModConfig.soulEePerHp();
        if (perHp <= 0.0D) return;

        float maxHealth = dead.getMaxHealth();
        if (!(maxHealth > 0.0F)) return;

        awardNearby(dead.level(), dead.blockPosition(), (double) maxHealth * perHp);
    }

    /**
     * 把 {@code ee} 记给"距离 {@code where} 不超过 {@code soul_radius} 格"的所有台座 ✓。
     *
     * @param level 死亡发生的维度 ✓
     * @param where 死亡位置（实体所在方块 ✓）
     * @param ee    这一笔的 EE（= 最大生命 / 40 ✓）
     */
    public static void awardNearby(Level level, BlockPos where, double ee) {
        if (!(level instanceof ServerLevel server) || where == null) return;
        if (!(ee > 0.0D)) return;

        int radius = ModConfig.soulRadius();
        if (radius < 0) return;

        double maxDistanceSqr = (double) radius * (double) radius;
        int r = radius;
        for (BlockPos at : BlockPos.betweenClosed(
                where.getX() - r, where.getY() - r, where.getZ() - r,
                where.getX() + r, where.getY() + r, where.getZ() + r)) {
            BlockEntity be = server.getBlockEntity(at);
            if (!(be instanceof ElderManaPedestalBlockEntity pedestal)) continue;
            // 用"方块中心到方块中心"的距离 ⇒ 与玩家/生物站位的直觉一致 ✓（相邻格 = 1 ✓）
            if (pedestal.getBlockPos().distSqr(where) > maxDistanceSqr) continue;
            pedestal.addPendingEnergy(ee);
        }
    }
}
