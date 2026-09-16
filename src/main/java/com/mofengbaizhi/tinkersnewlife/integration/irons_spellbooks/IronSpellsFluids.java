package com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.fluid.FluidRegistrar;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 铁魔法（irons_spellbooks）联动流体组：<b>只有铁魔法在场时才注册</b>（整组同生共死）。
 *
 * <p>材料「<b>圣灵</b>」（{@code tinkersnewlife:holy_spirit}）的三支流体：
 * <ol>
 *   <li>{@link #PRIMORDIAL_FIRE_SOUL 原初受火遗魂} —— 熔炼铁魔法「神圣灵魂碎片」
 *       （{@code irons_spellbooks:divine_soulshard}），1 碎片 = 250 mb；</li>
 *   <li>{@link #MOLTEN_ARCANE_INGOT 熔融奥术锭} —— 熔炼铁魔法「奥术锭」
 *       （{@code irons_spellbooks:arcane_ingot}），1 锭 = 90 mb；</li>
 *   <li>{@link #HOLY_SPIRIT 神圣灵液} —— 上面两支按 <b>100 + 100 → 360 mb</b> 合金而成，
 *       即材料「圣灵」的原料（1 单位 = 90 mb）。</li>
 * </ol>
 *
 * <p>公共代码一律<b>按注册名</b>取用（{@code tinkersnewlife:holy_spirit_still}），不引用本类字段，
 * 避免未安装铁魔法时把联动类型连带解析出来。
 */
public final class IronSpellsFluids {

    private static final FluidRegistrar REG = new FluidRegistrar(TinkersNewlife.MOD_ID);

    private IronSpellsFluids() {
    }

    /** 原初受火遗魂（熔炼神圣灵魂碎片；250 mb/碎片） */
    public static final FluidRegistrar.FluidEntry PRIMORDIAL_FIRE_SOUL = REG.entry("primordial_fire_soul",
            1500, 3000, 1400, 0xFFFF6A1E,  // 余烬橙红
            FluidRegistrar.lavaProps(MapColor.COLOR_ORANGE));

    /** 熔融奥术锭（熔炼铁魔法奥术锭；90 mb/锭） */
    public static final FluidRegistrar.FluidEntry MOLTEN_ARCANE_INGOT = REG.entry("molten_arcane_ingot",
            2000, 7000, 1000, 0xFF35A7B8,  // 奥术青（贴近铁魔法奥术锭）
            FluidRegistrar.lavaProps(MapColor.COLOR_LIGHT_BLUE));

    /** 神圣灵液（原初受火遗魂 + 熔融奥术锭 合金；材料「圣灵」的原料流体，1 单位 = 90 mb） */
    public static final FluidRegistrar.FluidEntry HOLY_SPIRIT = REG.entry("holy_spirit",
            1500, 2500, 1250, 0xFFFFF0BE,  // 圣金象牙白
            FluidRegistrar.lavaProps(MapColor.COLOR_YELLOW));

    /** 灼热之冰（材料「无相冰」的原料流体；熔炼永冻碎片 500mB/碎片、冰封手柄 150mB/个） */
    public static final FluidRegistrar.FluidEntry SCORCHING_ICE = REG.entry("scorching_ice",
            1200, 2000, 400, 0xFF7FD8F0,  // 苍冰蓝（冰里透热）
            FluidRegistrar.waterProps(MapColor.ICE));

    /** 把本组四张注册表挂到模组总线（仅铁魔法在场时被调用） */
    public static void register(IEventBus bus) {
        REG.register(bus);
    }
}
