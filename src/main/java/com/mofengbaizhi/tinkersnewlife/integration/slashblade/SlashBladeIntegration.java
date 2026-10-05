package com.mofengbaizhi.tinkersnewlife.integration.slashblade;

import com.mofengbaizhi.tinkersnewlife.integration.Integration;
import net.minecraftforge.eventbus.api.IEventBus;
import slimeknights.tconstruct.library.tools.capability.ToolCapabilityProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 拔刀剑（SlashBlade：重锋）联动模块（§997）。
 *
 * <p>⚠ 现状：**只有探针** ✗ —— 还没开始做「匠魂拔刀剑」本体 ✓。
 *
 * <p>为什么先做探针：用户选定**深度挂接（B）** ✓，即让匠魂工具挂上拔刀剑的
 * {@code CapabilitySlashBlade.BLADESTATE}、借它的连段与 SA ✓。
 * 但它的内部逻辑**可能按 {@code instanceof ItemSlashBlade} 硬判** ✗
 * ⇒ 若不先验证，后面 P1~P4 全做完才发现"它根本不认外来物品"就白干了 ✗。
 *
 * <p>探针做到什么程度：给**现有的一件匠魂工具**（西洋剑 ✓ 临时代用 ✓）挂上它的刀状态 Provider ✓，
 * 并在别人来查能力时**打日志 + 打一次调用栈** ✓
 * ⇒ 只要游戏里出现「有人来查刀状态了」这行日志 ✓ 就证明它**确实会查外来物品** ✓ B 可行 ✓。
 * 探针整体可在验证后一键撤掉 ✓（本包 + {@code RapierItem} 里那两行 ✓）。
 */
public final class SlashBladeIntegration implements Integration {

    private static final Logger LOGGER = LoggerFactory.getLogger("TinkersNewlife");

    public static final String MOD_ID = "slashblade";

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public void register(IEventBus bus) {
        // §1007：把"刀状态"注册成匠魂工具的一项能力
        //   —— 我们的 KatanaItem 移植自 ModifiableItem，其 initCapabilities 返回匠魂的 ToolCapabilityProvider
        //      ⇒ ItemSlashBlade 自带的刀状态被顶掉了，所以要从匠魂这边把状态再补回去（照 TiCEX 的做法）
        //   —— 效果：拔刀剑的"耐久/损坏"= 匠魂工具的面板耐久（ToolBladeStateCapability 只覆盖那三处）
        ToolCapabilityProvider.register((stack, tool) -> new SBItemCapabilityProvider(stack, tool));
        LOGGER.info("[联动] 拔刀剑：刀状态已接到匠魂工具上（拔刀剑的耐久 = 匠魂工具面板耐久）");
    }
}
