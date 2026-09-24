package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetUtil;
import com.mofengbaizhi.tinkersnewlife.content.entity.SpiritVortexEntity;
import com.mofengbaizhi.tinkersnewlife.content.modifier.ModularStaffModifier;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenSpiritScreen;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketSpiritState;
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
 * 再次按反转键 → 向视线笔直射出黑色漩涡（伤害 = <b>该个体最大生命上限 × {@link #VORTEX_HP_RATIO}</b>，
 * 即按"献祭/施放的那只咒灵的血量上限"动态变化，见 {@link #vortexDamageFor}；
 * 该底数在<b>发射时</b>再乘上<b>模块化魔杖的法术增幅</b>，见 {@link #fireVortex}）。
 * <p>
 * <b>服务端权威</b>：客户端只发"选了哪个个体"的请求（带个体 uid），
 * 释放/收回/成败判定全在服务端；服务端决定后<b>发回执</b>（{@code PacketSpiritState}）+ 聊天提示，
 * 客户端 UI 只照着回执显示 ⇒ 不会出现"UI 说在场上、场上其实没有"的幽灵态。
 */
public final class CursedSpiritTechnique extends BaseTechnique {

    public static final CursedSpiritTechnique INSTANCE = new CursedSpiritTechnique();

    private static final String KEY_SPIRITS = "tinkersnewlife.cursed_spirits";

    /** 模式：0=释放/收回 GUI；1=献祭蓄力 GUI */
    public static final int MODE_RELEASE = 0;
    public static final int MODE_SACRIFICE = 1;

    /**
     * 漩涡蓄力中：玩家 UUID → 漩涡伤害<b>底数</b>。
     * <p>献祭那一刻按"该个体生命上限 × {@link #VORTEX_HP_RATIO}"算好并<b>冻结</b>（发射时不再重算血量上限）；
     * 模块化魔杖的法术增幅<b>不在这一步</b>结算，而是发射时由 {@link #fireVortex} 在这份底数上乘一次 ⇒
     * 蓄力期间换手/换杖，伤害按<b>发射时</b>手持的杖算（与"献祭时不算杖"两条互不重复 ✓）。
     */
    private static final Map<UUID, Float> VORTEX_CHARGE = new ConcurrentHashMap<>();

    /**
     * 黑色漩涡的伤害系数：<b>底数伤害 = 该咒灵的最大生命上限 × 此系数</b>。
     *
     * <p>该底数只含"献祭/施放的那只个体生命上限"这一项（{@link #vortexDamageFor}）；
     * 系数 {@code 0.4} <b>沿用旧公式</b>里那一项
     * {@code 献祭个体生命上限 × 0.4}（旧整条公式
     * {@code round((1 + 亲和/100) × (输出×6 + 生命上限×0.4 + 其攻击×6))} 已不再使用）。
     * <p>用户口径（本轮）：这个底数<b>要吃模块化魔杖的法术增幅</b>（"可以吃增幅"）⇒ 发射时乘一次，
     * 见 {@link #fireVortex}；亲和 / 输出 / 该个体攻击力那三项<b>仍然不参与</b> ✓。
     * <p><b>想调强弱改这一行即可</b>（例如 0.5 ⇒ 更疼、0.3 ⇒ 更轻）；只影响漩涡，别的招式都不碰 ✓。
     * <p>对照（<b>无增幅</b>时）：僵尸 20 血 ⇒ 8；凋灵 300 血 ⇒ 120；普通 Boss 级亡灵 150 血 ⇒ 60。
     */
    public static final float VORTEX_HP_RATIO = 0.4F;

    /**
     * 快照里<b>取不到属性</b>时的兜底生命上限（= 原版普通生物 20 血 ⇒ 伤害 8）。
     * <p>兜底只在"旧存档里没有 {@code Attributes} 的快照 / 属性列表被别的 mod 改烂"时才用得上，
     * 属于最后一道保险；正常路径一律以快照或记录里的真实最大生命为准 ✓。
     */
    private static final float VORTEX_HP_FALLBACK = 20.0F;

    /**
     * 快照里"离线太久就拒载"的存档键（铁魔法 Boss 用它标记"上次存档时的世界时间"）。
     * 释放时会被消毒掉，见 {@link #sanitizeSnapshot}。
     */
    private static final String KEY_SNAPSHOT_TIME_GATE = "unloadedGametime";

    /** "强制清理幽灵记录"的确认窗口（tick）：60 秒内再点一次即认定玩家确认 */
    private static final int GHOST_CONFIRM_TICKS = 1200;

    /** 待确认的幽灵清理（玩家 UUID → 上次点击的个体 + 时刻） */
    private static final Map<UUID, GhostConfirm> GHOST_CONFIRM = new ConcurrentHashMap<>();

    /**
     * 个体 uid → <b>连续</b>"在已加载区块里找不到"的扫描次数。
     * 自愈扫描要连查 ≥3 次（约 3 秒）才敢断言"它确实已被移出世界" ——
     * 单次查不到可能只是实体刚加入/区块状态切换的瞬时现象，误判会把活着的仆从变成"收不回的野怪"✗。
     */
    private static final Map<String, Integer> GHOST_MISSES = new ConcurrentHashMap<>();

    private static final class GhostConfirm {
        final String uid;
        final long tick;

        GhostConfirm(String uid, long tick) {
            this.uid = uid;
            this.tick = tick;
        }
    }

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
        /**
         * 释放体<b>最后待过的维度</b> + <b>最后已知坐标</b>（{@code hasReleasedPos=false} 表示"还没记过"）。
         *
         * <p>为什么需要：单靠"在已加载区块里找不到"<b>无法区分</b>下面两件事 ✗：
         * ① 它已被静默移出世界（例如铁魔法 Boss 自己 {@code remove()}，没有死亡事件）→ 该清幽灵记录 ✓；
         * ② 它只是所在区块没加载（离得远 / 在别的维度）→ 清记录会把一只活着的仆从变成"永远收不回的野怪" ✗✗。
         * 有了"最后已知位置"，就能用 {@code hasChunkAt(该位置)} 判断"刚才那一带明明是加载的、
         * 却找不到它" ⇒ 才能<b>断言</b>它真的没了（见 {@link #certainGone}）。
         */
        public String releasedDim = "";
        public double releasedX;
        public double releasedY;
        public double releasedZ;
        public boolean hasReleasedPos = false;

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
            t.putString("releasedDim", releasedDim == null ? "" : releasedDim);
            t.putBoolean("hasReleasedPos", hasReleasedPos);
            if (hasReleasedPos) {
                t.putDouble("releasedX", releasedX);
                t.putDouble("releasedY", releasedY);
                t.putDouble("releasedZ", releasedZ);
            }
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
            e.releasedDim = t.getString("releasedDim");
            e.hasReleasedPos = t.getBoolean("hasReleasedPos");
            e.releasedX = t.getDouble("releasedX");
            e.releasedY = t.getDouble("releasedY");
            e.releasedZ = t.getDouble("releasedZ");
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
        // ⭐ 收服判定：原本只收 MobType.UNDEAD；戴「双向认知阻碍面具」者把**除玩家以外**的一切视为亡灵
        //    → 于是任何非玩家生物都能被咒灵操术回收 ✓（见 CognitiveMaskItem）
        if (target != null && com.mofengbaizhi.tinkersnewlife.content.item.CognitiveMaskItem
                .treatedAsUndead(player, target) && target.isAlive()
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
        // ⭐ 收服 = 由这位玩家击杀：先把归属记下来。
        //    下面三下里，**真正打死大多数 Boss 的是第三下**（①② 的 1e9 会被 Goety 使徒那类
        //    "单次限伤/免疫窗"吃掉），而 ② magic() 与 ③ kill()(=genericKill) **都没有攻击者** ✗ ⇒
        //    死亡事件里抓不到人 ⇒ 无为转变的形态记录写不进去 ✗
        //    （用户实测："杀死诡厄巫法使徒没有记录"——他走的正是"收服"这条路）。
        com.mofengbaizhi.tinkersnewlife.content.curse.KillAttribution.remember(target, player);
        com.mofengbaizhi.tinkersnewlife.content.curse.CurseDeath.mark(target);
        target.hurt(player.damageSources().playerAttack(player), 1.0E9F);
        if (target.isAlive()) {
            target.invulnerableTime = 0;
            com.mofengbaizhi.tinkersnewlife.content.curse.CurseDeath.mark(target);
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

    /**
     * GUI 选择（顺转：释放/收回；反转：献祭蓄力）。
     *
     * <p>⭐ <b>行身份用 uid，不用下标</b>：客户端那份列表是"打开 GUI 那一刻"的服务端快照，
     * 而玩家在远程服务器上点一下要经过一个来回 —— 这期间列表完全可能变（某个体战死 → 记录被删、
     * 主人又收服了一只）。以前只发下标，于是"点的是甲、服务端操作的是乙"✗。
     * 现在带上 uid 优先匹配；uid 匹配不上（旧客户端/空 uid）才退回下标。
     */
    public static void selectRow(ServerPlayer player, int mode, int row, String uid) {
        List<SpiritEntry> list = entries(player);
        SpiritEntry entry = null;
        if (uid != null && !uid.isEmpty()) {
            for (SpiritEntry e : list) {
                if (e.uid != null && uid.equals(e.uid.toString())) {
                    entry = e;
                    break;
                }
            }
        }
        if (entry == null) {
            if (row < 0 || row >= list.size()) return;
            entry = list.get(row);
        }
        if (mode == MODE_RELEASE) {
            toggleRelease(player, entry);
        } else {
            sacrifice(player, entry);
        }
    }

    /** 兼容旧签名（不带 uid）：退化为按下标选择 */
    public static void selectRow(ServerPlayer player, int mode, int row) {
        selectRow(player, mode, row, "");
    }

    /**
     * 顺转点击某个体：<b>记录说"在场上" ⇒ 这次点击的意图就是"收回"</b>；
     * 记录说"没放出" ⇒ 意图是"释放"。
     *
     * <p>⚠ 意图必须由<b>记录</b>决定，不能由"找不找得到实体"决定 ——
     * 以前是"找不到实体就当没释放、直接再放一只"，于是幽灵态（记录说在场上、实体其实没了）
     * 会被判成"释放"，玩家点多少次都是"已释放"、永远收不回来 ✗（用户实测：
     * "显示已释放、UI 也说在场上、但并没有出现，而且收不回来"）。
     */
    private static void toggleRelease(ServerPlayer player, SpiritEntry entry) {
        // ⭐ "场上有没有这个释放体"看 releasedId 与 guardUuid：
        //    守护体跨登出/区块重载、或主人把它无为转变之后，releasedId 可能已经失效（甚至 -1），
        //    只剩 guardUuid 还能认亲。
        Mob live = resolveLive(player, entry);
        if (live != null) {
            // 收回（保留记录）：静默移除（含召唤物清理），见 recallReleased
            recallReleased(live);
            clearFieldLink(entry);
            updateEntry(player, entry);
            GHOST_CONFIRM.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.recall", entry.name), true);
            sendState(player);
            return;
        }
        if (isOnField(entry)) {
            // 记录说"在场上"，但任何已加载区块里都找不到 →
            // 先判"它到底还在不在"，绝不无脑清标记 + 再放一只（那正是幽灵态的成因）
            if (certainGone(player, entry)) {
                ghostClear(player, entry, "message.tinkersnewlife.spirit.ghost_cleared");
            } else {
                ghostConfirm(player, entry);
            }
            return;
        }
        release(player, entry);
    }

    /** 记录是否宣称"这个个体正在场上"（守护体只认 guardUuid 也能认亲） */
    private static boolean isOnField(SpiritEntry e) {
        if (e.releasedId >= 0) return true;
        return e.guardUuid != null && !e.guardUuid.isEmpty();
    }

    /**
     * 清掉"场上释放体"的全部链接（记录本身保留）。
     * <p>⭐ 连 {@code releasedUuid} 一起清：它只是"重登后按 UUID 重链"的线索（见 {@link #normalize}），
     * 收回之后场上已经没有这具实体了，留着只会让 `resolveLive` 每次多查一遍、
     * 也会让"到底算不算在场上"的判定出现两套口径。
     */
    private static void clearFieldLink(SpiritEntry e) {
        e.releasedId = -1;
        e.releasedUuid = "";
        e.guardUuid = "";
        e.hasReleasedPos = false;
        e.releasedDim = "";
    }

    /**
     * <b>能不能断言"它已经被移出世界了"</b>？
     *
     * <p>只有"它最后待过的那片区块<b>现在是加载的</b>、却全服（所有维度）都找不到它"才算数 ✓；
     * 区块没加载（离得远 / 在别的维度）时<b>一律不下结论</b> ✗ ——
     * 否则会把一只活着的仆从当成幽灵清掉，它就成了永远收不回的野怪。
     */
    private static boolean certainGone(ServerPlayer owner, SpiritEntry e) {
        if (!e.hasReleasedPos || e.releasedDim == null || e.releasedDim.isEmpty()) return false;
        ServerLevel lvl = levelOf(owner, e.releasedDim);
        if (lvl == null) return false;
        return lvl.hasChunkAt(net.minecraft.core.BlockPos.containing(e.releasedX, e.releasedY, e.releasedZ));
    }

    /** 按维度 id 字符串找服务端世界（找不到返回 null） */
    private static ServerLevel levelOf(ServerPlayer owner, String dim) {
        if (dim == null || dim.isEmpty()) return null;
        for (ServerLevel lvl : owner.serverLevel().getServer().getAllLevels()) {
            if (lvl.dimension().location().toString().equals(dim)) return lvl;
        }
        return null;
    }

    /**
     * 幽灵记录清理：记录宣称"在场上"、实体却<b>确证</b>已不在世界 → 清字段（记录保留，可再次释放）。
     * 会显示消息 + 发回执。
     */
    private static void ghostClear(ServerPlayer player, SpiritEntry entry, String messageKey) {
        clearFieldLink(entry);
        updateEntry(player, entry);
        GHOST_CONFIRM.remove(player.getUUID());
        GHOST_MISSES.remove(entry.uid == null ? "" : entry.uid.toString());
        player.displayClientMessage(Component.translatable(messageKey, entry.name), true);
        TinkersNewlife.LOGGER.info("[咒灵操术] 清理幽灵记录：{}（{}）—— 记录保留，可再次释放",
                entry.name, entry.type);
        sendState(player);
    }

    /**
     * "找不到实体、又不敢断言它没了"时的回执：**不清任何标记**，如实告诉玩家为什么收不回来，
     * 并给一条"确认它真的没了 → 再次点击强制清理"的后路（否则玩家会卡在永远收不回来的死局里 ✗）。
     */
    private static void ghostConfirm(ServerPlayer player, SpiritEntry entry) {
        String uid = entry.uid == null ? "" : entry.uid.toString();
        long now = player.serverLevel().getGameTime();
        GhostConfirm pending = GHOST_CONFIRM.get(player.getUUID());
        if (pending != null && pending.uid.equals(uid) && now >= pending.tick
                && now - pending.tick <= GHOST_CONFIRM_TICKS) {
            GHOST_CONFIRM.remove(player.getUUID());
            ghostClear(player, entry, "message.tinkersnewlife.spirit.ghost_forced");
            return;
        }
        GHOST_CONFIRM.put(player.getUUID(), new GhostConfirm(uid, now));
        player.displayClientMessage(
                Component.translatable("message.tinkersnewlife.spirit.recall_unloaded", entry.name), true);
    }

    /**
     * 释放满血个体（服务端权威）。
     *
     * <p>⭐ <b>必须校验生成结果</b>：以前 {@code addFreshEntity} 的返回值被丢掉、也不检查实体是否
     * 已被移除，于是"生成失败"照样写 releasedId、照样提示"已释放" ⇒
     * UI 说在场上、场上什么都没有、还收不回来（用户实测的幽灵态）✗。
     * 现在：生成失败 → <b>不写任何释放标记</b>（记录原样）→ 回执失败原因 ✓。
     */
    private static boolean release(ServerPlayer player, SpiritEntry entry) {
        EntityType<?> type = EntityType.byString(entry.type).orElse(null);
        if (type == null) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.invalid"), true);
            return false;
        }
        ServerLevel level = player.serverLevel();
        Entity spawned = type.create(level);
        if (!(spawned instanceof LivingEntity living)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.invalid"), true);
            return false;
        }
        living.load(sanitizeSnapshot(entry.nbt));
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
        boolean added = level.addFreshEntity(living);
        // ⭐ 成败判定：addFreshEntity 是布尔 + 实体可能在自己 load() 里就把自己移除了
        //    （典型：铁魔法 Boss 的"离线太久拒载"闸门，见 sanitizeSnapshot）
        if (!added || living.isRemoved() || !living.isAlive()) {
            if (added) {
                living.discard();
            }
            TinkersNewlife.LOGGER.warn("[咒灵操术] 释放失败：{}（{}）addFreshEntity={} removed={} alive={} —— "
                            + "记录保持未释放；快照顶层键={}",
                    entry.name, entry.type, added, living.isRemoved(), living.isAlive(),
                    entry.nbt == null ? "null" : entry.nbt.getAllKeys());
            player.displayClientMessage(
                    Component.translatable("message.tinkersnewlife.spirit.release_failed", entry.name), true);
            return false;
        }
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
        // 记下"最后已知位置/维度"：自愈扫描靠它区分"被静默移除"与"只是区块没加载"
        entry.releasedDim = level.dimension().location().toString();
        entry.releasedX = living.getX();
        entry.releasedY = living.getY();
        entry.releasedZ = living.getZ();
        entry.hasReleasedPos = true;
        updateEntry(player, entry);
        GHOST_CONFIRM.remove(player.getUUID());
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.released", entry.name), true);
        sendState(player);
        return true;
    }

    /**
     * 快照"消毒"：把**不该跟着个体一起复活的存档字段**去掉。
     *
     * <p>⭐ 本次幽灵态的<b>真凶</b>就在这里：铁魔法 {@code FireBossEntity} 覆写了
     * {@code load(CompoundTag)}，里面有一道"离线太久就拒载"的闸门 ——
     * 快照里带着捕获那一刻的 {@code unloadedGametime}，只要"捕获 → 释放"相隔超过
     * 6000 tick（5 分钟世界时间），{@code load()} 会<b>当场把自己 {@code remove(DISCARDED)} 掉</b>
     * 并提前 return（日志：{@code Refusing to load ... elapsed time ... greater than limit}）。
     * 于是：{@code addFreshEntity} 返回 false（实体已被标记移除）→ 场上什么都没有，
     * 但我们照样写了 releasedId、照样说"已释放" ✗。
     * 去掉这个键 ⇒ 闸门不生效、正常读档 ✓（该键的语义是"区块卸载后过了多久"，
     * 对"从快照重新生成"这件事本来就不成立）。
     */
    private static CompoundTag sanitizeSnapshot(CompoundTag snapshot) {
        CompoundTag nbt = snapshot == null ? new CompoundTag() : snapshot.copy();
        // 位置/身份：由释放现场重新决定（否则会"落回捕获点"或 UUID 撞车）
        nbt.remove("UUID");
        nbt.remove("Pos");
        nbt.remove("Dimension");
        nbt.remove("Motion");
        nbt.remove("WorldUUIDMost");
        nbt.remove("WorldUUIDLeast");
        // ⭐ 存档时间闸门（铁魔法 Boss）：见方法注释
        nbt.remove(KEY_SNAPSHOT_TIME_GATE);
        return nbt;
    }

    private static void sacrifice(ServerPlayer player, SpiritEntry entry) {
        // 献祭：清除记录并进入漩涡蓄力
        removeEntry(player, entry.uid);
        // ⭐ 漩涡伤害**底数** = 该个体（被献祭的这只式神）的**最大生命上限** × VORTEX_HP_RATIO（见 vortexDamageFor）
        //    用户口径："根据咒灵血量上限动态变化" + "可以吃增幅" ⇒ 亲和/输出/个体攻击力那三项一律不加回来 ✓，
        //    模块化魔杖的法术增幅则在**发射时**乘一次（见 fireVortex 的 ⭐ 注释）✓
        VORTEX_CHARGE.put(player.getUUID(), vortexDamageFor(entry));
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.vortex_charge"), true);
        sendState(player);
    }

    /**
     * 计算漩涡伤害<b>底数</b>：<b>该咒灵的最大生命上限 × {@link #VORTEX_HP_RATIO}</b>。
     *
     * <p>⚠ 这里返回的是<b>底数</b>（不含模块化魔杖增幅）：增幅在发射时由 {@link #fireVortex}
     * 乘在这份底数之上 —— 全流程<b>只乘这一次</b>，献祭这一步不碰杖 ✓。
     *
     * <p><b>取值来源（按优先级）</b>：
     * <ol>
     *   <li>该个体快照 NBT 的 {@code Attributes} 列表里 {@code minecraft:generic.max_health} 的
     *       {@code Base} —— 这正是"释放（顺转）时读档复活的同一份数据"，也就是玩家在场上真正会看到的
     *       那只式神的血量上限；换算后的伤害 ≈ 它落地后的血量上限 × 系数 ✓；</li>
     *   <li>取不到（旧存档快照没属性 / 键名非 {@code Base}）⇒ 退回记录里的
     *       {@link SpiritEntry#maxHp}（收服那一刻 {@code target.getMaxHealth()} 的快照，
     *       无为转变改写形态时也会同步）；</li>
     *   <li>连记录也没有（{@code ≤ 0}）⇒ 最终兜底 {@link #VORTEX_HP_FALLBACK}（20）⇒ 伤害 8，
     *       保证永远不会算出 0 伤害或负数 ✓。</li>
     * </ol>
     * <p>为什么不读"场上活着的实体"：献祭/蓄力这一步玩家是在 <b>GUI 里选一只未释放的个体</b>，
     * 它此刻<b>并不在场</b>（没有实体可读）—— 来源只能是快照/记录 ✓。
     */
    private static float vortexDamageFor(SpiritEntry entry) {
        float maxHp = maxHealthFromSnapshot(entry == null ? null : entry.nbt);
        if (maxHp <= 0.0F && entry != null) {
            maxHp = entry.maxHp; // 兜底①：收服时记下的最大生命（含无为转变后的新形态）
        }
        if (maxHp <= 0.0F) {
            maxHp = VORTEX_HP_FALLBACK; // 兜底②：最后一道保险
        }
        return Math.max(1.0F, maxHp * VORTEX_HP_RATIO);
    }

    /**
     * 从实体快照 NBT 里读"最大生命上限"（属性列表里的 {@code minecraft:generic.max_health} 基数）。
     *
     * <p>与 {@code LivingEntity#load} 读属性的结构一致：
     * {@code Attributes = [{Name:"minecraft:generic.max_health", Base:20.0d}, …]}（1.20.1 的键就是 {@code Base}）。
     * <p>返回 {@code ≤ 0} 表示"这份快照里读不到" ⇒ 调用方再去走兜底 ✓（不抛异常、不改任何东西）。
     */
    private static float maxHealthFromSnapshot(CompoundTag snapshot) {
        if (snapshot == null || !snapshot.contains("Attributes", Tag.TAG_LIST)) return 0.0F;
        ListTag attrs = snapshot.getList("Attributes", Tag.TAG_COMPOUND);
        for (int i = 0; i < attrs.size(); i++) {
            CompoundTag a = attrs.getCompound(i);
            if (!"minecraft:generic.max_health".equals(a.getString("Name"))) continue;
            double base = a.contains("Base", Tag.TAG_ANY_NUMERIC)
                    ? a.getDouble("Base")
                    : a.getDouble("base"); // 极老存档里见过小写写法，顺手兼容（拿不到就是 0）
            if (base > 0.0D) return (float) base;
        }
        return 0.0F;
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
        // ⭐ 漩涡伤害 = **底数**（献祭那一刻按"被献祭的那只咒灵的最大生命上限 × VORTEX_HP_RATIO"算好，
        //    见 vortexDamageFor）→ **发射时再吃一次模块化魔杖的法术增幅**（用户口径："可以吃增幅" ✓）。
        //    亲和 / 输出 / 该个体攻击力**不参与** ✓（只加增幅这一项）。
        // ⚠ 只乘一次：`VORTEX_CHARGE` 里存的就是"未增幅的底数"（sacrifice() 不碰杖），这里乘完直接交给
        //    实体，`SpiritVortexEntity.explode()` 用 `caster.damageSources().mobAttack(caster)` 结算 ——
        //    其 msgId 为 `mob`，落不进 `ModularStaffModifier.onLivingHurt` 的 `isSpellDamage` 白名单
        //    （退一步说那也要求 source.getEntity() 是 Player），所以**全局事件不会再来一遍** ⇒ 不重复乘 ✓。
        //    `getSpellAmplification` 的语义是"返回增幅后的伤害值"（= 原伤害 ×(1+倍率) + 附加），
        //    不是倍率 ⇒ 直接赋值，切勿再 `damage *=` 或调用第二次 ✓。
        damage = ModularStaffModifier.getSpellAmplification(player, damage);
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
            // 献祭只能选未释放个体（守护体 releasedId 可能失效，但 guardUuid 仍算"在场上"）
            list.removeIf(CursedSpiritTechnique::isOnField);
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

    /**
     * 服务端 → 客户端回执：把"每个个体的释放状态"（按 uid）同步给该玩家。
     * <p>⭐ 服务端权威：客户端那份列表只是快照，任何"释放/收回/幽灵清理/战死删记录"
     * 由服务端决定后都回执一次 ⇒ 若玩家此刻正开着列表 GUI，它按回执更新（不会停在成功态）✓。
     */
    private static void sendState(ServerPlayer player) {
        List<SpiritEntry> list = entries(player);
        List<String> uids = new ArrayList<>();
        List<Boolean> released = new ArrayList<>();
        for (SpiritEntry e : list) {
            uids.add(e.uid == null ? "" : e.uid.toString());
            released.add(e.releasedId >= 0);
        }
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketSpiritState(uids, released));
    }

    /** 登出/死亡清理：撤销场上普通释放体（保留记录，召唤物一并清除），清除蓄力；
     *  守护形态（无为转变·守护）随从保留在场（由无为守护系统持久），仅解除释放位链接。 */
    public static void cleanup(ServerPlayer player) {
        VORTEX_CHARGE.remove(player.getUUID());
        GHOST_CONFIRM.remove(player.getUUID());
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
                // 已经亲手移除 ⇒ 链接全清（releasedUuid 留着只会让重登时白查一遍）
                clearFieldLink(e);
                continue;
            }
            // 没找到（多半只是区块没加载）：只复位 id，保留 releasedUuid 供重登后重链
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
                    recallReleased(live);          // 静默移除（含召唤物清理）
                }
                clearFieldLink(e);
                saveAll(owner, list);
                owner.displayClientMessage(Component.translatable("message.tinkersnewlife.spirit.recall", e.name), true);
                sendState(owner);
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
        sendState(owner);
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
                clearFieldLink(e);
                saveAll(owner, list);
                sendState(owner);
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
                // 最后已知位置：自愈扫描靠它区分"被静默移除"与"只是区块没加载"
                e.releasedDim = newForm.level().dimension().location().toString();
                e.releasedX = newForm.getX();
                e.releasedY = newForm.getY();
                e.releasedZ = newForm.getZ();
                e.hasReleasedPos = true;
                saveAll(owner, list);
                sendState(owner);
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
                    e.releasedDim = guard.level().dimension().location().toString();
                    e.releasedX = guard.getX();
                    e.releasedY = guard.getY();
                    e.releasedZ = guard.getZ();
                    e.hasReleasedPos = true;
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
     *
     * <p>⭐ 为什么是**带时效的窗口**（UUID → 标记时刻），而不是"补刀循环期间的一个集合"：
     * 收回确实走死亡链路，但<b>死亡不一定在同一 tick 内发生</b> ✗ ——
     * Boss（诡厄巫法的使徒/亚波伦这类有死亡动画、阶段转换甚至"击败后变形态"的）
     * 常常是"血打到 0 之后隔几 tick 才真正 {@code die()}"，甚至先复活/变身再死；
     * 那段延迟里标记早已被摘掉 ⇒
     * ① {@link #onMinionDeath} 把记录当"战死"删掉 ✗；
     * ② 掉落/经验抑制（{@link #isRecalling}）也放行 ⇒ **主动收回反而掉一地战利品** ✗
     * （用户实测："本人放出再收回导致使徒死亡掉落物品并删除记录"）。
     * 现在改成"标记后 {@value #RECALL_GRACE_TICKS} tick 内都算收回中" ✓ ——
     * 这期间它无论何时、以何种方式真正死掉，都按"收回"结算（不掉落、不删记录 ✓）。
     */
    private static final int RECALL_GRACE_TICKS = 200;   // 10 秒（快路径：短动画够用）
    private static final java.util.Map<java.util.UUID, Long> RECALLING =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 实体 ForgeData 键：这具实体是被"主动收回"的（跨 tick 有效，直到它真正消失 ✓） */
    private static final String KEY_RECALLING = "tinkersnewlife.recalling";

    /** 打上"正在收回"标记（带时刻，{@value #RECALL_GRACE_TICKS} tick 内有效） */
    private static void markRecalling(net.minecraft.world.entity.Entity e) {
        if (e == null) return;
        long now = e.level().getGameTime();
        if (RECALLING.size() > 64) {
            RECALLING.entrySet().removeIf(en -> now - en.getValue() > RECALL_GRACE_TICKS);
        }
        RECALLING.put(e.getUUID(), now);
        // ⭐ 同时打**实体持久标记**：有的 Boss 死亡动画比时间窗更长（用户实测："原初受火者收服后，
        //    每次收回，死亡动画结束后都会掉落物品" ✗）—— 光靠计时永远可能被更长的动画拖出去 ✗，
        //    所以只要这具实体还没消失就一直算"收回中" ✓（实体死了标记随它一起没 ✓，无需清理 ✓）。
        e.getPersistentData().putBoolean(KEY_RECALLING, true);
    }

    /** 该实体当前是否正在被"主动收回"（掉落/经验抑制、记录保留都要用） */
    public static boolean isRecalling(net.minecraft.world.entity.Entity e) {
        if (e == null) return false;
        // ⭐ 持久标记优先：死亡动画可能比时间窗长得多，只要实体还在、标记就还在 ⇒ 无论多久都兜得住 ✓
        if (e.getPersistentData().getBoolean(KEY_RECALLING)) return true;
        Long marked = RECALLING.get(e.getUUID());
        if (marked == null) return false;
        long now = e.level().getGameTime();
        // now < marked：换维度/存档重载导致 gameTime 回退 → 当过期处理，免得一直"收回中" ✗
        if (now < marked || now - marked > RECALL_GRACE_TICKS) {
            RECALLING.remove(e.getUUID(), marked);
            return false;
        }
        return true;
    }

    /**
     * 顺手调用 <b>akaishi</b> 的 {@code WardenBossHandler.removeBar(Warden)}。
     *
     * <p>该 mod 会给**每一只**监守者挂一个紫色 BossBar（{@code Map<Entity, ServerBossEvent>}），
     * 它的清理由它自己的死亡/消亡逻辑触发；我们主动收回时（尤其是走 {@code discard()} 兜底那条路）
     * 它可能收不到信号，血条就留在屏幕上。它这个方法是 {@code private static}、类名固定（未混淆），
     * 所以用反射直接调一次；**类不存在/方法改了都只是吞掉**，不影响收回本身。
     */
    private static void removeAkaishiWardenBar(net.minecraft.world.entity.monster.warden.Warden warden) {
        try {
            Class<?> handler = Class.forName("com.example.akaishi.forge.life.WardenBossHandler");
            java.lang.reflect.Method remove =
                    handler.getDeclaredMethod("removeBar", net.minecraft.world.entity.monster.warden.Warden.class);
            remove.setAccessible(true);
            remove.invoke(null, warden);
        } catch (Throwable ignored) {
            // 没装 akaishi / 它改了实现：都无所谓
        }
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
        // ⭐ ② 反查"实体自己持有的 ServerBossEvent 字段"（含子类，例如铁魔法的 ExtendedServerBossEvent ✗）：
        //    这类 Boss 把血条当字段挂在实体上，而且**只在 die() 里清**（很可能没覆写 remove() ✗）——
        //    我们改成静默移除后不会触发 die() ⇒ 血条会一直留在屏幕上 ✗（用户实测提问："那 boss 的战斗条会消失吗"）。
        //    反射直接 removeAllPlayers() + setVisible(false) ⇒ 不依赖它的 UUID、也不依赖它覆写哪个方法 ✓。
        //    失败一律吞掉（血条残留顶多是观感问题，绝不能影响收回 ✓）。
        for (Class<?> c = entity.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (!net.minecraft.server.level.ServerBossEvent.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    if (f.get(entity) instanceof net.minecraft.server.level.ServerBossEvent bar) {
                        bar.removeAllPlayers();
                        bar.setVisible(false);
                    }
                } catch (Throwable ignored) {
                    // 字段拿不到/类型不对 → 跳过 ✓
                }
            }
        }
    }

    /**
     * 收回一个释放体（保留记录）。
     *
     * <p>⭐ <b>现在改为"静默移除"（{@code discard()}），不再走死亡链路</b> ——
     * 走死亡链路时，被收回的 Boss 会照常跑完它<b>自己的死亡逻辑</b>（死亡动画 + 战利品表），
     * 于是"主动收回反而掉一地战利品" ✗：使徒（记录被当战死删掉 + 掉物品）、
     * 原初受火者（死亡动画结束后掉"奥术源质、钥匙之类"）两次都是这个原因 ✓（用户实测）。
     * 而这些掉落有的**根本不经过** {@code LivingDropsEvent}（是那个 mod 自己在死亡动画里 spawn 的 ✗），
     * 所以"标记 + 取消掉落"永远拦不全 ✗。收回需要的清理
     * （{@code dismissServantsOf} / {@code clearEntityBossBar} / akaishi 监守者血条）
     * 本来就已单独显式调用 ✓，实体追踪解除由 {@code discard()} 自己完成 ✓
     * ⇒ 直接静默移除最干净：无死亡事件 ⇒ 无掉落、无经验、无动画、无"被诅咒致死"文案 ✓。
     *
     * <p>下面那段"为什么走原版死亡链路"的旧说明**已作废**，保留仅作历史记录 ✗。
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

        TinkersNewlife.LOGGER.info("[收回] 开始：{}（{}，血量={}/{}）",
                mob.getName().getString(), net.minecraft.world.entity.EntityType.getKey(mob.getType()),
                mob.getHealth(), mob.getMaxHealth());
        clearEntityBossBar(mob);
        if (mob instanceof net.minecraft.world.entity.monster.warden.Warden warden) {
            com.mofengbaizhi.tinkersnewlife.network.curse.PacketDropWardenBars.broadcast();
            removeAkaishiWardenBar(warden);
        }
        // 收回 ≠ 击杀：清掉归属记忆（否则 20 秒内被记过归属的话，收回会被算成那位玩家的击杀 ✗），
        // 并打上"收回中"标记（兜底：万一有 mod 在实体被移除时补一道死亡，记录保留与掉落抑制仍生效 ✓）。
        com.mofengbaizhi.tinkersnewlife.content.curse.KillAttribution.forget(mob);
        markRecalling(mob);
        mob.setSilent(true);
        // ⭐ 静默移除：**不走死亡链路** ⇒ 没有死亡事件、没有死亡动画、没有战利品/经验 ✓
        //    （为什么不再补刀：被收回的 Boss 会照常跑完它自己的死亡逻辑，
        //      "奥术源质、钥匙之类"就是这么掉出来的 ✗；见方法注释）
        mob.discard();
        TinkersNewlife.LOGGER.info("[收回] 结束：{} 已静默移除（不走死亡链路 ⇒ 不掉落/不记形态 ✓）",
                mob.getName().getString());
    }

    /** 释放体战死 → 记录从 GUI 消失，其召唤物一并清除（玩家本人死亡绝不算释放体战死） */
    public static void onMinionDeath(Entity dead) {
        if (dead.level().isClientSide) return;
        if (dead instanceof net.minecraft.world.entity.player.Player) return; // 玩家不是释放体
        if (!(dead.level() instanceof ServerLevel sl)) return;
        dismissServantsOf(dead);
        clearEntityBossBar(dead);
        if (dead instanceof net.minecraft.world.entity.monster.warden.Warden warden) {
            com.mofengbaizhi.tinkersnewlife.network.curse.PacketDropWardenBars.broadcast();
            removeAkaishiWardenBar(warden);
        }
        // ⭐ 主动收回（recallReleased）也会走到这里：保留记录，只清召唤物
        if (isRecalling(dead)) return;
        for (ServerPlayer p : sl.getServer().getPlayerList().getPlayers()) {
            List<SpiritEntry> list = entries(p);
            SpiritEntry hit = findEntryFor(p, dead); boolean removed = hit != null && list.removeIf(x -> x.uid != null && x.uid.equals(hit.uid));
            if (removed) {
                saveAll(p, list);
                sendState(p);   // 记录没了 ⇒ 回执（正开着 GUI 的话那一行会消失，不会停在"在场上"）
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

    /**
     * 反射移除该 Mob 的所有目标选择目标（目标统一由操控 tick 指派）。
     *
     * <p>本模组召唤的"仆从"统一走这条路：<b>只摘目标选择，不碰 goalSelector</b> ——
     * 生物自带的攻击/施法/动画 AI 原样保留，目标是"谁"由外面每 tick 指派。
     * 对比 {@code WuWeiHandler#attachGuardAi}（那套会连 goalSelector 一起清空，改成玉犬式近战追击，
     * 非玉犬形态的生物用它等于把原生 AI 全废掉）。
     */
    public static void stripTargetGoals(Mob mob) {
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

        /** 自愈扫描间隔（tick）：1 秒一次 */
        private static final int SWEEP_INTERVAL = 20;

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            net.minecraft.server.MinecraftServer server = event.getServer();
            if (server == null) return;
            boolean sweepTick = server.getTickCount() % SWEEP_INTERVAL == 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                // ⭐ 先自愈（记录 ↔ 现实对齐），再操控；否则操控会被"幽灵记录"整段跳过
                if (sweepTick) {
                    sweep(player);
                }
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
                        // ⭐ 凋灵：两侧头跟随主头打同一个目标
                        //    （原版侧头不走 goalSelector，stripTargetGoals 管不到它 ✗）
                        com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler
                                .syncWitherSideHeads(minion, want);
                        continue;
                    }
                    // 无指令：清掉指向主人/同队的目标（无视施术者）
                    LivingEntity cur = minion.getTarget();
                    if (cur == null || PuppetUtil.isAllyOf(cur, player)) {
                        minion.setTarget(null);
                        // 凋灵：没目标时两侧头彻底哑火（否则它们会随机抓附近活体 —— 主人也在池子里 ✗）
                        com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler
                                .syncWitherSideHeads(minion, null);
                    } else {
                        // 已有合法目标：侧头跟着打它
                        com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler
                                .syncWitherSideHeads(minion, cur);
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

        /**
         * 自愈扫描（每 {@value #SWEEP_INTERVAL} tick）：让"记录"始终跟得上"现实"。
         *
         * <p>为什么必须有：实体可能<b>没有死亡事件</b>地离开世界 ——
         * 别的 mod 自己 `remove()`（典型：铁魔法 Boss 离线太久拒载 / 停战太久自行消散）、
         * 换维度、区块卸载、主人登出等。以前记录只在"死亡事件"里更新 ✗，于是记录能永远停在
         * "已在场上"，而场上早就空了 ⇒ UI 骗人 + 收不回来（用户实测）。
         *
         * <ul>
         *   <li>找得到实体、但记录里的 id/uuid/位置对不上（登出、换维度、区块重载、静默换 id）
         *       → <b>重新链接 + 刷新最后已知位置</b> ✓；</li>
         *   <li>找不到、且它最后待过的区块<b>现在是加载的</b>（{@link #certainGone}）→ 断言它已被移出世界
         *       → 清幽灵标记 + 通知 + 回执 ✓。</li>
         * </ul>
         * 判定不成立（区块没加载）时<b>什么也不做</b>：宁可让记录多留一会儿，也不能把活着的仆从
         * 变成"永远收不回的野怪" ✗。
         */
        private static void sweep(ServerPlayer player) {
            List<SpiritEntry> list = entries(player);
            boolean changed = false;
            List<SpiritEntry> ghosted = new ArrayList<>();
            for (SpiritEntry e : list) {
                if (!isOnField(e)) continue;
                String uid = e.uid == null ? "" : e.uid.toString();
                Mob live = resolveLive(player, e);
                if (live != null) {
                    GHOST_MISSES.remove(uid);
                    if (e.releasedId != live.getId()) {
                        e.releasedId = live.getId();
                        changed = true;
                    }
                    String uuid = live.getStringUUID();
                    if (e.releasedUuid == null || !e.releasedUuid.equals(uuid)) {
                        e.releasedUuid = uuid;
                        changed = true;
                    }
                    if (e.guard && (e.guardUuid == null || !e.guardUuid.equals(uuid))) {
                        e.guardUuid = uuid;
                        changed = true;
                    }
                    // 位置只在"首次"或"移动超过 32 格"时落盘：玩家持久数据里存着完整快照 NBT，
                    // 不能每 tick 重写一遍（见 SpiritEntry#hasReleasedPos）
                    String dim = live.level().dimension().location().toString();
                    if (!e.hasReleasedPos || !dim.equals(e.releasedDim)
                            || live.distanceToSqr(e.releasedX, e.releasedY, e.releasedZ) > 32.0 * 32.0) {
                        e.releasedDim = dim;
                        e.releasedX = live.getX();
                        e.releasedY = live.getY();
                        e.releasedZ = live.getZ();
                        e.hasReleasedPos = true;
                        changed = true;
                    }
                } else if ((e.guardUuid == null || e.guardUuid.isEmpty()) && certainGone(player, e)) {
                    // ⭐ 只自动清理"普通释放体"：守护随从本来就以 guardUuid 身份在场（跨登出/区块重载是常态），
                    //    自动清它的链接收益很小、误判代价却大（会把守在原地的守护体变成"收不回的野怪"）✗
                    //    ⇒ 守护体的幽灵态交给玩家点击时的判定处理（见 toggleRelease）。
                    // 另外：必须"连续 3 次"都查不到才动手，单次查不到可能只是瞬时现象（见 GHOST_MISSES）
                    if (GHOST_MISSES.merge(uid, 1, Integer::sum) >= 3) {
                        ghosted.add(e);
                    }
                } else {
                    GHOST_MISSES.remove(uid);
                }
            }
            if (!ghosted.isEmpty()) {
                for (SpiritEntry e : ghosted) {
                    GHOST_MISSES.remove(e.uid == null ? "" : e.uid.toString());
                    clearFieldLink(e);
                    changed = true;
                    player.displayClientMessage(
                            Component.translatable("message.tinkersnewlife.spirit.ghost_cleared", e.name), true);
                    TinkersNewlife.LOGGER.info("[咒灵操术] 自愈：{}（{}）已不在世界（最后所在区块是加载的却找不到）"
                            + " → 幽灵记录已清理（记录保留，可再次释放）", e.name, e.type);
                }
                GHOST_CONFIRM.remove(player.getUUID());
            }
            if (changed) {
                saveAll(player, list);
                sendState(player);
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
