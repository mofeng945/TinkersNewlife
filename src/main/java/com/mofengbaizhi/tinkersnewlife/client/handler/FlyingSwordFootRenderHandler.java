package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.handler.FlyingSwordCuriosHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 脚部飞剑的<b>客户端渲染</b>：飞起来时把飞剑画在玩家脚下、跟着玩家模型走。
 *
 * <h2>为什么不再用"脚下实体"</h2>
 * 旧做法是一个真实体 {@code FlyingSwordFootEntity}：服务端**每 tick** {@code setPos} 跟着玩家跑、
 * 再把位置同步给所有客户端 ✗ —— 纯视觉的东西却持续占用 tick 与带宽（"持续瞬移很影响游戏tick" ✗）。
 * 现在服务端不生成它（登录时还会清理旧存档残留 ✓），客户端自己画 ✓。
 *
 * <h2>两条渲染路径</h2>
 * <ul>
 *   <li><b>第三人称 / 他人视角</b>：{@link RenderPlayerEvent.Post} —— 就在原版玩家渲染那一趟里、
 *       模型画完之后 ✓，PoseStack 正停在"已平移到该玩家"那一层 ✓，所以只下移 0.1 就到脚下 ✓
 *       （与模型同帧同源 ✓，这就是"跟模型画在一起" ✓）；</li>
 *   <li><b>第一人称</b>：原版**不渲染自己**（上面那个事件不触发 ✗）→ 另走
 *       {@link RenderLevelStageEvent}（{@code AFTER_ENTITIES}）补一趟，只画自己 ✓。</li>
 * </ul>
 * ⚠ 接管玩家渲染的模组（如 YSM）会替换 {@code LivingEntityRenderer#render} → 上面第一条自然不生效，
 * 也就是"YSM 下看不见" —— 用户明确接受这一点（原版模型能画出来就行 ✓）。
 *
 * <h2>⭐ 必须用"插值"位置与朝向（"一顿一顿"的根因）</h2>
 * 相机与玩家模型都是按 {@code partialTick} 插值渲染的；如果直接用 {@code getX()/getY()/getZ()}
 * （整 tick 的位置），剑最多会落后一 tick 的距离 ⇒ 快速飞行时看起来一跳一跳 ✗（用户实测）。
 * 所以这里位置用 {@code Mth.lerp(partialTick, xOld, x)}、朝向用
 * {@link Player#getViewVector(float)}（插值视向量）—— 与模型完全同口径 ✓。
 *
 * <p>朝向数学是从旧的 {@code FlyingSwordFootRenderer} 原样搬的（世界空间：模型默认前向
 * {@code (0.707,0.707,0)} → 立起 → 平躺 → 水平转到玩家朝向，缩放 1.2 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FlyingSwordFootRenderHandler {

    private FlyingSwordFootRenderHandler() {}

    /** 模型默认前向：Y偏X 45°，即 (0.707, 0.707, 0)（与旧渲染器一致 ✓） */
    private static final Vector3f DEFAULT_FORWARD = new Vector3f(0.707f, 0.707f, 0f);

    /** 飞剑离脚底多高（旧实体就生成在 owner.y - 0.1 ✓） */
    private static final double FOOT_OFFSET_Y = -0.1;

    /** 第三人称 / 他人视角：跟原版玩家模型同一趟渲染 ✓ */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        if (!airborne(player)) return;
        ItemStack stack = FlyingSwordCuriosHandler.feetSwordOf(player);
        if (stack.isEmpty()) return;

        float partialTick = event.getPartialTick();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        // 该事件里 PoseStack 的原点已经平移到玩家（脚底）✓ → 只下移 0.1 ✓
        poseStack.translate(0.0, FOOT_OFFSET_Y, 0.0);
        applyOrientation(poseStack, player, partialTick);
        drawSword(poseStack, event.getMultiBufferSource(), event.getPackedLight(), stack, player);
        poseStack.popPose();
    }

    /**
     * 第一人称：原版不渲染自己 → 在世界渲染阶段补一趟（只画自己，且只在第一人称，
     * 免得第三人称被画两把 ✗）。
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.getCameraType().isFirstPerson()) return;   // 第三人称交给上面那趟 ✓
        ClientLevel level = mc.level;
        Player player = mc.player;
        if (level == null || player == null || !airborne(player)) return;
        ItemStack stack = FlyingSwordCuriosHandler.feetSwordOf(player);
        if (stack.isEmpty()) return;

        float partialTick = event.getPartialTick();
        // ⭐ 插值位置：相机是插值的，这里不插值就会"一顿一顿" ✗
        double x = Mth.lerp(partialTick, player.xOld, player.getX());
        double y = Mth.lerp(partialTick, player.yOld, player.getY());
        double z = Mth.lerp(partialTick, player.zOld, player.getZ());
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(x - cam.x, y + FOOT_OFFSET_Y - cam.y, z - cam.z);
        applyOrientation(poseStack, player, partialTick);
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
        drawSword(poseStack, mc.renderBuffers().bufferSource(), light, stack, player);
        poseStack.popPose();
    }

    /** 只在"飞起来"时画（自己的 abilities 精确 ✓；别人的 abilities 不同步 → 用是否离地判断 ✓） */
    private static boolean airborne(Player player) {
        return player.getAbilities().flying || !player.onGround();
    }

    /**
     * 旧 {@code FlyingSwordFootRenderer} 的朝向数学：立起 → 平躺 → 水平转到玩家朝向 + 缩放 1.2 ✓。
     * <p>朝向取**插值**视向量 —— 否则转身时剑会跟着"跳" ✗。
     */
    private static void applyOrientation(PoseStack poseStack, Player player, float partialTick) {
        Vec3 lookAngle = player.getViewVector(partialTick);
        Vector3f horizontalDir = new Vector3f((float) lookAngle.x, 0, (float) lookAngle.z);
        if (horizontalDir.lengthSquared() < 0.0001f) {
            horizontalDir.set(0, 0, -1);   // 备选朝北
        }
        horizontalDir.normalize();

        Quaternionf fixQuat = new Quaternionf().rotateTo(DEFAULT_FORWARD, new Vector3f(0, 1, 0));
        Quaternionf layQuat = new Quaternionf().rotateTo(new Vector3f(0, 1, 0), new Vector3f(0, 0, 1));
        Quaternionf horizontalQuat = new Quaternionf().rotateTo(new Vector3f(0, 0, 1), horizontalDir);
        poseStack.mulPose(new Quaternionf(horizontalQuat).mul(layQuat).mul(fixQuat));
        poseStack.scale(1.2f, 1.2f, 1.2f);
    }

    private static void drawSword(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                  ItemStack stack, Player player) {
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ItemDisplayContext.NONE, packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, player.level(), player.getId());
    }
}
