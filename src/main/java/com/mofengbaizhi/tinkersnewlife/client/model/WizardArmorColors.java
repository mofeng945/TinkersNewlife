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

    /**
     * ⭐ <b>首选</b>：向匠魂**运行时**要材料自带的颜色（{@code MaterialRenderInfo#vertexColor} ✓）。
     *
     * <p>为什么必须这样（踩过的坑 ✗）：一开始只用上面那张**硬编码 12 材料表** ✗ ⇒
     * 用户拿 <b>铜</b>（{@code tconstruct:copper}）+ <b>圣灵</b>（{@code tinkersnewlife:holy_spirit}）做法袍时，
     * 两个材料都不在表里 ✗ ⇒ 每槽都落到兜底色 ⇒ <b>整件全紫</b> ✗（用户实测 ✓）。
     * 这跟帽子是**同一套代码** ✓，所以帽子只要材料碰巧在表里就正常 ⇒ 问题一直在"颜色来源"而不是"分组" ✗。
     *
     * <p>改成读匠魂的材料渲染信息后，<b>任何材料都自动正确</b> ✓（铜 ✓、圣灵 ✓、别的模组的材料 ✓），
     * 帽子与法袍一起受益 ✓（同一套 {@code group()} 路径 ✓）。
     *
     * @param materialId 完整材料 id（如 {@code tconstruct:copper}、{@code tconstruct:wood#oak} ✓）
     * @return RGB，读不到返回 {@code null}（由调用方退回硬编码表 ✓）
     */
    @javax.annotation.Nullable
    public static float[] ofId(String materialId) {
        if (materialId == null || materialId.isEmpty()) return null;
        try {
            slimeknights.tconstruct.library.materials.definition.MaterialVariantId id =
                    slimeknights.tconstruct.library.materials.definition.MaterialVariantId.parse(materialId);
            java.util.Optional<slimeknights.tconstruct.library.client.materials.MaterialRenderInfo> info =
                    slimeknights.tconstruct.library.client.materials.MaterialRenderInfoLoader.INSTANCE.getRenderInfo(id);
            if (info.isPresent()) {
                int c = info.get().vertexColor();
                // -1 / 0 = 匠魂里"没定义颜色"⇒ 当作读不到 ✓
                if (c != 0 && c != -1) {
                    return new float[] { ((c >> 16) & 0xFF) / 255.0F, ((c >> 8) & 0xFF) / 255.0F, (c & 0xFF) / 255.0F };
                }
            }
        } catch (Throwable ignored) {
            // 匠魂没加载 / 解析失败 ⇒ 退回硬编码表 ✓
        }
        return null;
    }

    /**
     * 完整解析链：<b>匠魂运行时材料色</b>（首选 ✓）⇒ 硬编码 12 材料表（兜底 ✓）⇒ {@link #FALLBACK}（最后兜底 ✓）。
     *
     * @param materialId 完整材料 id（可带命名空间/变体 ✓，也容忍只给路径 ✓）
     */
    public static float[] resolve(String materialId) {
        float[] c = ofId(materialId);
        if (c != null) return c;
        return of(pathOf(materialId));
    }

    /** 取材料 id 的路径部分（{@code tconstruct:copper} → {@code copper} ✓；带变体的取主材料 ✓） */
    @javax.annotation.Nullable
    public static String pathOf(String materialId) {
        if (materialId == null || materialId.isEmpty()) return null;
        String s = materialId;
        int hash = s.indexOf('#');
        if (hash >= 0) s = s.substring(0, hash);
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1) : s;
    }
}
