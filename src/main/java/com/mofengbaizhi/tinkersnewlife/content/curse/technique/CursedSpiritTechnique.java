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
        /**
         * 当前场上释放体的 <b>UUID</b>（普通释放体也要记）。
         * <p>为什么需要：entity id 只在一次会话内有效 —— 登出/重启后 {@code normalize()}
         * 会把 {@code releasedId} 复位，而普通释放体没有 UUID 就**再也认不回**场上的实体，
         * 于是出现"UI 不更新、收回无效、还能再放一只"。守护体靠 {@code guardUuid} 早就解决了，
         * 这里把同一套办法补到所有释放体上。
         */
        public String releasedUuid = "";
        /** 无为转变·守护形态：释放/重链时按守护随从 AI 处理 */
        public boolean guard = false;
        /** 守护实体 UUID（跨登出/区块卸载后按此重链回释放位） */
        public String guardUuid = "";

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
            t.putString("releasedUuid", releasedUuid == null ? "" : releasedUuid);
            t.putBoolean("guard", guard);
            t.putString("guardUuid", guardUuid == null ? "" : guardUuid);
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
            e.releasedUuid = t.getString("releasedUuid");
            e.guard = t.getBoolean("guard");
            e.guardUuid = t.getString("guardUuid");
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
        entry.atk = attackDamageOf(target);
        append(player, entry);

        // 消散（黑色粒子）；boss 战直接结束
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.SMOKE,
                target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 80, 0.8, 1.0, 0.8, 0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 40, 0.5, 0.8, 0.5, 0.01);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.HOSTILE, 1.0F, 0.6F);
        // 注：命灯指轮只截"攻击者是佩戴者"的**伤害**，不会拦死亡事件 ——
        //     所以下面 playerAttack/magic 两下巨伤会被截（无害），最后那下 kill() 伤害源是
        //     genericKill（没有攻击者），命灯完全不介入，收服照常完成。
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
        // ⭐ "场上有没有这个释放体"必须同时看 releasedId 与 guardUuid：
        //    守护体跨登出/区块重载、或主人把它无为转变之后，releasedId 可能已经失效（甚至 -1），
        //    只剩 guardUuid 还能认亲。以前只判 releasedId >= 0，于是出现
        //    "被转成村民的守护体收不回来、还能再放一只旧的"。
        Mob live = resolveLive(player, entry);
        if (live != null) {
            // 收回（保留记录）：走原版死亡链路（含召唤物清理），见 recallReleased
            recallReleased(live);
            entry.releasedId = -1;
            entry.guardUuid = "";
            updateEntry(player, entry);
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.recall", entry.name), true);
            return;
        }
        // 记录说"已释放"但实体其实不在了（被打死/区块卸载/换形态丢了 id）→ 先清掉失效标记再重新释放
        if (entry.releasedId >= 0 || (entry.guardUuid != null && !entry.guardUuid.isEmpty())) {
            entry.releasedId = -1;
            entry.guardUuid = "";
            updateEntry(player, entry);
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
        if (living instanceof Mob mob) {
            if (entry.guard) {
                // 守护形态：释放后按"玉犬式守护随从 AI"行动（无为转变·守护；入世后再挂，需有效实体 id）
                com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler.attachGuardAi(mob, player);
            } else {
                // ⭐ 剥除该个体自带的目标选择目标（如"找最近玩家"），防止它自选主人/同队；
                //    其攻击目标一律由 SpiritEvents 每 tick 指派。
                stripTargetGoals(mob);
            }
        }
        entry.releasedId = living.getId();
        entry.releasedUuid = living.getStringUUID();
        if (entry.guard) {
            entry.guardUuid = living.getStringUUID();
        }
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
        // 模块化魔杖增幅（无杖时原样）
        damage = com.mofengbaizhi.tinkersnewlife.content.modifier.ModularStaffModifier
                .getSpellAmplification(player, damage);
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

    /** 登出/死亡清理：撤销场上普通释放体（保留记录，召唤物一并清除），清除蓄力；
     *  守护形态（无为转变·守护）随从保留在场（由无为守护系统持久），仅解除释放位链接。 */
    public static void cleanup(ServerPlayer player) {
        VORTEX_CHARGE.remove(player.getUUID());
        List<SpiritEntry> list = entries(player);
        for (SpiritEntry e : list) {
            if (e.releasedId >= 0
                    && player.serverLevel().getEntity(e.releasedId) instanceof Mob mob && mob.isAlive()) {
                if (mob.getPersistentData().contains(
                        com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler.KEY_GUARD_OWNER)) {
                    // 守护随从：保留（登出后仍站岗），记录保持 guardUuid 待重链
                    e.releasedId = -1;
                    continue;
                }
                dismissServantsOf(mob);
                mob.discard();
            }
            e.releasedId = -1;
        }
        saveAll(player, list);
    }

    /** 该实体是否为某玩家的场上释放体（同队豁免用） */
    public static boolean isReleasedMinionOf(Entity target, ServerPlayer owner) {
        if (target == null || owner == null) return false;
        return findEntryFor(owner, target) != null;
    }

    /**
     * 找某玩家记录里"对应这个实体"的条目。
     *
     * <p>⭐ 必须同时认两种身份，缺一个就会出 bug：
     * <ul>
     *   <li>{@code releasedId}：当前场上的释放体（entity id）；</li>
     *   <li>{@code guardUuid}：<b>守护形态</b>的 UUID —— 守护体的 {@code releasedId} 在
     *       跨登出/区块重载/被无为转变改写后可能已经失效（甚至指向别的实体），
     *       这时只能靠 UUID 认亲。以前这里只比 {@code releasedId}，于是出现
     *       "被转成村民的守护体：UI 里还是旧形态、收回也收不掉、还能再放一只旧的"。</li>
     * </ul>
     */
    public static SpiritEntry findEntryFor(ServerPlayer owner, Entity target) {
        if (owner == null || target == null) return null;
        String uuid = target.getStringUUID();
        for (SpiritEntry e : entries(owner)) {
            if (e.releasedId >= 0 && target.getId() == e.releasedId) return e;
            if (e.releasedUuid != null && !e.releasedUuid.isEmpty() && e.releasedUuid.equals(uuid)) return e;
            if (e.guardUuid != null && !e.guardUuid.isEmpty() && e.guardUuid.equals(uuid)) return e;
        }
        return null;
    }

    /** 该实体是哪位玩家的场上释放体；不是任何人的释放体返回 null（服务端用） */
    public static ServerPlayer ownerOfReleased(Entity target) {
        if (target == null || target.level().isClientSide) return null;
        if (!(target.level() instanceof ServerLevel sl)) return null;
        for (ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) {
            if (isReleasedMinionOf(target, p)) return p;
        }
        return null;
    }

    /** 天逆鉾右键仆从：直接收回（保留记录，召唤物一并清除），返回是否命中 */
    public static boolean recallByEntity(Entity target) {
        ServerPlayer owner = ownerOfReleased(target);
        if (owner == null) return false;
        List<SpiritEntry> list = entries(owner);
        for (SpiritEntry e : list) {
            if (isSameEntry(owner, target, e)) {
                Mob live = resolveLive(owner, e);
                if (live != null) {
                    recallReleased(live);          // 走原版死亡链路（含召唤物清理）
                }
                e.releasedId = -1;
                e.guardUuid = "";
                saveAll(owner, list);
                owner.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.recall", e.name), true);
                return true;
            }
        }
        return false;
    }

    /** 无为转变·他人把释放体变形：清除该个体记录（视为失去） */
    public static void removeOnForeignTransform(Entity target) {
        ServerPlayer owner = ownerOfReleased(target);
        if (owner == null) return;
        String name = target.getName().getString();
        List<SpiritEntry> list = entries(owner);
        SpiritEntry hit = findEntryFor(owner, target); if (hit != null) list.removeIf(x -> x.uid != null && x.uid.equals(hit.uid));
        saveAll(owner, list);
        owner.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.lost_foreign", name), true);
    }

    /** 无为转变·主人自己把释放体变形：收回并把记录改写为新形态（NBT/类型/属性快照） */
    public static void modifyOnOwnerTransform(ServerPlayer owner, Entity oldReleased, Mob newForm) {
        List<SpiritEntry> list = entries(owner);
        for (SpiritEntry e : list) {
            if (isSameEntry(owner, oldReleased, e)) {
                CompoundTag nbt = newForm.saveWithoutId(new CompoundTag());
                e.nbt = nbt;
                e.type = EntityType.getKey(newForm.getType()).toString();
                e.name = newForm.getName().getString();
                e.maxHp = newForm.getMaxHealth();
                e.atk = attackDamageOf(newForm);
                e.releasedId = -1;
                saveAll(owner, list);
                owner.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.modified", e.name), true);
                return;
            }
        }
    }

    /**
     * 无为转变·主人自己把场上释放体变形成守护随从：记录改写为新形态，并<b>保持"场上释放体"身份</b>
     * （releasedId 指向新实体；guard 标记 + 实体 UUID 供跨登出重链）。新形态由无为守护系统按随从 AI 驱动。
     */
    public static void relinkReleasedAsGuard(ServerPlayer owner, Entity oldReleased, Mob newForm) {
        List<SpiritEntry> list = entries(owner);
        for (SpiritEntry e : list) {
            if (isSameEntry(owner, oldReleased, e)) {
                CompoundTag nbt = newForm.saveWithoutId(new CompoundTag());
                e.nbt = nbt;
                e.type = EntityType.getKey(newForm.getType()).toString();
                e.name = newForm.getName().getString();
                e.maxHp = newForm.getMaxHealth();
                e.atk = attackDamageOf(newForm);
                e.guard = true;
                e.guardUuid = newForm.getStringUUID();
                e.releasedId = newForm.getId();
                e.releasedUuid = newForm.getStringUUID();
                saveAll(owner, list);
                owner.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.modified", e.name), true);
                return;
            }
        }
    }

    /** 守护实体入世/重挂后重链：某记录的 guardUuid 与该实体一致 → 恢复 releasedId（跨登出/区块卸载保链接） */
    public static void relinkGuardByUuid(Mob guard) {
        if (guard.level().isClientSide) return;
        if (!(guard.level() instanceof ServerLevel sl)) return;
        for (ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) {
            List<SpiritEntry> list = entries(p);
            boolean changed = false;
            for (SpiritEntry e : list) {
                if (e.guard && e.releasedId < 0
                        && e.guardUuid != null && e.guardUuid.equals(guard.getStringUUID())) {
                    e.releasedId = guard.getId();
                    changed = true;
                }
            }
            if (changed) {
                saveAll(p, list);
                return;
            }
        }
    }

    /**
     * 场上活着的释放体实体：先按 {@code releasedId} 找，再按 {@code releasedUuid} / 守护 UUID 跨世界找。
     * <p>收回忆（{@code toggleRelease}）、"是否在场"判断、天逆鉾右键收回都走这里，
     * 保证跨登出/跨维度/被无为转变改写之后依然认得出自己人。
     */
    private static Mob resolveLive(ServerPlayer player, SpiritEntry entry) {
        if (entry.releasedId >= 0) {
            if (player.serverLevel().getEntity(entry.releasedId) instanceof Mob mob && mob.isAlive()) {
                return mob;
            }
        }
        Mob byUuid = findUuidEntity(player, entry.releasedUuid);
        if (byUuid != null) return byUuid;
        return findUuidEntity(player, entry.guardUuid);
    }

    /** 跨世界按 UUID 字符串找活着的生物（非法 UUID 返回 null） */
    private static Mob findUuidEntity(ServerPlayer owner, String uuidText) {
        if (uuidText == null || uuidText.isEmpty()) return null;
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidText);
        } catch (Throwable t) {
            return null;
        }
        for (net.minecraft.server.level.ServerLevel lvl : owner.serverLevel().getServer().getAllLevels()) {
            Entity ent = lvl.getEntity(uuid);
            if (ent instanceof Mob m && m.isAlive()) return m;
        }
        return null;
    }

    /** 找某记录守护实体是否仍在场（跨世界按 UUID 查） */
    private static Mob findGuardEntity(ServerPlayer owner, SpiritEntry e) {
        return findUuidEntity(owner, e.guardUuid);
    }

    /**
     * Goety 仆从（IServant/IOwned 实现）的主人实体。
     * <p>
     * ⭐ 先确认"它真的是 Goety 仆从"再去读主人：以前只按"方法名带 owner/master 且返回 LivingEntity"
     * 盲扫所有公开方法，于是 {@code TamableAnimal#getOwner()}（以及式神自己 {@code getOwner} 的桥接方法）
     * 都会被当成一条"主人链"——结果自己的宠物/式神被算成同队，未调伏式神连主人都打不动。
     */
    private static LivingEntity goetyOwnerOf(LivingEntity entity) {
        if (!com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isGoetyServant(entity)) return null;
        LivingEntity viaApi = com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.getServantOwner(entity);
        if (viaApi != null) return viaApi;
        // 兜底：仍按方法名找，但只对"确实是仆从"的实体生效
        try {
            for (java.lang.reflect.Method m : entity.getClass().getMethods()) {
                if (m.getParameterCount() != 0 || m.getReturnType() != LivingEntity.class) continue;
                String n = m.getName();
                if (!n.startsWith("get")) continue;
                String low = n.toLowerCase();
                if (low.contains("owner") || low.contains("master")) {
                    Object o = m.invoke(entity);
                    if (o instanceof LivingEntity le) return le;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 主人链是否追溯到 root（兼容实体实例/id 两种比较） */
    private static boolean chainReaches(LivingEntity entity, Entity root) {
        LivingEntity cur = entity;
        int rootId = root.getId();
        for (int depth = 0; depth < 6; depth++) {
            if (cur == null) return false;
            if (cur == root || cur.getId() == rootId) return true;
            cur = goetyOwnerOf(cur);
        }
        return false;
    }

    /** 收回/移除某仆从时，连同它召唤的小弟（Goety 主人链下级）一并清除 */
    public static void dismissServantsOf(Entity root) {
        if (root == null || root.level().isClientSide) return;
        if (!(root.level() instanceof ServerLevel sl)) return;
        for (Mob m : sl.getEntitiesOfClass(Mob.class, root.getBoundingBox().inflate(256.0),
                mm -> mm.isAlive() && mm != root)) {
            if (chainReaches(m, root)) {
                m.discard();
            }
        }
    }

    /**
     * 递归同队：target 本人、或其（Goety 仆从）主人链上的任意一环属于 owner 的释放体/owner 本人，
     * 都算 owner 的同队——覆盖"使徒释放的召唤物"这类二阶仆从。
     */
    public static boolean isSpiritTeam(LivingEntity target, ServerPlayer owner) {
        if (target == null || owner == null) return false;
        // ⭐ 十影式神不是 Goety 仆从：只有「已调伏 + 同主人」才算同队。
        //   未调伏（调伏战中）是敌人——主人必须能打死它，所以绝不能因为带 ownerId 就判成友军。
        //   （下面按方法名反射找主人的 goetyOwnerOf 会把 TamableAnimal#getOwner()/式神自己的
        //     getOwner 桥接方法误当"Goety 主人链"，玉犬这类 TamableAnimal 式神就会被算成同队。）
        if (target instanceof com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiMob sm) {
            return sm.isTamed() && owner.getUUID().equals(sm.getOwnerId());
        }
        LivingEntity cur = target;
        for (int depth = 0; depth < 6; depth++) {
            if (cur == owner) return true;
            if (isReleasedMinionOf(cur, owner)) return true;
            LivingEntity up = goetyOwnerOf(cur);
            if (up == null || up == cur) return false;
            cur = up;
        }
        return false;
    }

    /** 找出"拥有该实体（或其主人链）"的释放者玩家；找不到返回 null */
    private static ServerPlayer findTeamOwner(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel sl)) return null;
        for (ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) {
            if (isSpiritTeam(entity, p)) return p;
        }
        return null;
    }

    /**
     * 正在"主动收回"的实体：收回走的是死亡链路（见 {@link #recallReleased}），
     * 但**收回本意是保留记录**（下次还能再放），所以 {@link #onMinionDeath} 必须放行它们，
     * 不能按"战死"把记录删掉。
     */
    private static final java.util.Set<java.util.UUID> RECALLING =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 该实体当前是否正在被"主动收回"（掉落/经验抑制要用） */
    public static boolean isRecalling(net.minecraft.world.entity.Entity e) {
        return e != null && RECALLING.contains(e.getUUID());
    }

    /**
     * 安全读取攻击力。
     *
     * <p>⚠⚠ <b>必须判空</b>：蝙蝠、村民这类生物<b>没有</b>
     * {@code minecraft:generic.attack_damage} 属性，
     * 直接 {@code getAttributeValue(ATTACK_DAMAGE)} 会抛
     * {@code IllegalArgumentException: Can't find attribute minecraft:generic.attack_damage}。
     *
     * <p>实测事故：这个异常在 {@link #relinkReleasedAsGuard} 里抛出，
     * 使整段"改写记录"逻辑<b>中途中断、{@code saveAll} 从未执行</b> ——
     * 表现就是玩家报的"无为转变后 GUI 不更新、按收回又召一只"（日志里能看到完整栈）。
     * 凡是要读<b>任意生物</b>的属性，一律走这里。
     */
    private static float attackDamageOf(LivingEntity entity) {
        if (entity == null) return 0.0F;
        var inst = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        return inst == null ? 0.0F : (float) inst.getValue();
    }

    /**
     * 是不是同一条记录。
     * <p>⚠ {@link #findEntryFor} 每次都是从 NBT **重新解析**出新对象，所以绝不能比引用（{@code == e}），
     * 必须比记录自身的 {@code uid} —— 曾经因此出现"找到了主人、却匹配不上记录"，
     * 表现就是无为转变后 GUI 不更新、收回又召一只。
     */
    private static boolean isSameEntry(ServerPlayer owner, Entity target, SpiritEntry e) {
        SpiritEntry hit = findEntryFor(owner, target);
        return hit != null && e.uid != null && e.uid.equals(hit.uid);
    }

    /**
     * 顺手清掉"以该实体 UUID 为 id"的 Boss 血条（收回/守护体死亡时调用）。
     *
     * <p><b>为什么要这么写</b>：原版监守者**没有** Boss 血条（1.20.1 里 {@code ServerBossEvent}
     * 只属于凋灵 / 袭击 / 末影龙），玩家看到的监守者血条来自**别的 mod**。
     * 不少 mod 会把血条 id 设成实体 UUID，对这类实现发一个标准 REMOVE 包即可清掉；
     * 对"随机 UUID"实现无效，但也不会有任何副作用（客户端找不到该 id 就忽略）。
     */
    public static void clearEntityBossBar(net.minecraft.world.entity.Entity entity) {
        if (entity == null || entity.level().isClientSide) return;
        if (!(entity.level() instanceof ServerLevel sl)) return;
        net.minecraft.network.protocol.game.ClientboundBossEventPacket pkt =
                net.minecraft.network.protocol.game.ClientboundBossEventPacket
                        .createRemovePacket(entity.getUUID());
        for (ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) {
            if (p.level() == sl) {
                p.connection.send(pkt);
            }
        }
    }

    /**
     * 收回一个释放体（保留记录）。
     *
     * <p><b>为什么走原版死亡链路</b>：这样所有"仆从死亡"相关逻辑与实体追踪解除都按原版正常路径结算
     * （以前直接 {@code discard()} 是"静默移除"，绕过了这一整套）。
     *
     * <p><b>三道保险</b>：
     * <ol>
     *   <li>击杀期间打上 {@link #RECALLING} 标记 → {@link #onMinionDeath} 不删记录（收回 ≠ 战死）；</li>
     *   <li>同标记让 WuWeiHandler 的掉落/经验抑制放行收回体 → <b>收回不掉任何东西</b>；</li>
     *   <li>若死亡被别的 mod 取消（复活/免疫/阶段转换），兜底 {@code discard()}，收回一定生效。</li>
     * </ol>
     * 顺手 {@code setSilent(true)}：收回不是击杀，不该冒出死亡音效。
     */
    public static void recallReleased(Mob mob) {
        if (mob == null) return;
        dismissServantsOf(mob);
        if (!mob.isAlive() || mob.isRemoved()) {
            mob.discard();
            return;
        }

        boolean wasSilent = mob.isSilent();
        clearEntityBossBar(mob);
        if (mob instanceof net.minecraft.world.entity.monster.warden.Warden) {
            com.mofengbaizhi.tinkersnewlife.network.curse.PacketDropWardenBars.broadcast();
        }
        mob.setSilent(true);
        RECALLING.add(mob.getUUID());
        try {
            mob.invulnerableTime = 0;
            mob.hurt(mob.damageSources().genericKill(), Float.MAX_VALUE);
        } finally {
            RECALLING.remove(mob.getUUID());
        }
        if (mob.isAlive() && !mob.isRemoved()) {
            mob.discard();          // 死亡被取消 → 兜底
        } else {
            mob.setSilent(wasSilent);
        }
    }

    /** 释放体战死 → 记录从 GUI 消失，其召唤物一并清除（玩家本人死亡绝不算释放体战死） */
    public static void onMinionDeath(Entity dead) {
        if (dead.level().isClientSide) return;
        if (dead instanceof net.minecraft.world.entity.player.Player) return; // 玩家不是释放体
        if (!(dead.level() instanceof ServerLevel sl)) return;
        dismissServantsOf(dead);
        clearEntityBossBar(dead);
        if (dead instanceof net.minecraft.world.entity.monster.warden.Warden) {
            com.mofengbaizhi.tinkersnewlife.network.curse.PacketDropWardenBars.broadcast();
        }
        // ⭐ 主动收回（recallReleased）也会走到这里：保留记录，只清召唤物
        if (RECALLING.contains(dead.getUUID())) return;
        for (ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) {
            List<SpiritEntry> list = entries(p);
            SpiritEntry hit = findEntryFor(p, dead); boolean removed = hit != null && list.removeIf(x -> x.uid != null && x.uid.equals(hit.uid));
            if (removed) {
                saveAll(p, list);
                p.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.lost", dead.getName().getString()), true);
                return;
            }
        }
    }

    /** 登录/重生后矫正：场上实体 id 会失效，把所有 released 标记复位（记录保留）；
     *  守护形态记录按 guardUuid 找回场上守护实体重链释放位。 */
    public static void normalize(ServerPlayer player) {
        List<SpiritEntry> list = entries(player);
        boolean changed = false;
        for (SpiritEntry e : list) {
            if (e.releasedId >= 0) {
                e.releasedId = -1;
                changed = true;
            }
        }
        // 守护形态：找场上守护实体（可能已加载）恢复链接
        for (SpiritEntry e : list) {
            if (e.releasedId < 0 && (e.guard || !e.releasedUuid.isEmpty())) {
                Mob g = findGuardEntity(player, e);
                if (g == null) g = findUuidEntity(player, e.releasedUuid);
                if (g != null) {
                    e.releasedId = g.getId();
                    changed = true;
                }
            }
        }
        if (changed) {
            saveAll(player, list);
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
                    // 守护形态（无为转变·守护）：由无为守护系统每 tick 驱动（跟随/护主/近战），此处不再操控
                    if (minion.getPersistentData().contains(
                            com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler.KEY_GUARD_OWNER)) {
                        continue;
                    }
                    // 主人在线：指派目标 = 主人攻击的目标 / 攻击主人的目标（保护主人）
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
                        continue;
                    }
                    // 无指令：清掉指向主人/同队的目标（无视施术者）
                    LivingEntity cur = minion.getTarget();
                    if (cur == null || PuppetUtil.isAllyOf(cur, player)) {
                        minion.setTarget(null);
                    }
                    // 跟随主人：过远传送、稍远走过去、贴身待命
                    double distSq = minion.distanceToSqr(player);
                    if (distSq > 64.0 * 64.0) {
                        double dx = (player.getRandom().nextDouble() - 0.5) * 2.0;
                        double dz = (player.getRandom().nextDouble() - 0.5) * 2.0;
                        minion.teleportTo(player.getX() + dx, player.getY(), player.getZ() + dz);
                        minion.getNavigation().stop();
                    } else if (distSq > 6.0 * 6.0) {
                        minion.getNavigation().moveTo(player, 1.05);
                    } else {
                        minion.getNavigation().stop();
                    }
                }
            }
        }

        @SubscribeEvent
        public static void onMinionDeathEvent(LivingDeathEvent event) {
            onMinionDeath(event.getEntity());
        }

        /** 登录时矫正 released 残留（防跨会话/重生后实体 id 撞车误删记录） */
        @SubscribeEvent
        public static void onPlayerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer sp) {
                normalize(sp);
            }
        }

        /**
         * 死亡重生：Forge 不会自动携带 persistentData —— 手动把咒灵记录（KEY_SPIRITS）拷给新实体，
         * 并复位 released（旧场上实体已随死亡清理）。这是"死亡后记录清空"的根治点。
         */
        @SubscribeEvent
        public static void onPlayerClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
            if (!event.isWasDeath()) return;
            if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
            CompoundTag src = event.getOriginal().getPersistentData();
            if (src.contains(KEY_SPIRITS)) {
                newPlayer.getPersistentData().put(KEY_SPIRITS, src.get(KEY_SPIRITS).copy());
            }
            normalize(newPlayer);
        }

        /** 换目标拦截：任何来源（含 Boss 自定义 AI / 其召唤物）都不能把（同队链上的）目标设为主人/同队 */
        @SubscribeEvent
        public static void onMinionTargetChange(net.minecraftforge.event.entity.living.LivingChangeTargetEvent event) {
            if (event.getEntity().level().isClientSide) return;
            if (!(event.getEntity() instanceof Mob minion)) return;
            LivingEntity newTarget = event.getNewTarget();
            if (newTarget == null) return;
            ServerPlayer owner = findTeamOwner(minion);
            if (owner == null) return;
            if (PuppetUtil.isAllyOf(newTarget, owner)) {
                event.setCanceled(true);
            }
        }

        /** 兜底：释放体/其召唤物对主人/同队造成伤害时直接取消 */
        @SubscribeEvent
        public static void onMinionAttackAlly(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
            if (event.getEntity().level().isClientSide) return;
            Entity source = event.getSource().getEntity();
            if (!(source instanceof LivingEntity srcLiving)) return;
            LivingEntity target = event.getEntity();
            ServerPlayer owner = findTeamOwner(srcLiving);
            if (owner == null) return;
            if (PuppetUtil.isAllyOf(target, owner)) {
                event.setCanceled(true);
            }
        }
    }
}
