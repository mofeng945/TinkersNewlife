package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;

/**
 * <b>匠魂拔刀剑「katana」</b>（§999 阶段 1：骨架 ✓）。
 *
 * <p>部件（用户口径 ✓）：<b>刀鞘 ＋ 刀身 ＋ 坚韧手柄</b> —— 见
 * {@code data/tinkersnewlife/tinkering/tool_definitions/katana.json} ✓
 * （前两个是 §998 新加的自研部件 ✓）。
 *
 * <h2>进度</h2>
 * <ul>
 *   <li><b>阶段 1（本类现状）</b>：可改造工具本体 ✓ 定义/模型/注册/标签/创造栏/手册 ✓；</li>
 *   <li><b>阶段 2（P3 §996）</b>：拔刀/收刀（右键长按 ✓）、左键连段、收刀态居合、SA ＋ 第一/三人称动画；</li>
 *   <li><b>阶段 3（P4 §996）</b>：与拔刀剑（SlashBlade）深度挂接 —— 挂
 *       {@code CapabilitySlashBlade.BLADESTATE} ✓、客户端委托它的刀渲染 ✓、借它的连段与 SA ✓
 *       ⚠ 前提是 §997 探针证明"它认外来物品" ✓。</li>
 * </ul>
 */
public class KatanaItem extends ModifiableItem {

    /** 工具定义 id ＝ {@code tinkersnewlife:katana}（与 {@code tool_definitions/katana.json} 同名 ✓） */
    public static final ToolDefinition KATANA_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "katana"));

    public KatanaItem(Properties properties) {
        super(properties, KATANA_DEFINITION);
    }
}
