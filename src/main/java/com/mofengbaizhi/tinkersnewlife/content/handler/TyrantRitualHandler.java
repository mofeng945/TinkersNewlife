package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
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
import net.minecraft.world.item.Items;
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
 * 仪式一「天与咒缚·暴君」（服务端）
 * <p>
 * 发动条件（必须全部满足）：
 * <ul>
 *   <li>时间：新月（月相 4）+ 夜晚（dayTime 13000~23000）；创造模式可跳过时间条件（便于测试）</li>
 *   <li>场所：非露天（水池正上方被遮盖）+ 亮度 &lt; 8</li>
 *   <li>水池：1 格深、≥9 个相连水源；池心正上方为空气</li>
 *   <li>16 格范围内：≥7 下界岩、≥9 黑曜石、≥7 灵魂沙、≥9 下界砖，
 *       且至少 1 个「下界岩上的火焰」与 1 个「灵魂沙上的灵魂火」</li>
 * </ul>
 * 流程：
 * <ol>
 *   <li>向水池投下凋零骷髅头颅 → 头颅自动漂向池心、旋转 3 秒后轰然散开，池水变黑（黑烟/黑色粒子）</li>
 *   <li>发起者跳入黑水：每 20 tick 池水「治疗 −11% 最大生命」（直接扣血，不吃护甲/无敌帧），
 *       伴随大量黑色方块破坏粒子；期间抑制自然回血，直到生命仅剩 1%</li>
 *   <li>黑暗降临，屏幕中央依次出现标题「天与咒缚」「肉体」「你失去了咒力」（各 2 秒），
 *       玻璃碎裂声 + 凋灵诞生音效；随后解除黑暗、抽干池水、熄灭火焰 → 仪式完成</li>
 *   <li>结算：发起者获得天与咒缚（HeavenlyRestrictionHandler）——失去咒力（无法佩戴咒力核心、
 *       黑闪概率锁 0），换来肉体强化（速度 ×5、跳跃 ×3、攻击 ×10）</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TyrantRitualHandler {

    private static final String TAG = "[天与咒缚·暴君] ";

    // ==================== 结构参数 ====================
    /** 16 格扫描半径（结构方块与火焰） */
    private static final int SCAN_RADIUS = 16;
    /** 水池最少水源数 */
    private static final int POOL_MIN_SOURCES = 9;
    private static final int REQ_NETHERRACK = 7;
    private static final int REQ_OBSIDIAN = 9;
    private static final int REQ_SOUL_SAND = 7;
    private static final int REQ_NETHER_BRICKS = 9;

    // ==================== 流程参数 ====================
    /** 头颅漂移+旋转时长：3 秒 */
    private static final int SKULL_TICKS = 60;
    /** 黑水等待超时：3 分钟 */
    private static final int BLACKWATER_TIMEOUT = 3 * 60 * 20;
    /** 抽血间隔：20 tick */
    private static final int DRAIN_INTERVAL = 20;
    /** 每脉冲抽取：11% 最大生命 */
    private static final float DRAIN_FRACTION = 0.11F;
    /** 标题文字（每段 2 秒） */
    private static final String[] FINAL_TITLES = {"天与咒缚", "肉体", "你失去了咒力"};
    private static final int FINAL_CLEANUP_TICK = 120;

    /** 黑色方块破坏粒子用方块 */
    private static final BlockState BLACK_BLOCK_STATE = Blocks.BLACKSTONE.defaultBlockState();

    /** 进行中的仪式：池心坐标 → 数据 */
    private static final Map<BlockPos, RitualData> RITUALS = new ConcurrentHashMap<>();
    /** 失败提示冷却：玩家 UUID → 上次提示的服务器 tick */
    private static final Map<UUID, Long> HINT_COOLDOWN = new ConcurrentHashMap<>();

    private TyrantRitualHandler() {}

    // ============================================================
    //  主循环：每 tick 推进仪式 + 每 10 tick 扫描头颅
    // ============================================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        long tick = server.getTickCount();

        if (tick % 10 == 0) {
            scanForSkulls(server);
        }
        tickRituals(server);
    }

    // ============================================================
    //  扫描：玩家附近水中的凋零骷髅头颅
    // ============================================================

    private static void scanForSkulls(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Level level = player.level();
            if (!level.dimensionType().hasSkyLight()) continue;
            AABB box = new AABB(player.getX() - 24, player.getY() - 16, player.getZ() - 24,
                    player.getX() + 24, player.getY() + 16, player.getZ() + 24);
            List<ItemEntity> skulls = level.getEntitiesOfClass(ItemEntity.class, box,
                    it -> it.isAlive() && it.getItem().is(Items.WITHER_SKELETON_SKULL) && isOverWater(it));
            for (ItemEntity skull : skulls) {
                if (anyRitualNear(skull.blockPosition())) continue;
                ServerLevel serverLevel = (ServerLevel) level;
                PoolInfo pool = findPool(serverLevel, skull);
                if (pool == null) {
                    hint(player, "水池无效：需要 1 格深、至少 " + POOL_MIN_SOURCES + " 个相连水源方块");
                    continue;
                }
                String envReason = environmentOk(player, serverLevel, pool.anchor);
                if (envReason != null) {
                    hint(player, envReason);
                    continue;
                }
                StructureCheck structure = checkStructure(serverLevel, pool.anchor);
                if (!structure.ok) {
                    hint(player, structure.reason);
                    continue;
                }
                ServerPlayer caster = resolveCaster(skull, player);
                if (caster == null) continue;
                if (HeavenlyRestrictionHandler.isRestricted(caster)) continue; // 天与咒缚者无法再次举行
                startRitual(caster, serverLevel, skull, pool);
                break;
            }
        }
    }

    /** 头颅是否悬在（或贴着）水面 */
    private static boolean isOverWater(ItemEntity item) {
        Level level = item.level();
        BlockPos pos = item.blockPosition();
        if (level.getFluidState(pos).is(Fluids.WATER)) return true;
        return level.getFluidState(pos.below()).is(Fluids.WATER);
    }

    /** 头颅所在处是否有进行中的仪式 */
    private static boolean anyRitualNear(BlockPos pos) {
        for (BlockPos anchor : RITUALS.keySet()) {
            if (anchor.distSqr(pos) < 24 * 24) return true;
        }
        return false;
    }

    /** 提示（带冷却，防刷屏） */
    private static void hint(ServerPlayer player, String message) {
        MinecraftServer server = player.server;
        long tick = server.getTickCount();
        Long last = HINT_COOLDOWN.get(player.getUUID());
        if (last != null && tick - last < 100) return;
        HINT_COOLDOWN.put(player.getUUID(), tick);
        player.displayClientMessage(Component.literal(TAG + "发动失败：" + message), true);
    }

    // ============================================================
    //  水池探测：1 格深水源连通簇
    // ============================================================

    private static boolean isWaterSource(Level level, BlockPos pos) {
        return level.getFluidState(pos).is(Fluids.WATER) && level.getFluidState(pos).isSource();
    }

    /** 从头颅所在水源向四周收集同一高度的水源簇；返回池心与所有水源位置 */
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

    /** 环境校验；通过返回 null，否则返回失败原因 */
    private static String environmentOk(ServerPlayer player, ServerLevel level, BlockPos anchor) {
        boolean cheat = player.isCreative();
        long dayTime = level.getDayTime() % 24000;
        boolean night = dayTime >= 13000 && dayTime < 23000;
        boolean newMoon = level.getMoonPhase() == 4;
        if (!cheat) {
            if (!night) return "需要夜晚（新月之夜）举行";
            if (!newMoon) return "需要新月之夜（月相 4）举行";
        }
        // 池心正上方必须为空气（玩家能跳进去）
        if (!level.getBlockState(anchor.above()).isAir()) return "池水正上方需要留出空间";
        // 非露天：水池所在纵列上方必须有遮盖（heightmap 高于水面）
        int topY = level.getHeight(Heightmap.Types.OCEAN_FLOOR, anchor.getX(), anchor.getZ());
        if (topY <= anchor.getY() + 1) return "仪式场所必须是非露天的封闭空间（水池上方需有屋顶）";
        // 亮度 < 8
        int blockLight = level.getBrightness(LightLayer.BLOCK, anchor);
        int skyLight = level.getBrightness(LightLayer.SKY, anchor);
        if (Math.max(blockLight, skyLight) >= 8) return "仪式场所亮度必须低于 8（太亮了）";
        return null;
    }

    /** 结构校验：16 格内材料计数 + 火焰/灵魂火 */
    private static StructureCheck checkStructure(ServerLevel level, BlockPos anchor) {
        int netherrack = 0, obsidian = 0, soulSand = 0, netherBricks = 0;
        boolean fireOnNetherrack = false, soulFireOnSoulSand = false;
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    BlockPos pos = anchor.offset(dx, dy, dz);
                    Block block = level.getBlockState(pos).getBlock();
                    if (block == Blocks.NETHERRACK) {
                        netherrack++;
                        if (!fireOnNetherrack && level.getBlockState(pos.above()).is(Blocks.FIRE)) fireOnNetherrack = true;
                    } else if (block == Blocks.OBSIDIAN) {
                        obsidian++;
                    } else if (block == Blocks.SOUL_SAND) {
                        soulSand++;
                        if (!soulFireOnSoulSand && level.getBlockState(pos.above()).is(Blocks.SOUL_FIRE)) soulFireOnSoulSand = true;
                    } else if (block == Blocks.NETHER_BRICKS) {
                        netherBricks++;
                    }
                }
            }
        }
        StructureCheck check = new StructureCheck();
        if (netherrack < REQ_NETHERRACK) {
            check.reason = "16 格内下界岩不足（需要 " + REQ_NETHERRACK + "，当前 " + netherrack + "）";
        } else if (obsidian < REQ_OBSIDIAN) {
            check.reason = "16 格内黑曜石不足（需要 " + REQ_OBSIDIAN + "，当前 " + obsidian + "）";
        } else if (soulSand < REQ_SOUL_SAND) {
            check.reason = "16 格内灵魂沙不足（需要 " + REQ_SOUL_SAND + "，当前 " + soulSand + "）";
        } else if (netherBricks < REQ_NETHER_BRICKS) {
            check.reason = "16 格内下界砖不足（需要 " + REQ_NETHER_BRICKS + "，当前 " + netherBricks + "）";
        } else if (!fireOnNetherrack) {
            check.reason = "需要在下界岩上点燃火焰（16 格内至少 1 处）";
        } else if (!soulFireOnSoulSand) {
            check.reason = "需要在灵魂沙上点燃灵魂火（16 格内至少 1 处）";
        } else {
            check.ok = true;
        }
        return check;
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
        caster.displayClientMessage(Component.literal(TAG + "条件达成，凋零之颅被吸向池心……"), true);
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
                case BLACKWATER -> tickBlackWater(server, level, data, caster);
                case DRAIN -> tickDrain(server, level, data, caster);
                case FINAL -> tickFinal(server, level, data, caster);
            }
        }
    }

    /** SKULL：头颅漂向池心、旋转 3 秒后炸开 → 池水变黑 */
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
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BLACK_BLOCK_STATE),
                    target.x, target.y, target.z, 1, 0.1, 0.1, 0.1, 0.0);
        }

        if (data.phaseTicks >= SKULL_TICKS) {
            // 头颅炸开，池水变黑
            skull.discard();
            data.phase = Phase.BLACKWATER;
            data.phaseTicks = 0;
            level.sendParticles(ParticleTypes.EXPLOSION, target.x, target.y, target.z, 1, 0, 0, 0, 0);
            blackBreak(level, target, 40, 1.6);
            blackBreak(level, target, 40, 3.2);
            if (caster != null) {
                caster.displayClientMessage(Component.literal(TAG + "头颅轰然散开，池水变得漆黑如墨——跳入黑水中完成仪式。"), false);
            }
        }
    }

    /** BLACKWATER：等待发起者跳入黑水 */
    private static void tickBlackWater(MinecraftServer server, ServerLevel level, RitualData data, ServerPlayer caster) {
        data.phaseTicks++;
        blackWaterFx(level, data, 5);
        if (data.phaseTicks % 100 == 0 && caster != null) {
            caster.displayClientMessage(Component.literal(TAG + "仪式已就绪：跳入漆黑的水池。"), true);
        }
        if (caster == null || !caster.level().dimension().equals(data.dimension)) {
            abort(level, data);
            return;
        }
        if (data.phaseTicks > BLACKWATER_TIMEOUT) {
            if (caster != null) {
                caster.displayClientMessage(Component.literal(TAG + "仪式失败：无人跳入黑水，咒力消散。"), false);
            }
            abort(level, data);
            return;
        }
        if (caster.isAlive() && inPoolWater(caster, data)) {
            data.phase = Phase.DRAIN;
            data.phaseTicks = 0;
            data.pulseTimer = 0;
            data.ceiling = caster.getHealth();
            caster.displayClientMessage(Component.literal(TAG + "你浸入黑水，咒力与生命力开始被池水抽离——坚持到生命仅剩 1%。"), false);
        }
    }

    /** DRAIN：每 20 tick 扣 11% 最大生命，直到 1% */
    private static void tickDrain(MinecraftServer server, ServerLevel level, RitualData data, ServerPlayer caster) {
        if (caster == null || !caster.level().dimension().equals(data.dimension)) {
            abort(level, data);
            return;
        }
        blackWaterFx(level, data, 4);
        if (!caster.isAlive()) {
            abort(level, data);
            return;
        }
        if (!inPoolWater(caster, data)) {
            if (data.phaseTicks++ % 80 == 0) {
                caster.displayClientMessage(Component.literal(TAG + "回到黑水中，仪式才能继续……"), true);
            }
            return;
        }
        data.phaseTicks++;
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
            caster.heal(newHp - caster.getHealth());
            data.ceiling = newHp;
            // 大量黑色方块破坏粒子（玩家周身 + 池心）
            blackBreak(level, caster.position(), 36, 1.8);
            blackBreak(level, centerOf(data.anchor), 18, 2.4);
            if (newHp <= target) {
                enterFinal(caster, level, data);
            }
        } else {
            // 抑制自然回血超过上次脉冲后的水平（保证能看到 20 tick 一跳的阶梯式扣血）
            if (caster.getHealth() > data.ceiling) {
                caster.heal(data.ceiling - caster.getHealth());
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
            blackBreak(level, caster.position(), 50, 2.5);
        } else if (t == 40) {
            sendTitle(caster, FINAL_TITLES[1]);
        } else if (t == 80) {
            sendTitle(caster, FINAL_TITLES[2]);
            // 玻璃碎裂声 + 凋灵诞生音效
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                    SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                    SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, 1.0F);
            blackBreak(level, caster.position(), 60, 3.0);
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

    /** 仪式完成：解除黑暗、抽干池水、熄灭火焰、赋予天与咒缚 */
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
        // 终幕粒子 + 结算
        blackBreak(level, centerOf(data.anchor), 80, 4.0);
        HeavenlyRestrictionHandler.applyRestriction(caster);
        caster.displayClientMessage(Component.literal(TAG + "仪式完成。你失去了咒力：再也无法佩戴咒力核心，黑闪概率锁定为 0。"), false);
        caster.displayClientMessage(Component.literal(TAG + "作为交换，肉体被彻底强化：移动速度 ×5、跳跃高度 ×3、攻击力 ×10。"), false);
        TinkersNewlife.LOGGER.info("{} 仪式完成（玩家 {}）", TAG, caster.getName().getString());
    }

    /** 中止：移除进行中的仪式（不结算、不动方块；SKULL 阶段恢复头颅自由落体，黑水视觉随条目移除而消失） */
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
    //  特效辅助
    // ============================================================

    private static Vec3 centerOf(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    /** 黑色方块破坏粒子（密集） */
    private static void blackBreak(ServerLevel level, Vec3 at, int count, double spread) {
        for (int i = 0; i < count; i++) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BLACK_BLOCK_STATE),
                    at.x + (level.random.nextDouble() - 0.5) * spread,
                    at.y + (level.random.nextDouble() - 0.5) * spread + 0.4,
                    at.z + (level.random.nextDouble() - 0.5) * spread,
                    1, 0.0, 0.08, 0.0, 0.0);
        }
    }

    /** 黑水效果：水面黑烟 + 零星黑色碎粒 */
    private static void blackWaterFx(ServerLevel level, RitualData data, int count) {
        int n = data.sources.size();
        if (n == 0) return;
        for (int i = 0; i < count; i++) {
            BlockPos w = data.sources.get(level.random.nextInt(n));
            double x = w.getX() + 0.2 + level.random.nextDouble() * 0.6;
            double z = w.getZ() + 0.2 + level.random.nextDouble() * 0.6;
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, w.getY() + 0.95, z, 1, 0.0, 0.05, 0.0, 0.0);
            if (level.random.nextInt(4) == 0) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BLACK_BLOCK_STATE),
                        x, w.getY() + 0.9, z, 1, 0.0, 0.12, 0.0, 0.0);
            }
        }
    }

    /** 玩家是否站在本仪式水池的黑水中 */
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

    private enum Phase { SKULL, BLACKWATER, DRAIN, FINAL }

    private static class PoolInfo {
        final BlockPos anchor;
        final List<BlockPos> sources;

        PoolInfo(BlockPos anchor, List<BlockPos> sources) {
            this.anchor = anchor;
            this.sources = sources;
        }
    }

    private static class StructureCheck {
        boolean ok;
        String reason = "16 格内结构材料不足";
    }

    private static class RitualData {
        final UUID casterId;
        final ResourceKey<Level> dimension;
        final BlockPos anchor;
        final List<BlockPos> sources;
        int skullId;
        Phase phase = Phase.SKULL;
        int phaseTicks;
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
