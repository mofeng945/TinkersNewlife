package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.Set;

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
 *   <li><b>只传送玩家</b>，怪物／掉落物直接穿过；</li>
 *   <li><b>防乒乓</b>：冷却记在玩家持久化数据里（{@link WhiteSpaceDimensions#armCooldown}）。
 *       因为门有 2 格高，玩家碰撞箱常常<b>同时压住两格</b> ⇒ {@code entityInside} 一 tick 会被调两次 ——
 *       第一次就把冷却装上了，第二次直接被顶掉 ⇒ 不会传送两次 ✓；</li>
 *   <li><b>落点自愈</b>（§661）＋ <b>脚下没落脚点就铺 3×3 黑曜石平台</b>（§663 用户新口径，见
 *       {@link WhiteSpaceDimensions#resolveLanding} 与 {@code ensurePlatform}）。</li>
 * </ul>
 */
public class WhiteSpacePortalBlock extends Block implements EntityBlock {

    /** 下半（主体）／上半（副体）：两格都是我们的方块，共同组成一扇门 */
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    /** 门的朝向（那片平面的法线）；开门时按玩家水平朝向写入 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

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
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, FACING);
    }

    /** 同一扇门的另一格 */
    public static BlockPos otherHalf(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
    }

    /**
     * 万一被别的途径（{@code /setblock} 之类）放置：只允许当作下半，且上方要放得下，
     * 朝向取玩家朝向（与开门时同一条规则）。
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (!context.getLevel().getBlockState(pos.above()).canBeReplaced(context)) return null;
        return defaultBlockState()
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(FACING, context.getHorizontalDirection());
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
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) return;
        if (WhiteSpaceDimensions.isOnCooldown(player)) return;
        if (!(level.getBlockEntity(pos) instanceof WhiteSpacePortalBlockEntity portal)) return;
        if (!portal.hasDestination()) return;

        ResourceKey<Level> destKey = portal.getDestinationDimension();
        if (destKey == null) return;
        MinecraftServer server = player.getServer();
        ServerLevel target = server == null ? null : server.getLevel(destKey);
        if (target == null) return;

        /*
         * §661「传送门会自动记录相对落点，以便往返」：
         * 落点上那扇门要是被拆了／被炸了，只要落点还是空地就按记录把门补回来并指回这里 ✓。
         * §663：补出来的门朝向按"正在走进去的这个玩家"的朝向 ⇒ 落地时门也是横在面前的 ✓。
         * 整段都被实体方块占住时返回 null ⇒ 宁可不传，也不把玩家塞进方块里 ✗。
         */
        BlockPos landing = WhiteSpaceDimensions.resolveLanding(
                target, portal.getDestinationPos(), level.dimension(), pos, player.getDirection());
        if (landing == null) {
            WhiteSpaceDimensions.armCooldown(player, 20);
            player.displayClientMessage(
                    Component.translatable("message.tinkersnewlife.white_space.landing_blocked"), true);
            return;
        }

        // §663 用户口径：传送出来脚底下没有落点 ⇒ 自动铺一块 3×3 黑曜石平台
        WhiteSpaceDimensions.ensurePlatform(target, landing);

        // 先上冷却：落地时人就在对面那扇门里，没冷却会立刻被送回来
        WhiteSpaceDimensions.armCooldown(player, WhiteSpaceDimensions.ARRIVE_COOLDOWN);
        try {
            player.teleportTo(target, landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D,
                    Set.of(), player.getYRot(), player.getXRot());
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[伟大白色空间] 跨维度传送异常：{}", t.toString());
        }
        if (player.serverLevel() != target) {
            // 被别的模组拦了（Forge 的 EntityTravelToDimensionEvent 是可以被取消的）⇒ 1 秒后再试
            WhiteSpaceDimensions.armCooldown(player, 20);
            player.displayClientMessage(
                    Component.translatable("message.tinkersnewlife.white_space.travel_blocked"), true);
        }
    }
}
