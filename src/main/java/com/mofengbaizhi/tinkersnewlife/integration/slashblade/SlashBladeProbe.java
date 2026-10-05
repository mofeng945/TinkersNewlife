package com.mofengbaizhi.tinkersnewlife.integration.slashblade;

import mods.flammpfeil.slashblade.capability.slashblade.SimpleBladeStateCapabilityProvider;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * <b>§997 探针</b>：给匠魂工具挂上拔刀剑的刀状态（{@code CapabilitySlashBlade.BLADESTATE}），
 * 并在**别人来查**时打日志与调用栈 ⇒ 验证「深度挂接（B）」这条路走不走得通。
 *
 * <h2>怎么用（用户实机步骤）</h2>
 * <ol>
 *   <li>进游戏（两个包里都有拔刀剑 ✓）；</li>
 *   <li><b>左键砍怪 / 右键蓄力</b> 拿着<b>西洋剑</b>试几下（也可以直接打开物品栏把鼠标停在上面 ✓）；</li>
 *   <li>看日志里有没有 <b>{@code [探针] 有人来查刀状态了}</b> 这行 ✓；
 *       有 ⇒ 它**会**查外来物品 ✓ ⇒ B 可行 ✓；没有 ⇒ B 不可行 ✗ ⇒ 回头走 C/A ✓；</li>
 *   <li>有的话把紧跟着的那段 <b>调用栈</b> 发我 ✓（我能据此知道是它的哪个类在查 ✓ 以及还要补什么 ✓）。</li>
 * </ol>
 *
 * <p>⚠ 实现上的两个要点：
 * <ul>
 *   <li>挂载走 {@code Item#initCapabilities}（物品栈级能力 ✓ 每个栈一份状态 ✓）—— 比
 *       {@code RegisterCapabilitiesEvent#registerItem} 好：后者是**整类共享一个 provider** ✗ 状态会串 ✗；</li>
 *   <li>本类里所有拔刀剑的类型**只出现在方法内部** ✓（字段/父类都不引 ✗）⇒ 拔刀剑不在场时
 *       本类被加载也不会解析它的类型 ✓，{@link #initCapabilities} 里靠 {@code enabled} 直接返回 null ✓。</li>
 * </ul>
 */
public final class SlashBladeProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger("TinkersNewlife/SlashBladeProbe");

    private SlashBladeProbe() {}

    /** 是否装了探针（= 拔刀剑在场 ✓ 由 {@link SlashBladeIntegration#register} 打开 ✓） */
    private static volatile boolean enabled = false;

    /** 被查次数（日志只打前若干次，免得刷屏 ✗） */
    private static final AtomicInteger QUERIES = new AtomicInteger();
    /** 已打过的调用栈数量（只打前 3 次 ✓） */
    private static final AtomicInteger TRACES = new AtomicInteger();

    public static void enable() {
        enabled = true;
    }

    public static void disable() {
        enabled = false;
    }

    /**
     * 由 {@code RapierItem#initCapabilities} 调用（§997 探针 ✓ 临时）：
     * 返回一个"转发给拔刀剑自己的 Provider ＋ 打日志"的能力提供者 ✓。
     *
     * @return 拔刀剑不在场 / 探针没开 ⇒ {@code null}（＝完全不影响原行为 ✓）
     */
    public static ICapabilityProvider initCapabilities(ItemStack stack, CompoundTag nbt) {
        if (!enabled) return null;
        try {
            return new LoggingProvider(stack);
        } catch (Throwable t) {
            LOGGER.warn("[探针] 挂载失败（已忽略）：{}", t.toString());
            return null;
        }
    }

    /** 转发给拔刀剑本体的 Provider，顺便统计"谁在查" */
    private static final class LoggingProvider implements ICapabilitySerializable<CompoundTag> {

        private final SimpleBladeStateCapabilityProvider delegate;

        LoggingProvider(ItemStack stack) {
            // 探针用本体默认刀的模型/贴图（先随便给 ✓ 只为让状态能建起来 ✓）
            this.delegate = new SimpleBladeStateCapabilityProvider(stack,
                    new ResourceLocation("slashblade", "model/blade.obj"),
                    new ResourceLocation("slashblade", "model/blade.png"),
                    4.0F, 0);
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
            LazyOptional<T> result = delegate.getCapability(capability, side);
            try {
                if (result.isPresent()) {
                    int n = QUERIES.incrementAndGet();
                    if (n <= 20) {
                        LOGGER.info("[探针] 有人来查刀状态了（第 {} 次）—— 说明拔刀剑**会**查外来物品 ✓", n);
                    }
                    if (n <= 3) {
                        LOGGER.info("[探针] 调用栈：\n{}", trace());
                    }
                }
            } catch (Throwable ignored) {
                // 日志出错不影响能力本身 ✓
            }
            return result;
        }

        @Override
        public CompoundTag serializeNBT() {
            return delegate.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            delegate.deserializeNBT(tag);
        }

        private static String trace() {
            StringBuilder sb = new StringBuilder();
            StackTraceElement[] st = new Throwable().getStackTrace();
            for (int i = 0; i < st.length && i < 25; i++) {
                sb.append("        at ").append(st[i]).append('\n');
            }
            return sb.toString();
        }
    }
}
