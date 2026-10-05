package com.mofengbaizhi.tinkersnewlife.integration.slashblade;

// 移植自 TiCEX (MIT): moffy.ticex.caps.EmbossmentMaterialCapability

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

/**
 * <b>"装裱"把某个工具部件的材料特性贴到工具上</b>（逐字移植 TiCEX {@code EmbossmentMaterialCapability} ✓ MIT）。
 *
 * <p>它是一份挂在<b>工具物品</b>上的能力 ✓（{@link net.minecraftforge.common.capabilities.ICapabilityProvider} 那一套 ✓）：
 * <ul>
 *   <li>{@link #accept(ItemStack, ItemStack, ToolPartItem)} ✓：记住"这块部件是什么材料/什么材料统计类型" ✓，
 *       把该材料在该统计类型下的全部<b>特性（trait）</b>加到工具上 ✓，并写进工具的持久化数据 ✓；</li>
 *   <li>{@link #remove(ItemStack)} ✓：把上一次装裱上去的特性摘掉 ✓（重复装裱会先摘再贴 ✓）；</li>
 *   <li>{@link #serializeNBT()}／{@link #deserializeNBT(CompoundTag)} ✓：状态就存在工具持久化数据的
 *       {@code tinkersnewlife:embossed_material} 这个 key 下 ✓（= TiCEX 的 {@code ticex:embossed_material} ✓）。</li>
 * </ul>
 *
 * <p>⚠ <b>挂接点</b>：TiCEX 是在它自家的 {@code TiCEXToolCapabilityProvider} 里转发这个能力的 ✓ ——
 * 本仓对应物是 {@link SBItemCapabilityProvider} ✓（见那里的注释 ✓）。
 *
 * <p>⚠ 与 TiCEX 的差异（如实记录 ✓）：TiCEX 的 {@code TiCEXToolCapabilityProvider} 把这份能力给**所有**匠魂工具 ✗；
 * 本仓把它**只给我们的拔刀剑**（{@code KatanaItem}）✓ —— 因为本仓的 {@code SBItemCapabilityProvider} 自带
 * "只认拔刀剑"的闸门 ✓（见 §1032 备忘录 ✓）。
 */
public class EmbossmentMaterialCapability {

    /** 工具上的"装裱材料"能力（照 TiCEX 用 {@code CapabilityManager.get} ✓） */
    public static final Capability<EmbossmentMaterialCapability> EMBOSSMENT_MATERIAL_CAPABILITY = CapabilityManager.get(
            new CapabilityToken<EmbossmentMaterialCapability>() {}
    );

    /** 状态在工具持久化数据里的 key（TiCEX 是 {@code ticex:embossed_material} ✓） */
    public static final ResourceLocation EMBOSSED_MATERIAL = TinkersNewlife.prefix("embossed_material");

    protected MaterialId embossedMaterialId;
    protected MaterialStatsId embossedMaterialStatType;
    protected final IToolStackView tool;

    public EmbossmentMaterialCapability(IToolStackView tool) {
        this.tool = tool;
        deserializeNBT(tool.getPersistentData().getCompound(EMBOSSED_MATERIAL));
    }

    /** 把 {@code partStack}（某个工具部件）的材料特性装裱到 {@code toolStack} 上 */
    public void accept(ItemStack toolStack, ItemStack partStack, ToolPartItem part) {
        MaterialId materialId = part.getMaterial(partStack).getId();
        ToolStack tool = ToolStack.from(toolStack);

        remove(toolStack);
        embossedMaterialId = materialId;
        embossedMaterialStatType = part.getStatType();

        for (ModifierEntry modifierEntry : MaterialRegistry.getInstance()
                .getTraits(materialId.getId(), part.getStatType())) {
            tool.addModifier(modifierEntry.getId(), modifierEntry.getLevel());
        }

        tool.getPersistentData().put(EMBOSSED_MATERIAL, serializeNBT());
    }

    /** 摘掉上一次装裱上去的那些特性 */
    public void remove(ItemStack toolStack) {
        ToolStack tool = ToolStack.from(toolStack);

        if (embossedMaterialId == null) return;

        for (ModifierEntry modifierEntry : MaterialRegistry.getInstance()
                .getTraits(embossedMaterialId, embossedMaterialStatType)) {
            tool.removeModifier(modifierEntry.getId(), modifierEntry.getLevel());
        }
    }

    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        CompoundTag materialTag = new CompoundTag();
        if (embossedMaterialId != null) {
            materialTag.putString("stat", embossedMaterialStatType.toString());
            materialTag.putString("id", embossedMaterialId.toString());
            nbt.put("material", materialTag);
        }
        return nbt;
    }

    public void deserializeNBT(CompoundTag nbt) {
        CompoundTag embossedMaterial = nbt.getCompound("material");
        embossedMaterialId = MaterialId.tryParse(embossedMaterial.getString("id"));
        embossedMaterialStatType = MaterialStatsId.tryParse(embossedMaterial.getString("stat"));
    }
}
