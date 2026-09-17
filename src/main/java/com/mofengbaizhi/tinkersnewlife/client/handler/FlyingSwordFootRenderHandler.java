package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.handler.FlyingSwordCuriosHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 脚部飞剑的<b>客户端渲染</b>：飞起来时把飞剑画在玩家脚下（和玩家模型同一趟渲染 ✓）。
 *
 * <h2>为什么不再用"脚下实体"</h2>
 * 旧做法是一个真实体 {@code FlyingSwordFootEntity}：服务端**每 tick** {@code setPos} 跟着玩家跑、
 * 再把位置同步给所有客户端 ✗ —— 纯视觉的东西却持续占用 tick 与带宽（用户："持续瞬移很影响游戏tick" ✗）。
 * 现在：服务端**完全不生成**那个实体（登录时还会清理旧存档里的残留 ✓），
 * 客户端在玩家渲染事件里直接画 ✓ —— 位置永远跟模型严格一致（无插值延迟 ✓），服务端零开销 ✓。
 *
 * <h2>观感保持完全一致</h2>
 * 数学是从旧的 {@code FlyingSwordFootRenderer} 原样搬过来的（世界空间：模型默认前向
 * {@code (0.707,0.707,0)} → 立起 → 平躺 → 水平转到玩家朝向，缩放 1.2 ✗→✓ 不变），
 * 只是把"实体位置 {@code owner.y - 0.1}"换成了"玩家原点下移 0.1" ✓。
 * {@link RenderPlayerEvent.Post} 的 PoseStack 正好停在"已平移到实体位置、坐标轴与世界对齐"这一层 ✓，
 * 所以这套世界空间数学可以照用 ✓。
 *
 * <p>⚠ 第一人称下原版根本不渲染自己（事件不触发）→ 自己也看不到自己的脚下飞剑（旧实体版能看到）
 * —— 这是"跟模型画在一起"的固有效果，需要的话另说 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FlyingSwordFootRenderHandler {

    private FlyingSwordFootRenderHandler() {}

    /** 模型默认前向：Y偏X 45°，即 (0.707, 0.707, 0)（与旧渲染器一致 ✓） */
    private static final Vector3f DEFAULT_FORWARD = new Vector3f(0.707f, 0.707f, 0f);

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        // 只在"飞起来"时画（本地玩家看 abilities，别的玩家看是否离地 —— abilities 只同步给自己 ✓）
        boolean airborne = player.getAbilities().flying || !player.onGround();
        if (!airborne) return;

        ItemStack stack = FlyingSwordCuriosHandler.feetSwordOf(player);
        if (stack.isEmpty()) return;

        // --- 与旧 FlyingSwordFootRenderer 完全一致的方向计算（忽略俯仰的水平朝向 ✓）---
        Vec3 lookAngle = player.getLookAngle();
        Vector3f horizontalDir = new Vector3f((float) lookAngle.x, 0, (float) lookAngle.z);
        if (horizontalDir.lengthSquared() < 0.0001f) {
            horizontalDir.set(0, 0, -1);   // 备选朝北
        }
        horizontalDir.normalize();

        Quaternionf fixQuat = new Quaternionf().rotateTo(DEFAULT_FORWARD, new Vector3f(0, 1, 0));
        Quaternionf layQuat = new Quaternionf().rotateTo(new Vector3f(0, 1, 0), new Vector3f(0, 0, 1));
        Quaternionf horizontalQuat = new Quaternionf().rotateTo(new Vector3f(0, 0, 1), horizontalDir);
        Quaternionf finalQuat = new Quaternionf(horizontalQuat).mul(layQuat).mul(fixQuat);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        // 旧实体就生成在 owner.y - 0.1 ✓（1.20.1 是 y - 0.1，1.21+ 才有 eyeHeight 变化）
        poseStack.translate(0.0, -0.1, 0.0);
        poseStack.mulPose(finalQuat);
        poseStack.scale(1.2f, 1.2f, 1.2f);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ItemDisplayContext.NONE, event.getPackedLight(), OverlayTexture.NO_OVERLAY,
                poseStack, event.getMultiBufferSource(), player.level(), player.getId());
        poseStack.popPose();
    }
}
