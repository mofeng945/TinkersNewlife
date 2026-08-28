package com.mofengbaizhi.tinkersnewlife.content.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;

import java.util.Map;
import java.util.function.Supplier;

import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;

/**
 * 咒力亲和战利品注入
 * <p>
 * 任意模组的饰品（curios 物品）在战利品生成时（非制作）都有概率随机携带
 * 0~50 点的咒力亲和（NBT: {@code tinkersnewlife:curse_affinity}）。
 * 玩家佩戴这些饰品时，亲和会累加到玩家咒力亲和（初始 0）上。
 */
public class CurseAffinityLootModifier extends LootModifier {

    private final float chance;

    public static final Supplier<Codec<CurseAffinityLootModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst ->
                    codecStart(inst).and(
                            Codec.FLOAT.fieldOf("chance").orElse(0.4f).forGetter(m -> m.chance)
                    ).apply(inst, CurseAffinityLootModifier::new)
            )
    );

    public CurseAffinityLootModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = chance;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        for (ItemStack stack : generatedLoot) {
            if (stack.isEmpty() || !isCurioItem(stack, context)) continue;
            if (context.getRandom().nextFloat() >= chance) continue;
            int affinity = context.getRandom().nextInt(51); // 0~50
            if (affinity > 0) {
                CursePowerHelper.setCurseAffinity(stack, affinity);
            }
        }
        return generatedLoot;
    }

    /**
     * 判定是否为饰品：只要能佩戴进 curios 饰品栏（curios 槽位）就算饰品。
     * 覆盖三类途径：
     * 1. 物品具有 curios 物品能力（ICurioItem 或通过 CuriosApi.registerCurio 注册）
     * 2. 能放入任意已注册的 curios 槽位（标签/槽位校验器判定，如 curios:ring 标签、自定义校验器）
     * 3. 兜底：带有任意 curios 命名空间物品标签
     */
    private static boolean isCurioItem(ItemStack stack, LootContext context) {
        if (CuriosApi.getCurio(stack).isPresent()) return true;
        Map<String, ISlotType> slots = CuriosApi.getItemStackSlots(stack, context.getLevel());
        if (!slots.isEmpty()) return true;
        for (TagKey<Item> tag : stack.getTags().toList()) {
            if (tag.location().getNamespace().equals("curios")) return true;
        }
        return false;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
