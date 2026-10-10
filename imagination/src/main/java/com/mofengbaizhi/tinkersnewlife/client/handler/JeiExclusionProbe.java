package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.compat.jei.TinkersNewlifeJeiPlugin;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Stream;

/**
 * §1285 probe: ask JEI exactly which GUI exclusion rectangles it receives for a Tinker
 * Station screen, and which registered handler produced each one.
 *
 * Background (decompiled JEI 15.48): ScreenHelper#getGuiExclusionAreas only collects
 * IGuiContainerHandler#getGuiExtraAreas (matched by screen class) plus every
 * IGlobalGuiHandler#getGuiExtraAreas. ImmutableRect2i then rejects width < 0, which is
 * the crash we are chasing. This probe never mutates anything.
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class JeiExclusionProbe {

    private JeiExclusionProbe() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        try {
            Screen screen = event.getScreen();
            if (!(screen instanceof slimeknights.tconstruct.tables.client.inventory.TinkerStationScreen)) {
                return;
            }
            TinkersNewlife.LOGGER.info("[JEI-PROBE] screen={}", screen.getClass().getName());
            Object runtime = TinkersNewlifeJeiPlugin.RUNTIME;
            if (runtime == null) {
                TinkersNewlife.LOGGER.info("[JEI-PROBE] runtime == null (JEI not ready)");
                return;
            }
            Object helper = runtime.getClass().getMethod("getScreenHelper").invoke(runtime);
            TinkersNewlife.LOGGER.info("[JEI-PROBE] screenHelper={}", helper == null ? "null" : helper.getClass().getName());
            if (helper == null) {
                return;
            }
            // 1) the API answer: which rectangles would JEI try to wrap?
            try {
                Object streamObj = helper.getClass().getMethod("getGuiExclusionAreas", Screen.class).invoke(helper, screen);
                if (streamObj instanceof Stream<?> stream) {
                    stream.forEach(r -> {
                        if (r instanceof Rect2i rect) {
                            TinkersNewlife.LOGGER.info("[JEI-PROBE] api-rect x={} y={} w={} h={}{}",
                                    rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight(),
                                    (rect.getWidth() < 0 || rect.getHeight() < 0) ? "   <== NEGATIVE (JEI would crash here)" : "");
                        }
                    });
                }
            } catch (Throwable t) {
                TinkersNewlife.LOGGER.info("[JEI-PROBE] api call failed: {}", t.toString());
            }
            // 2) name the culprits: walk the registered handlers
            dumpHandlers(helper, "guiContainerHandlers", screen);
            dumpHandlers(helper, "globalGuiHandlers", screen);
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.info("[JEI-PROBE] probe error: {}", t.toString());
        }
    }

    private static void dumpHandlers(Object helper, String fieldName, Screen screen) {
        try {
            Field f = findField(helper.getClass(), fieldName);
            if (f == null) {
                TinkersNewlife.LOGGER.info("[JEI-PROBE] field {} not found", fieldName);
                return;
            }
            f.setAccessible(true);
            Object holders = f.get(helper);
            if (!(holders instanceof List<?> list)) {
                TinkersNewlife.LOGGER.info("[JEI-PROBE] {} is not a List", fieldName);
                return;
            }
            TinkersNewlife.LOGGER.info("[JEI-PROBE] {} size={}", fieldName, list.size());
            for (Object holder : list) {
                Object handler = null;
                Object target = null;
                for (Field hf : holder.getClass().getDeclaredFields()) {
                    hf.setAccessible(true);
                    Object v = hf.get(holder);
                    if (v instanceof List<?> hs && !hs.isEmpty() && handler == null) {
                        target = holder;
                    }
                }
                for (Field hf : holder.getClass().getDeclaredFields()) {
                    hf.setAccessible(true);
                    Object v = hf.get(holder);
                    if (v instanceof List<?> hs) {
                        for (Object h : hs) {
                            report(h, screen, fieldName);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.info("[JEI-PROBE] dump {} failed: {}", fieldName, t.toString());
        }
    }

    private static void report(Object handler, Screen screen, String group) {
        try {
            Object res = handler.getClass().getMethod("getGuiExtraAreas",
                    net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class).invoke(handler, screen);
            StringBuilder sb = new StringBuilder();
            if (res instanceof java.util.Collection<?> col) {
                for (Object r : col) {
                    if (r instanceof Rect2i rect) {
                        sb.append(" [x=").append(rect.getX()).append(" y=").append(rect.getY())
                          .append(" w=").append(rect.getWidth()).append(" h=").append(rect.getHeight()).append("]");
                        if (rect.getWidth() < 0 || rect.getHeight() < 0) {
                            sb.append("  <== NEGATIVE");
                        }
                    }
                }
            }
            TinkersNewlife.LOGGER.info("[JEI-PROBE] {}.{} ->{}", group, handler.getClass().getName(),
                    sb.length() == 0 ? " (empty)" : sb.toString());
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.info("[JEI-PROBE] {}.{} threw {}", group, handler.getClass().getName(), t.toString());
        }
    }

    private static Field findField(Class<?> cls, String name) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }
}