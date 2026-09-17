package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * 同心戒·互为同伴的<b>伤害免疫</b>层（服务端）。
 *
 * <p>戴着<b>同一对</b>同心戒的两名玩家互为同伴 → 互相免疫对方的术式与领域效果。
 * 这件事分四层做，本类是其中"伤害事件"那一层：
 * <ol>
 *   <li><b>术式选敌</b>：{@code PuppetUtil#isAllyOf} 里加同伴判定 —— 术式<b>根本不会把同伴选成目标</b>
 *       （比"打了再免"更好：AoE 不会浪费在同伴身上 ✓）；无为转变/守护生物的目标筛选同理；</li>
 *   <li><b>领域效果</b>：{@code BaseDomain#entitiesInSphere} 把同伴剔出效果循环 ——
 *       定身/迟缓/灼烧/斩击/标记等<b>非伤害</b>效果一并免疫 ✓；</li>
 *   <li><b>穿透真伤</b>：{@code TruePierce#apply} 开头直接早退 ——
 *       它是"差额 setHealth 补齐"，事件层拦不住 ✗；</li>
 *   <li><b>伤害事件（本类）</b>：其余一切走 {@code hurt()} 的伤害（带攻击者的法术弹、
 *       领域直伤、仆从/召唤物误伤、以及近战）在这里统一取消 —— 兜底最彻底的一层 ✓。</li>
 * </ol>
 *
 * <p><b>口径说明</b>：本层是"带攻击者的伤害一律取消"，因此同伴之间<b>近战/弓箭也打不到</b>。
 * 这是刻意的 —— 与模组里"同队完全豁免"（{@code isAllyOf}）的口径一致：
 * 一对同心戒就是同一个人，不存在"小心别误伤"这回事 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TwinRingImmunityHandler {

    private TwinRingImmunityHandler() {}

    /**
     * 互为同伴的两名玩家之间，一切伤害作废。
     * <p>用 HIGHEST 优先级：越早取消，别人的"命中时触发"处理越不会白白跑一遍 ✓。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide) return;
        DamageSource src = event.getSource();
        if (src == null) return;
        LivingEntity attacker = asLiving(src.getEntity());
        if (attacker == null) attacker = asLiving(src.getDirectEntity());
        if (attacker == null) return;
        if (TwinRingLink.arePaired(attacker, victim)) {
            event.setCanceled(true);
        }
    }

    @Nullable
    private static LivingEntity asLiving(@Nullable Entity entity) {
        return entity instanceof LivingEntity living ? living : null;
    }
}
