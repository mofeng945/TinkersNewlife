package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.item.ConstructedBlueprintItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * 拟造蓝本（代理物品）的<b>模型代理</b>：把一个蓝本栈解析成"目标物品的模型"。
 *
 * <p>为什么要走 {@code ItemRenderer#getModel}：那是物品渲染的唯一入口，在这里换掉模型，
 * 后续的相机变换（{@code applyTransform}）、渲染层、模型 overrides（弓的拉弦、指南针角度、
 * 时钟指针……）用的就都是目标物品自己的模型，不需要我们再包一层 {@code BakedModel}。
 *
 * <p>解析失败（目标物品不存在等）返回 {@code null}，调用方保持原模型（蓝本自己的纸片图标）。
 */
public final class BlueprintModels {

    private BlueprintModels() {}

    @Nullable
    public static BakedModel resolve(ItemStack stack, ItemRenderer renderer,
                                     @Nullable Level level, @Nullable LivingEntity entity, int seed) {
        if (!ConstructedBlueprintItem.isBlueprint(stack)) return null;
        ItemStack shadow = ConstructedBlueprintItem.shadowOf(stack);
        if (shadow.isEmpty()) return null;
        try {
            BakedModel model = renderer.getItemModelShaper().getItemModel(shadow);
            if (model == null) return null;
            // ⭐ 再走一遍目标物品自己的 overrides：弓拉弦、指南针/时钟指向、弩上弦等都由它决定
            BakedModel resolved = model.getOverrides().resolve(model, shadow,
                    level == null ? Minecraft.getInstance().level : asClientLevel(level), entity, seed);
            return resolved != null ? resolved : model;
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    private static net.minecraft.client.multiplayer.ClientLevel asClientLevel(Level level) {
        return level instanceof net.minecraft.client.multiplayer.ClientLevel cl ? cl : null;
    }
}
