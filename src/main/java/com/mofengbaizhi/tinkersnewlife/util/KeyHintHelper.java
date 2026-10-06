package com.mofengbaizhi.tinkersnewlife.util;

import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;

/**
 * <b>§813 键位提示助手</b>：把「当前实际绑定的按键」取出来给提示文案用 ✓。
 *
 * <p>用户口径：「<b>我希望快捷键配置改变后键位工具提示也跟着变</b>」✓ —— 所以**任何**提示里
 * 需要写按键的地方，都不要再写死字母 ✗（本仓就踩过：飞剑切换键早就从 R 改成 Z，
 * 提示里却还写着"按 R 键切换模式" ✗），一律走这里 ✓。
 *
 * <h2>⚠ 为什么不能直接引用 KeyMapping（重要）</h2>
 * {@code KeyMapping} / {@code KeyBindings} 都是<b>客户端类</b> ✗：从公共代码里直接引用，
 * 专服启动时就是 {@code NoClassDefFoundError} ✗（本仓 §801 踩过"把可选模组的类型当超类返回"
 * 那类类加载坑 ✗）。⇒ 这里用 Forge 的 {@link DistExecutor#unsafeCallWhenOn} 把"取名字"
 * 这件事**甩给客户端** ✓：只有客户端才会真正加载 {@code KeyHintClient} ✓；
 * 服务端拿到 {@code null} ✓ ⇒ 调用方回退成<b>按键设置里那个名字</b>（翻译键 ✓ 双语 ✓ 永不失效 ✓）。
 *
 * <p>用法（提示文案里那个"按 %s"）：
 * <pre>
 *   Component.translatable("message.xxx", KeyHintHelper.hint(KeyHintHelper.FORM, "key.tinkersnewlife.open_wu_wei"))
 * </pre>
 */
public final class KeyHintHelper {

    // 键位标识：只是普通字符串 ⇒ 专服也安全 ✓（真正解析在客户端做 ✓）
    public static final String TECHNIQUE = "technique";
    public static final String REVERSE = "reverse";
    public static final String SWITCH_TECHNIQUE = "switch_technique";
    public static final String FORM = "form";
    public static final String FLY_SWITCH = "fly_switch";
    public static final String DOMAIN = "domain";
    public static final String DRAGON_STAFF = "dragon_staff";
    public static final String OPEN_BAG = "open_bag";

    private KeyHintHelper() {
    }

    /**
     * 提示里要显示"按 X"时用它 ✓。
     *
     * @param keyId           键位标识（本类的常量 ✓）
     * @param fallbackNameKey 取不到当前绑定时用的翻译键 —— 一般直接给
     *                        {@code key.tinkersnewlife.xxx}（＝按键设置里那个名字 ✓ 双语 ✓）
     */
    public static Component hint(String keyId, String fallbackNameKey) {
        String live = display(keyId);
        return (live == null || live.isEmpty())
                ? Component.translatable(fallbackNameKey)
                : Component.literal(live);
    }

    /** 当前绑定键的显示名 ✓（仅客户端）；服务端或出错 ⇒ {@code null} ✓ */
    @Nullable
    public static String display(String keyId) {
        try {
            return DistExecutor.unsafeCallWhenOn(Dist.CLIENT,
                    () -> () -> com.mofengbaizhi.tinkersnewlife.client.input.KeyHintClient.name(keyId));
        } catch (Throwable t) {
            return null;
        }
    }
}
