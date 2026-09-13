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
    /** 铁魔法 */
    public static final String IRON_SPELLBOOKS = "ironsspellbooks";
    /** 永恒枪械工坊 */
    public static final String TACZ = "tacz";
    /** JEI */
    public static final String JEI = "jei";
    /** 帕秋莉手册 */
    public static final String PATCHOULI = "patchouli";
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
            GOETY, GOETY_REVELATION, ICEANDFIRE, IRON_SPELLBOOKS, TACZ, JEI, PATCHOULI, AE2
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

        // ---- 逐模组分派：调用点位于 isLoaded 分支内，未安装时联动类不会被加载 ----
        if (isGoety()) {
            try {
                new com.mofengbaizhi.tinkersnewlife.integration.goety.GoetyIntegration().register(bus);
            } catch (Throwable t) {
                LOGGER.error("[联动] 诡厄巫法模块初始化失败（已降级为普通形态）", t);
            }
        }

        // 铁魔法：纯反射软依赖（无编译依赖），此处只做一次反射装填
        com.mofengbaizhi.tinkersnewlife.util.IronSpellsReflector.init();
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

    /** 共通物品：按注册名取本模组物品（公共代码的推荐取用方式） */
    public static Item item(String path) {
        return net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                new net.minecraft.resources.ResourceLocation(
                        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID, path));
    }
}
