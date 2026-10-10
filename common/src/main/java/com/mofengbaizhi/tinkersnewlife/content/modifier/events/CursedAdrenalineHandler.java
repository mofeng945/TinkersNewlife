package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.util.ArmorModifierHelper;
import com.mofengbaizhi.tinkersnewlife.util.GoetyBridge;
import com.mofengbaizhi.tinkersnewlife.util.SoulEnergyBridge;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 词条·咒上·腺速（诅咒金属材料护甲自带：armor 部件）
 * <p>
 * 穿戴者（玩家或诡厄仆从等任意生物）受到<b>敌对伤害</b>（伤害来源实体分类为 MONSTER，
 * 即原版+mod 敌对生物，含其弹射物）或<b>摔落伤害</b>时：消耗 10 点灵魂能量 →
 * 获得 5 秒等效迅捷 II 的加速。
 * <p>
 * ⭐ 无视角变化：不用原版 Speed 药水效果（会拉宽 FOV、冒绿色粒子），而是直接给穿戴者
 * {@code generic.movement_speed} 添加 {@code multiply_total +40%} 的临时 AttributeModifier
 * （迅捷 II 等效 = +40% 移速），持续 5 秒后到期移除。
 * <p>
 * 触发仅限敌对伤害/摔落（火、岩浆、溺水、爆炸等不触发）；冷却 1 分钟（1200 tick）。
 * <p>
 * 灵魂来源（2026-09 仆从支持）：玩家穿戴扣自己灵魂；诡厄仆从等非玩家穿戴者扣
 * <b>主人（玩家）</b>的灵魂（经 GoetyBridge.getServantOwner），主人非玩家/无主人/灵魂
 * 不足则本次不触发。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CursedAdrenalineHandler {

    private static final String MODIFIER_ID = "cursed_adrenaline";

    /** 灵魂消耗 */
    private static final int SOUL_COST = 10;
    /** 迅捷 II 等效移速加成：+40% */
    private static final double SPEED_BONUS = 0.4;
    /** 效果持续：5 秒 = 100 tick */
    private static final int DURATION_TICKS = 100;
    /** 冷却：1 分钟 = 1200 tick */
    private static final int COOLDOWN_TICKS = 1200;

    /** 速度属性修改器 UUID（固定，保证可重复移除） */
    private static final UUID SPEED_UUID = UUID.fromString("6f0f4f5a-2c0e-4b6a-9a3c-8d9e5f1a2b3c");

    /** 穿戴者 UUID → 加速结束 tick（服务端） */
    private static final Map<UUID, Long> SPEED_END = new ConcurrentHashMap<>();
    /** 穿戴者 UUID → 冷却结束 tick（服务端） */
    private static final Map<UUID, Long> COOLDOWN_END = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity wearer = event.getEntity();
        if (wearer.level().isClientSide) return;

        // 必须穿着带咒上·腺速的诅咒金属护甲（任意穿甲者）
        if (!ArmorModifierHelper.hasModifierOnArmor(wearer, MODIFIER_ID)) return;

        // 触发条件：敌对伤害（来源实体为 MONSTER 分类）或 摔落伤害
        if (!isHostileOrFall(event.getSource())) return;

        // 冷却检查
        long now = wearer.level().getGameTime();
        Long cd = COOLDOWN_END.get(wearer.getUUID());
        if (cd != null && now < cd) return;

        // 灵魂来源：玩家扣自己；仆从扣主人（玩家）
        Player soulSource = resolveSoulSource(wearer);
        if (soulSource == null) return;
        if (!SoulEnergyBridge.decreaseSouls(soulSource, SOUL_COST)) return;

        // 记录冷却（1 分钟）与加速结束时间（5 秒）
        COOLDOWN_END.put(wearer.getUUID(), now + COOLDOWN_TICKS);
        SPEED_END.put(wearer.getUUID(), now + DURATION_TICKS);

        applySpeed(wearer);
    }

    /**
     * 解析灵魂来源：玩家穿戴 → 自己；诡厄仆从等非玩家穿戴 → 其主人（须为玩家）。
     * 无主/主人非玩家 → null（不触发）。
     */
    private static Player resolveSoulSource(LivingEntity wearer) {
        if (wearer instanceof Player player) return player;
        if (!GoetyBridge.isGoetyServant(wearer)) return null;
        LivingEntity owner = GoetyBridge.getServantOwner(wearer);
        return owner instanceof Player player ? player : null;
    }

    /** 敌对（来源实体是 MONSTER 分类，含其弹射物经 getEntity 取 owner）或摔落 */
    private static boolean isHostileOrFall(net.minecraft.world.damagesource.DamageSource source) {
        if ("fall".equals(source.getMsgId())) return true;
        Entity entity = source.getEntity();
        if (entity == null) return false;
        return entity.getType().getCategory() == MobCategory.MONSTER;
    }

    /** 施加等效迅捷 II 的移动速度属性加成（无 FOV 变化、无药水粒子） */
    private static void applySpeed(LivingEntity wearer) {
        AttributeInstance attribute = wearer.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;
        attribute.removeModifier(SPEED_UUID); // 防重复触发叠加
        attribute.addTransientModifier(new AttributeModifier(
                SPEED_UUID, "tnl_cursed_adrenaline", SPEED_BONUS,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity wearer = event.getEntity();
        if (wearer.level().isClientSide) return;
        long now = wearer.level().getGameTime();
        UUID uuid = wearer.getUUID();

        // 到期移除速度加成（先查后删，避免提前取走值）
        Long end = SPEED_END.get(uuid);
        if (end != null) {
            if (now >= end) {
                SPEED_END.remove(uuid);
                AttributeInstance attribute = wearer.getAttribute(Attributes.MOVEMENT_SPEED);
                if (attribute != null) {
                    attribute.removeModifier(SPEED_UUID);
                }
            }
        }
        // 冷却到期清理（防止 map 无限增长）
        Long cd = COOLDOWN_END.get(uuid);
        if (cd != null && now >= cd) {
            COOLDOWN_END.remove(uuid);
        }
    }
}
