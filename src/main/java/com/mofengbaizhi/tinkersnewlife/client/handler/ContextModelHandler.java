package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.model.ContextModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * <b>把长矛的"物品栏模型"接上</b>（§938）—— 引用 §937 查到的匠魂做法（{@code applyTransform} 按场景切 ✓）。
 *
 * <p>时机：{@code ModelEvent.ModifyBakingResult} ✓（**MOD 总线** ✓ 烘焙完成后能改那张
 * {@code ResourceLocation → BakedModel} 表 ✓ —— 这是给已经烘好的模型"套壳"最省事的入口 ✓）。
 *
 * <p>做的事：把 {@code tinkersnewlife:item/spear} 换成
 * {@link ContextModel}（原模型 ＋ {@code tinkersnewlife:item/spear_gui} ✓）
 * ⇒ 物品栏走 {@code spear_gui.json} ✓ 手持走原模型 ✓。
 *
 * <p>⚠ 两个都取不到时**什么都不做** ✓（只打一行 warn ✓ 不崩 ✓）；
 * ⚠ 匠魂工具是按栈 overrides 解析模型的 ✓ 所以 {@link ContextModel} 里连 overrides 一起包了 ✓
 * （见那边的注释 ✓ 不然会被绕过 ✗）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ContextModelHandler {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("TinkersNewlife/Model");

    private ContextModelHandler() {}

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        try {
            Map<ResourceLocation, BakedModel> models = event.getModels();
            ResourceLocation base = new ResourceLocation(TinkersNewlife.MOD_ID, "item/spear");
            ResourceLocation gui = new ResourceLocation(TinkersNewlife.MOD_ID, "item/spear_gui");

            BakedModel original = models.get(base);
            if (original == null) {
                LOG.warn("[模型] 没找到 {}（长矛的物品栏模型没接上）", base);
                return;
            }
            BakedModel guiModel = models.get(gui);
            if (guiModel == null) {
                guiModel = original;      // 没写 spear_gui.json 就拿原模型顶上 ✓（等于没切换 ✓ 不崩 ✓）
                LOG.warn("[模型] 没找到 {} ⇒ 物品栏暂时沿用原模型", gui);
            }
            models.put(base, new ContextModel(original, guiModel));
            LOG.info("[模型] 长矛已接上按场景切模型：物品栏 → {}", gui);
        } catch (Throwable t) {
            LOG.warn("[模型] 接物品栏模型时出错（已忽略）：{}", t.toString());
        }
    }
}
