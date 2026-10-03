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
 * <b>帕秋莉的百宝书 —— 客户端部分</b>（§910）：开界面 ＋ 物品栏里的"吞噬"手势 ✓。
 *
 * <p>挂的是 <b>Forge 总线</b>（注解里不写 bus ⇒ 默认 FORGE ✓）＋ {@code Dist.CLIENT} ✓
 * ⇒ 专服加载不到这里 ✓ 不会 NoClassDefFoundError ✗。
 *
 * <p>⚠ 事件链已核对（反汇编 Forge jar ✓）：{@code MouseHandler#onPress} →
 * {@code ForgeHooksClient.onScreenMouseClickedPre} ⇒ 正是 {@code ScreenEvent.MouseButtonPressed.Pre} ✓
 * ⇒ **GUI 里右键一定进得来** ✓。§910b 起这里把判定过程也打一行日志 ✓（上一版没反应时毫无线索 ✗）。
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
     * 它比原版的槽位处理**更早** ✓ ⇒ 认出来就能 {@code setCanceled(true)} 拦掉原版那一手
     * （否则拖着东西右键会把百宝书"放一个进去" ✗）。
     * <p>为什么用 {@code getSlotUnderMouse()}：1.20.1 里它是 <b>public</b> ✓（已核对源码 ✓）⇒ 不用 AT/mixin ✓。
     * <p>⚠ 客户端只负责"识别手势 + 发包" ✓ <b>真正的改动全在服务端</b> ✓（见 {@link PacketCompendiumAbsorb} ✓）。
     */
    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 1) return;                              // 只认右键 ✓
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Slot slot = screen.getSlotUnderMouse();

        boolean carried = screen.getMenu().getCarried().getItem() instanceof CompendiumItem;
        boolean inHand = player.getMainHandItem().getItem() instanceof CompendiumItem;
        boolean inBag = !carried && !inHand && CompendiumItem.findInInventory(player) != null;

        // §910b 探针：只要"这次右键跟百宝书有关"就打一行 ✓（无关就一声不响 ✓ 不刷屏 ✓）
        if (carried || inHand || inBag) {
            ItemStack hovered = slot == null ? ItemStack.EMPTY : slot.getItem();
            LOG.info("[百宝书] 右键：界面={} 槽位={} 槽内={} 读出书id={} 光标={} 主手={} 背包={}",
                    screen.getClass().getSimpleName(),
                    slot == null ? "null" : String.valueOf(slot.index),
                    hovered.isEmpty() ? "空" : hovered.getDescriptionId(),
                    CompendiumItem.bookIdOf(hovered),
                    carried, inHand, inBag);
        }

        if (slot == null || !slot.hasItem()) return;
        if (CompendiumItem.bookIdOf(slot.getItem()) == null) return;      // 不是帕秋莉书 ⇒ 完全不插手 ✓
        if (!carried && !inHand && !inBag) return;                        // 手上/包里没百宝书 ⇒ 不插手 ✓

        int source = carried ? PacketCompendiumAbsorb.SOURCE_CARRIED
                : inHand ? PacketCompendiumAbsorb.SOURCE_MAIN_HAND
                : PacketCompendiumAbsorb.SOURCE_INVENTORY;
        TinkersNewlife.CHANNEL.sendToServer(new PacketCompendiumAbsorb(slot.index, source));
        LOG.info("[百宝书] 已发包：槽位={} 来源={}", slot.index, source);
        event.setCanceled(true);                                          // 拦掉原版的"放一个" ✗
    }
}
