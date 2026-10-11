package com.mofengbaizhi.tinkersnewlife.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.tables.block.entity.table.TinkerStationBlockEntity;

/**
 * §1313 便携工匠站（幽灵方块）：让匠魂菜单的客户端构造器"找得到方块实体"。
 *
 * <p>匠魂菜单客户端构造器调用 {@code getTileEntityFromBuf(buf, TinkerStationBlockEntity.class)}，
 * 它会**按 buf 里的坐标回世界查**方块实体 ⇒ 便携站不在世界里 ⇒ null
 * ⇒ Mantle 报 "Menu failed to find BlockEntity" ⇒ 客户端槽位数错 ⇒ IndexOutOfBounds ⇒ 界面被踢关。
 *
 * <p>这里在方法入口截住：buf 里坐标之后**还有我们的库存 NBT** 时，
 * 就地重建游离实体并直接返回 ⇒ 客户端不回世界查 ⇒ 两端菜单一致 ✓
 */
@Mixin(value = slimeknights.mantle.inventory.BaseContainerMenu.class, remap = false)
public class GhostStationMenuMixin {

    @Inject(method = "getTileEntityFromBuf", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tnl$ghostTile(FriendlyByteBuf buf, Class<?> clazz, CallbackInfoReturnable cir) {
        try {
            // §1321 箱子分支：匠魂菜单要的是**抽象类** AbstractChestBlockEntity
            // §1322 台子分支：部件制作台 / 强化调配台（★buf 与工匠站同格式：坐标 ＋ 库存 ✓）
            if (clazz == slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity.class
                    || clazz == slimeknights.tconstruct.tables.block.entity.table.ModifierWorktableBlockEntity.class) {
                buf.markReaderIndex();
                buf.readBlockPos();
                CompoundTag tableNbt = buf.readNbt();
                boolean partBuilder = clazz == slimeknights.tconstruct.tables.block.entity.table.PartBuilderBlockEntity.class;
                var which = partBuilder
                        ? com.mofengbaizhi.tinkersnewlife.content.item.PortableTableItem.Kind.PART_BUILDER
                        : com.mofengbaizhi.tinkersnewlife.content.item.PortableTableItem.Kind.MODIFIER_WORKTABLE;
                net.minecraft.world.level.block.entity.BlockEntity tableGhost = com.mofengbaizhi.tinkersnewlife.content.item.PortableTableItem.makeGhost(which);
                if (tableGhost != null) {
                    try {
                        tableGhost.setLevel(net.minecraft.client.Minecraft.getInstance().level);
                    } catch (Throwable ignored) {
                    }
                    com.mofengbaizhi.tinkersnewlife.content.item.PortableTableItem.importInto(tableGhost, tableNbt);
                    com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携台·菜单] 幽灵台子 {} 已交给匠魂菜单", which);
                    cir.setReturnValue(tableGhost);
                    return;
                }
            }

            if (clazz == slimeknights.tconstruct.tables.block.entity.chest.AbstractChestBlockEntity.class) {
                buf.markReaderIndex();
                buf.readBlockPos();
                String beName = buf.readUtf();
                CompoundTag chestNbt = buf.readNbt();
                var which = "slimeknights.tconstruct.tables.block.entity.chest.CastChestBlockEntity".equals(beName)
                        ? com.mofengbaizhi.tinkersnewlife.content.item.PortableChestItem.Kind.CAST_CHEST
                        : com.mofengbaizhi.tinkersnewlife.content.item.PortableChestItem.Kind.PART_CHEST;
                var chestGhost = com.mofengbaizhi.tinkersnewlife.content.item.PortableChestItem.makeGhost(which);
                if (chestGhost != null) {
                    com.mofengbaizhi.tinkersnewlife.content.item.PortableChestItem.importInto(chestGhost, chestNbt);
                    com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携箱·菜单] 幽灵箱子 {} 已交给匠魂菜单", beName);
                    cir.setReturnValue(chestGhost);
                    return;
                }
            }

            if (clazz != TinkerStationBlockEntity.class || buf == null || buf.readableBytes() < 8) {
                return;
            }
            buf.markReaderIndex();
            buf.readBlockPos();
            if (buf.readableBytes() <= 0) {
                buf.resetReaderIndex();
                return;
            }
            CompoundTag nbt = buf.readNbt();
            Level level = null;
            try {
                level = net.minecraft.client.Minecraft.getInstance().level;
            } catch (Throwable ignored) {
            }
            TinkerStationBlockEntity be = com.mofengbaizhi.tinkersnewlife.content.item.PortableTinkerStationItem
                    .createDetached(level);
            com.mofengbaizhi.tinkersnewlife.content.item.PortableTinkerStationItem.importInventory(be, nbt);
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携站·菜单] 幽灵实体已交给匠魂菜单 slots={}",
                    be.getContainerSize());
            cir.setReturnValue(be);
        } catch (Throwable ignored) {
        }
    }
}
