package com.mofengbaizhi.tinkersnewlife.content.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 星象仪（{@code tinkersnewlife:planetarium}）—— <b>工具类饰品</b>，装在通用「饰品」槽
 * （本整合包 = curios {@code charm} 槽，神秘遗物把它的中文名覆盖成了"饰品"）。
 *
 * <h2>功能（用户口径）</h2>
 * <ul>
 *   <li><b>物品材质随当日月相变化</b> ✓ —— 8 个月相 → 8 张图标 ✓
 *       走 <b>物品属性 + 模型 overrides</b> 这条原版路子 ✓（与「心」同款机制 ✓）：
 *       谓词 {@code tinkersnewlife:planetarium} 给出 {@code (月相+1)/10} ✓
 *       （月相 0..7 ⇒ 0.1..0.8 ✓ 落在 8 个 override 上 ✓）⇒ 物品栏 / 手上 / 饰品槽 / 地上<b>全都跟着变</b> ✓
 *       （★ 这一点是"物品模型"的特性 ✓ 不需要任何 mixin 或每 tick 同步 ✗）。</li>
 *   <li><b>装配时在咒术 HUD 上额外显示一个可独立拖动的小块</b> ✓（见
 *       {@code client/hud/PlanetariumHud} ✓ 位置存在 {@code CurseHudConfig} 里 ✓）。</li>
 *   <li><b>没有数值效果</b> ✓（用户口径：纯装饰 + 信息 ✓）。</li>
 * </ul>
 *
 * <h2>为什么不需要服务端逻辑 / 网络包</h2>
 * 月相是<b>世界时间算出来的</b>（{@code level.getMoonPhase()} ✓ = {@code (day % 8)} 的映射 ✓），
 * 客户端本地就有 ✓ ⇒ 图标与 HUD 都能在客户端直接算 ✓ <b>零包、零 NBT</b> ✓。
 */
public class PlanetariumItem extends Item implements ICurioItem {

    /** 月相种类数（我的世界固定 8 相 ✓） */
    public static final int PHASE_COUNT = 8;

    public PlanetariumItem(Properties properties) {
        super(properties);
    }

    // ============================================================
    //  Curios：通用「饰品」槽（照封呪瓶 ✓ 本模组不注册公共槽位 ✗）
    // ============================================================

    /** 可佩戴槽位：{@code charm} = 本整合包的通用「饰品」槽 ✓；{@code curio} = Curios 自带通用槽 ✓（兼容别的整合包 ✓） */
    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        String id = context.identifier();
        return "charm".equals(id) || "curio".equals(id);
    }

    /** 允许"手持右键直接装备到饰品槽" */
    @Override
    public boolean canEquipFromUse(SlotContext context, ItemStack stack) {
        return canEquip(context, stack);
    }

    // ============================================================
    //  月相名称（图标与 HUD 共用同一套键 ✓ 客户端与服务端都能取 ✓）
    // ============================================================

    /** 月相 → 名字翻译键（{@code phase} 会被规整到 0..7 ✓ 越界不崩 ✓） */
    public static String phaseKey(int phase) {
        int p = Math.floorMod(phase, PHASE_COUNT);
        return "moon.tinkersnewlife.phase." + p;
    }

    /** 月相 → 名字组件 */
    public static Component phaseName(int phase) {
        return Component.translatable(phaseKey(phase));
    }

    // ============================================================
    //  提示（说明它干什么 + 当前月相）
    // ============================================================

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tinkersnewlife.planetarium.hint")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.tinkersnewlife.planetarium.today",
                        phaseName(level == null ? 0 : level.getMoonPhase()))
                .withStyle(net.minecraft.ChatFormatting.AQUA));
    }
}
