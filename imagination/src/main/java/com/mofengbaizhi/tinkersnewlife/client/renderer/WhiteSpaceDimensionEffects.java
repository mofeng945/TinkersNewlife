package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>伟大白色空间的天空</b>（§660）：一片恒定、不随昼夜变化的<b>白</b>。
 *
 * <h2>怎么做到「永远白」</h2>
 * <ol>
 *   <li>维度类型里 {@code fixed_time = 6000} ⇒ 时间永远停在正午，本来就没有昼夜更替 ✓；</li>
 *   <li>{@link SkyType#NONE} ⇒ <b>不画</b>太阳/月亮/星星/云（原版在 {@code LevelRenderer#renderSky}
 *       里对 NONE 直接跳过）⇒ 天空不会被任何天体"染色" ✓；</li>
 *   <li>{@link #getBrightnessDependentFogColor} 恒返回纯白 ⇒ 原版 {@code FogRenderer#setupColor}
 *       会拿这个返回值当<b>雾色</b>，并且用它 {@code RenderSystem.clearColor(...)}
 *       —— 因为天空本身没画，屏幕上"天"的那部分就是这块清屏色 ⇒ <b>四面八方全白</b> ✓
 *       （下界也是同一套机制：SkyType.NONE + 雾色 ⇒ 背景就是那个雾色）。</li>
 * </ol>
 *
 * <p>{@code isFoggyAt} 返回 false ⇒ 不做下界那种贴脸浓雾，远处才会慢慢化进白色里，
 * 视野还是够用的 ✓（这个维度是交通枢纽，门可能很多）。
 *
 * <p>构造参数对齐 {@code NetherEffects}：{@code forceBrightLightmap=false, constantAmbientLight=true}
 * ⇒ 实体/方块统一按环境亮度打光，配合维度类型的 {@code ambient_light = 1.0}，整个空间均匀发亮 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class WhiteSpaceDimensionEffects extends DimensionSpecialEffects {

    /** 与 {@code dimension_type/great_white_space.json} 的 {@code "effects"} 字段一致 */
    public static final ResourceLocation ID =
            new ResourceLocation(TinkersNewlife.MOD_ID, "great_white_space");

    private static final Vec3 WHITE = new Vec3(1.0D, 1.0D, 1.0D);

    public WhiteSpaceDimensionEffects() {
        // cloudLevel = NaN ⇒ 没有云；hasGround = true（同下界）
        super(Float.NaN, true, SkyType.NONE, false, true);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return WHITE;
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    @SubscribeEvent
    public static void onRegisterDimensionSpecialEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ID, new WhiteSpaceDimensionEffects());
    }
}
