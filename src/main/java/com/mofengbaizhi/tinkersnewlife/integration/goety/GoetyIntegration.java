package com.mofengbaizhi.tinkersnewlife.integration.goety;

import com.mofengbaizhi.tinkersnewlife.content.item.ModularStaffItem;
import com.mofengbaizhi.tinkersnewlife.integration.Integration;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 诡厄巫法（goety）联动模块。
 *
 * <h2>隔离约定</h2>
 * 本类<b>不 import 任何诡厄类型</b>（只做分派），真正引用诡厄 API 的代码在同包的
 * {@link GoetyStaffItem}——它只在诡厄在场时才被本类触达，因此未安装诡厄时不会被 JVM 加载。
 *
 * <h2>与旧实现（util.GoetyStaffBridge）的区别</h2>
 * 旧实现靠 {@code Class.forName("…GoetyStaffItem")} 反射造物品、反射调静态方法；
 * 现在存在性判定统一走 {@code ModList}（{@code IntegrationLoader}），真形态通过本类
 * <b>直接 new / 直接调用</b>，不再有"字符串类名 + 反射"这一层易碎环节。
 */
public final class GoetyIntegration implements Integration {

    private static final Logger LOGGER = LoggerFactory.getLogger("TinkersNewlife");

    @Override
    public String modId() {
        return MOD_ID;
    }

    public static final String MOD_ID = "goety";

    @Override
    public void register(IEventBus bus) {
        // 本模组在诡厄侧没有需要单独注册的注册表对象：
        //  · 魔杖真形态（GoetyStaffItem）与普通形态共用同一物品 id "tinkersnewlife:modular_staff"，
        //    注册点必须留在公共侧（ModItems），只有"构造哪一类"的分叉走 IntegrationLoader.createModularStaff；
        //  · 高头骨（tall_skull）黏液头颅外观属纯客户端渲染，由 goety.client.GoetyClientIntegration 订阅客户端事件注册；
        //  · 其余联动（咒言/聚晶/属性/灵魂）全部是运行时读写，无注册表项。
        LOGGER.info("[联动] 诡厄巫法在场：模块化魔杖启用真法杖形态（IWand 原生施法），"
                + "高头骨黏液头颅外观启用，咒言/聚晶/属性/灵魂联动全部开启");
    }

    // ============================================================
    //  真法杖形态（仅在本类被加载 = 诡厄在场时才可能被调用）
    // ============================================================

    /** 直接构造真法杖（不再需要反射：本类只在诡厄在场时加载） */
    public static ModularStaffItem createStaff(Item.Properties properties) {
        return new GoetyStaffItem(properties);
    }

    /** 是否为真法杖形态 */
    public static boolean isStaff(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof GoetyStaffItem;
    }

    /** 把"装备中聚晶"镜像写入真法杖本体槽 */
    public static void mirrorEquippedFocus(ServerPlayer player, ItemStack staff) {
        GoetyStaffItem.mirrorEquippedFocus(player, staff);
    }

    /** 按真法杖强度刷新持有者诡厄 Spell 属性 */
    public static void refreshSpellAttrs(ServerPlayer player, ItemStack staff) {
        GoetyStaffItem.refreshSpellAttrs(player, staff);
    }

    /** 清空真法杖给持有者上的 Spell 属性 */
    public static void clearSpellAttrs(ServerPlayer player) {
        GoetyStaffItem.clearSpellAttrs(player);
    }
}
