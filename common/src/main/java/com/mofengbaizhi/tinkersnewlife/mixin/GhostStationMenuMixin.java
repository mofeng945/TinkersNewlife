package com.mofengbaizhi.tinkersnewlife.mixin;

import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * §1318 便携设备（通用）：让匠魂/Mantle 的菜单在客户端**不再按坐标回世界查方块实体**。
 *
 * <p>{@code BaseContainerMenu#getTileEntityFromBuf(buf, type)} 会读 buf 里的坐标回世界查 ⇒
 * 便携设备不在世界里 ⇒ null ⇒ Mantle 报错 ⇒ 客户端槽位数错 ⇒ IndexOutOfBounds ⇒ 界面被踢关。
 *
 * <p>这里在入口截住：buf 在坐标之后**还有我们的库存 NBT** 时，按 {@code type} 反射造一个游离实体返回 ✓
 * （★不用知道是哪种设备 ✗ ⭐ 四种通吃 ✓）
 */
@Mixin(value = slimeknights.mantle.inventory.BaseContainerMenu.class, remap = false)
public class GhostStationMenuMixin {

    @Inject(method = "getTileEntityFromBuf", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tnl$ghostTile(FriendlyByteBuf buf, Class<?> clazz, CallbackInfoReturnable cir) {
        try {
            if (buf == null || clazz == null || buf.readableBytes() < 8) {
                return;
            }
            buf.markReaderIndex();
            buf.readBlockPos();
            if (buf.readableBytes() <= 0) {
                buf.resetReaderIndex();
                return;
            }
            buf.resetReaderIndex();
            Object ghost = com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceItem
                    .buildGhost((Class) clazz, buf, "tconstruct:tinker_station");
            if (ghost != null) {
                cir.setReturnValue(ghost);
            }
        } catch (Throwable ignored) {
        }
    }
}