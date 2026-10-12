package com.mofengbaizhi.tinkersnewlife.network.tools;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * TNL831 \u2605\u2605\u2605 \u81ea\u5efa\u914d\u65b9\u6295\u9012\uff08\u2605\u7528\u6237\u9009\u7684 B \u65b9\u6848 \u2713\uff09\u3002
 *
 * <h2>\u4e3a\u4f55\u4e0d\u7528\u5320\u9b42\u81ea\u5e26\u7684\u6295\u9012</h2>
 * \u5320\u9b42\u7528 {@code UpdateCraftingRecipePacket(\u4e16\u754c\u5750\u6807, \u914d\u65b9)} \u53d1\uff0c\u5ba2\u6237\u7aef\u6536\u5305\u540e\u8981
 * {@code level.getBlockEntity(pos)} \u53d6\u65b9\u5757\u5b9e\u4f53 \u2717 \u800c\u6211\u4eec\u7684\u5e7d\u7075\u5728**\u53e3\u888b\u5750\u6807**
 * \uff08\u2605\u6ca1\u6709\u533a\u5757 \u2717\uff09\u21d2 \u6c38\u8fdc\u62ff\u4e0d\u5230 \u2717\uff08\u2605\u65e5\u5fd7\u91cc\u90a3\u4e00\u5806
 * {@code No block entity at BlockPos{x=0, y=-1024, z=0}} \u5c31\u662f\u5b83 \u2713\uff09\u3002
 *
 * <p>\u2605\u6539\u6210\u6211\u4eec\u81ea\u5df1\u53d1**\u914d\u65b9 id** \u2713\uff0c\u5ba2\u6237\u7aef\u6536\u5230\u540e\u76f4\u63a5\u5728\u865a\u62df\u8868\u91cc\u627e\u5230\u5e7d\u7075 \u2713
 * \u5e76\u8c03\u5320\u9b42**\u81ea\u5df1\u7684** {@code updateRecipe}\uff08\u2605public \u2713\uff09\u21d2 \u7ed3\u679c\u69fd\u5c31\u80fd\u7b97\u51fa\u4ea7\u7269 \u2713\u2713
 */
public class PacketPortableRecipe {

    private final BlockPos pos;
    private final ResourceLocation recipeId;

    public PacketPortableRecipe(BlockPos pos, ResourceLocation recipeId) {
        this.pos = pos;
        this.recipeId = recipeId;
    }

    public PacketPortableRecipe(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.recipeId = buf.readResourceLocation();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeResourceLocation(recipeId);
    }

    public static void handle(PacketPortableRecipe packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            try {
                if (ctx.get().getSender() != null) {
                    return;   // \u2605\u8fd9\u662f\u670d\u52a1\u7aef \u21d2 \u4e0d\u5904\u7406 \u2713
                }
                BlockEntity ghost = PortableDeviceSupport.lookupVirtual(packet.pos);
                if (ghost == null) {
                    return;
                }
                Level level = ghost.getLevel();
                if (level == null) {
                    return;
                }
                Optional<? extends Recipe<?>> opt = level.getRecipeManager().byKey(packet.recipeId);
                if (opt.isEmpty()) {
                    return;
                }
                Object recipe = opt.get();
                ghost.getClass().getMethod("updateRecipe",
                        Class.forName("net.minecraft.world.item.crafting.CraftingRecipe")).invoke(ghost, recipe);
            } catch (Throwable t) {
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
