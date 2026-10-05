package com.mofengbaizhi.tinkersnewlife.integration.slashblade;

import com.mofengbaizhi.tinkersnewlife.integration.Integration;
import net.minecraftforge.eventbus.api.IEventBus;
import slimeknights.tconstruct.library.tools.capability.ToolCapabilityProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 拔刀剑（SlashBlade：重锋）联动模块（§997 起）。
 *
 * <p>现状（§1020–§1022）：**已按 TiCEX 走通的路子完整挂接** ✓ ——
 * <ul>
 *   <li>刀状态作为匠魂工具的一项能力 ✓（{@link SBItemCapabilityProvider} ＋ {@link ToolBladeStateCapability} ✓）；</li>
 *   <li>三个服务端 mixin 把匠魂数值注入拔刀剑自己的攻击管线 ✓
 *       （{@code KatanaAttackHelperMixin}／{@code KatanaAttackManagerMixin}／{@code KatanaItemSlashBladeMixin} ✓）；</li>
 *   <li>刀状态的事件同步 ✓（{@link KatanaBladeSyncEvents} ＋ {@code PacketSlashBladeStateSync} ✓），
 *       照 TiCEX 的 {@code TicEXSBEvent}／{@code StateSyncPacket} ✓。</li>
 * </ul>
 * （早期那支"探针"已在 §1007 撤掉 ✓ —— 当时的结论是它**确实会认外来物品** ✓，B 路线可行 ✓。）
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
        // §1022：照 TiCEX，把刀状态在关键时机主动同步给客户端（否则连段/蓄力/技能的表现对不上 ✓）
        // ✗ §1034 拔刀剑整体搁置（可逆隐藏）：这一行**已注释掉** ✗ ——
        //   它会往 7 个事件（刀动作/落地/可飞行落地/死亡/经验掉落/受伤/输入指令）挂监听器，
        //   在打怪、受伤、落地等时机做 NBT 同步与调试日志 ⇒ 属于"有运行时动作"的部分 ✗。
        //   恢复：把下面这行取消注释即可 ✓（类与调用点全部保留 ✗ 未删）。
        // KatanaBladeSyncEvents.register();
        LOGGER.info("[联动] 拔刀剑：刀状态已接到匠魂工具上（拔刀剑的耐久 = 匠魂工具面板耐久）");
    }
}
