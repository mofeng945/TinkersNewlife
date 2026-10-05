package com.mofengbaizhi.tinkersnewlife.mixin;

// 移植自 TiCEX (MIT): moffy.ticex.mixin.slashblade.SlashBladeTEISRMixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mofengbaizhi.tinkersnewlife.client.slashblade.context.ContextFrame;
import com.mofengbaizhi.tinkersnewlife.client.slashblade.context.ItemRenderContext;
import com.mofengbaizhi.tinkersnewlife.client.slashblade.context.KatanaContexts;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mojang.blaze3d.vertex.PoseStack;
import mods.flammpfeil.slashblade.client.renderer.SlashBladeTEISR;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 拔刀剑本体渲染器（{@code SlashBladeTEISR}）× 匠魂 —— 移植 TiCEX {@code SlashBladeTEISRMixin}（MIT）。
 *
 * <p>三件事 ✓：
 * <ol>
 *   <li>{@code renderModel} 里"模型 / 贴图"两个局部变量 ⇒ 挂了「拵」特性时改用**刀自己 bladeState 里记的**
 *       {@code ModelName}／{@code TextureName} ✓；</li>
 *   <li>{@code renderIcon} 里第 2、3 处 {@code BladeRenderState#renderOverrided} 调用 ⇒ 包一层渲染上下文 ✓
 *       （告诉 {@code TicEXSBRenderers}"现在画的是哪把刀" ✓）。</li>
 * </ol>
 *
 * <p>⚠ <b>已核对字节码</b>（`javap -p -c` 反查 `SlashBladeResharped-1.20.1-1.9.65` ✓）：
 * {@code renderModel} 里恰好两次 {@code astore} 存 {@code ResourceLocation}（局部 8=模型 / 10=贴图 ✓ ⇒
 * {@code ordinal=0/1} 命中 ✓）；{@code renderIcon(...)Z} 里恰好三次
 * {@code BladeRenderState.renderOverrided(...)}（偏移 160/352/401 ✓ ⇒ {@code ordinal=1/2} 命中 ✓）；
 * {@code stackDefaultModel(ItemStack)} 存在 ✓。
 */
@Mixin(value = SlashBladeTEISR.class, remap = false)
public abstract class KatanaSlashBladeTEISRMixin {

    @Shadow
    public abstract ResourceLocation stackDefaultModel(ItemStack stack);

    @ModifyVariable(method = "renderModel", at = @At(value = "STORE"), ordinal = 0)
    public ResourceLocation modifyModel(ResourceLocation modelLocation,
                                        @Local(argsOnly = true) ItemStack stack) {
        if (!(stack.getItem() instanceof IModifiable)) return modelLocation;
        ToolStack tool = ToolStack.from(stack);

        if (Modifiers.KOSHIRAE != null && tool.getModifierLevel(Modifiers.KOSHIRAE.getId()) > 0) {
            CompoundTag persistentTag = stack.getOrCreateTag().getCompound("bladeState");
            if (persistentTag.contains("ModelName")) {
                return ResourceLocation.tryParse(persistentTag.getString("ModelName"));
            }
        }
        return modelLocation;
    }

    @ModifyVariable(method = "renderModel", at = @At(value = "STORE"), ordinal = 1)
    public ResourceLocation modifyTexture(ResourceLocation textureLocation,
                                          @Local(argsOnly = true) ItemStack stack) {
        if (!(stack.getItem() instanceof IModifiable)) return textureLocation;
        ToolStack tool = ToolStack.from(stack);

        if (Modifiers.KOSHIRAE != null && tool.getModifierLevel(Modifiers.KOSHIRAE.getId()) > 0) {
            CompoundTag persistentTag = stack.getOrCreateTag().getCompound("bladeState");
            if (persistentTag.contains("ModelName")) {
                return ResourceLocation.tryParse(persistentTag.getString("TextureName"));
            } else {
                return stack
                        .getCapability(ItemSlashBlade.BLADESTATE)
                        .filter(s -> s.getTexture().isPresent())
                        .map(s -> s.getTexture().get())
                        .orElseGet(() -> stackDefaultModel(stack));
            }
        }
        return textureLocation;
    }

    @WrapOperation(method = "renderIcon(Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IFZ)V",
            at = {
                    @At(value = "INVOKE", target = "Lmods/flammpfeil/slashblade/client/renderer/util/BladeRenderState;renderOverrided(Lnet/minecraft/world/item/ItemStack;Lmods/flammpfeil/slashblade/client/renderer/model/obj/WavefrontObject;Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", ordinal = 1),
                    @At(value = "INVOKE", target = "Lmods/flammpfeil/slashblade/client/renderer/util/BladeRenderState;renderOverrided(Lnet/minecraft/world/item/ItemStack;Lmods/flammpfeil/slashblade/client/renderer/model/obj/WavefrontObject;Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", ordinal = 2)
            })
    public void renderIcon(ItemStack stack, WavefrontObject model, String target, ResourceLocation texture,
                           PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn, Operation<Void> original) {
        try (ContextFrame<ItemRenderContext> frame = KatanaContexts.SB_RENDERING_CONTEXT.open(null)) {
            original.call(stack, model, target, texture, matrixStackIn, bufferIn, packedLightIn);
        }
    }
}
