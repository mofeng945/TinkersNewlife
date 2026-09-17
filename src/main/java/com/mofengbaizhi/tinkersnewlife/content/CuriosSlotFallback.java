package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.util.ISlotHelper;
import top.theillusivec4.curios.common.slottype.SlotType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Curios 槽位**运行时兜底**：保证"只有本模组 + Curios"时，本模组物品用到的槽位**真的有格子**。
 *
 * <h2>为什么要这个</h2>
 * Curios 的槽位分两半，很容易踩错：
 * <ul>
 *   <li><b>类型</b>来自它自带的<b>数据包</b>（{@code data/curios/curios/slots/*.json}，随 jar 永远存在 ✓）——
 *       所以 {@code head}/{@code ring}/{@code charm}/{@code curio}… 这些<b>名字</b>一直存在；</li>
 *   <li><b>槽数（size）必须由模组注册</b> —— 数据包那几个 JSON 里<b>没有 size 字段</b> ✗。
 *       没人注册的槽就是 <b>0 格</b>：类型在、物品却戴不上 ✗。</li>
 * </ul>
 * 实测（dev 环境，只装了匠魂/帕秋莉/JEI/Curios 等少数模组）：
 * {@code [Curios]: Loaded 12 curio slots} = 数据包 10 个 + 本模组注册的 {@code feet}/{@code curse_core}，
 * 而 {@code head} 是 0 格 —— 面具在那个环境里根本戴不上 ✗。
 *
 * <h2>为什么不用 IMC 注册（{@link ModCurios#enqueueIMC}）来兜</h2>
 * Curios 对同名槽位是"**后来者覆盖 size**"：无条件 IMC 注册会把整合包里别人设好的槽数改掉 ✗
 * （{@link ModCurios} 里 charm 那段注释记的就是这个坑）。
 * 所以这里走运行时兜底：<b>只在"确实没有格子"时补</b>，并且把别人设好的
 * 图标/顺序/校验器/开关原样带过来 ✓ —— 有提供者在场时本类什么都不做 ✓。
 *
 * <p>时机两个：{@link ServerAboutToStartEvent}（初次数据包加载完、任何玩家加入之前）+
 * {@link OnDatapackSyncEvent}（{@code /reload} 会按数据包+IMC 重建槽位表、把这里补的冲掉，
 * 用 HIGHEST 优先级抢在 Curios 把槽位表下发给客户端之前再补一次 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CuriosSlotFallback {

    private CuriosSlotFallback() {}

    /**
     * 本模组物品会用到的 curios 槽位 → "只有本模组 + Curios"时也要保证的最小槽数。
     * <p>改这里就够：新增物品用新槽位时，把槽位 id 与最小格数加进来 ✓。
     */
    private static final Map<String, Integer> REQUIRED = new LinkedHashMap<>();

    static {
        REQUIRED.put("ring", 2);         // 命灯指轮 / 同心戒（戒指槽）
        REQUIRED.put("head", 1);         // 双向认知阻碍面具（头部槽）
        REQUIRED.put("hands", 2);        // 噤默手套
        REQUIRED.put("feet", 1);         // 飞剑（脚部饰栏飞行）
        REQUIRED.put("charm", 1);        // 封呪瓶（通用饰品槽）
        REQUIRED.put("curio", 1);        // 封呪瓶（通用饰品槽兜底）
        REQUIRED.put("curse_core", 1);   // 咒力核心（本模组自己的槽）
    }

    /** 服务端启动：数据包已加载、玩家还没进来 → 补一次（之后玩家加入时 Curios 会把槽位表同步给客户端 ✓） */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        ensure();
    }

    /** {@code /reload} 之后重建过槽位表：再补一次（抢在 Curios 下发给客户端之前） */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ensure();
    }

    /**
     * 逐槽检查：缺格子的补上，已有足够格子的**一律不动**（绝不覆盖别人的 size/图标）。
     * <p>整个过程包在 try/catch 里：Curios 没装/API 变动都不该让服务端起不来 ✓。
     */
    public static void ensure() {
        try {
            ISlotHelper helper = CuriosApi.getSlotHelper();
            if (helper == null) return;   // Curios 还没就绪（或未安装）
            for (Map.Entry<String, Integer> entry : REQUIRED.entrySet()) {
                String id = entry.getKey();
                int want = entry.getValue();
                ISlotType existing = helper.getSlotType(id).orElse(null);
                if (existing != null && existing.getSize() >= want) continue;   // 已有：不动 ✓

                SlotType.Builder builder = new SlotType.Builder(id).size(want);
                if (existing != null) {
                    // 类型在（数据包定义的），只是没人给 size → 原样保留它的图标/顺序/校验器/开关 ✓
                    if (existing.getIcon() != null) builder.icon(existing.getIcon());
                    builder.order(existing.getOrder());
                    builder.useNativeGui(existing.useNativeGui());
                    builder.hasCosmetic(existing.hasCosmetic());
                    builder.renderToggle(existing.canToggleRendering());
                    if (existing.getDropRule() != null) builder.dropRule(existing.getDropRule());
                    for (ResourceLocation validator : existing.getValidators()) builder.validator(validator);
                } else if ("curse_core".equals(id)) {
                    // 连类型都没有的只有本模组自己的槽（数据包里有 10 个通用槽，不含 curse_core）
                    builder.icon(new ResourceLocation(TinkersNewlife.MOD_ID, "gui/tinker_pattern/curse_core"));
                }
                helper.addSlotType(builder.build());
                TinkersNewlife.LOGGER.info("[Curios 兜底] 槽位 {} 缺失（{} 格）→ 已补到 {} 格",
                        id, existing == null ? "无类型" : String.valueOf(existing.getSize()), want);
            }
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[Curios 兜底] 检查槽位时出现异常（已忽略）：{}", t.toString());
        }
    }
}
