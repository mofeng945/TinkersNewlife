package com.mofengbaizhi.tinkersnewlife.integration.goety_ladder;

import com.mofengbaizhi.tinkersnewlife.integration.Integration;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 「诡厄巫法：阶梯」({@code goety_ladder}) 联动模块（§725）。
 *
 * <h2>隔离约定（与本模组其它联动一致 ✓）</h2>
 * 本类<b>零联动类型 import</b>（只做分派 ✓）；真正需要阶梯类型的东西目前没有 —— 虚空金属的
 * 物品/效果/伤害判定全部走<b>按名反射</b>（{@code integration.goety_ladder.GoetyLadderCompat} ✓），
 * 这里只负责注册<b>熔融虚空金属</b>流体组 ✓。
 *
 * <h2>门控</h2>
 * 由 {@code IntegrationLoader} 在 {@code shouldRegisterLinked("goety_ladder")}（＝模组在场 ✓）分支里调用 ✓，
 * 未安装时本类<b>不会被加载</b> ✓ ⇒ 不会出现"流体注册了但桶没注册"这类半残状态 ✓。
 */
public final class GoetyLadderIntegration implements Integration {

    private static final Logger LOGGER = LoggerFactory.getLogger("TinkersNewlife");

    /** 阶梯的 modId */
    public static final String MOD_ID = "goety_ladder";

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public void register(IEventBus bus) {
        // 熔融虚空金属流体组（FluidType / 静止 / 流动 / 方块 / 桶 同生共死 ✓）
        GoetyLadderFluids.register(bus);
        LOGGER.info("[联动] 诡厄巫法·阶梯在场：注册「熔融虚空金属」流体组 ✓"
                + "（材料「虚空金属」改为熔融 → 浇筑获取 ✓；虚空抚摸/末影之力/虚无恩宠/守望意志四条特性随材料生效 ✓）");
    }
}
