package com.mofengbaizhi.tinkersnewlife.content.fluid.type;

import com.mofengbaizhi.tinkersnewlife.content.fluid.base.BaseFluidType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidType;

/**
 * 简单流体类型 - 直接硬编码纹理路径
 */
public class SimpleFluidType extends BaseFluidType {

    public SimpleFluidType(Properties properties, ResourceLocation stillTexture,
                           ResourceLocation flowingTexture, int tintColor) {
        super(properties, stillTexture, flowingTexture, tintColor);
    }
}