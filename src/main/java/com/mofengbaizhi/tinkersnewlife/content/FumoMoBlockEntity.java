package com.mofengbaizhi.tinkersnewlife.content;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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

    public void startDancing(int ticks) {
        if (this.danceTicks <= 0) this.danceStart = -1L;   // 重新起跳 ⇒ 相位归零 ✓
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

    /** 「朋友的酒」的玩偶方块实体（反射问它 {@code isPlaying()} ✓ 不引用它的类型 ⇒ 没装也不会崩 ✓） */
    private static final String FW_BE_CLASS = "com.friendswine.DollBlockEntity";
    private static java.lang.reflect.Method fwIsPlaying;
    private static boolean fwReflectTried;

    private static boolean friendswinePlaying(Level level, net.minecraft.core.BlockPos src) {
        try {
            if (!fwReflectTried) {
                fwReflectTried = true;
                fwIsPlaying = Class.forName(FW_BE_CLASS).getMethod("isPlaying");
            }
            if (fwIsPlaying == null) return false;
            for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(
                    src.offset(-3, -2, -3), src.offset(3, 2, 3))) {
                BlockEntity cand = level.getBlockEntity(p);
                if (cand != null && FW_BE_CLASS.equals(cand.getClass().getName())) {
                    Object r = fwIsPlaying.invoke(cand);
                    if (r instanceof Boolean b && b) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 音乐离玩偶多近才一起跳（格 ✓ 与 §1083 一致 ✓） */
    private static final double DANCE_RANGE = 16.0D;

    /**
     * §1086 每 tick（客户端 ✓）维护舞蹈 ✓ —— 用户实测两条都在这修掉：
     * ①「只转一下下就停」✗ ⇒ 不再靠"听到声音撑 2 分钟"，而是**每 tick 问它自己 {@code isPlaying()}** ✓ 在放就一直续 ✓；
     * ②「停止音乐依然在转」✗ ⇒ 它一说 {@code false} ⇒ 立刻归零 ✓（留 20 tick 宽限避免抖动 ✓）。
     */
    public static void maintainDance(Level level, FumoMoBlockEntity be) {
        if (level == null || be == null || !level.isClientSide) return;
        long now = level.getGameTime();
        net.minecraft.core.BlockPos src = musicPos;
        if (src == null || now - musicTick > 40L
                || be.getBlockPos().distSqr(src) > DANCE_RANGE * DANCE_RANGE) {
            be.danceTicks = 0;
            return;
        }
        if (friendswinePlaying(level, src)) {
            be.startDancing(20);
        } else if (now - musicTick > 20L) {
            be.danceTicks = 0;
        }
    }

    /** 立刻停止所有在跳的玩偶 ✓ */
    public static void stopAllDancing() {
        synchronized (RENDERED) {
            for (FumoMoBlockEntity be : RENDERED.keySet()) {
                if (be != null) be.danceTicks = 0;
            }
        }
    }
}
