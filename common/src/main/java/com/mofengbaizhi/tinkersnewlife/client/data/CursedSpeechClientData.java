package com.mofengbaizhi.tinkersnewlife.client.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 咒言编辑客户端缓存（打开编辑屏时由 S2C 填入；本地选择后即时更新）。
 */
public final class CursedSpeechClientData {

    private static List<String> learned = new ArrayList<>();
    private static List<String> chant = new ArrayList<>();

    private CursedSpeechClientData() {}

    public static void set(List<String> learnedIn, List<String> chantIn) {
        learned = new ArrayList<>(learnedIn == null ? List.of() : learnedIn);
        chant = new ArrayList<>(chantIn == null ? List.of() : chantIn);
        while (chant.size() < 6) chant.add("");
    }

    public static List<String> learned() {
        return new ArrayList<>(learned);
    }

    /** 当前组合第 index 段词条 id（可能为空串） */
    public static String part(int index) {
        return index >= 0 && index < chant.size() ? chant.get(index) : "";
    }

    public static void setPart(int index, String wordId) {
        if (index >= 0 && index < 6) {
            while (chant.size() <= index) chant.add("");
            chant.set(index, wordId);
        }
    }

    /** 是否已学会某词条 */
    public static boolean knows(String id) {
        return learned.contains(id);
    }
}
