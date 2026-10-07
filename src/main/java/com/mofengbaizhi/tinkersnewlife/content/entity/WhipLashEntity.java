package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.handler.SupervisorHandler;
import com.mofengbaizhi.tinkersnewlife.content.item.WhipItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * <b>鞭击</b>（§1053）—— 一次"<b>左键抽击</b>"的载体 ✓
 * （纯逻辑 ＋ 视觉 ✓ 不移动、不碰撞、不存档 ✓）。
 *
 * <h2>时间轴完全照参照模组 BetterWhips 的 {@code ArmMotor}（MIT ✓）</h2>
 * <ul>
 *   <li><b>左键 PRECISION</b> ✓：{@code windup = clamp(min(3, period−2),1,3)} ✓、
 *       {@code stroke = clamp(period−windup−1,1,4)} ✓（{@code period = ceil(攻击冷却)} ✓）
 *       ⇒ 进度 0→0.30 落在起手段 ✓、0.30→1.0 落在抽击段 ✓；
 *       <b>驱动一结束（{@code driveTick ≥ windup+stroke}）根部立刻回到手上</b> ✓
 *       ⇒ 此后绳子<b>自由飞</b> ✓（这是"甩出去"的关键 ✓，也是它注释里 82~190 格/秒的来源 ✓）；</li>
 *   <li><b>伤害窗口</b>：{@code ageTicks > windup && ≤ windup + 10} ✓ ⇒ <b>飞行段照样打人</b> ✓；</li>
 *   <li><b>伤害口径</b>（照它 ✓）：{@code floor(段速度/10) × 0.2} ✓，
 *       并按本鞭已命中目标数<b>逐次减半</b> ✓（{@code base / 2^prior} ✓）。</li>
 * </ul>
 *
 * <p>⚠ <b>§1118k 已删除</b> ✗：原「<b>右键蓄力 → 松手砸地</b>」那一整套（绕手自转 ✓ 钟摆下抽 ✓ 冲击波 ✓）。
 * 原因 ✓：自 §1058 起**右键已改成**「收回鞭身 ＋ 举械格挡」（见 {@code WhipItem#use} ✓）
 * ⇒ 那套逻辑**零调用点** ✗（用户口径：「我不是把右键改成格挡了吗，砸地的代码还留着？」✓）。
 */
public class WhipLashEntity extends Entity {

    public static final int PHASE_LASH = 0;
    /** §1058 收回段 ✓：右键"收回没有收回的鞭身" ✓ —— 绳子被拉回手心后散场 ✓ */
    public static final int PHASE_RETRACT = 3;

    private static final EntityDataAccessor<String> OWNER_UUID =
            SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> SWING_SIGN =
            SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.BOOLEAN);
    /** §1058 收回段已经走了多少 tick ✓ */
    private static final EntityDataAccessor<Integer> RETRACT_TICK =
            SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.INT);

    /**
     * ⭐ §1118d <b>弓弦部件的材质 VariantId</b> ✓ —— 用户口径：
     * 「长鞭抽击时的鞭身实体应当为**鞭子弓弦部件的材料色**」✓
     *
     * <p>做法照 {@code YoYoEntity.BOWSTRING_VARIANT} ✓（本仓既有的同一需求 ✓）：
     * 刷出鞭身时把弓弦材质 id **同步**给实体 ✓ ⇒ 由 {@code WhipLashRenderer} 在客户端解析成颜色 ✓
     * （那边走 {@code MaterialTooltipCache.getColor} ✓；{@code WizardArmorColors} 也证明这条链可用 ✓）。
     * <p>⚠ 空串 ＝ 拿不到 ✓（无弓弦部件／未知材料／模组没装 ✓）⇒ 渲染器退回原来的皮革米白 ✓。
     */
    private static final EntityDataAccessor<String> BOWSTRING_VARIANT =
            SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.STRING);

    /**
     * 弓弦部件在长鞭定义里的下标 ✓ ＝ <b>2</b> ✓
     * （{@code data/tinkersnewlife/tinkering/tool_definitions/whip.json} 的
     * {@code parts = [tconstruct:tough_handle, tconstruct:large_plate, tconstruct:bowstring]} ✓）
     */
    private static final int BOWSTRING_PART_INDEX = 2;

    /**
     * ⭐ §1118g <b>复用鞭身时的"重挥代次"</b> ✓ —— 用户口径：
     * 「**上一条鞭子未消失时不要创建新鞭子，而是驱动未消失的鞭子继续挥鞭**」✓
     *
     * <p>⚠ 为什么需要它 ✗：时间轴 {@code age} 是**本地字段** ✓（客户端只在 owner 就绪后才推进 ✓ 见 {@code tick()} ✓），
     * 而"重新驱动"是**服务端**决定的 ✓ ⇒ 必须让客户端也把 {@code age} 归零 ✗，否则两边时间轴错位 ✗
     * （客户端会以为驱动早就结束了 ⇒ 看不见这次挥鞭 ✓）。
     * ⇒ 做法：服务端 {@code +1} ✓，客户端发现值变了就归零 ✓（一次挥鞭只发一个字段更新 ✓ 很便宜 ✓）。
     */
    private static final EntityDataAccessor<Integer> LASH_GEN =
            SynchedEntityData.defineId(WhipLashEntity.class, EntityDataSerializers.INT);

    /** 客户端上次见到的代次 ✓（用来发现"服务端又驱动了一次"✓） */
    private int seenLashGen = 0;

    /**
     * 左键：伤害窗口 ＝ **起手段之后多少 tick 还能命中** ✓。
     *
     * <p>⚠ §1118e <b>用户口径「手感很差」的**结构性原因**就在这里</b> ✗：
     * 驱动段总长 ＝ {@link #WINDUP_TICKS}（3 ✓）＋ {@link #STROKE_TICKS}（4 ✓）＝ <b>7 tick</b> ✓，
     * 而这里原来是 <b>14</b> ✗ ⇒ 鞭子**视觉上早收杆了**，判定却一路生效到第 <b>17</b> tick ✗
     * ⇒ 手上就是「**抽完了还在命中／延迟判定**」✓（正是"手感差"最典型的来源 ✓）。
     * <p>⇒ 改成 <b>8</b> ✓：覆盖抽击段（3~7 ✓）＋ 出鞭后**4 tick 的余势**（鞭梢那声"脆响"本来就在这之后 ✓）
     * ⇒ 有鞭感、但不拖泥带水 ✓；再往后绳子自由飞（{@code LASH_FREE_FLIGHT_TICKS} ✓）纯粹是视觉 ✓。
     */
    private static final int LEFT_DAMAGE_WINDOW_TICKS = 8;
    /** 抽击驱动结束后，绳子还要自由飞这么多 tick ✓ 让波传完 ✓（照它实体活 32 tick 的量级 ✓） */
    private static final int LASH_FREE_FLIGHT_TICKS = 32;
    /** §1058 收回段持续多少 tick ✓（鞭身回到手里就散场 ✓） */
    private static final int RETRACT_TICKS = 8;
    /** §1058 每个玩家"当前那一条鞭" ✓ —— 右键要能找到它才能把鞭身收回来 ✓ */
    private static final Map<UUID, WhipLashEntity> ACTIVE_LASHES = new HashMap<>();
    /**
     * §1057 每个玩家"<b>下一次允许抽击</b>"的 tick ✓ ——
     * 间隔 ＝ {@link WhipItem#attackPeriodTicks} ✓ ⇒ <b>攻速属性只决定每秒能抽几次</b> ✓
     * （用户口径：「<b>攻速只影响冷却</b>」✓ —— 不影响力度/射程 ✓，驱动长度恒为 3＋4 ✓）；
     * 同时兼作"挥击包与命中包同 tick 都来、只甩一次"的闸门 ✓。
     */
    private static final Map<UUID, Integer> NEXT_LASH_TICK = new HashMap<>();

    private final WhipPhysics physics = new WhipPhysics();
    private final WhipPhysics.Drive drive = new WhipPhysics.Drive();
    /** 本鞭已结算过的目标 ⇒ 伤害按 2^prior 递减 ✓（照它的 {@code WhipMultiHitDamage} ✓） */
    private final Set<UUID> contactedTargets = new HashSet<>();
    /**
     * §1057 驱动长度<b>固定</b> ✓（照参照的 {@code ArmMotor}：起手 3 ＋ 抽击 4 ＝ 7 tick ✓）
     * —— <b>与攻速无关</b> ✓。
     * <p>用户口径（2026-10-05）：「<b>让攻速只影响冷却速度吧，别影响力度了</b>」✓
     * ⇒ 手永远以同样的速度划完同样的弧 ⇒ 射程与力度恒定 ✓；
     * 攻速只通过 {@link WhipItem#attackPeriodTicks}（冷却）决定"每秒能抽几次" ✓。
     */
    private static final int WINDUP_TICKS = 3;
    private static final int STROKE_TICKS = 4;
    private int windupTicks = WINDUP_TICKS;
    private int strokeTicks = STROKE_TICKS;
    /**
     * §1056 <b>本实体"真正开始模拟"的年龄</b> ✓ —— 只在 owner 解析成功后才 +1 ✓。
     * <p>⚠ 为什么不能用 {@code tickCount} ✗：客户端实体要靠**同步过来的 owner uuid** 才能模拟 ✓，
     * uuid 还没到的前 1~2 tick 我原来直接 {@code return} ✗（不模拟，但 {@code tickCount} 照样在涨 ✗）
     * ⇒ <b>短驱动会被整段吃掉</b> ✗：攻速快的鞭子驱动只有 2 tick ✗ ⇒ 客户端一帧都没画 ⇒
     * 用户实测「<b>攻速快的甩不出去，攻速慢的还能甩 5 格</b>」✓（慢的 7 tick、吃掉 1~2 tick 还剩 5 ✓）。
     */
    private int age;

    public WhipLashEntity(EntityType<? extends WhipLashEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    // ==================== 生成入口（服务端调用 ✓） ====================

    /** 冷却是否已好 ✓ —— 攻速越快 ⇒ 间隔越短 ⇒ 抽得越频繁 ✓（与力度/射程无关 ✓） */
    public static boolean isLashReady(Player player) {
        Integer next = NEXT_LASH_TICK.get(player.getUUID());
        return next == null || player.tickCount >= next;
    }

    /** 左键：一次抽击 ✓（<b>冷却</b>按攻速算 ✓；抽击动作本身恒为 3＋4 tick ✓） */
    public static void startLash(Player player) {
        if (!isLashReady(player)) {
            return;
        }
        NEXT_LASH_TICK.put(player.getUUID(), player.tickCount + WhipItem.attackPeriodTicks(player));
        // 照参照 beginPrecisionNow ✓：重置攻击冷却 ⇒ 准星上的攻击指示器与鞭子冷却同步 ✓
        player.resetAttackStrengthTicker();
        spawn(player, PHASE_LASH);
    }

    /** §1058 <b>不受抽击冷却限制</b>地挥一鞭 ✓ —— 完美格挡/反射时用 ✓（视觉与判定都要立刻出现 ✓） */
    public static void startLashNow(Player player) {
        spawn(player, PHASE_LASH);
    }

    private static void spawn(Player player, int phase) {
        if (phase == PHASE_LASH) {
            // ⭐ §1118g 用户口径：「**上一条鞭子未消失时不要创建新鞭子，而是驱动未消失的鞭子继续挥鞭**」✓
            //   ⇒ 有还活着的鞭身就**不新建** ✗：直接把它再驱动一次 ✓
            //   ⚠ 关键好处：`physics.reset(...)` 只在**私有构造函数**里 ✓（见下面 ✓）
            //     ⇒ 复用实体时**绳形与动量全都保留** ✓ ⇒ 它是"从当前位置再被甩一次" ✓ 正是要的效果 ✓
            //   ⚠ 已失效的旧记录要顺手清掉 ✗（它可能被 tickLifetime 自行 discard 了 ✓）
            WhipLashEntity existing = ACTIVE_LASHES.get(player.getUUID());
            if (existing != null) {
                if (existing.isAlive() && !existing.isRemoved()) {
                    existing.setBowstringVariant(bowstringVariantOf(player));   // 中途换过弓弦材料也跟着更新 ✓
                    existing.restartLash(player);                              // §1118h 顺带按当前所在侧决定方向 ✓
                    return;
                }
                ACTIVE_LASHES.remove(player.getUUID());
            }
        }
        WhipLashEntity lash = new WhipLashEntity(player.level(), player, phase);
        // §1118d 把"弓弦部件的材料色来源"同步给鞭身 ✓（渲染器据此给整条鞭身上色 ✓ 用户口径 ✓）
        lash.setBowstringVariant(bowstringVariantOf(player));
        player.level().addFreshEntity(lash);
        if (phase == PHASE_LASH) {
            ACTIVE_LASHES.put(player.getUUID(), lash);
        }
    }

    /**
     * ⭐ §1118g <b>复用未消失的鞭身：再挥一次</b> ✓（用户口径 ✓）。
     *
     * <p>只做两件事 ✓：①时间轴 {@code age} 归零（重新走起手＋抽击 ✓）；
     * ②清掉本轮命中记录（允许这一鞭重新打人 ✓）。
     * <p>⚠ 刻意**不碰**物理 ✗：绳形/动量保留 ⇒ 表现为"鞭子还在空中，又被甩了一鞭" ✓✓
     * （若这里 reset 物理 ⇒ 鞭子会瞬间跳回手上 ✗ 那是另一个观感 ✗）。
     * <p>⚠ 服务端还要把代次 +1 ✓ 通知客户端一起归零 ✓（见 {@link #LASH_GEN} ✓）。
     */
    public void restartLash(Player owner) {
        // ⭐ §1118h 每一鞭都按"鞭子当前在左还是在右"重新决定方向 ✓（用户口径 ✓）
        updateSwingSignFromSide(owner);
        this.age = 0;
        this.contactedTargets.clear();
        if (!this.level().isClientSide) {
            this.setLashGen(this.getLashGen() + 1);
        }
    }

    /**
     * ⭐ §1118h <b>按"鞭子当前在哪一侧"决定这一鞭往哪边挥</b> ✓ —— 用户口径：
     * 「鞭子不应只朝一个方向挥动：当它驱动时**在左侧应该向右挥动，在右侧则向左挥动**」✓
     *
     * <p>⚠ 为什么必须重算 ✗：`swingSign` 原来是在**私有构造函数**里随机定的 ✓（`:313` ✓）
     * —— 以前每鞭都是**新实体**（所以方向会变 ✓），而 §1118g 改成**复用同一条鞭身**之后 ✗
     * ⇒ 方向就**永远固定**了 ✗✓（正是用户看到的"只朝一个方向挥"✓）。
     *
     * <p>判据 ✓：取绳身各点相对玩家"右方向"的**平均横向偏移 `lat`** ✓（用整条绳，比只看梢端稳 ✓）。
     * <ul>
     *   <li>`lat &lt; 0` ⇒ 鞭子在**左**侧 ⇒ 这一鞭要**向右**挥 ✓；（`lat &gt; 0` 反之 ✓）</li>
     *   <li>⚠ 为什么乘 `side` ✗：手弧的横向偏移是 `side · sign · 0.16` ✓
     *       （见 {@code WhipPhysics#precisionHandAnchor} 的注释 ✓ —— "侧 0→side·sign·0.16" ✓）
     *       ⇒ 要让**起手落在鞭子当前那一侧、再横扫到另一侧** ✓，就得让 `sign(lat · side)` 决定 sign ✓
     *       ⇒ 否则左手持鞭时会**反着挥**（越挥越远 ✗）。</li>
     * </ul>
     */
    private void updateSwingSignFromSide(Player owner) {
        try {
            if (owner == null || physics == null || !physics.isStarted()) return;
            Vec3 aim = owner.getViewVector(1.0F);
            Vec3 forward = new Vec3(aim.x, 0.0D, aim.z);
            forward = forward.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : forward.normalize();
            Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
            Vec3 eye = owner.getEyePosition();
            double lat = 0.0D;
            int n = 0;
            for (int i = 1; i < WhipPhysics.POINTS; i++) {
                Vec3 p = physics.point(i);
                if (p == null) continue;
                lat += p.subtract(eye).dot(right);
                n++;
            }
            if (n == 0) return;
            lat /= n;
            this.setSwingSignPositive(lat * side(owner) >= 0.0D);
        } catch (Throwable ignored) {
            // 拿不到就沿用原方向 ✓ 绝不影响挥鞭本身 ✓
        }
    }

    /**
     * §1118d 取玩家手里那把长鞭的**弓弦部件**材质 VariantId ✓
     * （空串 ＝ 拿不到 ✓ 渲染器会用默认的皮革米白 ✓）。
     * <p>⚠ 完全照 {@code YoYoItem#getBowstringVariantId} 的写法 ✓（同一套匠魂 API ✓ 已在本仓跑通 ✓）：
     * {@code ToolStack.from(stack).getMaterials().get(2)} ⇒ {@code variant.getVariant().toString()} ✓。
     */
    private static String bowstringVariantOf(Player player) {
        try {
            for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
                net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
                if (stack.isEmpty() || !(stack.getItem() instanceof WhipItem)) continue;
                slimeknights.tconstruct.library.tools.nbt.ToolStack tool =
                        slimeknights.tconstruct.library.tools.nbt.ToolStack.from(stack);
                if (tool == null) continue;
                slimeknights.tconstruct.library.tools.nbt.MaterialNBT materials = tool.getMaterials();
                if (materials == null || materials.size() <= BOWSTRING_PART_INDEX) return "";
                slimeknights.tconstruct.library.materials.definition.MaterialVariant variant =
                        materials.get(BOWSTRING_PART_INDEX);
                if (variant == null || variant.isUnknown()) return "";
                slimeknights.tconstruct.library.materials.definition.MaterialVariantId id = variant.getVariant();
                return id == null ? "" : id.toString();
            }
        } catch (Throwable ignored) {
            // 匠魂没加载 / 结构不符 ⇒ 空串 ⇒ 默认色 ✓ 绝不影响鞭子本身 ✓
        }
        return "";
    }

    /**
     * §1058 用户口径：右键「<b>收回没有收回的鞭身</b>」✓ ——
     * 把玩家当前那条还在飞／还在抽的鞭切成<b>收回段</b> ✓，绳身会被拉回手心 ✓（{@code MODE_RETRACT} ✓）。
     */
    public static void retract(Player player) {
        WhipLashEntity lash = ACTIVE_LASHES.get(player.getUUID());
        if (lash == null || !lash.isAlive()) {
            ACTIVE_LASHES.remove(player.getUUID());
            return;
        }
        lash.setPhase(PHASE_RETRACT);
        lash.setRetractTick(0);
    }

    // ⚠ §1118k 这里原本有 startCharge / releaseCharge / cancelCharge（右键蓄力 → 松手砸地 ✓）
    //   —— 自 §1058 起右键已改成「收回鞭身 ＋ 举械格挡」（见 WhipItem#use ✓）⇒ 那三个方法**零调用点** ✗
    //   ⇒ 整套蓄力/砸地已在 §1118k 删除 ✓（用户口径：「我不是把右键改成格挡了吗，砸地的代码还留着？」✓）

    private WhipLashEntity(Level level, Player owner, int phase) {
        this(ModEntities.WHIP_LASH.get(), level);
        this.setPos(owner.getX(), owner.getY(), owner.getZ());
        this.setOwnerUuid(owner.getUUID().toString());
        this.setPhase(phase);
        this.setSwingSignPositive(owner.getRandom().nextBoolean());
        Vec3 aim = owner.getViewVector(1.0F);
        Vec3 forward = new Vec3(aim.x, 0.0D, aim.z);
        forward = forward.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : forward.normalize();
        this.physics.reset(WhipPhysics.handBase(owner.position(), owner.getEyeHeight(),
                side(owner), forward), aim);
    }

    // ==================== 同步字段 ====================

    @Override
    protected void defineSynchedData() {
        this.getEntityData().define(OWNER_UUID, "");
        this.getEntityData().define(PHASE, PHASE_LASH);
        this.getEntityData().define(SWING_SIGN, true);
        this.getEntityData().define(RETRACT_TICK, -1);
        this.getEntityData().define(BOWSTRING_VARIANT, "");
        this.getEntityData().define(LASH_GEN, 0);
    }

    public String getOwnerUuid() { return this.getEntityData().get(OWNER_UUID); }
    public void setOwnerUuid(String v) { this.getEntityData().set(OWNER_UUID, v); }
    /** §1118g 重挥代次（服务端 +1 ⇒ 客户端把时间轴归零 ✓） */
    public int getLashGen() { return this.getEntityData().get(LASH_GEN); }
    public void setLashGen(int v) { this.getEntityData().set(LASH_GEN, v); }
    /** §1118d 弓弦材质 id（空串 ＝ 拿不到 ⇒ 渲染器用默认色 ✓） */
    public String getBowstringVariant() { return this.getEntityData().get(BOWSTRING_VARIANT); }
    public void setBowstringVariant(String v) {
        this.getEntityData().set(BOWSTRING_VARIANT, v == null ? "" : v);
    }
    public int getPhase() { return this.getEntityData().get(PHASE); }
    public void setPhase(int v) { this.getEntityData().set(PHASE, v); }
    public boolean isSwingSignPositive() { return this.getEntityData().get(SWING_SIGN); }
    public void setSwingSignPositive(boolean v) { this.getEntityData().set(SWING_SIGN, v); }
    public int getRetractTick() { return this.getEntityData().get(RETRACT_TICK); }
    public void setRetractTick(int v) { this.getEntityData().set(RETRACT_TICK, v); }

    public WhipPhysics physics() {
        return physics;
    }

    private static double side(LivingEntity owner) {
        return owner.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? -1.0D : 1.0D;
    }

    // ==================== 主循环 ====================

    @Override
    public void tick() {
        super.tick();

        Player owner = resolveOwner();
        if (owner == null || !owner.isAlive()) {
            if (!this.level().isClientSide) {
                this.discard();
            }
            // ⚠ 客户端这里**不能**推进 age ✓ ⇒ 时间轴会等 owner 就绪之后才开始 ✓
            // 否则攻速快的短驱动会在客户端被整段吃掉 ✗（§1056 的 bug ✓）
            return;
        }
        // §1118g 复用鞭身：服务端又驱动了一次（代次变化）⇒ 客户端时间轴与命中记录一起归零 ✓ 两边对齐 ✓
        if (this.seenLashGen != getLashGen()) {
            this.seenLashGen = getLashGen();
            this.age = 0;
            this.contactedTargets.clear();
        }
        this.age++;

        Vec3 aim = owner.getViewVector(1.0F);
        Vec3 forward = new Vec3(aim.x, 0.0D, aim.z);
        forward = forward.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : forward.normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        double side = side(owner);
        Vec3 base = WhipPhysics.handBase(owner.position(), owner.getEyeHeight(), side, forward);

        int phase = getPhase();
        boolean server = !this.level().isClientSide;

        // ⚠ §1118k 这里原本还有 PHASE_CHARGE（绕手自转）与 PHASE_RELEASE（松手砸地）两个分支 ✓
        //   自 §1058 起右键已改成「收回 ＋ 格挡」⇒ 那两段**不可达** ✗ ⇒ 已随蓄力/砸地整套删除 ✓
        if (phase == PHASE_RETRACT) {
            // §1058 收回：根部回到手上 ✓ 且每个点被拉向手心 ✓（WhipPhysics.MODE_RETRACT ✓）
            int retract = Math.max(0, getRetractTick());
            if (server) {
                setRetractTick(retract + 1);
            }
            drive.mode = WhipPhysics.MODE_RETRACT;
            drive.aim = aim.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : aim.normalize();
            drive.rootFrom = base;
            drive.rootTo = base;
            drive.progressFrom = 0.0D;
            drive.progressTo = 0.0D;
        } else {
            // §1055 攻速决定"挥动速度"的方式 = 参照的原公式 ✓（⚠ §1054 我改错了方向 ✗）：
            //   攻速<b>快</b> ⇒ 周期短 ⇒ 起手/抽击更短 ⇒ 抽得又快又脆 ✓；
            //   攻速<b>慢</b> ⇒ 周期长，但<b>抽击段被硬封在 4 tick</b> ✓ ⇒ 手依旧很快、鞭子照样甩得远 ✓
            //   ⇒ 慢攻速只体现在<b>冷却更长</b>（每秒能抽的次数更少 ✓）。
            // ⚠ 关键教训：驱动 tick 数一旦被拉长 ✗，手在同一段弧上就更慢 ✗ ⇒ 绳子甩不出去 ✗
            //   （用户原话：「这样改了之后鞭子挥不远了」✓ 就是这个原因 ✓）。
            // §1057 攻速**只**影响冷却 ✓ —— 驱动长度恒定 ✓
            //（手始终以同样速度划完同样的 1.1 格弧 ✓ ⇒ 射程/力度不再随攻速变化 ✓）
            windupTicks = WINDUP_TICKS;
            strokeTicks = STROKE_TICKS;
            int total = windupTicks + strokeTicks;
            int driveTick = this.age - 1;
            boolean driving = driveTick >= 0 && driveTick < total;
            double progressFrom;
            double progressTo;
            if (driveTick < 0) {
                progressFrom = 0.0D;
                progressTo = 0.0D;
            } else if (driveTick < windupTicks) {
                progressFrom = WhipPhysics.PRECISION_RELEASE_RAW
                        * Mth.clamp(driveTick / (double) windupTicks, 0.0D, 1.0D);
                progressTo = WhipPhysics.PRECISION_RELEASE_RAW
                        * Mth.clamp((driveTick + 1.0D) / windupTicks, 0.0D, 1.0D);
            } else {
                double lashTick = driveTick - windupTicks;
                progressFrom = Mth.lerp(Mth.clamp(lashTick / (double) strokeTicks, 0.0D, 1.0D),
                        WhipPhysics.PRECISION_RELEASE_RAW, 1.0D);
                progressTo = Mth.lerp(Mth.clamp((lashTick + 1.0D) / strokeTicks, 0.0D, 1.0D),
                        WhipPhysics.PRECISION_RELEASE_RAW, 1.0D);
            }
            drive.mode = WhipPhysics.MODE_LASH;
            drive.eye = owner.getEyePosition();
            drive.aim = aim.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : aim.normalize();
            // ⚠ §1118k：drive.forward / right / side 已随蓄力/砸地删除 ✗（只剩抽击真正用到的字段 ✓）
            drive.swingSign = isSwingSignPositive() ? 1.0D : -1.0D;
            if (driving) {
                drive.rootFrom = driveTick <= 0 ? base
                        : WhipPhysics.precisionHandAnchor(base, drive.aim, right, progressFrom, drive.swingSign);
                drive.rootTo = WhipPhysics.precisionHandAnchor(base, drive.aim, right, progressTo, drive.swingSign);
                drive.progressFrom = progressFrom;
                drive.progressTo = progressTo;
            } else {
                // 驱动结束 ⇒ 根部回到手上 ✓ 绳子自由飞 ✓（导引同时关闭：进度归 0 ✓）
                drive.rootFrom = base;
                drive.rootTo = base;
                drive.progressFrom = 0.0D;
                drive.progressTo = 0.0D;
            }
        }

        physics.markTickStart();
        physics.step(this.level(), drive);

        if (server) {
            if (phase == PHASE_LASH) {
                tickLashDamage(owner);
            }
            tickLifetime(phase);
        } else {
            tickClientFx();
        }
    }

    /** 左键：逐段扫掠结算 ✓（照它的 damageForSpeed ＋ 减半 ✓） */
    private void tickLashDamage(Player owner) {
        if (this.age <= windupTicks || this.age > windupTicks + LEFT_DAMAGE_WINDOW_TICKS) {
            return;
        }
        AABB search = owner.getBoundingBox().inflate(WhipPhysics.totalLength() + 2.0D);
        List<LivingEntity> targets = this.level().getEntitiesOfClass(LivingEntity.class, search,
                e -> isValidTarget(owner, e));
        if (targets.isEmpty()) {
            return;
        }
        // §1054 用户口径：伤害由【攻击力属性】决定 ✓（基准面板 3.5 ⇒ 与原参照口径一致 ✓）
        float panel = WhipItem.attackPanel(owner.getMainHandItem());
        for (int seg = 0; seg < WhipPhysics.SEGMENTS; seg++) {
            double speed = physics.segmentSpeed(seg);
            float base = WhipItem.damageForSpeed(speed, panel);
            if (base <= 0.0F) {
                continue;
            }
            for (LivingEntity target : targets) {
                if (contactedTargets.contains(target.getUUID())) {
                    continue;
                }
                Vec3 contact = physics.segmentContact(seg, target.getBoundingBox());
                if (contact == null) {
                    continue;
                }
                contactedTargets.add(target.getUUID());
                if (SupervisorHandler.hasSupervisor(owner)) {
                    // §1068 监工（用户口径 ✓）：抽击伤害【降为 0】✓ 改为"管理" ——
                    //   抽自己人（宠物／仆从／同心戒同伴）⇒ 力量 ＋ 速度 逐次叠到 5 级 ✓；
                    //   抽村民 ⇒ 5% 概率半量补货 ✓（每村民每天最多 3 次 ✓）。
                    //   ⚠ 这里【完全不造成伤害】✓ 也【不叠 §1064 鞭痕】✗ ——
                    //   打的都是自家牲口 ✓ 再给它们减速减攻就荒唐了 ✗。
                    SupervisorHandler.onWhipTouch(owner, target);
                    this.level().playSound(null, contact.x, contact.y, contact.z,
                            SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.45F,
                            1.32F + this.random.nextFloat() * 0.08F);
                } else {
                    float damage = (float) (base / Math.pow(2.0D, contactedTargets.size()));
                    // ⭐ §1118j 新效果（用户口径 ✓）：
                    //   「当实体身上有**鞭痕效果**时，下一次被鞭子抽中的伤害将提升**每级 10%**」✓
                    //   口径：**不消耗**鞭痕 ✗ —— 同一鞭紧接着会再叠一层（§1064 ✓），若这里消费掉
                    //   就与"每次命中 +1 层"互相抵消 ⇒ 层数永远长不起来 ✗（会破坏鞭痕本身 ✓）。
                    //   ⇒ 现实现 ＝ 只要目标带着鞭痕，这一鞭就按层数加成 ✓（8 层 ＝ ×1.8 ✓ 越抽越疼 ✓）。
                    net.minecraft.world.effect.MobEffectInstance whipMark =
                            target.getEffect(ModEffects.WHIP_WEAKEN.get());
                    if (whipMark != null) {
                        int markLevel = Math.min(whipMark.getAmplifier() + 1,
                                com.mofengbaizhi.tinkersnewlife.content.effect.WhipWeakenEffect.MAX_STACKS);
                        damage *= 1.0F + (float) com.mofengbaizhi.tinkersnewlife.content.effect
                                .WhipWeakenEffect.BONUS_PER_STACK * markLevel;
                    }
                    // ⭐ §1118o 弓弦系远程加成（匠魂 tconstruct:power/punch ＋ 原版 力量/冲击/火矢 ✓）
                    //   用户口径：「弓弦可以附加远程特性，但是我鞭子抽打吃不到原版的远程效果」✓
                    //   ⚠ 这些词条/附魔的原有实现只作用于弹射物（ProjectileHook ✗）⇒ 抽击得自己补等效效果 ✓
                    net.minecraft.world.item.ItemStack whipStack = owner.getMainHandItem();
                    damage *= WhipItem.rangedDamageMultiplier(whipStack);
                    if (hurt(owner, target, contact, damage)) {
                        // §1064 用户口径：被鞭子抽中 ⇒ 叠一层"鞭痕"（每层 −10% 速度与攻击 ✓ 最多 8 层 ＝ −80% ✓）
                        ModEffects.applyWhipWeaken(target);
                        // §1118o 冲击（原版 Punch ✓ / 匠魂 punch ✓）：沿"我 → 目标"方向把目标推出去 ✓
                        double whipKnockback = WhipItem.rangedKnockback(whipStack);
                        if (whipKnockback > 0.0D) {
                            Vec3 pushDir = target.position().subtract(owner.position());
                            pushDir = pushDir.lengthSqr() < 1.0E-6D
                                    ? owner.getViewVector(1.0F) : pushDir.normalize();
                            target.push(pushDir.x * whipKnockback, 0.18D * whipKnockback,
                                    pushDir.z * whipKnockback);
                            target.hurtMarked = true;
                        }
                        // §1118o 火矢（原版 Flame ✓）：点燃 5 秒 ✓（匠魂 fiery 近战本来就生效 ✓ 不重复 ✓）
                        int whipIgnite = WhipItem.rangedIgniteSeconds(whipStack);
                        if (whipIgnite > 0) {
                            target.setSecondsOnFire(whipIgnite);
                        }
                        this.level().playSound(null, contact.x, contact.y, contact.z,
                                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.85F,
                                0.96F + this.random.nextFloat() * 0.08F);
                    }
                }
            }
        }
    }

    private boolean hurt(Player owner, LivingEntity target, Vec3 at, float amount) {
        if (amount <= 0.0F) {
            return false;
        }
        // ⚠ §1118l 防御（**不是我们的 bug ✗**，但必须兜住 ✓）：
        //   用户实测崩溃 ✓ `crash-2026-10-07_13.06.22-server.txt`：
        //     ReportedException: Ticking entity
        //       Caused by: LinkageError: loader constraint violation（log4j 的 MessageSupplier ✗）
        //                  at EventBus.handleException ← Forge 在记录"某个事件处理器抛的异常"时炸的
        //                  at ForgeHooks.onLivingAttack ← 攻击事件链
        //                  at TargetDummyEntity.m_6469_ ← 「试验假人」
        //                  at WhipLashEntity.hurt ← 我们只是**发起攻击的入口** ✓
        //   ⇒ 根因是**别的模组的 LivingAttackEvent 处理器抛异常** ✗ ＋ 整合包里 **log4j 类加载冲突** ✗
        //     （异常被 LinkageError 顶掉 ⇒ 服务端 tick 直接崩 ✗）。
        //   ⇒ 我们这边兜一层 ✓：伤害调用**任何** Throwable（含 LinkageError ✓）都只记一条日志、
        //     当作"这次没打中"处理 ✓ ⇒ **不再把游戏拖崩** ✓（代价：那一下不结算伤害 ✓ 明显好过崩 ✗）。
        boolean damaged;
        try {
            damaged = target.hurt(this.damageSources().playerAttack(owner), amount);
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "[鞭子] 结算伤害时被外部异常打断（已在 §1118l 兜住 ✓ 不再崩游戏 ✗）：{}", t.toString());
            return false;
        }
        if (!damaged) {
            return false;
        }
        target.invulnerableTime = 0;
        if (this.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.14D, 0.14D, 0.14D, 0.1D);
        }
        return true;
    }

    /** 客户端：梢部拖粒子 ✓ */
    private void tickClientFx() {
        if (this.tickCount % 2 != 0) {
            return;
        }
        Vec3 tip = physics.point(WhipPhysics.POINTS - 1);
        this.level().addParticle(ParticleTypes.CRIT, tip.x, tip.y, tip.z, 0.0D, 0.0D, 0.0D);
    }

    /** 生命周期 ✓（左键：起手＋抽击＋自由飞 ✓；砸地：钟摆＋自由飞 ✓；蓄力：松手或超时 ✓） */
    private void tickLifetime(int phase) {
        if (phase == PHASE_RETRACT) {
            if (getRetractTick() > RETRACT_TICKS) {
                this.discard();
            }
            return;
        }
        if (this.age > windupTicks + strokeTicks + LASH_FREE_FLIGHT_TICKS || this.tickCount > 400) {
            this.discard();
        }
    }

    private boolean isValidTarget(Player owner, LivingEntity candidate) {
        if (candidate == owner || !candidate.isAlive() || candidate.isSpectator()) {
            return false;
        }
        if (candidate instanceof Player other) {
            return !other.isAlliedTo(owner);
        }
        if (candidate instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame()) {
            return tame.getOwnerUUID() == null || !tame.getOwnerUUID().equals(owner.getUUID());
        }
        return true;
    }

    private Player resolveOwner() {
        try {
            UUID uuid = UUID.fromString(getOwnerUuid());
            return this.level().getPlayerByUUID(uuid);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ==================== 杂项 ====================

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(WhipPhysics.totalLength() + 2.0D);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 256.0D * 256.0D;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
