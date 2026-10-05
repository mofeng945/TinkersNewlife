package com.mofengbaizhi.tinkersnewlife.integration.slashblade;

// 移植自 TiCEX (MIT): moffy.ticex.lib.utils.TicEXSBUtils

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * TiCEX {@code TicEXSBUtils} 的必要部分 —— 逐字移植（MIT）。
 *
 * <p>只搬了三个"禁附魔清单 ＋ 安全写附魔"用得上成员 ✓：
 * <ul>
 *   <li>{@link #disallowedEnchantments} ✓（这些附魔<b>不允许</b>被贴到拔刀剑上：耐久/经验修补/锋利/节肢/亡灵/火焰附加/击退/抢夺 ✓）；</li>
 *   <li>{@link #calcEnchLevel}／{@link #applyEnchantment} ✓（把附魔**合并**进物品 NBT：同级则 +1，上限封顶 ✓
 *       并且走"手写 Enchantments 列表"这条路，绕开原版 {@code ItemStack#enchant} 的附魔台限制 ✓）。</li>
 * </ul>
 * <p>⚠ 原文里还有一个 {@code defaultTransform}（渲染用单位矩阵 ✗）—— 与本功能无关 ⇒ 没搬 ✓。
 */
public class KatanaSBUtils {

    /** 禁止贴到拔刀剑上的附魔（与 TiCEX 完全同表 ✓） */
    public static Set<Enchantment> disallowedEnchantments = new HashSet<>();

    static {
        disallowedEnchantments.add(Enchantments.UNBREAKING);
        disallowedEnchantments.add(Enchantments.MENDING);
        disallowedEnchantments.add(Enchantments.SHARPNESS);
        disallowedEnchantments.add(Enchantments.BANE_OF_ARTHROPODS);
        disallowedEnchantments.add(Enchantments.SMITE);
        disallowedEnchantments.add(Enchantments.FIRE_ASPECT);
        disallowedEnchantments.add(Enchantments.KNOCKBACK);
        disallowedEnchantments.add(Enchantments.MOB_LOOTING);
    }

    /** 目标等级：与当前同级则 +1（不超过该附魔上限 ✓），否则取 max(当前, 目标) 并封顶 ✓ */
    public static int calcEnchLevel(ItemStack stack, Enchantment key, int value) {
        int currentLv = stack.getEnchantmentLevel(key);
        int levelCap = key.getMaxLevel();
        if (value == currentLv) {
            return Math.min(value + 1, levelCap);
        }
        return Math.min(Math.max(value, currentLv), levelCap);
    }

    /**
     * 把附魔写进物品 —— 逐字照抄 TiCEX ✓（包括它那个"遍历禁表、命中就整段跳过"的写法 ✓）。
     *
     * @return 是否真的写进去了（命中了禁表 ⇒ false ✓）
     */
    public static boolean applyEnchantment(ItemStack toolStack, Enchantment enchantment, int level) {
        for (Enchantment disallowed : disallowedEnchantments) {
            if (!enchantment.getDescriptionId().equals(disallowed.getDescriptionId())) {
                if (toolStack.getEnchantmentLevel(enchantment) > 0) {
                    CompoundTag nbt = toolStack.getOrCreateTag();
                    if (!nbt.contains("Enchantments", Tag.TAG_LIST)) {
                        nbt.put("Enchantments", new ListTag());
                    }

                    ListTag listTag = nbt.getList("Enchantments", Tag.TAG_COMPOUND);
                    ListTag newListTag = new ListTag();
                    for (int i = 0; i < listTag.size(); i++) {
                        CompoundTag enchantmentTag = listTag.getCompound(i);
                        if (
                                enchantmentTag
                                        .getString("id")
                                        .equals(Objects.requireNonNull(ForgeRegistries.ENCHANTMENTS.getKey(enchantment)).toString())
                        ) {
                            newListTag.add(
                                    EnchantmentHelper.storeEnchantment(
                                            ResourceLocation.tryParse(enchantmentTag.getString("id")),
                                            calcEnchLevel(toolStack, enchantment, level)
                                    )
                            );
                        } else {
                            newListTag.add(enchantmentTag);
                        }
                    }
                    nbt.put("Enchantments", newListTag);
                } else {
                    toolStack.enchant(enchantment, Math.min(enchantment.getMaxLevel(), level));
                }
                return true;
            }
        }
        return false;
    }
}
