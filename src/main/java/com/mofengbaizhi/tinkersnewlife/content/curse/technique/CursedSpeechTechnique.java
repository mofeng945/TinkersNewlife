package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.StunHandler;
import com.mofengbaizhi.tinkersnewlife.content.cursespeech.CurseChant;
import com.mofengbaizhi.tinkersnewlife.content.cursespeech.CursedSpeechRegistry;
import com.mofengbaizhi.tinkersnewlife.content.cursespeech.CursedSpeechState;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetUtil;
import com.mofengbaizhi.tinkersnewlife.content.modifier.CurseSpeedModifier;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenCursedSpeechScreen;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 术式「咒言术」：
 * <p>
 * 只有顺转：按下术式键（C）咏唱当前咒言——每句咒言由六段组成
 * 「咏叹词，敬称+对象，祈求语+核心义，结谢语！」。
 * 反转键（F）打开咒言编辑界面（需学会对应词条）。
 * <p>
 * 段位影响：
 * <ul>
 *   <li>咏叹词 → 咒力消耗倍率（越稀有消耗越低：雑鱼 1.5x … 啊呀 1.0x）</li>
 *   <li>敬称   → 作用强度倍率（愚蠢的 0.8x … 不可名状的 3.0x）</li>
 *   <li>祈求语 → 持续时间倍率（命令你 0.8x … 拜求您 2.0x）</li>
 *   <li>结谢语 → 反噬削减倍率（赏赐 1.0x … 奉献一切 0.5x）</li>
 *   <li>对象   → 附带效果（奈亚/莎布/犹格仅限不可名状敬称）</li>
 *   <li>核心义 → 主效果（治疗/虚弱/禁锢/点燃/雷霆…）</li>
 * </ul>
 * 咏唱需读条（时长 = (基础 15 tick + 总稀有度 × 4) × 咒速输出倍率），期间减速；
 * <p>
 * <b>施术者加成</b>：咒力输出与咒力亲和决定「咒力强度」{@code power = 1 + (输出 + 亲和/10)/10}。
 * 强度同时放大作用效果（治疗量/持续时长/伤害）与<b>影响范围</b>
 * （半径 = 6 格 × power，对范围内所有非友方活体生效，最近者为主目标）。
 * 释放后结算反噬：取范围内血量占比最高的目标计算，
 * 自身咒力输出与亲和提供抵抗（output*10% + affinity*0.3%），反噬扣除自身血量。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CursedSpeechTechnique extends BaseTechnique {

    public static final CursedSpeechTechnique INSTANCE = new CursedSpeechTechnique();

    /** 吟唱读条基础时长（tick） */
    private static final int BASE_CHANT_TICKS = 15;
    /** 每级稀有度附加读条 tick */
    private static final int CHANT_PER_RARITY = 4;
    /** 瞄准射线距离（用于决定咒言中心点） */
    private static final double REACH = 16.0;
    /** 范围效果基础半径（格），实际半径 = 本值 × 咒力强度 */
    private static final double AOE_RADIUS = 6.0;

    /** 莎布尼古拉斯增殖追踪：uuid → 到期时刻 */
    private static final Map<UUID, Long> SHUB_GROWTH = new ConcurrentHashMap<>();
    /** 犹格索托斯经验追踪：uuid → 剩余 tick */
    private static final Map<UUID, Integer> YOG_EXP = new ConcurrentHashMap<>();

    private CursedSpeechTechnique() {
        super(Modifiers.CURSED_SPEECH.getId());
    }

    // ============================================================
    //  反转（F）：打开咒言编辑器
    // ============================================================

    @Override
    public void onReverseKeyPress(ServerPlayer player) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        if (CurseChant.isChanting(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.cursed_speech.chanting"), true);
            return;
        }
        // 确保默认词条已植入
        CursedSpeechState.chant(player);
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketOpenCursedSpeechScreen(
                        CursedSpeechState.learned(player),
                        java.util.Arrays.asList(CursedSpeechState.chant(player))));
    }

    // ============================================================
    //  顺转（C）：咏唱
    // ============================================================

    @Override
    public void onKeyPress(ServerPlayer player) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        if (CurseChant.isChanting(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.cursed_speech.chanting"), true);
            return;
        }
        String[] chant = CursedSpeechState.chant(player);
        // 逐段校验已学会；缺段回退后仍缺 → 提示先按 F 学习/编辑
        for (int i = 0; i < chant.length; i++) {
            String id = chant[i];
            if (id == null || id.isEmpty() || !CursedSpeechRegistry.exists(id)
                    || !CursedSpeechState.knows(player, id)) {
                player.displayClientMessage(Component.translatable(
                        "message.tinkersnewlife.cursed_speech.missing_part"), true);
                return;
            }
        }
        CursedSpeechRegistry.Word honorific = CursedSpeechRegistry.get(chant[1]);
        CursedSpeechRegistry.Word targetWord = CursedSpeechRegistry.get(chant[2]);
        // 对象敬称约束：奈亚/莎布/犹格仅限不可名状的
        boolean needUnnameable = targetWord != null && switch (targetWord.id()) {
            case CursedSpeechRegistry.OBJ_NYA,
                 CursedSpeechRegistry.OBJ_SHUB,
                 CursedSpeechRegistry.OBJ_YOG -> true;
            default -> false;
        };
        if (needUnnameable && (honorific == null || !honorific.id().equals("unnameable"))) {
            player.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.cursed_speech.honor_required"), true);
            return;
        }
        // 咏叹词决定咒力消耗：基础消耗 × 咏叹系数
        CursedSpeechRegistry.Word ex = CursedSpeechRegistry.get(chant[0]);
        double exMul = 1.5 - ex.rarity() * 0.1; // 雑鱼1.5 → 啊呀1.0
        int cost = Math.max(1, (int) Math.ceil(getCost(player) * exMul));
        if (!CursePowerHelper.isCurseInfinite(player)
                && CursePowerHelper.payCurseWithSoulFallback(player, cost) < 0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        // 读条：时长随稀有度提升，并被「咒速输出」缩短
        int raritySum = 0;
        for (String id : chant) {
            CursedSpeechRegistry.Word w = CursedSpeechRegistry.get(id);
            if (w != null) raritySum += w.rarity();
        }
        double speed = CurseSpeedModifier.chantScale(player);
        int total = Math.max(1, (int) Math.round((BASE_CHANT_TICKS + raritySum * CHANT_PER_RARITY) * speed));
        CurseChant.start(player, total, chantLabel(chant));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.EVOKER_PREPARE_SUMMON,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.3F);
        // 立即同步一次读条（进度条显示）
        syncChant(player);
    }

    /** 拼接咒言标题（展示用）：咏叹，敬称对象，祈求核心，结谢！ */
    private static String chantLabel(String[] chant) {
        StringBuilder sb = new StringBuilder();
        sb.append(trans(chant[0]));
        sb.append("，");
        sb.append(trans(chant[1]));
        sb.append(trans(chant[2]));
        sb.append("，");
        sb.append(trans(chant[3]));
        sb.append(trans(chant[4]));
        sb.append("，");
        sb.append(trans(chant[5]));
        sb.append("！");
        return sb.toString();
    }

    private static String trans(String id) {
        CursedSpeechRegistry.Word w = CursedSpeechRegistry.get(id);
        return w == null ? id : Component.translatable(w.langKey()).getString();
    }

    // ============================================================
    //  服务端 tick 驱动（由主类对所有在线玩家调用）
    // ============================================================

    /** 每 tick：读条推进 / 莎布增殖 / 犹格经验 */
    public static void tickServer(ServerLevel level, ServerPlayer player) {
        long now = level.getGameTime();
        if (CurseChant.isChanting(player)) {
            if (!player.isAlive() || player.isRemoved()) {
                CurseChant.clear(player);
                return;
            }
            long left = CurseChant.remaining(player);
            if (left <= 0) {
                finishChant(player);
            } else {
                if (now % 4 == 0) {
                    syncChant(player);
                }
                // 咏唱粒子
                level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.6, player.getZ(),
                        2, 0.4, 0.3, 0.4, 0);
            }
        }
        tickShubGrowth(level, player, now);
        tickYogExp(player);
    }

    /** 推送读条进度（remaining + total + label） */
    private static void syncChant(ServerPlayer player) {
        long total = player.getPersistentData().getLong(CurseChant.KEY_TOTAL);
        long remaining = Math.max(0, CurseChant.remaining(player));
        String label = CurseChant.label(player);
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new com.mofengbaizhi.tinkersnewlife.network.curse.PacketSyncCursedChant(remaining, total, label));
    }

    /** 读条完成 → 释放 */
    private static void finishChant(ServerPlayer player) {
        String label = CurseChant.label(player);
        CurseChant.clear(player);
        String[] chant = CursedSpeechState.chant(player);
        // 重新构建显示
        if (label == null || label.isEmpty()) {
            label = chantLabel(chant);
        }
        List<LivingEntity> targets = findChantTargets(player);
        if (targets.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.technique.no_target"), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(),
                30, 1.5, 0.8, 1.5, 0.02);
        // 释放（范围内全体）
        INSTANCE.release(player, targets, chant);
        // 结算反噬（取血量占比最高的目标）
        INSTANCE.applyBacklash(player, targets, chant);
        // 以玩家口吻向公屏广播完整咒文（模仿玩家发言：名字 + 「完整咒文」）
        String spoken = "「" + label + "」";
        Component spokenMsg = Component.translatable("chat.type.text",
                player.getDisplayName(),
                Component.literal(spoken).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        level.getServer().getPlayerList().broadcastSystemMessage(spokenMsg, false);
    }

    // ============================================================
    //  目标选取与咒力强度
    // ============================================================

    /**
     * 咒力强度：{@code 1 + (咒力输出等级 + 咒力亲和/10) / 10}。
     * 输出 3 级 + 亲和 30 → 1.6 倍；输出 5 级 + 亲和 50 → 2.0 倍。
     * 同时放大效果数值与作用半径。
     */
    public static double powerScale(ServerPlayer player) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        return 1.0 + (output + affinity / 10.0) / 10.0;
    }

    /** 瞄准射线命中的活体（仅用于决定咒言中心点，可为 null） */
    private static LivingEntity findAimedTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(REACH));
        AABB box = player.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.0);
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player, eye, end, box,
                e -> !e.isSpectator() && e.isPickable() && e instanceof LivingEntity le && le.isAlive()
                        && !PuppetUtil.isAllyOf(le, player),
                REACH * REACH);
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    /**
     * 咒言作用目标：以「瞄准到的目标」为中心（未瞄到则以自身为中心），
     * 半径 {@code 6 格 × 咒力强度} 内的所有非友方活体，按距离由近到远排序。
     */
    private static List<LivingEntity> findChantTargets(ServerPlayer player) {
        double radius = AOE_RADIUS * powerScale(player);
        LivingEntity aimed = findAimedTarget(player);
        Vec3 center = aimed != null ? aimed.position() : player.position();
        ServerLevel level = player.serverLevel();
        AABB box = new AABB(center, center).inflate(radius);
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (le == player || le.isRemoved() || !le.isAlive() || le.isSpectator()) continue;
            if (PuppetUtil.isAllyOf(le, player)) continue;
            if (le.position().distanceTo(center) > radius) continue;
            list.add(le);
        }
        list.sort(Comparator.comparingDouble(le -> le.position().distanceToSqr(center)));
        return list;
    }

    // ============================================================
    //  释放效果
    // ============================================================

    private void release(ServerPlayer player, List<LivingEntity> targets, String[] chant) {
        CursedSpeechRegistry.Word honorific = CursedSpeechRegistry.get(chant[1]);
        CursedSpeechRegistry.Word obj = CursedSpeechRegistry.get(chant[2]);
        CursedSpeechRegistry.Word pray = CursedSpeechRegistry.get(chant[3]);
        CursedSpeechRegistry.Word core = CursedSpeechRegistry.get(chant[4]);
        double power = powerScale(player);
        double honorMul = honorific == null ? 1.0 : (0.8 + honorific.rarity() * 0.35);
        double durMul = pray == null ? 1.0 : (0.8 + pray.rarity() * 0.2);
        // 默认 6s × 祈求倍率 × 咒力强度
        int baseDur = Math.max(20, (int) (120 * durMul * power));

        String fx = core == null ? CursedSpeechRegistry.FX_HEAL : core.id();
        String objectId = obj == null ? null : obj.id();
        // 吸血类回复量累计，最后一次性结算（避免逐目标重复触发治疗统计）
        double[] selfHeal = {0.0};
        boolean primary = true;
        for (LivingEntity target : targets) {
            if (target == null || target.isRemoved() || !target.isAlive()) continue;
            applyCore(player, target, fx, honorMul, power, baseDur, selfHeal);
            applyObjectEffect(player, target, objectId, honorMul, durMul, power, primary, selfHeal);
            primary = false;
        }
        if (selfHeal[0] > 0) {
            player.heal((float) selfHeal[0]);
        }
    }

    /** 单个目标的核心义效果 */
    private void applyCore(ServerPlayer player, LivingEntity target, String fx,
                           double honorMul, double power, int baseDur, double[] selfHeal) {
        ServerLevel level = player.serverLevel();
        switch (fx) {
            case CursedSpeechRegistry.FX_HEAL -> {
                double heal = (6.0 + 4.0 * honorMul) * power;
                target.heal((float) heal);
                level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + 1.0, target.getZ(),
                        8, 0.5, 0.5, 0.5, 0);
            }
            case CursedSpeechRegistry.FX_WEAK ->
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, baseDur, 1, false, true));
            case CursedSpeechRegistry.FX_BIND -> {
                target.addEffect(new MobEffectInstance(ModEffects.STUN.get(), baseDur, 0, false, true));
                if (target instanceof net.minecraft.world.entity.Mob mob) {
                    StunHandler.onStunApplied(mob);
                }
            }
            case CursedSpeechRegistry.FX_BLIND ->
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, baseDur, 0, false, true));
            case CursedSpeechRegistry.FX_BURN ->
                    target.setSecondsOnFire(Math.max(3, baseDur / 20 + 2));
            case CursedSpeechRegistry.FX_SICK -> {
                target.addEffect(new MobEffectInstance(MobEffects.HUNGER, baseDur, 2, false, true));
                target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, Math.min(200, baseDur), 0, false, true));
            }
            case CursedSpeechRegistry.FX_POISON -> {
                // 令他腐烂：持续中毒
                target.addEffect(new MobEffectInstance(MobEffects.POISON, baseDur, 1, false, true));
                level.sendParticles(ParticleTypes.ITEM_SLIME,
                        target.getX(), target.getY() + 1.0, target.getZ(), 10, 0.4, 0.4, 0.4, 0.02);
            }
            case CursedSpeechRegistry.FX_EXPLODE -> {
                // 爆破他：小型咒力爆裂（不破坏方块、不引火），中心咒术伤害随距离衰减
                target.invulnerableTime = 0;
                float dmg = (float) amplifyTechniqueDamage(player, 9.0 * honorMul * power);
                target.hurt(level.damageSources().magic(), dmg);
                float boom = Math.min(3.0F, 1.2F + 0.3F * (float) (honorMul * power));
                level.explode(player, target.getX(), target.getY() + 0.5, target.getZ(),
                        boom, false, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
            }
            case CursedSpeechRegistry.FX_LEECH -> {
                // 榨取他：咒术伤害并吸取半数伤害回复自身
                target.invulnerableTime = 0;
                float dmg = (float) amplifyTechniqueDamage(player, 7.0 * honorMul * power);
                target.hurt(level.damageSources().magic(), dmg);
                selfHeal[0] += dmg * 0.5;
                level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 1.0, target.getZ(),
                        12, 0.4, 0.5, 0.4, 0.02);
            }
            case CursedSpeechRegistry.FX_FREEZE -> {
                target.addEffect(new MobEffectInstance(ModEffects.FROST.get(), baseDur, 0, false, true));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, baseDur, 2, false, true));
            }
            case CursedSpeechRegistry.FX_THUNDER -> {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(target.getX(), target.getY(), target.getZ());
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                target.invulnerableTime = 0;
                float dmg = (float) amplifyTechniqueDamage(player, 8.0 * honorMul * power);
                target.hurt(level.damageSources().magic(), dmg);
            }
            case CursedSpeechRegistry.FX_ATTACK -> {
                // 攻击核心义本身已按咒力输出/亲和计算，故不再叠加 power，避免双重加成
                target.invulnerableTime = 0;
                double raw = (1.0 + CursePowerHelper.getCurseAffinity(player) / 100.0)
                        * (10.0 + CursePowerHelper.getCurseOutputLevel(player) * 5.0) * honorMul;
                raw = com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper
                        .applyCurseCoreTraits(player, target, raw);
                target.hurt(level.damageSources().mobAttack(player), (float) raw);
                com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper
                        .afterCurseCoreHit(player, target, raw);
            }
            case CursedSpeechRegistry.FX_KNOCK -> {
                Vec3 dir = target.position().subtract(player.position()).normalize();
                double push = 2.0 * power;
                target.setDeltaMovement(target.getDeltaMovement().add(dir.x * push, 0.8, dir.z * push));
                target.hurtMarked = true;
            }
            default -> { /* 未知核心义不生效 */ }
        }
    }

    /**
     * 对象附带效果（奈亚/莎布/犹格等）。
     *
     * @param primary  是否为主目标（最近者）：仅自身增益类效果（爱莉希雅回血、奈亚护体、犹格经验）只结算一次
     * @param selfHeal 自身回复累计（主目标治疗类一并记入）
     */
    private void applyObjectEffect(ServerPlayer player, LivingEntity target, String objectId,
                                   double honorMul, double durMul, double power,
                                   boolean primary, double[] selfHeal) {
        if (objectId == null) return;
        int dur = (int) (100 * durMul * power);
        switch (objectId) {
            case CursedSpeechRegistry.OBJ_WUZU -> {
                // 咒之祖巫：效果强度已由 honorMul 覆盖（本函数仅占位）
            }
            case CursedSpeechRegistry.OBJ_IDIOT -> {
                // 智力残缺大哥哥：反噬增强（见 applyBacklash）
            }
            case CursedSpeechRegistry.OBJ_CTHULHU -> {
                // 克图露：天体秩序之音 → 目标眩晕/失明复合
                target.addEffect(new MobEffectInstance(ModEffects.STUN.get(), Math.min(dur, 60), 0, false, true));
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, dur, 0, false, true));
                if (target instanceof net.minecraft.world.entity.Mob mob) {
                    StunHandler.onStunApplied(mob);
                }
            }
            case CursedSpeechRegistry.OBJ_ELYSIA -> {
                // 爱莉希雅：霜冻 + 治疗自身（回血仅主目标结算一次）
                target.addEffect(new MobEffectInstance(ModEffects.FROST.get(), dur, 0, false, true));
                if (primary) {
                    selfHeal[0] += (4.0 + 3.0 * honorMul) * power;
                    player.serverLevel().sendParticles(ParticleTypes.HEART,
                            player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.5, 0.5, 0.5, 0);
                }
            }
            case CursedSpeechRegistry.OBJ_RYOMEN -> {
                // 两面宿傩：借其业火，目标被点燃并陷入虚弱
                target.setSecondsOnFire(Math.max(4, dur / 20 + 2));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, dur, 1, false, true));
            }
            case CursedSpeechRegistry.OBJ_HASTUR -> {
                // 黄衣之王：低语侵扰，目标失明并迟缓
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, dur, 0, false, true));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 1, false, true));
                player.serverLevel().sendParticles(ParticleTypes.SCULK_SOUL,
                        target.getX(), target.getY() + 1.0, target.getZ(), 14, 0.5, 0.4, 0.5, 0.01);
            }
            case CursedSpeechRegistry.OBJ_NYA -> {
                // 奈亚拉托提普：给予自身不可名状（仅主目标一次）+ 使目标定住
                if (primary) {
                    player.addEffect(new MobEffectInstance(ModEffects.UNNAMEABLE.get(), dur, 0, false, true));
                }
                target.addEffect(new MobEffectInstance(ModEffects.STUN.get(), Math.min(dur, 80), 0, false, true));
                if (target instanceof net.minecraft.world.entity.Mob mob) {
                    StunHandler.onStunApplied(mob);
                }
            }
            case CursedSpeechRegistry.OBJ_SHUB -> {
                // 莎布尼古拉斯：目标血量不断增长，超上限 2 倍爆裂（范围内全体生效）
                SHUB_GROWTH.put(target.getUUID(), player.serverLevel().getGameTime() + dur);
            }
            case CursedSpeechRegistry.OBJ_YOG -> {
                // 犹格索托斯：持续提供经验（仅主目标一次）
                if (primary) {
                    YOG_EXP.put(player.getUUID(), dur);
                }
            }
            default -> { }
        }
    }

    // ============================================================
    //  反噬
    // ============================================================

    /**
     * 反噬：取范围内血量占比最高的目标计算，占比越高反噬越强。
     * 基础反噬 = 目标当前血/最大血 × 14 + 6，再乘对象系数(智力残缺 ×1.5)与结谢削减。
     * 自身输出（每级 -10%）与亲和（每点 -0.3%）抵抗；反噬扣自身真实血量。
     */
    private void applyBacklash(ServerPlayer player, List<LivingEntity> targets, String[] chant) {
        CursedSpeechRegistry.Word thanks = CursedSpeechRegistry.get(chant[5]);
        CursedSpeechRegistry.Word obj = CursedSpeechRegistry.get(chant[2]);
        double objMul = obj != null && CursedSpeechRegistry.OBJ_IDIOT.equals(obj.id()) ? 1.5 : 1.0;
        double backMul = thanks == null ? 1.0 : Math.max(0.5, 1.0 - thanks.rarity() * 0.1);

        double hpRatio = 1.0;
        double worst = -1.0;
        for (LivingEntity t : targets) {
            if (t == null || t.isRemoved()) continue;
            double ratio = t.getHealth() / Math.max(1.0f, t.getMaxHealth());
            if (ratio > worst) {
                worst = ratio;
            }
        }
        if (worst >= 0) {
            hpRatio = Math.min(1.0, worst);
        }
        double raw = 6.0 + hpRatio * 14.0;

        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        double resist = Math.min(0.8, output * 0.10 + affinity * 0.003);

        double finalDamage = raw * objMul * backMul * (1.0 - resist);
        // 反噬不超过自身当前血-1（避免自杀）
        double actual = Math.min(finalDamage, Math.max(0, player.getHealth() - 1.0));
        if (actual <= 0) {
            player.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.cursed_speech.backlash_zero"), true);
            return;
        }
        player.hurt(player.damageSources().magic(), (float) actual);
        player.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.cursed_speech.backlash", (int) Math.ceil(actual)), true);
        player.serverLevel().sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                player.getX(), player.getY() + 1.2, player.getZ(), 6, 0.4, 0.6, 0.4, 0);
    }

    // ============================================================
    //  莎布 / 犹格 tick
    // ============================================================

    private static void tickShubGrowth(ServerLevel level, ServerPlayer player, long now) {
        if (SHUB_GROWTH.isEmpty()) return;
        SHUB_GROWTH.entrySet().removeIf(e -> {
            if (e.getValue() <= now) return true;
            LivingEntity target = level.getEntity(e.getKey()) instanceof LivingEntity le ? le : null;
            if (target == null || !target.isAlive()) return true;
            // 不断恢复血量（模拟增殖）
            target.heal(1.0F);
            if (target.getHealth() > target.getMaxHealth() * 2.0F) {
                // 爆裂！
                target.invulnerableTime = 0;
                target.hurt(level.damageSources().magic(), 60.0F);
                level.explode(null, target.getX(), target.getY(), target.getZ(), 3.0F,
                        false, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
                return true;
            }
            return false;
        });
    }

    private static void tickYogExp(ServerPlayer player) {
        if (YOG_EXP.isEmpty()) return;
        Integer left = YOG_EXP.get(player.getUUID());
        if (left == null) return;
        if (left <= 0) {
            YOG_EXP.remove(player.getUUID());
            return;
        }
        YOG_EXP.put(player.getUUID(), left - 1);
        if (left % 20 == 0) {
            player.giveExperiencePoints(1);
        }
    }

    // ============================================================
    //  读条受击打断（不返还咒力，与构筑拟造一致）
    // ============================================================

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getEntity() instanceof ServerPlayer sp && CurseChant.isChanting(sp)) {
            CurseChant.clear(sp);
            sp.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.cursed_speech.interrupted"), true);
        }
    }

    /** 登出/死亡清理 */
    public static void cleanup(ServerPlayer player) {
        if (CurseChant.isChanting(player)) {
            CurseChant.clear(player);
        }
        SHUB_GROWTH.remove(player.getUUID());
        YOG_EXP.remove(player.getUUID());
    }
}
