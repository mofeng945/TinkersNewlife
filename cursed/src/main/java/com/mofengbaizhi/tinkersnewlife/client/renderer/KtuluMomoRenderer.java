package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.client.model.MomoGeoModel;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * TNL1293 克图露·墨默渲染器 —— **改用 GeckoLib**
 *
 * <p>用户口径：「干脆把玩家实体模型复制出来我去 bb 里面写动画」＋选 GeckoLib 路线 ✓
 *
 * <h3>和旧版（`HumanoidMobRenderer` ＋ `KtuluMomoModel`）的区别</h3>
 * <ul>
 *   <li>旧版：姿势/动画**全写在 Java** 里（`setupAnim` 一堆 if）✗ 美术改一次我就要抄一次 ✓；</li>
 *   <li>现在：**几何读 `geo/ktulu_momo.geo.json`、动画读 `animations/ktulu_momo.animation.json`**
 *       ⇒ 美术在 BB（GeckoLib 插件）里改完导出覆盖即可 ✓（见 `MomoGeoModel` ✓）。</li>
 * </ul>
 *
 * <p>姿势仍然由实体上报的**同步字节**决定（`KtuluMomo#tnl$animPose()`），
 * 对应关系在 `KtuluMomo#tnl$pickPoseAnimation`：1 blink / 2 resonance / 3 chord /
 * 4 hurt / 5 requiem / 6 kick。
 *
 * <p>⚠ 旧的"瞬身残影/和弦环"绘制随这次迁移**移除**（它们挂在旧的人类模型上）；
 * 若需要，之后用 GeckoLib 的渲染层重做。
 */
public class KtuluMomoRenderer extends GeoEntityRenderer<KtuluMomo> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("tinkersnewlife", "textures/entity/ktulu_momo.png");

    /** 与旧版一致的模型缩放 / 影子大小 */
    private static final float MODEL_SCALE = 0.85F;

    public KtuluMomoRenderer(EntityRendererProvider.Context context) {
        super(context, new MomoGeoModel());
        this.shadowRadius = 0.43F;
        this.scaleWidth = MODEL_SCALE;
        this.scaleHeight = MODEL_SCALE;
    }

    @Override
    public ResourceLocation getTextureLocation(KtuluMomo entity) {
        return TEXTURE;
    }
}
