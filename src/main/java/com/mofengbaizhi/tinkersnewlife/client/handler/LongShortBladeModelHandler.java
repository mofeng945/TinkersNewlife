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
     * ⭐ <b>物品栏·未手持</b> ✓ —— ⭐ 用户口径（2026-10-09）给了成品图「**物品栏-未手持**」✓
     * 用 ⭐ `short_inv_blade` ＋ `short_inv_handle` ✓ ⇒ ⭐ 语义 ＝ **还没配成一对时**那一把的样子 ✓
     * （⚠ 判定用 `lnb_pair` ✓ 见 `LongShortBladeModel` ✓）。
     */
    public static final ResourceLocation CTX_IDLE =
            ctx("ctx_idle");

    /**
     * ⭐ <b>物品栏·长刀</b> ✓ —— ⭐ 用户口径：「手持时长刀**物品栏模型**（**和普通剑同一匠魂父模型**）」✓
     * ⇒ ⭐ 所以它**同时**承担"未手持的长刀图标"与"手持时长刀在物品栏里的图标"✓
     * （⚠ 系统分不出"玩家有没有拿在手上" ✗ 见 §1146 的交代 ✓）。
     */
    public static final ResourceLocation CTX_INV_LONG =
            ctx("ctx_inv_long");

    /**
     * ⚠⚠ <b>短刀的槽位已**合并**</b> ✗ —— ⭐ 用户口径（2026-10-09）：
     * 「**短刀的物品栏模型和它的手持模型都走hand**」✓
     * ⇒ ⭐ 所以短刀**只有** {@link #CTX_HELD_SHORT} 一个模型 ✓，
     * ⭐ 物品栏与手持**共用**它 ✓。
     * <p>⚠ 原来这里还有一个 {@code CTX_INV_SHORT}（⭐ 走 `short_inv_*` 那两张 ✓）⇒ ⭐ 已**弃用并删除** ✓
     * （⭐ 模型 JSON 与贴图目录都清了 ✓ 生成器登记也去了那两条 ✓）。
     */

    /**
     * ⭐ <b>手持·长刀</b> ✓ —— ⭐ 用户口径：「手持时长刀手持模型（**大型工具**匠魂父模型）」✓
     * ⇒ ⭐ 父模型用 {@code tconstruct:item/base/large_tool} ✓（⭐ 普通剑那套是 {@code tall} ✓）。
     */
    public static final ResourceLocation CTX_HELD_LONG =
            ctx("ctx_held_long");

    /**
     * ⭐ <b>手持·短刀</b> ✓ —— ⚠⚠ 这一条是我**补上的** ✗：⭐ 用户画了 **10** 张切分图 ✓
     * 而我第一版只用了 **8** 张 ✗（⭐ 漏了 {@code short_hand_blade} 与 {@code short_hand_handle} ✓）
     * —— ⚠ 当时我按口径里那句「手持时短刀**物品栏模型和手持模型相同**」推断"短刀只建一个槽位"✗
     * ⇒ ⭐ 用户指正「**我不是画了10个拆分图吗**」✓ ⇒ ⭐ 补齐这个槽位 ✓ 用那两张 ✓
     * （⭐ 父模型同为 {@code tall} ✓ 短刀是普通剑那一路 ✓）。
     */
    public static final ResourceLocation CTX_HELD_SHORT =
            ctx("ctx_held_short");

    private static ResourceLocation ctx(String name) {
        return new ResourceLocation(TinkersNewlife.MOD_ID, "item/tool/long_short_blade/" + name);
    }

    private LongShortBladeModelHandler() {
    }

    /** ⭐ 把**三个**槽位登记进烘焙队列 ✓（⭐ 否则没人引用 ⇒ 不会被烘焙 ✗） */
    @SubscribeEvent
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        try {
            event.register(CTX_IDLE);
            event.register(CTX_INV_LONG);
            event.register(CTX_HELD_LONG);
            event.register(CTX_HELD_SHORT);
        } catch (Throwable t) {
            LOG.warn("[长短刃] 登记场景模型失败（已忽略）：{}", t.toString());
        }
    }

    /** ⭐ 烘焙完成后给基础模型外面包一层 ✓（⭐ 按场景 × 形态切 ✓） */
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        try {
            Map<ResourceLocation, BakedModel> models = event.getModels();
            BakedModel idle = models.get(CTX_IDLE);
            BakedModel invLong = models.get(CTX_INV_LONG);
            BakedModel heldLong = models.get(CTX_HELD_LONG);
            BakedModel heldShort = models.get(CTX_HELD_SHORT);
            // ⭐⭐ **短刀：物品栏与手持**都走 `hand`**（用户口径 ✓ 2026-10-09：
            //   「**短刀的物品栏模型和它的手持模型都走hand**」✓）
            //   ⇒ ⭐ 所以 `ctx_inv_short`（`short_inv_*` 那两张）**不再使用** ✓ 已随本轮清理删除 ✓
            //   ⇒ ⭐ 短刀两个场景**共用同一份** `ctx_held_short` ✓。
            BakedModel shortSide = heldShort;

            // ⚠⚠ **不能只查裸键** ✗ —— ⭐ 实测日志（用户 2026-10-09「还是」那一局 ✓）：
            //   `[长短刃] 没找到基础模型 tinkersnewlife:item/long_short_blade` ✗
            //   ⚠ 而同一条日志上方写着 `Missing textures in model
            //   tinkersnewlife:long_short_blade#inventory` ✓
            //   ⇒ ⭐ **物品模型的键是 `ModelResourceLocation`**（形如 `<路径>#inventory` ✓）
            //     而不是裸 `ResourceLocation` ✗ ⇒ ⭐ 原来的 `models.get(BASE)` 恒为 null ✗
            //     ⇒ ⭐ **整套"按场景切模型"从来没接上过** ✗ ✓ 这就是"还是占位"的真正原因 ✓。
            //   ⇒ ⭐ 改成 ⭐ **扫全表、按"路径"匹配** ✓ ——
            //     ⭐ 这样 `#inventory`／任何变体键都能被包上 ✓（⭐ 将来别的变体也不会漏 ✓）。
            int wrapped = 0;
            for (Map.Entry<ResourceLocation, BakedModel> e : new java.util.ArrayList<>(models.entrySet())) {
                ResourceLocation key = e.getKey();
                if (key == null || e.getValue() == null) {
                    continue;
                }
                // ⭐ 只认"这个物品模型自己"的键 ✓（⭐ 别把 `ctx_*` 自己也包进去 ✗ 会自己套自己 ✓）
                //   ⚠⚠ **path 有两种形态** ✗（⭐ 又差点漏掉 ✓）：
                //     · ⭐ `ModelResourceLocation`（物品：`tinkersnewlife:long_short_blade#inventory` ✓）
                //       ⇒ ⭐ 它的 `getPath()` 是 ⭐ **`long_short_blade`** ✗（⭐ 没有 `item/` 前缀 ✓）；
                //     · ⭐ 裸 `ResourceLocation`（⭐ 我 `RegisterAdditional` 的那几个 ✓）
                //       ⇒ ⭐ `getPath()` 是 ⭐ `item/tool/long_short_blade/ctx_*` ✓。
                //   ⇒ ⭐ 所以按 **"路径以 `long_short_blade` 结尾"** 来匹配 ✓ 且 ⭐ 排除含 `ctx_` 的 ✓。
                String path = key.getPath();
                if (!path.endsWith("long_short_blade")) {
                    continue;
                }
                if (path.contains("ctx_")) {
                    continue;
                }
                if (!TinkersNewlife.MOD_ID.equals(key.getNamespace())) {
                    continue;
                }
                models.put(key, new LongShortBladeModel(e.getValue(),
                        idle, invLong, shortSide, heldLong));
                wrapped++;
            }

            if (wrapped == 0) {
                LOG.warn("[长短刃] 表里没有任何路径以 long_short_blade 结尾的键 ⇒ 按场景切模型没接上（⭐ 这就是显示成占位的原因 ✓）");
                return;
            }
            LOG.info("[长短刃] 已接按场景×形态切模型（包了 {} 个键 ✓）：物品栏长={} 手持长={} 短刀(两处共用hand)={}",
                    wrapped, invLong != null, heldLong != null, shortSide != null);
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
