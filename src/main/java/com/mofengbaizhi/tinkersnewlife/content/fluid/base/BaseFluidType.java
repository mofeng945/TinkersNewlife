package com.mofengbaizhi.tinkersnewlife.content.fluid.base;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;

import java.util.function.Consumer;

/**
 * 流体类型基类
 * 提供通用的纹理和颜色处理，子类可选择硬编码或使用 Mantle JSON
 */
public abstract class BaseFluidType extends FluidType {

    protected final ResourceLocation stillTexture;
    protected final ResourceLocation flowingTexture;
    protected final int tintColor;

    public BaseFluidType(Properties properties, ResourceLocation stillTexture,
                         ResourceLocation flowingTexture, int tintColor) {
        super(properties);
        this.stillTexture = stillTexture;
        this.flowingTexture = flowingTexture;
        this.tintColor = tintColor;
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return stillTexture;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return flowingTexture != null ? flowingTexture : stillTexture;
            }

            @Override
            public int getTintColor() {
                return tintColor;
            }
        });
    }
}