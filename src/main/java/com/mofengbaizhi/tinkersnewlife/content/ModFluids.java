package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.fluid.FluidRegistrar;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.DeferredRegister;

/**
 * 本模组<b>原生</b>流体（原料来自原版 / 本模组内容 / 本模组事件，与任何联动模组无关）。
 *
 * <p>⭐ 联动材料的流体（熔融龙钢、龙血、熔融诅咒金属、不洁之血、熔融破碎之环…）**不在这里**：
 * 它们按"联动注册标准写法"放在 {@code integration/<modid>/} 里，与对应模组同生共死
 * （模组不在场 → 流体/FluidType/液体方块/桶整组不注册）。见
 * {@code integration.iceandfire.IceAndFireFluids} / {@code integration.goety.GoetyFluids} /
 * {@code integration.goety_revelation.GoetyRevelationFluids} / {@code integration.aquaculture.AquacultureFluids}。
 */
public class ModFluids {

    // ============================================================
    // 注册表（原生流体共用一套；联动流体各有自己的一套，见 FluidRegistrar）
    // ============================================================
    public static final FluidRegistrar REGISTRAR = new FluidRegistrar(TinkersNewlife.MOD_ID);

    public static final DeferredRegister<Fluid> FLUIDS = REGISTRAR.fluids;
    public static final DeferredRegister<FluidType> FLUID_TYPES = REGISTRAR.types;
    public static final DeferredRegister<Block> FLUID_BLOCKS = REGISTRAR.blocks;
    public static final DeferredRegister<Item> FLUID_BUCKETS = REGISTRAR.buckets;

    /** 声明原生流体（转发到公共注册器） */
    private static FluidRegistrar.FluidEntry entry(String name, int density, int viscosity, int temperature,
                                                   int tintColor,
                                                   net.minecraft.world.level.block.state.BlockBehaviour.Properties props) {
        return REGISTRAR.entry(name, density, viscosity, temperature, tintColor, props);
    }

    // ============================================================
    //  ⭐ §1118t 条件流体（只在指定模组组合同时在场时才挂总线 ✓）
    // ============================================================

    /**
     * ⭐ §1118t <b>邪灵辉质</b>（用户口径 ✓）：「给**邪灵辉锭**写流体，名字叫**邪灵辉质**，
     * 材质从锭上取多个色，然后选一个匠魂流体材质上色」✓
     *
     * <h2>为什么另开一张注册器 ✗</h2>
     * 它的用途就是"熔融邪灵辉锭"✓，而那个**物品本身是条件注册**的 ✓（§1118r：
     * 只在 {@code goety ＋ enigmaticlegacy} 同时在场时存在 ✓）⇒ 流体必须**跟着同一个条件** ✓
     * 否则会出现"流体在、能熔的锭不在"的半残状态 ✗。
     * ⇒ 做法与 §1118r 完全一致 ✓：另开 {@link #LINKED_REGISTRAR} ✓，
     * 由 {@code TinkersNewlife} 在**判定通过后**才 {@code .register(modEventBus)} ✓
     * ⇒ 不通过时这套表**从未挂上总线** ✓（对应的 `_bucket` / 流体方块都不存在 ✓）。
     *
     * <h2>贴图（我按用户口径生成的 ✓ 见 {@code tools/fluid_texture_tint.py} ✓）</h2>
     * ① 先对 {@code textures/item/sinister_glow_ingot.png}（用户的 5 帧锭 ✓）做**调色板量化取色** ✓
     * （实测主色：深靛 {@code #2D2467} ✓ 紫 {@code #7157FA} ✓ 青 {@code #7EFFFF} ✓
     * 粉紫 {@code #EBA1FF} ✓ 蓝 {@code #3FAAD8} ✓ 等 ✓）；
     * ② 再拿**匠魂风格的熔融流体底图**（{@code molten_dragonsteel_still/flowing} ✓ 16×160 / 32×320 各 10 帧 ✓）
     * **去色成亮度**后按"深靛 → 紫 → 青"重新上色 ✓（紫为主体 ✓ 青只留最亮尖端 ✓ 与锭一致 ✓）；
     * ③ ⚠ **绝不覆盖任何既有贴图** ✗ —— 只**新增**两张 ✓；脚本也会先检查目标不存在 ✓。
     */
    public static final FluidRegistrar LINKED_REGISTRAR = new FluidRegistrar(TinkersNewlife.MOD_ID);

    /**
     * 熔融的**邪灵辉锭** ✓ ⇒ 流体名「**邪灵辉质**」✓（id ＝ {@code tinkersnewlife:molten_sinister_glow} ✓）。
     * <p>数值照本仓熔融金属的口径 ✓（密度 2000 ✓ 黏度 10000 ✓）；
     * ⚠ <b>温度按用户口径 ＝ 2000</b> ✓（用户原话：「补上融化和浇筑配方，**温度2000**」✓）——
     * 且**熔化配方的 `temperature` 也一并写成 2000** ✓（两边一致 ✓ 免得出现
     * "流体自身 1300 而配方却要 2000"这种自相矛盾 ✗ 那是我第一版留下的 ✗）；
     * 颜色取锭的紫色主体 {@code FF7157FA} ✓。
     */
    public static final FluidRegistrar.FluidEntry MOLTEN_SINISTER_GLOW =
            LINKED_REGISTRAR.entry("molten_sinister_glow",
                    2000, 10000, 2000, 0xFF7157FA,
                    FluidRegistrar.lavaProps(MapColor.COLOR_PURPLE));

    /**
     * ⭐ §1118w <b>熔融以太</b>（用户口径 ✓）：「合金配方里面少写了个 **90mb 熔融以太** ✓；
     * **1 以太锭 ＝ 90mb 熔融以太（1950 度）**」✓ ⇒ 温度 **1950** ✓（用户给的数 ✓）。
     *
     * <p>用途 ✓：① 邪灵辉质的合金需要它 90mb ✓（已补进合金的 inputs ✓）；
     * ② 以太装备（护甲 4 件 ＋ 工具 5 件 ✓ 见 {@code smeltery/melting/etherium_*} ✓）都能熔成它 ✓；
     * ③ 它能浇回**以太锭** ✓（`casting/etherium_ingot/**` ✓）。
     * <p>颜色 ✓：按用户口径「从来源物取色」✓ —— 取 `enigmaticlegacy:etherium_ingot` 的
     * **#ABFFFF**（淡青白 ✓ 实测主色 ✓），底图仍是**匠魂默认熔融材质** ✓。
     */
    public static final FluidRegistrar.FluidEntry MOLTEN_ETHERIUM =
            LINKED_REGISTRAR.entry("molten_etherium",
                    2000, 10000, 1950, 0xFFABFFFF,
                    FluidRegistrar.lavaProps(MapColor.COLOR_LIGHT_BLUE));

    // ============================================================
    //  §1118u 合金链的 4 种"精质/星质"流体（用户口径 ✓ 全部同条件 ✓）
    // ============================================================

    /**
     * 四个合金原料流体 ✓ —— 用户口径：
     * 「1 灵质 ＝ 125mb **液态灵质** ✓；1 邪恶精髓 ＝ 125mb **流体邪恶** ✓；
     *   1 虚空回响 ＝ 250mb **液态虚空**（诡厄本体流体 ✓ 副产物 10mb **守望灵质** ✓）；
     *   1 星尘 ＝ 125mb **流动星空** ✓；
     *   250 液态灵质 ＋ 250 流体邪恶 ＋ 1000 液态虚空 ＋ 10 守望灵质 ＋ 250 流动星空 ＝ **360mb 邪灵辉质**」✓
     *
     * <p>⚠ 温度口径 ✓：四种原料流体的温度**按用户逐个给定** ✓ ——
     * 液态灵质 **300** ✓、流体邪恶 **1200** ✓、守望灵质 **1800** ✓、流动星空 **2000** ✓
     * （⚠ 对应的熔炼配方 `temperature` 与流体自身**取同一个值** ✓，
     * 免得出现"配方要一个温度、流体自己是另一个"的自相矛盾 ✗）；
     * 合金产物 {@link #MOLTEN_SINISTER_GLOW} 与合金配方仍是 **2000** ✓（用户给的数 ✓）。
     * <p>⚠ 颜色 ✓：四个都按用户口径「**从来源物上取色**」✓ ——
     * 液态灵质取 `goety:ectoplasm` 的 **#399AD6** ✓、流体邪恶取 `enigmaticlegacy:evil_essence` 的 **#220069** ✓、
     * 守望灵质取 `goety:void_echo` 的 **#1C0030** ✓（它就是虚空回响的副产物 ✓）、
     * 流动星空取 `enigmaticlegacy:astral_dust` 的 **#9F37CA** ✓；
     * 贴图一律是"**换色匠魂默认熔融材质**" ✓（生成器 `tools/fluid_texture_tint.py` 同族脚本 ✓）。
     */
    public static final FluidRegistrar.FluidEntry LIQUID_ECTOPLASM =
            LINKED_REGISTRAR.entry("liquid_ectoplasm",
                    1500, 6000, 300, 0xFF399AD6,
                    FluidRegistrar.waterProps(MapColor.COLOR_LIGHT_BLUE));

    public static final FluidRegistrar.FluidEntry LIQUID_EVIL =
            LINKED_REGISTRAR.entry("liquid_evil",
                    1600, 7000, 1200, 0xFF220069,
                    FluidRegistrar.waterProps(MapColor.COLOR_PURPLE));

    public static final FluidRegistrar.FluidEntry WATCHER_ECTOPLASM =
            LINKED_REGISTRAR.entry("watcher_ectoplasm",
                    1500, 6000, 1800, 0xFF1C0030,
                    FluidRegistrar.waterProps(MapColor.COLOR_BLACK));

    public static final FluidRegistrar.FluidEntry FLOWING_STARDUST =
            LINKED_REGISTRAR.entry("flowing_stardust",
                    1200, 4000, 2000, 0xFF9F37CA,
                    FluidRegistrar.waterProps(MapColor.COLOR_MAGENTA));

    // ============================================================
    // 原生流体
    // ============================================================

    /** 格赫罗斯之血（本模组下界矿/内容） */
    public static final FluidRegistrar.FluidEntry GHELOTH_BLOOD = entry("gheloth_blood",
            1500, 2000, 300, 0xFFFF4500,
            FluidRegistrar.waterProps(MapColor.CRIMSON_STEM));

    /** 熔融尼古拉斯的祝福（本模组喂食繁殖掉落） */
    public static final FluidRegistrar.FluidEntry MOLTEN_NICHOLAS_BLESSING = entry("molten_nicholas_blessing",
            2000, 6000, 800, 0xFF9B59B6,
            FluidRegistrar.lavaProps(MapColor.COLOR_PURPLE));

    /** 哈斯塔之恶（本模组 Y>400 坠落事件） */
    public static final FluidRegistrar.FluidEntry HASTUR_MALICE = entry("hastur_malice",
            1500, 2000, 300, 0xFF4B0082,
            FluidRegistrar.waterProps(MapColor.COLOR_PURPLE));

    /** 灰烬之墨（本模组钓鱼产出） */
    public static final FluidRegistrar.FluidEntry ASHEN_INK = entry("ashen_ink",
            1500, 2000, 300, 0xFFC0C0C0,
            FluidRegistrar.waterProps(MapColor.COLOR_LIGHT_GRAY));

    /**
     * 驳杂墨水（粗墨，铁魔法「普通墨水」的**粗料**）。
     *
     * <p>获取：冶炼炉里<b>熔炼鱿鱼</b>（实体熔炼 250 mB / 只）或<b>熔炼墨囊</b>（100 mB / 个）。
     * 去向：与<b>熔融铜 + 液态奥术</b>合金 → {@code irons_spellbooks:common_ink}（普通墨水，250 mB）。
     *
     * <p>贴图：按项目口径取自匠魂本体 {@code molten/transparent} 改色（墨蓝紫）。
     */
    public static final FluidRegistrar.FluidEntry IMPURE_INK = entry("impure_ink",
            1500, 2000, 300, 0xFF26264E,
            FluidRegistrar.waterProps(MapColor.COLOR_BLACK));

    /** 熔融杜兰达尔（本模组凋灵掉落线） */
    public static final FluidRegistrar.FluidEntry MOLTEN_DURANDAL = entry("molten_durandal",
            2000, 10000, 1500, 0xFFFFD700,  // 金黄色

            FluidRegistrar.lavaProps(MapColor.COLOR_ORANGE));

    /**
     * 咒力残秽（古代咒术残卷熔炼所得，1 份残卷 = 1 mb）：
     * 装进「封呪瓶」即可转化为咒力（1 mb = 10 咒力，满瓶 500 mb = 5000 咒力）。
     */
    public static final FluidRegistrar.FluidEntry CURSE_RESIDUE = entry("curse_residue",
            1200, 1500, 1200, 0xFF3B0A5A,  // 深紫（咒力色）
            FluidRegistrar.waterProps(MapColor.COLOR_PURPLE));
}
