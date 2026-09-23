package com.mofengbaizhi.tinkersnewlife.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * <b>魔力台座的「上段」</b>（§607 用户口径：「把台座变成两格高的方块物，只不过保持目前的模型和碰撞箱不变」✓）。
 *
 * <h2>它是什么 / 为什么需要它</h2>
 * 用户实测的需求：水晶悬浮的那一格（台座正上方）应当**算台座的一部分** ✓ ——
 * 漏斗 / 管道**直接接到那一格**上也能塞取 ✓。
 * <p>但 1.20.1 的容器查询是"**先取那一格的方块实体、再问能力**"（我读了 Forge 源码里漏斗那条路 ✓
 * 就是 {@code level.getBlockEntity(pos)} ✓）⇒ <b>空气格子永远不可能提供物品能力</b> ✗，
 * 而且这版 Forge 也**没有**"按坐标挂能力"的 API ✗（`RegisterCapabilitiesEvent` 只有 `register(Class)` ✓）。
 * ⇒ 唯一做法就是"那一格真的有个方块实体" ✓ —— 也就是把台座做成**两格高的结构** ✓。
 *
 * <h2>⚠ 为什么"看上去和以前一模一样"（用户要求 ✓）</h2>
 * <ul>
 *   <li>{@link RenderShape#INVISIBLE} ⇒ **完全不渲染** ✓（模型文件只是个空壳 ✓ 见
 *       {@code models/block/elder_mana_pedestal_top.json} ✓）；</li>
 *   <li>{@code getShape}/{@code getCollisionShape} = {@link Shapes#empty()} ⇒ **没有碰撞箱、也没有选中框** ✓
 *       ⇒ 玩家走得过去 ✓ 准星/挖掘也**选不中**它 ✓（所以它不可能被手挖掉 ✓ 不会被 Jade 显示成怪东西 ✓）；</li>
 *   <li>{@code getLightBlock = 0} + {@code propagatesSkylightDown = true} ⇒ **不挡光** ✓
 *       —— 这一条是**必须的** ✗：台座充能速率是按"台座上方那格的亮度"算的 ✓
 *       若这格挡光就会把台座变暗 ⇒ **白送一个加速 buff** ✗（用户口径"别爆表" ✓ 详见备忘录 ✓）；</li>
 *   <li>{@code canBeReplaced = true} ⇒ 玩家/管道照样能**往这一格放东西** ✓（放了它就自动让位 ✓
 *       台座不会把玩家的东西顶掉 ✓ —— 只要那格不是空气，台座那边也不会再补回来 ✓）。</li>
 * </ul>
 *
 * <h2>生命周期</h2>
 * <ul>
 *   <li><b>放台座</b> ⇒ {@link ElderManaPedestalBlock#onPlace} 顺手把上段补上（那格是空气才补 ✓）；</li>
 *   <li><b>台座没了</b> ⇒ {@link ElderManaPedestalTopBlockEntity} 每 tick 看一眼下面是不是台座 ✓
 *       不是就自己消失 ✓（爆炸/活塞把台座弄没了也不会留下孤儿 ✓）；</li>
 *   <li><b>区块重载 / 老存档</b>（原来只有一格台座 ✓）⇒ 台座那边每 tick 检查、是空气就补 ✓ 自动升级 ✓。</li>
 * </ul>
 */
public class ElderManaPedestalTopBlock extends BaseEntityBlock {

    public ElderManaPedestalTopBlock() {
        super(BlockBehaviour.Properties.of()
                // ⚠ 不可破坏（反正也没有选中框 ⇒ 挖不到 ✓）；爆炸抗性给足 ⇒ 只有"台座被炸没"这种极端情况会牵连它 ✓
                .strength(-1.0F, 3_600_000.0F)
                .noCollission()          // 没有碰撞箱 ✓
                .noOcclusion()           // 不遮挡 ✓（配合下面的 getLightBlock = 0 ✓）
                .replaceable()           // 玩家/管道能往这一格放东西 ✓（放了它就让位 ✓）
                .pushReaction(PushReaction.DESTROY)   // 活塞顶掉也没事：台座下一秒会补回来 ✓
                // ⚠ 1.20.1 里 isViewBlocking / isSuffocating **不是可覆写的方法** ✗ 得从属性设 ✓
                //   （原版玻璃就是这么写的 ✓ 光 .noOcclusion() 只管"不遮挡" ✗ 不管"挡不挡视线/闷不闷人" ✓）
                .isViewBlocking((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .sound(SoundType.AMETHYST)
                .mapColor(MapColor.NONE));
    }

    /** 空形状 ⇒ 无碰撞 + 无选中框 ✓（准星穿过去打到后面的方块 ✓ 与用户口径一致 ✓） */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** ⚠ 不挡天光 ✓（否则台座会凭空变暗 = 白送充能加速 ✗） */
    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    /** ⚠ 光衰减 0 ✓（同上 ✓ 台座靠"上方亮度"算速率 ✓ 这一格必须完全透明 ✓） */
    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    /** 不遮挡视线 ⇒ 不影响其它方块的渲染/剔除 ✓（⚠ 见上面属性里的注释：这条得从 Properties 设 ✓） */
    /** 玩家/管道可以往这一格放东西 ✓（放了就把我们替换掉 ✓ 台座不会强行补回来 ✗） */
    @Override
    public boolean canBeReplaced(BlockState state, net.minecraft.world.item.context.BlockPlaceContext ctx) {
        return true;
    }

    /** 完全不渲染 ✓ */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElderManaPedestalTopBlockEntity(pos, state);
    }

    /** 服务端每 tick：下面不是台座就自己消失 ✓（客户端不用挂 ✗） */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type,
                com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities.ELDER_MANA_PEDESTAL_TOP.get(),
                ElderManaPedestalTopBlockEntity::serverTick);
    }

    // ============================================================
    //  台座 ↔ 上段 的联动（⚠ 主动那一半在台座身上 ✗ 不在本类 ✓）
    // ============================================================

    /**
     * ⚠⚠ <b>本类刻意**不**写 {@code onPlace} / {@code onRemove}</b> ✗ —— 第一版我写在这里 ✓
     * 结果是**无限叠塔** ✗✗：{@code setBlock} 放下一个上段会触发**它自己**的 {@code onPlace}
     * ⇒ 又往上补一个 ⇒ 一路叠到建高上限 ✗。
     * <p>正确分工：<b>台座</b>（{@link ElderManaPedestalBlock}）负责"放下时补上段 / 拆掉时带上段" ✓；
     * <b>本方块</b>只负责"下面不再是台座就自己消失"（见 {@link ElderManaPedestalTopBlockEntity#serverTick} ✓）。
     */

    /**
     * 保证 {@code pedestalPos} 上面那一格是我们的上段 ✓（**只在那格是空气时**才放 ✓）。
     * <p>台座的方块实体每 tick 会调它一次 ⇒ 老存档/区块重载后会自动补上 ✓；
     * 而"玩家往那一格放了漏斗/管道"时那格不是空气 ⇒ 这里**什么都不做** ✓（不会顶掉玩家的东西 ✓）。
     */
    public static void ensureTop(Level level, BlockPos pedestalPos) {
        BlockPos above = pedestalPos.above();
        if (!level.getBlockState(above).isAir()) return;
        level.setBlock(above, com.mofengbaizhi.tinkersnewlife.content.ModBlocks.ELDER_MANA_PEDESTAL_TOP.get()
                .defaultBlockState(), 3);
    }
}
