package com.mofengbaizhi.tinkersnewlife.client.handler;

import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.tools.client.SlimeskullArmorModel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.function.Function;

/**
 * 诡厄巫法联动·黏液头颅（slimeskull）客户端：
 * 高头骨（goety:tall_skull）本身是可佩戴头颅模型，把它注册为 tinkersnewlife:tall_skull 材料
 * 的黏液头颅外观（仿 TwilightForest CTKClient）。
 * 软依赖：goety 未装时反射失败 → 不注册（默认黏液头颅外观），安全降级。
 */
@Mod.EventBusSubscriber(modid = com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class GoetySlimeskullClient {

    public static final MaterialId TALL_SKULL_MATERIAL = new MaterialId(new ResourceLocation(com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID + ":tall_skull"));

    static {
        try {
            register();
        } catch (Throwable t) {
            // goety 未装/接口变动：保留默认黏液头颅外观
        }
    }

    private static void register() throws Throwable {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("goety")) return;

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
