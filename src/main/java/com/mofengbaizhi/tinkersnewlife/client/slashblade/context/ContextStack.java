package com.mofengbaizhi.tinkersnewlife.client.slashblade.context;

// 移植自 TiCEX (MIT): moffy.ticex.lib.context.ContextStack

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 渲染期上下文栈（逐字移植 TiCEX ✓）。
 *
 * <p>用途：拔刀剑的渲染是"本体渲染器 → 我们的包装器 → 本体的 renderOverrided"这样一层套一层 ✓，
 * 中途需要把"当前正在渲染的（物品栈/显示上下文）""要替换的顶点缓冲""要贴的花纹精灵""颜色覆盖"传下去 ✓
 * ⇒ 用"栈 + try-with-resources 成对开关"最省事 ✓（{@code open(...)} 返回的 frame 在 close 时自动弹出 ✓）。
 *
 * <p>⚠ 它是**渲染线程专用** ✓（非线程安全，与原版一致 ✓）；只在客户端类里出现 ✓。
 */
@SuppressWarnings("resource")
public class ContextStack<T> {

    private final Deque<ContextFrame<T>> localDeque;
    private final T defaultValue;

    public ContextStack() {
        this(null);
    }

    public ContextStack(T defaultValue) {
        this.localDeque = new ArrayDeque<>();
        this.defaultValue = defaultValue;
    }

    public T get() {
        ContextFrame<T> local = localDeque.peek();
        if (local != null) {
            return local.get();
        }
        return defaultValue;
    }

    public void close(ContextFrame<T> local) {
        if (localDeque.peek() == local) {
            localDeque.pop();
        }
    }

    public ContextFrame<T> open(T object) {
        ContextFrame<T> local = new ContextFrame<>(this, object);
        localDeque.push(local);
        return local;
    }
}
