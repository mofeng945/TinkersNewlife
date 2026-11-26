package com.mofengbaizhi.tinkersnewlife.client.handler;

import java.util.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tinkersnewlife",value=Dist.CLIENT)
public final class SandBurrowClient {
    private static final Set<UUID> players=new HashSet<>();
    private static CameraType saved;
    public static void update(UUID id,boolean active){if(active)players.add(id);else players.remove(id);}
    public static boolean submerged(Player p){return players.contains(p.getUUID())&&p.isAlive();}
    private static void restore(){if(saved!=null){Minecraft.getInstance().options.setCameraType(saved);saved=null;}}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||!submerged(mc.player)){restore();return;}
        if(saved==null)saved=mc.options.getCameraType();
        if(mc.options.getCameraType()!=CameraType.THIRD_PERSON_BACK)mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }
    @SubscribeEvent public static void hands(RenderHandEvent e){var p=Minecraft.getInstance().player;if(p!=null&&submerged(p))e.setCanceled(true);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){players.clear();restore();}
}
