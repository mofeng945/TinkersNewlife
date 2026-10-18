package com.mofengbaizhi.tinkersnewlife.content.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import com.mofengbaizhi.tinkersnewlife.content.entity.ktulu.a;
import com.mofengbaizhi.tinkersnewlife.content.entity.ktulu.c;
import com.mofengbaizhi.tinkersnewlife.content.entity.ktulu.MomoHealthBridge;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 克图露·墨默（Ktulu Momo）—— 星辰魔女·墨默·克图露 ✓
 *
 * <h2>★本次改动（★用户口径 ✓）</h2>
 * <ul>
 *   <li>★**不再继承商人墨默的 AI** ✗ ⇒ 直接继承 {@link Monster} ✓
 *       只留一套标准敌对 AI（★近战/漂浮/游荡/看人✓）；</li>
 *   <li>属性：★626 血 ✓ 20 护甲 ✓ 15 韧性 ✓ 13 攻击 ✓ 0.4 速度 ✓
 *       ★完全抗击退 ✓ ★免疫火焰 ✓；</li>
 *   <li>★**不会被动挂任何负面效果** ✓（★{@code addEffect} 里拒收非正面 ✓）；</li>
 *   <li>★**单次伤害上限 = 最大生命的 6%** ✓；</li>
 *   <li>★**每 tick 记录血量** ✓ 一旦发现超过限伤 ⇒ **回弹补回** ✓；</li>
 *   <li>★受伤后 **1.5 秒无敌**（★无下限同款的无敌窗口：{@code invulnerableTime = 30} ✓）。</li>
 * </ul>
 */
public class KtuluMomo extends Monster implements com.mofengbaizhi.tinkersnewlife.util.TruePierce.PierceAware {

    /** ★单次伤害上限：最大生命的 6% ✓ */
    private static final float DAMAGE_CAP_RATIO = 0.06F;
    /** ★受伤后无敌：1.5 秒 = 30 tick ✓ */
    private static final int INVULN_TICKS = 30;

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.tinkersnewlife.ktulu_momo.bossbar"),
            BossEvent.BossBarColor.WHITE,
            BossEvent.BossBarOverlay.PROGRESS);

    /** ★上一 tick 的血量（★用来查是否突破限伤 ✓） */
    private float lastHealth = -1.0F;

    // ==================== TNL876 ★性能：热点路径缓存 ✓ ====================
    /** ★权威血量缓存（★避免 getHealth 每次都查 map/调 JNI ✓） */
    private float tnl$auth = -1.0F;
    /** TNL893 ★★ 真死标志：★一旦真死 ⇒ ★彻底停止护血/回弹/对外遮蔽 ✓
     *  ★用户反馈：★"如果这一击伤害使他死亡，他又回弹血量，
     *  会进入斜倒抽携阶段，虽然能被打死，但掉落物和死亡判定已经提前了" ✓
     *  ★原因 ✗：★原版在血量到 0 时**立刻走死亡流程**（★掉战利品/判定/倒地 ✓），
     *  ★而我的回弹又把它拉回 1 血 ✗ ⇒ ★复活成"倒地抽携" ✓。
     */
    private boolean tnl$dead = false;

    /** TNL893 ★是否已真死（★供内核/混入判断 ✓） */
    public boolean tnl$isDead() {
        return this.tnl$dead;
    }
    /** ★上次同步给血条的进度（★没变就不发包 ✓） */
    private float tnl$prog = -1.0F;

    /** ★供内核/混入更新缓存 ✓ */
    public void tnl$setAuth(float v) {
        this.tnl$auth = v;
    }

    public KtuluMomo(EntityType<? extends KtuluMomo> type, Level level) {
        super(type, level);
        this.xpReward = 500;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 626.0D)
                .add(Attributes.ARMOR, 20.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 15.0D)
                .add(Attributes.ATTACK_DAMAGE, 13.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.4D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // TNL1251 受击硬直：优先级 0（最高）并占 MOVE|LOOK ⇒ 会挤掉近战/游走，实现"站住 1 秒"
        this.goalSelector.addGoal(0, new com.mofengbaizhi.tinkersnewlife.content.skill.MomoHurtGoal(this));
        // TNL1261 换成只占 MOVE 的近战 Goal：配合技能禁用 LOOK 旗，
        // 施法期间它连起手都做不到 ⇒ 不再一边施法一边往玩家身上寻路。
        this.goalSelector.addGoal(1, new com.mofengbaizhi.tinkersnewlife.content.skill.MomoMeleeGoal(this));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        // TNL1174 技能现在都是 Goal，和诡厄使徒注册法术的方式一样
        // TNL1202 准则规定："开关"应该体现在注册处，而不是散落在
        // 技能内部：只注册打开的技能。方便一次单测一个技能。
        if (com.mofengbaizhi.tinkersnewlife.content.skill.MomoSkills.BLINK) {
            this.goalSelector.addGoal(3, new com.mofengbaizhi.tinkersnewlife.content.skill.BlinkGoal(this));
        }
        if (com.mofengbaizhi.tinkersnewlife.content.skill.MomoSkills.ASTRAL_RESONANCE) {
            this.goalSelector.addGoal(3, new com.mofengbaizhi.tinkersnewlife.content.skill.ResonanceGoal(this));
        }
        if (com.mofengbaizhi.tinkersnewlife.content.skill.MomoSkills.DISCORDANT_CHORD) {
            this.goalSelector.addGoal(3, new com.mofengbaizhi.tinkersnewlife.content.skill.ChordGoal(this));
        }
        // TNL1253 生息绝响：血量 < 80% 时主动放一次（10 秒领域 / 120 秒冷却）
        if (com.mofengbaizhi.tinkersnewlife.content.skill.MomoSkills.BREATH_REQUIEM) {
            this.goalSelector.addGoal(3, new com.mofengbaizhi.tinkersnewlife.content.skill.RequiemGoal(this));
        }
        // TNL1282 拉莱耶之影：血量 <= 50% 时自动召唤 3 个分身（无前摇、不可打断）
        if (com.mofengbaizhi.tinkersnewlife.content.skill.MomoSkills.RLYEH_SHADOW) {
            this.goalSelector.addGoal(3, new com.mofengbaizhi.tinkersnewlife.content.skill.RlyehShadowGoal(this));
        }
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean fireImmune() {
        return true;   // ★免疫火焰 ✓
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    /** ★★ 不接受任何非正面效果 ✓（★被动挂不上 ✓） */
    @Override
    public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
        try {
            if (effect != null && !effect.getEffect().isBeneficial()) {
                return false;
            }
        } catch (Throwable ignored) {
        }
        return super.addEffect(effect, source);
    }

    /**
     * TNL851 ★★ 伤害全部走内核判定 ✓（★限伤 6% + 无敌窗口 ✓）
     *
     * <p>★★ 这里**不读原版血量**做任何判断 ✗ ⇒
     * 逆向改 {@code getHealth()}/原版数据都**不影响**结算 ✓
     * （★原版那个值只是**显示镜像**，每 tick 被内核覆写 ✓）。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        // TNL1282 分身不是 Boss 本体：不吃血量内核（限伤/无敌窗/回弹都没有），走原版受伤
        if (!this.tnl$bossForm()) {
            return super.hurt(source, amount);
        }
        if (this.level().isClientSide || this.tnl$dead) {
            return super.hurt(source, amount);   // TNL893 ★真死后不干预 ✓
        }
        a.q0(this);
        // TNL1282 拉莱耶之影：**分身还活着 ⇒ 本体免疫**（用户口径："不击杀分身无法对墨默造成伤害"）
        if (this.tnl$shadowShieldActive()) {
            return false;
        }
        // TNL871 ★★ 真伤识别：★这些伤害类型不会通知 onTruePierce ✗
        //   ⇒ ★旧写法会把它当普通伤害放行（★实测 25.5 ✓）
        //   ★现在：★**按伤害类型直接拦** ✓ —— ★不掉血 ✗ ★不出伤害数字 ✗ ★不吃惩罚 ✗
        String tnl$id = source.getMsgId();
        if (tnl$id != null && (tnl$id.contains("truePierce") || tnl$id.contains("true_pierce"))) {
            // TNL872 ★★ 用户口径（★最终版 ✓）：
            //   ★真伤**照常打伤害** ✓ —— ★不再一律拦掉 ✗
            //   ★但必须**吃免疫窗的约束** ✓（★窗内打不动 ✗ ★也不能把窗清掉 ✗）
            //   ★所以这里**只记录不拦**✗ ⇒ ★下面走同一套闸门
            //   （q2：无敌窗内返回 0 ✓）+ 限伤（LivingHurt/LivingDamage LOWEST ✓）。
            a.qB("PIERCE", tnl$id, amount, a.qC(this));
        }
        // TNL866 ★★ hurt 只当"闸门"：无敌窗内/预算用完就直接拦 ✓
        //   ★真正的限伤在 LivingDamageEvent（LOWEST）里做 ✓（★因为伤害会在那之前被各特性放大 ✓）
        float gate = a.q2(this, amount, this.tickCount);
        a.qB("GATE", source.getMsgId(), amount, "gate=", gate, a.qC(this));
        if (gate <= 0.0F) {
            if (a.qH()) {
                this.tnl$shieldFlash();     // TNL907 ★窗内挡下 ✓
            }
            return false;
        }
        boolean tnl$hurt = super.hurt(source, amount);
        // TNL913 ★★ 探针：★闸门放行但 super.hurt 返回 false ✗
        //   ★说明是**原版 invulnerableTime**在拦 ✗（★不是我们的窗 ✓）
        //   ★用户口径：★"免疫窗疑似变为 2 倍，后 1.5s 无法接管" ✓
        if (!tnl$hurt && System.currentTimeMillis() - tnl$lastVanillaLog > 800L) {
            tnl$lastVanillaLog = System.currentTimeMillis();
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "TNL913 vanilla blocked: msgId={}, invulnerableTime={}, ourGate={}, state={}",
                    source.getMsgId(), this.invulnerableTime, gate, a.qC(this));
        }
        this.tnl$auth = a.qF(this);   // TNL876 ★受伤后刷新缓存 ✓
        if (this.tnl$auth <= 0.0F) {   // TNL1146 only the mirror may declare it dead, the sword calls die() and isDeadOrDying() would latch the protection off
            this.tnl$dead = true;     // TNL893 ★这一击真的打死了 ⇒ ★不再回弹 ✓
        }
        return tnl$hurt;
    }

    /** ★★ 每 tick 记录血量 ⇒ 一旦突破限伤就**回弹补回** ✓ */
    /**
     * TNL851 ★★ 每 tick：内核回弹 ⇒ 覆写原版值（★仅作显示 ✓）
     * + 血条同步 + 到零就死 ✓。
     */
    /**
     * TNL1273 用户："boss 跑到另一个维度时会新建一个血条，旧血条不消失"。
     * TNL1276 纠正两处 ✗：
     * <ol>
     *   <li>上一版覆写的是 {@code remove(RemovalReason)} ✗ —— <b>跨维度不走那条路</b> ✓
     *       （{@code Entity#changeDimension} 里直接调 {@code setRemoved(CHANGED_DIMENSION)}），
     *       而 {@code setRemoved} 是 <b>{@code public final}</b> ⇒ **覆写不了** ✗
     *       ⇒ 上一版的清理从来没被调到 ✓；</li>
     *   <li>现在分两处钩：{@link #changeDimension}（跨维度，**转移前**先摘玩家 ✓）
     *       ＋ 本方法（击杀 / 区块卸载 / 被替换 ✓）。</li>
     * </ol>
     */
    @Override
    public void remove(net.minecraft.world.entity.Entity.RemovalReason reason) {
        super.remove(reason);
        this.tnl$dropBossBar();
    }

    /** ★跨维度转移：**先**把玩家从这条血条上摘掉，新维度里的新实例再自己挂上 ✓ */
    @Override
    public net.minecraft.world.entity.Entity changeDimension(
            net.minecraft.server.level.ServerLevel level,
            net.minecraftforge.common.util.ITeleporter teleporter) {
        this.tnl$dropBossBar();
        return super.changeDimension(level, teleporter);
    }

    /** ★清空血条订阅 ✓（★关服/断线时连接可能已在拆 ⇒ 绝不能让这里把移除流程带崩 ✓） */
    private void tnl$dropBossBar() {
        try {
            if (this.bossEvent.getPlayers().isEmpty()) {
                return;
            }
            this.bossEvent.removeAllPlayers();
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL1276 momo dropped its old boss bar");
        } catch (Throwable ignored) {
        }
    }

    /**
     * TNL1282 是否是**Boss 本体**：分身（{@link com.mofengbaizhi.tinkersnewlife.content.entity.MomoShadow}）
     * 覆写成 {@code false} ⇒ 不接管血量内核、不挂血条、不吃限伤/保护。
     */
    protected boolean tnl$bossForm() {
        return true;
    }

    /**
     * TNL1283 给**外部**（血量 mixin / 死亡守卫 / 真伤通道）用的公开版本。
     *
     * <p>分身（{@link MomoShadow}）不是 Boss ⇒ 这些 Boss 专属保护必须**整个跳过**，
     * 否则会出现：{@code setHealth} 被 {@code a.qD} 顶回内核值（分身内核没初始化 ⇒ 永远是满血）、
     * 单次伤害被夹成 6%、死亡被"作弊秒杀"逻辑接管等问题。
     */
    public boolean tnl$isBossForm() {
        return this.tnl$bossForm();
    }

    /** TNL893 ★★ 真死钩子：★先置标志 + 注销内核 ✗ ⇒ ★之后不再回弹/不再护血 ✓（★掉落物与死亡判定只白一次 ✓） */
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        if (!this.tnl$bossForm()) {
            super.die(source);
            return;
        }
        // TNL1283 用户："墨默处于分身保护状态下被寰宇剑可以秒掉，血量保护保不住"。
        //   寰宇剑路径是 hurt(最大浮点) ⇒ setHealth(0) ⇒ **die()**：die 不看血量，
        //   所以光在 hurt 里挡不够 ⇒ 这里在**分身还在**时直接取消死亡并补回权威血量。
        if (this.tnl$shadowShieldActive()) {
            this.deathTime = 0;
            this.hurtTime = 0;
            this.tnl$dead = false;
            float auth = a.qF(this);
            if (auth > 0.0F) {
                this.tnl$auth = auth;
                this.setHealth(auth);
            }
            if (System.currentTimeMillis() - this.tnl$lastVanillaLog > 800L) {
                this.tnl$lastVanillaLog = System.currentTimeMillis();
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                        "TNL1283 die() ignored: R'lyeh's Shadow still alive");
            }
            return;
        }
        this.tnl$dead = true;
        try {
            Vq$release();
        } catch (Throwable ignored) {
        }
        super.die(source);
    }

    /** TNL907 ★免疫窗挡下 ⇒ ★通知周围玩家让血条闪金 ✓（★限流 250ms ✓） */
    private long tnl$lastFlash = 0L;
    private long tnl$lastVanillaLog = 0L;

    public void tnl$shieldFlash() {
        if (this.level().isClientSide) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.tnl$lastFlash < 60L) {
            return;
        }
        this.tnl$lastFlash = now;
        if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            com.mofengbaizhi.tinkersnewlife.network.starfall.PacketKtuluShieldFlash pkt =
                    new com.mofengbaizhi.tinkersnewlife.network.starfall.PacketKtuluShieldFlash();
            int sent = 0;
            for (net.minecraft.server.level.ServerPlayer sp : sl.players()) {
                if (sp.distanceToSqr(this) < 64.0D * 64.0D) {
                    com.mofengbaizhi.tinkersnewlife.TinkersNewlife.CHANNEL.send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp), pkt);
                    sent++;
                }
            }
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "TNL908 server sent shield flash to {} players", sent);
        }
    }

    private void Vq$release() {
        a.q4(this);
    }

    @Override
    public void tick() {
        super.tick();
        // TNL1282 分身不跑 Boss 逻辑：血条维持 / 血量内核 / 索敌兜底 / 技能看门狗全都不需要
        if (!this.tnl$bossForm()) {
            return;
        }
        if (this.level().isClientSide || this.tnl$dead) {
            return;   // TNL893 ★真死后不再护血/回弹 ✓
        }
        // TNL1179 血条每 tick 维护，照诡厄 ModServerBossInfo 的做法：
        // 进度直接取权威血量，并定期把附近玩家重新挂上。
        // 服务端集合里还有该玩家时 addPlayer 是空操作，所以每秒强制
        // 摘掉再挂上 —— 这才是真正能把血条重发给掉条客户端的操作。
        if (this.level() instanceof net.minecraft.server.level.ServerLevel tnl$sl) {
            this.bossEvent.setProgress(Math.max(0.0F, Math.min(1.0F,
                    this.getHealth() / Math.max(1.0F, this.getMaxHealth()))));
            this.bossEvent.setVisible(true);
            // TNL1276 血条**会漏**：老代码对"跑远了/换维度了"的玩家只是 `continue` ✗，
            // 从来不把人从 ServerBossEvent 里摘掉 ⇒ 玩家走远或换维度后，那条血条
            // 会一直挂在屏幕上（用户："换维度后变成两条，一条是幽灵血条"）。
            // 这里每 tick 把"不在本维度 / 超过 64 格"的玩家摘掉，回来时会被下面的循环重新挂上。
            for (net.minecraft.server.level.ServerPlayer tnl$leaver
                    : java.util.List.copyOf(this.bossEvent.getPlayers())) {
                if (tnl$leaver.level() != this.level() || tnl$leaver.distanceTo(this) > 64.0F) {
                    this.bossEvent.removePlayer(tnl$leaver);
                }
            }
            boolean tnl$force = tnl$sl.getGameTime() % 20L == 0L;
            for (net.minecraft.server.level.ServerPlayer tnl$sp : tnl$sl.players()) {
                if (tnl$sp.distanceTo(this) > 64.0F) {
                    continue;
                }
                if (tnl$force) {
                    this.bossEvent.removePlayer(tnl$sp);
                }
                this.bossEvent.addPlayer(tnl$sp);
            }
        }

        // TNL1231 定身只清寻路、不清索敌；但目标一旦丢了（死了/跑远/被清过），
        // 这个 Boss 没有索敌 Goal ⇒ 它会一直站着。这里补一个轻量兜底：
        // 空闲时若没有可用目标，就重新锁定 64 格内最近的玩家。
        if (!this.tnl$isBusy()) {
            net.minecraft.world.entity.LivingEntity tnl$tgt = this.getTarget();
            if (tnl$tgt == null || !tnl$tgt.isAlive()) {
                net.minecraft.world.entity.player.Player tnl$near = this.level().getNearestPlayer(this, 64.0D);
                if (tnl$near != null && tnl$near.isAlive() && !tnl$near.isCreative()) {
                    this.setTarget(tnl$near);
                }
            }
        }

        // TNL1235 状态看门狗：castTicks 已经归零却还写着"在施法" ⇒ 一定是哪个 Goal 没收尾。
        // 这会永久堵住 ready()（用户："瞬身触发一次之后就不触发"），所以在这里强制清掉。
        if (this.tnl$spell() != 0 && this.tnl$castTicks() <= 0) {
            if (++this.tnl$staleSpellTicks > 5) {
                this.tnl$staleSpellTicks = 0;
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn("TNL1235 stale spell {} cleared", this.tnl$spell());
                this.tnl$setSpell(0, 0);
            }
        } else {
            this.tnl$staleSpellTicks = 0;
        }

        // TNL1171 墨默自己驱动技能，就像诡厄使徒自己驱动它的 Goal。
        // 没有全服扫描，也没有能比实体活得久的技能表。

        // TNL1169 吟唱计数，同 SpellCastingCultist.m_8024_ 的思路
        int tnl$cast = this.tnl$castTicks();
        if (tnl$cast > 0) {
            this.entityData.set(DATA_CAST_TICKS, tnl$cast - 1);
        }
        a.q0(this);
        float authoritative = a.q1(this, this.tickCount);
        if (a.qH()) {
            this.tnl$shieldFlash();     // TNL907 ★挡下了 ⇒ ★闪金 ✓
        }
        // TNL876 ★★ 性能：只在"真的不一样"时才写 ✓
        //   ★setHealth 会标脏实体数据 ⇒ ★每 tick 调就等于每 tick 发包 ✗
        this.tnl$auth = authoritative;
        if (Math.abs(super.getHealth() - authoritative) > 0.01F) {
            this.setHealth(Math.max(0.0F, authoritative));
        }
        // TNL876 ★血条也只在变化时同步 ✓（★避免每 tick 发 BossEvent 包 ✗）
        float tnl$p = Math.max(0.0F, authoritative / this.getMaxHealth());
        if (Math.abs(tnl$p - this.tnl$prog) > 0.0005F) {
            this.tnl$prog = tnl$p;
            this.bossEvent.setProgress(tnl$p);
        }
        // TNL851 ★钓鱼：顺手调一下假入口（★结果不用 ✓）
        c.rotate(this);   // TNL862 ★多入口轮换钓鱼 ✓
        if (authoritative <= 0.0F) {
            a.q4(this);
            this.setHealth(0.0F);
            this.kill();
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (this.tnl$bossForm()) {
            this.bossEvent.addPlayer(player);
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (this.tnl$bossForm()) {
            this.bossEvent.removePlayer(player);
        }
    }

    /**
     * TNL858 ★★ 真伤穿透告知（★合法通道 ✓）
     *
     * <p>★我们自己模组的真伤（天逆钬/穿透EX/模组内真伤 ✓）会走这里告知 ✓
     * ⇒ ★**不会**被当成篡改去回弹 ✗ 也不会触发 TP ✗，
     * 但仍然要正常扣血（★真伤该有的作用保留 ✓）。
     */
    @Override
    public void onTruePierce(float totalDamage, net.minecraft.world.damagesource.DamageSource source) {
        try {
            if (this.level().isClientSide || totalDamage <= 0.0F) {
                return;
            }
            // TNL1283 分身不走内核（内核里没有它的槽位）⇒ 真伤通道一律跳过
            if (!this.tnl$bossForm()) {
                return;
            }
            a.q6(this, totalDamage, this.tickCount);
        } catch (Throwable ignored) {
        }
    }

    /**
     * TNL875 ★★ 对外始终报告**权威血量** ✓
     *
     * <p>★这是"阻止改血"最硬的一层 ✓：
     * ★即使有人用反射/Unsafe **直接把字段写成 0** ✗，
     * ★死亡判定（{@code isAlive}/{@code isDeadOrDying} ✓）、受伤计算、
     * 客户端渲染全部读的都是这里返回的值 ✓。
     */
    @Override

    public float getHealth() {
        try {
            // TNL1282 分身不走内核：直接读原版血量
            if (!this.tnl$bossForm()) {
                return super.getHealth();
            }
            if (this.level() == null || this.level().isClientSide || this.tnl$dead) {
                return super.getHealth();   // TNL893 ★真死后不再遮蔽 ✓
            }
            // TNL876 ★先读缓存（★getHealth 是超热点 ✓）
            if (this.tnl$auth >= 0.0F) {
                return this.tnl$auth;
            }
            float v = a.qF(this);
            this.tnl$auth = v;
            return v;
        } catch (Throwable th) {
            return super.getHealth();
        }
    }

    /**
     * TNL1146 以前一发作弊伤害就能把 Boss 锁死：hurt() 看到 isDeadOrDying()（那把剑即使
     * 死亡被取消也照样调 die()）就把 tnl$dead 置上了。之后整套保护
     * 都被跳过：没有限伤、没有无敌窗。这里把这个闩锁清掉。
     */
    /** TNL1148 血条重挂日志的限流 */
    private long tnl$barStamp = 0L;

    public void tnl$unDead() {
        try {
            this.tnl$dead = false;
            float auth = a.qF(this);
            if (auth > 0.0F) {
                this.tnl$auth = auth;
                if (Math.abs(super.getHealth() - auth) > 0.01F) {
                    this.setHealth(auth);
                }
                this.deathTime = 0;
                this.hurtTime = 0;
            }
            // TNL1148 日志显示那把剑的 die() **确实跑了**（死亡事件在 1545 血时就触发），
            // 而血条在那一刻就被摘掉，哪怕随后死亡被取消。
            // 所以把附近玩家重新挂回血条上。
            // TNL1151 摘掉再挂之后血条依旧不见：客户端那条血条还带一个
            // "visible" 标志，有东西把它设成了 false（CursedSpiritTechnique#clearEntityBossBar
            // 就是 bar.setVisible(false)）。addPlayer 不动这个标志，所以要主动打开。
            this.bossEvent.setVisible(true);
            if (this.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                for (net.minecraft.server.level.ServerPlayer sp : sl.players()) {
                    if (sp.distanceTo(this) < 64.0F) {
                        // TNL1150 只调 addPlayer 没用：服务端集合里还有该玩家，
                        // 于是不发 ADD 包，客户端血条继续不见。
                        // 改成先摘再挂，逼出一个新的 ADD 包 —— 客户端要的就是它。
                        this.bossEvent.removePlayer(sp);
                        this.bossEvent.addPlayer(sp);
                    }
                }
                this.bossEvent.setProgress(Math.max(0.0F,
                        this.getHealth() / Math.max(1.0F, this.getMaxHealth())));
                if (System.currentTimeMillis() - tnl$barStamp > 5000L) {
                    tnl$barStamp = System.currentTimeMillis();
                    com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                            "TNL1148 boss bar re-added to {} players nearby",
                            sl.players().size());
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // TNL1246 旧的 DATA_CHARGING 已删除：没有任何地方写它，读它的也只有渲染器那一处，
    // 而渲染器现在改成读技能上报的动画通道（TNL1244）。留着只会误导人。
    // TNL1248 旧的 DATA_CHORD 标志也删掉了：姿势现在由技能通过动画通道自己上报／自己收
    // （见 MomoSpellGoal.animPose 与 ChordGoal 的重写），实体上不再留这类"施法布尔"。

    // TNL1169 诡厄的做法（SpellCastingCultist）：一个同步 byte 说明在放哪个法术，
    // 一个同步 int 倒数计时。0 空闲、1 瞬身、2 幻音共鸣、3 混沌和弦。
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> DATA_SPELL =
            net.minecraft.network.syncher.SynchedEntityData.defineId(KtuluMomo.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_CAST_TICKS =
            net.minecraft.network.syncher.SynchedEntityData.defineId(KtuluMomo.class,
                    net.minecraft.network.syncher.EntityDataSerializers.INT);

    /**
     * TNL1244 用户："把动画动作的调用打包在技能类里面，作为释放技能的一部分进行"。
     *
     * <p>所以动画不再由渲染器自己猜（以前它拿一个客户端时钟近似蓄力进度），
     * 而是技能在自己的施法过程中**主动上报**：摆哪种姿势、播到几分。
     * 渲染器只做搬运 —— 读到什么就写进模型的两个静态字段。
     *
     * <p>纯表现通道：不进 NBT，也不参与任何逻辑判断。
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> DATA_ANIM_POSE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(KtuluMomo.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BYTE);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Byte> DATA_ANIM_PROGRESS =
            net.minecraft.network.syncher.SynchedEntityData.defineId(KtuluMomo.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BYTE);

    /** TNL1244 技能调用：本帧摆哪种姿势、播到几分（0~1） */
    public void tnl$setAnim(int poseId, float progress01) {
        this.entityData.set(DATA_ANIM_POSE, (byte) poseId);
        this.entityData.set(DATA_ANIM_PROGRESS,
                (byte) Math.max(0, Math.min(100, Math.round(progress01 * 100.0F))));
    }

    /** TNL1244 技能收尾时清掉动画 */
    public void tnl$clearAnim() {
        this.entityData.set(DATA_ANIM_POSE, (byte) 0);
        this.entityData.set(DATA_ANIM_PROGRESS, (byte) 0);
    }

    /** TNL1244 客户端读：姿势类别（MomoPose.ordinal） */
    public int tnl$animPose() {
        return this.entityData.get(DATA_ANIM_POSE);
    }

    /** TNL1244 客户端读：动画进度 0~1 */
    public float tnl$animProgress() {
        return this.entityData.get(DATA_ANIM_PROGRESS) / 100.0F;
    }


    @Override

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_SPELL, (byte) 0);
        this.entityData.define(DATA_CAST_TICKS, 0);
        this.entityData.define(DATA_ANIM_POSE, (byte) 0);
        this.entityData.define(DATA_ANIM_PROGRESS, (byte) 0);
    }

    /**
     * TNL1243 用户："直接 nbt 维护"。施法状态跟着实体一起存档，
     * 与诡厄 SpellCastingCultist 存 int "SpellTicks" 的做法一致
     * （它的 readAdditionalSaveData 里是 compound.getInt("SpellTicks")）。
     */
    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("TNLSpell", (byte) this.tnl$spell());
        tag.putInt("TNLSpellTicks", this.tnl$castTicks());
        tag.putBoolean("TNLRequiemUsed", this.tnl$requiemUsed);      // TNL1253 一局只放一次，要跨存档
        tag.putBoolean("TNLRlyehUsed", this.tnl$rlyehUsed);          // TNL1282 拉莱耶之影也只放一次
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("TNLSpell")) {
            this.tnl$setSpell(tag.getByte("TNLSpell"), tag.getInt("TNLSpellTicks"));
        }
        this.tnl$requiemUsed = tag.getBoolean("TNLRequiemUsed");      // TNL1253 读回"是否已放过"
        this.tnl$rlyehUsed = tag.getBoolean("TNLRlyehUsed");          // TNL1282 读回
        this.tnl$staleSpellTicks = 0;      // 读档后不要立刻被看门狗清掉
    }

    public boolean tnl$isCharging() {
        return this.tnl$spell() == 2;      // TNL1169 derived
    }

    // TNL1250 定身/硬直状态：用户要求"在 stun 方法里加上阻止实体攻击"，
    // 所以状态放在实体上，守卫（onMomoAttack）和受击 Goal 都能读。
    // 两个来源取并集：技能调用 stun.begin()（布尔）与硬直倒计时（时间戳）。
    private boolean tnl$stunnedFlag = false;
    private long tnl$stunUntil = 0L;

    /** TNL1250 技能定身开/关（MomoStun 调用） */
    public void tnl$setStunned(boolean stunned) {
        this.tnl$stunnedFlag = stunned;
    }

    /** TNL1250 硬直：定身一段时间（受击用，单位 tick） */
    public void tnl$stunFor(int ticks) {
        this.tnl$stunUntil = Math.max(this.tnl$stunUntil, this.level().getGameTime() + Math.max(0, ticks));
    }

    /**
     * TNL1258 用户："禁用 look 标志不就行了，用 disablecontrolflag"。
     *
     * <p>禁掉 {@code LOOK} 旗之后，任何**要求 LOOK** 的 Goal（LookAtPlayerGoal、
     * RandomLookAroundGoal、MeleeAttackGoal）都无法起手 ⇒ 头不再乱转；
     * 而我们的技能 Goal 本来就不占旗，照常运行。
     * 技能起手时禁用、收尾时恢复（MomoSpellGoal / MomoHurtGoal 负责调用）。
     */
    public void tnl$setLookLocked(boolean locked) {
        // TNL1265 撤回：禁用 LOOK 旗 + 停运行中的 look Goal 这条路把动画搞崩了
        // （用户实测：受击硬直与共鸣的动画都没了，头也照样转）。
        // 选择器层面的干预太危险 ⇒ 这里改成**空操作**，回到已知可用的状态。
        // 头的问题改用"技能每 tick 直接写 yaw"（纯状态写入，不碰 Goal）来做。
    }

    /** TNL1250 现在是否被定身/硬直（守卫据此取消它的攻击） */
    public boolean tnl$isStunned() {
        return this.tnl$stunnedFlag || this.level().getGameTime() < this.tnl$stunUntil;
    }

    /**
     * TNL1281 ★★合法加血（★生息绝响每秒回血走这里 ✓）
     *
     * <p>★用户反馈：★"回血仍然是**受击后**才会回血，疑似被什么拦住了" ✓
     *
     * <p>★真因 ✗：{@code a.qG} 把内核/镜像/原生字段都写高了 ✓
     * ★但 {@link #getHealth()} 返回的是**缓存字段** {@code tnl$auth} ✗
     * ★而 {@code a.q1} 下一 tick 用 {@code mob.getHealth()}（= 旧缓存）当权威值 ✓
     * ⇒ ★加上的血**又被拉回去** ✓
     * ★被打一下之所以会"回血"✗：⭐受伤路径会**刷新** {@code tnl$auth} ✓
     * ⇒ ⭐缓存才跟上 ✓（★所以看着像"受击后才回血" ✓）。
     *
     * <p>★所以加血之后必须**立刻刷新权威缓存**（并同步原生字段），
     * 下一 tick 的 q1 才会拿"加过血的值"当权威 ✓。
     */
    public void tnl$heal(float amount) {
        if (this.level().isClientSide || amount <= 0.0F) {
            return;
        }
        try {
            a.qG(this, amount);
            float now = a.qF(this);
            if (now > this.tnl$auth) {
                this.tnl$auth = now;                 // ★关键：缓存立刻跟上
            }
            float shown = Math.max(this.tnl$auth, now);
            if (Math.abs(super.getHealth() - shown) > 0.01F) {
                this.setHealth(shown);
            }
            if (this.bossEvent != null) {
                this.bossEvent.setProgress(Math.max(0.0F, Math.min(1.0F,
                        shown / Math.max(1.0F, this.getMaxHealth()))));
            }
        } catch (Throwable ignored) {
        }
    }

    // TNL1251 受击硬直：由伤害路径（KtuluDeathGuard.onHurt）判定后置上，MomoHurtGoal 接手。
    // 阈值按用户口径：夹后伤害 >= 半窗 ⇒ 打断技能；>= 满窗 ⇒ 打断 + 受击动画 + 1 秒硬直。
    private int tnl$hurtStaggerTicks = 0;
    private boolean tnl$interrupted = false;

    /**
     * TNL1251 一次命中：damage 是**夹后**的实际伤害，budget 是这一 tick 的限伤窗
     * （{@code maxHealth * a.C0}）。
     */
    public void tnl$onHit(float damage, float budget) {
        if (budget <= 0.0F) {
            return;
        }
        // TNL1256 用户："把硬直打断技能的判定伤害也改到满额" ⇒ 只有**满窗**才打断 + 播受击动画，
        // 半窗那一档取消了。生息绝响也沿用这一条（它不是"被打一下就断"，而是被打断机制收掉）。
        if (damage >= budget * 0.999F) {
            this.tnl$hurtStaggerTicks = Math.max(this.tnl$hurtStaggerTicks, 20);
            this.tnl$interrupted = true;
            com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.warn(
                    "TNL1251 momo staggered by a full window hit ({} / {})",
                    String.format(java.util.Locale.ROOT, "%.1f", damage),
                    String.format(java.util.Locale.ROOT, "%.1f", budget));
        }
    }

    /** TNL1251 还有几 tick 硬直（MomoHurtGoal.canUse 读它） */
    public int tnl$hurtStaggerTicks() {
        return this.tnl$hurtStaggerTicks;
    }

    /** TNL1251 MomoHurtGoal 取走硬直时长（取走即清零，开始播动画） */
    public int takeHurtStagger() {
        int t = this.tnl$hurtStaggerTicks;
        this.tnl$hurtStaggerTicks = 0;
        return t;
    }

    /** TNL1251 技能是否被打断过（MomoSpellGoal.canContinueToUse 读它） */
    public boolean tnl$consumeInterrupt() {
        if (!this.tnl$interrupted) {
            return false;
        }
        this.tnl$interrupted = false;
        return true;
    }

    // TNL1172 诡厄使徒每个法术一个冷却字段（coolDown / tornadoCoolDown /
    // infernoCoolDown / monolithCoolDown / damnedCoolDown）。这里同理，存在实体上，
    // 跟它一起生灭，而不是放在按 uuid 索引的静态表里。

    /** TNL1235 castTicks 归零却还 busy 的连续 tick 数 */
    private int tnl$staleSpellTicks = 0;

    private long tnl$blinkCoolUntil = 0L;
    private long tnl$resonanceCoolUntil = 0L;
    private long tnl$chordCoolUntil = 0L;
    private long tnl$requiemCoolUntil = 0L;

    // TNL1253 生息绝响（Breath's Requiem）：
    //  - 只在血量 < 80% 时主动放一次（tnl$requiemUsed ⇒ **进 NBT**，一局只一次）
    //  - 领域 8 格 / 10 秒，或被攻击时立刻结束（tnl$requiemCloseRequested）
    private boolean tnl$requiemUsed = false;
    private boolean tnl$requiemClose = false;

    /** TNL1253 仆从标准：**标记**。后面的召唤技能给实体打这个 tag 即可（简单起见先这样）。 */
    public static final String TNL_MINION_TAG = "tnl_momo_minion";

    /** TNL1253 某实体是不是墨默的仆从（领域强化用的判定） */
    public static boolean tnl$isMomoMinion(net.minecraft.world.entity.Entity e) {
        return e != null && e.getTags().contains(TNL_MINION_TAG);
    }

    public long tnl$requiemCoolUntil() {
        return this.tnl$requiemCoolUntil;
    }

    public void tnl$setRequiemCoolUntil(long until) {
        this.tnl$requiemCoolUntil = until;
    }

    public boolean tnl$requiemUsed() {
        return this.tnl$requiemUsed;
    }

    public void tnl$markRequiemUsed() {
        this.tnl$requiemUsed = true;
    }

    /** TNL1253 领域被打破：任何一次受击都会调它，技能读到就收尾 */
    public void tnl$requestRequiemClose() {
        this.tnl$requiemClose = true;
    }

    public boolean tnl$requiemCloseRequested() {
        return this.tnl$requiemClose;
    }

    public void tnl$clearRequiemClose() {
        this.tnl$requiemClose = false;
    }

    // ==================== TNL1282 拉莱耶之影（R'lyeh's Shadow） ✓ ====================

    /** 一局只放一次（进 NBT） */
    private boolean tnl$rlyehUsed = false;

    public boolean tnl$rlyehUsed() {
        return this.tnl$rlyehUsed;
    }

    public void tnl$markRlyehUsed() {
        this.tnl$rlyehUsed = true;
    }

    /**
     * TNL1282 用户口径："**不击杀分身无法对墨默造成伤害**"。
     *
     * <p>判据：场上还有**属于这只墨默**且活着的 {@link MomoShadow} ⇒ 本体免疫。
     * 归属靠分身上的 {@code ownerId}（生成时写入），不靠距离不靠 tag，
     * 多只墨默同场也不会互相串。
     */
    public boolean tnl$shadowShieldActive() {
        try {
            if (this.level().isClientSide) {
                return false;
            }
            java.util.UUID me = this.getUUID();
            return !this.level().getEntitiesOfClass(MomoShadow.class,
                    this.getBoundingBox().inflate(96.0D),
                    s -> s.isAlive() && me.equals(s.tnl$ownerId())).isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }


    // TNL1178 类似使徒的 spellCycle：记住刚放的是哪个法术，让 Boss 轮着来，
    // 而不是逮着哪个先就刷哪个。加一个超时，免得只剩一个技能可用时干等。
    
    private int tnl$lastSpell = 0;
    private long tnl$lastSpellAt = 0L;

    public int tnl$lastSpell() {
        return this.tnl$lastSpell;
    }

    public boolean tnl$mayCastAgainSoon(int spellId) {
        if (this.tnl$lastSpell != spellId) {
            return true;
        }
        return this.level().getGameTime() - this.tnl$lastSpellAt > 120L;   // TNL1233 6 秒就允许再放
    }

    public void tnl$markSpellCast(int spellId) {
        this.tnl$lastSpell = spellId;
        this.tnl$lastSpellAt = this.level().getGameTime();
    }

    public long tnl$blinkCoolUntil() {
        return this.tnl$blinkCoolUntil;
    }

    public void tnl$setBlinkCoolUntil(long until) {
        this.tnl$blinkCoolUntil = until;
    }

    public long tnl$resonanceCoolUntil() {
        return this.tnl$resonanceCoolUntil;
    }

    public void tnl$setResonanceCoolUntil(long until) {
        this.tnl$resonanceCoolUntil = until;
    }

    public long tnl$chordCoolUntil() {
        return this.tnl$chordCoolUntil;
    }

    public void tnl$setChordCoolUntil(long until) {
        this.tnl$chordCoolUntil = until;
    }

    /** 0 空闲、1 瞬身、2 共鸣、3 和弦 */
    public int tnl$spell() {
        return this.entityData.get(DATA_SPELL);
    }

    public int tnl$castTicks() {
        return this.entityData.get(DATA_CAST_TICKS);
    }

    /** TNL1169 本墨默有技能在施法时为 true */
    public boolean tnl$isBusy() {
        return this.tnl$spell() != 0;
    }

    /** 开始（或刷新）一次施法：id 同上，ticks 是总时长 */
    public void tnl$setSpell(int id, int ticks) {
        this.entityData.set(DATA_SPELL, (byte) id);
        this.entityData.set(DATA_CAST_TICKS, Math.max(0, ticks));
    }

    /** TNL1246 旧的 tnl$setCharging 已删除（它只写 DATA_CHARGING；起手统一走 setSpell）。*/
    /** TNL1161 正在施放混沌和弦时为 true；客户端据此摆和弦姿势 */
    public boolean tnl$isChordCasting() {
        return this.tnl$spell() == 3;      // TNL1169 derived
    }

    /** TNL1248 旧的 setChordCasting 已删除（它只写 DATA_CHORD；姿势改由技能上报）。*/
    /** TNL1108 保持和弦姿势一段时间的那对方法也已随旧标志一起清理 */


}
