package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.ColdBloodedModifier;
import com.mofengbaizhi.tinkersnewlife.content.modifier.EndlessColdWindModifier;
import com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks.IronSpellsSpellAccess;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 材料「无相冰」两个特性的结算器（铁魔法联动）。
 *
 * <h2>① 冷酷（有等级）</h2>
 * 每次攻击（近战 / 远程 / 法术都走 {@code LivingDamageEvent}）在**玩家伤害结算之后**追加一段
 * <b>冰霜学派法术伤害</b>：{@code 玩家伤害 × 0.1 × (1 + 等级)}。
 * <ul>
 *   <li>用 {@code LivingDamageEvent}（护甲/吸收之后）作为"玩家伤害"的口径 ✓；</li>
 *   <li>「之后追加」用**同 tick 的 ServerTick END** 落地（实体伤害都发生在实体 tick 里，
 *       END 阶段在它们之后 ✓），这样不会插在主伤害结算中间；</li>
 *   <li>追加伤害的伤害源是铁魔法的 {@code ice_magic}（冰霜学派法术伤害 ✓），
 *       归属攻击者（所以命灯指轮的慈悲、穿透等规则照常生效 ✓）；</li>
 *   <li>防递归：追加伤害本身也会触发受伤事件 → 用 {@link #APPLYING} 标志屏蔽自身。</li>
 * </ul>
 *
 * <h2>② 无止寒风（无等级）</h2>
 * 手持（主/副手）带此特性的物品时，给持有者加 <b>+50% 冰霜法术强度</b>
 * （铁魔法 {@code ICE_SPELL_POWER} 属性 ADDITION +0.5，transient 修饰符，每 10 tick 维持）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FormlessIceHandler {

    private FormlessIceHandler() {
    }

    /** 待落地的追加伤害：目标 → 累计量（同 tick 多次命中会累加） */
    private static final List<Pending> PENDING = new ArrayList<>();

    /** 正在落地追加伤害（防递归触发冷酷） */
    private static final java.util.Set<UUID> APPLYING = new java.util.HashSet<>();

    /** 无止寒风：属性修饰符 ID */
    private static final UUID ICE_POWER_MODIFIER = UUID.fromString("2b7f1c94-6a3d-4c58-9e21-4f8d7c0b1a35");

    private record Pending(LivingEntity target, LivingEntity attacker, float amount) {
    }

    // ============================================================
    //  ① 冷酷：记录"玩家伤害"，同 tick 末追加冰霜伤害
    // ============================================================

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof Player attacker)) return;
        if (APPLYING.contains(target.getUUID())) return;         // 是我们自己追加的伤害 → 不再累加
        int level = ColdBloodedModifier.bestLevel(attacker);
        if (level <= 0) return;
        float dealt = event.getAmount();
        if (dealt <= 0.0F) return;
        float extra = (float) (dealt * ColdBloodedModifier.EXTRA_RATIO_PER_LEVEL * (1 + level));
        if (extra <= 0.0F) return;
        PENDING.add(new Pending(target, attacker, extra));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (PENDING.isEmpty()) return;
        List<Pending> batch = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Pending p : batch) {
            LivingEntity target = p.target();
            if (target == null || !target.isAlive() || target.isRemoved()) continue;
            DamageSource ice = iceDamageSource(target.level(), p.attacker());
            if (ice == null) continue;
            APPLYING.add(target.getUUID());
            try {
                target.invulnerableTime = 0;                    // 追加段不被无敌帧吞掉
                target.hurt(ice, p.amount());
            } catch (Throwable t) {
                TinkersNewlife.LOGGER.warn("[冷酷] 追加冰霜伤害失败: {}", t.toString());
            } finally {
                APPLYING.remove(target.getUUID());
            }
        }
    }

    /** 铁魔法冰霜学派的伤害源（拿不到就返回 null —— 契约由 IronSpellsSpellAccess 保证存在） */
    private static DamageSource iceDamageSource(net.minecraft.world.level.Level level, LivingEntity attacker) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        try {
            ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE,
                    new ResourceLocation(ColdBloodedModifier.ICE_DAMAGE_TYPE));
            Holder<DamageType> holder = serverLevel.registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
            return new DamageSource(holder, attacker, attacker);
        } catch (Throwable t) {
            return null;
        }
    }

    // ============================================================
    //  ② 无止寒风：手持时 +50% 冰霜法术强度
    // ============================================================

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (player.tickCount % 10 != 0) return;
        Attribute icePower = IronSpellsSpellAccess.iceSpellPower();
        if (icePower == null) return;
        AttributeInstance inst = player.getAttribute(icePower);
        if (inst == null) return;
        // 先撤旧修饰符，再按当前手持状态决定是否加上（脱手立即失效）
        inst.removeModifier(ICE_POWER_MODIFIER);
        if (EndlessColdWindModifier.heldBy(player)) {
            inst.addTransientModifier(new AttributeModifier(ICE_POWER_MODIFIER,
                    "endless_cold_wind_ice_power", EndlessColdWindModifier.ICE_POWER_BONUS,
                    AttributeModifier.Operation.ADDITION));
        }
    }
}
