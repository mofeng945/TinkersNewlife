package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetUtil;
import com.mofengbaizhi.tinkersnewlife.content.entity.SpiritVortexEntity;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenSpiritScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 术式「咒灵操术」（仿咒术回战·夏油杰）。
 * <p>
 * 顺转：视线目标为亡灵且剩余血量 ≤ 2.5% → 将该【个体】记录入 GUI（存档完整 NBT），目标消散为
 * 黑色粒子；消耗 = ceil(max(1, 生命上限/20 × (10 + 输出×4) × (1 - 亲和/100)))，随后自身获得
 * 30s 饥饿 + 10s 反胃。否则打开个体列表 GUI（滚动条 + 3D 展示）：
 * 选择未释放个体 → 满血释放，以施术者为主人（只攻击威胁主人或主人攻击的目标，无视主人/同队）；
 * 已释放个体再次选择 → 收回；释放体战死 → 该记录从 GUI 消失。
 * <p>
 * 反转：GUI 选择一名未释放个体 → 清除其数据并进入漩涡蓄力；
 * 再次按反转键 → 向视线笔直射出黑色漩涡（伤害 = round((1+亲和/100) × (输出×6 + 生命上限×0.4 + 攻击×6))）。
 */
public final class CursedSpiritTechnique extends BaseTechnique {

    public static final CursedSpiritTechnique INSTANCE = new CursedSpiritTechnique();

    private static final String KEY_SPIRITS = "tinkersnewlife.cursed_spirits";

    /** 模式：0=释放/收回 GUI；1=献祭蓄力 GUI */
    public static final int MODE_RELEASE = 0;
    public static final int MODE_SACRIFICE = 1;

    /** 漩涡蓄力中：玩家 UUID → 漩涡伤害快照 */
    private static final Map<UUID, Float> VORTEX_CHARGE = new ConcurrentHashMap<>();

    private CursedSpiritTechnique() {
        super(Modifiers.CURSED_SPIRIT.getId());
    }

    // ================= 个体记录 =================

    public static final class SpiritEntry {
        public UUID uid;
        public String type = "";
        public String name = "";
        public CompoundTag nbt;
        public float maxHp;
        public float atk;
        public int releasedId = -1; // 当前场上释放体 entity id，-1 = 未释放

        SpiritEntry() {}

        CompoundTag toNBT() {
            CompoundTag t = new CompoundTag();
            t.putUUID("uid", uid);
            t.putString("type", type);
            t.putString("name", name == null ? "" : name);
            t.put("nbt", nbt);
            t.putFloat("maxHp", maxHp);
            t.putFloat("atk", atk);
            t.putInt("released", releasedId);
            return t;
        }

        static SpiritEntry fromNBT(CompoundTag t) {
            SpiritEntry e = new SpiritEntry();
            e.uid = t.getUUID("uid");
            e.type = t.getString("type");
            e.name = t.getString("name");
            e.nbt = t.getCompound("nbt");
            e.maxHp = t.getFloat("maxHp");
            e.atk = t.getFloat("atk");
            e.releasedId = t.getInt("released");
            return e;
        }
    }

    private static ListTag rawList(ServerPlayer player) {
        if (!player.getPersistentData().contains(KEY_SPIRITS)) {
            player.getPersistentData().put(KEY_SPIRITS, new ListTag());
        }
        return player.getPersistentData().getList(KEY_SPIRITS, Tag.TAG_COMPOUND);
    }

    /** 读取全部个体记录（按 GUI 行序） */
    public static List<SpiritEntry> entries(ServerPlayer player) {
        List<SpiritEntry> list = new ArrayList<>();
        for (Tag t : rawList(player)) {
            if (t instanceof CompoundTag c) {
                list.add(SpiritEntry.fromNBT(c));
            }
        }
        return list;
    }

    private static void saveAll(ServerPlayer player, List<SpiritEntry> list) {
        ListTag tag = new ListTag();
        for (SpiritEntry e : list) {
            tag.add(e.toNBT());
        }
        player.getPersistentData().put(KEY_SPIRITS, tag);
    }

    private static void append(ServerPlayer player, SpiritEntry e) {
        List<SpiritEntry> list = entries(player);
        list.add(e);
        saveAll(player, list);
    }

    private static void removeEntry(ServerPlayer player, UUID uid) {
        List<SpiritEntry> list = entries(player);
        list.removeIf(e -> e.uid.equals(uid));
        saveAll(player, list);
    }

    private static void updateEntry(ServerPlayer player, SpiritEntry updated) {
        List<SpiritEntry> list = entries(player);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).uid.equals(updated.uid)) {
                list.set(i, updated);
                break;
            }
        }
        saveAll(player, list);
    }

    // ================= 顺转 =================

    @Override
    public void onKeyPress(ServerPlayer player) {
        LivingEntity target = findTarget(player);
        if (target != null && target.getMobType() == MobType.UNDEAD && target.isAlive()
                && target.getHealth() <= target.getMaxHealth() * 0.025F) {
            capture(player, target);
            return;
        }
        // 不满足（无目标/非亡灵/血量未到斩杀线）→ 打开释放/收回 GUI
        openGui(player, MODE_RELEASE);
    }

    /** 濒死亡灵：记录个体并令其消散 */
    private void capture(ServerPlayer player, LivingEntity target) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        double maxHp = Math.max(1.0, target.getMaxHealth());
        int cost = (int) Math.ceil(Math.max(1.0,
                (maxHp / 20.0) * (10.0 + output * 4.0) * (1.0 - affinity / 100.0)));
        if (!CursePowerHelper.isCurseInfinite(player)
                && CursePowerHelper.payCurseWithSoulFallback(player, cost) < 0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        // 快照完整 NBT（含手持/装备/属性/效果）
        CompoundTag nbt = target.saveWithoutId(new CompoundTag());
        SpiritEntry entry = new SpiritEntry();
        entry.uid = UUID.randomUUID();
        entry.type = EntityType.getKey(target.getType()).toString();
        entry.name = target.getName().getString();
        entry.nbt = nbt;
        entry.maxHp = (float) maxHp;
        entry.atk = (float) target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        append(player, entry);

        // 消散（黑色粒子）；boss 战直接结束
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.SMOKE,
                target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 80, 0.8, 1.0, 0.8, 0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 40, 0.5, 0.8, 0.5, 0.01);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.HOSTILE, 1.0F, 0.6F);
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().playerAttack(player), 1.0E9F);
        if (target.isAlive()) {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().magic(), 1.0E9F);
        }
        if (target.isAlive()) {
            target.kill();
        }

        // 施术者代价：30s 饥饿 + 10s 反胃
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 0, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0, false, true));
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.captured", entry.name), true);
    }

    /** GUI 选择（顺转：释放/收回；反转：献祭蓄力）。row 为当前列表下标 */
    public static void selectRow(ServerPlayer player, int mode, int row) {
        List<SpiritEntry> list = entries(player);
        if (row < 0 || row >= list.size()) return;
        SpiritEntry entry = list.get(row);
        if (mode == MODE_RELEASE) {
            toggleRelease(player, entry);
        } else {
            sacrifice(player, entry);
        }
    }

    private static void toggleRelease(ServerPlayer player, SpiritEntry entry) {
        if (entry.releasedId >= 0) {
            // 收回（保留记录）
            if (player.serverLevel().getEntity(entry.releasedId) instanceof Mob mob && mob.isAlive()) {
                mob.discard();
            }
            entry.releasedId = -1;
            updateEntry(player, entry);
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.recall", entry.name), true);
            return;
        }
        // 释放满血个体
        EntityType<?> type = EntityType.byString(entry.type).orElse(null);
        if (type == null) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.invalid"), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        Entity spawned = type.create(level);
        if (!(spawned instanceof LivingEntity living)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.invalid"), true);
            return;
        }
        CompoundTag nbt = entry.nbt.copy();
        nbt.remove("UUID");
        nbt.remove("Pos");
        nbt.remove("Dimension");
        nbt.remove("Motion");
        nbt.remove("WorldUUIDMost");
        nbt.remove("WorldUUIDLeast");
        living.load(nbt);
        living.setHealth(Math.max(1.0F, entry.maxHp));
        if (living instanceof Mob mob) {
            mob.setPersistenceRequired();
            // ⭐ 剥除该个体自带的目标选择目标（如"找最近玩家"），防止它自选主人/同队；
            //    其攻击目标一律由 SpiritEvents 每 tick 指派。
            stripTargetGoals(mob);
        }
        // 主人面前 1.5 格生成，自动找安全高度
        Vec3 look = player.getLookAngle();
        Vec3 fwd = new Vec3(look.x, 0, look.z).normalize();
        if (fwd.lengthSqr() < 0.01) fwd = new Vec3(0, 0, 1);
        double px = player.getX() + fwd.x * 1.5;
        double pz = player.getZ() + fwd.z * 1.5;
        double py = safeY(level, px, player.getY(), pz, living);
        living.moveTo(px, py, pz, player.getYRot(), 0);
        level.addFreshEntity(living);
        entry.releasedId = living.getId();
        updateEntry(player, entry);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.released", entry.name), true);
    }

    private static void sacrifice(ServerPlayer player, SpiritEntry entry) {
        // 献祭：清除记录并进入漩涡蓄力
        removeEntry(player, entry.uid);
        float dmg = (float) Math.round((1.0 + CursePowerHelper.getCurseAffinity(player) / 100.0)
                * (CursePowerHelper.getCurseOutputLevel(player) * 6.0 + entry.maxHp * 0.4 + entry.atk * 6.0));
        VORTEX_CHARGE.put(player.getUUID(), dmg);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.vortex_charge"), true);
    }

    // ================= 反转 =================

    @Override
    public void onReverseKeyPress(ServerPlayer player) {
        Float charged = VORTEX_CHARGE.remove(player.getUUID());
        if (charged != null) {
            fireVortex(player, charged);
            return;
        }
        // 未蓄力：打开献祭选择 GUI（仅未释放个体）
        openGui(player, MODE_SACRIFICE);
    }

    private void fireVortex(ServerPlayer player, float damage) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        double m = 1.0 + (output + affinity / 10.0) / 10.0;
        int cost = (int) Math.ceil(m * 50.0);
        if (!CursePowerHelper.isCurseInfinite(player)
                && CursePowerHelper.payCurseWithSoulFallback(player, cost) < 0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        SpiritVortexEntity vortex = new SpiritVortexEntity(ModEntities.SPIRIT_VORTEX.get(), level);
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        vortex.moveTo(eye.x, eye.y, eye.z, player.getYRot(), player.getXRot());
        vortex.launch(player, damage, look);
        level.addFreshEntity(vortex);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.vortex_shot"), true);
    }

    // ================= GUI 数据 =================

    /** 打开 GUI：mode=0 释放/收回；1 献祭（服务端构建条目列表发往客户端） */
    private void openGui(ServerPlayer player, int mode) {
        List<SpiritEntry> list = entries(player);
        if (mode == MODE_SACRIFICE) {
            // 献祭只能选未释放个体
            list.removeIf(e -> e.releasedId >= 0);
            if (list.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.no_stored"), true);
                return;
            }
        } else if (list.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.no_stored"), true);
            return;
        }
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketOpenSpiritScreen(mode, list));
    }

    /** 登出/死亡清理：撤销场上释放体（保留记录），清除蓄力 */
    public static void cleanup(ServerPlayer player) {
        VORTEX_CHARGE.remove(player.getUUID());
        List<SpiritEntry> list = entries(player);
        for (SpiritEntry e : list) {
            if (e.releasedId >= 0
                    && player.serverLevel().getEntity(e.releasedId) instanceof Mob mob && mob.isAlive()) {
                mob.discard();
            }
            e.releasedId = -1;
        }
        saveAll(player, list);
    }

    /** 该实体是否为某玩家的场上释放体（同队豁免用） */
    public static boolean isReleasedMinionOf(Entity target, ServerPlayer owner) {
        if (target == null || owner == null) return false;
        for (SpiritEntry e : entries(owner)) {
            if (e.releasedId >= 0 && target.getId() == e.releasedId) return true;
        }
        return false;
    }

    /** 释放体战死 → 记录从 GUI 消失 */
    public static void onMinionDeath(Entity dead) {
        if (dead.level().isClientSide) return;
        if (!(dead.level() instanceof ServerLevel sl)) return;
        for (ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) {
            List<SpiritEntry> list = entries(p);
            boolean removed = list.removeIf(e -> e.releasedId >= 0 && dead.getId() == e.releasedId);
            if (removed) {
                saveAll(p, list);
                p.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.lost", dead.getName().getString()), true);
                return;
            }
        }
    }

    /** 反射移除该 Mob 的所有目标选择目标（目标统一由操控 tick 指派） */
    private static void stripTargetGoals(Mob mob) {
        try {
            java.lang.reflect.Field field = Mob.class.getDeclaredField("targetSelector");
            field.setAccessible(true);
            if (!(field.get(mob) instanceof net.minecraft.world.entity.ai.goal.GoalSelector selector)) {
                return;
            }
            for (net.minecraft.world.entity.ai.goal.WrappedGoal wg :
                    new ArrayList<>(selector.getAvailableGoals())) {
                selector.removeGoal(wg.getGoal());
            }
        } catch (Throwable ignored) {
            // 反射失败不影响释放；由 tick 清除 + 伤害取消兜底
        }
    }

    private static double safeY(ServerLevel level, double x, double y, double z, Entity e) {
        for (int i = 0; i < 4; i++) {
            double cy = y + i;
            if (level.noCollision(e, e.getBoundingBox().move(x - e.getX(), cy - e.getY(), z - e.getZ()))) {
                return cy;
            }
        }
        return y;
    }

    // ================= 服务端 tick：释放体操控 =================

    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class SpiritEvents {

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            net.minecraft.server.MinecraftServer server = event.getServer();
            if (server == null) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                for (SpiritEntry e : entries(player)) {
                    if (e.releasedId < 0) continue;
                    if (!(player.serverLevel().getEntity(e.releasedId) instanceof Mob minion)
                            || !minion.isAlive()) {
                        continue;
                    }
                    // 主人在线：指派目标 = 主人攻击的目标 / 攻击主人的目标
                    LivingEntity want = null;
                    LivingEntity attack = player.getLastHurtMob();
                    if (attack != null && attack.isAlive() && !PuppetUtil.isAllyOf(attack, player)) {
                        want = attack;
                    }
                    if (want == null) {
                        LivingEntity threat = player.getLastHurtByMob();
                        if (threat != null && threat.isAlive() && !PuppetUtil.isAllyOf(threat, player)) {
                            want = threat;
                        }
                    }
                    if (want != null) {
                        minion.setTarget(want);
                    } else {
                        // 无指令：清掉指向主人/同队的目标（无视施术者）
                        LivingEntity cur = minion.getTarget();
                        if (cur == null || PuppetUtil.isAllyOf(cur, player)) {
                            minion.setTarget(null);
                        }
                    }
                }
            }
        }

        @SubscribeEvent
        public static void onMinionDeathEvent(LivingDeathEvent event) {
            onMinionDeath(event.getEntity());
        }

        /** 兜底：已释放体对主人/同队造成伤害时直接取消（防个别目标选择遗漏） */
        @SubscribeEvent
        public static void onMinionAttackAlly(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
            if (event.getEntity().level().isClientSide) return;
            Entity source = event.getSource().getEntity();
            if (!(source instanceof Mob minion)) return;
            LivingEntity target = event.getEntity();
            ServerPlayer owner = findOwnerOf(minion);
            if (owner == null) return;
            if (PuppetUtil.isAllyOf(target, owner)) {
                event.setCanceled(true);
            }
        }

        private static ServerPlayer findOwnerOf(Mob minion) {
            if (minion.level() instanceof ServerLevel sl) {
                for (ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) {
                    for (SpiritEntry e : entries(p)) {
                        if (e.releasedId >= 0 && minion.getId() == e.releasedId) {
                            return p;
                        }
                    }
                }
            }
            return null;
        }
    }
}
