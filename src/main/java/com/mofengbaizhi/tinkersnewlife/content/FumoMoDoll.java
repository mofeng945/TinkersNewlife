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

import java.util.List;
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
    /** 内置默认皮肤的那只（{@code tinkersnewlife:fumo_mo} ✓ 行为/贴图/名字零变化 ✓） */
    public static final RegistryObject<Item> FUMO_MO_ITEM = ITEMS.register(
            FumoMoSkins.itemPath(FumoMoSkins.DEFAULT_SKIN),
            () -> new FumoMoItem(FUMO_MO.get(), new Item.Properties()));

    /**
     * §1079 <b>全部 fufu 物品</b>（默认皮肤在最前 ✓、扫描到的皮肤按名字排序在后 ✓）——
     * 创造栏、Curios 渲染器注册、物品模型注入都只遍历这一份 ✓。
     * <p>⚠ 这一行就是"**模组构造期自动遍历皮肤目录**"的入口 ✓（静态初始化 ⇒ {@link #init()} 一调就跑 ✓，
     * 一定早于 Forge 的 {@code RegisterEvent} ✓）；扫描本身在 {@link FumoMoSkins} 里，
     * 全是 try/catch ＋ 兜底 ⇒ 扫不到就只剩默认皮肤 ✓ 不会崩 ✓。
     */
    public static final List<RegistryObject<Item>> FUMO_ITEMS = buildSkinItems();

    /**
     * §1082 由<b>皮肤名</b>找到那只物品 ✓（找不到/为空 ⇒ 默认那只 ✓ 绝不返回 null ✗）。
     * <p>用途：挖掉/选取地上的玩偶时给回**它自己的皮肤** ✓（见 {@code FumoMoBlock#getDrops}/{@code getCloneItemStack} ✓）。
     */
    public static Item itemForSkin(String skin) {
        if (skin != null && !skin.isEmpty()) {
            // §1113 双保险：①按 FumoMoBaseItem.skin() ✓ ②再按**注册名** fumo_<皮肤> 兜一道 ✓
            //   （任一条命中就返回 ✓ —— 单靠 skin() 万一取不到就会静默退回默认皮肤 ✗ 正是用户那个 bug ✗）
            String wantPath = "fumo_" + skin;
            for (RegistryObject<Item> ro : FUMO_ITEMS) {
                try {
                    Item it = ro.get();
                    if (it instanceof FumoMoBaseItem base && skin.equals(base.skin())) {
                        return it;
                    }
                    net.minecraft.resources.ResourceLocation id =
                            net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(it);
                    if (id != null && wantPath.equals(id.getPath())) {
                        return it;
                    }
                } catch (Throwable ignored) {
                    // 取不到就继续找 ✓
                }
            }
        }
        return FUMO_MO_ITEM.get();
    }

    private static List<RegistryObject<Item>> buildSkinItems() {
        List<RegistryObject<Item>> items = new java.util.ArrayList<>();
        items.add(FUMO_MO_ITEM);                                   // 内置默认皮肤（永远第一个 ✓）
        List<String> skins;
        try {
            skins = FumoMoSkins.scanned();                         // ★ 运行时扫描（jar / 开发环境两种形态 ✓）
        } catch (Throwable t) {
            // 连扫描都炸了也只是一只额外的皮肤都没有 ⇒ 保留默认皮肤 ✓ 绝不崩启动 ✗
            TinkersNewlife.LOGGER.warn("[fufu] 皮肤扫描整体失败 ⇒ 只注册内置默认皮肤：{}", t.toString());
            skins = List.of();
        }
        for (String skin : skins) {                                // 名字已排序 ⇒ 注册顺序稳定 ✓
            try {
                items.add(ITEMS.register(FumoMoSkins.itemPath(skin),
                        () -> new FumoMoBaseItem(FUMO_MO.get(), new Item.Properties(), skin)));
            } catch (Throwable t) {
                // 单个皮肤出问题（id 不合法/重名…）只跳过它 ✓ 绝不让启动崩 ✗
                TinkersNewlife.LOGGER.warn("[fufu] 皮肤 {} 注册失败，已跳过（不影响启动）: {}", skin, t.toString());
            }
        }
        return List.copyOf(items);
    }

    /**
     * §1079 <b>强制在模组构造期初始化本类</b> ——
     * 目的只有一个：让 {@link #FUMO_ITEMS} 的皮肤扫描 ＋ 上面那几个 {@code DeferredRegister}
     * 的登记都发生在 Forge 发 {@code RegisterEvent} **之前** ✓
     * （本来靠 {@code @Mod.EventBusSubscriber} 的类加载也在这之前 ✓，这里显式来一遍是保险 ＋ 让时序可读 ✓）。
     */
    public static void init() {
        // 方法体故意为空：调用它就是让 JVM 初始化本类 ✓
    }

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

        /**
         * §1076 <b>8 向放置</b>（用户口径：「让我的 fufu 可以 8 向放置」✓）——
         * 在 {@link #FACING}（4 向 ✓）之上再加一档**相对偏移** ✓：
         * {@code 0 = 不偏}✓、{@code 2 = +45°}✓、{@code 14 = −45°}✓（16 档属性 ⇒ 每档 22.5° ✓，
         * 我们只用其中三档 ⇒ 与四个基本方向组合正好 **8 向** ✓）。
         *
         * <p>⚠ <b>为什么不直接把 FACING 换成 16 档属性</b> ✓：老存档里已放好的 fufu 只带 {@code facing=…} ✗
         * —— 1.20.1 解析方块状态时属性名对不上会让**整条调色板项作废**（玩偶直接消失 ✗）。
         * 新增属性则默认 {@code rotation=0} ✓ ⇒ 旧存档外观**一模一样** ✓（渲染公式退化成原来那条 ✓）。
         */
        public static final net.minecraft.world.level.block.state.properties.IntegerProperty ROTATION =
                BlockStateProperties.ROTATION_16;

        public FumoMoBlock() {
            super(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.WHITE_WOOL)
                    .noOcclusion()                 // 不是实心块 ⇒ 不挡光、能贴在一起 ✓
                    .instabreak()                  // 空手一下就掉 ✓
                    .sound(SoundType.WOOL));
            this.registerDefaultState(this.stateDefinition.any()
                    .setValue(FACING, Direction.NORTH)
                    .setValue(ROTATION, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, ROTATION);
        }

        /**
         * 放置时定朝向：正面对着玩家 ✓，并**吸附到 45° 的整数倍** ⇒ 支持斜向（8 向 ✓）。
         * <p>拆成两半：{@link #FACING} 记**最近的四个基本方向** ✓（碰撞箱还靠它判轴 ✓），
         * {@link #ROTATION} 记**相对它的 ±45°／0 偏移** ✓ ⇒ 两者相加就是最终朝向 ✓。
         */
        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            // 玩偶正面 = 玩家视线的反方向（放下来正面对着玩家 ✓，与 §880 起一致 ✓）
            float yaw = (float) ((ctx.getRotation() + 180.0F) % 360.0F);
            int eight = Math.round(yaw / 45.0F) & 7;               // 8 向：每档 45° ✓
            float snapped = eight * 45.0F;
            Direction facing = Direction.fromYRot(snapped);         // 最近的基本方向 ✓（可能 null ✗）
            if (facing == null || facing.getAxis() == Direction.Axis.Y) facing = Direction.SOUTH;
            // 偏移归一化到 (−180,180] ⇒ 必为 −45 / 0 / +45 ✓ ⇒ 折算成 22.5° 档：14 / 0 / 2 ✓
            int diff = ((Math.round(snapped) - Math.round(facing.toYRot()) + 180) % 360 + 360) % 360 - 180;
            int rotation = diff == 0 ? 0 : (diff > 0 ? 2 : 14);
            return this.defaultBlockState().setValue(FACING, facing).setValue(ROTATION, rotation);
        }

        /** 结构方块旋转/镜像时朝向跟着转 ✓（ROTATION 是"相对 FACING 的偏移" ⇒ 整体转 90° 时它不变 ✓） */
        @Override
        public BlockState rotate(BlockState state, Rotation rotation) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }

        @Override
        public BlockState mirror(BlockState state, Mirror mirror) {
            int off = state.getValue(ROTATION);
            if (off == 2) off = 14; else if (off == 14) off = 2;    // 镜像 ⇒ ±45° 偏移取反 ✓
            return state.setValue(FACING, mirror.mirror(state.getValue(FACING))).setValue(ROTATION, off);
        }

        @Override
        public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new FumoMoBlockEntity(pos, state);
        }

        /**
         * §1079 <b>放下时把"我是哪个皮肤"带进方块实体</b> ✓ ——
         * 全部皮肤**共用同一个方块**（{@code tinkersnewlife:fumo_mo} ✓ 老存档零影响 ✓），
         * 皮肤只存在方块实体里 ✓ ⇒ 物品 → 方块实体这一步必须在这里做 ✓。
         *
         * <p>用官方的 {@code Block#setPlacedBy}（{@code BlockItem#place} 内部会调 ✓，
         * 位置就是它真正落下的那个格子 ✓）⇒ 不需要自己猜坐标 ✓。
         *
         * <p>写完立刻 {@code sendBlockUpdated} ⇒ 服务端会把方块实体数据
         * （{@link FumoMoBlockEntity#getUpdatePacket()} ✓）发给周围客户端 ✓；
         * 不这么做的话，客户端只会看到默认皮肤的玩偶 ✗（区块重新加载时另有
         * {@link FumoMoBlockEntity#getUpdateTag()} 兜底 ✓）。
         * <p>整段 try/catch：皮肤写不进去最多退回默认皮肤 ✓ 绝不能让放方块崩 ✗。
         */
        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                                net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            try {
                if (stack.getItem() instanceof FumoMoBaseItem fumo
                        && level.getBlockEntity(pos) instanceof FumoMoBlockEntity be) {
                    be.setSkin(fumo.skin());
                    level.sendBlockUpdated(pos, state, state, 3);
                }
            } catch (Throwable t) {
                TinkersNewlife.LOGGER.warn("[fufu] 放置时写皮肤失败（该玩偶会用默认皮肤）：{}", t.toString());
            }
        }

        /**
         * §1113 <b>挖掉玩偶 ⇒ 掉回"带这只皮肤"的那一只</b> ✓。
         *
         * <p>⚠ §1082 那条路（走 {@code getDrops} ＋ 从战利品上下文里捞 {@code BLOCK_ENTITY} ✗）**不可靠** ✗：
         * 用户实测「**切石机切成别的 fufu，放下再挖掉，掉的是初始形态**」✗ ——
         * 说明那条路上没拿到方块实体（或抛了异常被兜底 ✗）⇒ 退回默认皮肤 ✗。
         *
         * <p>⇒ 改成覆写 {@link #playerDestroy} ✓：原版**玩家破坏方块必然走这里** ✓，
         * 而且它**直接带着** level / pos / 方块实体 ✓（不用绕战利品参数 ✗）：
         * <ul>
         *   <li><b>不调用 super</b> ✗ —— super 会走 {@code dropResources} ⇒ 又调 {@link #getDrops}
         *       ⇒ 变成**掉两份**（一份对、一份默认 ✗）；</li>
         *   <li>本方块本来就没有战利品表 ✓ ⇒ 自己 {@code popResource} 一份即可 ✓
         *       （行为与原版"掉落本体"一致 ✓ 不受时运影响 ✓ 本来也不该受 ✓）。</li>
         *   <li>⚠ 创造模式拆方块原版**不走**这里 ✓ ⇒ 不会掉落 ✓（用中键选取拿皮肤那只 ✓ 见 {@link #getCloneItemStack}）；</li>
         * </ul>
         */
        @Override
        public void playerDestroy(Level level, net.minecraft.world.entity.player.Player player, BlockPos pos,
                                  BlockState state,
                                  net.minecraft.world.level.block.entity.BlockEntity be, ItemStack tool) {
            if (level.isClientSide) return;
            String skin = be instanceof FumoMoBlockEntity fumo ? fumo.getSkin() : FumoMoSkins.DEFAULT_SKIN;
            net.minecraft.world.level.block.Block.popResource(level, pos, new ItemStack(itemForSkin(skin)));
        }

        /**
         * §1113 <b>兜底</b>：**非玩家破坏**（爆炸 / 活塞 / 别的模组 ✓）走这条 ⇒ 仍尽量按皮肤给 ✓。
         * <p>这条路上拿不到方块实体是正常的 ✓ ⇒ 那时退回默认皮肤 ✓ 不报错 ✓。
         */
        @Override
        public List<ItemStack> getDrops(BlockState state,
                                        net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
            try {
                net.minecraft.world.level.storage.loot.LootParams params =
                        builder.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.BLOCK);
                net.minecraft.world.level.block.entity.BlockEntity be =
                        params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
                String skin = be instanceof FumoMoBlockEntity fumo ? fumo.getSkin() : FumoMoSkins.DEFAULT_SKIN;
                return List.of(new ItemStack(itemForSkin(skin)));
            } catch (Throwable t) {
                return List.of(new ItemStack(FUMO_MO_ITEM.get()));
            }
        }

        /** §1082 中键选取（创造）也给带皮肤的那一只 ✓ */
        @Override
        public ItemStack getCloneItemStack(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
            try {
                if (level.getBlockEntity(pos) instanceof FumoMoBlockEntity fumo) {
                    return new ItemStack(itemForSkin(fumo.getSkin()));
                }
            } catch (Throwable ignored) {
            }
            return new ItemStack(FUMO_MO_ITEM.get());
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
