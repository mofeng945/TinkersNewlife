package com.mofengbaizhi.tinkersnewlife.integration.lavafishing;

import com.mofengbaizhi.tinkersnewlife.integration.Integration;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 熔岩钓鱼（lavafishing）联动模块。
 *
 * <p>熔岩钓鱼没有编译依赖（不在编译期类路径上），材料/特性/配方全部走数据包
 * （{@code forge:mod_loaded lavafishing}）+ 按注册名取的原版 API（例如它的
 * {@code lavafishing:lava_walker} 药水效果，见 {@code HeatLoverHandler}），因此本类自身零熔岩钓鱼 import；
 * 需要"注册"的联动内容只有流体组（熔融钷），入口 {@link LavafishingFluids#register(IEventBus)}。
 */
public final class LavafishingIntegration implements Integration {

    private static final Logger LOGGER = LoggerFactory.getLogger("TinkersNewlife");

    public static final String MOD_ID = "lavafishing";

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public void register(IEventBus bus) {
        // 流体整组（FluidType/静止/流动/方块/桶）同生共死
        LavafishingFluids.register(bus);
        LOGGER.info("[联动] 熔岩钓鱼在场：熔融钷流体已注册（钷材料/喜热特性/熔炼配方走 forge:mod_loaded lavafishing）");
    }
}
