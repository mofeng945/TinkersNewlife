package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler;
import com.mofengbaizhi.tinkersnewlife.content.modifier.TilosPurgatoryModifier;
import com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks.IronSpellsSpellAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
 *   <li><b>仆从处理走"咒灵操术·仆从类"</b>：{@code CursedSpiritTechnique#stripTargetGoals} +
 *       每 tick 指派目标（{@link #driveKnights}）—— 只摘目标选择，<b>骑士的原生 AI 原样保留</b>
 *       （{@code KeeperAnimatedWarlockAttackGoal} 的连招/施法/动画照常），
 *       目标由我们喂给 {@code getTarget()}；本模组召唤物都带
 *       {@link WuWeiHandler#KEY_GUARD_OWNER} 标记，彼此视作队友；
 *       <br>⚠ <b>不要改用 {@code WuWeiHandler#attachGuardAi}</b>：那套会清空 goalSelector 并改成
 *       玉犬式近战追击，适合"玉犬式守护形态"，用在远古骑士身上等于把它的招式全废掉；</li>
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

    /** 召唤后的免疫窗口（tick）：30s，覆盖地狱浮现的火焰与残留 */
    private static final int IMMUNE_TICKS = 30 * 20;

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
        extinguishKnights(player.serverLevel(), now);
        driveKnights(player.serverLevel(), player);
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
    //  召唤远古骑士（套用咒灵操术·仆从类：只摘目标选择，保留骑士原生 AI）
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

            // ⭐ 咒灵操术那套"仆从通用处理"：<b>只摘目标选择，保留骑士原生 AI</b> ——
            //    ISS 的远古骑士自带 {@code KeeperAnimatedWarlockAttackGoal}（连招/施法/动画），
            //    绝不能连 goalSelector 一起清空（那会退化成"慢速近战僵尸"，骑士的招式全没了）。
            //    目标统一由 {@link #driveKnights} 每 tick 指派。
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpiritTechnique
                    .stripTargetGoals(mob);
            // 同时挂上仆从归属标记：其它召唤物/咒灵释放体会据此把骑士当队友而不攻击它
            mob.getPersistentData().putUUID(WuWeiHandler.KEY_GUARD_OWNER, player.getUUID());

            // 地狱浮现会在地面留下火焰：给仆从防火（它们本来就是火系骑士，这里只是保险）+ 立即灭火
            mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, IMMUNE_TICKS, 0, false, false));
            mob.setRemainingFireTicks(0);
            KNIGHTS.put(mob.getUUID(), now + TilosPurgatoryModifier.KNIGHT_LIFETIME);
            KNIGHT_IDS.put(mob.getUUID(), mob.getId());
            SPELL_IMMUNE.put(mob.getUUID(), now + IMMUNE_TICKS);
        }

    }

    /** 无吟唱、以玩家为中心释放地狱浮现 */
    private static void castHell(ServerPlayer player) {
        Object spell = IronSpellsSpellAccess.spellById(TilosPurgatoryModifier.HELL_SPELL);
        if (spell == null) {

            return;
        }
        int level = TilosPurgatoryModifier.HELL_LEVEL;
        int before = IronSpellsSpellAccess.manaOf(player);
        int cost = IronSpellsSpellAccess.manaCostOf(spell, level);
        // 特性赠送：法力不足就临时垫上，施法后原样还原（这一发是"白送"的，不该因缺蓝而哑火）
        if (cost > 0 && before >= 0 && before < cost) {
            IronSpellsSpellAccess.setMana(player, cost);
        }
        boolean ok;
        try {
            ok = IronSpellsSpellAccess.cast(player, spell, level);
        } finally {
            if (cost > 0 && before >= 0) {
                IronSpellsSpellAccess.setMana(player, before);
            }
        }

    }

    // ============================================================
    //  每 tick 指派目标（咒灵操术·仆从类同款：只指目标，不动原生 AI）
    // ============================================================

    /**
     * 给在世的骑士指派目标 + 维持跟随。目标选择与咒灵操术仆从一致：
     * <b>主人攻击的目标 &gt; 攻击主人的目标 &gt; （都没有就）清掉指向主人/同队的目标</b>。
     *
     * <p>骑士的原生 {@code KeeperAnimatedWarlockAttackGoal} 只认 {@code getTarget()}：
     * 我们每 tick 把目标喂进去，它自己会走位、连招、施法、播动画 ——
     * 这才是"远古骑士"，而不是被清空 AI 后的近战木头人。
     */
    private static void driveKnights(ServerLevel level, ServerPlayer owner) {
        if (KNIGHTS.isEmpty()) return;
        for (UUID id : KNIGHTS.keySet()) {
            Mob knight = knightOf(level, id);
            if (knight == null) continue;

            // 1) 指派目标
            LivingEntity want = null;
            LivingEntity attack = owner.getLastHurtMob();
            if (isValidTarget(attack, owner, knight)) want = attack;
            if (want == null) {
                LivingEntity threat = owner.getLastHurtByMob();
                if (isValidTarget(threat, owner, knight)) want = threat;
            }
            if (want != null) {
                knight.setTarget(want);
            } else {
                // 没有指令：清掉指向主人/同队骑士的目标（不给它们自选主人的机会）
                LivingEntity cur = knight.getTarget();
                if (cur == null || !cur.isAlive() || isAlly(cur, owner, knight)) {
                    knight.setTarget(null);
                }
            }

            // 2) 跟随主人（骑士的走位由攻击 AI 负责，这里只管"跟丢/贴身"两种情况）
            double dSq = knight.distanceToSqr(owner);
            if (dSq > 64.0 * 64.0) {
                double dx = (owner.getRandom().nextDouble() - 0.5) * 2.0;
                double dz = (owner.getRandom().nextDouble() - 0.5) * 2.0;
                knight.teleportTo(owner.getX() + dx, owner.getY(), owner.getZ() + dz);
                knight.getNavigation().stop();
            } else if (knight.getTarget() == null) {
                if (dSq > 6.0 * 6.0) {
                    knight.getNavigation().moveTo(owner, 1.05);
                } else {
                    knight.getNavigation().stop();
                }
            }
        }
    }

    /** 该目标能不能打：活着、不是自己、不属于主人阵营（主人本人/其它仆从/式神/咒灵释放体都排除） */
    private static boolean isValidTarget(LivingEntity target, ServerPlayer owner, Mob knight) {
        return target != null && target.isAlive() && target != knight && !isAlly(target, owner, knight);
    }

    /** 是不是"主人这一边"（主人本人、主人的其它仆从、式神、咒灵释放体、Goety 仆从都算） */
    private static boolean isAlly(LivingEntity target, ServerPlayer owner, Mob knight) {
        if (target == null || target == knight) return true;
        if (target == owner) return true;
        if (com.mofengbaizhi.tinkersnewlife.content.entity.PuppetUtil.isAllyOf(target, owner)) return true;
        return WuWeiHandler.isSameOwnerMinion(target, owner);
    }

    /** 按记录取回在世的骑士（核对 UUID，防实体 id 复用） */
    private static Mob knightOf(ServerLevel level, UUID id) {
        Integer entityId = KNIGHT_IDS.get(id);
        if (entityId == null) return null;
        Entity e = level.getEntity(entityId);
        return e instanceof Mob mob && mob.getUUID().equals(id) ? mob : null;
    }

    /** 到期消失 */
    private static void despawnExpired(ServerLevel level, long now) {
        if (KNIGHTS.isEmpty()) return;
        for (UUID id : KNIGHTS.keySet().toArray(new UUID[0])) {
            Long until = KNIGHTS.get(id);
            if (until == null || now < until) continue;
            // ⚠ 先取实体再删记录：knightOf 要读 KNIGHT_IDS
            Mob knight = knightOf(level, id);
            KNIGHTS.remove(id);
            SPELL_IMMUNE.remove(id);
            KNIGHT_IDS.remove(id);
            if (knight != null) knight.discard();
        }
    }

    // ============================================================
    //  骑士不该打自己人
    // ============================================================

    /**
     * 兜底拦截：目标是"主人的其它仆从"（带 {@link WuWeiHandler#KEY_GUARD_OWNER} 标记）
     * 或主人本人 → 取消。
     *
     * <p>主防线其实是召唤时的 {@code stripTargetGoals}（骑士不再自选目标，目标由
     * {@link #driveKnights} 指派）；这里保留是因为别的 goal / 第三方模组仍可能直接调用
     * {@code setTarget}，有这一层就不会出现"两只骑士互殴"。
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

    /** 兜底：把已经锁到友军身上的目标清掉（目标事件偶尔会被别的 goal 绕过） */
    private static void clearFriendlyTargets(ServerLevel level, long now) {
        if (KNIGHTS.isEmpty()) return;
        for (UUID id : KNIGHTS.keySet()) {
            Mob mob = knightOf(level, id);
            if (mob == null) continue;
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

    /**
     * 处于免疫窗口内的远古骑士：免疫"召唤者这次法术"造成的伤害（地狱浮现对仆从无伤害）。
     *
     * <p>⚠ 只按伤害类型前缀（{@code irons_spellbooks.}）判断**不够** —— 实测地狱浮现会伤到仆从：
     * 它的火焰/范围伤害往往挂的是原版伤害类型（{@code in_fire}/{@code on_fire}/{@code magic} 等），
     * 与 ISS 前缀无关。所以这里改成"<b>只要来源是主人</b>（攻击者/直接实体/法术实体所属者）
     * 或 ISS 法术类型"就取消；再叠加召唤时的防火与每 0.5s 灭火双保险。
     */
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
        if (isFromOwner(mob, event.getSource())) {
            event.setCanceled(true);
        }
    }

    /** 这次伤害是不是"召唤他的主人"造成的（含主人的法术实体与火焰） */
    private static boolean isFromOwner(Mob knight, DamageSource src) {
        if (src == null) return false;
        String damageId = src.getMsgId();
        if (damageId != null && damageId.toLowerCase().startsWith("irons_spellbooks.")) return true;
        UUID ownerId = knight.getPersistentData().contains(WuWeiHandler.KEY_GUARD_OWNER)
                ? knight.getPersistentData().getUUID(WuWeiHandler.KEY_GUARD_OWNER) : null;
        if (ownerId == null) return false;
        if (matchesOwner(src.getEntity(), ownerId)) return true;
        if (matchesOwner(src.getDirectEntity(), ownerId)) return true;
        // 法术实体（火墙/火球那类）：看它的 owner/caster
        for (Entity e : new Entity[]{src.getDirectEntity(), src.getEntity()}) {
            if (e == null) continue;
            for (String m : new String[]{"getOwner", "getCaster"}) {
                try {
                    Object o = e.getClass().getMethod(m).invoke(e);
                    if (matchesOwner(o instanceof Entity en ? en : null, ownerId)) return true;
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    private static boolean matchesOwner(Entity entity, UUID ownerId) {
        return entity != null && ownerId.equals(entity.getUUID());
    }

    /** 免疫窗口内持续灭火（地狱浮现留下的火焰会反复点燃仆从） */
    private static void extinguishKnights(ServerLevel level, long now) {
        if (KNIGHTS.isEmpty()) return;
        for (UUID id : KNIGHTS.keySet()) {
            Long until = SPELL_IMMUNE.get(id);
            if (until == null || now > until) continue;
            Mob mob = knightOf(level, id);
            if (mob != null && mob.getRemainingFireTicks() > 0) {
                mob.setRemainingFireTicks(0);
            }
        }
    }
}
