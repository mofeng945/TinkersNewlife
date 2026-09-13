package com.mofengbaizhi.tinkersnewlife.integration.iceandfire;

import com.mofengbaizhi.tinkersnewlife.integration.Integration;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 冰火传说（iceandfire）联动模块。
 *
 * <p>冰火没有编译依赖（不在编译期类路径上），所有 API 交互都走运行时反射，
 * 因此本类自身零冰火 import；需要"注册"的联动内容只有流体组
 * （熔融龙钢 / 龙血 / 悚怖系），入口 {@link IceAndFireFluids#register(IEventBus)}。
 */
public final class IceAndFireIntegration implements Integration {

    private static final Logger LOGGER = LoggerFactory.getLogger("TinkersNewlife");

    public static final String MOD_ID = "iceandfire";

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public void register(IEventBus bus) {
        // 流体整组（FluidType/静止/流动/方块/桶）同生共死
        IceAndFireFluids.register(bus);
        LOGGER.info("[联动] 冰火传说在场：熔融龙钢×3、龙血×3、悚怖系流体×2 已注册（共 8 组）");
    }
}
