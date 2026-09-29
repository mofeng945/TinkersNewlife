package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

/**
 * 词条·<b>兵士佩刀</b>的实际逻辑（§808 · 唐横刀自带 · 无等级 ✓）。
 *
 * <h2>效果（用户口径 ✓ 逐字实现）</h2>
 * <ol>
 *   <li><b>4 格内按目标距离追加伤害</b> ✓：4格 <b>0</b> 段 / 3格 <b>1</b> 段 / 2格 <b>2</b> 段 /
 *       1格 <b>3</b> 段 / 不到 1 格 <b>4</b> 段 ✓（4 格以外不加 ✓）；</li>
 *   <li><b>每段挥出一道灰色刀光</b> ✓：用原版 {@code ParticleTypes.SWEEP_ATTACK}（本来就是灰白弧光 ✓，
 *       <b>不需要任何新贴图</b> ✓ —— 与"先不给纹理"的口径一致 ✓），沿"攻击者眼睛 → 目标身体中心"
 *       按段数均分铺开 ✓（视觉上就是拔刀剑那种多道刀光 ✓）。</li>
 * </ol>
 *
 * <h2>两个实现口径（如实说明 ✓）</h2>
 * <ul>
 *   <li><b>用词条判断而不是硬认"唐横刀"</b> ✓：读 {@code ToolStack.getModifiers().getLevel(soldiers_saber)}
 *       ⇒ 以后任何工具挂上这个词条都能吃到 ✓（也正是"词条效果"该有的样子 ✓）；</li>
 *   <li><b>追加伤害并入同一次伤害事件</b>（{@code event.setAmount(原 + 段数 × 每段) }✓）而不是"多打几次" ✗：
 *       避免反复触发无敌帧/击杀归属等副作用 ✓；"多段"体现在<b>追加的数值与刀光道数</b>上 ✓。</li>
 * </ul>
 *
 * <h2>每段追加伤害（用户拍板 ✓）</h2>
 * 用户口径：**每段追加「工具伤害」的 <b>50%</b>** ✓ ⇒ {@link #BONUS_PER_STAGE_RATIO} = <b>0.5</b> ✓，
 * 追加量 = <b>工具攻击面板 × 0.5 × 段数</b> ✓（读匠魂面板 {@code ToolStats.ATTACK_DAMAGE} ✓
 * ⇒ 材料/强化改了面板，追加量跟着走 ✓ 不会"前期强后期废" ✓）。
 * 贴身 4 段 ⇒ <b>+200% 工具伤害</b> ✓；4 格处 0 段 ⇒ 不加 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SoldiersSaberHandler {

    /** 词条 id（与 {@code Modifiers.SOLDIERS_SABER} 一致 ✓） */
    private static final slimeknights.tconstruct.library.modifiers.ModifierId SABER =
            new slimeknights.tconstruct.library.modifiers.ModifierId(
                    new ResourceLocation(TinkersNewlife.MOD_ID, "soldiers_saber"));

    /** 每段追加伤害 = 工具攻击面板 × 该比例 ✓（用户口径 50% ✓ 要改只改这里 ✓） */
    public static final float BONUS_PER_STAGE_RATIO = 0.5F;

    /** 生效半径：4 格 ✓（用户口径"4格范围内"✓） */
    public static final double MAX_RANGE = 4.0D;

    private SoldiersSaberHandler() {
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;   // 只认"某个人打的"✓
        if (attacker == victim) return;

        ItemStack held = attacker.getMainHandItem();
        if (held.isEmpty() || !hasSaber(held)) return;

        int stages = stagesFor(attacker.distanceTo(victim));
        if (stages <= 0) return;

        event.setAmount(event.getAmount() + bonusDamage(held, stages));
        spawnSlashes(attacker, victim, stages);
    }

    /**
     * 距离 ⇒ 段数（用户口径 ✓）：不到 1 格 4 段 / 1 格 3 段 / 2 格 2 段 / 3 格 1 段 / 4 格 0 段 ✓
     * <p>用"实体中心距"（{@code distanceTo} ✓）—— 与玩家感知的"几格"一致 ✓。
     */
    public static int stagesFor(double distance) {
        if (distance < 1.0D) return 4;
        if (distance < 2.0D) return 3;
        if (distance < 3.0D) return 2;
        if (distance < MAX_RANGE) return 1;
        return 0;
    }

    /** 每段追加伤害 = 工具攻击面板 × 50% × 段数 ✓（拿不到工具数据就当作 0 ✓ 绝不抛错 ✗） */
    private static float bonusDamage(ItemStack stack, int stages) {
        try {
            float panel = ToolStack.from(stack).getStats().get(ToolStats.ATTACK_DAMAGE);
            return panel * BONUS_PER_STAGE_RATIO * stages;
        } catch (Throwable t) {
            return 0.0F;
        }
    }

    /** 手上这把工具带不带「兵士佩刀」✓（拿不到工具数据就当作不带 ✓ 绝不抛错 ✗） */
    private static boolean hasSaber(ItemStack stack) {
        try {
            return ToolStack.from(stack).getModifiers().getLevel(SABER) > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 灰色刀光：每段一道 {@code SWEEP_ATTACK} ✓ 沿"眼睛 → 目标中心"均分 ✓ */
    private static void spawnSlashes(LivingEntity attacker, LivingEntity victim, int stages) {
        try {
            if (!(attacker.level() instanceof ServerLevel level)) return;
            Vec3 from = attacker.getEyePosition();
            Vec3 to = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
            for (int i = 1; i <= stages; i++) {
                Vec3 p = from.lerp(to, (double) i / (stages + 1));
                level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        p.x, p.y, p.z, 1, 0.12D, 0.12D, 0.12D, 0.0D);
            }
        } catch (Throwable ignored) {
            // 光效失败不影响伤害 ✓
        }
    }
}
