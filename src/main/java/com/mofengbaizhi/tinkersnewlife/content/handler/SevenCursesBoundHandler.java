package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandlerModifiable;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * ⭐ §1118y <b>七咒所缚</b>的拦截器（用户口径 ✓）。
 *
 * <h2>规矩（用户原文 ✓）</h2>
 * 「只有承受**七咒**时间为**在世界上时间 99% 以上**的人才可以使用它」✓；
 * 「不满足条件的人**手持、装备或装配进饰品时**会自动将工具**丢回背包**，
 * 如果背包中没有空位会**自动将其扔在地上**」✓。
 *
 * <h2>判定数据（持久化 ✓ 照仓库口径 {@code player.getPersistentData()} ✓）</h2>
 * <ul>
 *   <li>{@link #KEY_TOTAL} ✓：玩家**在线总 tick**（每个 tick ＋1 ✓）；</li>
 *   <li>{@link #KEY_CURSED} ✓：其中**受七咒的 tick**（身上带七咒之戒时 ＋1 ✓）。</li>
 * </ul>
 * ⇒ 合格条件 ✓：{@code cursed * 100 >= total * 90} ✓（**整数运算** ✓ 免得浮点误差 ✗）。
 *
 * <p>「受七咒」的判据 ✓＝身上带神秘遗物的**七咒之戒** ✓（id {@link #CURSED_RING} ✓，
 * 与 {@code client/handler/CursedRingTooltipHandler} 的判定一致 ✓）：主背包 ✓ 护甲槽 ✓ 副手 ✓ 饰品 ✓ 都算 ✓。
 *
 * ⚠ <b>只做"塞回背包/丢地上"</b> ✓ 不做别的 ✗ —— 这是用户口径里唯一要求的动作 ✓。
 * <p>⚠ 每 {@link #CHECK_INTERVAL} tick 才真正检查一次 ✓（每 tick 扫背包太浪费 ✗）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SevenCursesBoundHandler {

    private SevenCursesBoundHandler() {
    }

    /** 本模组"七咒所缚"特性的注册 id ✓（与 {@code Modifiers} 里注册的一致 ✓） */
    private static final ModifierId TRAIT_ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "seven_curses_bound"));

    /** 神秘遗物·七咒之戒 ✓（与 CursedRingTooltipHandler 同口径 ✓） */
    private static final String CURSED_RING = "enigmaticlegacy:cursed_ring";

    private static final String KEY_TOTAL = "tn_cursed_bound_total";
    private static final String KEY_CURSED = "tn_cursed_bound_cursed";

    /** 合格阈值 ✓：受七咒时间 ÷ 在线时间 ≥ 99% ✓（用户口径 ✓） */
    private static final int RATIO_PERCENT = 99;

    /** 检查节流 ✓（每 20 tick ＝ 1 秒一次 ✓） */
    private static final int CHECK_INTERVAL = 20;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        try {
            CompoundTag data = player.getPersistentData();
            long total = data.getLong(KEY_TOTAL) + 1L;
            boolean cursed = carriesCursedRing(player);
            long cursedTicks = data.getLong(KEY_CURSED) + (cursed ? 1L : 0L);
            data.putLong(KEY_TOTAL, total);
            data.putLong(KEY_CURSED, cursedTicks);

            if (player.tickCount % CHECK_INTERVAL != 0) {
                return;
            }
            if (isQualified(total, cursedTicks)) {
                return;
            }
            ejectBoundItems(player);
        } catch (Throwable ignored) {
            // 任何意外都不该把玩家 tick 打崩 ✓
        }
    }

    /**
     * 是否合格 ✓：受七咒 tick 占在线 tick 的 **99% 以上** ✓（用户口径 ✓）。
     * <p>⚠ 用整数比较 ✗：{@code cursed / total >= 0.9} ⇔ {@code cursed * 100 >= total * 90} ✓。
     */
    public static boolean isQualified(long totalTicks, long cursedTicks) {
        if (totalTicks <= 0L) {
            return false;
        }
        return cursedTicks * 100L >= totalTicks * (long) RATIO_PERCENT;
    }

    /** 玩家身上（背包/护甲/副手/饰品 ✓）是否带着七咒之戒 ✓ */
    private static boolean carriesCursedRing(Player player) {
        Inventory inv = player.getInventory();
        for (ItemStack stack : inv.items) {
            if (isCursedRing(stack)) {
                return true;
            }
        }
        for (ItemStack stack : inv.armor) {
            if (isCursedRing(stack)) {
                return true;
            }
        }
        for (ItemStack stack : inv.offhand) {
            if (isCursedRing(stack)) {
                return true;
            }
        }
        try {
            var resolved = CuriosApi.getCuriosInventory(player).resolve();
            if (resolved.isPresent()) {
                for (ICurioStacksHandler handler : resolved.get().getCurios().values()) {
                    IItemHandlerModifiable stacks = handler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        if (isCursedRing(stacks.getStackInSlot(i))) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // 没装 Curios / API 变动 ⇒ 只按背包判定 ✓
        }
        return false;
    }

    private static boolean isCursedRing(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            return stack.getItem().builtInRegistryHolder().key().location().toString().equals(CURSED_RING);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 这件物品是否带"七咒所缚" ✓（破坏的工具照样算 ✓ ⇒ 用 ignoringBroken 版 ✓） */
    private static boolean hasBoundTrait(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            return ToolHelper.getModifierLevelIgnoringBroken(ToolStack.from(stack), TRAIT_ID) > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 把不合格者身上带"七咒所缚"的东西**收回背包** ✓ 背包没空位 ⇒ **丢在脚下** ✓（用户口径 ✓）。
     *
     * <p>范围 ✓：手持（当前选中的快捷栏格 ✓）、护甲槽 ✓、副手 ✓、饰品槽 ✓；
     * 目标位置 ✓：**背包的"非快捷栏"格**（第 9~35 格 ✓）—— ⚠ 刻意不放回快捷栏 ✗，
     * 否则玩家下一次轮到这个格子就得再被收一次 ✓（那会变成每 20 tick 的抖动 ✗）。
     */
    private static void ejectBoundItems(ServerPlayer player) {
        Inventory inv = player.getInventory();

        // ① 手持：当前选中的快捷栏格 ✓
        int selected = inv.selected;
        ItemStack held = inv.getItem(selected);
        if (hasBoundTrait(held)) {
            ItemStack copy = held.copy();
            inv.setItem(selected, ItemStack.EMPTY);
            stash(player, inv, copy);
        }

        // ② 护甲槽 ＋ 副手 ✓
        for (int i = 0; i < inv.armor.size(); i++) {
            ItemStack stack = inv.armor.get(i);
            if (hasBoundTrait(stack)) {
                ItemStack copy = stack.copy();
                inv.armor.set(i, ItemStack.EMPTY);
                stash(player, inv, copy);
            }
        }
        for (int i = 0; i < inv.offhand.size(); i++) {
            ItemStack stack = inv.offhand.get(i);
            if (hasBoundTrait(stack)) {
                ItemStack copy = stack.copy();
                inv.offhand.set(i, ItemStack.EMPTY);
                stash(player, inv, copy);
            }
        }

        // ③ 饰品槽 ✓
        try {
            var resolved = CuriosApi.getCuriosInventory(player).resolve();
            if (resolved.isPresent()) {
                for (ICurioStacksHandler handler : resolved.get().getCurios().values()) {
                    IItemHandlerModifiable stacks = handler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        ItemStack stack = stacks.getStackInSlot(i);
                        if (!hasBoundTrait(stack)) {
                            continue;
                        }
                        ItemStack taken = stacks.extractItem(i, stack.getCount(), false);
                        if (!taken.isEmpty()) {
                            stash(player, inv, taken);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // 同上 ✓ 拿不到饰品就只处理背包侧 ✓
        }
    }

    /** 塞进背包"非快捷栏"格 ✓ 没空位就丢在脚下 ✓（照 {@code CurseVaultInteractionHandler} 的口径 ✓） */
    private static void stash(ServerPlayer player, Inventory inv, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        for (int i = Inventory.getSelectionSize(); i < inv.items.size(); i++) {
            if (inv.items.get(i).isEmpty()) {
                inv.items.set(i, stack);
                return;
            }
        }
        // ⚠ 背包满 ⇒ 扔在地上 ✓ 绝不凭空消失 ✗（用户口径 ✓）
        player.drop(stack, false);
    }
}
