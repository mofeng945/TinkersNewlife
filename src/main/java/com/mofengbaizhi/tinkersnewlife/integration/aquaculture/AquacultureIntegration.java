package com.mofengbaizhi.tinkersnewlife.integration.aquaculture;

import com.mofengbaizhi.tinkersnewlife.integration.Integration;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 水产养殖2（aquaculture）联动模块。
 *
 * <p>水产没有编译依赖（不在编译期类路径上），材料/特性/配方全部走数据包
 * （{@code forge:mod_loaded aquaculture}）+ 反射/事件，因此本类自身零水产 import；
 * 需要"注册"的联动内容只有流体组（熔融海王金属），入口 {@link AquacultureFluids#register(IEventBus)}。
 */
public final class AquacultureIntegration implements Integration {

    private static final Logger LOGGER = LoggerFactory.getLogger("TinkersNewlife");

    public static final String MOD_ID = "aquaculture";

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public void register(IEventBus bus) {
        // 流体整组（FluidType/静止/流动/方块/桶）同生共死
        AquacultureFluids.register(bus);
        LOGGER.info("[联动] 水产养殖2在场：熔融海王金属流体已注册（海王金属材料/海王之力特性/熔炼配方走 forge:mod_loaded aquaculture）");
    }
}
