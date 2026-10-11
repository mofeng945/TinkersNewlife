package com.mofengbaizhi.tinkersnewlife.content.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * §1321 便携箱（★铸造箱 / 部件箱 ✗ ⭐ 一个类管两种 ✓）。
 *
 * <p>与工匠站的关键差别（★都是反编译读来的 ✓）：
 * <ul>
 *   <li>箱子的内容**不在 Container 里** ✗ ⭐ 在 Forge 的 {@code IItemHandler}（{@code IChestItemHandler}）里
 *       ⇒ 读写一律走 capability ✓；</li>
 *   <li>箱子菜单的客户端构造器要的是**抽象类** {@code AbstractChestBlockEntity}
 *       ⇒ buf 里必须带**真实体类名** ✗ ⭐ 混入据此反射造 ✓；</li>
 *   <li>{@code ScalingChestItemHandler} 的槽位数由 NBT 里的 {@code Size} 决定
 *       ⇒ 必须显式写 Size（否则 0 槽 ⇒ 侧栏宽度为 0/负 ⇒ 又回到 JEI 崩那个坑）✓。</li>
 * </ul>
 */
public class PortableChestItem extends Item {

    public enum Kind {
        CAST_CHEST("tconstruct:cast_chest",
                "slimeknights.tconstruct.tables.block.entity.chest.CastChestBlockEntity", 4),
        PART_CHEST("tconstruct:part_chest",
                "slimeknights.tconstruct.tables.block.entity.chest.PartChestBlockEntity", 8);

        public final String blockId;
        public final String beClass;
        public final int slotLimit;

        Kind(String blockId, String beClass, int slotLimit) {
            this.blockId = blockId;
            this.beClass = beClass;
            this.slotLimit = slotLimit;
        }
    }

    /** 每个箱子在**玩家存档**里各自的键（★不碰物品 NBT ⇒ 没有刷物品风险 ✓） */
    public static String saveKey(Kind kind) {
        return "tnl_portable_" + kind.name().toLowerCase() + "_inv";
    }

    public static String nameKey(Kind kind) {
        return "item.tinkersnewlife.portable_" + kind.name().toLowerCase();
    }

    private final Kind kind;

    public PortableChestItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    /** 造游离箱子实体（★公开构造器 (BlockPos, BlockState) ✓） */
    public static BlockEntity makeGhost(Kind kind) {
        return PortableDeviceSupport.makeGhost(kind.beClass, kind.blockId);
    }

    private static IItemHandler handlerOf(BlockEntity be) {
        return PortableDeviceSupport.handlerOf(be);
    }

    /** 导出：Size ＋ 每槽物品（★Size 必须写 ✗ ⭐ 否则侧栏槽位是 0 ✓） */
    public static CompoundTag export(BlockEntity be, int size) {
        return PortableDeviceSupport.export(be, size);
    }

    /** 导入：直接把 {Size, Items} 灌进 handler（★ItemStackHandler#deserializeNBT 认这个格式 ✓） */
    public static void importInto(BlockEntity be, CompoundTag tag) {
        PortableDeviceSupport.importInto(be, tag);
    }

    /** 打开前：内容来自**玩家存档** */
    public static void loadFromPlayer(Player player, BlockEntity be, Kind kind) {
        PortableDeviceSupport.loadFromPlayer(player, be, saveKey(kind));
    }

    /** 关闭时：写回**玩家存档** */
    public static void storeToPlayer(Player player, BlockEntity be, Kind kind) {
        PortableDeviceSupport.storeToPlayer(player, be, saveKey(kind), 9);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.pass(stack);
        }
        BlockEntity ghost = makeGhost(kind);
        if (ghost == null) {
            return InteractionResultHolder.fail(stack);
        }
        loadFromPlayer(sp, ghost, kind);
        CompoundTag data = export(ghost, 9);
        IItemHandler h = handlerOf(ghost);
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携箱] 打开 {} slots={} 有handler={}",
                kind.name(), h == null ? -1 : h.getSlots(), h != null);
        NetworkHooks.openScreen(sp, new SimpleMenuProvider(
                (id, inv, p) -> new com.mofengbaizhi.tinkersnewlife.content.menu.PortableChestMenu(id, inv,
                        (slimeknights.tconstruct.tables.block.entity.chest.AbstractChestBlockEntity) ghost, kind),
                net.minecraft.network.chat.Component.translatable(nameKey(kind))),
                buf -> {
                    buf.writeBlockPos(new BlockPos(0, -1024, 0));
                    buf.writeUtf(kind.beClass);     // ★客户端据此反射造（★它要的是抽象类 ✓）
                    buf.writeNbt(data);
                });
        return InteractionResultHolder.success(stack);
    }
}