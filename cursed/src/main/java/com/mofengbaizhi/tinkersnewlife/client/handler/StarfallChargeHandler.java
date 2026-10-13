package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TNL891 \u2605\u2605 \u81ea\u5df1\u6570\u84c4\u529b\uff08\u2605\u4e0d\u4f9d\u8d56\u539f\u7248\u4f7f\u7528\u72b6\u6001 \u2713\uff09
 *
 * <p>\u2605\u65e5\u5fd7\u8bc1\u636e\uff08TNL889\uff09\uff1a\u2605\u8fde\u7eed 24 \u79d2 ``onUseTick useTime=0 remaining=72000`` \u2717
 * \u21d2 \u2605\u539f\u7248\u4f7f\u7528\u72b6\u6001\u88ab\u53cd\u590d\u91cd\u7f6e \u2717 \u21d2 \u2605\u84c4\u529b\u52a8\u753b/\u53d1\u5c04\u90fd\u8d77\u4e0d\u6765 \u2713
 *
 * <p>\u2605\u73b0\u5728\uff1a\u2605\u76f4\u63a5\u8bfb**\u53f3\u952e\u662f\u5426\u6309\u4e0b**\uff08{@code keyUse.isDown()} \u2713\uff09
 * \u2605\u81ea\u5df1\u6570 tick \u2713 \u5199\u8fdb\u624b\u4e0a\u90a3\u628a\u5f13\u7684 NBT ``TnlCharge`` \u2713
 * \u21d2 \u2605\u6a21\u578b\u8c13\u8bcd\uff08\u2605\u81ea\u5df1\u6ce8\u518c\u7684 ``tinkersnewlife:charge`` \u2713\uff09\u8bfb\u5b83\u6362\u5e27 \u2713\u3002
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StarfallChargeHandler {

    /** \u2605\u84c4\u529b\u4e0a\u9650\uff08tick\uff09\u2713 \u2014\u2014 \u2605\u4e0e\u539f\u7248\u5f13\u4e00\u6837 20 tick \u6ee1\u5f26 \u2713 */
    public static final int MAX_CHARGE = 20;
    /** \u2605\u4e0a\u6b21\u53d1\u5c04\u7684 tick\uff08\u2605\u9632\u8fde\u53d1 \u2713\uff09 */
    private static long lastShot = -100L;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        boolean ours = stack.getItem() == com.mofengbaizhi.tinkersnewlife.content.ModItems.STARFALL_LAMENT.get();
        boolean down = mc.options.keyUse.isDown();
        if (ours && down) {
            int c = tnl$charge(stack) + 1;
            if (c > MAX_CHARGE) {
                c = MAX_CHARGE;
            }
            tnl$setCharge(stack, c);
        } else if (ours) {
            int c = tnl$charge(stack);
            if (c > 0) {
                // \u2605\u677e\u624b \u21d2 \u2605\u53d1\u5305\u53d1\u5c04 \u2713\uff08\u2605\u670d\u52a1\u7aef\u6743\u5a01 \u2713\uff09
                long now = player.level().getGameTime();
                if (now - lastShot > 4L) {
                    lastShot = now;
                    TinkersNewlife.CHANNEL.sendToServer(
                            new com.mofengbaizhi.tinkersnewlife.network.starfall.PacketStarfallShoot(c));
                }
                tnl$setCharge(stack, 0);
            }
        } else {
            tnl$setCharge(stack, 0);
        }
    }

    /** \u2605\u8bfb\u84c4\u529b tick \u2713 */
    public static int tnl$charge(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt("TnlCharge") : 0;
    }

    private static void tnl$setCharge(ItemStack stack, int c) {
        if (c <= 0) {
            if (stack.hasTag()) {
                stack.getTag().remove("TnlCharge");
            }
        } else {
            stack.getOrCreateTag().putInt("TnlCharge", c);
        }
    }
}
