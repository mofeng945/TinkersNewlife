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

    /** 已打印过"伪装替换成功"日志的玩家（每玩家一条，便于确认 Hook 真的在跑） */
    private static final java.util.Set<java.util.UUID> tinkersnewlife$logged = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** 诊断用：Hook 是否确实被调用过（只打一次） */
    private static boolean tinkersnewlife$hookLogged = false;


    // ⭐ 两个 HEAD 注入器功能完全相同（都调用 tinkersnewlife$replace）：
    //   ① 用 MCP 名 render + refmap 解析成 SRG 名；
    //   ② 直接用 SRG 名 m_114384_ 且 remap=false，完全绕开 refmap。
    //   原因：曾因手写 refmap 用「点号类名」，而 Mixin 按「斜杠内部名」查表 → 查不到 →
    //   退回字面量 "render"（生产环境没有这个名字）→ 注入静默失败（require=0），
    //   表现就是伪装渲染完全不生效、日志里连 Hook 行都没有。现在两个名字至少一个能命中；
    //   万一都命中，也只是多画一次同一个代理模型，视觉无差别。
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void tinkersnewlife$replaceDisguisedPlayer(Entity entity, double x, double y, double z,
                                                       float rotationYaw, float partialTicks,
                                                       PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                                       CallbackInfo ci) {
        tinkersnewlife$replace(entity, x, y, z, rotationYaw, partialTicks, poseStack, buffer, packedLight, ci);
    }

    /** 兜底注入：直连 SRG 方法名，绕开 refmap（见上） */
    @Inject(method = "m_114384_", at = @At("HEAD"), cancellable = true, remap = false)
    private void tinkersnewlife$replaceDisguisedPlayerSrg(Entity entity, double x, double y, double z,
                                                          float rotationYaw, float partialTicks,
                                                          PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                                          CallbackInfo ci) {
        tinkersnewlife$replace(entity, x, y, z, rotationYaw, partialTicks, poseStack, buffer, packedLight, ci);
    }

    private void tinkersnewlife$replace(Entity entity, double x, double y, double z,
                                        float rotationYaw, float partialTicks,
                                        PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                        CallbackInfo ci) {
        try {
            // ⭐ 非玩家：被无为转变"原地换形态"的可操控单位（傀儡操术的傀儡 / 黑鸟操术的黑鸟等）。
            //    这类单位不能删掉重建（操控链路是"相机绑实体 id + 输入包"），所以保留实体、只换渲染。
            if (!(entity instanceof Player) && entity instanceof LivingEntity livingMob) {
                if (tinkersnewlife$replaceMob(livingMob, x, y, z, rotationYaw, partialTicks,
                        poseStack, buffer, packedLight, ci)) {
                    return;
                }
            }
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

            if (tinkersnewlife$logged.add(player.getUUID())) {
                TinkersNewlife.LOGGER.info("[WuWei] 伪装渲染替换成功：玩家={} 形态={}",
                        player.getName().getString(), proxy.getType().getDescription().getString());
            }
            // 取消原渲染：原版玩家模型与 YSM 模型都不再绘制
            ci.cancel();
        } catch (Throwable t) {
            // 任何异常都退回原渲染，绝不影响正常游戏
            TinkersNewlife.LOGGER.warn("[WuWei] 伪装渲染替换异常，已回退原渲染: {}", t.toString());
        }
    }

    /**
     * 非玩家实体（可操控单位）的形态替换：查客户端"实体形态伪装"表，命中就用渲染代理画目标生物。
     *
     * @return true 表示已接管并取消了原渲染
     */
    private boolean tinkersnewlife$replaceMob(LivingEntity living, double x, double y, double z,
                                              float rotationYaw, float partialTicks,
                                              PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                              CallbackInfo ci) {
        if (ClientWuWeiData.getMobDisguise(living.getUUID()).isEmpty()) return false;
        if (!com.mofengbaizhi.tinkersnewlife.config.ModConfig.WUWEI_DISGUISE_RENDER.get()) return false;
        Entity proxy = ClientWuWeiData.getOrCreateMobProxy(living.getUUID(), living);
        if (!(proxy instanceof LivingEntity proxyLiving)) return false;
        EntityRenderDispatcher dispatcher = (EntityRenderDispatcher) (Object) this;
        EntityRenderer<? super Entity> renderer = dispatcher.getRenderer(proxy);
        if (renderer == null) return false;
        WuWeiDisguiseRenderer.syncProxy(living, proxyLiving);
        Vec3 offset = renderer.getRenderOffset(proxy, partialTicks);
        poseStack.pushPose();
        poseStack.translate(x + offset.x, y + offset.y, z + offset.z);
        renderer.render(proxy, rotationYaw, partialTicks, poseStack, buffer, packedLight);
        poseStack.popPose();
        ci.cancel();
        return true;
    }
}
