package com.mofengbaizhi.tinkersnewlife.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;

import java.io.Reader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 客户端语言索引：把<b>所有命名空间</b>的 {@code lang/en_us.json} 一次性读进内存，
 * 供 GUI 在中文环境下用<b>英文名</b>搜索（构筑选择界面 / 无为转变形态选择共用同一份缓存）。
 */
public final class LangNameIndex {

    private static Map<String, String> EN;

    private LangNameIndex() {}

    /** 取翻译键对应的英文名（小写）；没有则返回空串。首次调用会扫描资源包。 */
    public static String en(String translationKey) {
        if (translationKey == null) return "";
        if (EN == null) load();
        return EN.getOrDefault(translationKey, "");
    }

    private static synchronized void load() {
        if (EN != null) return;
        Map<String, String> map = new HashMap<>();
        EN = map;   // 先赋值：即便读失败也只尝试一次
        try {
            var rm = Minecraft.getInstance().getResourceManager();
            Map<net.minecraft.resources.ResourceLocation, Resource> found =
                    rm.listResources("lang", p -> p.getPath().endsWith("en_us.json"));
            for (Resource res : found.values()) {
                try (Reader reader = res.openAsReader()) {
                    var obj = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                    for (var e : obj.entrySet()) {
                        if (e.getValue().isJsonPrimitive()) {
                            map.put(e.getKey(), e.getValue().getAsString().toLowerCase(Locale.ROOT));
                        }
                    }
                } catch (Throwable ignored) {
                    // 单个资源包坏了不影响其它包
                }
            }
        } catch (Throwable ignored) {
            // 读不到就退化为按注册名搜索
        }
    }
}
