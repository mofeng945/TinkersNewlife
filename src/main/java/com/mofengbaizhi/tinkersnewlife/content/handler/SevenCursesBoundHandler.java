package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import com.mofengbaizhi.tinkersnewlife.integration.enigmaticlegacy.EnigmaticPlaytimeBridge;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
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
 * <h2>⚠⚠ 判定数据已改为**神秘遗物原生**（用户口径 ✓「尽量用原版」✓）</h2>
 * 先前我自己用 {@code getPersistentData()} 记"在线 tick / 受七咒 tick" ✗ —— ⚠ 那是**第二套账** ✗
 * （和本体各算一套 ✓ 可能对不上 ✓；而且我按"**背包里带着**戒指"算 ✗，本体按"**佩戴**"算 ✗ ⇒ 口径也不一致 ✗）。
 * 现改为读**原版统计**里神秘遗物同步过来的那两个数 ✓（**反编译实证** ✓ 它自己就用 `Stats.CUSTOM` 写这两个统计 ✓）：
 * <pre>
 * enigmaticlegacy:play_time_with_seven_curses      // 戴着七咒之戒的时长
 * enigmaticlegacy:play_time_without_seven_curses   // 没戴的时长
 * </pre>
 * ⇒ 占比 ＝ {@code with / (with + without)} ✓ 与"受七咒时间 ÷ 在世界上时间"逐字对应 ✓
 * （本体还把这两个数同步进**原版统计** ✓ 见 {@code PlayerPlaytimeCounter} ✓）。
 *
 * <p>⚠ 跨模组类型一律经 {@code integration/enigmaticlegacy/EnigmaticPlaytimeBridge} 访问 ✓
 * （仓库铁律：只有 {@code integration/<modid>/} 才能 import 别模组类型 ✓）
 * ⇒ 本类只调桥 ✓ 且**先判 {@code isLoaded}** ✓ ⇒ 没装神秘遗物时桥类根本不会被加载 ✓ 不会炸 ✗。
 *
 * <p>⚠ <b>取不到数据 ⇒ 放行（不管）</b> ✗ —— 宁可漏管 ✓ 也**绝不**因为读不到就误收玩家装备 ✗。
 *
 * <p>⚠ 每 {@link #CHECK_INTERVAL} tick 才检查一次 ✓（每 tick 扫背包太浪费 ✗）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SevenCursesBoundHandler {

    private SevenCursesBoundHandler() {
    }

    /** 本模组"七咒所缚"特性的注册 id ✓（与 {@code Modifiers} 里一致 ✓） */
    private static final ModifierId TRAIT_ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "seven_curses_bound"));

    /** 合格阈值 ✓：受七咒时间 ÷ 在世界上时间 ≥ **99%** ✓（用户口径 ✓；
     * ⚠ 神秘遗物自己那个 {@code worthyOnesOnly} 用的是 **99.5%** ✓ 我们按用户给的 99 ✓） */
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
        // ⚠ 没装神秘遗物 ⇒ 无"七咒"可言 ⇒ 不管 ✓（该特性本就以它为前提 ✓）
        if (!IntegrationLoader.isLoaded(IntegrationLoader.ENIGMATIC_LEGACY)) {
            return;
        }
        if (player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        try {
            if (EnigmaticPlaytimeBridge.meetsRatio(player, RATIO_PERCENT)) {
                return;
            }
            ejectBoundItems(player);
        } catch (Throwable ignored) {
            // 任何意外都不该把玩家 tick 打崩 ✓
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
            // 拿不到饰品就只处理背包侧 ✓
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
