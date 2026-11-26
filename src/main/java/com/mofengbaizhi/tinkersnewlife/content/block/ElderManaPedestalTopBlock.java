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
 *   <li><b>轮廓（选中框）= 整格</b>（§607c ✓）{@code getCollisionShape} = {@link Shapes#empty()} ⇒
 *       <b>有方框、但没有碰撞箱</b> ✓ ⇒ 玩家走得过去 ✓ 站着也不会踩上去 ✓；
 *       ⚠ 有轮廓是**必须的** ✗ —— 用户口径「整个空气我没法放置朝向啊」✓：没有轮廓就选不到"面"，
 *       也就没法对着它朝下放漏斗 / 把管道接进来 ✓；</li>
 *   <li>{@code getLightBlock = 0} + {@code propagatesSkylightDown = true} ⇒ **不挡光** ✓
 *       —— 这一条是**必须的** ✗：台座充能速率是按"台座上方那格的亮度"算的 ✓
 *       若这格挡光就会把台座变暗 ⇒ **白送一个加速 buff** ✗（用户口径"别爆表" ✓ 详见备忘录 ✓）；</li>
 *   <li>{@code canBeReplaced = false}（§607b 按用户实测反馈改的 ✓）⇒ 这一格**真的属于台座** ✓
 *       <b>挡建造、也挡流体</b> ✓（想在那儿盖东西 ⇒ 先拆台座 ✓）；而漏斗/管道照样能用 ✓ ——
 *       接法是"**上面一格朝下接**"或"**用管道从侧面接进这一格**" ✓，不是把方块放进这一格 ✗。</li>
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
                // ⚠ 硬度 -1 = 生存里**打不掉** ✓（§607c 起它有轮廓、能被准星选中 ⇒ 左键点得到 ✓ 但挖不动 ✓）
                //   创造模式倒是能敲掉 ⇒ 也没关系：台座下一秒会补回来 ✓（见 ElderManaPedestalBlockEntity#tick ✓）
                //   爆炸抗性给足 ⇒ 只有"台座被炸没"这种极端情况会牵连它 ✓
                .strength(-1.0F, 3_600_000.0F)
                .noCollission()          // 没有碰撞箱 ✓
                .noOcclusion()           // 不遮挡 ✓（配合下面的 getLightBlock = 0 ✓）
                // ⚠ §607b **刻意不给 replaceable** ✗ —— 用户实测反馈：「它上方那一格还是能放置方块啊」✓
                //   你要的是"那格真的属于台座" ⇒ 所以它必须**挡住建造** ✓（跟门的上半扇同一个道理 ✓）：
                //   想在这格盖东西 ⇒ **先把台座拆了** ✓（拆台座会带走这一格 ✓ 见 ElderManaPedestalBlock#onRemove ✓）。
                //   ⚠ 代价（诚实记一笔 ✓）：① 漏斗/箱子**不能放进**这一格 ✗（但可以放在**它上面一格**朝下接 ✓、
                //   或者用管道从**侧面**接进这一格 ✓ —— 这正是用户要的"接" ✓）；
                //   ② 水/岩浆也不会流进这一格 ✗（以前是空气时可以 ✓ 属"两格高结构"的必然结果 ✓）。
                .pushReaction(PushReaction.DESTROY)   // 活塞顶掉也没事：台座下一秒会补回来 ✓
                // ⚠ 1.20.1 里 isViewBlocking / isSuffocating **不是可覆写的方法** ✗ 得从属性设 ✓
                //   （原版玻璃就是这么写的 ✓ 光 .noOcclusion() 只管"不遮挡" ✗ 不管"挡不挡视线/闷不闷人" ✓）
                .isViewBlocking((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .sound(SoundType.AMETHYST)
                .mapColor(MapColor.NONE));
    }

    /**
     * §607c <b>轮廓（选中框）= 整格</b> ✓ —— 用户口径：「好歹那一格要让我能指上去的时候显示边框吧？
     * 整个空气我没法放置朝向啊」✓。
     *
     * <p>⚠ 关键区分：<b>轮廓（{@code getShape}）≠ 碰撞箱（{@code getCollisionShape}）</b> ✓
     * —— 这里给整格轮廓 ⇒ 准星**能选中它** ✓ 于是：
     * <ul>
     *   <li>指着它能看到一个方框 ✓（知道"这格是台座的" ✓）；</li>
     *   <li>能选到它的**六个面** ✓ ⇒ 对着它**朝下放漏斗**、或者把管道接进这一格都行 ✓
     *       （这就是用户说的"放置朝向" ✓ —— 没有轮廓就只能是空气 ⇒ 没有面可选 ✗）；</li>
     *   <li>碰撞箱仍然由 {@code noCollission()} 管着 ⇒ {@link #getCollisionShape} 依旧返回空 ✓
     *       ⇒ **走路站着跟以前一模一样** ✓（用户上一轮明确要求"碰撞箱不变" ✓）。</li>
     * </ul>
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    /** 碰撞箱保持**空** ✓（轮廓给了整格 ⇒ 这一条必须显式写 ✗ 不然就会变成"踩着能站上去" ✗） */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /**
     * §607c <b>右键转发给下面的台座</b> ✓ —— 因为现在准星会**先命中这一格**（不再是穿透过去 ✓），
     * 所以"对着悬浮的水晶右键放/取水晶"这个习惯动作必须还能用 ✓。
     * <p>做法：把这一下原样交给台座方块自己的 {@code use(...)} ✓（它那边已经有完整逻辑：
     * 台上有水晶就取回 ✓ 空着而手里是水晶就放上 ✓ 其它情况 PASS ✓）⇒ 不需要在两边各写一份 ✗。
     * <p>⚠ 手里拿的是普通方块时 ⇒ 台座那边返回 PASS ✓ 于是原版照常把方块放在**这一格的相邻位置** ✓
     * （不是放进这一格 ✗ 见 {@link #canBeReplaced} ✓）。
     */
    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (belowState.getBlock() instanceof ElderManaPedestalBlock) {
            return belowState.getBlock().use(belowState, level, below, player, hand, hit);
        }
        return net.minecraft.world.InteractionResult.PASS;
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
    /**
     * §607b <b>挡住建造</b> ✓ —— 用户实测反馈「它上方那一格还是能放置方块啊」✗ ⇒ 改掉那个"可被替换" ✓。
     * <p>即：这一格**真的属于台座** ✓ 你不能往里面塞方块/箱子/漏斗 ✗（要盖就先拆台座 ✓）。
     * <p>⚠ 漏斗/管道依然能用 ✓ —— 接法不是"放进这一格"✗ 而是：**上面一格朝下接** ✓ 或**用管道从侧面接进这一格** ✓。
     */
    @Override
    public boolean canBeReplaced(BlockState state, net.minecraft.world.item.context.BlockPlaceContext ctx) {
        return false;
    }

    /** 流体也不许流进来 ✓（这一格是台座的一部分 ⇒ 它不是空气 ✓） */
    @Override
    public boolean canBeReplaced(BlockState state, net.minecraft.world.level.material.Fluid fluid) {
        return false;
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
