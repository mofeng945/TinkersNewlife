package com.mofengbaizhi.tinkersnewlife.content.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class RandomGloveModifier extends LootModifier {

    private final int minTier;
    private final int minCount;
    private final int maxCount;
    private final float chance;

    private static List<IMaterial> cachedMaterials = null;
    private static int cachedMinTier = -1;

    public static final Supplier<Codec<RandomGloveModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst ->
                    codecStart(inst).and(
                            inst.group(
                                    Codec.INT.fieldOf("min_tier").orElse(0).forGetter(m -> m.minTier),
                                    Codec.INT.fieldOf("min_count").orElse(1).forGetter(m -> m.minCount),
                                    Codec.INT.fieldOf("max_count").orElse(1).forGetter(m -> m.maxCount),
                                    Codec.FLOAT.fieldOf("chance").orElse(0.3f).forGetter(m -> m.chance)
                            )
                    ).apply(inst, RandomGloveModifier::new)
            )
    );

    public RandomGloveModifier(LootItemCondition[] conditions, int minTier, int minCount, int maxCount, float chance) {
        super(conditions);
        this.minTier = minTier;
        this.minCount = minCount;
        this.maxCount = maxCount;
        this.chance = chance;
    }

    private List<IMaterial> getAvailableMaterials() {
        if (cachedMaterials == null || cachedMinTier != this.minTier) {
            cachedMaterials = new ArrayList<>();
            for (IMaterial material : MaterialRegistry.getInstance().getAllMaterials()) {
                if (material == null || material == IMaterial.UNKNOWN) continue;
                if (material.getTier() >= this.minTier) {
                    cachedMaterials.add(material);
                }
            }
            cachedMinTier = this.minTier;
        }
        return cachedMaterials;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        List<IMaterial> available = getAvailableMaterials();
        if (available.isEmpty() || context.getRandom().nextFloat() >= chance) {
            return generatedLoot;
        }

        int count = context.getRandom().nextInt(maxCount - minCount + 1) + minCount;

        // ✅ 两个部件独立随机选择材质
        IMaterial coreMaterial = available.get(context.getRandom().nextInt(available.size()));
        IMaterial bindingMaterial = available.get(context.getRandom().nextInt(available.size()));

        // 构建手套
        ItemStack baseStack = new ItemStack(ModItems.SILENT_GLOVE.get());
        ToolStack tool = ToolStack.from(baseStack);
        if (tool == null) {
            return generatedLoot;
        }

        // 核心部件用 coreMaterial，皮面部件用 bindingMaterial
        MaterialNBT materialNBT = MaterialNBT.of(coreMaterial, bindingMaterial);
        tool.setMaterials(materialNBT);
        tool.rebuildStats();
        ItemStack result = tool.createStack();

        for (int i = 0; i < count; i++) {
            generatedLoot.add(result.copy());
        }

        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}