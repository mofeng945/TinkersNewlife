package com.mofengbaizhi.tinkersnewlife.integration.goety.client;

import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.tools.client.SlimeskullArmorModel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.function.Function;

/**
 * 诡厄巫法联动·黏液头颅（slimeskull）客户端：
 * 高头骨（goety:tall_skull）本身是可佩戴头颅模型，把它注册为 tinkersnewlife:tall_skull 材料
 * 的黏液头颅外观（仿 TwilightForest CTKClient）。
 *
 * <p>软依赖：诡厄未装时反射失败 → 不注册（保留默认黏液头颅外观），安全降级。
 * 入口由 {@link GoetyClientIntegration} 在 {@code FMLClientSetupEvent} 阶段调用（存在性判定走 ModList）。
 * 本类只 import 客户端类型，不含任何诡厄编译引用，诡厄侧的模型/图层全部按类名反射获取。
 */
public final class GoetySlimeskullClient {

    public static final MaterialId TALL_SKULL_MATERIAL = new MaterialId(new ResourceLocation(TinkersNewlife.MOD_ID + ":tall_skull"));

    private GoetySlimeskullClient() {
    }

    /** 注册高头骨黏液头颅外观；任何失败都静默降级（保留默认外观） */
    public static void registerTallSkullHead() {
        if (!IntegrationLoader.isGoety()) return;
        try {
            register();
        } catch (Throwable t) {
            // 诡厄未装 / 模型类变动：保留默认黏液头颅外观
            TinkersNewlife.LOGGER.debug("[联动] 高头骨黏液头颅外观注册跳过：{}", t.toString());
        }
    }

    private static void register() throws Throwable {

        // 1. 反射取 goety 的 tall_skull 模型层
        ModelLayerLocation layer = tallSkullLayer();

        // 2. 模型函数：bakeLayer(层) → 反射构造 TallSkullModel(part)
        Function<EntityModelSet, ? extends SkullModelBase> modelFn = modelSet ->
                (SkullModelBase) buildModel(modelSet.bakeLayer(layer));

        // 3. 高头骨贴图（goety）
        ResourceLocation texture = new ResourceLocation("goety:textures/entity/servants/skeleton/skeleton_villager_servant.png");

        // 4. 注册到 tinkersnewlife:tall_skull 材料
        SlimeskullArmorModel.registerHeadModel(TALL_SKULL_MATERIAL, modelFn, texture);
    }

    private static ModelLayerLocation tallSkullLayer() throws Throwable {
        Class<?> cls = Class.forName("com.Polarice3.Goety.client.render.block.ModBlockLayer");
        Field f = cls.getField("TALL_SKULL");
        return (ModelLayerLocation) f.get(null);
    }

    private static Object constructTallSkull(ModelPart part) throws Throwable {
        Class<?> cls = Class.forName("com.Polarice3.Goety.client.render.model.TallSkullModel");
        Constructor<?> c = cls.getConstructor(ModelPart.class);
        return c.newInstance(part);
    }

    /** 免检包装：让反射抛出的已检查异常在 lambda 里可抛 */
    private static Object buildModel(ModelPart part) {
        try {
            return constructTallSkull(part);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
