package com.mofengbaizhi.tinkersnewlife.client.model;

import java.util.HashMap;
import java.util.Map;

/**
 * 材料 → 颜色（由 tools 脚本从 assets/tinkersnewlife/mantle/colors.json 抽出，共 12 个可做盔甲的材料）。
 *
 * <p>为什么单独生成一张表：{@code ModelPart#render(...)} 自带颜色参数 ⇒ 一张灰阶底图 + 每槽材料色
 * 顶点着色就能实现"混搭材料 = 多色"，不必为每个材料预生成贴图；而运行时读 JSON 又太绕 ✗。
 */
public final class WizardArmorColors {

    private WizardArmorColors() {}

    private static final Map<String, int[]> BY_MATERIAL = new HashMap<>();
    /** 取不到材料色时的兜底（法袍紫） */
    public static final float[] FALLBACK = { 0.42F, 0.33F, 0.58F };

    static {
        BY_MATERIAL.put("arcane_iron", new int[] { 136, 140, 204 });
        BY_MATERIAL.put("ashen_ink", new int[] { 192, 192, 192 });
        BY_MATERIAL.put("cursed_metal", new int[] { 67, 106, 132 });
        BY_MATERIAL.put("dark_metal", new int[] { 52, 53, 64 });
        BY_MATERIAL.put("divine_gold", new int[] { 255, 190, 46 });
        BY_MATERIAL.put("dragonsteel_fire", new int[] { 255, 85, 85 });
        BY_MATERIAL.put("dragonsteel_ice", new int[] { 85, 255, 255 });
        BY_MATERIAL.put("dragonsteel_lightning", new int[] { 255, 255, 85 });
        BY_MATERIAL.put("dreadsteel", new int[] { 255, 85, 255 });
        BY_MATERIAL.put("magic_gold", new int[] { 223, 168, 69 });
        BY_MATERIAL.put("origin_alloy", new int[] { 154, 146, 232 });
        BY_MATERIAL.put("pyrium", new int[] { 255, 174, 60 });
    }

    /** 材料 id（路径）→ 归一化 RGB；没有登记时给兜底色 ✓ */
    public static float[] of(String materialPath) {
        int[] c = materialPath == null ? null : BY_MATERIAL.get(materialPath);
        if (c == null) return FALLBACK;
        return new float[] { c[0] / 255.0F, c[1] / 255.0F, c[2] / 255.0F };
    }
}
