package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.entity.FlamePhantom;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 术式「炎熔操术」。
 * <p>
 * 顺转（释放键）：对视线目标，将其脚下 3×3 内方块（基岩除外）融化为炽热岩浆（临时岩浆池，
 * 3 秒后还原原方块）；目标身处岩浆池内持续受到咒术伤害（每 0.75s 一跳，伤害基底 ×25%，套用
 * 魔杖增幅与核心材料特性），并被岩浆点燃。
 * <p>
 * 反转（反转键 F）：消耗 = ceil(M×100)（M = 1+(输出+亲和/10)/10）召唤 5 只 1/8 体型、1 点生命的
 * 自爆幻翼（焰羽）：1 秒前摇后错峰依次飞速撞向敌人，每次撞击引发等同黑鸟自爆的爆炸
 * （中心 = (1+亲和/100)×(输出×3+1)×10，半径 3 线性衰减），并在敌人脚下 3×3 燃起火焰、点燃敌人。
 */
public final class FlameManipulationTechnique extends BaseTechnique {

    public static final FlameManipulationTechnique INSTANCE = new FlameManipulationTechnique();

    /** 岩浆池 / 火焰场时长 3 秒 */
    private static final int FIELD_DURATION = 60;
    /** 岩浆池伤害间隔 15 tick（3 秒内最多 4 跳） */
    private static final int HIT_INTERVAL = 15;
    /** 岩浆单跳伤害系数（相对共享伤害基底） */
    private static final double MELT_HIT_FACTOR = 0.25;
    /** 反转召唤幻翼数量与前摇错峰 */
    private static final int PHANTOM_COUNT = 5;
    private static final int PHANTOM_WINDUP = 20;

    private static final List<MeltField> MELT_FIELDS = new CopyOnWriteArrayList<>();
    private static final List<FireField> FIRE_FIELDS = new CopyOnWriteArrayList<>();

    private FlameManipulationTechnique() {
        super(Modifiers.FLAME_MANIPULATION.getId());
    }

    // ================= 顺转：脚下 3×3 熔为岩浆 =================

    @Override
    public void onKeyPress(ServerPlayer player) {
        LivingEntity target = findTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_target"), true);
            return;
        }
        if (!payCost(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        MeltField field = new MeltField(level, player.getUUID(), target.blockPosition());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                field.tryMelt(level,
                        new BlockPos(target.blockPosition().getX() + dx, target.blockPosition().getY(), target.blockPosition().getZ() + dz));
            }
        }
        MELT_FIELDS.add(field);
        level.sendParticles(ParticleTypes.LAVA,
                target.getX(), target.getY() + 0.3, target.getZ(), 24, 1.5, 0.3, 1.5, 0);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.8F, 0.7F);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.flame.melt"), true);
    }

    // ================= 反转：召唤 5 只自爆幻翼 =================

    @Override
    public void onReverseKeyPress(ServerPlayer player) {
        LivingEntity target = findTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_target"), true);
            return;
        }
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        int cost = (int) Math.ceil((1.0 + (output + affinity / 10.0) / 10.0) * 100.0);
        if (!CursePowerHelper.isCurseInfinite(player)
                && CursePowerHelper.payCurseWithSoulFallback(player, cost) < 0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 pos = player.position();
        for (int i = 0; i < PHANTOM_COUNT; i++) {
            double angle = i * (Math.PI * 2.0 / PHANTOM_COUNT) + 0.3;
            double px = pos.x + Math.cos(angle) * 1.6;
            double pz = pos.z + Math.sin(angle) * 1.6;
            FlamePhantom phantom = new FlamePhantom(ModEntities.FLAME_PHANTOM.get(), level);
            phantom.moveTo(px, pos.y + 1.6 + (i % 2) * 0.5, pz, player.getYRot(), 0);
            phantom.setMission(player, target, PHANTOM_WINDUP + i * 6);
            level.addFreshEntity(phantom);
        }
        level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y + 1.5, pos.z, 30, 1.6, 1.0, 1.6, 0.02);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.flame.phantoms"), true);
    }

    /** 幻翼爆炸后：敌人脚下 3×3 燃起火焰（3 秒后熄灭还原） */
    public static void startFireField(ServerLevel level, BlockPos feet) {
        FireField field = new FireField(level);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                field.tryIgnite(level,
                        new BlockPos(feet.getX() + dx, feet.getY(), feet.getZ() + dz));
            }
        }
        if (!field.cells.isEmpty()) {
            FIRE_FIELDS.add(field);
        }
    }

    /**
     * 单跳咒术伤害（顺转岩浆池每跳 / 幻翼撞击保底共用）：
     * 共享伤害基底 × 25% + 魔杖增幅 + 核心材料特性。
     * 咒术伤害与火焰无关——防火/抗火免疫目标同样全额承受。
     */
    public static double computeHitDamage(ServerPlayer owner, LivingEntity victim) {
        double raw = INSTANCE.amplifyTechniqueDamage(owner,
                INSTANCE.computeBaseDamage(owner) * MELT_HIT_FACTOR);
        return CurseCoreTraitHelper.applyCurseCoreTraits(owner, victim, raw);
    }

    /** 登出 / 死亡：还原该玩家岩浆池（火焰场短暂自动熄灭，无需清理） */
    public static void cleanup(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Iterator<MeltField> it = MELT_FIELDS.iterator();
        while (it.hasNext()) {
            MeltField field = it.next();
            if (uuid.equals(field.ownerId)) {
                field.restore();
                it.remove();
            }
        }
    }

    // ================= 服务端 tick =================

    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class FlameEvents {

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Iterator<MeltField> meltIt = MELT_FIELDS.iterator();
            while (meltIt.hasNext()) {
                if (meltIt.next().tick()) {
                    meltIt.remove();
                }
            }
            Iterator<FireField> fireIt = FIRE_FIELDS.iterator();
            while (fireIt.hasNext()) {
                if (fireIt.next().tick()) {
                    fireIt.remove();
                }
            }
        }
    }

    /** 岩浆池：3×3 地表方块（基岩除外）临时融为岩浆，身处其中持续咒术伤害，到期还原 */
    private static final class MeltField {
        private final ServerLevel level;
        private final UUID ownerId;
        private final BlockPos center;
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private int ticksLeft = FIELD_DURATION;
        private int hitTimer = 10;

        MeltField(ServerLevel level, UUID ownerId, BlockPos center) {
            this.level = level;
            this.ownerId = ownerId;
            this.center = center;
        }

        void tryMelt(ServerLevel world, BlockPos pos) {
            int cy = pos.getY();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int yy = cy; yy >= cy - 3; yy--) {
                m.set(pos.getX(), yy, pos.getZ());
                BlockState s = world.getBlockState(m);
                if (s.isAir() || s.canBeReplaced()) continue;   // 空气/植物：继续向下找实心
                if (!s.getFluidState().isEmpty()) continue;      // 水/岩浆等液体：跳过
                if (s.getBlock() == Blocks.BEDROCK) return;      // 基岩不可融化
                blocks.put(m.immutable(), s);
                world.setBlock(m, Blocks.LAVA.defaultBlockState(), 2);
                return;
            }
        }

        boolean tick() {
            ticksLeft--;
            if (--hitTimer <= 0) {
                hitTimer = HIT_INTERVAL;
                hit();
            }
            if (ticksLeft <= 0) {
                restore();
                return true;
            }
            return false;
        }

        private void hit() {
            ServerPlayer owner = level.getServer() != null
                    ? level.getServer().getPlayerList().getPlayer(ownerId) : null;
            AABB box = new AABB(center.getX() - 1.8, center.getY() - 1.0, center.getZ() - 1.8,
                    center.getX() + 1.8, center.getY() + 2.5, center.getZ() + 1.8);
            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e.isAlive() && !PuppetUtil.isAllyOf(e, owner));
            for (LivingEntity victim : victims) {
                float dmg = 1.0F;
                if (owner != null) {
                    dmg = (float) computeHitDamage(owner, victim);
                }
                victim.invulnerableTime = 0;
                if (owner != null) {
                    victim.hurt(owner.damageSources().mobAttack(owner), dmg);
                    CurseCoreTraitHelper.afterCurseCoreHit(owner, victim, dmg);
                }
                victim.setSecondsOnFire(2);
                level.sendParticles(ParticleTypes.LAVA,
                        victim.getX(), victim.getY() + 0.5, victim.getZ(), 5, 0.4, 0.4, 0.4, 0);
            }
        }

        void restore() {
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                BlockPos pos = entry.getKey();
                if (level.getBlockState(pos).is(Blocks.LAVA)) { // 含源/流动岩浆
                    level.setBlock(pos, entry.getValue(), 2);
                }
            }
            blocks.clear();
        }
    }

    /** 火焰场：3×3 燃火（每 10 tick 补一次火苗防熄灭），到期还原 */
    private static final class FireField {
        private final ServerLevel level;
        private final List<Map.Entry<BlockPos, BlockState>> cells = new ArrayList<>();
        private int ticksLeft = FIELD_DURATION;
        private int relight = 0;

        FireField(ServerLevel level) {
            this.level = level;
        }

        void tryIgnite(ServerLevel world, BlockPos pos) {
            int cy = pos.getY();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            // 自目标脚面向下找实心支撑，火焰放在其上方一格
            for (int yy = cy; yy >= cy - 3; yy--) {
                m.set(pos.getX(), yy, pos.getZ());
                BlockState s = world.getBlockState(m);
                if (s.isAir() || !s.getFluidState().isEmpty() || s.canBeReplaced()) continue;
                BlockPos firePos = m.above();
                BlockState cur = world.getBlockState(firePos);
                if (!cur.getFluidState().isEmpty()) return;
                if (!cur.isAir() && !cur.canBeReplaced()) return;
                cells.add(new java.util.AbstractMap.SimpleEntry<>(firePos.immutable(), cur));
                world.setBlock(firePos, Blocks.FIRE.defaultBlockState(), 2);
                return;
            }
        }

        boolean tick() {
            ticksLeft--;
            if (--relight <= 0) {
                relight = 10;
                for (Map.Entry<BlockPos, BlockState> entry : cells) {
                    BlockPos pos = entry.getKey();
                    BlockState cur = level.getBlockState(pos);
                    BlockState below = level.getBlockState(pos.below());
                    if (below.isAir() || !below.getFluidState().isEmpty()) continue;
                    if (cur.isAir() || cur.is(Blocks.FIRE) || cur.canBeReplaced()) {
                        level.setBlock(pos, Blocks.FIRE.defaultBlockState(), 2);
                    }
                }
            }
            if (ticksLeft <= 0) {
                for (Map.Entry<BlockPos, BlockState> entry : cells) {
                    BlockPos pos = entry.getKey();
                    if (level.getBlockState(pos).is(Blocks.FIRE)) {
                        level.setBlock(pos, entry.getValue(), 2);
                    }
                }
                return true;
            }
            return false;
        }
    }
}
