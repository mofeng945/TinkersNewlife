package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.AllSpellsUnityModifier;
import com.mofengbaizhi.tinkersnewlife.content.modifier.ArcaneDominationModifier;
import com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks.IronSpellsSpellAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 材料「魔金」三个需要持续维持 / 事件触发的特性（铁魔法联动）。
 *
 * <h2>① 万法归一（有等级，通用）</h2>
 * <ul>
 *   <li>法力上限 <b>+100 × 等级</b>：给 {@code MAX_MANA} 挂 transient 修饰符，
 *       每 10 tick 重算一次（<b>只取身上最高的一件</b> ✓ "最高等级的单件生效"）；</li>
 *   <li>攻击命中时赠送一次 <b>5 级回响打击</b>（30s 冷却）✓；</li>
 *   <li>血量 ≤20% 时赠送一次 <b>3 级深渊庇佑</b>（200s 冷却）✓。</li>
 * </ul>
 *
 * <h2>② 奥法支配（无等级，通用）</h2>
 * 持有/穿戴期间给 {@code SPELL_POWER} 挂 <b>+2.0</b>（= 全学派法术强度 +200%，
 * 因为该属性本身就是所有学派的公共乘数）✓。
 *
 * <h2>赠送的法术"无需消耗法力"</h2>
 * 走 {@link IronSpellsSpellAccess#cast}（施法来源是 {@code SCROLL}，见访问层的来源选择：
 * <b>SCROLL 不消耗法力</b>、也不吃铁魔法自身的冷却）✓；这里仍然按"先垫上、之后原样还原"
 * 兜底一次 ✗ → 万一来源被改回 SPELLBOOK 也不会因为缺蓝而哑火 ✓。
 *
 * <p>铁魔法不在场时：属性/法术都取不到 → 静默跳过（特性本身也来自铁魔法联动材料，不会存在）✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MagicGoldHandler {

    private MagicGoldHandler() {
    }

    /** 法力上限修饰符 id */
    private static final UUID MANA_MODIFIER_ID = UUID.fromString("3f7a1c62-9b04-4d18-8e77-2ac5d6014b93");
    /** 法术强度修饰符 id */
    private static final UUID POWER_MODIFIER_ID = UUID.fromString("b6e24d10-7c93-4a5f-9d38-1f0e7a2c8456");

    /** 赠送法术冷却：玩家 UUID → 下次可用 gameTime */
    private static final Map<UUID, Long> ECHO_COOLDOWN = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SHROUD_COOLDOWN = new ConcurrentHashMap<>();

    // ============================================================
    //  ① 属性维持 + ③ 残血触发
    // ============================================================

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        if (player.tickCount % 10 != 0) return;                 // 每 0.5s 重算一次

        // 万法归一：法力上限 +100 × 最高等级
        int unity = AllSpellsUnityModifier.bestLevel(player);
        applyAttribute(player, "MAX_MANA", MANA_MODIFIER_ID, "all_spells_unity_mana",
                unity > 0 ? AllSpellsUnityModifier.MANA_PER_LEVEL * unity : 0.0D);

        // 奥法支配：全学派法术强度 +200%
        applyAttribute(player, "SPELL_POWER", POWER_MODIFIER_ID, "arcane_domination_power",
                ArcaneDominationModifier.wornBy(player) ? ArcaneDominationModifier.POWER_BONUS : 0.0D);

        tryShroud(player, player.level().getGameTime());
    }

    private static void tryShroud(ServerPlayer player, long now) {
        if (!player.isAlive()) return;
        if (!AllSpellsUnityModifier.wornBy(player)) return;
        float max = player.getMaxHealth();
        if (max <= 0.0F) return;
        if (player.getHealth() > max * AllSpellsUnityModifier.TRIGGER_RATIO) return;
        Long next = SHROUD_COOLDOWN.get(player.getUUID());
        if (next != null && now < next) return;

        SHROUD_COOLDOWN.put(player.getUUID(), now + AllSpellsUnityModifier.SHROUD_COOLDOWN);
        giftCast(player, AllSpellsUnityModifier.SHROUD_SPELL, AllSpellsUnityModifier.SHROUD_LEVEL);
    }

    // ============================================================
    //  ② 攻击时赠送回响打击
    // ============================================================

    @SubscribeEvent
    public static void onAttack(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (attacker == event.getEntity()) return;               // 自伤不触发
        if (event.getAmount() <= 0.0F) return;
        if (!AllSpellsUnityModifier.wornBy(attacker)) return;

        long now = attacker.level().getGameTime();
        Long next = ECHO_COOLDOWN.get(attacker.getUUID());
        if (next != null && now < next) return;

        ECHO_COOLDOWN.put(attacker.getUUID(), now + AllSpellsUnityModifier.ECHO_COOLDOWN);
        giftCast(attacker, AllSpellsUnityModifier.ECHO_SPELL, AllSpellsUnityModifier.ECHO_LEVEL);
    }

    // ============================================================
    //  工具
    // ============================================================

    /** 赠送施法：不消耗法力（顺手做一次"垫蓝—还原"兜底） */
    private static void giftCast(ServerPlayer player, String spellId, int level) {
        Object spell = IronSpellsSpellAccess.spellById(spellId);
        if (spell == null) return;                              // 铁魔法不在场 / 法术改名 → 静默
        int before = IronSpellsSpellAccess.manaOf(player);
        int cost = IronSpellsSpellAccess.manaCostOf(spell, level);
        if (cost > 0 && before >= 0 && before < cost) {
            IronSpellsSpellAccess.setMana(player, cost);
        }
        try {
            IronSpellsSpellAccess.cast(player, spell, level);
        } finally {
            if (cost > 0 && before >= 0) {
                IronSpellsSpellAccess.setMana(player, before);
            }
        }
    }

    /** 维持一个 transient 属性修饰符（value <= 0 表示移除） */
    private static void applyAttribute(LivingEntity entity, String field, UUID id, String name, double value) {
        Attribute attr = IronSpellsSpellAccess.attribute(field);
        if (attr == null) return;
        AttributeInstance instance = entity.getAttribute(attr);
        if (instance == null) return;
        instance.removeModifier(id);
        if (value > 0.0D) {
            instance.addTransientModifier(new AttributeModifier(id, name, value,
                    AttributeModifier.Operation.ADDITION));
        }
    }
}
