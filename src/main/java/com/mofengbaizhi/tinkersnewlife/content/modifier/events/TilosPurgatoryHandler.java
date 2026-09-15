package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler;
import com.mofengbaizhi.tinkersnewlife.content.modifier.TilosPurgatoryModifier;
import com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks.IronSpellsSpellAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 特性「<b>提洛斯炼狱</b>」结算器（铁魔法联动，见 {@link TilosPurgatoryModifier}）：
 *
 * <p>佩戴者半血以下 → 召唤两只<b>远古骑士</b>（{@code irons_spellbooks:citadel_keeper}）
 * + 无吟唱释放一次 <b>5 级地狱浮现</b>（以玩家为中心）；骑士 <b>150s</b> 后消失，冷却 <b>200s</b>。
 *
 * <h2>两个关键点</h2>
 * <ul>
 *   <li><b>仆从 AI 复用咒灵操术</b>：{@link WuWeiHandler#attachGuardAi(Mob, ServerPlayer)}
 *       —— 这就是那套"仆从通用类"，骑士会自动跟随/攻击玩家的敌人，且不攻击玩家本人；</li>
 *   <li><b>地狱浮现对仆从无伤害</b>：刚召唤的骑士进入一段"免疫铁魔法法术伤害"的窗口，
 *       把这次法术（及其残留效果）对仆从的伤害整体挡掉。</li>
 * </ul>
 *
 * <p>铁魔法不在场时：实体 id 取不到 → 静默跳过（特性本身也来自铁魔法联动材料，不会存在）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TilosPurgatoryHandler {

    private TilosPurgatoryHandler() {
    }

    /** 冷却：玩家 UUID → 下次可用 gameTime */
    private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();
    /** 已召唤的骑士：实体 UUID → 到期 gameTime */
    private static final Map<UUID, Long> KNIGHTS = new ConcurrentHashMap<>();
    /** 骑士实体 id（用于取回实体；取到后再核对 UUID 防 id 复用） */
    private static final Map<UUID, Integer> KNIGHT_IDS = new ConcurrentHashMap<>();
    /** 仆从的法术免疫窗口：实体 UUID → 到期 gameTime */
    private static final Map<UUID, Long> SPELL_IMMUNE = new ConcurrentHashMap<>();

    /** 召唤后的法术免疫窗口（tick）：20s，覆盖地狱浮现及其残留 */
    private static final int IMMUNE_TICKS = 20 * 20;

    // ============================================================
    //  触发
    // ============================================================

    @SubscribeEvent
    public static void onPlayerTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        if (player.tickCount % 10 != 0) return;                 // 每 0.5s 检查一次
        long now = player.level().getGameTime();
        despawnExpired(player.serverLevel(), now);
        clearFriendlyTargets(player.serverLevel(), now);
        tryTrigger(player, now);
    }

    private static void tryTrigger(ServerPlayer player, long now) {
        if (!TilosPurgatoryModifier.wornBy(player)) return;
        float max = player.getMaxHealth();
        if (max <= 0.0F) return;
        if (player.getHealth() > max * TilosPurgatoryModifier.TRIGGER_RATIO) return;
        Long next = COOLDOWN.get(player.getUUID());
        if (next != null && now < next) return;

        COOLDOWN.put(player.getUUID(), now + TilosPurgatoryModifier.COOLDOWN);
        summonKnights(player, now);
        castHell(player);
    }

    // ============================================================
    //  召唤远古骑士（复用咒灵操术的仆从 AI）
    // ============================================================

    private static void summonKnights(ServerPlayer player, long now) {
        var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(TilosPurgatoryModifier.KNIGHT_ENTITY));
        if (type == null) return;                                // 铁魔法不在场 / 实体改名 → 静默
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < TilosPurgatoryModifier.KNIGHT_COUNT; i++) {
            Entity spawned = type.create(level);
            if (!(spawned instanceof Mob mob)) {
                if (spawned != null) spawned.discard();
                continue;
            }
            double angle = Math.toRadians(player.getYRot() + (i == 0 ? -60.0 : 60.0));
            double dx = -Math.sin(angle) * 2.0;
            double dz = Math.cos(angle) * 2.0;
            mob.moveTo(player.getX() + dx, player.getY(), player.getZ() + dz, player.getYRot(), 0.0F);
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);

            // ⭐ 咒灵操术那套仆从通用 AI：跟随 + 只打玩家的敌人，不攻击玩家
            WuWeiHandler.attachGuardAi(mob, player);

            KNIGHTS.put(mob.getUUID(), now + TilosPurgatoryModifier.KNIGHT_LIFETIME);
            KNIGHT_IDS.put(mob.getUUID(), mob.getId());
            SPELL_IMMUNE.put(mob.getUUID(), now + IMMUNE_TICKS);
        }
        TinkersNewlife.LOGGER.debug("[提洛斯炼狱] {} 触发：召唤 {} 只远古骑士", player.getName().getString(),
                TilosPurgatoryModifier.KNIGHT_COUNT);
    }

    /** 无吟唱、以玩家为中心释放地狱浮现 */
    private static void castHell(ServerPlayer player) {
        Object spell = IronSpellsSpellAccess.spellById(TilosPurgatoryModifier.HELL_SPELL);
        if (spell == null) return;
        boolean ok = IronSpellsSpellAccess.cast(player, spell, TilosPurgatoryModifier.HELL_LEVEL);
        TinkersNewlife.LOGGER.debug("[提洛斯炼狱] 地狱浮现 Lv{} 释放{}",
                TilosPurgatoryModifier.HELL_LEVEL, ok ? "成功" : "失败（法术不可用）");
    }

    // ============================================================
    //  到期消失
    // ============================================================

    private static void despawnExpired(ServerLevel level, long now) {
        if (KNIGHTS.isEmpty()) return;
        for (UUID id : KNIGHTS.keySet().toArray(new UUID[0])) {
            Long until = KNIGHTS.get(id);
            if (until == null || now < until) continue;
            KNIGHTS.remove(id);
            SPELL_IMMUNE.remove(id);
            Integer entityId = KNIGHT_IDS.remove(id);
            if (entityId == null) continue;
            Entity e = level.getEntity(entityId);
            if (e != null && e.getUUID().equals(id)) {            // 核对 UUID，防实体 id 复用
                e.discard();
            }
        }
    }

    // ============================================================
    //  骑士不该打自己人
    // ============================================================

    /**
     * 铁魔法的远古骑士自带敌对目标（会锁定附近的怪物/玩家）—— 干掉攻击者之后
     * <b>两只骑士会互相锁定并把对方当怪打</b>。这里从目标选择的源头拦掉：
     * 目标是"主人的其它仆从"（带 {@link WuWeiHandler#KEY_GUARD_OWNER} 标记）或主人本人 → 取消。
     */
    @SubscribeEvent
    public static void onFriendlyTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!isOurKnight(mob)) return;
        var target = event.getNewTarget();
        if (target == null) return;
        // 打另一只骑士 / 主人的其它仆从
        if (target.getPersistentData().contains(WuWeiHandler.KEY_GUARD_OWNER)) {
            event.setCanceled(true);
            return;
        }
        // 打主人本人
        if (target instanceof net.minecraft.world.entity.player.Player) {
            UUID ownerId = mob.getPersistentData().contains(WuWeiHandler.KEY_GUARD_OWNER)
                    ? mob.getPersistentData().getUUID(WuWeiHandler.KEY_GUARD_OWNER) : null;
            if (ownerId != null && ownerId.equals(target.getUUID())) {
                event.setCanceled(true);
            }
        }
    }

    /** 这只怪是不是本特性召唤的远古骑士（按召唤记录判定，不依赖类名） */
    private static boolean isOurKnight(Mob mob) {
        return KNIGHTS.containsKey(mob.getUUID());
    }

    /** 兜底：把已经互相锁定的目标清掉（目标事件偶尔会被别的 goal 绕过） */
    private static void clearFriendlyTargets(ServerLevel level, long now) {
        if (KNIGHTS.isEmpty()) return;
        for (UUID id : KNIGHTS.keySet()) {
            Integer entityId = KNIGHT_IDS.get(id);
            if (entityId == null) continue;
            Entity e = level.getEntity(entityId);
            if (!(e instanceof Mob mob) || !mob.getUUID().equals(id)) continue;
            var target = mob.getTarget();
            if (target == null) continue;
            boolean friendly = target.getPersistentData().contains(WuWeiHandler.KEY_GUARD_OWNER)
                    || (mob.getPersistentData().contains(WuWeiHandler.KEY_GUARD_OWNER)
                        && mob.getPersistentData().getUUID(WuWeiHandler.KEY_GUARD_OWNER).equals(target.getUUID()));
            if (friendly) mob.setTarget(null);
        }
    }

    // ============================================================
    //  对仆从无伤害
    // ============================================================

    /** 处于免疫窗口内的远古骑士：免疫铁魔法法术造成的伤害（地狱浮现对仆从无伤害） */
    @SubscribeEvent
    public static void onServantSpellDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (mob.level().isClientSide) return;
        UUID id = mob.getUUID();
        Long until = SPELL_IMMUNE.get(id);
        if (until == null) return;
        if (mob.level().getGameTime() > until) {
            SPELL_IMMUNE.remove(id);
            return;
        }
        String damageId = event.getSource().getMsgId();
        if (damageId != null && damageId.toLowerCase().startsWith("irons_spellbooks.")) {
            event.setCanceled(true);
        }
    }
}
