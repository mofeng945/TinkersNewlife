package com.mofengbaizhi.tinkersnewlife.content;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 墨封白织fufu 的方块实体（§880）：一开始不存数据 ✓ 只是给"玩家模型渲染器"当载体 ✓。
 *
 * <p>§908 起多了一件事：<b>抚摸时的挤压动画</b> ——
 * 结构照诡厄巫法的玩偶（{@code PlushieBlockEntity}，已反编译核对 ✓ **只学结构** ✓）：
 * 只存两个 tick 计数 ＋ 一个开关 ✓，渲染器拿 {@link #getAnimation(float)} 去压扁 ✓。
 *
 * <p>§1079 又多了<b>皮肤</b>：全部 fumo 皮肤**共用同一个方块** ✓ ⇒ 「这只玩偶是哪个皮肤」
 * 只能存在方块实体里 ✓（放置时由 {@code FumoMoBlock#setPlacedBy} 从物品带过来 ✓）。
 * <ul>
 *   <li>存 NBT 键 {@code Skin} ✓（{@link #saveAdditional}/{@link #load} ✓）；</li>
 *   <li>发给客户端：区块加载走 {@link #getUpdateTag()}、放下的那一刻走 {@link #getUpdatePacket()}
 *       （服务端 {@code sendBlockUpdated} 会把它发出去 ✓）；</li>
 *   <li><b>老存档/字段缺失/名字不认</b> ⇒ 一律回退内置默认皮肤 ✓ <b>不崩</b> ✓。</li>
 * </ul>
 */
public class FumoMoBlockEntity extends BlockEntity implements net.minecraft.world.Nameable {

    /** 挤压动画总时长（tick）——与诡厄玩偶同为 <b>12 tick = 0.6 秒</b> ✓ */
    public static final int MAX_ANIMATION_TICKS = 12;

    /** 压到最扁用几个 tick（之后都是弹回 ✓ 诡厄也是 4 ✓） */
    public static final int PRESS_TICKS = 4;

    /** §1079 皮肤存在 NBT 的这个键里（老存档没这个键 ⇒ 默认皮肤 ✓） */
    public static final String TAG_SKIN = "Skin";

    /** 当前 tick 计数 / 上一 tick 的计数（渲染时 lerp 用 ✓ 照诡厄的写法 ✓） */
    private int animationTickCount;
    private int oAnimationTickCount;
    /** 正在播放挤压 ✓ */
    private boolean animating;

    /** §1079 这只玩偶的皮肤名（默认＝内置默认皮肤 ✓） */
    private String skin = FumoMoSkins.DEFAULT_SKIN;

    public FumoMoBlockEntity(BlockPos pos, BlockState state) {
        super(FumoMoDoll.FUMO_MO_BE.get(), pos, state);
    }

    // ============================================================
    //  §1079 皮肤
    // ============================================================

    /** 渲染/同步用：这只玩偶的皮肤名 ✓（为空或不认的一律回默认 ✓ 绝不抛异常 ✓） */
    public String getSkin() {
        String s = this.skin;
        if (s == null || s.isEmpty()) return FumoMoSkins.DEFAULT_SKIN;
        if (!FumoMoSkins.isKnown(s)) return FumoMoSkins.DEFAULT_SKIN;   // 皮肤被删掉/名字不认识 ⇒ 默认 ✓
        return s;
    }

    /** 放置时由方块从物品带过来 ✓（顺手标脏 ⇒ 存档里记得住 ✓） */
    public void setSkin(String skin) {
        this.skin = (skin == null || skin.isEmpty()) ? FumoMoSkins.DEFAULT_SKIN : skin;
        this.setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // 老存档没有这个键 ⇒ 直接用默认皮肤 ✓（§1079 的"旧数据回退"就靠这一句 ✓）
        this.skin = tag.contains(TAG_SKIN) ? tag.getString(TAG_SKIN) : FumoMoSkins.DEFAULT_SKIN;
    }

    /**
     * §1082 <b>地上的玩偶按自己的皮肤显示名字</b> ✓（用户报告：放下「跃金fufu」却显示「墨封白织fufu」✗）。
     *
     * <p>根因 ✓：所有皮肤**共用同一个方块** {@code tinkersnewlife:fumo_mo} ✗ ⇒
     * 显示名原本取自**方块**语言键 {@code block.tinkersnewlife.fumo_mo}（＝墨封白织fufu ✗），
     * 与"这只玩偶是哪个皮肤"无关 ✗。
     *
     * <p>修法 ✓：让方块实体实现 {@link net.minecraft.world.Nameable} ✓ ——
     * Jade／WAILA 这类"看着方块报名字"的 HUD 对有名字的方块实体就是走这条 ✓
     * ⇒ 名字直接复用**物品**的键体系 {@code item.tinkersnewlife.fumo_<皮肤>} ✓
     * （与 {@code FumoMoBaseItem#getDescriptionId} 完全一致 ✓ 皮肤改了名这里也跟着 ✓ 不会两套 ✗）。
     *
     * <p>⚠ 缺键兜底 ✓：皮肤被删/名字不认识 ⇒ {@link #getSkin()} 已回退默认皮肤 ✓；
     * 连默认键都没有（按理不会 ✗）时原版会把键名原样显示出来 ✓ —— 不会崩 ✓ 也不会显示成错误的皮肤 ✓。
     */
    @Override
    public net.minecraft.network.chat.Component getName() {
        return net.minecraft.network.chat.Component.translatable("item.tinkersnewlife.fumo_" + getSkin());
    }

    /**
     * ⚠ §1085 <b>这里必须返回 {@code true}</b> ✓ —— 用户实测："放下跃金fufu，玉(Jade)里仍显示墨封白织fufu" ✗。
     *
     * <p>原因 ✓：玉判断"要不要用**方块实体自己**的名字"时看的是这个标志 ✗ ——
     * §1082 我按"没在铁砧改名"的语义写了 {@code false} ✗ ⇒ 玉直接退回**方块默认名**
     * {@code block.tinkersnewlife.fumo_mo}（墨封白织fufu ✗）⇒ 皮肤渲染对了、名字却没跟着 ✓。
     * 改成 {@code true} ⇒ 玉走 {@code getDisplayName()} ⇒ 也就是 {@link #getName()}（＝皮肤那只的名字 ✓）。
     *
     * <p>⚠ 副作用检查过 ✓：原版只有"容器类"用它决定要不要显示自定义名 ✓；
     * 我们的方块自己不绘制任何名字 ✗（世界里由渲染器画玩偶 ✓），所以除了玉那一行之外没有别的可见变化 ✓。
     */
    @Override
    public boolean hasCustomName() {
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(TAG_SKIN, this.skin == null ? FumoMoSkins.DEFAULT_SKIN : this.skin);
    }

    /**
     * 区块（重新）发给客户端时带上皮肤 ✓ —— 老存档第一次进游戏/走远再回来，
     * 玩偶也能立刻是它自己的皮肤 ✓（不认的名字会在 {@link #getSkin()} 那里回退默认 ✓）。
     */
    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    /**
     * 放下玩偶的那一刻把皮肤推给周围客户端 ✓
     * （服务端 {@code FumoMoBlock#setPlacedBy} 里会调 {@code sendBlockUpdated} ⇒
     * 原版 {@code ChunkHolder} 的广播逻辑会取这个方法发出的包 ✓）。
     * <p>反序列化那一半用 Forge 的默认实现（{@code IForgeBlockEntity#onDataPacket} ⇒ 调 {@link #load} ✓）。
     */
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * 右键摸头时调用 —— <b>客户端和服务器都调</b> ✓（与诡厄 {@code PlushieBlock#use} 完全一致的写法 ✓：
     * 客户端本地放动画 ✓，服务器负责播音效 ✓）。
     * <p>§909 ⚠ 与诡厄不同的一处（**故意的** ✓）：诡厄的 {@code startAnimating} 只把开关打开、
     * **不复位 tick 计数** ⇒ 动画播到一半再摸，进度会从中间接着走、这一下"压"就被跳过了 ✗
     * ⇒ 实机感觉"必须等这次动画放完才能摸下一次" ✗（用户实测口径 ✓）。
     * <p>这里改成 **每次摸都从 0 重新开始** ✓：
     * <ul>
     *   <li>{@code o = 当前值}（保留）⇒ 渲染插值能从当前进度**平滑滑回** 0 ✓ 不会"啪"地跳一下 ✓；</li>
     *   <li>{@code count = 0} ⇒ 下一个 tick 起就是全新的 0 → 12 ⇒ 每摸一下都完整压一次 ✓。</li>
     * </ul>
     * <p>⚠ 已知限制（诡厄同样如此 ✓）：别人看不到你摸出来的挤压 ——
     * 本方块实体没有做数据同步 ✗。要多人可见得再补 {@code getUpdateTag}/{@code getUpdatePacket} ✓（备忘录 §908 记着 ✓）。
     */
    public void startAnimating() {
        this.oAnimationTickCount = this.animationTickCount;   // 保留当前值 ⇒ 插值回 0 是平滑的 ✓
        this.animationTickCount = 0;                          // ★ 每次摸都从头开始 ✓
        this.animating = true;
    }

    /**
     * 推进动画（由 {@code FumoMoBlock#getTicker} 每 tick 调用 ✓ 客户端也跑 ✓）。
     * <p>逻辑照诡厄玩偶的 {@code PlushieBlockEntity#animation} ✓：
     * 先记下 {@code o = 当前值} ⇒ 超过总时长就停 ✓ ⇒ 在播就 +1 ✓ ⇒ 没在播且还有余量就整体归零 ✓。
     */
    public static void tick(Level level, BlockPos pos, BlockState state, FumoMoBlockEntity be) {
        be.oAnimationTickCount = be.animationTickCount;
        if (be.animationTickCount > MAX_ANIMATION_TICKS) {
            be.animating = false;
        }
        if (be.animating) {
            be.animationTickCount++;
        } else if (be.animationTickCount > 0) {
            be.oAnimationTickCount = 0;
            be.animationTickCount = 0;
        }
        // §1086 先问「朋友的酒」还在不在放 ⇒ 在放就续命、停了就立刻归零 ✓
        //   （用户实测两条：①只转一下就停 ②停了还在转 ⇒ 都靠这里修 ✓）
        maintainDance(level, be);
        // 跳舞倒计时（纯客户端视觉 ✓ 音乐每 tick 会把它续上 ✓ 停了就归零 ✓）
        if (be.danceTicks > 0) {
            be.danceTicks--;
        }
    }

    /** 渲染用：0 → 12 的平滑进度（两个 tick 计数之间 lerp ✓ 照诡厄的 {@code getAnimation} ✓） */
    public float getAnimation(float partialTick) {
        return Mth.lerp(partialTick, this.oAnimationTickCount, this.animationTickCount);
    }

    // ============================================================
    //  §1083 跟随「朋友的酒」({@code friendswine}) 的音乐跳舞（纯客户端视觉 ✓）
    // ============================================================

    /** 剩余跳舞 tick ✓（>0 就转＋挤压 ✓ 只活在内存里 ✗ 不进 NBT、不发包 ✓）
     *  <p>⚠ 写入可能来自声音线程 ⇒ {@code volatile} ✓ */
    private volatile int danceTicks;
    /** §1086 舞蹈开始的世界时间 ✓（相位从"音乐开始"算 ✓ 才对得上它自己的 rotationStartTick ✓） */
    private long danceStart = -1L;

    /**
     * §1096 <b>跳舞范围＝16 格</b>（用户口径：「周围16格范围内的所有fumo」✓）
     * —— <b>两条来源统一用这一个口径</b> ✓：
     * ①原版唱片机在放我们的唱片 ✓ ②「朋友的酒」的音乐 ✓。
     * <p>判定一律用 {@code distSqr}（省一次开方 ✓）⇒ 比较 {@link #DANCE_RANGE_SQR} ✓；
     * 恰好 16 格（＝256）算"在范围内" ✓（判的是 {@code >} ✗）。
     */
    public static final double DANCE_RANGE = 16.0D;
    /** {@link #DANCE_RANGE} 的平方（＝256 ✓ 判定用 ✓ 别开方 ✓） */
    private static final double DANCE_RANGE_SQR = DANCE_RANGE * DANCE_RANGE;

    public void startDancing(int ticks) {
        // ⚠ §1089 <b>这里绝不能碰 {@code danceStart}</b> ✗✗ ——
        //   §1086 我在这里写了"danceTicks<=0 ⇒ danceStart=-1（相位归零）"✗，
        //   而调用方 {@link #maintainDance} 的顺序是「**先**写 danceStart=now ✓ → **再**调本方法」✓
        //   ⇒ 那句会把刚刚写好的起跳时刻**覆盖成 −1** ✗ ⇒ {@link #danceSeconds} 恒 0
        //   ⇒ {@code fwAngle(0)=0}、{@code fwSquash(0)=0} ⇒ **状态在跳、画面完全不动** ✗
        //   ——用户三次实测「不转」的最终原因就是它 ✓（§1087 修的赋值被它当场抹掉 ✗）。
        //   ⇒ 相位起点**只由调用方**在真正起跳那一刻写 ✓（见 maintainDance ✓）。
        if (ticks > this.danceTicks) this.danceTicks = ticks;
    }

    public boolean isDancing() {
        return this.danceTicks > 0;
    }

    /** §1086 舞蹈已进行的秒数 ✓（用"起跳时刻"算 ✓ 与它的 rotationSeconds 同一个起点 ✓）；没在世界里 ⇒ 0 ✓ */
    public static double danceSeconds(Level level, FumoMoBlockEntity be, float partialTick) {
        if (level == null || be == null || be.danceStart < 0L) return 0.0D;
        return Math.max(0.0D, level.getGameTime() - be.danceStart + partialTick) / 20.0D;
    }

    // ── §1086 照抄「朋友的酒」的 JellyAnimation（周期 0.91667 秒 ＝ 一圈转完 ＝ 挤两下 ✓）──
    /** 与它的 {@code TIMES} 完全一致 ✓ */
    private static final double[] FW_TIMES = {0.0D, 0.25D, 0.45833D, 0.70833D, 0.91667D};
    /** 与它的 {@code VALUES} 完全一致 ✓ */
    private static final double[] FW_VALUES = {0.0D, 1.0D, 0.0D, 1.0D, 0.0D};
    /** 它的 {@code phase(seconds)} ✓ */
    private static double fwPhase(double seconds) {
        return Math.max(0.0D, seconds) % 0.91667D;
    }
    /** 它的 {@code angle(seconds)} ✓（⇒ 每 0.91667 秒整一圈 ⇒ 约 5.5°/tick ✓） */
    public static float fwAngle(double seconds) {
        return (float) (-360.0D * fwPhase(seconds) / 0.91667D);
    }
    /** 它的 {@code squash(seconds)} ✓（关键帧之间用 smoothstep ✓） */
    public static float fwSquash(double seconds) {
        double t0 = fwPhase(seconds);
        int i = 0;
        while (i < FW_TIMES.length - 2 && t0 > FW_TIMES[i + 1]) i++;
        double t = (t0 - FW_TIMES[i]) / (FW_TIMES[i + 1] - FW_TIMES[i]);
        return (float) (FW_VALUES[i] + (FW_VALUES[i + 1] - FW_VALUES[i]) * t * t * (3.0D - 2.0D * t));
    }

    /**
     * 客户端**渲染过的**玩偶名册 ✓（{@code WeakHashMap} ⇒ 不泄漏 ✓）。
     * <p>⚠ 声音事件可能来自**声音线程** ⇒ 全部访问都要 {@code synchronized} ✓（§1085 的竞态教训 ✓）。
     */
    private static final java.util.Map<FumoMoBlockEntity, Long> RENDERED = new java.util.WeakHashMap<>();

    /** 渲染器每帧登记 ✓ */
    public static void trackRendered(FumoMoBlockEntity be, Level level) {
        if (be == null || level == null) return;
        synchronized (RENDERED) {
            if (RENDERED.size() > 128) {
                long now = level.getGameTime();
                RENDERED.entrySet().removeIf(e -> now - e.getValue() > 200L);
            }
            RENDERED.put(be, level.getGameTime());
        }
    }

    /** §1086 记住"朋友酒的音乐从哪儿放的" ✓（声音事件里记 ✓ 每 tick 靠它去找它那只玩偶 ✓） */
    private static volatile net.minecraft.core.BlockPos musicPos;
    private static volatile long musicTick = Long.MIN_VALUE;

    public static void noteMusicSource(Level level, double x, double y, double z) {
        if (level == null) return;
        musicPos = net.minecraft.core.BlockPos.containing(x, y, z);
        musicTick = level.getGameTime();
    }

    // ============================================================
    //  §1096 原版唱片机在放「我们的唱片」⇒ 跳舞（**完全不依赖「朋友的酒」** ✓）
    // ============================================================

    /**
     * 我们的唱片是从<b>哪台唱片机</b>放出来的 ✓＋那一格属于哪个维度 ✓。
     * <p>由客户端声音事件写（{@code FumoMoDanceHandler} ✓）；这里只读 ✓ ⇒ {@code volatile} ✓。
     * <p>⚠ 维度也要记：唱片机在 A 维度、玩偶在 B 维度时 {@code BlockPos} 会"撞上"B 维度同坐标的方块 ✗。
     */
    private static volatile BlockPos discPos;
    private static volatile ResourceKey<Level> discDim;
    /** 听到唱片开始的客户端 tick ✓（配曲长兜底判"放完了" ✓） */
    private static volatile long discStartTick = Long.MIN_VALUE;
    /**
     * ⭐⭐⭐⭐ §1241 <b>新舞蹈：⭐ "听到的是哪张唱片" ＋ ⭐ "那张唱片多长"</b> ✗✗
     * （⭐ 用户口径 ✓ 2026-10-10：「**不是替换，是新舞蹈和新唱片**」✓）
     * <ul>
     *   <li>{@code discStyle ＝ 0} ⇒ ⭐ 老舞蹈（⭐ 朋友的酒那套"旋转＋挤压" ✓）；</li>
     *   <li>{@code discStyle ＝ 1} ⇒ ⭐ **新舞蹈**（⭐ 左转＋前倾点头 ✗ ⭐ 原路回正 ✗ ⭐ 右转＋前倾点头 ✓）。</li>
     * </ul>
     * ⚠ ⭐ 曲长也一起记 ✗ —— ⭐ 两张唱片**长度不同** ✓（⭐ 581KB 那张 1126 tick ✗
     * ⭐ 新的那张 1324 tick ✓）⇒ ⭐ `discPlayingNear` 的"放完了没"判定必须用**记下来的那张** ✓ ✓。
     */
    private static volatile int discStyle = 0;
    private static volatile int discLength = 1126;

    /**
     * <b>曲长兜底</b>：我们的唱片本身有多长（tick ✓ 由 ogg 末页 granule 算出来 ✓
     * 见 {@code ModItems#MUSIC_DISC_DOLL_MUSIC_LENGTH_TICKS} ✓）。
     * <p>⚠ 为什么不直接问唱片机"还在放吗"就完事 ✗——<b>原版不给客户端同步唱片机的播放状态</b> ✗：
     * {@code JukeboxBlockEntity} 既没有 {@code getUpdatePacket}、{@code setItem} 也不
     * {@code sendBlockUpdated} ⇒ 客户端那只方块实体里的"唱片物品／IsPlaying"是**区块加载那一刻的快照**
     * ✗（插入唱片时它多半还是空的 ✗、原曲放完了它可能还停在 true ✗）⇒ <b>它的"没在放"不能信</b> ✗
     * （信了就是"插了唱片也不跳"✗）。
     * <p>⇒ 真正靠得住的三件事：①声音事件给的位置 ✓ ②那格方块状态 {@code HAS_RECORD}
     * （方块状态是同步的 ✓ 唱片被取走／唱片机被拆 ⇒ 立刻为假 ✓）③这个曲长 ✓。
     */
    private static final int DISC_END_PAD_TICKS = 20;

    /** §1096 声音事件告诉我们"我们的唱片从这儿放了" ✓（⭐ 老签名保留 ✗ ⭐ 走 style 0 ✓） */
    public static void noteDiscSource(Level level, double x, double y, double z) {
        noteDiscSource(level, x, y, z, 0, 1126);
    }

    /** ⭐ §1241 带"哪张唱片"的版本 ✗（⭐ style 1 ＝ 新舞蹈 ✓） */
    public static void noteDiscSource(Level level, double x, double y, double z, int style, int lengthTicks) {
        if (level == null) return;
        discPos = BlockPos.containing(x, y, z);
        discDim = level.dimension();
        discStartTick = level.getGameTime();
        discStyle = style;
        discLength = Math.max(20, lengthTicks);
    }

    /** ⭐ §1241 当前跳舞风格（⭐ 0 ＝ 老旋转 ✗ ⭐ 1 ＝ 新序列 ✓） */
    public static int discStyle() {
        return discStyle;
    }

    /** §1096 同一格唱片机改放**别的**唱片 ⇒ 把"我们的唱片在放"立刻作废 ✓（否则会跟着别人的曲子跳 ✗） */
    public static void clearDiscSourceAt(double x, double y, double z) {
        BlockPos p = discPos;
        if (p != null && p.equals(BlockPos.containing(x, y, z))) {
            discPos = null;
            discDim = null;
            discStartTick = Long.MIN_VALUE;
            discStyle = 0;
        }
    }

    /**
     * §1096 ①「原版唱片机正在放<b>我们的</b>唱片、且离这只玩偶 ≤ {@link #DANCE_RANGE} 格」⇒ {@code true}
     * （调用方据此续命跳舞 ✓ 见 {@link #maintainDance} ✓）。任何异常都吞掉 ⇒ 最多"不跳" ✓ 绝不崩 ✗。
     *
     * <p>判定链（全部只用客户端拿得到的东西 ✓）：
     * <ol>
     *   <li><b>位置</b>：{@link #discPos}（声音事件给的 ✓）不是这台玩偶所在维度 ⇒ 不算 ✓；</li>
     *   <li><b>距离</b>：{@code distSqr > 16²} ⇒ 不算 ✓（用户口径 16 格 ✓）；</li>
     *   <li><b>方块状态</b>：那格还得是<b>装着唱片的唱片机</b>（{@code JukeboxBlock.HAS_RECORD} ✓）
     *       —— 唱片被取走／唱片机被拆 ⇒ 立刻为假 ⇒ <b>玩偶立刻停</b> ✓；</li>
     *   <li><b>曲长</b>：超过 ogg 真实时长＋{@link #DISC_END_PAD_TICKS} ⇒ 认定"放完了"⇒ 停 ✓
     *       （＝原版唱片机自己的停机线口径 ✓ {@code length + 20} ✓）；</li>
     *   <li><b>{@code JukeboxBlockEntity#isRecordPlaying()} 佐证</b> ✓（用户点名要问它 ✓）——
     *       ⚠ 只采信它**明确说"正在放别的唱片"**那一种情况 ✗（⇒ 立刻停 ✓）；
     *       它说"没在放"时**不能当停** ✗（客户端数据本来就不新鲜 ✗ 见 {@link #DISC_END_PAD_TICKS} 上面的说明 ✓）。</li>
     * </ol>
     */
    private static boolean discPlayingNear(Level level, FumoMoBlockEntity be, long now) {
        try {
            BlockPos p = discPos;
            if (p == null) return false;
            if (discDim != null && !discDim.equals(level.dimension())) return false;
            if (be.getBlockPos().distSqr(p) > DANCE_RANGE_SQR) return false;      // ← 16 格口径 ✓
            BlockState st = level.getBlockState(p);
            if (!(st.getBlock() instanceof net.minecraft.world.level.block.JukeboxBlock)
                    || !st.getValue(net.minecraft.world.level.block.JukeboxBlock.HAS_RECORD)) {
                clearDiscSourceAt(p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D);
                return false;                                                     // 唱片没了/机器没了 ⇒ 立刻停 ✓
            }
            if (now - discStartTick > (long) discLength + DISC_END_PAD_TICKS) {
                return false;                                                     // 曲子放完了 ⇒ 停 ✓
            }
            // ⑤ 唱片机方块实体佐证 ✓（只采信"它明确在放别的唱片"⇒ 停 ✗ 见方法注释 ✓）
            BlockEntity jb = level.getBlockEntity(p);
            if (jb instanceof net.minecraft.world.level.block.entity.JukeboxBlockEntity jukebox
                    && jukebox.isRecordPlaying()
                    && !jukebox.getFirstItem().isEmpty()
                    && !jukebox.getFirstItem().is(ModItems.MUSIC_DISC_DOLL_MUSIC.get())
                    && !jukebox.getFirstItem().is(ModItems.MUSIC_DISC_TELL_ME.get())) {
                return false;
            }
            return true;
        } catch (Throwable t) {
            return false;                                                         // fail-safe：不跳 ✓ 不崩 ✗
        }
    }

    /** 「朋友的酒」的玩偶方块实体类名（**只用名字比对** ✓ 不用 Class.forName ✗） */
    private static final String FW_BE_CLASS = "com.friendswine.DollBlockEntity";
    /** 每个类各自缓存 isPlaying（⚠ §1088：**必须从找到的那个对象取类** ✓
     *  —— Forge 每个模组一个类加载器 ✗ ⇒ 在**我们**的加载器里 `Class.forName("com.friendswine.…")` 会失败 ✗，
     *  这就是 §1086/§1087"永远问不到 ⇒ 一直不转"的根因 ✓） */
    private static final java.util.Map<Class<?>, java.lang.reflect.Method> FW_METHODS =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** 缓存"它那只玩偶在哪" ✓ */
    private static volatile net.minecraft.core.BlockPos fwDollPos;

    /**
     * 问一个方块实体"你在放音乐吗" ✓ —— 三态：
     * {@code TRUE} 在放 ✓／{@code FALSE} 它说没放 ✓／{@code null} **问不到**（不是它 / 反射不可用 ✓）
     * ⇒ 调用方对 {@code null} 采取"**当作在放**"✓（保证一定会转 ✓ 用户口径优先 ✓）。
     */
    private static Boolean fwAskPlaying(Level level, net.minecraft.core.BlockPos p) {
        try {
            BlockEntity cand = level.getBlockEntity(p);
            if (cand == null) return null;
            Class<?> c = cand.getClass();
            if (!FW_BE_CLASS.equals(c.getName())) return null;
            java.lang.reflect.Method m = FW_METHODS.get(c);
            if (m == null) {
                m = c.getMethod("isPlaying");      // ★ 用它自己的类 ⇒ 跨加载器也能拿到 ✓
                FW_METHODS.put(c, m);
            }
            Object r = m.invoke(cand);
            return r instanceof Boolean b ? b : null;
        } catch (Throwable t) {
            return null;                            // 问不到 ⇒ 交给调用方当"在放"✓
        }
    }

    /**
     * 在**我们自己玩偶附近**找它那只 ✓（缓存优先 ⇒ 便宜 ✓）。
     *
     * <p>返回 ✓：{@code TRUE}=它在放 ✓／{@code FALSE}=它明确说没放 或 **附近根本没有它的玩偶** ✓／
     * {@code null}=**有它那只但问不到**（反射失败 ✓ ⇒ 调用方当作在放 ✓）。
     *
     * <p>⚠ §1090 修（用户实测：「有玩偶没放音乐时不转 ✓、放音乐转 ✓、**打掉玩偶（音乐停）还在转** ✗」）：
     * 原来"问不到"一律返回 {@code null} ✗ ⇒ 它那只被砸掉/搬走后 ✗ 我们会一直当作"在放" ⇒
     * 在 3 分钟窗口内**继续转** ✗ ⇒ 现在改成"**没找到它那只 ⇒ 就是没在放** ⇒ 立刻停" ✓。
     */
    private static Boolean fwQuery(Level level, FumoMoBlockEntity be) {
        net.minecraft.core.BlockPos cached = fwDollPos;
        if (cached != null) {
            if (FW_BE_CLASS.equals(level.getBlockEntity(cached) == null
                    ? "" : level.getBlockEntity(cached).getClass().getName())) {
                Boolean r = fwAskPlaying(level, cached);
                if (r != null) return r;
            }
            fwDollPos = null;
        }
        boolean sawTheirs = false;
        net.minecraft.core.BlockPos mine = be.getBlockPos();
        for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(
                mine.offset(-8, -4, -8), mine.offset(8, 4, 8))) {
            BlockEntity cand = level.getBlockEntity(p);
            if (cand == null || !FW_BE_CLASS.equals(cand.getClass().getName())) continue;
            sawTheirs = true;
            Boolean r = fwAskPlaying(level, p);
            if (r != null) {
                fwDollPos = p.immutable();
                return r;
            }
        }
        // 有它那只但反射不了 ⇒ 当作在放 ✓；附近压根没有它那只 ⇒ 它的音乐不可能在放 ⇒ 停 ✓
        return sawTheirs ? Boolean.TRUE : Boolean.FALSE;
    }

    /**
     * §1086~§1088 每 tick（客户端 ✓）维护舞蹈 ✓：
     * <ul>
     *   <li>「只转一下下就停」✗ ⇒ 窗口放宽 {@link #HEARD_WINDOW} ＋ 每 tick 续命 ✓；</li>
     *   <li>「停止音乐依然在转」✗ ⇒ 问到它说 false ⇒ 立刻停 ✓；</li>
     *   <li>⚠「不转了」✗ ⇒ ①{@code danceStart} 没赋值（§1087 已修 ✓）②<b>问不到</b>时不再当作停 ✗
     *       ⇒ 改成**当作在放** ✓（宁可多跳一会儿 ✓ 也不能一点都不跳 ✓）。</li>
     * </ul>
     *
     * <p>§1096 加一条<b>前置</b>分支 ✓：<b>①原版唱片机在放我们的唱片</b> ⇒ 跳 ✓
     * （{@link #discPlayingNear} ✓ 距离 16 格 ✓）。它<b>命中就直接 return</b> ✓ ⇒
     * 下面那条「朋友的酒」分支不会把它清掉 ✓ —— <b>没装朋友的酒时也照跳</b> ✓
     *（用户口径：「无朋友的酒模组情况下」✓）。
     * <p>而"我们的唱片停了"（唱片被取走／机器被拆／曲子放完 ✓）⇒ ①不命中 ⇒
     * 落回②这条 ✓（没装那模组时 {@link #fwQuery} 找不到它那只 ⇒ 说"没在放" ⇒
     * {@code danceTicks = 0} ⇒ <b>立刻停</b> ✓ 正是用户要的 ✓）。
     */
    public static void maintainDance(Level level, FumoMoBlockEntity be) {
        if (level == null || be == null || !level.isClientSide) return;
        long now = level.getGameTime();
        // ── §1096 ①原版唱片机 + 我们的唱片 ⇒ 跳舞 ✓（**不依赖朋友的酒** ✓ 16 格 ✓）
        if (discPlayingNear(level, be, now)) {
            if (be.danceTicks <= 0) be.danceStart = now;   // 起跳时刻 ⇒ 相位起点 ✓
            be.startDancing(20);
            return;
        }
        // ── ②§1083~§1090 原来的「朋友的酒」分支 ✓（逻辑一字未改 ✓ 只把距离口径写死成 16 格 ✓）
        boolean heardRecently = musicPos != null && now - musicTick <= HEARD_WINDOW;
        if (!heardRecently) {
            fwDollPos = null;
            be.danceTicks = 0;
            return;
        }
        Boolean playing = fwQuery(level, be);
        if (DEBUG_LEFT > 0 && now % 200L == 0L) {
            DEBUG_LEFT--;
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[fufu·诊断] 音乐窗口内 ✓ 它说我={} 玩偶={} 跳舞={}",
                    playing, be.getBlockPos(), be.danceTicks);
        }
        if (playing == null || playing) {              // ★ 问不到 ⇒ 当作在放 ✓
            // §1096 距离口径统一 16 格 ✓：locate 到它那只了 ⇒ 就用它那只的位置量 ✓
            //   （下面那条邻近扫描箱是 ±8 ⇒ 最远约 12 格 ⇒ 天然满足 ✓ 这句是把口径写死、免得以后被人放大 ✗）
            if (fwDollPos != null && be.getBlockPos().distSqr(fwDollPos) > DANCE_RANGE_SQR) {
                be.danceTicks = 0;
                return;
            }
            if (be.danceTicks <= 0) be.danceStart = now;   // 起跳时刻 ⇒ 相位起点 ✓
            be.startDancing(20);
        } else {
            be.danceTicks = 0;                         // 它明确说停了 ⇒ 立刻停 ✓
        }
    }

    /** 诊断日志还剩几条（§1088 临时 ✓ 确认后删） */
    private static int DEBUG_LEFT = 10;

    /** 听到音乐后多久内允许去问它（3 分钟 ✓ 覆盖最长那几首；真正停靠它自己的答复 ✓ 问不到时才靠这个兜底 ✓） */
    private static final long HEARD_WINDOW = 3600L;

    /** 立刻停止所有在跳的玩偶 ✓ */
    public static void stopAllDancing() {
        synchronized (RENDERED) {
            for (FumoMoBlockEntity be : RENDERED.keySet()) {
                if (be != null) be.danceTicks = 0;
            }
        }
    }
    // ============================================================
    //  §1242 新舞蹈（⭐ 第二版 ✗ ⭐ 用户实测「**模型在往上抽搐**」后重写 ✓）
    //  （⭐ 用户口径 ✓：「**左转一下然后整体前倾点头，再原路回到初始正面，
    //    再右转一下前倾点头**」✓ ⭐ 参考用户给的 gif ✓）
    //
    //  ⚠⚠ 第一版**错在两处** ✗（⭐ 如实 ✓）：
    //    ① ⭐ **点头用了 8 Hz 正弦** ✗（⭐ 0.25 秒里摆两次 ✓）⇒ ⭐ 那**就是"抽搐"** ✓ ✓；
    //    ② ⭐ **前倾符号反了** ✗ ⇒ ⭐ 看着像"**往上**"而不是"**往前趴**" ✓。
    //  ⇒ ⭐ 第二版 ✗：⭐ **不要高频正弦** ✗ ⭐ 用"**前倾下去 ⭐ 再点回来**"
    //    （⭐ 一次**慢点头** ≈ 0.8 秒 ✓）⭐ 这才是 gif 那个感觉 ✓；
    //    ⭐ 符号抽成 ⭐ **一个常数** `ND_LEAN_SIGN` ✗ ⭐ 要反过来只改这一处 ✓ ✓。
    // ============================================================

    /** 总周期（秒 ✓）：⭐ 两个方向各一轮 ＋ ⭐ 中间停顿 ✓ */
    private static final double ND_PERIOD = 3.60D;
    /** 转向角度（度 ✓） */
    private static final float ND_TURN = 45.0F;
    /** 前倾角度（度 ✓） */
    private static final float ND_LEAN = 15.0F;
    /**
     * ⭐⭐ **前倾/点头的方向符号** ✗（⭐ 第二版的"唯一开关" ✓）。
     * <p>⚠ 用户实测第一版 ⭐「**模型在往上抽搐**」✓ ⇒ ⭐ 若现在看还是"往后仰" ✗
     * ⭐ **把这里改成 `+1.0F`** 即可 ✓（⭐ 别的地方都不用动 ✓）。
     */
    private static final float ND_LEAN_SIGN = -1.0F;

    /** ⭐ 平滑（⭐ smoothstep ✓）：⭐ 起停都不生硬 ✓ */
    private static double ndSmooth(double t) {
        if (t <= 0.0D) return 0.0D;
        if (t >= 1.0D) return 1.0D;
        return t * t * (3.0D - 2.0D * t);
    }

    /** ⭐ 段内进度 ✗（⭐ 落在 [0,1] ✓ ⭐ 段外夹住 ✓） */
    private static double ndSeg(double t, double a, double b) {
        if (t <= a) return 0.0D;
        if (t >= b) return 1.0D;
        return (t - a) / (b - a);
    }

    /** ⭐ 新舞蹈·偏航 ✗：⭐ 左转 ⇒ ⭐ 保持 ✓ ⇒ ⭐ 原路回正 ✓ ⇒ ⭐ 右转 ✓ ⇒ ⭐ 保持 ✓ ⇒ ⭐ 回正 ✓ */
    public static float seqYaw(double seconds) {
        double t = seconds % ND_PERIOD;
        if (t < 0.45D) return -ND_TURN * (float) ndSmooth(ndSeg(t, 0.00D, 0.45D));   // 左转出去 ✓
        if (t < 1.25D) return -ND_TURN;                                             // 停在左边（含点头 ✓）
        if (t < 1.70D) return -ND_TURN * (float) (1.0D - ndSmooth(ndSeg(t, 1.25D, 1.70D)));  // 原路回正 ✓
        if (t < 2.05D) return ND_TURN * (float) ndSmooth(ndSeg(t, 1.70D, 2.05D));   // 右转出去 ✓
        if (t < 2.85D) return ND_TURN;                                              // 停在右边（含点头 ✓）
        if (t < 3.30D) return ND_TURN * (float) (1.0D - ndSmooth(ndSeg(t, 2.85D, 3.30D)));   // 回正 ✓
        return 0.0F;                                                                // 停顿 ✓
    }

    /**
     * ⭐ 新舞蹈·前倾＋点头 ✗ —— ⭐ **一次"下去再回来"就是一次点头** ✓
     * <p>⚠ ⭐ 不再叠加高频正弦 ✗（⭐ 那正是第一版"抽搐"的根因 ✓）。
     */
    public static float seqLean(double seconds) {
        double t = seconds % ND_PERIOD;
        float v = 0.0F;
        if (t >= 0.45D && t < 1.25D) {
            // 左转那次：⭐ 前倾下去（0.45~0.85）⇒ ⭐ 点回来（0.85~1.25）✓
            v = (t < 0.85D)
                    ? ND_LEAN * (float) ndSmooth(ndSeg(t, 0.45D, 0.85D))
                    : ND_LEAN * (float) (1.0D - ndSmooth(ndSeg(t, 0.85D, 1.25D)));
        } else if (t >= 2.05D && t < 2.85D) {
            // 右转那次：⭐ 同上 ✓
            v = (t < 2.45D)
                    ? ND_LEAN * (float) ndSmooth(ndSeg(t, 2.05D, 2.45D))
                    : ND_LEAN * (float) (1.0D - ndSmooth(ndSeg(t, 2.45D, 2.85D)));
        }
        return v * ND_LEAN_SIGN;
    }

    /** ⭐ 兼容旧调用 ✗：⭐ 第二版**没有独立点头**了 ✓（⭐ 点头 ＝ 前倾下去再回来 ✓） */
    public static float seqNod(double seconds) {
        return 0.0F;
    }}
