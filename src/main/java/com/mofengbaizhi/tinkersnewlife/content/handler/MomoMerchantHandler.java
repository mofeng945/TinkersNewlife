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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 墨默（武器商人）全局逻辑：
 * <ul>
 *   <li>{@link LivingDamageEvent}：记录墨默每击实际受到的伤害（判定"秒杀"：一击 ≥ 最大生命）</li>
 *   <li>满月午夜刷新：每次满月的午夜，在主世界随机选一名服务器玩家，
 *       刷新在其周围 30 格内可落脚处（优先草方块上方 / 亮度 ≥ 8 的位置）；同夜不重复刷</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MomoMerchantHandler {

    private static final int SPAWN_INTERVAL = 100; // 5s 一次检查
    private static final int MIDNIGHT_HALF_WINDOW = 200; // 午夜窗口（18000±200 tick）
    private static int tickCounter = 0;
    /** 本满月午夜已刷新过的那一天（防同夜重复刷） */
    private static long lastFullMoonSpawnDay = -1;

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof MomoMerchant momo) {
            momo.recordDamageTaken(event.getAmount());
            String type = event.getSource().getMsgId();
            // 吟唱抗性：对应类型 60s 内伤害降为 40%
            if (momo.isResistantTo(type)) {
                event.setAmount(event.getAmount() * 0.4f);
            }
            momo.markDamaged();
            momo.recordHit(type, event.getAmount());
        }
    }

    /** 雇主攻击命中目标 → 通知其雇佣的墨默协助集火（近战/弓箭等带攻击者的伤害） */
    @SubscribeEvent
    public static void onEmployerAttack(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getEntity().isRemoved()) return;
        var src = event.getSource();
        if (src == null) return;
        net.minecraft.world.entity.Entity attacker = src.getEntity(); // 弓箭=射击者；近战=攻击者
        if (attacker instanceof ServerPlayer p) {
            notifyHiredMomo(p, event.getEntity());
        }
    }

    /** 通知玩家雇佣的墨默协助攻击该目标（供本类事件与天逆鉾穿透等无攻击者的伤害主动调用） */
    public static void notifyHiredMomo(ServerPlayer p, LivingEntity victim) {
        if (victim == null || victim.isRemoved() || victim.level().isClientSide) return;
        if (victim instanceof MomoMerchant || victim instanceof ServerPlayer) return;
        if (!(p.level() instanceof ServerLevel sl)) return;
        AABB box = new AABB(p.getX() - 128, p.getY() - 128, p.getZ() - 128,
                p.getX() + 128, p.getY() + 128, p.getZ() + 128);
        for (MomoMerchant momo : sl.getEntitiesOfClass(MomoMerchant.class, box,
                m -> m.isHired() && m.getEmployer() == p)) {
            momo.notifyEmployerAttack(victim);
        }
    }

    /** 墨默不可被命名（命名牌右键无效） */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide) return;
        if (event.getTarget() instanceof MomoMerchant
                && event.getItemStack().is(Items.NAME_TAG)) {
            event.setCanceled(true);
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
        // 满月午夜：月相 0 且当日时间在午夜附近
        long dayTime = level.getDayTime() % 24000;
        if (Math.abs(dayTime - 18000) > MIDNIGHT_HALF_WINDOW) return;
        if (level.getMoonPhase() != 0) return;
        long worldDay = level.getDayTime() / 24000;
        if (worldDay == lastFullMoonSpawnDay) return;   // 本满月午夜已刷，不重复
        // 随机选一名服务器上的玩家
        ServerPlayer player = players.get(level.random.nextInt(players.size()));
        if (spawnNearPlayer(level, player)) {
            lastFullMoonSpawnDay = worldDay;
        }
    }

    /**
     * 在玩家周围 30 格内找落脚点刷墨默。
     * 优先选择：站在草方块上方，或落脚点亮度 ≥ 8；两者都无则回退任意可落脚点；全无则不刷。
     */
    private static boolean spawnNearPlayer(ServerLevel level, ServerPlayer player) {
        BlockPos center = player.blockPosition();
        int radius = 30;
        List<BlockPos> good = new ArrayList<>();
        List<BlockPos> any = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;   // 圆形 30 格
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                for (int y = center.getY() + radius; y >= center.getY() - 20; y--) {
                    BlockPos ground = new BlockPos(x, y, z);
                    if (!isStandable(level, ground)) {
                        if (level.getBlockState(ground).isSolid()
                                && !level.getBlockState(ground.above()).isSolid()) {
                            // 该列有地面但下方不满足 → 不再下探
                        }
                        continue;
                    }
                    any.add(ground);
                    BlockPos stand = ground.above();
                    if (isGrassAt(level, ground) || level.getRawBrightness(stand, 0) >= 8) {
                        good.add(ground);
                    }
                    break;   // 该列只取最高可行地面
                }
            }
        }
        List<BlockPos> candidates = good.isEmpty() ? any : good;
        if (candidates.isEmpty()) return false;
        BlockPos spot = candidates.get(level.random.nextInt(candidates.size()));
        return placeMomo(level, spot);
    }

    /** 地面判定：脚下是实心、站位格与头部格均空 */
    private static boolean isStandable(ServerLevel level, BlockPos ground) {
        if (!level.getBlockState(ground).isSolid()) return false;
        if (level.getBlockState(ground.above()).isSolid()) return false;
        return level.isEmptyBlock(ground.above()) && level.isEmptyBlock(ground.above(2));
    }

    /** 脚下是否为草方块 */
    private static boolean isGrassAt(ServerLevel level, BlockPos ground) {
        return level.getBlockState(ground).is(Blocks.GRASS_BLOCK);
    }

    /** 落点生成墨默；成功返回 true */
    private static boolean placeMomo(ServerLevel level, BlockPos ground) {
        MomoMerchant momo = ModEntities.MOMO_MERCHANT.get().create(level);
        if (momo == null) return false;
        momo.moveTo(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5,
                level.random.nextFloat() * 360.0F, 0.0F);
        if (!level.noCollision(momo)) {
            momo.discard();
            return false;
        }
        momo.finalizeSpawn(level, level.getCurrentDifficultyAt(ground),
                MobSpawnType.EVENT, null, null);
        // 自然（满月）刷新：白天到来时消失；刷怪蛋召唤的不受影响
        momo.setNaturalSpawn(true);
        level.addFreshEntity(momo);
        level.sendParticles(ParticleTypes.SNEEZE, ground.getX() + 0.5, ground.getY() + 1.5,
                ground.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, ground, SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.HOSTILE, 1.0F, 1.0F);
        return true;
    }
}
