package com.mofengbaizhi.tinkersnewlife.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * <b>终焉图书馆（EndingLibrary）数据组件的软依赖桥</b>（§836）。
 *
 * <h2>为什么要用它</h2>
 * 用户口径：「**终焉图书馆 mod 有全套数据组件，可以参考模仿**」✓ ——
 * 实查确认（反编译 ✓）：它是 1.20.1 上<b>整套 1.21 数据组件系统</b>的移植 ✓，
 * 其中 {@code UseEffectsComponent} <b>就是 1.21.11 的 {@code minecraft:use_effects}</b> ✓：
 * {@code record UseEffectsComponent(boolean canSprint, float speedMultiplier)} ✓
 * （字段名 {@code can_sprint} / {@code speed_multiplier} ✓ 与官方一致 ✓），
 * 而他们的客户端混入 {@code LocalPlayerMixin} 负责消费它 ✓：
 * <pre>
 * // 终焉图书馆 mixin/advanced/data_expand/component/LocalPlayerMixin（2.1.19 与 2.2 都是这套 ✓）
 * forwardImpulse *= 5.0F * speedMultiplier;   // 1.20.1 原版先乘了 0.2 ⇒ 5×1.0 正好抵消 ✓
 * canStartSprinting() ⇒ canSprint 为真时返回 true ✓
 * </pre>
 * ⇒ 长矛只要声明 {@code use_effects = {can_sprint: true, speed_multiplier: 1.0}}
 * 就<b>等价于原版 1.21.11 长矛</b>（蓄力不减速 ✓ 还能疾跑 ✓）✓
 * —— 这也正是 §835 用户实测"右键速度变慢了"的正解 ✓（1.20.1 把这条写死成 ×0.2 ✗）。
 *
 * <h2>为什么只写 NBT、不 import 他们的类</h2>
 * 两个包里的版本不同（测试包 <b>2.2</b> ✓ NL 包 <b>2.1.19fix</b> ✓）⇒ 直接编译期依赖有版本风险 ✗；
 * 而组件本来就是<b>存在物品 NBT 里的</b> ✓（根键 {@code Component} ✓ 见 {@code ItemComponentManager.HEAD} ✓），
 * 组件 id 用的是 {@code ResourceLocation} 字符串 ✓、值就是那个 record 的字段 ✓
 * ⇒ <b>按格式写 NBT 即可</b> ✓ 零版本绑定、零类加载风险 ✓（他们不在场时这一段就是 no-op ✓）。
 *
 * <h2>格式依据（反编译实读 ✓）</h2>
 * <ul>
 *   <li>{@code ItemComponentManager.HEAD = "Component"} ✓；</li>
 *   <li>{@code DataComponents.COM_USE_EFFECTS = new ResourceLocation("use_effects")} ✓ ⇒
 *       NBT 里的键是 {@code "minecraft:use_effects"} ✓（ResourceLocation#toString 带命名空间 ✓）；</li>
 *   <li>{@code ComponentChanges.CODEC} ＝ {@code dispatchedMap(组件id → 组件codec)} ✓
 *       ⇒ {@code Component: { "minecraft:use_effects": { can_sprint: 1b, speed_multiplier: 1.0f } }} ✓。</li>
 * </ul>
 * <h2>⚠ §837 实测补充（重要 ✓）</h2>
 * 用户实测"**还是减速**" ✗ —— 反编译他们的 `ItemStackMixin` 后找到原因 ✓：
 * 他们的组件管理器是**缓存**的 ✗（`endingLibrary$getComponentManagerIfPresent()` 直接返回缓存字段 ✓
 * 默认是一张**空表** ✓），只有那份 ItemStack 的 NBT 真的被解析过（`ItemStack(CompoundTag)` 构造 /
 * `setTag` ✓）才会带上组件 ✓；而 TConstruct 的工具**每 tick 都在写自己的 `tic_*` NBT** ✓
 * ⇒ 我们塞进根键 `Component` 的组件在客户端那份栈上很容易缺席（或被 `setTag` 重建冲掉 ✗）。
 * ⇒ 因此"不减速"这件事**已经改由我们自己的客户端混入**兜底 ✓
 * （{@code mixin/SpearChargeSlowdownMixin} ✓ 纯客户端 ✓ 不依赖任何数据同步 ✓）。
 * 本类保留 ✓：它符合原版语义、终焉图书馆在场时是"双保险" ✓、不在场时 no-op ✓。
 */
public final class EndingLibraryComponents {

    /** 终焉图书馆的 modid ✓ */
    public static final String MOD_ID = "ending_library";

    /** 组件根键 ✓（他们的 ItemComponentManager.HEAD ✓） */
    private static final String HEAD = "Component";
    /** {@code minecraft:use_effects} ✓ */
    private static final String USE_EFFECTS = "minecraft:use_effects";

    private static final String CAN_SPRINT = "can_sprint";
    private static final String SPEED_MULTIPLIER = "speed_multiplier";

    private EndingLibraryComponents() {}

    /** 终焉图书馆在不在场 ✓（不在就什么都不做 ⇒ 我们的东西照常能跑 ✓） */
    public static boolean available() {
        try {
            return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 确保这个物品栈带上了 {@code use_effects} 组件 ✓（已有就原样不动 ✓ 幂等 ✓）。
     *
     * @param canSprint       蓄力时能不能开始疾跑 ✓（原版长矛 = true ✓）
     * @param speedMultiplier 使用物品时的移动倍率 ✓（原版长矛 = 1.0 = 不减速 ✓）
     */
    public static void ensureUseEffects(ItemStack stack, boolean canSprint, float speedMultiplier) {
        if (stack == null || stack.isEmpty() || !available()) return;
        CompoundTag root = stack.getOrCreateTag();
        CompoundTag components = root.contains(HEAD, Tag.TAG_COMPOUND) ? root.getCompound(HEAD) : new CompoundTag();
        if (components.contains(USE_EFFECTS)) return;      // 已经有了 ⇒ 不覆盖 ✓（玩家/别的模组改过也尊重 ✓）

        CompoundTag value = new CompoundTag();
        value.putBoolean(CAN_SPRINT, canSprint);
        value.putFloat(SPEED_MULTIPLIER, speedMultiplier);
        components.put(USE_EFFECTS, value);
        root.put(HEAD, components);
    }

    /** 长矛专用：{@code use_effects = (can_sprint = true, speed_multiplier = 1.0)} ✓ ＝ 原版 1.21.11 长矛 ✓ */
    public static void ensureSpearUseEffects(ItemStack stack) {
        ensureUseEffects(stack, true, 1.0F);
    }
}
