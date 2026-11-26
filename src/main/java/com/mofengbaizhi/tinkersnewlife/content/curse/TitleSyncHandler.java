package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** §1305 登录时同步"持有 ＋ 选中"头衔（★选择界面要用 ✓）。 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TitleSyncHandler {

    private TitleSyncHandler() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            if (event.getEntity() instanceof ServerPlayer sp) {
                // §1306 延迟 40 tick 再发（★登录瞬间连接可能还没完全就绪 ✓）
                var server = sp.getServer();
                if (server != null) {
                    server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 40, () -> {
                        if (!sp.isRemoved()) {
                            CurseTitles.sync(sp);
                        }
                    }));
                }
            }
        } catch (Throwable ignored) {
        }
    }
}