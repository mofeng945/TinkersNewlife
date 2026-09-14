package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import com.mofengbaizhi.tinkersnewlife.client.renderer.UnnameableGlitchRenderer;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/**
 * 「不可名状」的客户端观感：
 *
 * <ul>
 *   <li><b>拉高 FOV</b>（{@link ViewportEvent.ComputeFov}）—— 视野被"撑开"的压迫感；</li>
 *   <li><b>锐化 + 高对比度 + 高饱和度 + 反转颜色</b>—— 逐像素效果，覆盖层做不到，走<b>后处理着色器链</b>
 *       （{@code assets/tinkersnewlife/shaders/post/unnameable.json} → program → fsh），
 *       用原版同款的 {@code GameRenderer#loadEffect} 挂、{@code shutdownEffect} 摘；</li>
 *   <li><b>视角晃动</b>（反胃）；</li>
 *   <li><b>信号干扰花屏</b>覆盖层（{@code UnnameableGlitchRenderer}）。</li>
 * </ul>
 *
 * <h2>⚠ 色彩效果"绝不常驻"（两条保险）</h2>
 * 反转颜色要是残留下来，玩家整局游戏画面都是反的 —— 所以：
 * <ol>
 *   <li><b>生命周期兜底</b>：效果消失 / 死亡重生 / 退出世界 / 断线 / 换维度，
 *       都会立刻 {@code shutdownEffect}（见 {@link #forceShutdown} 的全部挂钩点）；</li>
 *   <li><b>生效期间也是闪断的</b>（默认）：亮 {@link #PULSE_ON_MIN}~{@link #PULSE_ON_MAX} tick、
 *       断 {@link #PULSE_OFF_MIN}~{@link #PULSE_OFF_MAX} tick 随机交替 —— 既像"信号时断时续"，
 *       也不会让玩家一直糊在反转色里。想要一直开着：{@code unnameable.post_effect_pulse = false}。</li>
 * </ol>
 *
 * <p>⚠ 历史：这里原本是"模拟失明"（雾的远近平面缩到 1%、雾色涂黑），按需求已删除。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public class UnnameableClientHandler {

    /** 视角晃动幅度（反胃参数） */
    private static final float SWAY_AMOUNT = 10f;

    /** 后处理链（原版同款格式：post 链 → program → fsh） */
    private static final ResourceLocation POST_CHAIN =
            new ResourceLocation(TinkersNewlife.MOD_ID, "shaders/post/unnameable.json");

    /** 闪断节奏（tick）：亮一段 / 断一小段，随机取值 */
    private static final int PULSE_ON_MIN = 45;
    private static final int PULSE_ON_MAX = 95;
    private static final int PULSE_OFF_MIN = 12;
    private static final int PULSE_OFF_MAX = 35;

    private static final Random RNG = new Random();

    /** 我们此刻是否把后处理挂上去了（避免重复 load，也避免误摘别人的效果） */
    private static boolean postApplied = false;
    /** 下一次闪断切换的 tick（客户端 tick 计数） */
    private static int nextPulseAt = 0;
    private static int clientTicks = 0;

    private UnnameableClientHandler() {
    }

    private static MobEffectInstance unnameable(LocalPlayer player) {
        return player == null ? null : player.getEffect(ModEffects.UNNAMEABLE.get());
    }

    private static boolean pulseEnabled() {
        try {
            return ModConfig.UNNAMEABLE_POST_EFFECT_PULSE.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean postEffectEnabled() {
        try {
            return ModConfig.UNNAMEABLE_POST_EFFECT.get();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static void applyPost() {
        if (postApplied) return;
        Minecraft.getInstance().gameRenderer.loadEffect(POST_CHAIN);
        postApplied = true;
        TinkersNewlife.LOGGER.info("[不可名状] 后处理挂上：{}（锐化/对比度/饱和度/反转颜色）", POST_CHAIN);
    }

    private static void removePost() {
        if (!postApplied) return;
        Minecraft.getInstance().gameRenderer.shutdownEffect();
        postApplied = false;
        TinkersNewlife.LOGGER.info("[不可名状] 后处理摘除");
    }

    /**
     * 无条件摘除后处理并复位状态。<b>所有"效果可能已经结束"的时机都要调它</b>：
     * 退出世界、断线、死亡重生、换维度、切换服务器……宁可多摘一次，也别让反转色残留。
     */
    public static void forceShutdown() {
        postApplied = false;
        nextPulseAt = 0;
        try {
            Minecraft.getInstance().gameRenderer.shutdownEffect();
        } catch (Throwable ignored) {
            // 渲染器还没起来 / 已经关了：忽略
        }
    }

    /** 拉高 FOV：视野被撑开 */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (unnameable(player) == null) return;

        double multiplier = 1.4D;
        try {
            multiplier = Math.max(1.0D, Math.min(2.5D, ModConfig.UNNAMEABLE_FOV_MULTIPLIER.get()));
        } catch (Throwable ignored) {
            // 配置未就绪 → 用默认值
        }
        event.setFOV(event.getFOV() * multiplier);
    }

    /**
     * 每 tick 维护后处理：
     * <ul>
     *   <li>没有效果 → 立刻摘掉（兜底）；</li>
     *   <li>有效果且开了闪断 → 亮/断按随机节奏交替（这是"色彩效果不常驻"的常态表现）；</li>
     *   <li>有效果且关了闪断 → 一直挂着。</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        clientTicks++;

        // ⚠⚠ 这里**只能**由"身上真的有不可名状"来决定是否挂后处理。
        //    曾经有个 TNL_DEBUG_POST_EFFECT 调试开关能让它无条件挂上（为了验证着色器编译），
        //    结果那个环境变量被 Gradle 守护进程记住、后续 runClient 全都继承了
        //    → 玩家"没获得效果也在反转颜色"。这类开关一律不许再进正式构建。
        boolean want = unnameable(player) != null && postEffectEnabled();
        if (!want) {
            removePost();
            nextPulseAt = 0;
            return;
        }
        if (!pulseEnabled()) {
            applyPost();
            return;
        }
        if (clientTicks >= nextPulseAt) {
            if (postApplied) {
                removePost();
                nextPulseAt = clientTicks + PULSE_OFF_MIN + RNG.nextInt(PULSE_OFF_MAX - PULSE_OFF_MIN + 1);
            } else {
                applyPost();
                nextPulseAt = clientTicks + PULSE_ON_MIN + RNG.nextInt(PULSE_ON_MAX - PULSE_ON_MIN + 1);
            }
        }
    }

    // ---------- 生命周期兜底：任何"离开"的时机都摘掉后处理 ----------

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        // 进世界/换服务器：先复位，保证从干净状态开始
        forceShutdown();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        forceShutdown();
    }

    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        // 死亡重生 / 换维度：玩家对象被替换，本地效果状态会丢 → 先摘干净
        forceShutdown();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        forceShutdown();
    }

    /** 模拟反胃：视角晃动 */
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (unnameable(player) == null) return;

        float ticks = player.tickCount + (float) event.getPartialTick();

        float swayYaw = (float) Math.sin(ticks * 0.08f) * SWAY_AMOUNT * 2;
        float swayPitch = (float) Math.cos(ticks * 0.07f) * SWAY_AMOUNT * 1.2f;
        float swayRoll = (float) Math.sin(ticks * 0.05f) * SWAY_AMOUNT * 0.8f;

        event.setYaw(event.getYaw() + swayYaw);
        event.setPitch(event.getPitch() + swayPitch);
        event.setRoll(event.getRoll() + swayRoll);
    }

    /**
     * 模拟"信号干扰 / 花屏"：整屏覆盖层（撕裂条 + 噪点 + 滚动干扰带 + 不定时闪屏）。
     *
     * <p>画在 {@code RenderGuiEvent.Post}（整块 GUI 画完之后），所以血条/物品栏/HUD 也会被"干扰"到，
     * 观感就是"显示器坏了"而不是"画面里多了一层贴图"。
     */
    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (unnameable(player) == null) return;

        // 1.20.1 的 RenderGuiEvent 只给 GuiGraphics/partialTick，屏幕尺寸从窗口取（GUI 缩放后的尺寸）
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        UnnameableGlitchRenderer.render(
                event.getGuiGraphics(), width, height, player.tickCount, unnameable(player));
    }
}
