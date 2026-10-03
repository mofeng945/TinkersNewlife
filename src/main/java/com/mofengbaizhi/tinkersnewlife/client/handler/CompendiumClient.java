package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.screen.CompendiumScreen;
import com.mofengbaizhi.tinkersnewlife.content.item.CompendiumItem;
import com.mofengbaizhi.tinkersnewlife.network.compendium.PacketCompendiumAbsorb;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>帕秋莉的百宝书 —— 客户端部分</b>（§910）：开界面 ＋ 物品栏里的"吞噬"手势 ✓。
 *
 * <p>挂的是 **Forge 总线**（注解里不写 bus ⇒ 默认 FORGE ✓）＋ {@code Dist.CLIENT} ✓
 * ⇒ 专服加载不到这里 ✓ 不会 NoClassDefFoundError ✗。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public final class CompendiumClient {

    private CompendiumClient() {}

    /** 手持右键 ⇒ 打开"查阅"界面 ✓（由 {@code CompendiumItem#use} 经 DistExecutor 调过来 ✓） */
    public static void openScreen(ItemStack stack) {
        Minecraft.getInstance().setScreen(new CompendiumScreen(stack));
    }

    /**
     * §910 <b>物品栏手势</b>：光标上拖着百宝书（或主手握百宝书）＋ **右键**一格**帕秋莉书** ⇒ 吞噬 ✓。
     *
     * <p>为什么挂 {@code ScreenEvent.MouseButtonPressed.Pre}：
     * 它比原版的槽位处理**更早** ✓ ⇒ 认出来就能 {@code setCanceled(true)} 拦掉原版那一手
     * （否则拖着东西右键会把百宝书"放一个进去" ✗）。
     * <p>为什么用 {@code getSlotUnderMouse()}：1.20.1 里它是 **public** ✓（已核对源码 ✓）⇒ 不用 AT/mixin ✓。
     * <p>⚠ 客户端只负责"识别手势 + 发包" ✓ **真正的改动全在服务端** ✓（见 {@link PacketCompendiumAbsorb} ✓）。
     */
    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 1) return;                              // 只认右键 ✓
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || !slot.hasItem()) return;
        if (CompendiumItem.bookIdOf(slot.getItem()) == null) return;      // 不是帕秋莉书 ⇒ 完全不插手 ✓

        boolean fromCarried = screen.getMenu().getCarried().getItem() instanceof CompendiumItem;
        boolean fromHand = !fromCarried && Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.getMainHandItem().getItem() instanceof CompendiumItem;
        if (!fromCarried && !fromHand) return;                            // 手里没百宝书 ⇒ 不插手 ✓

        TinkersNewlife.CHANNEL.sendToServer(new PacketCompendiumAbsorb(slot.index, fromCarried));
        event.setCanceled(true);                                          // 拦掉原版的"放一个" ✗
    }
}
