package com.mofengbaizhi.tinkersnewlife.content.energy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * 古老者水晶的<b>数据层</b>：EE 的读写、容量、以及"玩家身上/佩戴处有哪些水晶"的扫描与抽能。
 *
 * <h2>两个存放位置（都是 NBT "EE" ✓ 键名统一）</h2>
 * <ul>
 *   <li><b>水晶物品</b>{@code tinkersnewlife:elder_crystal}：EE 直接写在<b>物品根标签</b>
 *       （{@code stack.getTag().getInt("EE")}）✓ 容量 {@link #CRYSTAL_CAPACITY}；</li>
 *   <li><b>水晶方块物品</b>{@code tinkersnewlife:elder_crystal_block}：EE 写在
 *       <b>{@code BlockEntityTag.EE}</b> ✓ 容量 {@link #BLOCK_CAPACITY}——
 *       这是原版方块物品的通用约定 ✓（潜影盒同款 ✓）：
 *       <pre>
 *         放下：BlockItem#updateCustomBlockEntityTag 会把 BlockEntityTag 灌进方块实体 ✓
 *         挖掉：战利品表 copy_nbt（source=block_entity）把方块实体的 NBT 写回 BlockEntityTag ✓
 *       </pre>
 *       于是"挖掉掉自己、魔力一起带走"是原版机制自带的 ✓ 不需要我们自己发明包裹物品 ✗。</li>
 * </ul>
 *
 * <p>方块实体自己也用同一个键 {@code EE}（见 {@code block.ElderCrystalBlockEntity}）✓
 * ⇒ 物品 ↔ 方块 的搬运是"同一个键从物品搬到方块实体"，没有任何映射表 ✗ 不会对不上。
 */
public final class ElderCrystalStorage {

    private ElderCrystalStorage() {}

    /** 水晶物品的 EE 键（同时也是方块实体的存档键 ✓） */
    public static final String KEY_EE = "EE";

    /** 水晶物品容量 */
    public static final int CRYSTAL_CAPACITY = 1000;

    /** 水晶方块容量 = 4 × 水晶（4 合 1 正好装满 ✓ 一点不丢 ✓） */
    public static final int BLOCK_CAPACITY = CRYSTAL_CAPACITY * 4;

    // ============================================================
    //  水晶物品：读写 EE
    // ============================================================

    /** 水晶物品里的 EE（0 ~ {@link #CRYSTAL_CAPACITY}；空栈/无标签 = 0） */
    public static int getCrystalEe(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getTag() == null) return 0;
        return clamp(stack.getTag().getInt(KEY_EE), CRYSTAL_CAPACITY);
    }

    /** 写水晶物品的 EE（EE<=0 时把键删掉 ✓ 空水晶连标签都没有 ⇒ 能正常堆叠 ✓） */
    public static void setCrystalEe(ItemStack stack, int ee) {
        if (stack == null || stack.isEmpty()) return;
        int v = clamp(ee, CRYSTAL_CAPACITY);
        if (v <= 0) {
            if (stack.getTag() != null) stack.getTag().remove(KEY_EE);
            return;
        }
        stack.getOrCreateTag().putInt(KEY_EE, v);
    }

    /**
     * 往水晶物品里加 EE。
     *
     * @return 实际加进去的量（装不下的部分被拒绝 ✓ 不凭空蒸发别人的账 ✗）
     */
    public static int addCrystalEe(ItemStack stack, int amount) {
        if (stack == null || stack.isEmpty() || amount <= 0) return 0;
        int cur = getCrystalEe(stack);
        int add = Math.min(amount, CRYSTAL_CAPACITY - cur);
        if (add <= 0) return 0;
        setCrystalEe(stack, cur + add);
        return add;
    }

    // ============================================================
    //  水晶方块物品：读写 BlockEntityTag.EE
    // ============================================================

    /** 水晶方块物品里带的 EE（无 BlockEntityTag = 0） */
    public static int getBlockItemEe(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getTag() == null) return 0;
        CompoundTag be = stack.getTag().getCompound(net.minecraft.world.item.BlockItem.BLOCK_ENTITY_TAG);
        return clamp(be.getInt(KEY_EE), BLOCK_CAPACITY);
    }

    /** 写水晶方块物品的 EE（EE<=0 时清掉 BlockEntityTag ✓ 不留空壳标签 ✗） */
    public static void setBlockItemEe(ItemStack stack, int ee) {
        if (stack == null || stack.isEmpty()) return;
        int v = clamp(ee, BLOCK_CAPACITY);
        if (v <= 0) {
            if (stack.getTag() != null) stack.getTag().remove(net.minecraft.world.item.BlockItem.BLOCK_ENTITY_TAG);
            return;
        }
        stack.getOrCreateTagElement(net.minecraft.world.item.BlockItem.BLOCK_ENTITY_TAG).putInt(KEY_EE, v);
    }

    // ============================================================
    //  通用读取（不关心是水晶还是方块）
    // ============================================================

    /** 是"古老者水晶"系物品吗（水晶物品 或 水晶方块物品） */
    public static boolean isCrystal(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        net.minecraft.world.item.Item item = stack.getItem();
        return item == com.mofengbaizhi.tinkersnewlife.content.ModItems.ELDER_CRYSTAL.get()
                || item == com.mofengbaizhi.tinkersnewlife.content.ModItems.ELDER_CRYSTAL_BLOCK.get();
    }

    /** 该水晶系物品的容量（水晶 1000 / 方块 4000；非水晶 = 0） */
    public static int capacityOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        if (stack.getItem() == com.mofengbaizhi.tinkersnewlife.content.ModItems.ELDER_CRYSTAL_BLOCK.get()) {
            return BLOCK_CAPACITY;
        }
        if (stack.getItem() == com.mofengbaizhi.tinkersnewlife.content.ModItems.ELDER_CRYSTAL.get()) {
            return CRYSTAL_CAPACITY;
        }
        return 0;
    }

    /** 该水晶系物品当前存的 EE（非水晶 = 0） */
    public static int eeOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        if (stack.getItem() == com.mofengbaizhi.tinkersnewlife.content.ModItems.ELDER_CRYSTAL_BLOCK.get()) {
            return getBlockItemEe(stack);
        }
        if (stack.getItem() == com.mofengbaizhi.tinkersnewlife.content.ModItems.ELDER_CRYSTAL.get()) {
            return getCrystalEe(stack);
        }
        return 0;
    }

    // ============================================================
    //  玩家身上的扫描
    // ============================================================

    /**
     * <b>背包 + 副手 + 饰品</b>里所有水晶的 EE 之和 —— 供"寒冷反噬"用 ✓（用户口径：背包/副手/饰品都算）。
     *
     * <p>⚠ 只统计"存了电"的水晶 ⇒ 空水晶贡献 0 ✓（空水晶完全不冷 ✓）。
     */
    public static int totalCarriedEe(Player player) {
        if (player == null) return 0;
        int total = 0;
        // 主背包（含快捷栏，36 格）
        for (ItemStack stack : player.getInventory().items) {
            if (isCrystal(stack)) total += eeOf(stack);
        }
        // 副手
        if (isCrystal(player.getOffhandItem())) total += eeOf(player.getOffhandItem());
        // 饰品（curios 任意槽）
        total += curiosEe(player);
        return total;
    }

    /**
     * <b>副手 + 饰品</b>里所有水晶的 EE 之和 —— 供"为施法供能"用 ✓
     * （用户口径：放副手/饰品时才为施法供能 ⇒ <b>不看背包</b> ✗）。
     */
    public static int suppliedEe(Player player) {
        if (player == null) return 0;
        int total = 0;
        if (isCrystal(player.getOffhandItem())) total += eeOf(player.getOffhandItem());
        total += curiosEe(player);
        return total;
    }

    /**
     * 从"副手 → 饰品"里抽 {@code amount} 点 EE。
     *
     * @return 实际抽出的量（不足则有多少抽多少 ✓）
     */
    public static int drainSuppliedEe(Player player, int amount) {
        if (player == null || amount <= 0) return 0;
        int need = amount;
        // ① 副手
        ItemStack off = player.getOffhandItem();
        if (isCrystal(off)) need -= takeFrom(off, need);
        // ② 饰品
        if (need > 0) need -= drainCurios(player, need);
        return amount - Math.max(0, need);
    }

    /** 水晶系物品抽能（就地改写 NBT ✓） */
    private static int takeFrom(ItemStack stack, int amount) {
        int cur = eeOf(stack);
        int take = Math.min(cur, amount);
        if (take <= 0) return 0;
        if (stack.getItem() == com.mofengbaizhi.tinkersnewlife.content.ModItems.ELDER_CRYSTAL_BLOCK.get()) {
            setBlockItemEe(stack, cur - take);
        } else {
            setCrystalEe(stack, cur - take);
        }
        return take;
    }

    private static int curiosEe(Player player) {
        var curios = CuriosApi.getCuriosInventory(player).resolve();
        if (curios.isEmpty()) return 0;
        int total = 0;
        for (ICurioStacksHandler handler : curios.get().getCurios().values()) {
            IDynamicStackHandler stacks = handler.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (isCrystal(stack)) total += eeOf(stack);
            }
        }
        return total;
    }

    private static int drainCurios(Player player, int amount) {
        var curios = CuriosApi.getCuriosInventory(player).resolve();
        if (curios.isEmpty()) return 0;
        int need = amount;
        for (ICurioStacksHandler handler : curios.get().getCurios().values()) {
            IDynamicStackHandler stacks = handler.getStacks();
            for (int i = 0; i < slots(stacks) && need > 0; i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (isCrystal(stack)) need -= takeFrom(stack, need);
            }
            if (need <= 0) break;
        }
        return amount - Math.max(0, need);
    }

    private static int slots(IDynamicStackHandler stacks) {
        return stacks.getSlots();
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(v, max));
    }
}
