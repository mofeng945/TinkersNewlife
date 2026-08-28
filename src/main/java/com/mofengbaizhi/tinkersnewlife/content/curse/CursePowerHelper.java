package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.item.CurseCoreItem;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * 咒力系统核心辅助类（服务端）
 * <p>
 * 咒力是佩戴咒力核心玩家的专属资源：
 * <ul>
 *   <li>咒力上限 = 咒力总量等级 × (咒力输出等级 + 咒力亲和/10) × 100</li>
 *   <li>每 5 秒恢复 (咒力输出等级 + 咒力亲和/10) × 5 点（仅佩戴咒力核心时）</li>
 *   <li>领域每秒消耗 半径×20 点咒力</li>
 * </ul>
 * 咒力数值与「咒力无限」截止时刻保存在玩家持久数据中（死亡/重登保留）。
 */
public final class CursePowerHelper {

    private CursePowerHelper() {}

    /** 玩家持久数据：当前咒力（double） */
    public static final String KEY_CURSE = "tinkersnewlife.curse_power";
    /** 玩家持久数据：咒力无限截止时刻（long，服务器 tick） */
    public static final String KEY_INFINITE_UNTIL = "tinkersnewlife.curse_infinite_until";
    /** 物品 NBT：咒力亲和值（int，0~50，战利品生成的饰品可能携带） */
    public static final String KEY_AFFINITY = "tinkersnewlife.curse_affinity";
    /** 匠魂工具持久数据键（ModDataNBT 用 ResourceLocation） */
    private static final ResourceLocation KEY_AFFINITY_TOOL =
            new ResourceLocation(TinkersNewlife.MOD_ID, "curse_affinity");

    // ============================================================
    //  佩戴的咒力核心
    // ============================================================

    /** 查找玩家佩戴的咒力核心（curios 咒力核心槽），未佩戴返回空栈 */
    public static ItemStack findEquippedCurseCore(Player player) {
        if (player == null || player.level().isClientSide) return ItemStack.EMPTY;
        var curios = CuriosApi.getCuriosInventory(player).resolve();
        if (curios.isEmpty()) return ItemStack.EMPTY;
        var slot = curios.get().findFirstCurio(stack -> stack.getItem() instanceof CurseCoreItem);
        return slot.map(s -> s.stack()).orElse(ItemStack.EMPTY);
    }

    /** 读取咒力核心工具上指定修改器的等级 */
    public static int getModifierLevel(ItemStack core, ModifierId id) {
        if (core.isEmpty()) return 0;
        ToolStack tool = ToolHelper.getToolStack(core);
        return tool == null ? 0 : tool.getModifierLevel(id);
    }

    /** 咒力输出等级（佩戴的咒力核心） */
    public static int getCurseOutputLevel(Player player) {
        return getModifierLevel(findEquippedCurseCore(player), Modifiers.CURSE_OUTPUT.getId());
    }

    /** 咒力总量等级（佩戴的咒力核心） */
    public static int getCurseTotalLevel(Player player) {
        return getModifierLevel(findEquippedCurseCore(player), Modifiers.CURSE_TOTAL.getId());
    }

    /** 坐杀搏徒等级（佩戴的咒力核心） */
    public static int getZuoShaBoTuLevel(Player player) {
        return getModifierLevel(findEquippedCurseCore(player), Modifiers.ZUOSHA_BOTU.getId());
    }

    // ============================================================
    //  咒力亲和（玩家修饰符，初始 0）
    // ============================================================

    /**
     * 玩家当前咒力亲和 = 所有已装备饰品携带的咒力亲和之和（初始 0）。
     * 战利品生成的饰品可能随机携带 0~50 点亲和（见 CurseAffinityLootModifier）。
     */
    public static int getCurseAffinity(Player player) {
        var curios = CuriosApi.getCuriosInventory(player).resolve();
        if (curios.isEmpty()) return 0;
        int sum = 0;
        for (ICurioStacksHandler handler : curios.get().getCurios().values()) {
            IDynamicStackHandler stacks = handler.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                sum += getCurseAffinity(stack);
            }
        }
        return sum;
    }

    /** 读取单件物品的咒力亲和（匠魂工具读持久数据，普通物品读原始标签） */
    public static int getCurseAffinity(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool != null) {
            return tool.getPersistentData().getInt(KEY_AFFINITY_TOOL);
        }
        if (stack.getTag() == null) return 0;
        return stack.getTag().getInt(KEY_AFFINITY);
    }

    /** 设置单件物品的咒力亲和（匠魂工具写持久数据，避免被 updateStack 整标签替换抹掉） */
    public static void setCurseAffinity(ItemStack stack, int value) {
        if (stack.isEmpty()) return;
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool != null) {
            tool.getPersistentData().putInt(KEY_AFFINITY_TOOL, Math.max(0, value));
            tool.updateStack(stack);
            return;
        }
        stack.getOrCreateTag().putInt(KEY_AFFINITY, Math.max(0, value));
    }

    // ============================================================
    //  咒力数值
    // ============================================================

    /** 咒力上限 = 咒力总量等级 × (咒力输出等级 + 咒力亲和/10) × 500 */
    public static double getMaxCurse(Player player) {
        int total = getCurseTotalLevel(player);
        int output = getCurseOutputLevel(player);
        int affinity = getCurseAffinity(player);
        return total * (output + affinity / 10.0) * 500.0;
    }

    /** 当前咒力 */
    public static double getCurse(Player player) {
        return player.getPersistentData().getDouble(KEY_CURSE);
    }

    /** 设置咒力（自动夹在 0 与上限之间） */
    public static void setCurse(Player player, double value) {
        double max = getMaxCurse(player);
        player.getPersistentData().putDouble(KEY_CURSE, Math.max(0, Math.min(value, max)));
    }

    /** 增加咒力（不超过上限） */
    public static void addCurse(Player player, double amount) {
        setCurse(player, getCurse(player) + amount);
    }

    /** 消耗咒力（不低于 0） */
    public static void spendCurse(Player player, double amount) {
        setCurse(player, getCurse(player) - amount);
    }

    // ============================================================
    //  咒力无限
    // ============================================================

    /** 是否处于咒力无限状态（60 秒窗口内） */
    public static boolean isCurseInfinite(Player player) {
        return player.getPersistentData().getLong(KEY_INFINITE_UNTIL) > player.level().getGameTime();
    }

    /** 设置咒力无限截止时刻（重复触发会刷新到最新 60 秒窗口） */
    public static void setInfiniteUntil(Player player, long until) {
        player.getPersistentData().putLong(KEY_INFINITE_UNTIL, until);
    }
}
