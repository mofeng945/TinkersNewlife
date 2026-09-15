package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.LifeLampRingItem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 命灯指轮的效果：**佩戴者打出的伤害永远不会致死**，目标最终总会剩 1 点生命。
 *
 * <h2>两道保险</h2>
 * <ol>
 *   <li>{@link LivingHurtEvent}（主要）：把"会打死人的那一击"截成"只打到剩 1 滴"
 *       —— 事件里的 {@code amount} 已经是**扣完护甲/附魔/吸收之后**的最终扣血量，
 *       所以直接 {@code amount = 当前血量 - 1} 就能精确停在 1 点；</li>
 *   <li>{@link LivingDeathEvent}（兜底）：万一有绕过伤害直杀的效果（处决类、指令类、某些 mod 的特效），
 *       只要"凶手"是佩戴者，就取消死亡并把血量拉回 1。</li>
 * </ol>
 *
 * <h2>判定"凶手"</h2>
 * 先看 {@code source.getEntity()}（直接攻击者），否则看 {@code getDirectEntity()}
 * 是否是**弹射物/召唤物**并取它的 owner —— 这样飞剑、魔法弹、被指使的仆从打出的伤害同样受命灯约束。
 * 自己也豁免：只有"攻击别人"才触发，不干预佩戴者自己受到的伤害。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LifeLampRingHandler {

    private LifeLampRingHandler() {
    }

    /** 伤害被截住时至少留下的生命值 */
    private static final float KEEP_HEALTH = 1.0F;

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target == null || target.level().isClientSide) return;
        LivingEntity wearer = attackerOf(event.getSource());
        if (wearer == null || wearer == target) return;
        if (!LifeLampRingItem.isWorn(wearer)) return;

        float hp = target.getHealth();
        if (hp <= KEEP_HEALTH) {
            // 只剩 1 滴血 → 这一击干脆不掉血（否则"1 滴"守不住）
            event.setAmount(0.0F);
            return;
        }
        if (event.getAmount() >= hp) {
            event.setAmount(hp - KEEP_HEALTH);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        if (target == null || target.level().isClientSide) return;
        LivingEntity wearer = attackerOf(event.getSource());
        if (wearer == null || wearer == target) return;
        if (!LifeLampRingItem.isWorn(wearer)) return;

        event.setCanceled(true);
        target.setHealth(KEEP_HEALTH);
        target.clearFire();          // 免得刚被救回来又被烧死
        target.setAirSupply(Math.max(target.getAirSupply(), 0));
    }

    /** 伤害来源的"责任者"：直接攻击者，或弹射物/召唤物的主人 */
    private static LivingEntity attackerOf(DamageSource source) {
        if (source == null) return null;
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile && projectile.getOwner() instanceof LivingEntity owner) {
            return owner;
        }
        Entity sourceEntity = source.getEntity();
        if (sourceEntity instanceof LivingEntity living) return living;
        if (direct instanceof LivingEntity livingDirect) return livingDirect;
        return null;
    }
}
