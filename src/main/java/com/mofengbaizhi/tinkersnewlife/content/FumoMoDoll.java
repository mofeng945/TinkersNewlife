package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>墨封白织fufu</b>（§876 用户口径 ✓）：仿"森罗物语：玩偶 / 应用机动玩偶"的做法 ——
 * <b>具名多部件的原版方块模型</b>（零自定义渲染代码 ✓）＋ 放下后<b>右键摸头出声</b> ✓
 * ＋ 单独一个创造物品栏页 ✓。
 *
 * <p>造型：32×32 贴图（由墨默的 64×64 模型皮肤转出 ✓）＋ 头 8×8×8、身体 6×5×4、两条腿与两条手臂 ✓
 * ⇒ 头大身小的娃娃比例 ✓（坐标照 Blockbench 那种精确到 0.5 格的手法 ✓）。
 *
 * <p>音效：用户提供的抚摸声 ✓（`assets/tinkersnewlife/sounds/block/fumo_mo_touch.ogg` ✓）
 * 注册成 {@code tinkersnewlife:block.fumo_mo_touch} ✓，右键时按随机音调播放 ✓（每次摸都有点不一样 ✓）。
 *
 * <p>⚠ 本类**自成一体**（自己开 DeferredRegister ＋ 自己的创造栏）✓ ⇒
 * 不需要改动 {@code ModBlocks/ModItems/ModCreativeTabs} ✗（要挪进现有页签再说 ✓）。
 * <p>📌 尚未做（下一步 ✓）：curios 头部槽位（能戴头上）＋ 墨默好感度 30 自动赠送。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FumoMoDoll {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TinkersNewlife.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TinkersNewlife.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, TinkersNewlife.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TinkersNewlife.MOD_ID);

    /** 抚摸音效（用户的 duck_toy.ogg 收进来的那份 ✓） */
    public static final RegistryObject<SoundEvent> TOUCH_SOUND = SOUNDS.register("block.fumo_mo_touch",
            () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(TinkersNewlife.MOD_ID, "block.fumo_mo_touch")));

    public static final RegistryObject<Block> FUMO_MO = BLOCKS.register("fumo_mo", FumoMoBlock::new);
    /** 玩偶方块实体（§880：世界里的玩偶走"玩家模型渲染" ✓ 不再用方块模型 ✓） */
    public static final RegistryObject<BlockEntityType<FumoMoBlockEntity>> FUMO_MO_BE =
            BLOCK_ENTITIES.register("fumo_mo",
                    () -> BlockEntityType.Builder.of(FumoMoBlockEntity::new, FUMO_MO.get()).build(null));
    public static final RegistryObject<Item> FUMO_MO_ITEM = ITEMS.register("fumo_mo",
            () -> new FumoMoItem(FUMO_MO.get(), new Item.Properties()));

    static {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        SOUNDS.register(bus);
        // §905：原先这里还注册了一页"fumo_mo"独立创造栏 ⇒ 已并入主创造栏（ModCreativeTabs ✓）后撤掉 ✓
        BLOCK_ENTITIES.register(bus);
    }

    private FumoMoDoll() {}

    /** 每个玩家上一次摸头的时间（毫秒 ✓ 防连点刷音效 ✓） */
    private static final Map<UUID, Long> LAST_TOUCH = new ConcurrentHashMap<>();

    /**
     * 玩偶的碰撞箱：只占中间一小块（四周还能贴着走过去 ✓）——朝向南北时用这个 ✓。
     * <p>§907 高度从 0.875 提到 **1.0**：玩偶放大到约 0.98 格高之后，
     * 头顶会超原来的箱子 ⇒ 右键"摸头"点不到 ✗（{@code use()} 要求命中碰撞箱 ✓）⇒ 跟着长高 ✓。
     * x/z 保持原来的小一圈 ✓（不影响点头顶 ✓，也保留"能贴着走过去"的手感 ✓）。
     */
    private static final VoxelShape SHAPE_Z = Shapes.box(0.25D, 0.0D, 0.3125D, 0.75D, 1.0D, 0.6875D);
    /** §906 朝向东西时把 x/z 对调（玩偶本身比较"扁" ✓ 碰撞箱跟着转 ✓） */
    private static final VoxelShape SHAPE_X = Shapes.box(0.3125D, 0.0D, 0.25D, 0.6875D, 1.0D, 0.75D);

    /** 玩偶方块：右键＝摸头 ✓ */
    public static class FumoMoBlock extends Block implements net.minecraft.world.level.block.EntityBlock {

        /**
         * §906 <b>朝向</b>：玩偶**有正面**（脸朝向的那一面 ✓）⇒ 放置时像箱子一样定朝向 ✓，
         * 渲染器按这个值转（否则永远只朝一个方向 ✗ 用户实测 ✓）。
         * <p>口径：{@code FACING} = <b>玩偶正面指向的方向</b>；
         * 放置时取「玩家水平朝向的**反方向**」⇒ 放下来时**正面对着玩家** ✓（与 §880 起的行为一致 ✓）。
         */
        public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

        public FumoMoBlock() {
            super(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.WHITE_WOOL)
                    .noOcclusion()                 // 不是实心块 ⇒ 不挡光、能贴在一起 ✓
                    .instabreak()                  // 空手一下就掉 ✓
                    .sound(SoundType.WOOL));
            this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        /** 放置时定朝向：正面对着玩家 ✓ */
        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return this.defaultBlockState()
                    .setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }

        /** 结构方块旋转/镜像时朝向跟着转 ✓ */
        @Override
        public BlockState rotate(BlockState state, Rotation rotation) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }

        @Override
        public BlockState mirror(BlockState state, Mirror mirror) {
            return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
        }

        @Override
        public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new FumoMoBlockEntity(pos, state);
        }

        /**
         * §908 每 tick 推进挤压动画（客户端也要跑 ✓ 动画本来就是纯客户端的 ✓；
         * 服务器跑着只会推一个没人看的计数器 ✓ 无害 ✓ —— 诡厄也是这么做的 ✓）。
         */
        @Override
        public <T extends net.minecraft.world.level.block.entity.BlockEntity>
        net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
                Level level, BlockState state,
                net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
            return (lvl, pos, st, be) -> {
                if (be instanceof FumoMoBlockEntity fumo) {
                    FumoMoBlockEntity.tick(lvl, pos, st, fumo);
                }
            };
        }

        /** 世界里不画方块模型 ✓（改由玩家模型渲染器画 ✓）——GUI/手持仍用 item 模型 ✓ */
        @Override
        public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
            return net.minecraft.world.level.block.RenderShape.INVISIBLE;
        }

        /** §906 朝向东西时用"转过 90°"的那个碰撞箱 ✓ */
        private static VoxelShape shapeFor(BlockState state) {
            return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return shapeFor(state);
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return shapeFor(state);
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                     InteractionHand hand, BlockHitResult hit) {
            // 只认"摸头"：命中点在碰撞箱上半部（≈ 脑袋那一块 ✓）
            boolean head = hit.getLocation().y - pos.getY() >= 0.5D;
            if (!head) return InteractionResult.PASS;
            // §908 挤压动画：**两端都触发** ✓（与诡厄 PlushieBlock#use 完全同构 ✓）
            //   客户端 ⇒ 本地放挤压动画 ✓；服务器 ⇒ 往下走、播音效 ✓
            if (level.getBlockEntity(pos) instanceof FumoMoBlockEntity fumo) {
                fumo.startAnimating();
            }
            if (level.isClientSide) return InteractionResult.SUCCESS;
            long now = System.currentTimeMillis();
            Long last = LAST_TOUCH.get(player.getUUID());
            if (last != null && now - last < 150L) return InteractionResult.SUCCESS;   // 连点节流 ✓
            LAST_TOUCH.put(player.getUUID(), now);
            level.playSound(null, pos, TOUCH_SOUND.get(), SoundSource.BLOCKS,
                    0.9F, 0.95F + level.random.nextFloat() * 0.1F);                    // 音调随机 ⇒ 每次不太一样 ✓
            if (level instanceof net.minecraft.server.level.ServerLevel server) {
                server.sendParticles(ParticleTypes.HEART, pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D,
                        3, 0.25D, 0.15D, 0.25D, 0.0D);                                  // 冒个小爱心 ✓
            }
            return InteractionResult.CONSUME;
        }

        /** 播放音效的便捷入口（以后"戴头上"右键也要用 ✓） */
        public static void playTouch(Level level, double x, double y, double z, Player player) {
            long now = System.currentTimeMillis();
            Long last = LAST_TOUCH.get(player.getUUID());
            if (last != null && now - last < 150L) return;
            LAST_TOUCH.put(player.getUUID(), now);
            level.playSound(null, x, y, z, TOUCH_SOUND.get(), SoundSource.PLAYERS,
                    0.9F, 0.95F + level.random.nextFloat() * 0.1F);
        }
    }
}
