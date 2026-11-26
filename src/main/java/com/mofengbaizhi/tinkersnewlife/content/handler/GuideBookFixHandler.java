package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「新生神秘学编年史」的**裸书自救**（用户口径 ✓ 备忘录 §473）。
 *
 * <p>帕秋莉的书物品（{@code tinkersnewlife:guide_book} = {@code ItemModBook}）靠 **NBT 键 {@code patchouli:book}**
 * 指向具体书 ✓ 只给裸物品会显示「Book ID: null! / 无效的书：没有定义ID」✗。
 *
 * <p>这个 handler 保证：**任何时候**玩家拿一本没写书 id 的编年史**右键**，就先把书 id 补上 ✓ 再让原版/帕秋莉正常打开 ✓
 * —— 于是**已经拿到手里的那本旧书不用丢** ✓ 也不用清"只送一次"的标记 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GuideBookFixHandler {

    private GuideBookFixHandler() {}

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickItem event) {
        try {
            ItemStack stack = event.getItemStack();
            if (stack.isEmpty() || stack.getItem() != ModItems.GUIDE_BOOK.get()) return;
            var tag = stack.getTag();
            if (tag != null && tag.contains("patchouli:book")) return;
            stack.getOrCreateTag().putString("patchouli:book", TinkersNewlife.MOD_ID + ":guide");
        } catch (Throwable ignored) {
        }
    }
}
