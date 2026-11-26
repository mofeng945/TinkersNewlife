package com.mofengbaizhi.tinkersnewlife.content.fluid.type;

import com.mofengbaizhi.tinkersnewlife.content.fluid.base.BaseFluidType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;
import slimeknights.mantle.fluid.texture.ClientTextureFluidType;

import java.util.function.Consumer;

/**
 * 使用 Mantle JSON 配置纹理的流体类型
 * JSON 路径：assets/[modid]/mantle/fluid_texture/[fluid_name].json
 */
public class MantleFluidType extends BaseFluidType {

    private ClientTextureFluidType clientExtension;

    public MantleFluidType(Properties properties, ResourceLocation defaultStill, int defaultTint) {
        super(properties, defaultStill, null, defaultTint);
    }

    @Override
    public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        if (clientExtension == null) {
            clientExtension = new ClientTextureFluidType(this);
        }
        consumer.accept(clientExtension);
    }
}