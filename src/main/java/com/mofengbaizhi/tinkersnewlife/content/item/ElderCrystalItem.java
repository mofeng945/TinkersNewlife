package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.content.energy.ElderCrystalStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.List;

/**
 * <b>古老者水晶</b>（物品形态）：以 NBT 存 {@code EE}（晶能），容量
 * {@link ElderCrystalStorage#CRYSTAL_CAPACITY} = 1000 EE。
 *
 * <ul>
 *   <li>放在<b>副手</b>或<b>饰品槽</b>（整合包的通用「饰品」槽 = curios {@code charm} / {@code curio}）时，
 *       会在你手持施法物品（或读条中）且法力不满时<b>自动供能</b>
 *       （1 EE = 1 法力 ✓ 见 {@code handler.ElderCrystalManaFeeder}）；</li>
 *   <li>身上带着<b>有电</b>的水晶会持续受"寒冷反噬"（细雪同款冻结 ✓ 见
 *       {@code handler.ElderCrystalColdHandler}）——<b>空水晶完全不冷</b> ✓；</li>
 *   <li>4 个水晶可以合并成 1 个<b>古老者水晶方块</b>（容量 4000），
 *       走自定义配方 {@code ElderCrystalMergeRecipe} ⇒ <b>四个水晶里存的 EE 求和后一点不丢</b> ✓
 *       （原版无序配方会把 NBT 抹掉 ✗ 所以不能用原版）。</li>
 * </ul>
 *
 * <p>数据读写统一走 {@link ElderCrystalStorage}（一键名 {@code EE} ✓ 与方块实体同键 ✓）。
 */
public class ElderCrystalItem extends Item implements ICurioItem {

    /** 耐久条颜色（浅蓝紫，与占位贴图同色系 ✓） */
    private static final int BAR_COLOR = 0x9B8CF0;

    public ElderCrystalItem() {
        super(new Properties().stacksTo(64));
    }

    // ========== ICurioItem：可放整合包的通用「饰品」槽 ==========

    /**
     * 与封呪瓶同一套口径（见 {@code CurseBottleItem#canEquip}）：
     * {@code charm} = 本整合包里的通用「饰品」槽（神秘遗物把 curios 的 charm 槽中文名覆盖成了"饰品"，
     * 该槽带 {@code curios:tag} 校验 ⇒ 物品必须写进 {@code data/curios/tags/items/charm.json}）；
     * {@code curio} = Curios 自带的通用槽标识，兼容其它整合包。
     *
     * <p>本模组<b>不注册</b>这些公共槽位（避免覆盖别人设好的槽位大小/图标 ✗）。
     */
    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        String id = context.identifier();
        return "charm".equals(id) || "curio".equals(id);
    }

    /** 允许"手持右键直接戴到饰品槽"（水晶没有别的右键功能 ✓ 开着更顺手 ✓） */
    @Override
    public boolean canEquipFromUse(SlotContext context, ItemStack stack) {
        return canEquip(context, stack);
    }

    // ========== 耐久条 = 已存 EE ==========

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return ElderCrystalStorage.getCrystalEe(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        double ratio = ElderCrystalStorage.getCrystalEe(stack) / (double) ElderCrystalStorage.CRYSTAL_CAPACITY;
        return (int) Math.round(Math.max(0.0, Math.min(1.0, ratio)) * 13.0);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    // ========== tooltip ==========

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                               List<Component> tooltip, TooltipFlag flag) {
        int ee = ElderCrystalStorage.getCrystalEe(stack);
        tooltip.add(Component.translatable("item.tinkersnewlife.elder_crystal.power",
                        ee, ElderCrystalStorage.CRYSTAL_CAPACITY)
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.tinkersnewlife.elder_crystal.rate")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.tinkersnewlife.elder_crystal.hint")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
