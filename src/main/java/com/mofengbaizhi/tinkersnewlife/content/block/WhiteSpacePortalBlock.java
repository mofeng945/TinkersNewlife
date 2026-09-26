package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.portal.WhiteSpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>伟大白色空间传送门</b>（§660／§661／§663）。
 *
 * <h2>§663：做成 <b>2 格高</b>的门（用户口径：「做成 2 格高，类似铁魔法传送门法术那样的效果」）</h2>
 * 门是 <b>1 宽 × 2 高</b>的一对立柱方块：{@link #HALF} 分下半（主体）与上半（副体），
 * 两格各自带一份<b>相同</b>的目的地（{@code WhiteSpacePortalBlockEntity}）⇒ 从哪一格走进去都能传送 ✓。
 * <ul>
 *   <li><b>外观</b>：不再是实心发光方块，而是<b>一片竖着的半透明平面</b> ——
 *       模型 {@code white_space_portal_lower/_upper.json} 画的是 1 单位厚的薄片，
 *       下半用动态贴图 {@code white_space_portal_gate.png} 的<b>下半个画面</b>、
 *       上半用<b>上半个画面</b>（模型 UV 各取 v=8..16 / v=0..8）⇒ 两格拼成一整幅 1×2 的椭圆漩涡 ✓。</li>
 *   <li><b>动画</b>：贴图是 <b>16×256 ＝ 8 帧 16×32</b> 的动画贴图（配 {@code .png.mcmeta}），
 *       8 帧转一圈 ⇒ 漩涡持续旋转。这样<b>完全不需要方块实体渲染器</b>（零代码风险）✓。
 *       ⚠ 帧尺寸必须显式写 {@code "width":16,"height":32} —— 原版
 *       {@code AnimationMetadataSection.calculateFrameSize} 在宽高都没给时按
 *       {@code min(w,h)} 取正方形（16×256 会被当成 16×16 的 16 帧 ⇒ 画面被切碎 ✗）。</li>
 *   <li><b>朝向</b>：{@link #FACING} 决定这片平面的法线，开门时取玩家的水平朝向 ⇒
 *       门正好横在你面前，走进去是"穿过去"而不是"擦过去" ✓。属性只有渲染意义，
 *       {@code entityInside} 用整格 AABB ⇒ 朝向不影响传送判定 ✓。</li>
 * </ul>
 *
 * <h2>其余行为（§660／§661 原有口径，全部保留）</h2>
 * <ul>
 *   <li><b>无碰撞、不挡视线、自发光 15</b>；
 *       <b>§664 起硬度 -1（正常方式挖不动、炸不掉、推不动）</b>，
 *       只能拿<b>天逆鉾右键</b>拆（见 {@link #use}，会响一声玻璃碎）；
 *       因为拆不掉，所以也没有战利品表这件事本身就没意义了 ✓；</li>
 *   <li><b>拆一格＝整扇门消失</b>：{@link #onRemove} 会把另一半也清掉，不会留下半扇门 ✗
 *       （爆炸／活塞／指令也走这条路 ✓）；</li>
 *   <li><b>§668 起：选中轮廓在客户端被<b>完全隐藏</b></b>（用户口径「让选中框基本看不出来」）。
 *       ⚠ 注意这里<b>没有</b>去改 {@code getShape} —— 因为准星拾取和轮廓用的是<b>同一个</b>形状
 *       （{@code ClipContext.Block.OUTLINE} ⇒ {@code getShape}），改小它就会让天逆鉾右键一起变难点中 ✗。
 *       实际做法是在客户端取消 {@code RenderHighlightEvent.Block}，见
 *       {@code client/handler/WhiteSpacePortalOutlineHider}；
 *       ⇒ {@code getShape} 依旧是整格 ⇒ <b>准星锁得住、右键敲得碎</b>，只是那条框不画了 ✓；</li>
 *   <li><b>§666 起：玩家与其它生物都会被传送</b>（用户口径「不要只能传送玩家，其他生物也应当可以被传送」）。
 *       非生物实体（掉落物、船、箭这类）仍然直接穿过；生物连同它的乘客一起送过去 ✓；</li>
 *   <li><b>防乒乓</b>：冷却记在实体自己的持久化数据里（{@link WhiteSpaceDimensions#armCooldown}）。
 *       门有 2 格高，实体碰撞箱常常<b>同时压住两格</b> ⇒ {@code entityInside} 一 tick 会被调两次 ——
 *       第一次就把冷却装上了，第二次直接被顶掉 ⇒ 不会传送两次 ✓。
 *       ⚠ 跨维度传送对生物是"<b>新建一个实体并拷 NBT</b>"（{@code Entity#restoreFrom} 走
 *       {@code saveWithoutId}/{@code load}）⇒ <b>ForgeData 会被继承</b>，
 *       所以"传送前先装冷却"能跟着新实体过去，落地就在对面门里也不会被立刻弹回来 ✓；</li>
 *   <li><b>落点自愈</b>（§661）＋ <b>脚下没落脚点就铺 3×3 黑曜石平台</b>（§663 用户新口径，见
 *       {@link WhiteSpaceDimensions#resolveLanding} 与 {@code ensurePlatform}）。</li>
 * </ul>
 *
 * <h2>§666：为什么门有白／黑两副贴图</h2>
 * 用户口径：「材质纹理建议在<b>其他维度为白色</b>，在<b>伟大白色空间</b>的传送门为<b>黑色</b>」——
 * 因为白色空间里到处都是白的，白门几乎看不见 ✓。
 * 实现方式是<b>方块状态属性 {@link #DARK}</b> ＋ 两套模型：
 * {@code dark=false} 用 {@code white_space_portal_*_gate}（白），
 * {@code dark=true} 用 {@code ..._gate_dark}（近黑＋淡银描边）。
 * 这个值<b>由"门被放在哪个维度"决定</b>（{@link WhiteSpaceDimensions} 里
 * {@code isWhiteSpace(level)}），每次写目的地时都会顺手校正一遍 ⇒
 * 旧存档里颜色不对的门、或者被 {@code /setblock} 放错维度的门，下次被碰一下就会自己变对 ✓。
 * 用状态属性而不是方块实体渲染器 ⇒ <b>不需要任何渲染代码</b> ✓。
 */
public class WhiteSpacePortalBlock extends Block implements EntityBlock {

    /** 下半（主体）／上半（副体）：两格都是我们的方块，共同组成一扇门 */
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    /** 门的朝向（那片平面的法线）；开门时按玩家水平朝向写入 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * §666 黑白变体：{@code false} ＝ 白门（白色空间之外用），{@code true} ＝ 黑门（伟大白色空间里用）。
     * <p>由"门所在维度"自动决定，不是玩家选项 ✓（见 {@link WhiteSpaceDimensions#placePortal}）。
     */
    public static final BooleanProperty DARK = BooleanProperty.create("dark");

    public WhiteSpacePortalBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.SNOW)
                // §664 用户口径：正常方式**无法挖掘**（硬度 -1，同基岩）——
                // 抗爆 3600000 ⇒ 爆炸也炸不掉；硬度 -1 ⇒ 活塞推不动；
                // 想拆只能用天逆鉾右键（见 use），会响一声玻璃碎 ✓
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.GLASS)
                .lightLevel(state -> 15)
                .noCollission()
                .noOcclusion()
                .isViewBlocking((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any()
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(FACING, Direction.NORTH)
                .setValue(DARK, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, FACING, DARK);
    }

    /** 同一扇门的另一格 */
    public static BlockPos otherHalf(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
    }

    /**
     * 万一被别的途径（{@code /setblock} 之类）放置：只允许当作下半，且上方要放得下，
     * 朝向取玩家朝向；黑白按"放在哪个维度"决定（与开门时同一条规则）。
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (!context.getLevel().getBlockState(pos.above()).canBeReplaced(context)) return null;
        return defaultBlockState()
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(FACING, context.getHorizontalDirection())
                .setValue(DARK, WhiteSpaceDimensions.isWhiteSpace(context.getLevel()));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WhiteSpacePortalBlockEntity(pos, state);
    }

    /** 天空光能透下来（同玻璃）—— 免得白色空间里那层门在地面上投出一块暗斑 */
    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    /**
     * 拆掉任意一格 ⇒ 把另一格也清掉（用户口径是"一扇门"，不该留下半扇）。
     * <p>放在 {@code onRemove} 而不是 {@code playerWillDestroy}：这样<b>爆炸／活塞／指令</b>
     * 拆门时同样会清干净 ✓。用 {@code removeBlock}（不是 {@code destroyBlock}）⇒ 不会再走一次
     * 破坏/掉落流程，也就不会递归 ✓ —— 第二格进 {@code onRemove} 时第一格已经是空气，
     * {@code getBlockState(other).is(this)} 直接不成立 ✓。
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            BlockPos other = otherHalf(state, pos);
            if (level.getBlockState(other).is(this)) {
                level.removeBlock(other, false);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** 客户端气氛：偶尔飘几颗白色光点（贴图自己会转，这里只补一层粒子） */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) != 0) return;
        double x = pos.getX() + random.nextDouble();
        double y = pos.getY() + random.nextDouble();
        double z = pos.getZ() + random.nextDouble();
        level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0D, 0.01D, 0.0D);
    }

    // ============================================================
    //  拆除（§664：只认天逆鉾）
    // ============================================================

    /**
     * <b>§664 用户口径</b>：「让传送门方块用正常方式无法触及挖掘，只有使用**天逆鉾**右键时才会拆除
     * 并发出**玻璃破碎音效**」。
     *
     * <p>门本身硬度 -1（见构造器）⇒ 挖不动、炸不掉、推不动；这里给天逆鉾开一个"合法拆除"的口子：
     * <ul>
     *   <li>手里的东西不是天逆鉾 ⇒ {@code PASS}（照常把右键让给别的逻辑，不拦 ✓）；</li>
     *   <li>是天逆鉾 ⇒ 服务端把<b>整扇门（上下两格）一起拆掉</b>，并在门的位置播
     *       {@link SoundEvents#GLASS_BREAK}（音量 1.0／音高 0.9，略低一点更像"碎了一整片"）✓；</li>
     *   <li>天逆鉾本身<b>不消耗、不掉耐久</b>（用户没提，就不动它 ✓）。</li>
     * </ul>
     * ⚠ 音效只在<b>服务端</b>播（{@code level.playSound(null, ...)}）⇒ 由服务端广播给附近所有玩家 ✓
     * 不会出现"自己听两遍"或"别人听不到"✗。
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).is(ModItems.TIAN_NI_HUO.get())) return InteractionResult.PASS;
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            WhiteSpaceDimensions.removePortal(serverLevel, pos);
            level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 0.9F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // ============================================================
    //  传送
    // ============================================================

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide()) return;
        /*
         * §666 用户口径：「不要只能传送玩家，其他生物也应当可以被传送」。
         * ⇒ 生物（LivingEntity）全部可传；非生物实体（掉落物／船／箭…）依旧直接穿过 —— 用户说的是"生物" ✓。
         */
        if (!(entity instanceof LivingEntity)) return;
        if (WhiteSpaceDimensions.isOnCooldown(entity)) return;
        if (!(level.getBlockEntity(pos) instanceof WhiteSpacePortalBlockEntity portal)) return;
        if (!portal.hasDestination()) return;

        ResourceKey<Level> destKey = portal.getDestinationDimension();
        if (destKey == null) return;
        MinecraftServer server = entity.getServer();
        ServerLevel target = server == null ? null : server.getLevel(destKey);
        if (target == null) return;

        /*
         * §661「传送门会自动记录相对落点，以便往返」：
         * 落点上那扇门要是被拆了／被炸了，只要落点还是空地就按记录把门补回来并指回这里 ✓。
         * §663：补出来的门朝向按"正在走进去的这个实体"的朝向 ⇒ 落地时门也是横在面前的 ✓。
         * 整段都被实体方块占住时返回 null ⇒ 宁可不传，也不把生物塞进方块里 ✗。
         */
        BlockPos landing = WhiteSpaceDimensions.resolveLanding(
                target, portal.getDestinationPos(), level.dimension(), pos, entity.getDirection());
        if (landing == null) {
            WhiteSpaceDimensions.armCooldown(entity, 20);
            if (entity instanceof ServerPlayer player) {
                player.displayClientMessage(
                        Component.translatable("message.tinkersnewlife.white_space.landing_blocked"), true);
            }
            return;
        }

        // §663 用户口径：传送出来脚底下没有落点 ⇒ 自动铺一块 3×3 黑曜石平台
        WhiteSpaceDimensions.ensurePlatform(target, landing);

        /*
         * 连同"这一串"一起送：跨维度传送内部会 unRide()，只送坐骑会把乘客留在原地
         * （玩家骑着生物进门时尤其明显）✗ ⇒ 先把乘客放下来、再逐个送到同一个落点 ✓。
         */
        List<Entity> stack = new ArrayList<>(entity.getSelfAndPassengers().toList());
        if (!entity.getPassengers().isEmpty()) entity.ejectPassengers();

        boolean ok = false;
        for (Entity member : stack) {
            /*
             * 先上冷却再传：生物的跨维度传送是"新建实体 + 拷 NBT"
             * （Entity#restoreFrom → saveWithoutId / load）⇒ ForgeData 会被继承 ✓
             * ⇒ 冷却跟着新实体过去，落地就在对面那扇门里也不会被立刻弹回来 ✓。
             */
            WhiteSpaceDimensions.armCooldown(member, WhiteSpaceDimensions.ARRIVE_COOLDOWN);
            boolean memberOk = WhiteSpaceDimensions.teleportEntity(member, target, landing);
            /*
             * ⚠ 玩家侧 ServerPlayer#teleportTo 恒返回 true（被 Forge 的 EntityTravelToDimensionEvent
             * 取消时也只是什么都不做）⇒ 必须再复核一次维度；生物侧 Entity#teleportTo 的返回值可靠 ✓。
             */
            if (member instanceof ServerPlayer player) {
                memberOk = memberOk && player.serverLevel() == target;
            }
            if (member == entity) ok = memberOk;
        }

        if (!ok) {
            // 被别的模组拦了（Forge 的 EntityTravelToDimensionEvent 是可以被取消的）⇒ 1 秒后再试
            WhiteSpaceDimensions.armCooldown(entity, 20);
            if (entity instanceof ServerPlayer player) {
                player.displayClientMessage(
                        Component.translatable("message.tinkersnewlife.white_space.travel_blocked"), true);
            }
        }
    }
}
