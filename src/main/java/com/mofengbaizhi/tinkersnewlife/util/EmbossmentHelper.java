package com.mofengbaizhi.tinkersnewlife.util;

// 移植自 TiCEX (MIT): moffy.ticex.lib.utils.TicEXUtils#applyCatalystEmbossment

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * <b>"催化剂（catalyst）装裱"</b> —— 逐字移植 TiCEX {@code TicEXUtils#applyCatalystEmbossment}（MIT）。
 *
 * <p>它处理的是**被装裱物品里那份 {@code embossed} NBT** ✓ ——
 * 那份 NBT 由 {@link com.mofengbaizhi.tinkersnewlife.content.recipe.EmbossmentCastingRecipe}
 * 在浇铸台把整把刀"烧进"部件时写进去 ✓（{@code assembled.tag.embossed = 被浇铸那把刀的完整存档} ✓）。
 *
 * <p>本方法把那份"被牺牲的刀"的 NBT <b>并进合成结果</b> ✓ ——
 * 只补结果上没有的 key ✓（已有的 key 一律保留结果自己的值 ✓ ⇒ {@code ModifierKoshirae} 先写进去的
 * {@code bladeState} 不会被覆盖 ✓）。
 *
 * <p>⚠ 与 TiCEX 的差异（如实记录 ✓，见备忘录 §1032）：
 * <ul>
 *   <li>TiCEX 在 {@code copyAttribute=true} 时还会合并 <b>Curios 饰品属性</b> ✗ ——
 *       本仓没有那条 Curios 属性桥 ⇒ 未搬 ✓（只有 {@code embossment_building} 走 {@code true} ✓，
 *       而本仓的建刀配方目前不用它 ✓）；</li>
 *   <li>TiCEX 末尾发现"含催化剂"时会补一个它自家的 {@code REBIRTH_MODIFIER} ✗ ——
 *       本仓没有那个修饰符 ⇒ 未搬 ✓（与本次三个拔刀剑特性无关 ✓）。</li>
 * </ul>
 */
public final class EmbossmentHelper {

    private EmbossmentHelper() {
    }

    /**
     * 把容器里所有带 {@code embossed} 标签的输入的存档 NBT 并进 {@code toolItemStack}。
     *
     * @param toolItemStack 合成结果（工具）
     * @param inv           修补台容器
     * @param copyAttribute 是否顺带合并装备属性（只有"建刀"流程为 true ✓）
     * @return 重新包一层的工具栈（照 TiCEX ✓）
     */
    public static ItemStack applyCatalystEmbossment(ItemStack toolItemStack, ITinkerStationContainer inv,
                                                    boolean copyAttribute) {
        for (int i = 0; i < inv.getInputCount(); i++) {
            ItemStack input = inv.getInput(i);
            if (input.getOrCreateTag().contains("embossed")) {
                CompoundTag embossedTag = Objects.requireNonNull(input.getTag()).getCompound("embossed");
                CompoundTag tagTmp = embossedTag.copy();
                if (embossedTag.contains("id")) {
                    CompoundTag stackTag = embossedTag.copy();
                    stackTag.put("tag", new CompoundTag());
                    if (copyAttribute) {
                        ItemStack embossedStack = ItemStack.of(stackTag);
                        for (EquipmentSlot slot : EquipmentSlot.values()) {
                            Multimap<Attribute, AttributeModifier> attributeModifierMultimap =
                                    toolItemStack.getAttributeModifiers(slot);
                            Multimap<Attribute, AttributeModifier> tmp = ArrayListMultimap.create();
                            embossedStack.getAttributeModifiers(slot).asMap().forEach((attribute, attributeModifier) -> {
                                if (!attributeModifierMultimap.containsKey(attribute)) {
                                    tmp.putAll(attribute, attributeModifier);
                                }
                            });
                            tmp.forEach((attribute, attributeModifier) ->
                                    toolItemStack.addAttributeModifier(attribute, attributeModifier, slot));
                        }
                    }

                    tagTmp = embossedTag.getCompound("tag").copy();
                }

                CompoundTag compoundTag = toolItemStack.getOrCreateTag();
                for (String key : tagTmp.getAllKeys()) {
                    Tag merged;
                    if (compoundTag.contains(key)) {
                        merged = compoundTag.get(key);
                    } else {
                        merged = tagTmp.get(key).copy();
                    }
                    compoundTag.put(key, merged);
                }
            }
        }

        // 照 TiCEX：无论有没有催化剂，最后都走一遍 ToolStack 往返 ✓（结果与传入的栈等价 ✓）
        return ToolStack.from(toolItemStack).createStack();
    }
}
