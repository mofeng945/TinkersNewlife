package com.mofengbaizhi.tinkersnewlife.content.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkHooks;

/**
 * §1334 便携工匠台 / 便携工匠工具包（★同一个实现 ✗ ⭐ 只是存档键不同 ✓）。
 *
 * <p>★为什么"工匠台"就够了 ✗：⭐ 匠魂**工匠台**的界面自带工具切换标签
 * ⇒ 一个界面里就能用 工匠站／工匠砧 的制作 ✓（用户口径"⭐ 6 个功能"的第一步 ✓）
 *
 * <p>幽灵实体由**方块**造（{@code Block#newBlockEntity} ✓）⇒ 槽位数／布局与真实方块一致 ✓
 * ✗ ⭐ 内容存**玩家存档**（★不碰物品 NBT ⇒ 无刷物品风险 ✓）。
 */
public class PortableCraftingStationItem extends Item {

    public enum Kind {
        CRAFTING_STATION("tconstruct:crafting_station",
                "slimeknights.tconstruct.tables.block.entity.table.CraftingStationBlockEntity",
                "tnl_portable_crafting_station_inv"),
        TOOLKIT("tconstruct:crafting_station",
                "slimeknights.tconstruct.tables.block.entity.table.CraftingStationBlockEntity",
                "tnl_portable_toolkit_inv");

        public final String blockId;
        public final String beClass;
        public final String saveKey;

        Kind(String blockId, String beClass, String saveKey) {
            this.blockId = blockId;
            this.beClass = beClass;
            this.saveKey = saveKey;
        }
    }

    private final Kind kind;

    public PortableCraftingStationItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public static String nameKey(Kind kind) {
        return "item.tinkersnewlife.portable_" + kind.name().toLowerCase();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.pass(stack);
        }
        // §1347 主实体也放到基准点（★BFS 从它开始 ✓）
        BlockEntity ghost = PortableDeviceSupport.makeGhostAt(kind.beClass, kind.blockId,
                kind == Kind.TOOLKIT ? sp.blockPosition().above(3) : PortableDeviceSupport.POCKET_POS);
        if (ghost == null) {
            return InteractionResultHolder.fail(stack);
        }
        try {
            ghost.setLevel(level);
        } catch (Throwable ignored) {
        }
        PortableDeviceSupport.loadFromPlayer(sp, ghost, kind.saveKey);
        CompoundTag data = PortableDeviceSupport.export(ghost);
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携设备] 打开 {} slots={} key={}",
                kind.name(), PortableDeviceSupport.slots(ghost), kind.saveKey);
        // §1335 工具包：★把 6 个幽灵方块作为 6 个"侧栏"并排摆开 ✓
        if (kind == Kind.TOOLKIT) {
            java.util.List<BlockEntity> sides = new java.util.ArrayList<>();
            for (String[] d : PortableDeviceSupport.DEVICES) {
                BlockEntity g = PortableDeviceSupport.makeGhost(d[0], d[1]);
                if (g != null) {
                    try {
                        g.setLevel(level);
                    } catch (Throwable ignored) {
                    }
                    PortableDeviceSupport.loadFromPlayer(sp, g, d[2]);
                    sides.add(g);
                }
            }
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携设备] 工具包侧栏 {} 个", sides.size());
            PortableDeviceSupport.PENDING_SIDES.clear();
            PortableDeviceSupport.PENDING_SIDES.addAll(sides);
            // §1347 ★放在**玩家附近**（★区块已加载 ⇒ StationTabPacket 的 hasChunkAt 才能过 ✓）
            PortableDeviceSupport.VIRTUAL_BASE = sp.blockPosition().above(3);
            PortableDeviceSupport.VIRTUAL.clear();
            PortableDeviceSupport.registerVirtualMain(ghost);   // §1350 ★主实体（BFS 起点）
            for (int i = 0; i < sides.size(); i++) {
                PortableDeviceSupport.registerVirtual(i, sides.get(i));
            }
            PortableDeviceSupport.dumpVirtual();   // §1351 ★注册完了才打总数 ✓
            PortableDeviceSupport.PENDING_INV = sp.getInventory();   // §1338 ✓
            NetworkHooks.openScreen(sp, new SimpleMenuProvider(
                    (id, inv, p) -> com.mofengbaizhi.tinkersnewlife.content.menu.PortableToolkitMenu.server(
                            id, inv,
                            (slimeknights.tconstruct.tables.block.entity.table.CraftingStationBlockEntity) ghost,
                            sides),
                    net.minecraft.network.chat.Component.translatable(nameKey(kind))),
                    buf -> {
                    buf.writeBlockPos(com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.VIRTUAL_BASE);   // §1357 ★必须和顶栏用同一个基准 ✓
                        buf.writeUtf(PortableDeviceSupport.MAGIC);
                        buf.writeUtf(kind.beClass);
                        buf.writeUtf(kind.blockId);
                        buf.writeVarInt(sides.size());
                        for (int i = 0; i < sides.size(); i++) {
                            buf.writeUtf(PortableDeviceSupport.DEVICES[i][0]);
                            buf.writeUtf(PortableDeviceSupport.DEVICES[i][1]);
                        }
                    });
            return InteractionResultHolder.success(stack);
        }

        NetworkHooks.openScreen(sp, new SimpleMenuProvider(
                (id, inv, p) -> new com.mofengbaizhi.tinkersnewlife.content.menu.PortableCraftingStationMenu(
                        id, inv,
                        (slimeknights.tconstruct.tables.block.entity.table.CraftingStationBlockEntity) ghost,
                        kind.saveKey),
                net.minecraft.network.chat.Component.translatable(nameKey(kind))),
                buf -> {
                    buf.writeBlockPos(new BlockPos(0, -1024, 0));
                    buf.writeUtf(PortableDeviceSupport.MAGIC);
                    buf.writeUtf(kind.beClass);
                    buf.writeUtf(kind.blockId);
                });
        return InteractionResultHolder.success(stack);
    }
}
