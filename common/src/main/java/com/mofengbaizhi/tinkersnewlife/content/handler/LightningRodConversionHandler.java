package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * <b>避雷针雷击 → 单流体容器里的烈焰血转化为液态闪电</b>（用户口径 ✓）。
 *
 * <p>规则（用户原话）：「在装有烈焰血的单流体容器上方放置避雷针，如果有闪电劈中，
 * 容器中的烈焰血会转化为液态闪电」＋「转化率是 <b>5:4</b>，即那五分之一会消失」 ✓
 * ⇒ 1000 mB 烈焰血 → 800 mB 液态闪电（余下 200 mB 直接消失 ✓）。
 *
 * <h2>实现要点</h2>
 * <ol>
 *   <li><b>雷击判定对齐原版</b>：监听 {@link LightningBolt} 入世（{@link EntityJoinLevelEvent}），
 *       取"落点方块"用的就是原版 {@code LightningBolt#getStrikePosition()} 同款算法
 *       （{@code BlockPos.containing(x, y - 1e-6, z)} —— 反编译 1.20.1 {@code LightningBolt.java:136-139} ✓）；
 *       原版 {@code LightningBolt#tick()} 正是拿这个位置去调 {@code LightningRodBlock#onLightningStrike} ✓
 *       ⇒ 我们与"原版认为哪一格被劈中"完全一致 ✓（不用自己猜 ±1 格 ✓）。
 *       另外再兜一层：若落点方块**下面**那格才是避雷针（个别模组的闪电实体位置偏高），也认 ✓。</li>
 *   <li><b>容器 = 避雷针正下方那一格</b>（用户口径"容器上方放置避雷针" ✓）。</li>
 *   <li><b>只认"单流体容器"</b>：判据 = <b>容器里所有非空罐装的都只有烈焰血这一种流体</b> ✓
 *       —— ⚠ <b>不是</b> {@code getTanks() == 1} ✗：匠魂储罐一装上流体就返回 2 ⇒ 会被误挡 ✗（§655 已修 ✓）。</li>
 *   <li><b>先算产出、再抽干、再按 4/5 灌入</b>：输出恒小于输入（4/5 &lt; 1）⇒ 刚腾空的罐子一定装得下 ✓，
 *       不会出现"抽走了却灌不进去、白白丢流体" ✗（万一某罐子拒收，会打一条 WARN 说明丢了多少 ✓）。
 *       ⚠ 顺序很重要：**先算再抽**，否则"量太少算不出产出"时会把烈焰血白抽销毁 ✗（§655 已修 ✓）。</li>
 *   <li><b>没装铁魔法时什么都不做</b>：{@code liquid_lightning} 是本模组的<b>铁魔法联动流体</b>
 *       （没装铁魔法就不注册 ✓）⇒ 查不到输出流体就<b>提前 return</b> ✓
 *       —— 绝不能"先把烈焰血抽干再发现没流体" ✗。</li>
 * </ol>
 *
 * <p>⚠ 与"闪电苦力怕 → 液态闪电"（{@code ChargedCreeperMeltingRecipe}）是两条独立来源 ✓，
 * 液态闪电同时也是一种冶炼炉燃料（§289）⇒ 本机制实质是"把烈焰血这种燃料用雷升级成更好的燃料" ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LightningRodConversionHandler {

    private LightningRodConversionHandler() {}

    /** 输入流体：匠魂本体的<b>烈焰血</b> |
     *  （TCon 的流体注册名不带 {@code _still} 后缀 ✓，中文名见 {@code fluid.tconstruct.blazing_blood} = 烈焰血 ✓） */
    public static final ResourceLocation BLAZING_BLOOD = new ResourceLocation("tconstruct", "blazing_blood");

    /** 输出流体：本模组的<b>液态闪电</b>（铁魔法联动组；没装铁魔法 ⇒ 查不到 ⇒ 整条机制不生效 ✓） */
    public static final ResourceLocation LIQUID_LIGHTNING =
            new ResourceLocation(TinkersNewlife.MOD_ID, "liquid_lightning_still");

    /** 转化率 5:4（每 5 mB 烈焰血 → 4 mB 液态闪电；余下 1/5 消失 ✓） */
    public static final int INPUT_PER_CYCLE = 5;
    public static final int OUTPUT_PER_CYCLE = 4;

    @SubscribeEvent
    public static void onLightningBoltJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof LightningBolt bolt)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos rodPos = struckRod(level, bolt);
        if (rodPos == null) return;          // 没劈中避雷针 ⇒ 不管 ✓
        convert(level, rodPos);
    }

    /** 这道闪电劈中的避雷针位置；没劈中避雷针返回 null（算法与原版 {@code getStrikePosition()} 一致 ✓） */
    @Nullable
    private static BlockPos struckRod(ServerLevel level, LightningBolt bolt) {
        Vec3 p = bolt.position();
        BlockPos strike = BlockPos.containing(p.x, p.y - 1.0E-6D, p.z);
        if (level.getBlockState(strike).getBlock() instanceof LightningRodBlock) return strike;
        // 兜底：个别自定义闪电实体位置偏高 ⇒ 落点方块的下方那格也算 ✓
        BlockPos lower = strike.below();
        return level.getBlockState(lower).getBlock() instanceof LightningRodBlock ? lower : null;
    }

    /** 避雷针正下方那一格：若是"装着烈焰血的单流体容器" ⇒ 5:4 换成液态闪电 ✓ */
    private static void convert(ServerLevel level, BlockPos rodPos) {
        BlockPos tankPos = rodPos.below();
        BlockEntity be = level.getBlockEntity(tankPos);
        if (be == null) return;

        Fluid blazing = ForgeRegistries.FLUIDS.getValue(BLAZING_BLOOD);
        Fluid lightning = ForgeRegistries.FLUIDS.getValue(LIQUID_LIGHTNING);
        // ⚠ 输出流体必须先确认存在（没装铁魔法 ⇒ 不注册 ✓）——否则会"抽干烈焰血却灌不进东西" ✗
        if (blazing == null || lightning == null) return;

        // 无方向先试一次；再六面各试一次（很多方块实体只在某些面暴露能力 ✓ 与"匠魂自己找燃料罐"同款 ✓）
        if (tryConvert(be.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null),
                level, tankPos, blazing, lightning)) {
            return;
        }
        for (Direction side : Direction.values()) {
            if (tryConvert(be.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElse(null),
                    level, tankPos, blazing, lightning)) {
                return;
            }
        }
    }

    /**
     * 对一个流体处理器尝试转化；真转化了就返回 true（调用方据此停止试其它面 ✓）。
     *
     * <h2>⚠⚠ 判据是"容器里<b>只装着烈焰血这一种流体</b>"，**不是** {@code getTanks() == 1} ✗</h2>
     * 这里原本写成 {@code getTanks() == 1} ✗ —— 那是**错的** ✓，会让**匠魂自己的储罐**永远不转化 ✗：
     * <pre>
     * // SmelteryTank.java:92-95（反编译实测 ✓）
     * public int getTanks() {
     *   if (contained &lt; capacity) {
     *     return fluids.size() + 1;      // ← 已经有流体时返回「已装种类数 + 1」
     *   }
     * }
     * </pre>
     * ⇒ 匠魂储罐（seared / scorched tank）**一装上烈焰血，{@code getTanks()} 就是 2** ✗
     * （它给第二种流体预留一个空槽 ✓）⇒ 被旧判据挡掉 ✗
     * —— 而 JEI 的说明里明明写着"匠魂储罐也算" ✗ ⇒ **实现与自己的说明矛盾** ✓ 已在 §655 修掉 ✓。
     */
    private static boolean tryConvert(@Nullable IFluidHandler handler, ServerLevel level, BlockPos tankPos,
                                      Fluid blazing, Fluid lightning) {
        if (handler == null) return false;
        int tanks = handler.getTanks();
        if (tanks <= 0) return false;

        // ① 判据：**所有非空罐装的都必须是烈焰血**（混了别的流体 ⇒ 不算"单流体容器" ✓）
        int total = 0;
        boolean any = false;
        for (int i = 0; i < tanks; i++) {
            FluidStack fs = handler.getFluidInTank(i);
            if (fs.isEmpty()) continue;
            if (!fs.getFluid().isSame(blazing)) return false;
            any = true;
            total += fs.getAmount();
        }
        if (!any || total <= 0) return false;

        // ② ⚠ **先算产出、再抽** ✗ —— 旧代码是"先抽干再算"，于是"量太少算不出产出"时
        //    （例如正好 1 mB：1×4÷5 = 0）烈焰血**已经被抽走销毁、却什么都没产出** ✗（§655 一起修 ✓）
        if (scaled(total) <= 0) return false;

        // ③ 抽干（逐个非空罐抽；以**实际抽到**的量为准 ✓）
        int drainedTotal = 0;
        for (int i = 0; i < tanks; i++) {
            FluidStack fs = handler.getFluidInTank(i);
            if (fs.isEmpty()) continue;
            FluidStack d = handler.drain(fs.copy(), IFluidHandler.FluidAction.EXECUTE);
            drainedTotal += d.getAmount();
        }
        if (drainedTotal <= 0) return false;

        int out = scaled(drainedTotal);
        if (out <= 0) {
            // 极少见：实际抽到的比预估少 ⇒ 把抽出来的**还回去**，绝不白抽 ✗
            handler.fill(new FluidStack(blazing, drainedTotal), IFluidHandler.FluidAction.EXECUTE);
            return false;
        }

        int filled = handler.fill(new FluidStack(lightning, out), IFluidHandler.FluidAction.EXECUTE);
        if (filled < out) {
            // 正常不会发生（4/5 < 1 ⇒ 刚腾空的罐子一定装得下 ✓）；真发生就照实记账 ✓
            TinkersNewlife.LOGGER.warn("[避雷针转化] {} 拒收了 {} mB 液态闪电（本次产出 {} mB）—— 这部分丢失",
                    tankPos, out - filled, out);
        }

        int lost = drainedTotal - out;                                  // 5:4 里"消失"的那 1/5 ✓
        TinkersNewlife.LOGGER.info("[避雷针转化] {} 的烈焰血 {} mB → 液态闪电 {} mB（5:4，消失 {} mB）",
                tankPos, drainedTotal, filled, lost);

        // 雷击特效（原版闪电本身已有音效 ✓ 这里只补罐子上的一圈电火花 + 一声轻响 ✓）
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                tankPos.getX() + 0.5D, tankPos.getY() + 1.1D, tankPos.getZ() + 0.5D,
                24, 0.35D, 0.25D, 0.35D, 0.02D);
        level.playSound(null, tankPos, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 0.8F, 1.4F);
        return true;
    }

    /** 5:4 换算（向下取整 ⇒ 那不足一份的零头随"消失的 1/5"一起没掉 ✓） */
    private static int scaled(int inMb) {
        return (int) ((long) inMb * OUTPUT_PER_CYCLE / INPUT_PER_CYCLE);
    }
}
