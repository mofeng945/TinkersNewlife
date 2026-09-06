package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.SoulEnergyBridge;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 词条·堕落：攻击命中（近战/弹射，LivingDamageEvent 结算前）时，
 * 消耗 0.5% 灵魂能量上限（图腾上限，单次 ≤100 点）增幅本次伤害，
 * 每 5 点灵魂 +0.2 伤害、+0.05 格击退（击退累计 ≤3 格）。
 * 伤害在 {@link LivingHurtEvent}（原版实际扣血前）补入，保证与护甲结算顺序正确。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CorruptionHandler {

    private static final ModifierId CORRUPTION = new ModifierId(
            new ResourceLocation(TinkersNewlife.MOD_ID, "corruption"));

    /** 单次消耗上限（点）：0.5% 上限，封顶 100 */
    private static final double MAX_CONSUME = 100.0;
    /** 每 5 点灵魂 → +0.2 伤害 */
    private static final double DMG_PER_5 = 0.2;
    /** 每 5 点灵魂 → +0.05 格击退 */
    private static final double KB_PER_5 = 0.05;
    /** 击退累计上限（格） */
    private static final double KB_CAP = 3.0;

    /** 伤害结算前补增幅：在 LivingDamageEvent 中把增幅加入本次伤害，随后正常走护甲结算 */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof Player player)) return;
        LivingEntity target = event.getEntity();
        if (target == player) return;

        ToolStack tool = ToolHelper.getCombatToolWith(event.getSource(), player, CORRUPTION);
        if (tool == null) return;
        int level = tool.getModifierLevel(CORRUPTION);
        if (level <= 0) return;

        // 灵魂上限 × 0.5%，封顶 100；灵魂不足则不增幅
        int maxSouls = SoulEnergyBridge.getMaxSouls(player);
        if (maxSouls <= 0) return;
        int consume = Math.min((int) Math.ceil(maxSouls * 0.005), (int) MAX_CONSUME);
        if (consume <= 0) return;
        if (!SoulEnergyBridge.decreaseSouls(player, consume)) return;

        // 每 5 点灵魂 +0.2 伤害 / +0.05 格击退（击退封顶 3 格）
        double groups = consume / 5.0;
        float bonusDmg = (float) (groups * DMG_PER_5) * level;
        double kb = Math.min(groups * KB_PER_5, KB_CAP) * level;

        event.setAmount(event.getAmount() + bonusDmg);
        // 击退：沿攻击者指向目标的方向
        if (kb > 0 && target.isAlive()) {
            Vec3 dir = target.position().subtract(player.position()).normalize();
            target.knockback(kb, dir.x, dir.z);
            // 服务端有实体的 knockback 会自己处理；玩家目标需显式施加
            if (target instanceof ServerPlayer sp) {
                sp.hurtMarked = true;
            }
        }
        // 视觉：暗紫增幅粒
        if (target.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    6, 0.3, 0.4, 0.3, 0.01);
        }
    }
}
