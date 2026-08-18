package com.mofengbaizhi.tinkersnewlife.content.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;

public class AddItemsModifier extends LootModifier {

    private final List<Item> items;
    private final int minCount;
    private final int maxCount;
    private final float chance;

    public static final Supplier<Codec<AddItemsModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst ->
                    codecStart(inst).and(
                            inst.group(
                                    ForgeRegistries.ITEMS.getCodec().listOf().fieldOf("items").forGetter(m -> m.items),
                                    Codec.INT.fieldOf("min_count").orElse(1).forGetter(m -> m.minCount),
                                    Codec.INT.fieldOf("max_count").orElse(1).forGetter(m -> m.maxCount),
                                    Codec.FLOAT.fieldOf("chance").orElse(0.3f).forGetter(m -> m.chance)
                            )
                    ).apply(inst, AddItemsModifier::new)
            )
    );

    public AddItemsModifier(LootItemCondition[] conditions, List<Item> items, int minCount, int maxCount, float chance) {
        super(conditions);
        this.items = items;
        this.minCount = minCount;
        this.maxCount = maxCount;
        this.chance = chance;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!items.isEmpty() && context.getRandom().nextFloat() < chance) {
            Item selectedItem = items.get(context.getRandom().nextInt(items.size()));
            int count = context.getRandom().nextInt(maxCount - minCount + 1) + minCount;
            generatedLoot.add(new ItemStack(selectedItem, count));
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}