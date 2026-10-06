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
public class FumoMoBlockEntity extends BlockEntity {

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
    }

    /** 渲染用：0 → 12 的平滑进度（两个 tick 计数之间 lerp ✓ 照诡厄的 {@code getAnimation} ✓） */
    public float getAnimation(float partialTick) {
        return Mth.lerp(partialTick, this.oAnimationTickCount, this.animationTickCount);
    }
}
