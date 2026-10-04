package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.content.item.CompendiumItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import vazkii.patchouli.api.PatchouliAPI;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>帕秋莉的百宝书 · 查阅界面</b>（§910 用户口径：「查阅已吞噬书籍的所有内容，**按书归类**」）——
 * 复用本仓既有的 {@link AbstractRowListScreen}（一行一本、超出滚动、滚轮 + 侧边滑块都是现成的 ✓）。
 *
 * <ul>
 *   <li>每行：那本书的**图标**（帕秋莉 {@code getBookStack} 现取 ✓ 拿不到就用原版书兜底 ✓）
 *       ＋ **书名**（吞噬时抓下来的显示名 ✓ 不依赖帕秋莉数据也能显示 ✓）＋ 灰色小字的**书 id** ✓；</li>
 *   <li>点一行 ⇒ 关掉本界面、直接打开**那本帕秋莉书** ✓（{@code PatchouliAPI.openBookGUI} ✓
 *       ⇒ "查阅这本书的全部内容"就交给帕秋莉自己的界面 ✓ 不重复造轮子 ✗）；</li>
 *   <li>一本都没有时显示提示 ✓（文案 {@code screen.tinkersnewlife.compendium.empty} ✓）。</li>
 * </ul>
 *
 * <p>⚠ 数据直接读**手上那叠的 NBT** ✓（客户端可见 ✓ ⇒ 不需要任何网络往返 ✓）。
 */
public class CompendiumScreen extends AbstractRowListScreen<CompoundTag> {

    public CompendiumScreen(ItemStack compendium) {
        super(Component.translatable("screen.tinkersnewlife.compendium.title"),
                toRows(compendium), 220, 26, 22, 58, 34);
    }

    /** NBT 里的每条记录 ⇒ 一行 ✓ */
    private static List<CompoundTag> toRows(ItemStack compendium) {
        ListTag list = CompendiumItem.absorbedList(compendium);
        List<CompoundTag> rows = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            rows.add(list.getCompound(i));
        }
        return rows;
    }

    @Override
    protected void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.tinkersnewlife.compendium.hint", this.rows.size()),
                this.width / 2, 36, 0x9E9E9E);
    }

    @Override
    protected void drawEmptyMessage(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.tinkersnewlife.compendium.empty"),
                this.width / 2, (this.listTop + this.listBottom) / 2, 0xAAAAAA);
    }

    @Override
    protected void drawRow(GuiGraphics graphics, CompoundTag row, int index, int x, int y, int w, int h,
                           boolean hover, double mouseX, double mouseY) {
        graphics.fill(x, y, x + w, y + h, hover ? 0x66FFFFFF : 0x33000000);
        // 图标优先用**记录里的物品 id**（§910q：效果型条目没有帕秋莉书 id ⇒ 只能靠它 ✓）
        ItemStack icon = itemIcon(row.getString(CompendiumItem.KEY_ITEM));
        if (icon.isEmpty()) icon = bookIcon(row.getString(CompendiumItem.KEY_ID));
        graphics.renderItem(icon.isEmpty() ? new ItemStack(Items.BOOK) : icon, x + 3, y + 3);
        graphics.drawString(this.font, row.getString(CompendiumItem.KEY_NAME), x + 24, y + 4, 0xFFFFFF);
        graphics.drawString(this.font, row.getString(CompendiumItem.KEY_ID), x + 24, y + 14, 0x8A8A8A);
    }

    /** 按**物品 id**取图标（§910q ✓） */
    private static ItemStack itemIcon(String itemId) {
        try {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null) return ItemStack.EMPTY;
            net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /** 那本书的物品图标（帕秋莉按书 id 现给 ✓ 给不出就空栈 ⇒ 调用处用原版书兜底 ✓） */
    private static ItemStack bookIcon(String bookId) {
        try {
            ResourceLocation id = ResourceLocation.tryParse(bookId);
            return id == null ? ItemStack.EMPTY : PatchouliAPI.get().getBookStack(id);
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    protected void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.tinkersnewlife.compendium.footer"),
                this.width / 2, this.height - 24, 0x808080);
    }

    @Override
    protected void onRowClick(int index, CompoundTag row) {
        String rawId = row.getString(CompendiumItem.KEY_ID);
        // §910q 效果型知识条目（没有帕秋莉界面 ✓）⇒ 提示用"潜行右键唤醒" ✓
        if (rawId.startsWith("effect:")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.translatable("message.tinkersnewlife.compendium.effect_entry"), true);
            }
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        Minecraft.getInstance().setScreen(null);        // 先关掉自己 ✓ 免得两界面叠着 ✗
        if (id != null) {
            PatchouliAPI.get().openBookGUI(id);         // 直接打开那本帕秋莉书 ✓
        }
    }
}
