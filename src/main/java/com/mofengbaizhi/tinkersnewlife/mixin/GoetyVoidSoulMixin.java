package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.modifier.events.VoidGraceHandler;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>虚无恩宠 → 虚空法术的灵魂能量减半</b>（虚空金属盔甲特性）。
 *
 * <h2>为什么挂在这里</h2>
 * 诡厄的灵魂消耗链路（反编译确认 ✓）：
 * {@code ISpell#SoulCalculation(LivingEntity)} 先算 {@code defaultSoulCost() * SoulCostUp(caster)}，
 * 再按学派各自打折 —— 其中<b>虚空学派那一条</b>是 {@code VoidSoulDiscount(caster)}（默认查虚空法袍 ✓）。
 * ⇒ 我们既不能改它的法袍判定（那是"减 X%"的配置值 ✗），也不该去动 {@code SEHelper#decreaseSouls}
 * （那里已经不知道是哪个法术了 ✗，会连仪式/方块一起减 ✗）。
 * ⇒ 直接把 {@code SoulCalculation} 的<b>返回值</b>砍一半最准 ✓：此时它已经算完所有其它折扣 ✓。
 *
 * <h2>命中条件（两个都要满足 ✓）</h2>
 * <ol>
 *   <li>施法者身上有「虚无恩宠」护甲特性（{@link VoidGraceHandler#wears} ✓）；</li>
 *   <li>这个法术是<b>虚空系</b>（{@link VoidGraceHandler#isVoidSpell} 反射读诡厄
 *       {@code ISpell#getSpellTypes()} ✓，与阶梯自己的 {@code isVoidSpellDamage} 同一口径 ✓）。</li>
 * </ol>
 * 最低保底 1 点灵魂 ✓（避免 0 消耗导致 UI/扣费口径异常 ✓）。
 *
 * <p>⚠ 这是**接口默认方法**注入（{@code ISpell} 是接口 ✓）。Mixin 支持接口注入 ✓；
 * 万一某个版本不支持，配置里 {@code required:false} ⇒ 只会日志报错、不影响进游戏 ✓
 * （而且那时"减半"这条就不生效 —— 备忘录里记为"未实机验证"✓）。
 */
@Mixin(targets = "com.Polarice3.Goety.api.magic.ISpell", remap = false)
public interface GoetyVoidSoulMixin {

    @Inject(method = "SoulCalculation", at = @At("RETURN"), cancellable = true, remap = false, require = 1)
    // ⚠ §752：接口 mixin 的注入方法**必须是 public** ✗ —— 原来是 private ⇒ Mixin 直接拒绝加载 ✗
    //   （日志实证：InvalidInterfaceMixinException: Interface mixin contains a non-public method ✗）
    //   ⇒ 也就是说"虚空法术灵魂减半"这条从 §723 起**一直没生效** ✗，这次一并修好 ✓
    // ⚠ Java 里接口方法不能"public + 带方法体" ✗ ⇒ 用 **default** ✓（default 方法本身就是 public ✓
    //   正好满足 Mixin 对"接口 mixin 注入方法必须 public"的要求 ✓）
    default void tinkersnewlife$halveVoidSoulCost(LivingEntity caster,
                                                 CallbackInfoReturnable<Integer> cir) {
        if (caster == null) return;
        if (!VoidGraceHandler.wears(caster)) return;
        if (!VoidGraceHandler.isVoidSpell(this)) return;
        Integer base = cir.getReturnValue();
        if (base == null || base <= 1) return;
        cir.setReturnValue(Math.max(1, (int) Math.round(base * VoidGraceHandler.VOID_SOUL_COST_FACTOR)));
    }
}
