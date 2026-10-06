package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.client.search.FluidSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §1117m <b>熔炼炉 / 熔铸炉：给匠魂界面塞一个 MC 原生 `EditBox` 当搜索框</b> ✓。
 *
 * <h2>为什么要换做法 ✗（用户一句话点醒 ✓）</h2>
 * 用户说：「**我构筑术式不是也写过搜索框吗，为什么不能模仿**」✓
 * ⇒ 仓库里 `ConstructSelectScreen`（构筑术式 ✓）／`WuWeiScreen`／`QuantumVaultScreen`／`DimensionPassScreen`
 * **全都是标准 `EditBox`** ✓：`new EditBox(font, x, y, w, h, …)` ＋ `setResponder(…)` ＋ `addRenderableWidget(…)` ✓
 * ⇒ `EditBox` **自己**处理按键／字符／光标／退格／**输入法中文** ✓✓
 * ⇒ 我前面手搓的"SRG 按键码翻译 + `charTyped` 注入"8 轮**全是绕远路** ✗（中文永远进不来 ✓）。
 *
 * <h2>怎么塞进别人的界面 ✓（不用 shadow 原版方法 ✗）</h2>
 * `addRenderableWidget` 是原版 **protected** 方法 ✗（shadow 它要写混淆名 ✗ ⇒ 本仓已经因此丢过整个 mixin ✗ 见 §1117 ✓）；
 * 但 `Screen#children()` 是**公开**方法 ✓ ⇒ `screen.children().add(editBox)` ✓
 * ⇒ 原版的按键/字符派发本来就会遍历 `children()` ✓ ⇒ **输入、输入法、退格全部自动生效** ✓✓。
 * 渲染交给 {@code GuiSmelteryTankSearchMixin}（它有 `GuiGraphics` ＋ 流体列坐标 ✓）。
 *
 * <p>⚠ `m_181908_` 是**每 tick 都被调用**的方法 ✗（§1117k 实测 ✓）⇒ 创建逻辑必须**幂等** ✓
 * （已存在就不再建 ✓ 否则会每秒堆一个框 ✗）。
 */
@Mixin(targets = "slimeknights.tconstruct.smeltery.client.screen.HeatingStructureScreen")
public abstract class HeatingStructureScreenSearchMixin {

    @Inject(method = "m_181908_", at = @At("TAIL"), require = 1, remap = false)
    private void tnl$ensureSearchBox(CallbackInfo ci) {
        try {
            // ⚠ mixin 类的静态类型不是 Screen ✗ ⇒ 必须先经 Object 中转再 instanceof ✓（否则编译不过 ✓）
            if (!(((Object) this) instanceof Screen screen)) return;

            EditBox existing = FluidSearch.getEditBox();
            if (existing != null && screen.children().contains(existing)) {
                existing.setFocused(true);      // 保持聚焦 ⇒ 打开就能直接打字 ✓（不需要点 ✓）
                return;
            }

            EditBox box = new EditBox(Minecraft.getInstance().font, 0, 0, 106, 14, Component.literal("搜索"));
            box.setMaxLength(64);
            box.setValue(FluidSearch.getQuery());
            box.setResponder(FluidSearch::setQuery);   // 内容一变 ⇒ 立刻过滤 ✓（闭环 ✓）
            box.setFocused(true);
            // ⚠ `children()` 的返回类型带通配符 ✗ ⇒ 经 `List<?>` 中转再转成 GuiEventListener 列表 ✓（unchecked 但安全 ✓）
            @SuppressWarnings("unchecked")
            java.util.List<net.minecraft.client.gui.components.events.GuiEventListener> kids =
                    (java.util.List<net.minecraft.client.gui.components.events.GuiEventListener>)
                            (java.util.List<?>) screen.children();
            kids.add(box);                             // ⭐ 原版派发就会喂给它按键/字符/输入法 ✓
            FluidSearch.setEditBox(box);
            FluidSearch.diag("已创建 EditBox 搜索框（MC 原生 ✓ 支持输入法中文 ✓）");
        } catch (Throwable t) {
            FluidSearch.diag("创建 EditBox 失败: " + t);
        }
    }
}
