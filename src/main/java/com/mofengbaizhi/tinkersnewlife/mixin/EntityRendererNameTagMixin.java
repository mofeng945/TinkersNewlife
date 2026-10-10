package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.item.CognitiveMaskItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * §798 <b>认知阻碍面具：把"真正画名字/背景"的那一步直接掐掉</b> ✓
 *
 * <h2>为什么光挂 {@code RenderNameTagEvent} 不够（NL / 用户实测 ✗）</h2>
 * Forge 的事件挂在 {@code EntityRenderer#m_7392_}（渲染入口 ✓）里（补丁原文实核 ✓）：
 * <pre>
 *   var e = new RenderNameTagEvent(...); MinecraftForge.EVENT_BUS.post(e);
 *   if (e.getResult() != DENY && (e.getResult() == ALLOW || this.m_6512_(entity))) {
 *       this.m_7649_(entity, e.getContent(), ...);   // ← 真正画名字（含那条半透明背景）在这里 ✓
 *   }
 * </pre>
 * ⇒ 只要某个模组**自己重写 {@code m_7392_} 并直接调 {@code m_7649_}**，事件就被绕过 ✗。
 *
 * <p>实测正是如此 ✓：<b>是，史蒂夫模型（YSM 2.6.5）</b>的
 * {@code com.elfmcys.yesstevemodel.o0ooO0o0ooo000OOO0o0oo0o}
 * 同时重写了 {@code m_6512_}（{@code shouldShowName}）、{@code m_7392_}（渲染入口）**和**
 * {@code m_7649_}（{@code renderNameTag}）✓（javap 实核 ✓），并在自己的 {@code m_7649_} 里
 * **两次** {@code invokespecial super.m_7649_} ✓ ⇒ 用户看到的就是那两遍
 * {@code drawInBatch} 留下的**半透明背景条** ✗（名字被我们挡掉、底板还在 ✓）。
 * <p>补一句：原版自己也是"两遍画"——一遍 {@code SEE_THROUGH} 带背景（穿墙可见 ✓）、
 * 一遍 {@code NORMAL} 不带背景（被方块挡住就看不见 ✓）⇒ 与用户描述的"有遮挡时只剩背景条"完全吻合 ✓。
 *
 * <h2>做法：拦最底层那一步 ✓</h2>
 * 在 {@code EntityRenderer#renderNameTag}（SRG {@code m_7649_}）的 HEAD 处，
 * 只要是**戴着面具的玩家**就 {@code ci.cancel()} ✓ ——
 * 不管调用者是原版、YSM、还是别的什么模组 ✗，最终都要走这个方法 ✓
 * （YSM 也是 {@code super.m_7649_} ✓）⇒ 一次拦住全部 ✓，连背景一起不画 ✓。
 *
 * <ul>
 *   <li>客户端专用 ✓（{@code EntityRenderer} 本来就是客户端类 ✓），登记在
 *       {@code tinkersnewlife.mixins.json} 的 {@code client} 数组 ✓；</li>
 *   <li>写法照抄本项目既有的原版目标混入（{@code EntityRenderDispatcherMixin} / {@code ItemRendererMixin} ✓）：
 *       <b>MCP 名一份</b>（开发环境 ✓）＋ <b>SRG 名一份 ＋ {@code remap = false}</b>（生产环境 ✓）
 *       —— 手写 refmap 里没有这条，靠 SRG 那份才在生产环境**真正生效** ✓（§753 的教训 ✓）；</li>
 *   <li>SRG 那份带 {@code require = 1} ✓ ⇒ 万一将来方法名变了，**启动就明确报错** ✗
 *       而不是静默失效 ✓。</li>
 * </ul>
 */
@Mixin(value = EntityRenderer.class, priority = 1500)
public class EntityRendererNameTagMixin {

    /** 开发环境（MCP 名）✓ 生产环境没有这个名字 ⇒ 默认 require=0 静默跳过 ✓ */
    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    private void tinkersnewlife$hideMaskedNameDev(Entity entity, Component name, PoseStack poseStack,
                                                  MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        hide(entity, ci);
    }

    /** 生产环境（SRG 名 {@code m_7649_}）✓ —— 真正生效的那一份 ✓ */
    @Inject(method = "m_7649_", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void tinkersnewlife$hideMaskedNameProd(Entity entity, Component name, PoseStack poseStack,
                                                   MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        hide(entity, ci);
    }

    private static void hide(Entity entity, CallbackInfo ci) {
        try {
            if (entity instanceof Player player && CognitiveMaskItem.isWorn(player)) {
                ci.cancel();          // 连背景一起不画 ✓
            }
        } catch (Throwable ignored) {
            // 查询失败就照常画 ✓ 绝不因为面具功能把渲染搞崩 ✓
        }
    }
}
