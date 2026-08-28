package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.event.SlotModifiersUpdatedEvent;
import top.theillusivec4.curios.api.type.ICuriosMenu;

/**
 * 客户端 curios 菜单动态刷新
 * <p>
 * 服务端槽位修饰符变化（如静默手套增减戒指槽）后，curios 会同步客户端并触发
 * {@link SlotModifiersUpdatedEvent}；此时本地 curios 数据已更新、菜单内容同步包
 * 尚未到达，正好原地重建打开的 curios 菜单（resetSlots），实现槽位动态增减
 * 而无需关闭/重开界面，也不会触发槽数不一致的越界。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CuriosMenuRefreshHandler {

    @SubscribeEvent
    public static void onSlotModifiersUpdated(SlotModifiersUpdatedEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // 只处理本地玩家
        if (event.getEntity() != mc.player) return;
        if (mc.player.containerMenu instanceof ICuriosMenu menu) {
            menu.resetSlots();
        }
    }
}
