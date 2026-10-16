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
 * TNL831 ★★★ 自建配方投递（★用户选的 B 方案 ✓）。
 *
 * <h2>为何不用匠魂自带的投递</h2>
 * 匠魂用 {@code UpdateCraftingRecipePacket(世界坐标, 配方)} 发，客户端收包后要
 * {@code level.getBlockEntity(pos)} 取方块实体 ✗ 而我们的幽灵在**口袋坐标**
 * （★没有区块 ✗）⇒ 永远拿不到 ✗（★日志里那一堆
 * {@code No block entity at BlockPos{x=0, y=-1024, z=0}} 就是它 ✓）。
 *
 * <p>★改成我们自己发**配方 id** ✓，客户端收到后直接在虚拟表里找到幽灵 ✓
 * 并调匠魂**自己的** {@code updateRecipe}（★public ✓）⇒ 结果槽就能算出产物 ✓✓
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
                    return;   // ★这是服务端 ⇒ 不处理 ✓
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
