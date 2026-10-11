package com.mofengbaizhi.tinkersnewlife.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * §1329 便携设备（★唯一入口 ✗ ⭐ 只认我们自己的标记串 ✓）。
 *
 * <p>【背景】{@code BaseContainerMenu#getTileEntityFromBuf(buf, type)} 会读 buf 里的坐标
 * **回世界查**方块实体 ⇒ 便携设备不在世界里 ⇒ 返回 null ⇒ Mantle 报错 ⇒ 客户端槽位数错
 * ⇒ {@code IndexOutOfBounds} ⇒ 界面被踢关 ✓
 *
 * <p>【格式】我们自己的菜单 buf 是：
 * <pre>[坐标][标记串 "tnl:portable:v1"][实体类名][方块 id][库存 NBT]</pre>
 * 读到标记 ⇒ 造游离实体返回 ✗ 读不到 ⇒ {@code resetReaderIndex()} **原样放行**
 * （★匠魂自己的界面因此完全不受影响 ✓ —— §1329 修的正是"原版 GUI 打不开"那个回归 ✓）
 */
@Mixin(value = slimeknights.mantle.inventory.BaseContainerMenu.class, remap = false)
public class GhostStationMenuMixin {




    @Inject(method = "getTileEntityFromBuf", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tnl$ghostTile(FriendlyByteBuf buf, Class<?> clazz, CallbackInfoReturnable cir) {
        if (buf == null || clazz == null) {
            return;
        }
        if (buf.readableBytes() < 16) {
            return;   // ★坐标都放不下 ⇒ 不可能是我们的 ✓
        }
        buf.markReaderIndex();
        try {
            buf.readBlockPos();
            int before = buf.readableBytes();
            String magic = buf.readUtf(64);
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携设备·菜单] 标记串校验 clazz={} 剩余字节={} 读到=[{}]",
                    clazz.getSimpleName(), before, magic);
            if (!com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.MAGIC.equals(magic)) {
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携设备·菜单] 不是我们的 buf ⇒ 原样放行 ✓");
                buf.resetReaderIndex();
                return;
            }
            String beClass = buf.readUtf(256);
            String blockId = buf.readUtf(128);
            CompoundTag nbt = buf.readNbt();

            // §1335 工具包：坐标/类名/方块/NBT 之后还可以跟"若干侧栏幽灵"
            com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.CLIENT_SIDES.clear();
            try {
                int sides = buf.readVarInt();
                for (int i = 0; i < sides; i++) {
                    String sc = buf.readUtf(256);
                    String sb = buf.readUtf(128);
                    CompoundTag sn = buf.readNbt();
                    net.minecraft.world.level.block.entity.BlockEntity g2 =
                            com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.makeGhost(sc, sb);
                    if (g2 != null) {
                        g2.setLevel(net.minecraft.client.Minecraft.getInstance().level);
                        com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.importInto(g2, sn);
                        com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.CLIENT_SIDES.add(g2);
                    }
                }
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[便携设备·菜单] 侧栏幽灵 {} 个", sides);
                com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.PENDING_SIDES.clear();
                com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.PENDING_SIDES
                        .addAll(com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.CLIENT_SIDES);
            } catch (Throwable ignored) {
            }

            net.minecraft.world.level.block.entity.BlockEntity ghost =
                    com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.makeGhost(beClass, blockId);
            if (ghost == null) {
                return;
            }
            try {
                ghost.setLevel(net.minecraft.client.Minecraft.getInstance().level);
            } catch (Throwable ignored) {
            }
            com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.importInto(ghost, nbt);
            int slots = -1;
            try {
                slots = (ghost instanceof net.minecraft.world.Container c) ? c.getContainerSize() : -1;
                if (slots < 0) {
                    var h = ghost.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
                    slots = (h == null) ? -1 : h.getSlots();
                }
            } catch (Throwable ignored) {
            }
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                    "[便携设备·菜单] 幽灵实体 {} 方块={} 槽数={} 已交给匠魂菜单", beClass, blockId, slots);
            cir.setReturnValue(ghost);
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("[便携设备·菜单] 校验抛异常 ⇒ 放行: {}", t.toString());
            buf.resetReaderIndex();
        }
    }
}