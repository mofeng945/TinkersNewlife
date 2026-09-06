package com.mofengbaizhi.tinkersnewlife.content.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.item.AncientCursedScrollItem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * 在战利品箱中加入「古代咒术残卷」（内含随机咒言词条，右击学习不消耗）。
 * 每个箱子 loot 结算时以 chance 概率添加 1 张残卷。
 */
public class AncientScrollModifier extends LootModifier {

    private final float chance;

    public static final Supplier<Codec<AncientScrollModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst ->
                    codecStart(inst).and(
                            Codec.FLOAT.fieldOf("chance").orElse(0.12f).forGetter(m -> m.chance)
                    ).apply(inst, AncientScrollModifier::new)
            )
    );

    public AncientScrollModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = chance;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        // 只对"无实体来源"的 loot 生效：箱子/容器生成时没有 THIS_ENTITY 参数，
        // 怪物击杀掉落带 THIS_ENTITY → 跳过（避免打怪也掉残卷）
        if (context.getParamOrNull(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY) != null) {
            return generatedLoot;
        }
        if (context.getRandom().nextFloat() < chance) {
            generatedLoot.add(AncientCursedScrollItem.roll());
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
