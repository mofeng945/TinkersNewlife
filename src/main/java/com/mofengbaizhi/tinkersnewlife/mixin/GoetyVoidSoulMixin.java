package com.mofengbaizhi.tinkersnewlife.mixin;

import com.Polarice3.Goety.api.magic.ISpell;
import com.mofengbaizhi.tinkersnewlife.content.modifier.events.VoidGraceHandler;
import com.mofengbaizhi.tinkersnewlife.util.VoidArmorDiag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * <b>虚无恩宠 → 虚空法术的灵魂能量减半</b>（虚空金属盔甲特性）。
 *
 * <h2>为什么挂在这里</h2>
 * 诡厄的灵魂消耗链路（javap 反编译两版实核 ✓ 2.5.54.5 与 2.5.57.3 完全同构 ✓）：
 * <pre>
 *   DarkWand（黑暗法杖）
 *     ├─ setSpellConditions(...)  偏移 20  → ISpell.soulCost(caster, stack)   ← 把消耗写进法杖 NBT ✓
 *     ├─ m_7203_（= 原版 Item#use）偏移 353 → ISpell.soulCost(caster, stack)   ← 施法入口 ✓
 *     └─ m_5929_（= 原版 Item#onUseTick）偏移 406 → ISpell.soulCost(...)        ← 引导型法术每 tick ✓
 *   ISpell.soulCost(caster, stack)（接口默认方法）
 *     └─ SoulCalculation(caster) → defaultSoulCost() * SoulCostUp() * （各学派折扣，虚空那条是 VoidSoulDiscount ✓）
 * </pre>
 * ⇒ 直接把 {@code soulCost(...)} 的<b>返回值</b>砍一半最准 ✓：此时它已经算完所有其它折扣 ✓；
 * 而且<b>三处一起改</b>才自洽 ✓（写进 NBT 的值、施法判定用的值、引导每 tick 的值 ✓ 口径一致 ✓）。
 *
 * <h2>命中条件（两个都要满足 ✓）</h2>
 * <ol>
 *   <li>施法者身上有「虚无恩宠」护甲特性（{@link VoidGraceHandler#wears} ✓）；</li>
 *   <li>这个法术是<b>虚空系</b>（{@link VoidGraceHandler#isVoidSpell} 反射读诡厄
 *       {@code ISpell#getSpellTypes()} ✓，与阶梯自己的 {@code isVoidSpellDamage} 同一口径 ✓）。</li>
 * </ol>
 * 最低保底 1 点灵魂 ✓（避免 0 消耗导致 UI/扣费口径异常 ✓）。
 *
 * <h2>⚠⚠ 血泪史：这条特性从 §723 做到 §753 才真正生效 ✗</h2>
 * <ol>
 *   <li><b>§723</b>：写成<b>接口 mixin</b>（{@code @Mixin(targets="…ISpell")}）注入
 *       {@code SoulCalculation} ✗；</li>
 *   <li><b>§752</b>：日志实证 {@code InvalidInterfaceMixinException: Interface mixin contains a
 *       non-public method!} ✗ ⇒ 把 handler 从 {@code private} 改成 {@code default}（Java 里接口方法
 *       不能「public ＋ 带方法体」✗，而 {@code default} 本身就是 public ✓）；</li>
 *   <li><b>§753</b>：再测 ⇒ 换成 {@code InvalidInterfaceMixinException: …is not supported on
 *       interface mixin method handler$…} ✗ —— <b>Mixin 0.8.5 根本不支持在接口 mixin 里用
 *       {@code @Inject}／{@code @Redirect}</b> ✗（{@code MixinApplicatorInterface#prepareInjections}
 *       直接抛异常 ✓，与 public／default 无关 ✗）。
 *       ⇒ 只能<b>改注入点到实现类</b>：{@code DarkWand} 是唯一调用 {@code ISpell.soulCost} 的类 ✓
 *       （全 jar 扫过：只有 {@code ISpell} 自己和 {@code WardingCharmItem} 提到
 *       {@code SoulCalculation} ✓ 而法杖才是扣灵魂的地方 ✓）。</li>
 * </ol>
 *
 * <h2>⚠ 方法名必须写「运行时」的名字（SRG）</h2>
 * 发布的模组 jar 里，**覆盖了原版方法的那些成员会被 reobf 成 SRG 名** ✓：
 * {@code DarkWand} 的 {@code use} → {@code m_7203_} ✓、{@code onUseTick} → {@code m_5929_} ✓
 * （javap 实测两版都是这个名 ✓）；而诡厄自己独有的方法保持官方名 ✓ ——
 * 所以这里 {@code method = {"setSpellConditions", "m_7203_", "m_5929_"}} 混着写 ✓，
 * 并且整条注解 {@code remap = false} ✓（同 {@code SuperTierEffectMixin} 的口径 ✓）。
 */
@Mixin(targets = "com.Polarice3.Goety.common.items.magic.DarkWand", remap = false)
public class GoetyVoidSoulMixin {

    /**
     * 把「虚空法术的灵魂消耗」按 {@link VoidGraceHandler#VOID_SOUL_COST_FACTOR} 打折 ✓。
     *
     * <p>⚠ handler 形参必须是 {@code (接收者, 原参数…)} 的<b>精确类型</b> ✓：
     * 接收者是 {@code ISpell} ✓，随后是原方法的两个参数 ✓。
     * <p>⚠ 里面调 {@code spell.soulCost(caster, stack)} 走的是<b>原始接口方法</b> ✓ 不会递归 ✓。
     */
    @Redirect(
            method = {"setSpellConditions", "m_7203_", "m_5929_"},
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/Polarice3/Goety/api/magic/ISpell;soulCost(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)I"),
            remap = false)
    private int tinkersnewlife$halveVoidSoulCost(ISpell spell, LivingEntity caster, ItemStack stack) {
        int base = spell.soulCost(caster, stack);
        try {
            if (base > 1 && caster != null
                    && VoidGraceHandler.wears(caster)
                    && VoidGraceHandler.isVoidSpell(spell)) {
                int halved = Math.max(1, (int) Math.round(base * VoidGraceHandler.VOID_SOUL_COST_FACTOR));
                // §753 诊断：确证"这条注入真的被调用了" ✓（排查完 VoidArmorDiag.ENABLED=false 一起静音 ✓）
                VoidArmorDiag.log("soul:halve", "🌀 虚空法术灵魂消耗减半 {} → {} ✓（法杖 {} / 施法者 {}）",
                        base, halved, stack.getItem(), caster.getName().getString());
                return halved;
            }
        } catch (Throwable ignored) {
            // 折扣算不出来就按原价 ✓ 绝不许因为这条把施法搞崩 ✗
        }
        return base;
    }
}
