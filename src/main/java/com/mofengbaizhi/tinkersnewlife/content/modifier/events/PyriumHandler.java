package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.AncientSanctuaryModifier;
import com.mofengbaizhi.tinkersnewlife.content.modifier.BlazingModifier;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 材料「炽金」两个"会改数值"的特性结算器（铁魔法联动）。
 *
 * <h2>① 炽热（有等级，工具）</h2>
 * <ul>
 *   <li>每次攻击 <b>5% × 等级</b> 概率点燃目标 5 秒；</li>
 *   <li>每次攻击伤害结算后，追加 {@code 0.1 × (等级+1) × 本次伤害} 的**炽焰学派法术伤害** ——
 *       与「冷酷」同机制：{@code LivingDamageEvent} 记录 → 同 tick {@code ServerTick END} 落地
 *       （保证"伤害之后"）+ {@link #APPLYING} 防递归；伤害源用铁魔法 {@code fire_magic}，
 *       归属攻击者（命灯/穿透规则照常生效）。</li>
 * </ul>
 *
 * <h2>② 远古庇护（无等级，盔甲）</h2>
 * 穿着任意一件带此特性的装备时，给持有者 <b>炽焰法术强度 +50%</b>（固定，不按件数叠加）；
 * 减伤部分在 {@link AncientSanctuaryModifier} 的护甲钩子里逐件链乘。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PyriumHandler {

    private PyriumHandler() {
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Set<UUID> APPLYING = new HashSet<>();

    private static final UUID FIRE_POWER_MODIFIER = UUID.fromString("6c1d3a72-9b4e-4d05-8f37-2e5a9c1b7d40");

    private record Pending(LivingEntity target, LivingEntity attacker, float amount) {
    }

    // ============================================================
    //  ① 炽热
    // ============================================================

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof Player attacker)) return;
        if (APPLYING.contains(target.getUUID())) return;              // 我们自己的追加伤害 → 不再触发
        int level = BlazingModifier.bestLevel(attacker);
        if (level <= 0) return;

        // 点燃：5% × 等级
        if (attacker.getRandom().nextFloat() < BlazingModifier.IGNITE_CHANCE_PER_LEVEL * level) {
            target.setSecondsOnFire(BlazingModifier.IGNITE_SECONDS);
        }

        // 追加炽焰法术伤害
        float dealt = event.getAmount();
        if (dealt <= 0.0F) return;
        float extra = (float) (dealt * BlazingModifier.EXTRA_RATIO_BASE * (level + 1));
        if (extra > 0.0F) PENDING.add(new Pending(target, attacker, extra));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        List<Pending> batch = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Pending p : batch) {
            LivingEntity target = p.target();
            if (target == null || !target.isAlive() || target.isRemoved()) continue;
            DamageSource fire = spellDamageSource(target.level(), p.attacker(), BlazingModifier.FIRE_DAMAGE_TYPE);
            if (fire == null) continue;
            APPLYING.add(target.getUUID());
            try {
                target.invulnerableTime = 0;
                target.hurt(fire, p.amount());
            } catch (Throwable t) {
                TinkersNewlife.LOGGER.warn("[炽热] 追加炽焰伤害失败: {}", t.toString());
            } finally {
                APPLYING.remove(target.getUUID());
            }
        }
    }

    /** 用铁魔法的学派伤害类型构造伤害源（拿不到返回 null） */
    private static DamageSource spellDamageSource(net.minecraft.world.level.Level level, LivingEntity attacker,
                                                  String damageTypeId) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        try {
            ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(damageTypeId));
            Holder<DamageType> holder = serverLevel.registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
            return new DamageSource(holder, attacker, attacker);
        } catch (Throwable t) {
            return null;
        }
    }

    // ============================================================
    //  ② 远古庇护：炽焰法强 +50%
    // ============================================================

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (player.tickCount % 10 != 0) return;
        Attribute firePower = IronSpellsSpellAccess.spellPower("FIRE_SPELL_POWER");
        if (firePower == null) return;
        AttributeInstance inst = player.getAttribute(firePower);
        if (inst == null) return;
        inst.removeModifier(FIRE_POWER_MODIFIER);
        if (AncientSanctuaryModifier.wornBy(player)) {
            inst.addTransientModifier(new AttributeModifier(FIRE_POWER_MODIFIER,
                    "ancient_sanctuary_fire_power", AncientSanctuaryModifier.FIRE_POWER_BONUS,
                    AttributeModifier.Operation.ADDITION));
        }
    }
}
