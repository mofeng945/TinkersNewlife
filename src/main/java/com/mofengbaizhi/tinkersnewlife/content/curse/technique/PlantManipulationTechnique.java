package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.TechniqueHandler;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetUtil;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenPlantScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 术式「草木操术」。
 * <p>
 * 顺转（释放键）：按下打开选择界面（树根 / 咒种）→ 选定后进入蓄力；
 * 瞄准敌人再次按下释放：咒力消耗沿用公式 (1-亲和/100)×(10+输出×5)。
 * <li>树根：目标脚下及周围 2 格放置甜浆果丛，持续 3 秒；期间每 0.75s 对场内敌人造成
 * 咒术伤害（伤害基底 ×20%/跳，套用魔杖增幅与核心材料特性）并施加减速；
 * 浆果丛打碎不掉落、破坏被拦截（防逃跑），到期自动还原原方块。
 * <li>咒种：目标获得「咒种寄生」buff（攻击 -40%，咒力总量/输出各 -1 级、亲和 -60）。
 * <p>
 * 反转（反转键 F）：立即吸收自身 5×5×5 范围内植物的生命能量转化为咒力
 * （草方块→砂土 +1；草 / 花破坏 +3；树叶破坏 +8；不掉落任何物品，无消耗）。
 */
public final class PlantManipulationTechnique extends BaseTechnique {

    public static final PlantManipulationTechnique INSTANCE = new PlantManipulationTechnique();

    /** 选择模式 */
    public enum Mode { ROOTS, SEED }

    /** 蓄力上限（tick），超时自动消散 */
    private static final int CHARGE_TIMEOUT = 300;
    /** 树根场持续 3 秒 */
    private static final int FIELD_DURATION = 60;
    /** 树根伤害间隔 15 tick（3 秒内最多 4 跳） */
    private static final int HIT_INTERVAL = 15;
    /** 咒种寄生时长：160 + 输出×16 tick */
    private static final int SEED_BASE_TICKS = 160;
    /** 树根单跳伤害系数（相对共享伤害基底） */
    private static final double ROOT_HIT_FACTOR = 0.2;

    /** 蓄力中：玩家 UUID → 模式；及蓄力起始时刻 */
    private static final Map<UUID, Mode> CHARGING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> CHARGE_START = new ConcurrentHashMap<>();
    /** 生效中的树根场（可同时存在多个，各自到期独立消散/还原） */
    private static final java.util.List<RootField> FIELDS = new java.util.concurrent.CopyOnWriteArrayList<>();

    private PlantManipulationTechnique() {
        super(Modifiers.PLANT_MANIPULATION.getId());
    }

    // ================= 顺转 =================

    /** 按下释放键：蓄力中 → 释放；否则打开选择界面 */
    @Override
    public void onKeyPress(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Mode mode = CHARGING.get(uuid);
        if (mode == null) {
            TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new PacketOpenPlantScreen(getCost(player)));
            return;
        }
        // 蓄力超时自动消散
        long start = CHARGE_START.getOrDefault(uuid, 0L);
        if (player.serverLevel().getGameTime() - start > CHARGE_TIMEOUT) {
            CHARGING.remove(uuid);
            CHARGE_START.remove(uuid);
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.plant.charge_expired"), true);
            return;
        }
        // 锁定敌人
        LivingEntity target = findTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_target"), true);
            return;
        }
        // 释放时扣除咒力（套用通用公式），不足则取消本次（蓄力保留可再试）
        if (!payCost(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        CHARGING.remove(uuid);
        CHARGE_START.remove(uuid);
        if (mode == Mode.ROOTS) {
            castRoots(player, target);
        } else {
            castSeed(player, target);
        }
    }

    /** 树根：目标脚下及周围 2 格放置甜浆果丛（3 秒后还原） */
    private void castRoots(ServerPlayer player, LivingEntity target) {
        ServerLevel level = player.serverLevel();
        BlockPos center = target.blockPosition();
        RootField field = new RootField(level, player.getUUID(), center);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos pos = new BlockPos(center.getX() + dx, center.getY(), center.getZ() + dz);
                field.tryPlace(level, pos);
            }
        }
        FIELDS.add(field);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 24, 2.2, 1.0, 2.2, 0);
    }

    /** 咒种：目标获得咒种寄生 */
    private void castSeed(ServerPlayer player, LivingEntity target) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int duration = SEED_BASE_TICKS + output * 16;
        target.addEffect(new MobEffectInstance(ModEffects.SEED_PARASITE.get(), duration, 0, false, true));
        player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,
                target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 16, 0.4, 0.5, 0.4, 0);
    }

    /** 选择界面选定：进入蓄力 */
    public static void selectMode(ServerPlayer player, int modeId) {
        if (modeId < 0 || modeId >= Mode.values().length) return;
        if (!Modifiers.PLANT_MANIPULATION.getId().equals(TechniqueHandler.getSelectedTechniqueId(player))) return;
        UUID uuid = player.getUUID();
        CHARGING.put(uuid, Mode.values()[modeId]);
        CHARGE_START.put(uuid, player.serverLevel().getGameTime());
        player.displayClientMessage(Component.translatable(
                modeId == 0 ? "message.tinkersnewlife.plant.charge_roots" : "message.tinkersnewlife.plant.charge_seed"), true);
    }

    // ================= 反转：吸收植物生命能量 =================

    @Override
    public void onReverseKeyPress(ServerPlayer player) {
        // 取消顺转蓄力（若在蓄力），再执行吸收
        cancelCharge(player);
        absorbPlants(player);
    }

    private void absorbPlants(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos base = player.blockPosition();
        int gained = 0;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;
                    int g = classifyPlant(state);
                    if (g < 0) continue; // 非植物
                    if (state.getBlock() == Blocks.GRASS_BLOCK) {
                        level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
                    } else {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                    gained += g;
                }
            }
        }
        if (gained > 0) {
            CursePowerHelper.addCurse(player, gained);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    player.getX(), player.getY() + 1.0, player.getZ(), 20, 2.4, 1.6, 2.4, 0);
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.plant.absorb", gained), true);
        } else {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.plant.absorb_none"), true);
        }
    }

    /** 分类：非植物 -1；草方块 1；草 / 花 / 树苗 3；树叶 8（返回可吸收的咒力数） */
    private static int classifyPlant(BlockState state) {
        if (state.getBlock() == Blocks.GRASS_BLOCK) return 1;
        if (state.is(BlockTags.LEAVES)) return 8;
        if (state.is(BlockTags.SAPLINGS)) return 3;
        if (state.is(BlockTags.SMALL_FLOWERS) || state.is(BlockTags.TALL_FLOWERS)) return 3;
        var block = state.getBlock();
        if (block == Blocks.GRASS || block == Blocks.FERN
                || block == Blocks.TALL_GRASS || block == Blocks.LARGE_FERN) {
            return 3;
        }
        return -1;
    }

    // ================= 蓄力 / 场 清理 =================

    public static void cancelCharge(ServerPlayer player) {
        CHARGING.remove(player.getUUID());
        CHARGE_START.remove(player.getUUID());
    }

    /** 登出 / 死亡 / 切换术式 / 中断：取消蓄力并立即还原该玩家全部在场树根 */
    public static void cleanup(ServerPlayer player) {
        cancelCharge(player);
        UUID uuid = player.getUUID();
        for (int i = FIELDS.size() - 1; i >= 0; i--) {
            RootField field = FIELDS.get(i);
            if (uuid.equals(field.ownerId)) {
                field.restore();
                FIELDS.remove(i);
            }
        }
    }

    /** 天逆鉾中断该玩家草木术式（蓄力 + 树根场全部取消并还原）；返回是否确有进行中 */
    public static boolean interruptAll(ServerPlayer player) {
        UUID uuid = player.getUUID();
        boolean had = CHARGING.containsKey(uuid);
        if (!had) {
            for (RootField field : FIELDS) {
                if (uuid.equals(field.ownerId)) {
                    had = true;
                    break;
                }
            }
        }
        cleanup(player);
        return had;
    }

    /** 天逆鉾右键甜浆果丛：移除包含该位置的树根场并还原；返回是否命中 */
    public static boolean removeFieldAt(ServerLevel level, BlockPos pos) {
        for (int i = FIELDS.size() - 1; i >= 0; i--) {
            RootField field = FIELDS.get(i);
            if (field.level == level && field.blocks.containsKey(pos)) {
                field.restore();
                FIELDS.remove(i);
                return true;
            }
        }
        return false;
    }

    // ================= 服务端 tick / 方块破坏拦截 =================

    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class PlantEvents {

        /** 树根场逐 tick：到期还原；间隔对场内敌人造成咒术伤害 + 减速 */
        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            if (FIELDS.isEmpty()) return;
            // CopyOnWriteArrayList 迭代器不支持 remove → 下标逆序删除
            for (int i = FIELDS.size() - 1; i >= 0; i--) {
                if (FIELDS.get(i).tick()) {
                    FIELDS.remove(i);
                }
            }
        }

        /** 树根持续期间，甜浆果丛不可被破坏（不掉落 → 无法快速逃走） */
        @SubscribeEvent
        public static void onBlockBreak(BlockEvent.BreakEvent event) {
            if (FIELDS.isEmpty()) return;
            if (!event.getState().is(Blocks.SWEET_BERRY_BUSH)) return;
            if (isFieldBlock(event.getPos())) {
                event.setCanceled(true);
            }
        }

        /** 树根持续期间禁止采摘/催熟（右击会被拦截，浆果丛不结果、不掉甜浆果）；手持天逆鉾例外（由其中断处理器接管） */
        @SubscribeEvent
        public static void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
            if (FIELDS.isEmpty()) return;
            if (event.getLevel().isClientSide) return;
            net.minecraft.world.entity.player.Player p = event.getEntity();
            if (p != null && p.getMainHandItem().getItem()
                    instanceof com.mofengbaizhi.tinkersnewlife.content.item.TianNiHuoItem) {
                return;
            }
            if (!event.getLevel().getBlockState(event.getPos()).is(Blocks.SWEET_BERRY_BUSH)) return;
            if (isFieldBlock(event.getPos())) {
                event.setCanceled(true);
            }
        }

        private static boolean isFieldBlock(BlockPos pos) {
            for (RootField field : FIELDS) {
                if (field.blocks.containsKey(pos)) return true;
            }
            return false;
        }
    }

    /** 一个树根场：记录已放置的浆果丛与原始方块，到期还原；2 秒内从幼苗长到满阶段 */
    private static final class RootField {
        private final ServerLevel level;
        private final UUID ownerId;
        private final BlockPos center;
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private int ticksLeft = FIELD_DURATION;
        private int hitTimer = 10;
        private int growthTicks = 0;
        private int growthAge = 0;

        RootField(ServerLevel level, UUID ownerId, BlockPos center) {
            this.level = level;
            this.ownerId = ownerId;
            this.center = center;
        }

        void tryPlace(ServerLevel world, BlockPos pos) {
            // 该列自目标脚面向下找实心地表（跳过草/花等可替换植物），浆果丛种在地表上一格
            int cy = pos.getY();
            int surfaceY = -1;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int yy = cy; yy >= cy - 4; yy--) {
                BlockState s = world.getBlockState(m.set(pos.getX(), yy, pos.getZ()));
                if (s.isAir() || !s.getFluidState().isEmpty() || s.canBeReplaced()) continue;
                surfaceY = yy;
                break;
            }
            if (surfaceY == -1) return;
            BlockPos plantPos = new BlockPos(pos.getX(), surfaceY + 1, pos.getZ());
            if (Math.abs(plantPos.getY() - cy) > 2) return; // 只在目标周围的地表高度种
            BlockState current = world.getBlockState(plantPos);
            if (!current.getFluidState().isEmpty()) return;
            if (!current.isAir() && !current.canBeReplaced()) return;
            blocks.put(plantPos.immutable(), current);
            world.setBlock(plantPos, Blocks.SWEET_BERRY_BUSH.defaultBlockState(), 2);
        }

        /** 每 tick；返回 true 表示场结束应移除 */
        boolean tick() {
            ticksLeft--;
            // 生长动画：幼苗(AGE 0) 每 10 tick 长一阶段，2 秒到满阶段(AGE 3)，不结果
            growthTicks++;
            int stage = Math.min(3, growthTicks / 10);
            if (stage != growthAge) {
                growthAge = stage;
                BlockState grown = Blocks.SWEET_BERRY_BUSH.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE, stage);
                for (BlockPos pos : blocks.keySet()) {
                    if (level.getBlockState(pos).is(Blocks.SWEET_BERRY_BUSH)) {
                        level.setBlock(pos, grown, 2);
                    }
                }
            }
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
            AABB box = new AABB(center.getX() - 2.5, center.getY() - 1.0, center.getZ() - 2.5,
                    center.getX() + 2.5, center.getY() + 3.0, center.getZ() + 2.5);
            List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e.isAlive() && !PuppetUtil.isAllyOf(e, owner));
            for (LivingEntity victim : victims) {
                float dmg = 1.0F;
                if (owner != null) {
                    double raw = PlantManipulationTechnique.INSTANCE.amplifyTechniqueDamage(owner,
                            PlantManipulationTechnique.INSTANCE.computeBaseDamage(owner) * ROOT_HIT_FACTOR);
                    raw = CurseCoreTraitHelper.applyCurseCoreTraits(owner, victim, raw);
                    dmg = (float) raw;
                }
                victim.invulnerableTime = 0;
                if (owner != null) {
                    victim.hurt(owner.damageSources().mobAttack(owner), dmg);
                }
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, false, true));
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        victim.getX(), victim.getY() + 0.5, victim.getZ(), 4, 0.4, 0.4, 0.4, 0);
            }
        }

        void restore() {
            for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
                BlockPos pos = entry.getKey();
                if (level.getBlockState(pos).is(Blocks.SWEET_BERRY_BUSH)) {
                    level.setBlock(pos, entry.getValue(), 2);
                }
            }
            blocks.clear();
        }
    }
}
