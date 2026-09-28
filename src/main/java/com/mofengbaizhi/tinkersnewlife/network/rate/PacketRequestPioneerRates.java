package com.mofengbaizhi.tinkersnewlife.network.rate;

import com.mofengbaizhi.tinkersnewlife.content.item.IndustrialPioneerCertificateItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：<b>刷新产率界面</b>（§744 ✓）
 *
 * <p>为什么需要它：用户口径「每次打开 gui 自动统计一次，动态变化」✓
 * ⇒ 界面打开后会<b>每 5 秒</b>要一次最新数据 ✓，这样"刚跑完的那一轮采样"能立刻反映到界面上 ✓
 * 而不是"开着不动、数字永远是打开那一刻的"✗。
 *
 * <h2>安全（服务端自己再验一遍 ✓ 不信客户端 ✗）</h2>
 * 客户端会带上"我正在看哪个维度"✓，但服务端<b>不会</b>照单全收 ✗：
 * 它会检查这名玩家身上（主手 / 副手 / charm 槽 ✓）**确实**带着一枚
 * <b>绑定到该维度</b>的「工业开拓之证」✓，验不过就直接丢弃这个包 ✓
 * ⇒ 既拿不到别的维度数据、也拿不到自己没资格看的数据 ✓。
 */
public class PacketRequestPioneerRates {

    private final String dimensionId;

    public PacketRequestPioneerRates(String dimensionId) {
        this.dimensionId = dimensionId == null ? "" : dimensionId;
    }

    public PacketRequestPioneerRates(FriendlyByteBuf buf) {
        this.dimensionId = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(dimensionId);
    }

    public static void handle(PacketRequestPioneerRates packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            ResourceLocation id = ResourceLocation.tryParse(packet.dimensionId);
            if (id == null) return;
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, id);
            if (!holdsCertificateFor(player, dimension)) return;   // 验不过 ⇒ 丢弃 ✓
            IndustrialPioneerCertificateItem.sendRateReport(player, dimension, false);   // 刷新不催采样 ✓
        });
        context.setPacketHandled(true);
    }

    /** 主手 / 副手 / charm 槽里有没有"绑定到该维度"的工业开拓之证 ✓ */
    private static boolean holdsCertificateFor(ServerPlayer player, ResourceKey<Level> dimension) {
        for (ItemStack stack : new ItemStack[]{
                player.getMainHandItem(), player.getOffhandItem()}) {
            if (isBoundTo(stack, dimension)) return true;
        }
        var curios = CuriosApi.getCuriosInventory(player).resolve();
        if (curios.isEmpty()) return false;
        var handler = curios.get().getStacksHandler(IndustrialPioneerCertificateItem.CHARM_SLOT);
        if (handler.isEmpty()) return false;
        var stacks = handler.get().getStacks();
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
            if (isBoundTo(stacks.getStackInSlot(slot), dimension)) return true;
        }
        return false;
    }

    private static boolean isBoundTo(ItemStack stack, ResourceKey<Level> dimension) {
        if (!(stack.getItem() instanceof IndustrialPioneerCertificateItem)) return false;
        ResourceKey<Level> bound = IndustrialPioneerCertificateItem.boundDimension(stack);
        return bound != null && bound.equals(dimension);
    }
}
