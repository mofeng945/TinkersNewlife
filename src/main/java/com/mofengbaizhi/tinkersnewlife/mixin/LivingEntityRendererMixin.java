package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.item.CognitiveMaskItem;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 双向认知阻碍面具：<b>被当作隐身、但身体照常渲染</b>。
 *
 * <h2>为什么必须动渲染层</h2>
 * 需求是"人物要能看见，但小地图不显示、无名字、无法被索敌"。MC 里这三件事和"看不见人"
 * <b>共用同一个开关</b> —— {@code Entity#isInvisible()}：
 * <ul>
 *   <li>索敌：{@code TargetingConditions} / 生物感知看它 ✗（正好是我们要的 ✓）；</li>
 *   <li>名牌：{@code EntityRenderer#shouldShowName} 里 {@code !entity.isInvisibleTo(player)} ✗（要的 ✓）；</li>
 *   <li>小地图雷达：Xaero's 的 {@code RadarStateUpdater#isInvisibleTo} 看的就是它，
 *       而且它那个"隐藏隐身实体"开关<b>默认是开的</b>
 *       （反编译 {@code MinimapProfiledConfigOptions.RADAR_HIDE_INVISIBLE}：{@code setDefaultValue(true)} ✓）✗（要的 ✓）；</li>
 *   <li>但身体渲染也看它 ✗ —— 所以不处理的话人就真隐身了（用户明确否掉 ✗）。</li>
 * </ul>
 *
 * <h2>本 mixin 做的唯一一件事</h2>
 * 把 {@code LivingEntityRenderer#isBodyVisible} 对"戴着面具的玩家"强制返回 {@code true} ——
 * 于是 {@code isInvisible()} 依然是 true（雷达/名牌/索敌全都按隐身处理 ✓），
 * 但模型照常实体渲染 ✓✓。**纯客户端、纯渲染**，不改任何数值、不同步任何数据 ✓。
 *
 * <p>另外两条不碰：{@code HumanoidArmorLayer} 本来就照常画盔甲/手持物 ✓；
 * 若玩家另喝了隐身药水（{@code MobEffects.INVISIBILITY}），药水优先、人照样看不见 ✓
 * （免得"面具把药水顶掉"这种更离谱的事 ✗）。
 *
 * <p>双注入 = 本模组既有的写法（见 {@code HumanoidArmorLayerMixin}）：官方名给开发环境、
 * SRG 名给生产环境（本模组未启用 Mixin 注解处理器，没有 refmap 兜底）。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    /** 官方名注入（开发环境生效） */
    @Inject(method = "isBodyVisible", at = @At("HEAD"), cancellable = true)
    private void tinkersnewlife$maskStillVisible(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (shouldRenderDespiteInvisible(entity)) {
            cir.setReturnValue(true);
        }
    }

    /** 兜底注入：直连 SRG 方法名（{@code isBodyVisible} = {@code m_5933_}），绕开 refmap */
    @Inject(method = "m_5933_", at = @At("HEAD"), cancellable = true, remap = false)
    private void tinkersnewlife$maskStillVisibleSrg(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (shouldRenderDespiteInvisible(entity)) {
            cir.setReturnValue(true);
        }
    }

    /** 戴着认知阻碍面具 → 即使被标记为隐身也照常画身体（另有效果类隐身时不顶替 ✓） */
    private static boolean shouldRenderDespiteInvisible(LivingEntity entity) {
        return entity instanceof Player player
                && player.isInvisible()
                && !player.hasEffect(MobEffects.INVISIBILITY)
                && CognitiveMaskItem.isWorn(player);
    }
}
