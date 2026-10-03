package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
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
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TinkersNewlife.MOD_ID);
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

    /** 单独一页创造栏（图标就是 fufu 自己 ✓） */
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("fumo_mo",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.tinkersnewlife.fumo_mo"))
                    .icon(() -> new ItemStack(FUMO_MO_ITEM.get()))
                    .displayItems((params, output) -> output.accept(FUMO_MO_ITEM.get()))
                    .build());

    static {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        SOUNDS.register(bus);
        TABS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }

    private FumoMoDoll() {}

    /** 每个玩家上一次摸头的时间（毫秒 ✓ 防连点刷音效 ✓） */
    private static final Map<UUID, Long> LAST_TOUCH = new ConcurrentHashMap<>();

    /** 玩偶的碰撞箱：只占中间一小块（四周能走过去 ✓ 与方块模型大致对得上 ✓） */
    private static final VoxelShape SHAPE = Shapes.box(0.25D, 0.0D, 0.3125D, 0.75D, 0.875D, 0.6875D);

    /** 玩偶方块：右键＝摸头 ✓ */
    public static class FumoMoBlock extends Block implements net.minecraft.world.level.block.EntityBlock {
        public FumoMoBlock() {
            super(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.WHITE_WOOL)
                    .noOcclusion()                 // 不是实心块 ⇒ 不挡光、能贴在一起 ✓
                    .instabreak()                  // 空手一下就掉 ✓
                    .sound(SoundType.WOOL));
        }

        @Override
        public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new FumoMoBlockEntity(pos, state);
        }

        /** 世界里不画方块模型 ✓（改由玩家模型渲染器画 ✓）——GUI/手持仍用 item 模型 ✓ */
        @Override
        public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
            return net.minecraft.world.level.block.RenderShape.INVISIBLE;
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                     InteractionHand hand, BlockHitResult hit) {
            // 只认"摸头"：命中点在碰撞箱上半部（≈ 脑袋那一块 ✓）
            boolean head = hit.getLocation().y - pos.getY() >= 0.5D;
            if (!head) return InteractionResult.PASS;
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
