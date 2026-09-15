package com.mofengbaizhi.tinkersnewlife.integration;

import com.mofengbaizhi.tinkersnewlife.content.item.ModularStaffItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 联动注册总入口 —— <b>主类只调 {@link #init(IEventBus)}</b>，其余联动细节都在本包内消化。
 *
 * <p>判定一律走 {@link #isLoaded(String)}（{@code ModList.get().isLoaded}），禁止 {@code Class.forName} 试探。
 * 联动类只在对应模组在场时才被 JVM 加载（调用点都在 {@code if (isLoaded(...))} 分支内），
 * 因此未安装联动模组的环境里不会出现 {@code NoClassDefFoundError}。
 */
public final class IntegrationLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("TinkersNewlife");

    // ============================================================
    //  联动的模组 id（唯一事实来源，公共代码只引用这里的常量）
    // ============================================================
    /** 诡厄巫法 */
    public static final String GOETY = "goety";
    /** 诡厄巫法：启示录 */
    public static final String GOETY_REVELATION = "goety_revelation";
    /** 冰火传说 */
    public static final String ICEANDFIRE = "iceandfire";
    /** 铁魔法（Iron's Spells 'n Spellbooks）——⚠ modid 是 <b>带下划线</b>的 {@code irons_spellbooks}
     *  （Java 包名 {@code io.redspace.ironsspellbooks} 没有下划线，极易写错；写错的后果是联动判定恒为 false、
     *  模块化魔杖的法术功能被静默停用） */
    public static final String IRON_SPELLBOOKS = "irons_spellbooks";
    /** 永恒枪械工坊 */
    public static final String TACZ = "tacz";
    /** JEI */
    public static final String JEI = "jei";
    /** 帕秋莉手册 */
    public static final String PATCHOULI = "patchouli";
    /** 玉（Jade，方块信息显示） */
    public static final String JADE = "jade";
    /** 应用能源2（蓝本接口探测用） */
    public static final String AE2 = "ae2";
    /** Curios（硬依赖，但蓝本接口探测仍按名判定） */
    public static final String CURIOS = "curios";
    /** 匠魂 */
    public static final String TCONSTRUCT = "tconstruct";
    /** 月光库（部分整合包前置） */
    public static final String MOONLIGHT = "moonlight";

    /** 环境探测日志用的关注清单（顺序即日志顺序） */
    private static final String[] WATCHED = {
            GOETY, GOETY_REVELATION, ICEANDFIRE, IRON_SPELLBOOKS, TACZ, JEI, PATCHOULI, JADE, AE2
    };

    private IntegrationLoader() {
    }

    // ============================================================
    //  存在性判定（唯一入口）
    // ============================================================

    /** 模组是否存在（{@code ModList} 未就绪时视为不存在，绝不抛异常） */
    public static boolean isLoaded(String modId) {
        if (modId == null || modId.isEmpty()) return false;
        try {
            ModList list = ModList.get();
            return list != null && list.isLoaded(modId);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * <b>modid 自检</b>：关注清单里判定为"不在场"的 id，如果它去掉下划线 / 忽略大小写之后
     * 能匹配到某个<b>确实已加载</b>的模组，那基本就是我们自己把 modid 写错了。
     *
     * <p>为什么必须自检：这类错字**不会报错**，只会让整条联动静默失效。
     * 实测踩过两个：
     * <ul>
     *   <li>{@code ironsspellbooks} vs 真实 {@code irons_spellbooks}（少一个下划线）
     *       → 模块化魔杖"发不出铁魔法"；</li>
     *   <li>蓝图兼容清单里的 {@code @goetyrevelation} vs 真实命名空间 {@code goety_revelation}
     *       → 启示录的神灵金盔甲被做成蓝本代理物（穿不上、看不见模型）。</li>
     * </ul>
     * 判定本身仍然只用 {@link ModList}（这里的"模糊匹配"仅用于打印警告）。
     */
    private static void warnAboutMisspelledIds() {
        try {
            ModList list = ModList.get();
            if (list == null) return;
            for (String id : WATCHED) {
                if (isLoaded(id)) continue;
                String norm = id.replace("_", "").toLowerCase(java.util.Locale.ROOT);
                for (var info : list.getMods()) {
                    String other = info.getModId();
                    if (other == null || other.equals(id)) continue;
                    if (other.replace("_", "").toLowerCase(java.util.Locale.ROOT).equals(norm)) {
                        LOGGER.warn("[联动] ⚠ 关注的模组 id「{}」实际应为「{}」——IntegrationLoader 里的常量写错了，"
                                + "当前这条联动静默失效，请修正", id, other);
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 自检失败绝不影响启动
        }
    }

    public static boolean isGoety() {
        return isLoaded(GOETY);
    }

    /** goety 家族（本体或启示录）在场 */
    public static boolean isGoetyFamily() {
        return isGoety() || isLoaded(GOETY_REVELATION);
    }

    public static boolean isIceAndFire() {
        return isLoaded(ICEANDFIRE);
    }

    public static boolean isIronSpells() {
        return isLoaded(IRON_SPELLBOOKS);
    }

    public static boolean isTacz() {
        return isLoaded(TACZ);
    }

    public static boolean isPatchouli() {
        return isLoaded(PATCHOULI);
    }

    /**
     * 玉（Jade）是否在场。
     * <p>注意：玉的联动**不需要在这里分派** —— {@code integration/jade/CurseVaultJadePlugin} 由玉自己
     * 扫描 {@code @WailaPlugin} 注解后加载（被玉反向加载），本方法只用于日志/其它判断。
     */
    public static boolean isJade() {
        return isLoaded(JADE);
    }

    public static boolean isAe2() {
        return isLoaded(AE2);
    }

    // ============================================================
    //  初始化
    // ============================================================

    /**
     * 联动总初始化：主类唯一调用点。
     *
     * <p>各联动模块的 {@link Integration#register(IEventBus)} 只在对应模组在场时分派，
     * 保证联动类不会被提前加载。
     */
    public static void init(IEventBus bus) {
        Map<String, Boolean> detected = new LinkedHashMap<>();
        for (String id : WATCHED) detected.put(id, isLoaded(id));
        LOGGER.info("[联动] 环境探测：{}", detected);
        warnAboutMisspelledIds();
        LOGGER.info("[联动] 联动内容（流体整组）注册决策：goety = {}；iceandfire = {}；goety_revelation = {}",
                linkDecision(GOETY), linkDecision(ICEANDFIRE), linkDecision(GOETY_REVELATION));

        // ---- 逐模组分派：调用点位于 shouldRegisterLinked 分支内，未安装时联动类不会被加载 ----
        // ⭐ 联动内容（流体整组）是否注册 = 模组在场 **或** 匠魂 force_integration_materials 打开
        //    （后者会让联动材料定义照常加载，此时流体必须存在，否则材料引用悬空）。
        if (shouldRegisterLinked(GOETY)) {
            try {
                new com.mofengbaizhi.tinkersnewlife.integration.goety.GoetyIntegration().register(bus);
            } catch (Throwable t) {
                LOGGER.error("[联动] 诡厄巫法模块初始化失败（已降级为普通形态）", t);
            }
        }
        if (shouldRegisterLinked(ICEANDFIRE)) {
            try {
                new com.mofengbaizhi.tinkersnewlife.integration.iceandfire.IceAndFireIntegration().register(bus);
            } catch (Throwable t) {
                LOGGER.error("[联动] 冰火传说模块初始化失败", t);
            }
        }
        if (shouldRegisterLinked(GOETY_REVELATION)) {
            try {
                new com.mofengbaizhi.tinkersnewlife.integration.goety_revelation.GoetyRevelationIntegration().register(bus);
            } catch (Throwable t) {
                LOGGER.error("[联动] 诡厄巫法·启示录模块初始化失败", t);
            }
        }

        // 铁魔法：流体组走标准联动注册（模组在场才注册）；法术/属性读写仍走反射装填
        if (shouldRegisterLinked(IRON_SPELLBOOKS)) {
            try {
                new com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks.IronSpellsIntegration().register(bus);
            } catch (Throwable t) {
                LOGGER.error("[联动] 铁魔法模块初始化失败", t);
            }
        }
        // 铁魔法：纯反射软依赖（无编译依赖），此处只做一次反射装填
        com.mofengbaizhi.tinkersnewlife.util.IronSpellsReflector.init();
    }

    /**
     * 联动内容是否需要注册：**只看模组是否在场**。
     *
     * <p>为什么不看匠魂的 {@code force_integration_materials}：该配置在 mod 构造阶段**读不到**
     * （配置装载晚于构造），只能"保守返回 true"＝在模组缺失时也照样注册，等于没有门控；
     * 而它唯一的意义是"强制让联动材料定义加载"，那条路会引用到并不存在的流体。
     * 因此统一口径：**注册/材料定义/配方全部只认 {@code forge:mod_loaded}**，三方一致、无悬空引用。
     */
    public static boolean shouldRegisterLinked(String modId) {
        return isLoaded(modId);
    }

    /** 注册决策的可读描述（写进启动日志，便于排查"联动流体该不该注册"） */
    private static String linkDecision(String modId) {
        return isLoaded(modId) ? "注册（模组在场）" : "不注册（模组未安装）";
    }

    // ============================================================
    //  公共代码 ⇄ 诡厄魔杖真形态（唯一通道）
    //  ⭐ 公共代码绝不 import 诡厄类型，也不引用联动类静态字段
    // ============================================================

    /**
     * 构造模块化魔杖：诡厄在场 → 真法杖形态（实现 IWand，走原生施法管线）；
     * 否则普通形态。物品 id / 数据 / 模型完全一致，无硬依赖。
     *
     * <p>物品注册点必须留在公共侧（{@code modular_staff} 两种环境下都要存在），
     * 只有"构造哪一类"这一个分叉走联动分支。
     */
    public static ModularStaffItem createModularStaff(Item.Properties properties) {
        if (isGoety()) {
            try {
                return com.mofengbaizhi.tinkersnewlife.integration.goety.GoetyIntegration.createStaff(properties);
            } catch (Throwable t) {
                LOGGER.error("[联动] 真法杖形态构造失败，回落普通形态", t);
            }
        }
        return new ModularStaffItem(properties);
    }

    /** 该魔杖是否为真法杖形态（替代 {@code instanceof GoetyStaffItem}） */
    public static boolean isGoetyStaffItem(ItemStack stack) {
        if (!isGoety() || stack == null || stack.isEmpty()) return false;
        try {
            return com.mofengbaizhi.tinkersnewlife.integration.goety.GoetyIntegration.isStaff(stack);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 把"装备中聚晶"镜像写入真法杖本体槽 */
    public static void mirrorEquippedFocus(ServerPlayer player, ItemStack staff) {
        if (!isGoety()) return;
        try {
            com.mofengbaizhi.tinkersnewlife.integration.goety.GoetyIntegration.mirrorEquippedFocus(player, staff);
        } catch (Throwable t) {
            LOGGER.warn("[联动] 聚晶镜像写入失败", t);
        }
    }

    /** 按真法杖强度刷新持有者诡厄 Spell 属性 */
    public static void refreshSpellAttrs(ServerPlayer player, ItemStack staff) {
        if (!isGoety()) return;
        try {
            com.mofengbaizhi.tinkersnewlife.integration.goety.GoetyIntegration.refreshSpellAttrs(player, staff);
        } catch (Throwable t) {
            LOGGER.warn("[联动] Spell 属性刷新失败", t);
        }
    }

    /** 清空真法杖给持有者上的 Spell 属性 */
    public static void clearSpellAttrs(ServerPlayer player) {
        if (!isGoety()) return;
        try {
            com.mofengbaizhi.tinkersnewlife.integration.goety.GoetyIntegration.clearSpellAttrs(player);
        } catch (Throwable t) {
            LOGGER.warn("[联动] Spell 属性清理失败", t);
        }
    }

    // ============================================================
    //  公共代码按注册名取用（联动类静态字段一律不引用）
    //  ⚠ 一律经 SafeRegistry：Forge 的 getValue 对不存在的 id 返回"默认值"
    //    （ITEMS→AIR、FLUIDS→EMPTY），直接当物品用会得到 count=0 的空栈
    // ============================================================

    /** 按注册名取本模组物品；不存在返回 null（不是 {@code Items.AIR}） */
    @javax.annotation.Nullable
    public static Item item(String path) {
        return com.mofengbaizhi.tinkersnewlife.util.SafeRegistry.item(
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID, path);
    }

    /** 按注册名取本模组流体；不存在返回 null（不是 {@code Fluids.EMPTY}） */
    @javax.annotation.Nullable
    public static net.minecraft.world.level.material.Fluid fluid(String path) {
        return com.mofengbaizhi.tinkersnewlife.util.SafeRegistry.fluid(
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID, path);
    }

    /** 该物品是否真的存在（联动模组不在场时其内容整组未注册 → false） */
    public static boolean hasItem(String path) {
        return item(path) != null;
    }
}
