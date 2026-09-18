package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;

/**
 * 巫师套装的<b>贴图解析器</b>：决定"这件甲、这一组部件"到底用哪张贴图。
 *
 * <h2>它要解决的两种贴图来源（对应"接匠魂生成器"的两条路）</h2>
 * <ol>
 *   <li><b>匠魂生成器产出的按材料贴图</b>（首选 ✓）——
 *       命名沿匠魂盔甲惯例：{@code <prefix><材料>armor.png}（腿部层是 {@code ...leggings.png}）。
 *       匠魂本体那批是构建期生成的 ✗；我们这侧可以由游戏内指令
 *       （{@code /tconstruct generate_part_textures}，见 {@code tinkering/generator_part_textures.json} 的登记 ✓）
 *       或 {@code tools/gen-wizard-armor-materials.ps1} 产出 ✓；</li>
 *   <li><b>兜底的灰阶底图</b>（现在在用 ✓）——{@code all_grey.png}，
 *       颜色由 {@code WizardArmorModel} 的<b>顶点着色</b>按材料槽染 ✓。</li>
 * </ol>
 *
 * <h2>为什么单独一个类</h2>
 * 现在渲染走 {@code Item#getArmorTexture}（整件只能给**一张**贴图 ✗），
 * 而"每个部件组各用自己材料的那张图"必须在<b>渲染层</b>里逐组取图 ✓ ——
 * 所以把"材料 → 贴图路径"的判断独立出来 ✓，渲染层接上时直接调 {@link #materialArmorTexture} 即可 ✓。
 *
 * <p>结果有缓存（材质包重载后调 {@link #clearCache()} ✓），避免每次渲染都查资源管理器 ✗。
 */
public final class WizardArmorTextures {

    private WizardArmorTextures() {}

    /** 兜底灰阶底图（配合顶点着色 ✓） */
    public static final ResourceLocation GREY = tex("armor/wizard/grey.png");

    /** 各组的贴图前缀（与匠魂盔甲惯例一致：<prefix><材料>armor.png / ...leggings.png ✓） */
    public static final String PREFIX_HAT = "armor/wizard/hat";
    public static final String PREFIX_ROBE = "armor/wizard/robe";
    public static final String PREFIX_LEGGINGS = "armor/wizard/mage_leggings";
    public static final String PREFIX_BOOTS = "armor/wizard/mage_boots";

    /** 存在性缓存：key = 完整贴图路径，value = 是否真的存在 ✓ */
    private static final Map<String, Boolean> EXISTS = new HashMap<>();

    private static ResourceLocation tex(String path) {
        return new ResourceLocation(TinkersNewlife.MOD_ID, "textures/" + path);
    }

    /** 渲染层是否正常工作（正常 ⇒ 本模组自己画、原版层画透明图避免重复 ✓；异常 ⇒ 回退原版层画灰图 ✓） */
    private static volatile boolean layerOk = false;
    /** 懒烘焙的自绘模型（渲染层用 ✓） */
    private static com.mofengbaizhi.tinkersnewlife.client.model.WizardArmorModel model;

    public static boolean isLayerOk() { return layerOk; }
    public static void setLayerOk(boolean ok) { layerOk = ok; }

    /** 透明贴图（渲染层接手时用，避免原版层重复绘制 ✓；由 tools 脚本生成 ✓） */
    public static final ResourceLocation TRANSPARENT = tex("armor/wizard/transparent.png");

    /** 懒烘焙自绘模型（失败返回 null ⇒ 渲染层直接跳过，外观由原版层兜底 ✓） */
    public static com.mofengbaizhi.tinkersnewlife.client.model.WizardArmorModel model() {
        try {
            if (model == null) {
                model = new com.mofengbaizhi.tinkersnewlife.client.model.WizardArmorModel(
                        net.minecraft.client.Minecraft.getInstance().getEntityModels()
                                .bakeLayer(com.mofengbaizhi.tinkersnewlife.client.model.WizardArmorModel.LAYER));
            }
            return model;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 材质包/资源重载后清缓存 ✓ */
    public static void clearCache() {
        EXISTS.clear();
    }

    /**
     * 某一组的材料贴图（{@code <prefix><材料>armor.png}）——
     * 不存在时返回 {@code null} ⇒ 调用方退回 {@link #GREY} + 顶点着色 ✓。
     *
     * @param prefix       组的贴图前缀（见上面的常量 ✓）
     * @param materialPath 材料 id 的路径部分（如 {@code origin_alloy} ✓）
     * @param leggings     是否取腿部层（{@code ...leggings.png} ✓）
     */
    public static ResourceLocation materialArmorTexture(String prefix, String materialPath, boolean leggings) {
        if (materialPath == null || materialPath.isEmpty()) return null;
        // 生成器命名规则：<底图路径>_<命名空间>_<材料>（见 TConstructGeneratedPartTextures 输出 ✓）
        String path = prefix + "_" + TinkersNewlife.MOD_ID + "_" + materialPath + ".png";
        Boolean cached = EXISTS.get(path);
        if (cached != null) return cached ? tex(path) : null;
        boolean exists = false;
        try {
            ResourceLocation rl = tex(path);
            exists = Minecraft.getInstance().getResourceManager().getResource(rl).isPresent();
        } catch (Throwable ignored) {
            // 资源管理器不可用（极早期/异常）⇒ 当作不存在，走兜底 ✓
        }
        EXISTS.put(path, exists);
        return exists ? tex(path) : null;
    }

    /** 按装备槽取"这一件的组的贴图前缀" ✓ */
    public static String prefixFor(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> PREFIX_HAT;
            case CHEST -> PREFIX_ROBE;
            case LEGS -> PREFIX_LEGGINGS;
            default -> PREFIX_BOOTS;
        };
    }

    /** 腿部层判定：护腿与靴子这两件会取 {@code ...leggings.png} ✓ */
    public static boolean usesLeggingsLayer(EquipmentSlot slot) {
        return slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }
}
