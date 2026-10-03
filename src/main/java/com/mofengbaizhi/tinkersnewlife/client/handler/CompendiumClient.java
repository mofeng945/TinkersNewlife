package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.screen.CompendiumScreen;
import com.mofengbaizhi.tinkersnewlife.content.item.CompendiumItem;
import com.mofengbaizhi.tinkersnewlife.network.compendium.PacketCompendiumAbsorb;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>帕秋莉的百宝书 —— 客户端部分</b>（§910 / §910d）：开界面 ＋ 物品栏里的"吞噬"手势 ✓。
 *
 * <p>挂的是 <b>Forge 总线</b>（注解里不写 bus ⇒ 默认 FORGE ✓）＋ {@code Dist.CLIENT} ✓
 * ⇒ 专服加载不到这里 ✓ 不会 NoClassDefFoundError ✗。
 *
 * <p>事件链已核对（反汇编 Forge jar ✓）：{@code MouseHandler#onPress} →
 * {@code ForgeHooksClient.onScreenMouseClickedPre} ⇒ 正是 {@code ScreenEvent.MouseButtonPressed.Pre} ✓。
 *
 * <h3>§910d 创造模式的处理（实测逼出来的 ✓）</h3>
 * 创造模式里**光标上拖着的那叠只在客户端存在** ✗（服务端只认按背包槽号直接写的
 * {@code ServerboundSetCreativeModeSlotPacket} ✓）⇒ 服务端看到的 carried 永远是空气 ✓
 * （日志：`来源 0 上没找到百宝书（拿到的=block.minecraft.air）` ✓）。
 * <p>因此：**创造模式 + 东西在光标上**时 ⇒ 客户端**本地登记**到那叠 ✓
 * （之后把它放进背包时，整叠会带着我们写的 NBT 一起同步上去 ✓），
 * 同时发 {@code SOURCE_CLIENT_LOCAL} 让服务端**只把那本书吃掉** ✓。
 * 生存模式的 carried 是服务端看得见的 ✓ ⇒ 仍然走服务端权威写入 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class CompendiumClient {

    private static final Logger LOG = LoggerFactory.getLogger("TinkersNewlife/Compendium");

    private CompendiumClient() {}

    /** 手持右键 ⇒ 打开"查阅"界面 ✓（由 {@code CompendiumItem#use} 经 DistExecutor 调过来 ✓） */
    public static void openScreen(ItemStack stack) {
        Minecraft.getInstance().setScreen(new CompendiumScreen(stack));
    }

    /**
     * §910 <b>物品栏手势</b>：手上有百宝书（光标拖着 / 主手 / 背包里）＋ <b>右键</b>一格<b>帕秋莉书</b> ⇒ 吞噬 ✓。
     *
     * <p>为什么挂 {@code ScreenEvent.MouseButtonPressed.Pre}：
     * 它比原版的槽位处理**更早** ✓ ⇒ 认出来就能 {@code setCanceled(true)} 拦掉原版那一手 ✓。
     * <p>为什么用 {@code getSlotUnderMouse()}：1.20.1 里它是 <b>public</b> ✓（已核对源码 ✓）⇒ 不用 AT/mixin ✓。
     */
    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 1) return;                              // 只认右键 ✓
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !slot.hasItem()) return;

        ItemStack hovered = slot.getItem();
        String bookId = CompendiumItem.bookIdOf(hovered);
        ItemStack carriedStack = screen.getMenu().getCarried();
        boolean carried = carriedStack.getItem() instanceof CompendiumItem;
        boolean inHand = player.getMainHandItem().getItem() instanceof CompendiumItem;
        boolean inBag = !carried && !inHand && CompendiumItem.findInInventory(player) != null;

        // 探针：只要"这次右键跟百宝书有关"就打一行 ✓（无关就一声不响 ✓ 不刷屏 ✓）
        if (carried || inHand || inBag) {
            LOG.info("[百宝书] 右键：界面={} 槽位={} 槽内={} 读出书id={} 光标={} 主手={} 背包={} 创造={}",
                    screen.getClass().getSimpleName(), slot.index, hovered.getDescriptionId(),
                    bookId, carried, inHand, inBag, player.isCreative());
        }

        if (bookId == null) return;                                       // 不是帕秋莉书 ⇒ 完全不插手 ✓
        if (!carried && !inHand && !inBag) return;                        // 手上/包里没百宝书 ⇒ 不插手 ✓

        int source;
        if (carried && player.isCreative()) {
            // §910d 创造模式：光标那叠服务端看不见 ✗ ⇒ 本地登记 ✓ ＋ 让服务端只吃掉这本书 ✓
            boolean first = CompendiumItem.absorb(carriedStack, bookId, hovered.getHoverName().getString());
            LOG.info("[百宝书] 创造模式光标：本地登记完成（第一次={}）现在共 {} 本 ⇒ 请服务端消费该书",
                    first, CompendiumItem.absorbedCount(carriedStack));
            source = PacketCompendiumAbsorb.SOURCE_CLIENT_LOCAL;
        } else if (carried) {
            source = PacketCompendiumAbsorb.SOURCE_CARRIED;
        } else if (inHand) {
            source = PacketCompendiumAbsorb.SOURCE_MAIN_HAND;
        } else {
            source = PacketCompendiumAbsorb.SOURCE_INVENTORY;
        }

        TinkersNewlife.CHANNEL.sendToServer(new PacketCompendiumAbsorb(bookId, source));
        LOG.info("[百宝书] 已发包：书id={} 来源={}", bookId, source);
        event.setCanceled(true);                                          // 拦掉原版的"放一个" ✗
    }
}
