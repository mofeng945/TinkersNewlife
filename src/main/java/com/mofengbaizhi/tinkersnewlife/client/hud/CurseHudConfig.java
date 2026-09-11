package com.mofengbaizhi.tinkersnewlife.client.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 咒术 HUD 的位置/宽度存储（<b>独立小文件</b>，由游戏内拖动界面写入）。
 *
 * <p>为什么不放 Forge 主配置：主配置是 COMMON 类型、由 Forge 统一管理写回时机，
 * 游戏内频繁拖动时直接改它容易和 Forge 的写回打架；这里用一个自己的
 * {@code config/mofengbaizhi/curse_hud.json}，读写完全可控、也能手改。
 */
public final class CurseHudConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("mofengbaizhi/curse_hud.json");

    public static boolean enabled = true;
    public static int x = 6;
    public static int y = 6;
    public static int width = 104;

    private static boolean loaded = false;

    private CurseHudConfig() {}

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        load();
    }

    public static void load() {
        try {
            if (!Files.exists(PATH)) return;
            String text = Files.readString(PATH, StandardCharsets.UTF_8);
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            if (o.has("enabled")) enabled = o.get("enabled").getAsBoolean();
            if (o.has("x")) x = o.get("x").getAsInt();
            if (o.has("y")) y = o.get("y").getAsInt();
            if (o.has("width")) width = o.get("width").getAsInt();
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[咒术HUD] 读取 curse_hud.json 失败，使用默认位置: {}", t.toString());
        }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", enabled);
            o.addProperty("x", x);
            o.addProperty("y", y);
            o.addProperty("width", width);
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[咒术HUD] 写入 curse_hud.json 失败: {}", t.toString());
        }
    }

    public static boolean isEnabled() {
        ensureLoaded();
        return enabled;
    }

    public static int getX() {
        ensureLoaded();
        return x;
    }

    public static int getY() {
        ensureLoaded();
        return y;
    }

    public static int getWidth() {
        ensureLoaded();
        return width;
    }
}
