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

    /**
     * 液态奥术（<b>独立流体</b>，不是"熔融奥术锭"）。
     *
     * <p>用途：材料「无相冰」的配方里，熔炼冰封手柄的**副产物**（150mb 灼热之冰 + 50mb 液态奥术）。
     * 铁魔法本身只有奥术源质/锭/符文这些**物品**，没有对应流体，所以这一支由本模组提供。
     */
    public static final FluidRegistrar.FluidEntry LIQUID_ARCANE = REG.entry("liquid_arcane",
            1500, 3000, 900, 0xFF8A6BFF,  // 奥术紫
            FluidRegistrar.waterProps(MapColor.COLOR_PURPLE));

    /** 流体灰烬（熔炼铁魔法「灰烬源质」cinder_essence 得到；材料「炽金」合金的原料之一） */
    public static final FluidRegistrar.FluidEntry CINDER_ASH = REG.entry("cinder_ash",
            1200, 2500, 900, 0xFF7A6E63,  // 暖灰
            FluidRegistrar.waterProps(MapColor.COLOR_GRAY));

    /** 熔融炽金（材料「炽金」的原料流体；熔炼炽金锭 或 流体灰烬+熔融金 合金） */
    public static final FluidRegistrar.FluidEntry MOLTEN_PYRIUM = REG.entry("molten_pyrium",
            2000, 8000, 1100, 0xFFFFAE3C,  // 炽金橙
            FluidRegistrar.lavaProps(MapColor.COLOR_ORANGE));

    /** 熔融秘银（材料「秘银」的原料流体；熔炼粗秘银 30mB / 碎片 20mB / 锭 90mB） */
    public static final FluidRegistrar.FluidEntry MOLTEN_MITHRIL = REG.entry("molten_mithril",
            2000, 7000, 1100, 0xFFC9D6E8,  // 秘银银蓝
            FluidRegistrar.lavaProps(MapColor.COLOR_LIGHT_BLUE));

    /**
     * 魔金精华（材料「<b>魔金</b>」的原料流体）。
     *
     * <p>获取方式只有一条合金：<b>90 mB 熔融炽金 + 90 mB 熔融秘银 + 90 mB 熔融奥术锭 → 180 mB 魔金精华</b>
     * （也就是"三种联动材料的熔体合一"）✓，1 单位材料 = 90 mB ✓。
     */
    public static final FluidRegistrar.FluidEntry MAGIC_GOLD_ESSENCE = REG.entry("magic_gold_essence",
            2500, 9000, 1200, 0xFFDFA845,  // 紫影魔金
            FluidRegistrar.lavaProps(MapColor.COLOR_ORANGE));

    /** 把本组四张注册表挂到模组总线（仅铁魔法在场时被调用） */
    public static void register(IEventBus bus) {
        REG.register(bus);
    }
}
