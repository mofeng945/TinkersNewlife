package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.content.item.CurseBottleItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * 封呪瓶（咒力容器）的数据层：瓶内咒力的读写、佩戴查询、以及"1mb 咒力残秽 = 10 咒力"的换算。
 *
 * <h2>数值约定</h2>
 * <ul>
 *   <li>瓶内咒力：{@code double}，存在物品 NBT（{@link #KEY_POWER}），上限 {@link #CAPACITY} = <b>5000</b>；</li>
 *   <li>咒力残秽：<b>1 mb = 10 咒力</b> ⇒ 满瓶 = {@link #CAPACITY_MB} mb = 500 mb；</li>
 *   <li>瓶内咒力是**流体量的唯一真值**：{@code 流体mb = floor(咒力 / 10)}，反过来 {@code 咒力 = mb * 10}。</li>
 * </ul>
 *
 * <p>死亡不丢：瓶内咒力写在物品 NBT 上，与玩家咒力池（{@code player.getPersistentData()}）互相独立，
 * 玩家重生、咒力池被清空（天与咒缚等）都不会影响瓶内的储备。
 */
public final class CurseBottleHelper {

    /** 物品 NBT：瓶内咒力（double） */
    public static final String KEY_POWER = "tinkersnewlife.curse_bottle_power";
    /** 一个瓶子最多积攒的咒力 */
    public static final double CAPACITY = 5000.0;
    /** 1 mb 咒力残秽换算的咒力 */
    public static final int POWER_PER_MB = 10;
    /** 瓶子能装的咒力残秽（mb）= 500 */
    public static final int CAPACITY_MB = (int) (CAPACITY / POWER_PER_MB);

    private CurseBottleHelper() {
    }

    // ============================================================
    //  单瓶读写
    // ============================================================

    /** 读取瓶内咒力（0 ~ 上限） */
    public static double getPower(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getTag() == null) return 0.0;
        double v = stack.getTag().getDouble(KEY_POWER);
        if (v <= 0) return 0.0;
        return Math.min(v, CAPACITY);
    }

    /** 直接设置瓶内咒力（钳制到 0 ~ 上限；为 0 时清掉标签，避免物品永远"带数据"） */
    public static void setPower(ItemStack stack, double value) {
        if (stack == null || stack.isEmpty()) return;
        double v = Math.max(0.0, Math.min(value, CAPACITY));
        if (v <= 0.0) {
            if (stack.getTag() != null) stack.getTag().remove(KEY_POWER);
            return;
        }
        stack.getOrCreateTag().putDouble(KEY_POWER, v);
    }

    /** 往瓶里加咒力，返回**实际加进去**的量（满瓶时返回 0） */
    public static double addPower(ItemStack stack, double amount) {
        if (stack == null || stack.isEmpty() || amount <= 0) return 0.0;
        double current = getPower(stack);
        double added = Math.min(amount, CAPACITY - current);
        if (added <= 0) return 0.0;
        setPower(stack, current + added);
        return added;
    }

    /** 从瓶里扣咒力，返回**实际扣掉**的量（空瓶返回 0） */
    public static double consumePower(ItemStack stack, double amount) {
        if (stack == null || stack.isEmpty() || amount <= 0) return 0.0;
        double current = getPower(stack);
        double used = Math.min(current, amount);
        if (used <= 0) return 0.0;
        setPower(stack, current - used);
        return used;
    }

    /** 瓶内液面（咒力残秽 mb，向下取整） */
    public static int getFluidAmount(ItemStack stack) {
        return (int) Math.floor(getPower(stack) / POWER_PER_MB);
    }

    /** 该物品是不是封呪瓶 */
    public static boolean isBottle(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof CurseBottleItem;
    }

    // ============================================================
    //  佩戴中的瓶子（curios「饰品」槽）
    // ============================================================

    /**
     * 玩家佩戴的封呪瓶：优先取**没满**的那一个（多个瓶子时先灌满前面的），
     * 都满了就返回第一个（供读取总量用）。未佩戴返回空栈。
     */
    public static ItemStack findWorn(Player player) {
        if (player == null) return ItemStack.EMPTY;
        var curios = CuriosApi.getCuriosInventory(player).resolve();
        if (curios.isEmpty()) return ItemStack.EMPTY;
        ItemStack first = ItemStack.EMPTY;
        for (ICurioStacksHandler handler : curios.get().getCurios().values()) {
            IDynamicStackHandler stacks = handler.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (!isBottle(stack)) continue;
                if (first.isEmpty()) first = stack;
                if (getPower(stack) < CAPACITY) return stack;   // 找到没满的
            }
        }
        return first;
    }

    /**
     * 遍历佩戴的封呪瓶，对每个瓶子执行一次操作。
     * <p>⭐ 咒力恢复是**每 tick** 调用的，所以这里刻意不建 List、不额外分配对象。
     */
    private static void forEachWornBottle(Player player, java.util.function.Consumer<ItemStack> action) {
        if (player == null) return;
        var curios = CuriosApi.getCuriosInventory(player).resolve();
        if (curios.isEmpty()) return;
        for (ICurioStacksHandler handler : curios.get().getCurios().values()) {
            IDynamicStackHandler stacks = handler.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (isBottle(stack)) action.accept(stack);
            }
        }
    }

    /** 佩戴中的瓶子总咒力（未佩戴 = 0；供咒力核心统计） */
    public static double getWornPower(Player player) {
        double[] sum = new double[1];
        forEachWornBottle(player, stack -> sum[0] += getPower(stack));
        return sum[0];
    }

    /** 佩戴中的瓶子总容量（每个 {@link #CAPACITY}；未佩戴 = 0；供咒力核心统计） */
    public static double getWornCapacity(Player player) {
        int[] count = new int[1];
        forEachWornBottle(player, stack -> count[0]++);
        return count[0] * CAPACITY;
    }

    /** 佩戴中的封呪瓶数量 */
    public static int getWornCount(Player player) {
        int[] count = new int[1];
        forEachWornBottle(player, stack -> count[0]++);
        return count[0];
    }

    /** 把 amount 优先灌进佩戴的瓶子，返回**灌不下、需要落到咒力核心池**的余量 */
    public static double storeIntoWorn(Player player, double amount) {
        double[] remaining = new double[]{ amount };
        forEachWornBottle(player, stack -> {
            if (remaining[0] <= 0) return;
            remaining[0] -= addPower(stack, remaining[0]);
        });
        return Math.max(0, remaining[0]);
    }

    /** 从佩戴的瓶子扣咒力（依次扣），返回**实际扣掉**的量 */
    public static double consumeFromWorn(Player player, double amount) {
        double[] state = new double[]{ amount, 0.0 };   // [0]=还要扣多少, [1]=已扣多少
        forEachWornBottle(player, stack -> {
            if (state[0] <= 0) return;
            double took = consumePower(stack, state[0]);
            state[1] += took;
            state[0] -= took;
        });
        return state[1];
    }
}
