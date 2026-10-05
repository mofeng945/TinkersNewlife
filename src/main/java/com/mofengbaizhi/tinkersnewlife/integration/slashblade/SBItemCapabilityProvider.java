package com.mofengbaizhi.tinkersnewlife.integration.slashblade;

import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import slimeknights.tconstruct.library.tools.capability.ToolCapabilityProvider;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import java.util.function.Supplier;

/**
 * <b>给匠魂工具挂"拔刀剑刀状态"的能力提供者</b>（§1007 · 照 TiCEX 的做法 ✓）。
 *
 * <p>注册方式（见 {@link SlashBladeIntegration#register} ✓）：
 * <pre>
 *   ToolCapabilityProvider.register((stack, tool) -&gt; new SBItemCapabilityProvider(stack, tool));
 * </pre>
 * ⇒ 匠魂自己的 {@code ToolCapabilityProvider}（我们的 {@code KatanaItem} 经移植后正返回它 ✓）
 * 会把它转发出去 ✓；而 {@code ItemSlashBlade} 那边取 {@code BLADESTATE} 时就能拿到 ✓。
 *
 * <p>⚠ 只对**我们的拔刀剑**（{@code KatanaItem}）给状态 ✓ —— 否则会给**所有**匠魂工具都挂上刀状态 ✗，
 * 那会让拔刀剑把别的工具也当成刀 ✗（副作用不可控 ✓）。
 *
 * <p>§1032：<b>顺带转发"装裱材料"能力</b> ✓（{@link EmbossmentMaterialCapability} ✓，照 TiCEX 的
 * {@code TiCEXToolCapabilityProvider} 同一个转发位置 ✓）—— 它是「装裱」修饰符
 * （{@code ModifierEmbossment}）能用起来的前提 ✓；同样只给拔刀剑 ✓。
 */
public class SBItemCapabilityProvider implements ToolCapabilityProvider.IToolCapabilityProvider {

    private final ItemStack stack;
    private final Supplier<? extends IToolStackView> tool;
    /** 懒建 + 可清理（匠魂会在工具数据变化时调 {@link #clearCache()} ✓） */
    private ToolBladeStateCapability bladeState;
    /** §1032 装裱材料能力（同样懒建 ✓ 见 {@link EmbossmentMaterialCapability} ✓） */
    private EmbossmentMaterialCapability embossmentMaterial;

    public SBItemCapabilityProvider(ItemStack stack, Supplier<? extends IToolStackView> tool) {
        this.stack = stack;
        this.tool = tool;
    }

    @Override
    public <T> LazyOptional<T> getCapability(IToolStackView toolView, Capability<T> capability) {
        // 只认我们的拔刀剑 ✓
        if (!(stack.getItem() instanceof com.mofengbaizhi.tinkersnewlife.content.item.KatanaItem)) {
            return LazyOptional.empty();
        }
        if (capability == CapabilitySlashBlade.BLADESTATE) {
            if (bladeState == null) {
                bladeState = new ToolBladeStateCapability(stack, toolView);
            }
            return LazyOptional.of(() -> bladeState).cast();
        }
        // §1032 装裱材料能力（TiCEX 的 TiCEXToolCapabilityProvider 也在这里转发 ✓）
        if (capability == EmbossmentMaterialCapability.EMBOSSMENT_MATERIAL_CAPABILITY) {
            if (embossmentMaterial == null) {
                embossmentMaterial = new EmbossmentMaterialCapability(toolView);
            }
            return LazyOptional.of(() -> embossmentMaterial).cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public void clearCache() {
        bladeState = null;
        embossmentMaterial = null;
    }
}
