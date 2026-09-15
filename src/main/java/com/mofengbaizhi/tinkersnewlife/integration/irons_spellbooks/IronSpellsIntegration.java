package com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks;

import com.mofengbaizhi.tinkersnewlife.integration.Integration;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 铁魔法（irons_spellbooks）联动模块。
 *
 * <p>本类<b>不 import 任何铁魔法类型</b>（只做分派与注册），因为铁魔法在本模组里是
 * **纯反射软依赖**（{@code build.gradle} 不引用它，见 {@code util.IronSpellsReflector}）：
 * 需要读写它 API 的地方走反射，需要注册注册表对象的地方放这里。
 *
 * <h2>当前内容</h2>
 * <ul>
 *   <li>{@link IronSpellsFluids}：材料「圣灵」的三支流体
 *       （原初受火遗魂 / 熔融奥术锭 / 神圣灵液），整组同生共死；</li>
 *   <li>铁魔法本身的法术/属性读写仍在 {@code util.IronSpellsReflector}，保持"只有一个入口"。</li>
 * </ul>
 */
public final class IronSpellsIntegration implements Integration {

    private static final Logger LOGGER = LoggerFactory.getLogger("TinkersNewlife");

    /** ⚠ 铁魔法的 modid 带下划线 */
    public static final String MOD_ID = "irons_spellbooks";

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public void register(IEventBus bus) {
        // 联动流体整组（FluidType / 静止 / 流动 / 方块 / 桶）同生共死：
        // 原初受火遗魂、熔融奥术锭、神圣灵液
        IronSpellsFluids.register(bus);
        // 特性「魔导」的事件监听（铁魔法事件类在运行时才确定 → 原始 addListener）
        IronSpellsArcaneHandler.attach(bus);
        LOGGER.info("[联动] 铁魔法在场：圣灵材料流体组已注册（原初受火遗魂 / 熔融奥术锭 / 神圣灵液）");
    }
}
