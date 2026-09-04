package com.mofengbaizhi.tinkersnewlife.client.input;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.util.Lazy;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KeyBindings {

    public static final String KEY_CATEGORY = "key.tinkersnewlife.category";

    public static final Lazy<KeyMapping> USE_SKILL = Lazy.of(() -> new KeyMapping(
            "key.tinkersnewlife.use_skill",
            GLFW.GLFW_KEY_R,
            KEY_CATEGORY
    ));

    public static final Lazy<KeyMapping> DRAGON_STAFF_USE = Lazy.of(() -> new KeyMapping(
            "key.tinkersnewlife.dragon_staff_use",
            GLFW.GLFW_KEY_G,
            KEY_CATEGORY
    ));

    public static final Lazy<KeyMapping> OPEN_BAG = Lazy.of(() -> new KeyMapping(
            "key.tinkersnewlife.open_bag",
            GLFW.GLFW_KEY_B,
            KEY_CATEGORY
    ));

    // ✅ 新增飞剑模式切换按键（与 USE_SKILL 冲突，我们将其改为其他键，比如 Z）
    public static final Lazy<KeyMapping> SWITCH_FLYING_SWORD_MODE = Lazy.of(() -> new KeyMapping(
            "key.tinkersnewlife.switch_flying_sword_mode",
            GLFW.GLFW_KEY_Z,   // 改为 Z 键，因为 R 已被 USE_SKILL 占用
            KEY_CATEGORY
    ));

    // ✅ 坐杀搏徒：展开/关闭领域
    public static final Lazy<KeyMapping> TOGGLE_DOMAIN = Lazy.of(() -> new KeyMapping(
            "key.tinkersnewlife.toggle_domain",
            GLFW.GLFW_KEY_V,
            KEY_CATEGORY
    ));

    // ✅ 术式释放（解等术式：对看向的实体释放）
    public static final Lazy<KeyMapping> USE_TECHNIQUE = Lazy.of(() -> new KeyMapping(
            "key.tinkersnewlife.use_technique",
            GLFW.GLFW_KEY_C,
            KEY_CATEGORY
    ));

    // ✅ 切换当前术式（核心上有多个术式时循环选择；X 键）
    public static final Lazy<KeyMapping> SWITCH_TECHNIQUE = Lazy.of(() -> new KeyMapping(
            "key.tinkersnewlife.switch_technique",
            GLFW.GLFW_KEY_X,
            KEY_CATEGORY
    ));

    // ✅ 术式反转（无下限·苍 → 赫 等）：F 键
    public static final Lazy<KeyMapping> REVERSE_TECHNIQUE = Lazy.of(() -> new KeyMapping(
            "key.tinkersnewlife.reverse_technique",
            GLFW.GLFW_KEY_F,
            KEY_CATEGORY
    ));

    // ✅ 无为转变：形态选择界面（P 键）
    public static final Lazy<KeyMapping> OPEN_WU_WEI = Lazy.of(() -> new KeyMapping(
            "key.tinkersnewlife.open_wu_wei",
            GLFW.GLFW_KEY_P,
            KEY_CATEGORY
    ));

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(USE_SKILL.get());
        event.register(DRAGON_STAFF_USE.get());
        event.register(OPEN_BAG.get());
        event.register(SWITCH_FLYING_SWORD_MODE.get()); // 确保注册
        event.register(TOGGLE_DOMAIN.get()); // 确保注册
        event.register(USE_TECHNIQUE.get()); // 确保注册
        event.register(SWITCH_TECHNIQUE.get()); // 确保注册
        event.register(REVERSE_TECHNIQUE.get()); // 确保注册
        event.register(OPEN_WU_WEI.get()); // 确保注册
    }
}