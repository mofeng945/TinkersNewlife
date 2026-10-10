package com.mofengbaizhi.tinkersnewlife.integration.goety_revelation;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.fluid.FluidRegistrar;
import com.mofengbaizhi.tinkersnewlife.integration.Integration;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 诡厄巫法：启示录（goety_revelation）联动模块。
 *
 * <p>本模组与启示录的耦合只有两处：破环/圣环熔炼出的<b>熔融破碎之环</b>流体（材料「神灵金」原料），
 * 以及 {@code GoetyBridge} 里对 Apollyon（亚波伦）的反射兼容。后者是运行时读取、无注册项；
 * 前者必须在启示录在场时才注册。
 */
public final class GoetyRevelationIntegration implements Integration {

    private static final Logger LOGGER = LoggerFactory.getLogger("TinkersNewlife");

    public static final String MOD_ID = "goety_revelation";

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public void register(IEventBus bus) {
        GoetyRevelationFluids.register(bus);
        LOGGER.info("[联动] 诡厄巫法·启示录在场：熔融破碎之环已注册（材料「神灵金」原料）");
    }
}
