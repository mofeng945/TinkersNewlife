package com.mofengbaizhi.tinkersnewlife.client.input;

import net.minecraft.client.KeyMapping;

import javax.annotation.Nullable;

/**
 * <b>§813 客户端侧</b>：把键位标识解析成「当前绑定键」的显示名 ✓。
 *
 * <p>⚠ 这个类<b>只允许被客户端代码碰</b> ✗ —— 唯一入口是
 * {@code KeyHintHelper#display} 里那句 {@code DistExecutor.unsafeCallWhenOn(Dist.CLIENT, ...)} ✓
 * （专服上那个 lambda 永远不会执行 ⇒ 本类不会被加载 ⇒ 不会 {@code NoClassDefFoundError} ✓）。
 *
 * <p>{@code getTranslatedKeyMessage()} 返回的就是玩家在"按键设置"里看到的那串字 ✓
 * ⇒ 玩家改键后，提示立刻跟着变 ✓（用户口径 ✓）。
 */
public final class KeyHintClient {

    private KeyHintClient() {
    }

    @Nullable
    public static String name(String keyId) {
        KeyMapping mapping;
        switch (keyId) {
            case "technique" -> mapping = KeyBindings.USE_TECHNIQUE.get();
            case "reverse" -> mapping = KeyBindings.REVERSE_TECHNIQUE.get();
            case "switch_technique" -> mapping = KeyBindings.SWITCH_TECHNIQUE.get();
            case "form" -> mapping = KeyBindings.OPEN_WU_WEI.get();
            case "fly_switch" -> mapping = KeyBindings.SWITCH_FLYING_SWORD_MODE.get();
            case "domain" -> mapping = KeyBindings.TOGGLE_DOMAIN.get();
            case "dragon_staff" -> mapping = KeyBindings.DRAGON_STAFF_USE.get();
            case "open_bag" -> mapping = KeyBindings.OPEN_BAG.get();
            default -> {
                return null;
            }
        }
        String name = mapping.getTranslatedKeyMessage().getString();
        return name.isEmpty() ? null : name;
    }
}
