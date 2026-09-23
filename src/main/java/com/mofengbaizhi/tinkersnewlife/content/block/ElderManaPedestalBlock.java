package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>魔力台座</b>：把古老者水晶放上去，它就会<b>吸收周围环境能量给水晶充能</b> ✓
 * （用户口径：水晶<b>不会</b>自动充能 ⇒ "必须放在魔力台座上吸收周围能量才充电" ✓）。
 *
 * <h2>怎么用（§525 定稿规则）</h2>
 * <ul>
 *   <li><b>取</b>：台座上有水晶时 <b>右键就取</b> —— <b>不用空手、不用潜行、手里拿什么都不影响</b> ✓
 *       （台座被占用时"放"本来就不合法 ⇒ 这一下右键只可能是"取" ⇒ 不需要任何前置条件 ✓）；</li>
 *   <li><b>放</b>：台座空着 + 手里拿着 {@code tinkersnewlife:elder_crystal} + 右键 ⇒ 放上去 <b>1 个</b>
 *       （一次只放一个 ✓ 剩下的留在手里 ✓）；</li>
 *   <li>台座上已经有水晶、手里又拿着水晶 ⇒ 右键 = <b>取回</b>（不交换、不叠加 ✗ 免得手滑丢电；
 *       想换一颗就"先取下来、再放上去" ✓）；</li>
 *   <li>其它情况（台座空着 + 手里不是水晶 / 空手 / 手里是水晶方块）⇒ <b>什么都不做</b>（{@code PASS} ✓）；</li>
 *   <li>台座 <b>上方/下方/四邻</b> 的<b>古老者水晶方块</b>也会被一起充能 ✓（容量 4000 ✓）；</li>
 *   <li>充能规律 = <b>亮度越低越快</b>（默认满速 5 EE/秒；公式在 {@code content/energy/LightLevelEnergySource}）✓。</li>
 * </ul>
 *
 * <h2>⚠ 为什么必须挂 {@code RightClickBlock} 事件（§525 查出的真因）</h2>
 * 原版 {@code ServerPlayerGameMode#useItemOn} 在调用 {@code Block#use} 之前有<b>一道潜行门</b>
 * （1.20.1-47.4.22 源码 347~351 行，客户端 {@code MultiPlayerGameMode#performUseItemOn} 318~326 行同款）：
 * <pre>
 * boolean flag  = !主手.isEmpty() || !副手.isEmpty();
 * boolean flag1 = player.isSecondaryUseActive() &amp;&amp; flag;        // 潜行 且 任一只手拿着东西
 * if (event.getUseBlock() == ALLOW || (... &amp;&amp; !flag1)) { blockstate.use(...); }   // 否则连 use 都不会调用 ✗
 * </pre>
 * ⇒ §523 写进 tooltip 与手册的"<b>潜行右击取回</b>"<b>从来没有生效过</b> ✗：潜行时只要手里拿着东西
 * （放完水晶剩下的那一叠 / 镐子 / 火把…），台座的 {@code use} <b>根本不被调用</b>
 * ⇒ 玩家看到的就是"<b>取不下、也放不上去、悬浮水晶一直挂着</b>"✗。
 * <p>修法 = 本类末尾的 {@link InteractionGate}：监听 {@code PlayerInteractEvent.RightClickBlock}
 * （<b>在原版那道门之前</b>触发，两端都触发 ✓），只对本方块 {@code setUseBlock(ALLOW)}
 * ⇒ 潜行与非潜行<b>行为完全一致</b> ✓。同一套做法在本仓库已有一处先例并写过同样的原因：
 * {@code content/block/CurseVaultInteractionHandler}（呪蔵）✓。
 *
 * <h2>挖掉台座：水晶不会丢</h2>
 * 走 {@link #getDrops} 把台座上的水晶（连它 NBT 里的 EE）追加进掉落物 ✓
 * —— 借 loot table 的 {@code LootContextParams.BLOCK_ENTITY} 参数拿方块实体 ✓，
 * 于是<b>玩家挖、爆炸炸</b>都走同一条路 ✓（比只挂 {@code playerWillDestroy} 覆盖面更全 ✓，
 * 而这里<b>不能</b>再挂 {@code playerWillDestroy} ⇒ 玩家挖会掉两份 ✗）。
 *
 * <h2>⚠ 台座刻意不发光</h2>
 * 见 {@link ElderManaPedestalBlockEntity} 的类注释：自己发光会抬高上方那格的亮度 ⇒
 * 按"亮度越低越快"的规则反过来拖慢自己 ✗。"正在充能"的表现交给冷色粒子 + 轻微风铃 ✓。
 */
public class ElderManaPedestalBlock extends BaseEntityBlock {

    /**
     * §527 **碰撞/轮廓形状 = 模型三段的并集**（用户口径：「让台座碰撞箱贴合它的模型」✓）。
     * <p>模型（`models/block/elder_mana_pedestal.json`）就是这三段 ⇒ 这里逐段照抄 ✓：
     * 底座 16×16×3、立柱 x5~11 × y3~11、台面 x3~13 × y11~13。
     * <p>⇒ 玩家可以**站在台面上**、也能**贴着立柱走**（不再是一整格实心方块 ✗）；
     * 覆写 `getShape` 就同时管**轮廓（选中框）**与**碰撞**（原版 `getCollisionShape` 默认取它 ✓）。
     */
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
            net.minecraft.world.phys.shapes.Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 3.0D / 16.0D, 1.0D),
            net.minecraft.world.phys.shapes.Shapes.box(5.0D / 16.0D, 3.0D / 16.0D, 5.0D / 16.0D,
                    11.0D / 16.0D, 11.0D / 16.0D, 11.0D / 16.0D),
            net.minecraft.world.phys.shapes.Shapes.box(3.0D / 16.0D, 11.0D / 16.0D, 3.0D / 16.0D,
                    13.0D / 16.0D, 13.0D / 16.0D, 13.0D / 16.0D));

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }

    public ElderManaPedestalBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(3.0F, 6.0F)
                .sound(SoundType.AMETHYST)
                .requiresCorrectToolForDrops()   // 已在 minecraft:mineable/pickaxe 标签里 ✓
                .mapColor(MapColor.COLOR_PURPLE));
    }

    // ============================================================
    //  方块实体
    // ============================================================

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // 台座本体是普通方块模型 ✓（浮在上面的水晶由 BER 画 ✓ 见 client/renderer/ElderManaPedestalRenderer）
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElderManaPedestalBlockEntity(pos, state);
    }

    /** 服务端每 tick 驱动（充能结算 ✓）；客户端不挂 ticker ✗（数据是服务端权威的 ✓） */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.ELDER_MANA_PEDESTAL.get(),
                ElderManaPedestalBlockEntity::serverTick);
    }

    // ============================================================
    //  §607 两格高结构：放下时补"上段"，拆掉时带走它
    //  ⚠ 这两件事**必须写在台座身上** ✗ —— 写到上段自己身上会**无限叠塔** ✗
    //     （setBlock 放上段会触发它自己的 onPlace ✓ 见 ElderManaPedestalTopBlock 里的注释 ✓）
    // ============================================================

    /** 台座放下 ⇒ 上面那格若是空气就补上段 ✓（那格已经有东西 ⇒ 不动 ✗） */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (level.isClientSide) return;
        ElderManaPedestalTopBlock.ensureTop(level, pos);
    }

    /** 台座没了 ⇒ 上段跟着走 ✓（只删"确实是上段"的那一格 ✓ 玩家放的东西不动 ✗） */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockPos above = pos.above();
            if (level.getBlockState(above).getBlock() instanceof ElderManaPedestalTopBlock) {
                level.removeBlock(above, false);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // ============================================================
    //  放 / 取 水晶
    // ============================================================

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ElderManaPedestalBlockEntity pedestal)) {
            return InteractionResult.PASS;   // 方块实体没就绪（理论上不会）⇒ 不硬来 ✗
        }

        // ① 取回：台座上有水晶 ⇒ 这一个右键就是"取"（手里拿什么、有没有潜行都不影响 ✓ 见类注释 §525）
        if (pedestal.hasCrystal()) {
            if (!level.isClientSide) {
                // takeCrystal 内部：**先**把方块实体的栈无条件清成 EMPTY、**再**同步（顺序不能反 ✓）
                // ⇒ 客户端 BER 立刻不再画那颗水晶 ✓（见 ElderManaPedestalBlockEntity#takeCrystal）
                ItemStack taken = pedestal.takeCrystal();
                // "清空"在这一行之前**已经做完**了 ⇒ 下面这段是"交还给玩家"，成败都不影响台座已经空了 ✓
                if (!taken.isEmpty() && !player.getInventory().add(taken)) {
                    player.drop(taken, false);   // 背包满 ⇒ 掉在脚下，绝不凭空消失 ✗
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // ② 放上去：能走到这里 ⇒ 台座一定是空的 ✓；手里正好拿着水晶 ⇒ 放 1 个（剩下的留在手里 ✓）
        ItemStack held = player.getItemInHand(hand);
        if (isElderCrystal(held)) {
            if (!level.isClientSide) {
                ItemStack one = held.copy();
                one.setCount(1);
                pedestal.setCrystal(one);
                held.shrink(1);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // ③ 台座空着、手里又不是古老者水晶（空手 / 别的物品 / 水晶方块）⇒ 什么都不做 ✓
        return InteractionResult.PASS;
    }

    /** 手里拿着的是古老者水晶**物品**吗（水晶方块物品不放台座 ✗ —— 方块直接摆在地上也能被充能 ✓） */
    private static boolean isElderCrystal(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() == ModItems.ELDER_CRYSTAL.get()
                || stack.getItem() == ModItems.ELDER_CRYSTAL_BLOCK.get());   // §555 水晶方块也能悬浮放上去 ✓（容量 4000 ✓）
    }

    // ============================================================
    //  挖掉：把台座上的水晶一起还回来
    // ============================================================

    /**
     * 掉落 = 战利品表（台座自己）+ 台座上的水晶（连 EE ✓）。
     * <p>{@code getDrops} 在 1.20.1 标了 {@code @Deprecated}，但它依然是"所有破坏途径"（玩家挖 / 爆炸 /
     * 其它模组拆方块）共用的那一个钩子 ✓ 所以继续用它 ✓。
     */
    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = new ArrayList<>(super.getDrops(state, params));
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof ElderManaPedestalBlockEntity pedestal && pedestal.hasCrystal()) {
            // 台座上只可能是一颗古老者水晶物品 ⇒ 整栈搬走（EE 在它的 NBT 里，一点不丢 ✓）
            drops.add(pedestal.getCrystal().copy());
        }
        return drops;
    }

    // ============================================================
    //  潜行门旁路（§525）：台座的交互不该被原版的"潜行 + 手里有东西"挡掉
    // ============================================================

    /**
     * 只做一件事：右键点的是<b>本方块</b>时，把 {@code UseBlock} 置成 {@code ALLOW}。
     *
     * <h2>为什么需要它</h2>
     * 见类注释：原版 {@code ServerPlayerGameMode#useItemOn}（客户端 {@code performUseItemOn} 同款）
     * 在调用 {@code Block#use} 前有一道门 —— <b>潜行且任一只手拿着东西</b>时直接跳过 {@code use}
     * ⇒ §523 承诺的"潜行右击取回"从未生效 ✗（表现：取不下 / 放不上去 / 悬浮水晶不消失）。
     * {@code setUseBlock(ALLOW)} 是 Forge 给这条路留的正规开关（原版那一行就是
     * {@code event.getUseBlock() == ALLOW || (... && !flag1)} ✓）⇒ 潜行与非潜行<b>完全一致</b> ✓。
     *
     * <h2>为什么用 ALLOW 而不是在这里自己放/取</h2>
     * 放/取逻辑只保留 {@link #use} <b>一份</b> ✓（ALLOW 只是"把原版那道门拆掉"，剩下交给原版流程
     * ⇒ 客户端预测、挥手动画、发包数量与"不潜行时"逐字一致 ✓ 不会出现两端各做一半 ✗）。
     *
     * <h2>边界</h2>
     * <ul>
     *   <li>被别人 {@code DENY} 了（保护类模组禁止交互）⇒ <b>不抢</b>，直接放行 ✓；</li>
     *   <li>两端都会触发本事件（客户端在 {@code performUseItemOn} 里也发一次 ✓）
     *       ⇒ 两边都拿到 ALLOW，客户端预测与服务端结算口径一致 ✓；</li>
     *   <li>只认本方块 ⇒ 对台座以外的一切右键<b>零影响</b> ✓（不碰玩家的手持物品、不碰潜行语义）。</li>
     * </ul>
     * <p>同一套做法在本仓库已有一处先例并写过同样的原因：{@code content/block/CurseVaultInteractionHandler} ✓。
     */
    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class InteractionGate {

        private InteractionGate() {
        }

        @SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            if (event.getUseBlock() == Event.Result.DENY) return;   // 别人明确禁止 ⇒ 不抢 ✗
            if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof ElderManaPedestalBlock)) {
                return;                                             // 不是台座 ⇒ 一点不碰 ✓
            }
            event.setUseBlock(Event.Result.ALLOW);                  // 拆掉潜行门 ⇒ 潜行 = 非潜行 ✓
        }
    }
}
