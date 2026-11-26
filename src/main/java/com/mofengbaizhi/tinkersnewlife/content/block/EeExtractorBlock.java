package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * <b>EE 抽取方块</b>（{@code tinkersnewlife:ee_extractor}）的方块本体。
 *
 * <h2>⚠ §558 最新口径（与 §557 冲突时以本节为准）</h2>
 * <ul>
 *   <li>§557 写的是"<b>没有右键交互</b>、插上就工作" ✗ ⇒ <b>作废</b> ✓；</li>
 *   <li>现在：<b>右键打开 GUI</b> ✓（里面一个槽位，放有能量的水晶/水晶方块 ✓
 *       每 tick 从槽位抽进缓存、缓存再推给相邻方块 ✓ 详见 {@link EeExtractorBlockEntity}）；</li>
 *   <li>本类还是那三件事（形状 / 注册 / ticker）+ 多了一个 {@link #use} ✓。</li>
 * </ul>
 */
public class EeExtractorBlock extends BaseEntityBlock {

    /**
     * 碰撞/轮廓 = {@code models/block/ee_extractor.json} 那几段的并集 ✓（照 §527 台座的写法 ✓）：
     * 基座 2~14 × y0~2、机体 4~12 × y2~10（x/z 都是 2~14）、顶部结晶 5~11 × y10~14。
     * <p>⚠ 四角那四根"腿"（1 格见方）<b>没有</b>进形状 ✗ —— 它们纯粹是贴图上的装饰，
     * 让碰撞箱贴合"能站上去的那块实体"就够了 ✓（把腿也算进来只会让玩家在方块缝里卡住 ✗）。
     */
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
            net.minecraft.world.phys.shapes.Shapes.box(2.0D / 16.0D, 0.0D, 2.0D / 16.0D,
                    14.0D / 16.0D, 2.0D / 16.0D, 14.0D / 16.0D),
            net.minecraft.world.phys.shapes.Shapes.box(4.0D / 16.0D, 2.0D / 16.0D, 4.0D / 16.0D,
                    12.0D / 16.0D, 10.0D / 16.0D, 12.0D / 16.0D),
            net.minecraft.world.phys.shapes.Shapes.box(5.0D / 16.0D, 10.0D / 16.0D, 5.0D / 16.0D,
                    11.0D / 16.0D, 14.0D / 16.0D, 11.0D / 16.0D));

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }

    public EeExtractorBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(3.0F, 6.0F)
                .sound(SoundType.COPPER)
                .requiresCorrectToolForDrops()   // 已加进 minecraft:mineable/pickaxe 标签 ✓
                .mapColor(MapColor.COLOR_PURPLE));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;            // 普通方块模型 ✓ 不走 BER ✗
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EeExtractorBlockEntity(pos, state);
    }

    /** 服务端每 tick 驱动（从槽位抽 + 推给邻居 ✓）；客户端不挂 ticker ✗（数据是服务端权威的 ✓） */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.EE_EXTRACTOR.get(),
                EeExtractorBlockEntity::serverTick);
    }

    // ============================================================
    //  §558 右键打开 GUI
    // ============================================================

    /**
     * <b>右键 ⇒ 打开 GUI</b> ✓（用户 §558 口径）。
     *
     * <h2>为什么只有服务端那半在做事</h2>
     * 原版容器是服务端权威的 ✓：服务端 {@code openMenu} 会给客户端发
     * {@code ClientboundOpenScreenPacket}（含菜单类型 + 标题 + 附加数据 = 方块坐标 ✓）✓
     * 客户端收到后自己建一个菜单（走 {@code IForgeMenuType} 那条工厂 ✓ 见 {@code ModMenus}）✓
     * ⇒ <b>客户端这一半什么都不用做</b> ✗（在这里也调一次只会开出两个界面 ✗）。
     *
     * <h2>返回值</h2>
     * 服务端用 {@code sidedSuccess(true)} ✓（原版 {@code use} 的标准写法 ✓
     * 客户端那一半由原版流程自己给"成功"⇒ 挥手动画与声音正常 ✓）。
     *
     * <h2>⚠ 潜行门</h2>
     * 本方块<b>不需要</b>台座 §525 那个 {@code RightClickBlock ⇒ ALLOW} 的旁路 ✓ ——
     * 那条旁路是给"手里拿着东西 + 潜行时仍要取回台座上的水晶"用的 ✓
     * 而我们这里潜行与不潜行做的是同一件事（开 GUI）✓ 没有"取不下/放不上"的语义 ✗
     * ⇒ 交给原版判断即可 ✓（少挂一个事件 = 少一处能坏的地方 ✓）。
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;                 // 客户端只负责"挥手成功" ✓
        }
        if (level.getBlockEntity(pos) instanceof EeExtractorBlockEntity extractor
                && player instanceof ServerPlayer serverPlayer) {
            extractor.useGui(serverPlayer);                   // 服务端开界面（附带方块坐标 ✓）
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;                        // 方块实体没就绪（理论上不会）⇒ 不硬来 ✗
    }

    // ============================================================
    //  §558 挖掉方块：槽位里那件一起还回来
    // ============================================================

    /**
     * 掉落 = 战利品表（方块自己）+ <b>槽位里那一件（连它剩下的 EE ✓）</b>。
     *
     * <p>做法与台座 §523 的 {@code getDrops} <b>逐字同一套</b> ✓：借
     * {@code LootContextParams.BLOCK_ENTITY} 拿到方块实体 ✓
     * ⇒ <b>玩家挖、爆炸、别的模组拆</b>都走同一条路 ✓（只挂 {@code playerWillDestroy} 覆盖面不够 ✗，
     * 而两个都挂会让玩家挖时掉两份 ✗）。
     * <p>⚠ 这就是"放进去一颗有电的水晶、拆掉方块"时玩家的东西不会丢的保证 ✓。
     */
    @Override
    @SuppressWarnings("deprecation")
    public java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        java.util.List<net.minecraft.world.item.ItemStack> drops =
                new java.util.ArrayList<>(super.getDrops(state, params));
        if (params.getOptionalParameter(
                net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY)
                instanceof EeExtractorBlockEntity extractor) {
            net.minecraft.world.item.ItemStack left = extractor.takeSlotForDrop();
            if (!left.isEmpty()) drops.add(left);
        }
        return drops;
    }
}
