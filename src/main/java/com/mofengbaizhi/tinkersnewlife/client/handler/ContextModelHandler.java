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
 * <b>给长矛接上"按场景切模型"</b>（§938 建 ✓ §939 改成"默认＝物品栏、手持可选"）——
 * 引用 §937 查到的匠魂做法（`applyTransform` 按场景切 ✓）。
 *
 * <p>时机：{@code ModelEvent.ModifyBakingResult} ✓（**MOD 总线** ✓ 烘焙完成后能改那张
 * {@code ResourceLocation → BakedModel} 表 ✓）。
 *
 * <h2>口径（§939）</h2>
 * - <b>默认模型</b> = {@code tinkersnewlife:item/spear} ✓ ＝ **物品栏那一套** ✓（用户口径 ✓）；
 * - <b>手持</b>走 {@code tinkersnewlife:item/spear_held} ✓ —— **这个文件是可选的** ✓：
 *   没写 ⇒ 手持也用默认 ✓（= 完全等于没切换 ✓ 不报错不崩 ✓ 只是进 log 说一句 ✓）。
 *
 * <p>⚠ 匠魂工具是按栈 overrides 解析模型的 ✓ 所以 {@link ContextModel} 里连 overrides 一起包了 ✓
 * （见那边的注释 ✓ 不然会被绕过 ✗）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ContextModelHandler {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("TinkersNewlife/Model");

    /** 默认（物品栏那一套） */
    public static final ResourceLocation SPEAR_BASE = new ResourceLocation(TinkersNewlife.MOD_ID, "item/spear");
    /** 手持那一套（**可选** ✓ 不写就沿用默认 ✓） */
    public static final ResourceLocation SPEAR_HELD = new ResourceLocation(TinkersNewlife.MOD_ID, "item/spear_held");

    private ContextModelHandler() {}

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        try {
            Map<ResourceLocation, BakedModel> models = event.getModels();
            BakedModel base = models.get(SPEAR_BASE);
            if (base == null) {
                LOG.warn("[模型] 没找到 {}（长矛的按场景切模型没接上）", SPEAR_BASE);
                return;
            }
            BakedModel held = models.get(SPEAR_HELD);
            if (held == null) {
                // 这是**设计上允许**的情况 ✓（用户口径：默认物品栏贴图，只有需要时才额外写手持 ✓）
                held = base;
                LOG.info("[模型] 长矛：没写 {} ⇒ 手持沿用默认贴图（默认＝物品栏那一套）", SPEAR_HELD);
            } else {
                LOG.info("[模型] 长矛：已接按场景切模型 —— 默认 {} ／ 手持 {}", SPEAR_BASE, SPEAR_HELD);
            }
            models.put(SPEAR_BASE, new ContextModel(base, held));
        } catch (Throwable t) {
            LOG.warn("[模型] 接按场景切模型时出错（已忽略）：{}", t.toString());
        }
    }
}
