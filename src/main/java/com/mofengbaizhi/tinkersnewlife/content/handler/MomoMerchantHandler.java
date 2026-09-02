package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 墨默（武器商人）全局逻辑：
 * <ul>
 *   <li>{@link LivingDamageEvent}：记录墨默每击实际受到的伤害（判定"秒杀"：一击 ≥ 最大生命）</li>
 *   <li>满月刷新：满月夜晚，在主世界村庄集会点（钟 / 教堂占位）附近刷新墨默；
 *       同一钟 48 格内已存在墨默则不重复刷</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MomoMerchantHandler {

    private static final int SPAWN_INTERVAL = 100; // 5s 一次检查
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof MomoMerchant momo) {
            momo.recordDamageTaken(event.getAmount());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++tickCounter % SPAWN_INTERVAL != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension() != Level.OVERWORLD) continue;
            trySpawnAtFullMoon(level);
        }
    }

    private static void trySpawnAtFullMoon(ServerLevel level) {
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        // 满月夜晚（占位：教堂结构未建，用村钟=集会点 POI 定位）
        long dayTime = level.getDayTime() % 24000;
        if (dayTime < 13000 || dayTime > 23000) return;
        if (level.getMoonPhase() != 0) return;

        for (ServerPlayer player : players) {
            BlockPos center = player.blockPosition();
            // 收集玩家附近 128 格内所有钟（集会点），按距离近到远尝试
            List<BlockPos> bells = level.getPoiManager()
                    .getInRange(t -> t == PoiTypes.MEETING, center, 128,
                            net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
                    .map(rec -> rec.getPos())
                    .filter(pos -> level.isLoaded(pos))
                    .sorted(java.util.Comparator.comparingDouble(pos -> pos.distSqr(center)))
                    .toList();
            for (BlockPos poiPos : bells) {
                // 该钟 48 格内已有墨默 → 不重复刷
                AABB near = new AABB(poiPos).inflate(48.0);
                if (!level.getEntitiesOfClass(MomoMerchant.class, near).isEmpty()) continue;
                if (spawnAtBell(level, poiPos)) {
                    return; // 一次检查最多刷一只
                }
            }
        }
    }

    /** 在钟周围找空地刷墨默；成功返回 true */
    private static boolean spawnAtBell(ServerLevel level, BlockPos bellPos) {
        // 在钟周围 2~5 格找可站立的地面点（教堂前空地）
        for (int radius = 2; radius <= 5; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue; // 只扫圆环
                    int x = bellPos.getX() + dx;
                    int z = bellPos.getZ() + dz;
                    // 从钟高度往下找地面
                    for (int y = bellPos.getY() + 1; y >= bellPos.getY() - 8; y--) {
                        BlockPos ground = new BlockPos(x, y, z);
                        if (level.getBlockState(ground).isSolid() && !level.getBlockState(ground.above()).isSolid()
                                && level.isEmptyBlock(ground.above(2))) {
                            MomoMerchant momo = ModEntities.MOMO_MERCHANT.get().create(level);
                            if (momo == null) return false;
                            momo.moveTo(x + 0.5, y + 1.0, z + 0.5,
                                    level.random.nextFloat() * 360.0F, 0.0F);
                            if (!level.noCollision(momo)) {
                                momo.discard();
                                continue;
                            }
                            momo.finalizeSpawn(level, level.getCurrentDifficultyAt(ground),
                                    MobSpawnType.EVENT, null, null);
                            level.addFreshEntity(momo);
                            level.sendParticles(ParticleTypes.SNEEZE, x + 0.5, y + 1.5, z + 0.5,
                                    12, 0.3, 0.3, 0.3, 0.02);
                            level.playSound(null, ground, SoundEvents.ILLUSIONER_MIRROR_MOVE,
                                    SoundSource.HOSTILE, 1.0F, 1.0F);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
