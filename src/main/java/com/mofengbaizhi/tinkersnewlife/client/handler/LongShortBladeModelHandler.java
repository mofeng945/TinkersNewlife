package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.model.LongShortBladeModel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;

/**
 * ⭐ §1146 <b>给长短刃接上「按场景 × 形态切模型」</b>（用户口径 ✓「能先搭建好环境吗」✓）。
 *
 * <h2>⭐ 用户要画的五套贴图 ⇒ 对应五个模型槽位</h2>
 * <table border="1">
 *   <tr><th>#</th><th>用途</th><th>模型（本类注册 ✓）</th><th>贴图目录</th></tr>
 *   <tr><td>1</td><td>⭐ <b>物品栏·未手持</b></td><td>{@code ctx_gui_idle}</td><td>{@code .../long_short_blade/ctx_gui_idle/}</td></tr>
 *   <tr><td>2</td><td>⭐ <b>物品栏·已装配＝长刀</b></td><td>{@code ctx_gui_long}</td><td>{@code .../ctx_gui_long/}</td></tr>
 *   <tr><td>3</td><td>⭐ <b>物品栏·已装配＝短刀</b></td><td>{@code ctx_gui_short}</td><td>{@code .../ctx_gui_short/}</td></tr>
 *   <tr><td>4</td><td>⭐ <b>手持·长刀</b></td><td>{@code ctx_held_long}</td><td>{@code .../ctx_held_long/}</td></tr>
 *   <tr><td>5</td><td>⭐ <b>手持·短刀</b></td><td>{@code ctx_held_short}</td><td>{@code .../ctx_held_short/}</td></tr>
 * </table>
 *
 * <h2>⚠⚠ 为什么五个都要显式 {@code RegisterAdditional}</h2>
 * ⭐ 原版只烘焙**被引用到**的模型 ✗ ⇒ ⚠ 这五个不写进物品模型 JSON 的 {@code overrides} 里
 * （⭐ 本仓铁律：`models/**` 既有文件不许改 ✗）⇒ ⭐ **没人引用 ⇒ 根本不会被烘焙** ✗
 * ⇒ ⭐ 必须在 {@link ModelEvent.RegisterAdditional} 里**显式登记** ✓（⭐ `BrokenToolModels` 同款做法 ✓）。
 *
 * <h2>⚠ 缺文件不报错（⭐ 设计如此 ✓）</h2>
 * ⭐ 用户**可以只画一部分** ✓ ⇒ ⭐ 没画的槽位**回退到默认模型** ✓（⭐ 见
 * {@link LongShortBladeModel} 的构造 ✓）⇒ ⭐ 不崩、不丢贴图、只在 log 里说一句 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LongShortBladeModelHandler {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("TinkersNewlife/长短刃模型");

    /** ⭐ 基础模型（⭐ 就是现有的 {@code item/long_short_blade.json} ✓ **不动它** ✗） */
    public static final ResourceLocation BASE =
            new ResourceLocation(TinkersNewlife.MOD_ID, "item/long_short_blade");

    // ============================================================
    //  ⭐⭐ 三个槽位（⚠ 用户 2026-10-09 给了精确口径 ✗ 由 5 个收敛成 3 个 ✓）
    // ============================================================

    /**
     * ⭐ <b>物品栏·长刀</b> ✓ —— ⭐ 用户口径：「手持时长刀**物品栏模型**（**和普通剑同一匠魂父模型**）」✓
     * ⇒ ⭐ 所以它**同时**承担"未手持的长刀图标"与"手持时长刀在物品栏里的图标"✓
     * （⚠ 系统分不出"玩家有没有拿在手上" ✗ 见 §1146 的交代 ✓）。
     */
    public static final ResourceLocation CTX_INV_LONG =
            ctx("ctx_inv_long");

    /**
     * ⭐ <b>短刀（物品栏＋手持共用）</b> ✓ —— ⭐ 用户口径：
     * 「手持时短刀**物品栏模型和手持模型相同**」✓ ⇒ ⭐ 短刀只要**这一个**模型 ✓。
     */
    public static final ResourceLocation CTX_INV_SHORT =
            ctx("ctx_inv_short");

    /**
     * ⭐ <b>手持·长刀</b> ✓ —— ⭐ 用户口径：「手持时长刀手持模型（**大型工具**匠魂父模型）」✓
     * ⇒ ⭐ 父模型用 {@code tconstruct:item/base/large_tool} ✓（⭐ 普通剑那套是 {@code tall} ✓）。
     */
    public static final ResourceLocation CTX_HELD_LONG =
            ctx("ctx_held_long");

    private static ResourceLocation ctx(String name) {
        return new ResourceLocation(TinkersNewlife.MOD_ID, "item/tool/long_short_blade/" + name);
    }

    private LongShortBladeModelHandler() {
    }

    /** ⭐ 把三个槽位登记进烘焙队列 ✓（⭐ 否则没人引用 ⇒ 不会被烘焙 ✗） */
    @SubscribeEvent
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        try {
            event.register(CTX_INV_LONG);
            event.register(CTX_INV_SHORT);
            event.register(CTX_HELD_LONG);
        } catch (Throwable t) {
            LOG.warn("[长短刃] 登记场景模型失败（已忽略）：{}", t.toString());
        }
    }

    /** ⭐ 烘焙完成后给基础模型外面包一层 ✓（⭐ 按场景 × 形态切 ✓） */
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        try {
            Map<ResourceLocation, BakedModel> models = event.getModels();
            BakedModel base = models.get(BASE);
            if (base == null) {
                LOG.warn("[长短刃] 没找到基础模型 {} ⇒ 按场景切模型没接上", BASE);
                return;
            }
            BakedModel invLong = models.get(CTX_INV_LONG);
            BakedModel invShort = models.get(CTX_INV_SHORT);
            BakedModel heldLong = models.get(CTX_HELD_LONG);
            // ⚠ 缺哪个就传 null ✗ ⇒ ⭐ 包装器自己回退 ✓（⭐ 只画一部分也不会崩 ✓）
            // ⭐ 短刀：⭐ 物品栏与手持**共用** `invShort` ✓（⭐ 用户口径 ✓ 见常量注释 ✓）
            models.put(BASE, new LongShortBladeModel(base,
                    invLong, invLong, invShort, heldLong, invShort));
            LOG.info("[长短刃] 已接按场景×形态切模型：物品栏长={} 短={} 手持长={}（短刀两处共用物品栏那套 ✓）",
                    invLong != null, invShort != null, heldLong != null);
        } catch (Throwable t) {
            LOG.warn("[长短刃] 接按场景切模型时出错（已忽略）：{}", t.toString());
        }
    }

    /**
     * ⚠ 仅供自检：⭐ `ModelResourceLocation` 与 `ResourceLocation` 的区别 ✗ ——
     * ⭐ `event.getModels()` 的键是**后者** ✓（⭐ `BrokenToolModels` 已实证 ✓）——
     * 这里留一个引用免得有人误用前者 ✓。
     */
    @SuppressWarnings("unused")
    private static ModelResourceLocation unusedSelfCheck() {
        return null;
    }
}
