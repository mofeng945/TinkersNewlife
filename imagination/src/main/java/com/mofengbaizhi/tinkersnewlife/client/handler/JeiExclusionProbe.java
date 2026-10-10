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
            dumpModules(screen);
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
            Object managers = f.get(helper);
            if (managers == null) {
                TinkersNewlife.LOGGER.info("[JEI-PROBE] {} == null", fieldName);
                return;
            }
            // GuiContainerHandlers (or the global list) -> entries
            List<?> entries;
            if (managers instanceof List<?> l) {
                entries = l;
            } else {
                Field ef = findField(managers.getClass(), "entries");
                if (ef == null) {
                    TinkersNewlife.LOGGER.info("[JEI-PROBE] {} has no entries field ({})", fieldName, managers.getClass().getName());
                    return;
                }
                ef.setAccessible(true);
                entries = (List<?>) ef.get(managers);
            }
            TinkersNewlife.LOGGER.info("[JEI-PROBE] {} entries={}", fieldName, entries == null ? "null" : entries.size());
            if (entries == null) {
                return;
            }
            for (Object entry : entries) {
                Object cls = readField(entry, "containerClass");
                List<?> handlers = readList(entry, "handlers");
                boolean applies = cls instanceof Class<?> c
                        ? (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
                           && c.isInstance(screen))
                        : fieldName.contains("global");
                if (handlers == null) {
                    continue;
                }
                for (Object h : handlers) {
                    if (applies) {
                        report(h, screen, fieldName + "[" + (cls instanceof Class<?> cc ? cc.getName() : "?") + "]");
                    } else {
                        TinkersNewlife.LOGGER.info("[JEI-PROBE] (skip) {} registered for {}",
                                h.getClass().getName(), cls instanceof Class<?> cc ? cc.getName() : "?");
                    }
                }
            }
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.info("[JEI-PROBE] dump {} failed: {}", fieldName, t.toString());
        }
    }

    private static Object readField(Object holder, String name) {
        try {
            Field f = findField(holder.getClass(), name);
            if (f == null) {
                return null;
            }
            f.setAccessible(true);
            return f.get(holder);
        } catch (Throwable t) {
            return null;
        }
    }

    private static List<?> readList(Object holder, String name) {
        Object v = readField(holder, name);
        return v instanceof List<?> l ? l : null;
    }

    private static void report(Object handler, Screen screen, String group) {
        try {
            java.lang.reflect.Method m = findMethod(handler.getClass(), "getGuiExtraAreas", net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class);
            if (m == null) { TinkersNewlife.LOGGER.info("[JEI-PROBE] {}.{} has no getGuiExtraAreas", group, handler.getClass().getName()); return; }
            m.setAccessible(true);
            Object res = m.invoke(handler, screen);
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

    private static java.lang.reflect.Method findMethod(Class<?> cls, String name, Class<?>... params) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    /** §1287 dump the Mantle module list: which module has a broken width, and how wide the screen is. */
    /** §1287 dump the Mantle module list with pure reflection (1.20.1 widgets keep x/y/width protected). */
    private static void dumpModules(Screen screen) {
        try {
            StringBuilder head = new StringBuilder();
            for (String n : new String[] { "imageWidth", "imageHeight", "leftPos", "topPos", "xSize", "ySize" }) {
                Object v = readField(screen, n);
                if (v != null) {
                    head.append(' ').append(n).append('=').append(v);
                }
            }
            TinkersNewlife.LOGGER.info("[JEI-PROBE] screen {} ->{}", screen.getClass().getSimpleName(), head);
            Field mf = findField(screen.getClass(), "modules");
            if (mf == null) {
                TinkersNewlife.LOGGER.info("[JEI-PROBE] no modules field on {}", screen.getClass().getName());
                return;
            }
            mf.setAccessible(true);
            Object mods = mf.get(screen);
            if (!(mods instanceof List<?> list)) {
                TinkersNewlife.LOGGER.info("[JEI-PROBE] modules not a List: {}", mods == null ? "null" : mods.getClass().getName());
                return;
            }
            TinkersNewlife.LOGGER.info("[JEI-PROBE] modules size={}", list.size());
            for (Object m : list) {
                StringBuilder sb = new StringBuilder();
                for (Field f : m.getClass().getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(m);
                        if (v instanceof Integer || v instanceof Boolean || v instanceof String) {
                            sb.append(' ').append(f.getName()).append('=').append(v);
                        } else if (v != null) {
                            sb.append(' ').append(f.getName()).append('<').append(v.getClass().getSimpleName()).append('>');
                            for (String wn : new String[] { "x", "y", "width", "height", "leftPos", "topPos" }) {
                                Object wv = readField(v, wn);
                                if (wv != null) {
                                    sb.append(' ').append(wn).append('=').append(wv);
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
                TinkersNewlife.LOGGER.info("[JEI-PROBE] module {} ->{}", m.getClass().getSimpleName(), sb);
            }
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.info("[JEI-PROBE] dumpModules failed: {}", t.toString());
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
