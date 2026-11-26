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
 * TNL891 ★★ 自己数蓄力（★不依赖原版使用状态 ✓）
 *
 * <p>★日志证据（TNL889）：★连续 24 秒 ``onUseTick useTime=0 remaining=72000`` ✗
 * ⇒ ★原版使用状态被反复重置 ✗ ⇒ ★蓄力动画/发射都起不来 ✓
 *
 * <p>★现在：★直接读**右键是否按下**（{@code keyUse.isDown()} ✓）
 * ★自己数 tick ✓ 写进手上那把弓的 NBT ``TnlCharge`` ✓
 * ⇒ ★模型谓词（★自己注册的 ``tinkersnewlife:charge`` ✓）读它换帧 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StarfallChargeHandler {

    /** ★蓄力上限（tick）✓ —— ★与原版弓一样 20 tick 满弦 ✓ */
    public static final int MAX_CHARGE = 20;
    /** ★上次发射的 tick（★防连发 ✓） */
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
                // ★松手 ⇒ ★发包发射 ✓（★服务端权威 ✓）
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

    /** ★读蓄力 tick ✓ */
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
