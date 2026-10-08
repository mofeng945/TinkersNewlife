package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.LongShortBladeItem;
import com.mojang.math.Axis;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * ⭐ §1124 长短刃「<b>杀戮光环</b>」的**玩家模型旋转**（用户口径 ✓
 * 「使玩家模型双手平举刀刃持平然后开始**快速旋转**」✓ —— ⚠ 本轮只做"旋转" ✗，「双手平举」见类末 ✓）。
 *
 * <h2>⭐ 信号从哪来（⚠ 关键在于"客户端看不到服务端状态" ✗）</h2>
 * 光环的真实状态在服务端的静态集合里 ✓ ⇒ ⚠ **客户端看不到** ✗（hud-side 审查时专门提醒过 ✓）。
 * ⇒ ⭐ 改用**物品 NBT**（{@link LongShortBladeItem#TAG_ULTIMATE_END} ✓）当信号 ✓ ——
 * ⭐ 手持物品的 NBT **会同步给客户端** ✓ ⇒ 客户端读它就知道"该转起来了" ✓ ⇒ ⭐ **不需要网络包** ✓。
 * ⚠ 判断只依赖"**是否 &gt; 0**" ✗ 不依赖精确剩余值 ✓（物品 NBT 的同步不保证每 tick ✓ 会滞后 ✓）。
 *
 * <h2>⚠ 姿态栈的 push/pop 必须配对（本仓 flying sword 那套吃过多层姿态的亏 ✗）</h2>
 * ⚠ 若在 {@code Pre} 里 {@code pushPose()} 却在 {@code Post} 里**无条件** pop ✗
 * ⇒ 那些**没有信号**的实体会把**原版自己那一层**弹掉 ✗ ⇒ 整帧渲染错位 ✗。
 * ⇒ ⭐ 所以用一个**按实体记录**的标记 ✓（{@code WeakHashMap} ＋ 渲染是单线程 ✓）——
 * 只有真的 push 过才 pop ✓ 且**同一个实体**才算 ✓。
 *
 * <h2>⚠ 为什么以"身体中心"为轴 ✗</h2>
 * {@code RenderPlayerEvent.Pre} 的姿态栈原点在**实体脚底** ✓ ⇒ 直接转会让模型**绕脚底甩一圈** ✗
 * （看起来像飘出去 ✓）⇒ ⭐ 先抬到身体中心（0.9 格 ≈ 1.8 身高的一半 ✓）、旋转、再抬回去 ✓。
 *
 * <h2>⚠ 本轮**没做**的（如实记录 ✗ 不擅自动 ✓）</h2>
 * ⭐ 用户口径里的「**双手平举、刀刃持平**」需要动 {@code HumanoidModel} 的
 * {@code rightArm}/{@code leftArm} 骨骼 ✓ —— ⚠ 那**风险更高**（可能干扰举盾/拉弓等既有姿势 ✗）
 * ⇒ ⭐ 先只做旋转 ✓ 等用户点头再补手臂 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LongShortBladeSpinHandler {

    private LongShortBladeSpinHandler() {
    }

    /** ⭐ 每 tick 转 **90°** ⇒ 4 tick（0.2 秒）一圈 ✓ —— 用户给的参考实现就是这个值 ✓（我原先写 36 ✗ 太慢 ✓） */
    private static final float DEG_PER_TICK = 90.0F;

    /** ⭐ 旋转轴心高度（格 ✓）：约等于玩家身高 1.8 的一半 ⇒ 以身体中心为轴 ✓ */
    private static final double PIVOT_Y = 0.9D;

    /** ⭐ 已经 push 过、还等着 pop 的玩家 ✓（⚠ 只有真 push 过的才 pop ✗ 免得多弹一层 ✓） */
    private static final Map<Player, Boolean> PUSHED = new WeakHashMap<>();

    // ============================================================
    //  ⭐ 交替挥动：副手那一挥（由 S2C 包驱动 ✓ 不走物品 NBT ✗）
    // ============================================================

    /** ⭐ 副手挥动的时长（tick ✓ 约等于原版挥动时长 ✓） */
    public static final int OFFHAND_SWING_TICKS = 6;

    /**
     * ⭐ "副手这一挥要播到哪个客户端 tick 为止" ✓（0 ＝ 没在挥 ✓）。
     * <p>⚠ 用**绝对 tick** 而不是"剩余次数" ✗ —— 渲染是**每帧**调 ✓
     * （⚠ §1130 就是"按调用次数递减"导致一秒内跑完 ✗ 这次从设计上避开 ✓）。
     */
    private static volatile int offhandSwingUntil = 0;

    /**
     * ⭐ 客户端收到 {@code PacketSwingOffhand} 时调 ✓ —— ⭐ 从**下一个 tick** 起播 6 tick 的左臂前挥 ✓。
     * <p>⚠ 必须在**主线程**上调 ✓（包的 `enqueueWork` 已经是主线程 ✓）。
     */
    public static void clientStartOffhandSwing() {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || mc.player == null) {
                return;
            }
            offhandSwingUntil = mc.player.tickCount + OFFHAND_SWING_TICKS;
            // ⭐⭐ **关键一步**（反编译 `ItemInHandRenderer` 才看清 ✓）：
            //   原版第一人称里**副手本来就会挥** ✓ —— 但它的判据是
            //   `hand = player.swingingArm ?? MAIN_HAND` ⇒ ⭐ **只有 `swingingArm == OFF_HAND` 时**才给副手
            //   传真实的挥动进度（否则传写死的 `0.0f` ✗）。⚠ 而我发的包原先**只设了自己的计时器** ✗
            //   没动客户端这两个字段 ⇒ ⭐ 原版认定"这一刀是主手挥的" ⇒ 副手永远拿 0 ⇒ **不挥** ✗ ✓。
            //   ⇒ ⭐ 直接把客户端的字段设成"副手在挥" ✓ ——
            //     ⚠ **不能调 `player.swing(OFF_HAND)`** ✗（⭐ 客户端那道闸门会把它吞掉 ✓ 就是 §1133 的坑 ✓）
            //     ⇒ ⭐ 直接写字段 ✓（`swinging`/`swingingArm` 公开可写 ✓ 已在 mixin 里验证过 ✓）。
            //   ⭐ **一手治两处** ✓：第一人称的手（`ItemInHandRenderer` ✓）＋ 第三人称的模型
            //     （`HumanoidModel` 那边仍由本类的 `offhandSwingProgress` 补 ✓ 因为模型只认 `getMainArm()` ✗）。
            mc.player.swinging = true;
            mc.player.swingingArm = net.minecraft.world.InteractionHand.OFF_HAND;
            mc.player.swingTime = -1;      // ⚠ 让它**从头**开始挥 ✓（不等于 0 才能骗过原版的状态机 ✓）
            mc.player.attackAnim = 0.0F;   // ⭐ 平滑值也归零 ⇒ 挥动从 0 起 ✓ 不会"半截开始"✓
            mc.player.oAttackAnim = 0.0F;
            // ⚠ **临时探针**（⭐ 验证 S2C 包到底有没有到客户端 ✓ 定位完即删 ✗）
            TinkersNewlife.LOGGER.info("[长短刃·探针] 收到副手挥动包 ⇒ tick={} 播到 {}",
                    mc.player.tickCount, offhandSwingUntil);
        } catch (Throwable ignored) {
            // ⭐ 客户端动画失败绝不能崩 ✗
        }
    }

    /**
     * ⭐ 本帧左臂该挥多少 ✓（0 ＝ 不挥 ✓ 0~1 之间 ＝ 正在挥 ✓）。
     *
     * @param tickCount 实体当前的 {@code tickCount} ✓
     */
    public static float offhandSwingProgress(int tickCount) {
        int until = offhandSwingUntil;
        int left = until - tickCount;
        if (left <= 0 || left > OFFHAND_SWING_TICKS) {
            return 0.0F;
        }
        // ⭐ 0 → 1 的正弦挥动 ✓（与手臂角度相乘 ✓）
        float f = 1.0F - (float) left / (float) OFFHAND_SWING_TICKS;
        return (float) Math.sin(Math.sqrt(f) * Math.PI);
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        try {
            // ⚠ RenderPlayerEvent#getEntity() 的返回类型**就是 Player** ✗
            //   ⇒ 不能写 `instanceof Player player`（"表达式类型的子类型"⇒ 非法 ✗）
            //   ⚠ 这已经是**第二次**踩同一个坑 ✓（第一次是 projectile-side 替我编译时揪出来的 ✓）
            Player player = event.getEntity();
            if (player == null || !spinning(player)) {
                return;
            }
            // ⭐ 用 **partialTick** 插值 ✓ —— 只用 tickCount 的话每 tick 才跳 90° ⇒ 看起来一顿一顿的 ✗
            //   （⚠ 用户给的参考实现也是 `(skillTimer + partialTick) * 90f` ✓）
            float partialTick = event.getPartialTick();
            float angle = ((player.tickCount + partialTick) * DEG_PER_TICK) % 360.0F;
            var pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(0.0D, PIVOT_Y, 0.0D);
            pose.mulPose(Axis.YP.rotationDegrees(angle));
            pose.translate(0.0D, -PIVOT_Y, 0.0D);
            PUSHED.put(player, Boolean.TRUE);
        } catch (Throwable ignored) {
            // ⭐ 渲染出错绝不能崩客户端 ✗
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        try {
            Player player = event.getEntity();
            if (player != null && Boolean.TRUE.equals(PUSHED.remove(player))) {
                event.getPoseStack().popPose();
            }
        } catch (Throwable ignored) {
            // ⭐ 同理 ✗
        }
    }

    /**
     * ⭐ 这个玩家此刻该转吗 ✓ —— 看**手持物品的 NBT 信号** ✓
     * （⚠ 主手优先 ✗ 主手没有再看副手 ✓ —— 按原版 F 换手后信号可能落在副手那把上 ✓）。
     */
    private static boolean spinning(Player player) {
        ItemStack main = player.getMainHandItem();
        if (LongShortBladeItem.isUltimateVisual(main)) {
            return true;
        }
        ItemStack off = player.getOffhandItem();
        return LongShortBladeItem.isUltimateVisual(off);
    }
}
