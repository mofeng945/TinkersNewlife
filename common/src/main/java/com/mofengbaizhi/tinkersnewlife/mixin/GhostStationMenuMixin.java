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
                // §1339 安全读取：每一步都先看剩余字节（★彻底消除 EOF ✓）
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[步进] 入口 clazz={} bytes={}", clazz.getSimpleName(), buf.readableBytes());
            if (buf.readableBytes() < 8) {
                    buf.resetReaderIndex();
                    return;
                }
                net.minecraft.core.BlockPos wantedPos = buf.readBlockPos();   // §1347 客户端用同一坐标 ✓
                com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.VIRTUAL_BASE = wantedPos;
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[步进] 读完坐标 bytes={}", buf.readableBytes());
                String magic = buf.readableBytes() > 0 ? buf.readUtf(64) : "";
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[步进] 读到标记=[{}] bytes={}", magic, buf.readableBytes());
                if (!com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.MAGIC.equals(magic)) {
                    buf.resetReaderIndex();
                    return;
                }
                String beClass = buf.readableBytes() > 0 ? buf.readUtf(256) : "";
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[步进] 类名=[{}] bytes={}", beClass, buf.readableBytes());
                String blockId = buf.readableBytes() > 0 ? buf.readUtf(128) : "";
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[步进] 方块=[{}] bytes={}", blockId, buf.readableBytes());
                com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.CLIENT_TOOLKIT =
                        buf.readableBytes() > 0 && buf.readVarInt() > 0;   // §1344 工具包写的是侧栏数（6）✗ ⭐其它设备写 0 ✓
                if (com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.CLIENT_TOOLKIT) {
                    // §1342 客户端也把 6 个幽灵贴到相邻坐标 ✓
                    com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.VIRTUAL.clear();
                    java.util.List<net.minecraft.world.level.block.entity.BlockEntity> gs =
                            com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.buildDeviceGhosts();
                    for (int i = 0; i < gs.size(); i++) {
                        com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.registerVirtual(i, gs.get(i));
                    }
                }   // §1341 工具包标记 ✓
                // §1340 库存**不在 buf 里传**（★槽位内容由原版同步 ✓ ⭐避开了读 NBT 的 EOF ✗）
                net.minecraft.nbt.CompoundTag nbt = null;

                net.minecraft.world.level.block.entity.BlockEntity ghost =
                        com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.makeGhostAt(beClass, blockId, wantedPos);
                if (ghost == null) {
                    buf.resetReaderIndex();
                    return;
                }
                try {
                    ghost.setLevel(net.minecraft.client.Minecraft.getInstance().level);
                } catch (Throwable ignored) {
                }
                com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.importInto(ghost, nbt);

                // §1338 工具包侧栏（★有多少读多少 ✓）
                com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.CLIENT_SIDES.clear();
                com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.PENDING_SIDES.clear();
                try {
                    int sides = buf.readableBytes() > 0 ? buf.readVarInt() : 0;
                    for (int i = 0; i < sides && buf.readableBytes() > 0; i++) {
                        String sc = buf.readUtf(256);
                        String sb = buf.readUtf(128);
                        net.minecraft.nbt.CompoundTag sn = null;   // §1340 侧栏也只传类型 ✓
                        net.minecraft.world.level.block.entity.BlockEntity g2 =
                                com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.makeGhostAt(sc, sb, wantedPos);
                        if (g2 != null) {
                            try {
                                g2.setLevel(net.minecraft.client.Minecraft.getInstance().level);
                            } catch (Throwable ignored) {
                            }
                            com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.importInto(g2, sn);
                            com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.PENDING_SIDES.add(g2);
                        }
                    }
                } catch (Throwable ignored) {
                }
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                        "[便携设备·菜单] 幽灵实体 {} 方块={} 侧栏={} 已交给匠魂菜单",
                        beClass, blockId, com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.PENDING_SIDES.size());

            // §1350 主实体也进虚拟表（★BFS 从它开始 ✓）
            if (com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.CLIENT_TOOLKIT) {
                com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.VIRTUAL_BASE = wantedPos;
                com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport.registerVirtualMain(ghost);
            }
cir.setReturnValue(ghost);
        } catch (Throwable t) {
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("[便携设备·菜单] 校验抛异常 ⇒ 放行: {}", t.toString());
            buf.resetReaderIndex();
        }
    }
}
