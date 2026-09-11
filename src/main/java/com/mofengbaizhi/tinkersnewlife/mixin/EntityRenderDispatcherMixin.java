package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.data.ClientWuWeiData;
import com.mofengbaizhi.tinkersnewlife.client.renderer.WuWeiDisguiseRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 无为转变·伪装渲染拦截（<b>YSM 兼容的关键</b>）。
 *
 * <p>为什么必须在 {@link EntityRenderDispatcher#render} 这一层拦截：
 * <ul>
 *   <li>我们原先只挂 {@code RenderPlayerEvent.Pre}——那是 <b>更下层</b>（在 {@code PlayerRenderer.render} 内）。</li>
 *   <li>是，史蒂夫模型（YSM）自己 mixin 了 {@code EntityRenderDispatcher#render} 来接管玩家渲染，
 *       于是原版 {@code PlayerRenderer} 这条链在 YSM 开启时根本走不到，我们的取消与改绘被整条绕过
 *       （实测：关闭 YSM 渲染即恢复正常）。</li>
 *   <li>YSM 也 <b>从不检查 {@code isInvisible} / {@code isSpectator}</b>（其 955 个类中 0 处引用），
 *       所以"客户端隐身"之类的标志位绕法同样无效。</li>
 * </ul>
 *
 * <p>做法：在 {@code render} 的 HEAD 处拦截。若被渲染者是「正在伪装中的玩家」，就按与调度器相同的
 * 位移把「生物渲染代理」画出来，然后 {@code ci.cancel()}——原版与 YSM 的玩家模型都不会再画，
 * 从而无论装了什么都只显示目标生物外观。
 *
 * <p>安全性：<b>只在游戏中确实存在伪装玩家时才生效</b>，平时完全不介入；整个方法体包在 try/catch 里，
 * 任何异常都直接退回原渲染。另受配置 {@code wuwei_disguise/enable_disguise_render} 控制，可随时关闭。
 */
@Mixin(value = EntityRenderDispatcher.class, priority = 1500)
public abstract class EntityRenderDispatcherMixin {

    /** 只记录一次"已生效"日志，便于排查 */
    private static boolean tinkersnewlife$logged = false;
    /** 诊断用：Hook 是否确实被调用过（只打一次） */
    private static boolean tinkersnewlife$hookLogged = false;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void tinkersnewlife$replaceDisguisedPlayer(Entity entity, double x, double y, double z,
                                                       float rotationYaw, float partialTicks,
                                                       PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                                       CallbackInfo ci) {
        try {
            if (!(entity instanceof Player player)) return;
            boolean disguised = ClientWuWeiData.isDisguised(player.getUUID());
            // ⭐ 诊断：证明本 Hook 确实在跑（同时给出"客户端是否已知该玩家伪装"）
            if (!tinkersnewlife$hookLogged) {
                tinkersnewlife$hookLogged = true;
                TinkersNewlife.LOGGER.info("[WuWei] 渲染 Hook 已生效（EntityRenderDispatcher.render HEAD）：玩家={} 客户端已知伪装={}",
                        player.getName().getString(), disguised);
            }
            if (!disguised) return;
            // 配置开关（默认开）：与 YSM 等模组冲突时可关闭本替换
            if (!com.mofengbaizhi.tinkersnewlife.config.ModConfig.WUWEI_DISGUISE_RENDER.get()) return;

            Entity proxy = ClientWuWeiData.getOrCreateProxy(player.getUUID(), player);
            if (!(proxy instanceof LivingEntity proxyLiving)) {   // 代理不可用 → 交回原渲染
                TinkersNewlife.LOGGER.warn("[WuWei] 伪装代理创建失败（玩家={} 形态={}），已回退原渲染",
                        player.getName().getString(), ClientWuWeiData.getDisguise(player.getUUID()));
                return;
            }

            EntityRenderDispatcher dispatcher = (EntityRenderDispatcher) (Object) this;
            EntityRenderer<? super Entity> renderer = dispatcher.getRenderer(proxy);
            if (renderer == null) return;

            WuWeiDisguiseRenderer.syncProxy(player, proxyLiving);

            Vec3 offset = renderer.getRenderOffset(proxy, partialTicks);
            poseStack.pushPose();
            poseStack.translate(x + offset.x, y + offset.y, z + offset.z);
            renderer.render(proxy, rotationYaw, partialTicks, poseStack, buffer, packedLight);
            poseStack.popPose();

            if (!tinkersnewlife$logged) {
                tinkersnewlife$logged = true;
                TinkersNewlife.LOGGER.info("[WuWei] 伪装渲染替换已生效（EntityRenderDispatcher Mixin，兼容 YSM）");
            }
            // 取消原渲染：原版玩家模型与 YSM 模型都不再绘制
            ci.cancel();
        } catch (Throwable t) {
            // 任何异常都退回原渲染，绝不影响正常游戏
            TinkersNewlife.LOGGER.warn("[WuWei] 伪装渲染替换异常，已回退原渲染: {}", t.toString());
        }
    }
}
