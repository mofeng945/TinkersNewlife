package com.mofengbaizhi.tinkersnewlife.content.curse;
import com.mofengbaizhi.tinkersnewlife.content.curse.binding.BindingStateHandler;

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
    /** 玩家持久数据：特等奖增益截止时刻（long，33 秒 HP 锁定等） */
    public static final String KEY_GRAND_UNTIL = "tinkersnewlife.grand_until";
    /** 玩家持久数据：临时咒力亲和加成（int，特等奖 +100） */
    public static final String KEY_AFFINITY_BUFF = "tinkersnewlife.curse_affinity_buff";
    /** 玩家持久数据：临时咒力亲和加成截止时刻（long） */
    public static final String KEY_AFFINITY_BUFF_UNTIL = "tinkersnewlife.curse_affinity_buff_until";
    /** 玩家持久数据：术式熔断截止时刻（long，期间无法展开领域/使用术式） */
    public static final String KEY_BURNOUT_UNTIL = "tinkersnewlife.burnout_until";
    /** 物品 NBT：咒力亲和值（int，0~50，战利品生成的饰品可能携带） */
    public static final String KEY_AFFINITY = "tinkersnewlife.curse_affinity";
    /** 匠魂工具持久数据键（ModDataNBT 用 ResourceLocation） */
    private static final ResourceLocation KEY_AFFINITY_TOOL =
            new ResourceLocation(TinkersNewlife.MOD_ID, "curse_affinity");

    // ============================================================
    //  佩戴的咒力核心
    // ============================================================

    /** 查找玩家佩戴的咒力核心（curios 咒力核心槽），未佩戴返回空栈（服务端与客户端本地镜像均适用） */
    public static ItemStack findEquippedCurseCore(Player player) {
        if (!com.mofengbaizhi.tinkersnewlife.config.ModConfig.CURSE_CORE_ENABLED.get()) return ItemStack.EMPTY;   // 配置：关闭咒力核心使用
        if (player == null) return ItemStack.EMPTY;
        var curios = CuriosApi.getCuriosInventory(player).resolve();
        if (curios.isEmpty()) return ItemStack.EMPTY;
        var slot = curios.get().findFirstCurio(stack -> stack.getItem() instanceof CurseCoreItem);
        return slot.map(s -> s.stack()).orElse(ItemStack.EMPTY);
    }

    /** 该咒力核心是否正被玩家佩戴（客户端镜像同样适用：身份或内容一致即可） */
    public static boolean isEquippedCurseCore(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        ItemStack worn = findEquippedCurseCore(player);
        if (worn.isEmpty()) return false;
        return worn == stack || ItemStack.isSameItemSameTags(worn, stack);
    }

    /** 读取咒力核心工具上指定修改器的等级 */
    public static int getModifierLevel(ItemStack core, ModifierId id) {
        if (core.isEmpty()) return 0;
        ToolStack tool = ToolHelper.getToolStack(core);
        return tool == null ? 0 : tool.getModifierLevel(id);
    }

    /** 咒力输出等级（佩戴的咒力核心；天与咒缚·咒力者佩戴时自动 +1 级；咒种寄生时 -1 级且不低于 1） */
    public static int getCurseOutputLevel(Player player) {
        int level = getModifierLevel(findEquippedCurseCore(player), Modifiers.CURSE_OUTPUT.getId());
        if (level > 0) {
            level += com.mofengbaizhi.tinkersnewlife.content.curse.binding.BindingStateHandler.getCoreLevelBonus(player);
        }
        if (level > 0 && hasSeedParasite(player)) {
            level = Math.max(1, level - 1);
        }
        return level;
    }

    /** 咒种寄生是否生效（草木操术·咒种）：攻击 -40%（效果自带修正），咒力属性按此扣减 */
    public static boolean hasSeedParasite(Player player) {
        return player.hasEffect(com.mofengbaizhi.tinkersnewlife.content.ModEffects.SEED_PARASITE.get());
    }

    /** 咒力总量等级（佩戴的咒力核心；天与咒缚·咒力者佩戴时自动 +1 级；咒种寄生时 -1 级且不低于 1） */
    public static int getCurseTotalLevel(Player player) {
        int level = getModifierLevel(findEquippedCurseCore(player), Modifiers.CURSE_TOTAL.getId());
        if (level > 0) {
            level += com.mofengbaizhi.tinkersnewlife.content.curse.binding.BindingStateHandler.getCoreLevelBonus(player);
        }
        if (level > 0 && hasSeedParasite(player)) {
            level = Math.max(1, level - 1);
        }
        return level;
    }

    /** 坐杀搏徒等级（佩戴的咒力核心） */
    public static int getZuoShaBoTuLevel(Player player) {
        return getModifierLevel(findEquippedCurseCore(player), Modifiers.ZUOSHA_BOTU.getId());
    }

    // ============================================================
    //  咒力亲和（玩家修饰符，初始 0）
    // ============================================================

    /**
     * 玩家当前咒力亲和 = 所有已装备饰品携带的咒力亲和之和（初始 0）+ 临时加成（特等奖 +100）
     * + 天与咒缚·咒力的自带亲和（200）。
     * 战利品生成的饰品可能随机携带 0~50 点亲和（见 CurseAffinityLootModifier）。
     */
    public static int getCurseAffinity(Player player) {
        var curios = CuriosApi.getCuriosInventory(player).resolve();
        int sum = 0;
        if (curios.isPresent()) {
            for (ICurioStacksHandler handler : curios.get().getCurios().values()) {
                IDynamicStackHandler stacks = handler.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    sum += getCurseAffinity(stack);
                }
            }
        }
        return Math.max(0, sum + getTemporaryAffinity(player)
                + com.mofengbaizhi.tinkersnewlife.content.curse.binding.BindingStateHandler.getAffinityBonus(player)
                - (hasSeedParasite(player) ? 60 : 0));
    }

    /** 设置临时咒力亲和加成（到期后自动失效） */
    public static void setCurseAffinityBuff(Player player, int amount, long until) {
        player.getPersistentData().putInt(KEY_AFFINITY_BUFF, amount);
        player.getPersistentData().putLong(KEY_AFFINITY_BUFF_UNTIL, until);
    }

    /** 当前生效的临时咒力亲和加成（过期返回 0） */
    public static int getTemporaryAffinity(Player player) {
        if (player.getPersistentData().getLong(KEY_AFFINITY_BUFF_UNTIL) <= player.level().getGameTime()) {
            return 0;
        }
        return player.getPersistentData().getInt(KEY_AFFINITY_BUFF);
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

    /**
     * 增加咒力（不超过上限）。⭐ 存储优先级：**佩戴的封呪瓶 → 绑定自己的呪蔵（放下的方块）→ 咒力核心池**
     * （核心池是"临时存储"，垫底）。
     */
    public static void addCurse(Player player, double amount) {
        if (amount <= 0) return;
        double leftover = CurseBottleHelper.storeIntoWorn(player, amount);
        if (leftover > 0) leftover = storeIntoVaults(player, leftover);
        if (leftover > 0) setCurse(player, getCurse(player) + leftover);
    }

    /**
     * 消耗咒力（不低于 0）。⭐ 级联顺序：**核心池 → 佩戴的封呪瓶 → 绑定自己的呪蔵**。
     * <p>判定"够不够"请配套用 {@link #canPayCurse}（同样口径），不要只看核心池。
     */
    public static void spendCurse(Player player, double amount) {
        spendCurseCascade(player, amount);
    }

    /**
     * 咒力消耗级联：**咒力核心池 → 封呪瓶 → 呪蔵**（与 {@link #getTotalCurse} 的口径一致）。
     * <p>灵魂能量兜底不在这里（见 {@link #payCurseWithSoulFallback}），本方法只处理咒力本体。
     *
     * @return 仍然付不清的余量（0 = 已付清）
     */
    public static double spendCurseCascade(Player player, double amount) {
        if (amount <= 0) return 0;
        double pool = getCurse(player);
        double used = Math.min(pool, amount);
        if (used > 0) setCurse(player, pool - used);
        double remaining = amount - used;
        if (remaining <= 0) return 0;
        remaining = Math.max(0, remaining - CurseBottleHelper.consumeFromWorn(player, remaining));
        if (remaining <= 0) return 0;
        return Math.max(0, remaining - consumeFromVaults(player, remaining));
    }

    /**
     * 付得起吗？**口径 = 核心池 + 佩戴的封呪瓶 + 绑定自己的所有呪蔵**（与 {@link #spendCurseCascade} 的扣费顺序一致）。
     *
     * <p>⭐ <b>所有"能不能发动"的判定都必须用它</b>：以前好几处直接看 {@link #getCurse}（核心池），
     * 于是核心池一空就误判"咒力不足"——哪怕封呪瓶、呪蔵里还存着几十万咒力
     * （实测用户报的正是这个：术式扣费不读瓶子和呪蔵）。
     * 咒力无限时恒为 true。
     */
    public static boolean canPayCurse(Player player, double amount) {
        if (amount <= 0) return true;
        if (isCurseInfinite(player)) return true;
        return getTotalCurse(player) >= amount;
    }

    // ------------------------------------------------------------
    //  呪蔵（放置的方块容器）
    // ------------------------------------------------------------

    /** 把咒力灌进该玩家绑定的所有呪蔵，返回灌不下的余量（数据在 world data 里，不要求区块加载） */
    public static double storeIntoVaults(Player player, double amount) {
        if (amount <= 0) return 0;
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) return amount;   // 客户端/无服务器：交给上层处理
        return CurseVaultData.storeForPlayer(server, player.getUUID(), amount);
    }

    /** 从该玩家绑定的呪蔵里扣咒力，返回实际扣掉的量 */
    public static double consumeFromVaults(Player player, double amount) {
        if (amount <= 0) return 0;
        net.minecraft.server.MinecraftServer server = player.getServer();
        if (server == null) return 0;
        return CurseVaultData.consumeForPlayer(server, player.getUUID(), amount);
    }

    /** 绑定该玩家的所有呪蔵里储存的咒力（跨维度） */
    public static double getVaultCurse(Player player) {
        net.minecraft.server.MinecraftServer server = player.getServer();
        return server == null ? 0 : CurseVaultData.sumPowerFor(server, player.getUUID());
    }

    /** 绑定该玩家的呪蔵提供的总容量（每个 10 万） */
    public static double getVaultCapacity(Player player) {
        net.minecraft.server.MinecraftServer server = player.getServer();
        return server == null ? 0 : CurseVaultData.capacityFor(server, player.getUUID());
    }

    // ------------------------------------------------------------
    //  封呪瓶统计（咒力核心"统计"要算上瓶中的咒力与容量）
    // ------------------------------------------------------------

    /** 佩戴的封呪瓶里储存的咒力（未佩戴 = 0） */
    public static double getBottleCurse(Player player) {
        return CurseBottleHelper.getWornPower(player);
    }

    /** 佩戴的封呪瓶提供的咒力容量（每瓶 5000，未佩戴 = 0） */
    public static double getBottleCapacity(Player player) {
        return CurseBottleHelper.getWornCapacity(player);
    }

    /** 统计总量 = 咒力核心池 + 佩戴的封呪瓶 + 绑定自己的所有呪蔵（HUD/统计显示用） */
    public static double getTotalCurse(Player player) {
        return getCurse(player) + getBottleCurse(player) + getVaultCurse(player);
    }

    /** 统计总上限 = 咒力核心上限 + 封呪瓶容量 + 呪蔵容量（HUD/统计显示用） */
    public static double getTotalMaxCurse(Player player) {
        return getMaxCurse(player) + getBottleCapacity(player) + getVaultCapacity(player);
    }

    // ------------------------------------------------------------
    //  数值显示：超过 1 万（1w）用"万"记法
    // ------------------------------------------------------------

    /**
     * 咒力数值显示：{@code < 10000} 照旧显示整数/一位小数；{@code >= 10000} 用"万"记法
     * （10000 → {@code 1w}，12345 → {@code 1.2w}，1234567 → {@code 123.5w}）。
     */
    public static String formatAmount(double value) {
        double v = Math.max(0, value);
        if (v < 10000.0) {
            if (v == Math.floor(v)) return String.valueOf((long) v);
            return String.format(java.util.Locale.ROOT, "%.1f", v);
        }
        double wan = v / 10000.0;
        if (wan == Math.floor(wan)) return String.valueOf((long) wan) + "w";
        return String.format(java.util.Locale.ROOT, "%.1fw", wan);
    }

    /** 背包中结界碎片数量（1 碎片 = 25 咒力） */
    public static int countBoundaryFragments(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(com.mofengbaizhi.tinkersnewlife.content.ModItems.BOUNDARY_FRAGMENT.get())) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** 消耗指定数量结界碎片（优先从背包逐格扣除） */
    public static void consumeBoundaryFragments(Player player, int amount) {
        var fragment = com.mofengbaizhi.tinkersnewlife.content.ModItems.BOUNDARY_FRAGMENT.get();
        for (ItemStack stack : player.getInventory().items) {
            if (amount <= 0) break;
            if (!stack.is(fragment)) continue;
            int take = Math.min(stack.getCount(), amount);
            stack.shrink(take);
            amount -= take;
        }
    }

    /**
     * 支付咒力（领域消耗与术式消耗共用）：
     * 优先消耗背包中的结界碎片（1 碎片 = 25 咒力）→ 咒力核心池 → **封呪瓶** →
     * 差额按 1:3 由诡厄巫法灵魂能量兜底。
     * 返回 0 = 咒力/碎片支付，1 = 灵魂能量兜底支付，-1 = 全部不足。
     */
    public static int payCurseWithSoulFallback(Player player, double cost) {
        // 1) 优先消耗结界碎片
        int fragments = countBoundaryFragments(player);
        if (fragments > 0) {
            double fragValue = fragments * 25.0;
            if (fragValue >= cost) {
                int use = (int) Math.ceil(cost / 25.0);
                consumeBoundaryFragments(player, Math.min(use, fragments));
                return 0;
            }
            consumeBoundaryFragments(player, fragments);
            cost -= fragValue;
        }
        // 2) 咒力：咒力核心池 → 封呪瓶（级联）
        double deficit = spendCurseCascade(player, cost);
        if (deficit <= 0) return 0;
        // 3) 灵魂能量兜底（神灵金盔甲"灵魂折扣"：每级 -5% 灵魂消耗）
        int soulsNeeded = (int) Math.ceil(deficit * 3.0);
        int discount = soulDiscountLevel(player);
        if (discount > 0) {
            soulsNeeded = Math.max(1, (int) Math.ceil(soulsNeeded * (1.0 - 0.05 * discount)));
        }
        int souls = com.mofengbaizhi.tinkersnewlife.util.SoulEnergyBridge.getSouls(player);
        if (souls < soulsNeeded) return -1;
        return com.mofengbaizhi.tinkersnewlife.util.SoulEnergyBridge.decreaseSouls(player, soulsNeeded) ? 1 : -1;
    }

    /** 读取穿戴护甲上"灵魂折扣"特性总级数（对任意穿戴者生效） */
    public static int soulDiscountLevel(net.minecraft.world.entity.LivingEntity wearer) {
        int total = 0;
        for (net.minecraft.world.item.ItemStack armor : wearer.getArmorSlots()) {
            if (armor.isEmpty()) continue;
            slimeknights.tconstruct.library.tools.nbt.ToolStack tool =
                    com.mofengbaizhi.tinkersnewlife.util.ToolHelper.getToolStack(armor);
            if (tool != null) {
                total += tool.getModifierLevel(
                        com.mofengbaizhi.tinkersnewlife.content.modifier.SoulDiscountModifier.ID);
            }
        }
        return total;
    }

    // ============================================================
    //  咒力无限
    // ============================================================

    /** 是否处于咒力无限状态（60 秒窗口内；创造模式恒为无限，便于测试） */
    public static boolean isCurseInfinite(Player player) {
        if (player.isCreative()) return true;
        return player.getPersistentData().getLong(KEY_INFINITE_UNTIL) > player.level().getGameTime();
    }

    /** 设置咒力无限截止时刻（重复触发会刷新到最新 60 秒窗口） */
    public static void setInfiniteUntil(Player player, long until) {
        player.getPersistentData().putLong(KEY_INFINITE_UNTIL, until);
    }

    // ============================================================
    //  特等奖增益（33 秒）
    // ============================================================

    /** 设置特等奖增益截止时刻（HP 锁定上限等） */
    public static void setGrandUntil(Player player, long until) {
        player.getPersistentData().putLong(KEY_GRAND_UNTIL, until);
    }

    /** 特等奖增益是否生效中 */
    public static boolean isGrandActive(Player player) {
        return player.getPersistentData().getLong(KEY_GRAND_UNTIL) > player.level().getGameTime();
    }

    // ============================================================
    //  术式熔断
    // ============================================================

    /**
     * 设置术式熔断截止时刻（生存模式关闭/领域被破坏后触发）。
     * 时长 = 60 - (咒力输出等级×5 + 咒力亲和) 秒，最低 10 秒。
     */
    public static void applyBurnout(Player player) {
        int output = getCurseOutputLevel(player);
        int affinity = getCurseAffinity(player);
        int seconds = Math.max(10, 60 - (output * 5 + affinity));
        player.getPersistentData().putLong(KEY_BURNOUT_UNTIL, player.level().getGameTime() + seconds * 20L);
    }

    /** 是否处于术式熔断 */
    public static boolean isBurnout(Player player) {
        return player.getPersistentData().getLong(KEY_BURNOUT_UNTIL) > player.level().getGameTime();
    }

    /** 术式熔断剩余秒数（未熔断返回 0） */
    public static int getBurnoutRemainingSeconds(Player player) {
        long remaining = player.getPersistentData().getLong(KEY_BURNOUT_UNTIL) - player.level().getGameTime();
        if (remaining <= 0) return 0;
        return (int) ((remaining + 19) / 20);
    }

    // ============================================================
    //  术式/领域封印（雅各布天梯）
    // ============================================================

    /** 玩家持久数据：封印截止 gameTime（期间禁用术式与领域） */
    public static final String KEY_SEALED_UNTIL = "tinkersnewlife.sealed_until";

    /** 设置封印截止时刻（60 秒） */
    public static void applySeal(Player player, int seconds) {
        player.getPersistentData().putLong(KEY_SEALED_UNTIL, player.level().getGameTime() + seconds * 20L);
    }

    /** 是否处于封印（禁用术式与领域） */
    public static boolean isSealed(Player player) {
        return player.getPersistentData().getLong(KEY_SEALED_UNTIL) > player.level().getGameTime();
    }

    /** 封印剩余秒数（未封印返回 0） */
    public static int getSealedRemainingSeconds(Player player) {
        long remaining = player.getPersistentData().getLong(KEY_SEALED_UNTIL) - player.level().getGameTime();
        if (remaining <= 0) return 0;
        return (int) ((remaining + 19) / 20);
    }
}
