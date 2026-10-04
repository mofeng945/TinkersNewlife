package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;

/**
 * <b>西洋剑（Rapier）</b>—— 移植自**匠魂2**的同名武器 ✓（§943 起）。
 *
 * <h2>匠魂2 原版的五条特征（[MC百科](https://www.mcmod.cn/item/51928.html) 查证 ✓）</h2>
 * <ol>
 *   <li><b>攻击伤害较低</b> ✓ 但 <b>攻速更快</b> ✓ —— 已写进
 *       {@code data/tinkersnewlife/tinkering/tool_definitions/rapier.json} ✓
 *       （{@code attack_damage 3.0 × 0.8} ✓ {@code attack_speed 2.0} ✓＝匕首档 ✓ 见 §945 ✓）；</li>
 *   <li><b>无视目标护甲</b>造成伤害 ✓ —— **阶段 2** 用"**方案 B**"实现 ✓
 *       （1.20.1 的 {@code bypasses_armor} 是**伤害类型标签**驱动的 ✗ 用它会波及所有同类型攻击 ✗
 *       ⇒ 改为在 {@code LivingHurtEvent}（护甲前）与 {@code LivingDamageEvent}（护甲后）之间
 *       **把被护甲减掉的差额补回来** ✓ 只影响本武器 ✓）；</li>
 *   <li>合成 **剑刃 ＋ 手柄 ＋ 十字柄** ✓ —— 匠魂3 没有独立十字柄 ✗ ⇒ 用 {@code tough_handle} 顶位 ✓
 *       （部件真名实查 TC jar ✓ 见 §944 ✓）；</li>
 *   <li><b>右键后跳</b>闪避 ✓ —— **阶段 3**；</li>
 *   <li><b>手持时副手盾牌等无法使用</b> ✓ —— **阶段 4**。</li>
 * </ol>
 *
 * <p>⚠ 当前进度：**阶段 1**（本类 ＋ 注册 ＋ 定义 ＋ 模型 ＋ 占位贴图 ✓）；
 * 阶段 2–5 见 {@code docs/开发备忘录.md} §943 计划 ✓。
 *
 * <p>⚠ 与长矛不同，西洋剑**没有**蓄力/冲锋机制 ✓（匠魂2 那版是纯刺击 ＋ 右键后跳 ✓），
 * 所以本类暂时只是"带定义的可改造工具" ✓ —— 后跳等行为在阶段 3 加 ✓。
 */
public class RapierItem extends ModifiableItem {

    /** 工具定义 id ＝ {@code tinkersnewlife:rapier} ✓（与 {@code tool_definitions/rapier.json} 同名 ✓） */
    public static final ToolDefinition RAPIER_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "rapier"));

    public RapierItem(Properties properties) {
        super(properties, RAPIER_DEFINITION);
    }
}
