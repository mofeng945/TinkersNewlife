package com.mofengbaizhi.tinkersnewlife.network.slashblade;

import java.util.function.Supplier;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import slimeknights.tconstruct.library.tools.item.IModifiable;

/**
 * <b>§1022 照抄 TiCEX 的 {@code StateSyncPacket}</b> ✓（<a href="https://github.com/mofumofumoffy/ticex">ticex</a> `1.20.1` 分支）
 * —— <b>服务端 → 客户端</b>同步刀状态 ✓：把服务端那份 BLADESTATE 的 NBT 写到**客户端手里这把刀**的状态上 ✓。
 *
 * <p>为什么需要它：连段/蓄力/技能表现都依赖客户端那份刀状态 ✓；
 * 光靠原版槽位同步不够及时 ✓（TiCEX 就是在若干事件时机主动推这个包 ✓，见 {@code KatanaBladeSyncEvents} ✓）。
 */
public class PacketSlashBladeStateSync {

    private final CompoundTag stateNbt;

    public PacketSlashBladeStateSync(CompoundTag stateNbt) {
        this.stateNbt = stateNbt;
    }

    public PacketSlashBladeStateSync(FriendlyByteBuf buf) {
        this.stateNbt = buf.readNbt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(stateNbt);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().level != null && Minecraft.getInstance().player != null) {
                ItemStack mainHandStack = Minecraft.getInstance().player.getMainHandItem();
                if (mainHandStack.getItem() instanceof IModifiable) {
                    mainHandStack.getCapability(ItemSlashBlade.BLADESTATE)
                            .ifPresent(state -> state.deserializeNBT(stateNbt));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
