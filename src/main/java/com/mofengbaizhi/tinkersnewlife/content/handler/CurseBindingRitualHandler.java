package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 仪式二「天与咒缚·咒力」（服务端）——与「天与咒缚·暴君」相反方向的束缚
 * <p>
 * 发动条件（必须全部满足；与暴君相反）：
 * <ul>
 *   <li>时间：满月（月相 0）+ 夜晚（dayTime 13000~23000）；创造模式可跳过时间条件（便于测试）</li>
 *   <li>场所：露天（水池正上方无遮盖）+ 亮度 &gt; 8</li>
 *   <li>水池：1 格深、≥9 个相连水源；池心正上方为空气</li>
 *   <li>16 格范围内：≥7 下界岩、≥9 哭泣黑曜石、≥7 灵魂沙、≥9 石英块，
 *       且至少 1 个「下界岩上的火焰」与 1 个「灵魂沙上的灵魂火」</li>
 * </ul>
 * 流程（与暴君相同）：
 * <ol>
 *   <li>向水池投入一枚任意咒力核心 → 核心自动漂向池心、旋转 3 秒后散开，水池泛起白光（白光粒子）</li>
 *   <li>发起者跳入池水：每 20 tick 池水「治疗 −11% 最大生命」（直接扣血，不吃护甲/无敌帧），
 *       伴随大量白色方块破坏粒子；期间抑制自然回血，直到生命仅剩 1%</li>
 *   <li>黑暗降临，屏幕中央依次出现标题（各 2 秒），玻璃碎裂声 + 凋零死亡音效；
 *       随后解除黑暗、抽干池水、熄灭火焰 → 仪式完成</li>
 *   <li>结算：发起者获得天与咒缚·咒力（CurseBindingHandler）——基础生命上限/速度/伤害减半，
 *       但自带咒力亲和 200，且佩戴咒力核心时咒力总量与咒力输出各自动 +1 级；
 *       状态固化（死亡不解除）；仪式结束时生命恢复至新上限的 40%。
 *       与「暴君」束缚互相排斥（举行本仪式会把暴君束缚转换掉）</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CurseBindingRitualHandler {

    private static final String TAG = "[天与咒缚·咒力] ";

    // ==================== 结构参数 ====================
    /** 16 格扫描半径（结构方块与火焰） */
    private static final int SCAN_RADIUS = 16;
    /** 水池最少水源数 */
    private static final int POOL_MIN_SOURCES = 9;
    private static final int REQ_NETHERRACK = 7;
    private static final int REQ_CRYING_OBSIDIAN = 9;
    private static final int REQ_SOUL_SAND = 7;
    private static final int REQ_QUARTZ = 9;

    // ==================== 流程参数 ====================
    /** 核心漂移+旋转时长：3 秒 */
    private static final int SKULL_TICKS = 60;
    /** 白光等待超时：3 分钟 */
    private static final int WAIT_TIMEOUT = 3 * 60 * 20;
    /** 抽血间隔：20 tick */
    private static final int DRAIN_INTERVAL = 20;
    /** 每脉冲抽取：11% 最大生命 */
    private static final float DRAIN_FRACTION = 0.11F;
    /** 标题文字（每段 2 秒） */
    private static final String[] FINAL_TITLES = {"天与咒缚", "肉体", "你失去了咒力"};
    private static final int FINAL_CLEANUP_TICK = 120;

    /** 白色方块破坏粒子用方块（白光主题） */
    private static final BlockState WHITE_BLOCK_STATE = Blocks.WHITE_CONCRETE.defaultBlockState();

    /** 进行中的仪式：池心坐标 → 数据 */
    private static final Map<BlockPos, RitualData> RITUALS = new ConcurrentHashMap<>();

    private CurseBindingRitualHandler() {}

    // ============================================================
    //  主循环：每 tick 推进仪式 + 每 10 tick 扫描投下的咒力核心
    // ============================================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        long tick = server.getTickCount();

        if (tick % 10 == 0) {
            scanForOfferings(server);
        }
        tickRituals(server);
    }

    // ============================================================
    //  扫描：玩家附近水中投下的咒力核心
    // ============================================================

    private static void scanForOfferings(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Level level = player.level();
            if (!level.dimensionType().hasSkyLight()) continue;
            AABB box = new AABB(player.getX() - 24, player.getY() - 16, player.getZ() - 24,
                    player.getX() + 24, player.getY() + 16, player.getZ() + 24);
            List<ItemEntity> cores = level.getEntitiesOfClass(ItemEntity.class, box,
                    it -> it.isAlive() && isCurseCore(it) && isOverWater(it));
            for (ItemEntity core : cores) {
                if (anyRitualNear(core.blockPosition())) continue;
                ServerLevel serverLevel = (ServerLevel) level;
                PoolInfo pool = findPool(serverLevel, core);
                if (pool == null) continue;
                if (!environmentOk(player, serverLevel, pool.anchor)) continue;
                if (!checkStructure(serverLevel, pool.anchor)) continue;
                ServerPlayer caster = resolveCaster(core, player);
                if (caster == null) continue;
                if (CurseBindingHandler.isBound(caster)) continue; // 已是咒力束缚者，无法再次举行
                startRitual(caster, serverLevel, core, pool);
                break;
            }
        }
    }

    /** 是否为咒力核心（任意材质） */
    private static boolean isCurseCore(ItemEntity item) {
        return !item.getItem().isEmpty() && item.getItem().is(ModItems.CURSE_CORE.get());
    }

    /** 投物是否悬在（或贴着）水面 */
    private static boolean isOverWater(ItemEntity item) {
        Level level = item.level();
        BlockPos pos = item.blockPosition();
        if (level.getFluidState(pos).is(Fluids.WATER)) return true;
        return level.getFluidState(pos.below()).is(Fluids.WATER);
    }

    /** 投物所在处是否有进行中的仪式 */
    private static boolean anyRitualNear(BlockPos pos) {
        for (BlockPos anchor : RITUALS.keySet()) {
            if (anchor.distSqr(pos) < 24 * 24) return true;
        }
        return false;
    }

    // ============================================================
    //  水池探测：1 格深水源连通簇
    // ============================================================

    private static boolean isWaterSource(Level level, BlockPos pos) {
        return level.getFluidState(pos).is(Fluids.WATER) && level.getFluidState(pos).isSource();
    }

    /** 从投物所在水源向四周收集同一高度的水源簇；返回池心与所有水源位置 */
    private static PoolInfo findPool(ServerLevel level, ItemEntity skull) {
        BlockPos bp = skull.blockPosition();
        BlockPos seed = isWaterSource(level, bp) ? bp
                : (isWaterSource(level, bp.below()) ? bp.below() : null);
        if (seed == null) return null;

        int y = seed.getY();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> sources = new ArrayList<>();
        queue.add(seed);
        visited.add(seed);
        int minX = seed.getX(), maxX = seed.getX(), minZ = seed.getZ(), maxZ = seed.getZ();

        while (!queue.isEmpty() && visited.size() < 800) {
            BlockPos cur = queue.poll();
            if (!isWaterSource(level, cur)) continue;
            sources.add(cur);
            minX = Math.min(minX, cur.getX());
            maxX = Math.max(maxX, cur.getX());
            minZ = Math.min(minZ, cur.getZ());
            maxZ = Math.max(maxZ, cur.getZ());
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos next = cur.relative(dir);
                if (Math.abs(next.getX() - seed.getX()) > 24 || Math.abs(next.getZ() - seed.getZ()) > 24) continue;
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        if (sources.size() < POOL_MIN_SOURCES) return null;

        // 1 格深：每个水源下方不得是水（水池底部为实体/非水方块）
        int solidUnder = 0;
        for (BlockPos pos : sources) {
            if (!level.getFluidState(pos.below()).is(Fluids.WATER)) solidUnder++;
        }
        if (solidUnder < sources.size()) return null;

        BlockPos anchor = new BlockPos((minX + maxX) / 2, y, (minZ + maxZ) / 2);
        return new PoolInfo(anchor, sources);
    }

    // ============================================================
    //  环境与结构校验
    // ============================================================

    /** 环境校验（时间 / 露天 / 亮度 / 池心空间）；满足返回 true */
    private static boolean environmentOk(ServerPlayer player, ServerLevel level, BlockPos anchor) {
        boolean cheat = player.isCreative();
        long dayTime = level.getDayTime() % 24000;
        boolean night = dayTime >= 13000 && dayTime < 23000;
        boolean fullMoon = level.getMoonPhase() == 0;
        if (!cheat && (!night || !fullMoon)) return false;
        // 池心正上方必须为空气（玩家能跳进去）
        if (!level.getBlockState(anchor.above()).isAir()) return false;
        // 露天：水池所在纵列上方不得被遮盖（heightmap 不高于水面）
        int topY = level.getHeight(Heightmap.Types.OCEAN_FLOOR, anchor.getX(), anchor.getZ());
        if (topY > anchor.getY() + 1) return false;
        // 亮度 > 8（明亮场所：满月之夜 + 火焰/灯光照明）
        int blockLight = level.getBrightness(LightLayer.BLOCK, anchor);
        int skyLight = level.getBrightness(LightLayer.SKY, anchor);
        return Math.max(blockLight, skyLight) > 8;
    }

    /** 结构校验：16 格内材料计数 + 火焰/灵魂火；满足返回 true */
    private static boolean checkStructure(ServerLevel level, BlockPos anchor) {
        int netherrack = 0, cryingObsidian = 0, soulSand = 0, quartz = 0;
        boolean fireOnNetherrack = false, soulFireOnSoulSand = false;
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    BlockPos pos = anchor.offset(dx, dy, dz);
                    Block block = level.getBlockState(pos).getBlock();
                    if (block == Blocks.NETHERRACK) {
                        netherrack++;
                        if (!fireOnNetherrack && level.getBlockState(pos.above()).is(Blocks.FIRE)) fireOnNetherrack = true;
                    } else if (block == Blocks.CRYING_OBSIDIAN) {
                        cryingObsidian++;
                    } else if (block == Blocks.SOUL_SAND) {
                        soulSand++;
                        if (!soulFireOnSoulSand && level.getBlockState(pos.above()).is(Blocks.SOUL_FIRE)) soulFireOnSoulSand = true;
                    } else if (block == Blocks.QUARTZ_BLOCK) {
                        quartz++;
                    }
                }
            }
        }
        return netherrack >= REQ_NETHERRACK && cryingObsidian >= REQ_CRYING_OBSIDIAN
                && soulSand >= REQ_SOUL_SAND && quartz >= REQ_QUARTZ
                && fireOnNetherrack && soulFireOnSoulSand;
    }

    private static ServerPlayer resolveCaster(ItemEntity skull, ServerPlayer nearby) {
        net.minecraft.world.entity.Entity owner = skull.getOwner();
        if (owner instanceof ServerPlayer player) return player;
        return nearby;
    }

    // ============================================================
    //  仪式启动（SKULL 阶段）
    // ============================================================

    private static void startRitual(ServerPlayer caster, ServerLevel level, ItemEntity skull, PoolInfo pool) {
        BlockPos anchor = pool.anchor;
        if (RITUALS.containsKey(anchor)) return;
        RitualData data = new RitualData(caster.getUUID(), level.dimension(), anchor, pool.sources);
        data.skullId = skull.getId();
        RITUALS.put(anchor, data);
        TinkersNewlife.LOGGER.info("{} 仪式发动（玩家 {} @ {}）", TAG, caster.getName().getString(), anchor);
    }

    // ============================================================
    //  仪式状态机
    // ============================================================

    private static void tickRituals(MinecraftServer server) {
        if (RITUALS.isEmpty()) return;
        for (RitualData data : new ArrayList<>(RITUALS.values())) {
            ServerLevel level = server.getLevel(data.dimension);
            if (level == null) {
                RITUALS.remove(data.anchor);
                continue;
            }
            ServerPlayer caster = server.getPlayerList().getPlayer(data.casterId);
            switch (data.phase) {
                case SKULL -> tickSkull(server, level, data, caster);
                case WAITWATER -> tickWaitWater(server, level, data, caster);
                case DRAIN -> tickDrain(server, level, data, caster);
                case FINAL -> tickFinal(server, level, data, caster);
            }
        }
    }

    /** SKULL：咒力核心漂向池心、旋转 3 秒后散开 → 水池泛起白光 */
    private static void tickSkull(MinecraftServer server, ServerLevel level, RitualData data, ServerPlayer caster) {
        if (caster == null) {
            abort(level, data);
            return;
        }
        if (!(level.getEntity(data.skullId) instanceof ItemEntity skull) || !skull.isAlive()) {
            abort(level, data);
            return;
        }
        data.phaseTicks++;
        // 漂向池心（池心水面下一点）
        Vec3 target = new Vec3(data.anchor.getX() + 0.5, data.anchor.getY() + 0.55, data.anchor.getZ() + 0.5);
        Vec3 cur = skull.position();
        Vec3 delta = target.subtract(cur);
        if (delta.length() > 0.25) {
            Vec3 move = delta.normalize().scale(0.25);
            skull.setPos(cur.x + move.x, cur.y + move.y, cur.z + move.z);
        } else {
            skull.setPos(target.x, target.y, target.z);
        }
        skull.setNoGravity(true);
        skull.setDeltaMovement(Vec3.ZERO);
        skull.setYRot(skull.getYRot() + 30.0F);
        skull.setPickUpDelay(32767);

        // 旋转黑烟粒子
        level.sendParticles(ParticleTypes.SMOKE, target.x, target.y, target.z, 2,
                level.random.nextDouble() - 0.5, level.random.nextDouble() - 0.3, level.random.nextDouble() - 0.5, 0.0);
        if (level.random.nextInt(6) == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, WHITE_BLOCK_STATE),
                    target.x, target.y, target.z, 1, 0.1, 0.1, 0.1, 0.0);
        }

        if (data.phaseTicks >= SKULL_TICKS) {
            // 核心散开，水池泛起白光
            skull.discard();
            data.phase = Phase.WAITWATER;
            data.phaseTicks = 0;
            level.sendParticles(ParticleTypes.EXPLOSION, target.x, target.y, target.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, target.x, target.y, target.z, 40, 0.6, 0.6, 0.6, 0.08);
            whiteBreak(level, target, 40, 1.6);
            whiteBreak(level, target, 40, 3.2);
        }
    }

    /** WAITWATER：等待发起者跳入泛白光的池水 */
    private static void tickWaitWater(MinecraftServer server, ServerLevel level, RitualData data, ServerPlayer caster) {
        data.phaseTicks++;
        whiteWaterFx(level, data, 5);
        if (caster == null || !caster.level().dimension().equals(data.dimension)) {
            abort(level, data);
            return;
        }
        if (data.phaseTicks > WAIT_TIMEOUT) {
            abort(level, data);
            return;
        }
        if (caster.isAlive() && inPoolWater(caster, data)) {
            data.phase = Phase.DRAIN;
            data.phaseTicks = 0;
            data.pulseTimer = 0;
            data.ceiling = caster.getHealth();
        }
    }

    /** DRAIN：每 20 tick 扣 11% 最大生命，直到 1% */
    private static void tickDrain(MinecraftServer server, ServerLevel level, RitualData data, ServerPlayer caster) {
        if (caster == null || !caster.level().dimension().equals(data.dimension)) {
            abort(level, data);
            return;
        }
        whiteWaterFx(level, data, 4);
        if (!caster.isAlive()) {
            abort(level, data);
            return;
        }
        if (!inPoolWater(caster, data)) {
            return;
        }
        float maxHp = caster.getMaxHealth();
        float target = maxHp * 0.01F;
        if (caster.getHealth() <= target) {
            enterFinal(caster, level, data);
            return;
        }
        data.pulseTimer++;
        if (data.pulseTimer >= DRAIN_INTERVAL) {
            data.pulseTimer = 0;
            float drain = maxHp * DRAIN_FRACTION;
            float newHp = Math.max(target, caster.getHealth() - drain);
            // ⭐ 直接 setHealth 扣血：heal(负数) 会被 Forge 回血钩子（LivingHealEvent）拦截导致不掉血
            caster.setHealth(newHp);
            data.ceiling = newHp;
            // 大量黑色方块破坏粒子（玩家周身 + 池心）
            whiteBreak(level, caster.position(), 36, 1.8);
            whiteBreak(level, centerOf(data.anchor), 18, 2.4);
            if (newHp <= target) {
                enterFinal(caster, level, data);
            }
        } else {
            // 抑制自然回血超过上次脉冲后的水平（保证能看到 20 tick 一跳的阶梯式扣血）
            if (caster.getHealth() > data.ceiling) {
                caster.setHealth(data.ceiling);
            }
        }
    }

    /** FINAL：黑暗 + 三段标题 + 音效 → 清理 → 结算 */
    private static void tickFinal(MinecraftServer server, ServerLevel level, RitualData data, ServerPlayer caster) {
        if (caster == null || !caster.level().dimension().equals(data.dimension)) {
            abort(level, data);
            return;
        }
        if (!caster.isAlive()) {
            abort(level, data);
            return;
        }
        // 生命锁定在 1%
        caster.setHealth(0.01F * caster.getMaxHealth());
        int t = data.phaseTicks++;
        if (t == 0) {
            caster.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 10 * 20, 0, false, false, false));
            sendTitle(caster, FINAL_TITLES[0]);
            whiteBreak(level, caster.position(), 50, 2.5);
        } else if (t == 40) {
            sendTitle(caster, FINAL_TITLES[1]);
        } else if (t == 80) {
            sendTitle(caster, FINAL_TITLES[2]);
            // 玻璃碎裂声 + 凋零死亡音效
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                    SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1.0F, 1.0F);
            whiteBreak(level, caster.position(), 60, 3.0);
        } else if (t >= FINAL_CLEANUP_TICK) {
            completeRitual(server, level, data, caster);
        }
    }

    /** 进入最终阶段（生命 ≤ 1%） */
    private static void enterFinal(ServerPlayer caster, ServerLevel level, RitualData data) {
        data.phase = Phase.FINAL;
        data.phaseTicks = 0;
        caster.setHealth(0.01F * caster.getMaxHealth());
    }

    // ============================================================
    //  结算与清理
    // ============================================================

    /** 仪式完成：解除黑暗、抽干池水、熄灭火焰、赋予天与咒缚·咒力 */
    private static void completeRitual(MinecraftServer server, ServerLevel level, RitualData data, ServerPlayer caster) {
        RITUALS.remove(data.anchor);
        // 解除黑暗
        caster.removeEffect(MobEffects.DARKNESS);
        // 抽干池水（所有登记的水源）
        for (BlockPos pos : data.sources) {
            if (level.getFluidState(pos).is(Fluids.WATER)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // 熄灭 16 格内所有火焰/灵魂火
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    BlockPos pos = data.anchor.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        // 终幕粒子 + 结算：赋予天与咒缚·咒力（生命上限/速度/伤害减半 + 咒力亲和 200 + 核心等级 +1），
        // 并把生命恢复到（减半后）上限的 40%
        whiteBreak(level, centerOf(data.anchor), 80, 4.0);
        CurseBindingHandler.applyBinding(caster);
        caster.setHealth(caster.getMaxHealth() * 0.4F);
        TinkersNewlife.LOGGER.info("{} 仪式完成（玩家 {}，生命恢复至 40%）", TAG, caster.getName().getString());
    }

    /** 中止：移除进行中的仪式（不结算、不动方块；SKULL 阶段恢复投物自由落体，白光视觉随条目移除而消失） */
    private static void abort(ServerLevel level, RitualData data) {
        RitualData removed = RITUALS.remove(data.anchor);
        if (removed != null) {
            if (removed.phase == Phase.SKULL
                    && level.getEntity(removed.skullId) instanceof ItemEntity item && item.isAlive()) {
                item.setNoGravity(false);
                item.setPickUpDelay(0);
            }
            TinkersNewlife.LOGGER.info("{} 仪式中止 @ {}", TAG, data.anchor);
        }
    }

    // ============================================================
    //  玩家死亡 / 登出 → 中止其仪式
    // ============================================================

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            abortFor(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            abortFor(player);
        }
    }

    private static void abortFor(ServerPlayer player) {
        UUID id = player.getUUID();
        RITUALS.entrySet().removeIf(entry -> entry.getValue().casterId.equals(id));
    }

    // ============================================================
    //  抽血 / 收尾期间免疫外来伤害
    //  （仪式房间亮度 <8 会刷怪；1% 生命下任何一击都会致死并打断仪式。
    //   抽血用 setHealth 直扣，不走伤害事件，不受此免疫影响）
    // ============================================================

    @SubscribeEvent
    public static void onLivingHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        for (RitualData data : RITUALS.values()) {
            if (data.casterId.equals(victim.getUUID())
                    && (data.phase == Phase.DRAIN || data.phase == Phase.FINAL)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    // ============================================================
    //  特效辅助
    // ============================================================

    private static Vec3 centerOf(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /** 白色方块破坏粒子（密集，白光主题） */
    private static void whiteBreak(ServerLevel level, Vec3 at, int count, double spread) {
        for (int i = 0; i < count; i++) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, WHITE_BLOCK_STATE),
                    at.x + (level.random.nextDouble() - 0.5) * spread,
                    at.y + (level.random.nextDouble() - 0.5) * spread + 0.4,
                    at.z + (level.random.nextDouble() - 0.5) * spread,
                    1, 0.0, 0.08, 0.0, 0.0);
        }
    }

    /** 白光效果：水面白色光点上浮 + 零星白色碎粒（水池冒白光） */
    private static void whiteWaterFx(ServerLevel level, RitualData data, int count) {
        int n = data.sources.size();
        if (n == 0) return;
        for (int i = 0; i < count; i++) {
            BlockPos w = data.sources.get(level.random.nextInt(n));
            double x = w.getX() + 0.2 + level.random.nextDouble() * 0.6;
            double z = w.getZ() + 0.2 + level.random.nextDouble() * 0.6;
            level.sendParticles(ParticleTypes.END_ROD, x, w.getY() + 0.9, z, 1, 0.0, 0.12, 0.0, 0.0);
            if (level.random.nextInt(3) == 0) {
                level.sendParticles(ParticleTypes.WHITE_ASH, x, w.getY() + 1.0, z, 1, 0.0, 0.06, 0.0, 0.0);
            }
        }
    }

    /** 玩家是否站在本仪式水池中 */
    private static boolean inPoolWater(ServerPlayer player, RitualData data) {
        if (!player.isInWater()) return false;
        Vec3 pos = player.position();
        for (BlockPos w : data.sources) {
            if (Math.abs(w.getX() + 0.5 - pos.x) < 1.2
                    && Math.abs(w.getZ() + 0.5 - pos.z) < 1.2
                    && Math.abs(w.getY() + 0.6 - pos.y) < 1.6) {
                return true;
            }
        }
        return false;
    }

    /** 屏幕中央标题（含 2 秒显示时长动画） */
    private static void sendTitle(ServerPlayer player, String text) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(2, 36, 2));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(text)));
    }

    // ============================================================
    //  数据
    // ============================================================

    private enum Phase { SKULL, WAITWATER, DRAIN, FINAL }

    private static class PoolInfo {
        final BlockPos anchor;
        final List<BlockPos> sources;

        PoolInfo(BlockPos anchor, List<BlockPos> sources) {
            this.anchor = anchor;
            this.sources = sources;
        }
    }

    private static class RitualData {
        final UUID casterId;
        final ResourceKey<Level> dimension;
        final BlockPos anchor;
        final List<BlockPos> sources;
        int skullId;
        Phase phase = Phase.SKULL;        int phaseTicks;
        int pulseTimer;
        float ceiling;

        RitualData(UUID casterId, ResourceKey<Level> dimension, BlockPos anchor, List<BlockPos> sources) {
            this.casterId = casterId;
            this.dimension = dimension;
            this.anchor = anchor;
            this.sources = sources;
        }
    }
}
