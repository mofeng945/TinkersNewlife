package com.mofengbaizhi.tinkersnewlife.integration.iris;

/**
 * 光影包（Oculus / Iris）探测：<b>全部走反射，不硬依赖</b> —— 没装光影模组时恒为 false ✓。
 *
 * <p>为什么需要它：光影包只会替换 {@code GameRenderer} 上那些**原版 shader getter** 返回的程序
 * （见 Oculus `net/irisshaders/iris/mixin/MixinGameRenderer`），模组自己 {@code new ShaderInstance}
 * 出来的核心着色器它既不知道也不会换 ⇒ 用自带着色器画的几何在光影包下会整条看不见。
 * 目前用到这条探测的地方：{@link com.mofengbaizhi.tinkersnewlife.mixin.MantleFluidShaderMixin}
 * （匠魂流体）。
 *
 * <p>⚠ 不缓存探测结果：光影包可以在游戏里随时开关，缓存了就会一直用旧结论。
 * 只缓存反射出来的 {@code Method} 与实例（每帧调用也就一次反射 invoke，开销可忽略）。
 */
public final class IrisCompat {

    private static boolean resolved = false;
    private static Object apiInstance;
    private static java.lang.reflect.Method isShaderPackInUse;

    private IrisCompat() {}

    /** 当前是否有光影包正在生效（没装 Oculus/Iris，或调用失败，一律 false ✓） */
    public static boolean isShaderPackInUse() {
        if (!resolved) resolve();
        if (apiInstance == null || isShaderPackInUse == null) return false;
        try {
            Object result = isShaderPackInUse.invoke(apiInstance);
            return result instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void resolve() {
        resolved = true;
        try {
            // Oculus 1.8.0 实测有这个 API（net.irisshaders.iris.api.v0.IrisApi）：
            //   IrisApi.getInstance().isShaderPackInUse()
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            apiInstance = api.getMethod("getInstance").invoke(null);
            isShaderPackInUse = api.getMethod("isShaderPackInUse");
        } catch (Throwable t) {
            apiInstance = null;
            isShaderPackInUse = null;
        }
    }
}
