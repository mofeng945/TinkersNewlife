package com.mofengbaizhi.tinkersnewlife.integration.goety;

import com.Polarice3.Goety.common.entities.boss.Apostle;
import com.Polarice3.Goety.common.entities.projectiles.DeathArrow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>诡厄巫法·使徒改造补丁</b>（§853）—— 只在**装了诡厄**时被加载 ✓（由 {@code GoetyIntegration} 触达 ✓）。
 *
 * <h2>本条实现的是"组一"（纯事件／数值，风险最低）</h2>
 * <ol>
 *   <li><b>#2 瞬移后 2s 内减伤 60%</b> ✓
 *       —— 不混入诡厄内部 ✗：我们每 10 tick 扫一次使徒位置 ✓，一 tick 内位移超过
 *       {@link #TELEPORT_JUMP} 格 ⇒ 判定"刚刚瞬移" ✓ ⇒ 记 40 tick 窗口 ✓；
 *       窗口内在 {@code LivingHurtEvent} 里按 {@code 1 - 0.6} 结算 ✓。</li>
 *   <li><b>#11 射箭伤害改为魔法伤害</b> ✓
 *       —— 命中时若来源是诡厄的 {@link DeathArrow} 且主人是 {@link Apostle} ✓
 *       ⇒ 取消原本的箭伤 ✗ 改以 {@code indirectMagic(使徒, 使徒)} 重发同额伤害 ✓
 *       （伤害类型变成魔法 ⇒ 抗性/免疫口径随魔法走 ✓ 与原版长矛那种"改类型"同法 ✓）。</li>
 * </ol>
 *
 * <h2>配置（**默认开启** ✓ 用户口径 ✓）</h2>
 * 本补丁自带一份 COMMON 配置 {@code mofengbaizhi/tinkersnewlife-apostle.toml} ✓：
 * {@code enabled}（总开关 ✓）、{@code teleport_damage_reduction}（默认 0.6 ✓）、
 * {@code arrow_as_magic}（默认 true ✓）。
 *
 * <p>⚠ 组二/组三（狱云清增益、柱子出猪灵蛮兵、主世界柱子数量+自回血、二阶段射箭同施法、
 * 近身咆哮、鞘翅拽落、爆焰陷阱）**尚未实现** ✗ —— 落到具体类名/字段见备忘录 §852d ✓。
 * <p>⚠ 本类**直接引用诡厄类型** ✓ ⇒ 只有 {@code GoetyIntegration#register} 在
 * {@code ModList.isLoaded("goety")} 为真时才会触达 ✓（未装诡厄 ⇒ 本类不会被 JVM 加载 ✓ 不会崩 ✓）。
 */
public final class ApostlePatch {

    private static final Logger LOGGER = LoggerFactory.getLogger("TinkersNewlife/ApostlePatch");

    /** 一 tick 内位移超过这么多格 ⇒ 当成"瞬移" ✓ */
    private static final double TELEPORT_JUMP = 8.0D;
    /** 瞬移后的减伤窗口（tick ✓ 2 秒 ✓） */
    private static final int TELEPORT_WINDOW_TICKS = 40;
    /** 扫描间隔（tick ✓ 每 10 tick 一次 ⇒ 位移要按 10 tick 折算 ✓ 阈值已放大 ✓） */
    private static final int SCAN_INTERVAL = 10;

    /** 使徒 → 减伤窗口截止的游戏刻 ✓ */
    private static final Map<UUID, Long> TELEPORT_SHIELD = new ConcurrentHashMap<>();
    /** 使徒 → 上次扫描到的位置 ✓ */
    private static final Map<UUID, net.minecraft.world.phys.Vec3> LAST_POS = new ConcurrentHashMap<>();

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.DoubleValue TELEPORT_DAMAGE_REDUCTION;
    public static final ForgeConfigSpec.BooleanValue ARROW_AS_MAGIC;
    public static final ForgeConfigSpec.DoubleValue DUAL_TITLE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue OVERWORLD_REGEN_PERCENT;
    public static final ForgeConfigSpec.BooleanValue OVERWORLD_REGEN_IGNORES_SMITE;
    public static final ForgeConfigSpec.DoubleValue APOSTLE_DAMAGE_MULTIPLIER;

    // ── §869：使徒各项加强的独立开关（用户口径：加强都要能在配置里开关 ✓）──
    public static final ForgeConfigSpec.BooleanValue HELL_CLOUD_PURGE;
    public static final ForgeConfigSpec.BooleanValue NETHERITE_PIGLIN_BRUTE;
    public static final ForgeConfigSpec.BooleanValue ELYTRA_DRAG_DOWN;
    public static final ForgeConfigSpec.BooleanValue FIRE_BLAST_TRAP;
    public static final ForgeConfigSpec.BooleanValue ARROW_FIRE_BLAST_TRAP;
    public static final ForgeConfigSpec.BooleanValue MONOLITH_CAP_SIX;
    public static final ForgeConfigSpec.IntValue MONOLITH_TOPUP_SECONDS;
    public static final ForgeConfigSpec.BooleanValue MONOLITH_LIFESPAN;
    public static final ForgeConfigSpec.DoubleValue MONOLITH_LIFESPAN_HP_PER_SECOND;
    public static final ForgeConfigSpec.BooleanValue WALL_TELEPORT;
    public static final ForgeConfigSpec.BooleanValue ARROW_RAIN;
    public static final ForgeConfigSpec.IntValue ARROW_RAIN_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.BooleanValue RAID_WAVE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("诡厄巫法·使徒改造补丁（§853）").push("apostle");
        ENABLED = b.comment("总开关（默认开启）").define("enabled", true);
        TELEPORT_DAMAGE_REDUCTION = b
                .comment("瞬移后 2 秒内的减伤比例（0.6 = 减伤 60%）")
                .defineInRange("teleport_damage_reduction", 0.6D, 0.0D, 1.0D);
        ARROW_AS_MAGIC = b.comment("使徒射出的箭造成魔法伤害（默认 true）").define("arrow_as_magic", true);
        DUAL_TITLE_CHANCE = b
                .comment("使徒生成时额外获得【第二个头衔】的概率（0.1 = 10% ⇒ 双头衔使徒）",
                        "第二头衔一定会真正生效：若与主头衔同属'箭矢附着'类（会互相覆盖同一个字段），",
                        "则改抽【不灭重生／可怖之物／荣耀之名】这三个不会冲突的头衔之一。",
                        "§1073 双头衔使徒现在还会：血量 ×2、造成的伤害在上述 damage_multiplier 之上再 ×1.5、",
                        "死亡时战利品表额外 roll 一次（等于 roll 两次）。")
                .defineInRange("dual_title_chance", 0.10D, 0.0D, 1.0D);
        OVERWORLD_REGEN_PERCENT = b
                .comment("主世界二阶段自回血速率：每秒回复【最大生命】的百分比（0.01 = 每秒 1%）",
                        "0 = 关闭自回血。例：使徒 300 血 ⇒ 默认每秒回 3 点。")
                .defineInRange("overworld_regen_percent", 0.01D, 0.0D, 1.0D);
        OVERWORLD_REGEN_IGNORES_SMITE = b
                .comment("自回血是否无视诡厄的『亡灵杀手禁疗』（默认 true）",
                        "诡厄把 Apostle#heal 覆写成『被亡灵杀手打中后 1~5 秒内不回血』；",
                        "你的武器带亡灵杀手时，尊重它 ⇒ 自回血等于没有（§860 实测）。默认无视。")
                .define("overworld_regen_ignores_smite", true);
        APOSTLE_DAMAGE_MULTIPLIER = b
                .comment("使徒造成的伤害倍率（默认 1.5 = 上调 50%）",
                        "涵盖使徒的全部伤害来源：近战、箭、法术、狱云、爆燃陷阱等（都按这个倍率乘一次）。",
                        "改成 1.0 = 关闭；改 2.0 = 翻倍。",
                        "§1073 双头衔使徒在此倍率之上**再 ×1.5** ⇒ 默认 1.5 时合计 2.25。")
                .defineInRange("damage_multiplier", 1.5D, 0.0D, 100.0D);
        HELL_CLOUD_PURGE = b.comment("狱云清掉玩家所有增益（#3，默认 true）").define("hell_cloud_purge", true);
        NETHERITE_PIGLIN_BRUTE = b.comment("黑曜石柱召唤的猪灵蛮兵给全身下界合金甲＋下界合金斧（#6，默认 true）")
                .define("netherite_piglin_brute", true);
        ELYTRA_DRAG_DOWN = b.comment("玩家鞘翅飞行 5 秒后把其拽向地面并造成撞击伤害（#8，默认 true）")
                .define("elytra_drag_down", true);
        FIRE_BLAST_TRAP = b.comment("使徒二阶段在身边 5 格随机丢爆燃陷阱（#9，默认 true）")
                .define("fire_blast_trap", true);
        ARROW_FIRE_BLAST_TRAP = b.comment("使徒二阶段射箭时 30% 概率在目标脚下补一发爆燃陷阱（#1，默认 true）")
                .define("arrow_fire_blast_trap", true);
        MONOLITH_CAP_SIX = b.comment("黑曜石柱同屏上限从 4 提到 6（#10，默认 true）").define("monolith_cap_six", true);
        MONOLITH_TOPUP_SECONDS = b.comment("柱子补位间隔（秒）：每多少秒最多补一根柱子",
                        "§869 用户口径：召唤速度减慢一倍 ⇒ 默认 10 秒（原来是 5 秒）。")
                .defineInRange("monolith_topup_seconds", 10, 1, 600);
        MONOLITH_LIFESPAN = b.comment("黑曜石柱有生命周期：血量随时间流逝（§869 用户口径，默认 true）")
                .define("monolith_lifespan", true);
        MONOLITH_LIFESPAN_HP_PER_SECOND = b.comment("柱子每秒流逝多少血量（默认 2 点／秒）")
                .defineInRange("monolith_lifespan_hp_per_second", 2.0D, 0.0D, 1000.0D);
        WALL_TELEPORT = b.comment("使徒与玩家隔墙时瞬移到玩家身后；身后没位置就瞬移到玩家所在位置（§869，默认 true）")
                .define("wall_teleport", true);
        ARROW_RAIN = b.comment("给使徒的法术组加上『箭雨』（诡厄箭雨聚晶的效果，默认 true）")
                .define("arrow_rain", true);
        ARROW_RAIN_COOLDOWN_SECONDS = b.comment("箭雨冷却（秒，默认 20）")
                .defineInRange("arrow_rain_cooldown_seconds", 20, 1, 600);
        RAID_WAVE = b.comment("主世界 10% 血刷一波认使徒为主的袭击者（#12，默认 true）").define("raid_wave", true);
        b.pop();
        SPEC = b.build();
    }

    private ApostlePatch() {}

    /** 由 {@link GoetyIntegration#register} 调用 ✓ */
    public static void register() {
        try {
            ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "mofengbaizhi/tinkersnewlife-apostle.toml");
        } catch (Throwable t) {
            LOGGER.warn("[使徒补丁] 配置注册失败（用默认值继续）: {}", t.toString());
        }
        MinecraftForge.EVENT_BUS.register(ApostlePatch.class);
        LOGGER.info("[使徒补丁] 已启用（默认开启 ✓ 配置 config/mofengbaizhi/tinkersnewlife-apostle.toml ✓）：瞬移后 2s 减伤 60% ✓ 射箭改魔法伤害 ✓");
    }

    /** §869：读布尔配置（读不到 ⇒ 用默认值 ✓ 与 enabled() 一个口径 ✓） */
    private static boolean flag(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static boolean enabled() {
        try {
            return ENABLED.get();
        } catch (Throwable ignored) {
            return true;                                  // 配置还没加载时按"默认开启"走 ✓
        }
    }

    // ============================================================
    //  §863 使徒伤害倍率（用户口径：上调 1.5 倍 ✓ 可配 ✓）
    // ============================================================

    /** 正在"同额度重发"箭伤（#11 改魔法伤害那一下 ✓）⇒ 重发的那一发**不再乘一次倍率** ✗ */
    private static boolean REHITTING = false;

    /**
     * 这一下是不是**使徒造成的** ✓ —— 三种都算：
     * <ol>
     *   <li>直接由使徒本体打出（近战 ⇒ {@code getEntity()} 就是它 ✓）；</li>
     *   <li>投射物／法术由使徒发射（{@code getDirectEntity()} 是它 ✓）；</li>
     *   <li>带主人的实体（诡厄的 {@code HellCloud}／{@code FireBlastTrap}／{@code DeathArrow} 等
     *       ⇒ {@link net.minecraft.world.entity.OwnableEntity#getOwner()} 是它 ✓）。</li>
     * </ol>
     */
    private static boolean isFromApostle(net.minecraft.world.damagesource.DamageSource src) {
        if (src == null) return false;
        if (src.getEntity() instanceof Apostle || src.getDirectEntity() instanceof Apostle) return true;
        for (Entity e : new Entity[]{src.getEntity(), src.getDirectEntity()}) {
            if (e instanceof net.minecraft.world.entity.OwnableEntity owned
                    && owned.getOwner() instanceof Apostle) {
                return true;
            }
        }
        return false;
    }

    // ============================================================
    //  §1073 双头衔使徒：血量翻倍 ＋ 伤害再加 1.5 倍 ＋ 战利品 roll 两次
    // ============================================================

    /** 这一下是谁打的使徒 ✓（近战本体 ✓／投射物发射者 ✓／带主人的诡厄实体 ✓）；没有就 null ✓ */
    private static Apostle apostleOf(net.minecraft.world.damagesource.DamageSource src) {
        if (src == null) return null;
        if (src.getEntity() instanceof Apostle a) return a;
        if (src.getDirectEntity() instanceof Apostle a) return a;
        for (Entity e : new Entity[]{src.getEntity(), src.getDirectEntity()}) {
            if (e instanceof net.minecraft.world.entity.OwnableEntity owned && owned.getOwner() instanceof Apostle a) {
                return a;
            }
        }
        return null;
    }

    /**
     * 是不是**双头衔使徒** ✓ —— 只看我们自己的 NBT 键 {@link #KEY_SECOND_TITLE} ✓
     * （诡厄自身只有一个 {@code titleNumber} ✗ 表达不了"两个头衔" ✓ 所以以我们的键为准 ✓）。
     */
    private static boolean isDualTitle(Apostle apostle) {
        if (apostle == null) return false;
        try {
            return apostle.getPersistentData().contains(KEY_SECOND_TITLE);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * §1073 <b>双头衔的强化</b> ✓（用户口径：「**血量翻倍，伤害在我加强的 1.5 倍基础上再 ×1.5**」✓）：
     * <ol>
     *   <li><b>血量 ×2</b> ✓：直接把 {@code MAX_HEALTH} 的**基础值**翻倍 ✓ 再回满 ✓
     *       （⚠ 只做一次 ✓ 用 {@link #KEY_DUAL_HEALTH_DONE} 标记 ✓ —— 基础值会存盘 ✗，
     *        不设标记的话每次读档都会再 ×2 ⇒ ×4 ✗）；</li>
     *   <li><b>伤害再加 ×1.5</b> ✓：在 {@link #onLivingHurt} 里对"双头衔打出的伤害"额外乘
     *       {@link #DUAL_DAMAGE_EXTRA} ✓ ⇒ 配置默认 1.5 时合计 <b>2.25</b> ✓（若你把配置改成别的值 ✓ 就跟着变 ✓ 始终是"配置值 ×1.5" ✓）。</li>
     * </ol>
     */
    private static void applyDualTitleBuffs(Apostle apostle) {
        if (apostle == null || apostle.level().isClientSide) return;
        net.minecraft.nbt.CompoundTag data = apostle.getPersistentData();
        if (data.getBoolean(KEY_DUAL_HEALTH_DONE)) return;
        try {
            var attr = apostle.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (attr != null) {
                double base = attr.getBaseValue();
                attr.setBaseValue(base * DUAL_HEALTH_MULTIPLIER);          // ×2 ✓
                float max = apostle.getMaxHealth();
                apostle.setHealth(max);                                    // 生成/读档时直接满血 ✓
                data.putBoolean(KEY_DUAL_HEALTH_DONE, true);                // 只做一次 ✓
                LOGGER.info("[使徒补丁] §1073 双头衔使徒 ⇒ 血量 {} → {}（×{}）✓",
                        String.format(java.util.Locale.ROOT, "%.1f", base),
                        String.format(java.util.Locale.ROOT, "%.1f", max),
                        String.format(java.util.Locale.ROOT, "%.1f", DUAL_HEALTH_MULTIPLIER));
            }
        } catch (Throwable ignored) {
            // fail-safe ✓
        }
    }

    // ============================================================
    //  #2 瞬移检测（每 10 tick 扫一次 ✓ 不混入诡厄内部 ✓）
    // ============================================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !enabled()) return;
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % SCAN_INTERVAL != 0) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Apostle apostle)) continue;
                net.minecraft.world.phys.Vec3 now = apostle.position();
                net.minecraft.world.phys.Vec3 last = LAST_POS.put(apostle.getUUID(), now);
                if (last == null) continue;
                if (now.distanceTo(last) > TELEPORT_JUMP) {
                    TELEPORT_SHIELD.put(apostle.getUUID(), level.getGameTime() + TELEPORT_WINDOW_TICKS);
                }
            }
        }
        long now = server.getTickCount();
        TELEPORT_SHIELD.entrySet().removeIf(e -> e.getValue() < now - 200L);   // 顺带清理过期项 ✓
        // ⭐⭐ §1240 **`TRAP_COOLDOWN` 也要清过期项** ✗（⭐ 用户口径 ✓ 六项全做 ✓）
        //   ⚠ 原来只有 `TELEPORT_SHIELD` 清了 ✗ ⭐ 同一文件里两张表**只清一张** ✓
        //     ⇒ ⭐ 使徒打得越多 ⭐ 这张表越大 ✓（⭐ 虽然量小 ✗ ⭐ 但没理由留着 ✓）
        //   ⭐ 判据同款 ✓：⭐ 过期的记录 ⭐ **比"现在 200 tick 之前"还早** 就扔掉 ✓。
        TRAP_COOLDOWN.entrySet().removeIf(e -> e.getValue() < now - 200L);
    }

    // ============================================================
    //  #2 减伤 ＋ #11 箭改魔法伤害
    // ============================================================

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!enabled()) return;
        LivingEntity victim = event.getEntity();
        net.minecraft.world.damagesource.DamageSource src = event.getSource();

        // ── §863：使徒造成的伤害 ×倍率（用户口径默认 1.5 倍 ✓ 可配 ✓）──────────
        //   ⚠ REHITTING 守卫：下面 #11 会把箭伤"同额度重发"成魔法伤害 ⇒ 重发那一发**不再乘一次** ✗
        //     （否则一次命中被乘两遍 ⇒ 1.5×1.5 = 2.25 ✗）
        if (!REHITTING && event.getAmount() > 0.0F && !(victim instanceof Apostle)) {
            double mult;
            try {
                mult = APOSTLE_DAMAGE_MULTIPLIER.get();
            } catch (Throwable ignored) {
                mult = 1.5D;
            }
            if (isFromApostle(src)) {
                // ⭐⭐ §1209 **亚波伦（Apollyon）不吃我们的加强** ✗
                //   （⭐ 用户口径 ✓ 2026-10-10：「**让我的使徒加强别对亚波伦生效**」✓）
                //   ⚠ 用**权威字段**判 ✗（⭐ `ApollyonAbilityHelper#isApollyon()` ✓
                //     ⭐ 而不是"自定义名是否以头衔结尾"那个**间接**判据 ✓）。
                if (com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isApollyon(apostleOf(src))) {
                    return;
                }
                // §1073 双头衔使徒：在**配置倍率之上**再 ×1.5 ✓
                //   （用户口径：「伤害在我加强的 1.5 倍基础上再 ×1.5」✓ ⇒ 配置默认 1.5 时合计 **2.25** ✓；
                //    若你把配置改成别的值 ✓ 它就始终是"配置值 ×1.5" ✓ 不会写死 ✗）
                double total = mult * (isDualTitle(apostleOf(src)) ? DUAL_DAMAGE_EXTRA : 1.0D);
                if (total != 1.0D) {
                    event.setAmount((float) (event.getAmount() * total));
                }
            }
        }

        // ── #11：使徒的箭 ⇒ 改成魔法伤害（同额度重发 ✓ 不再二次乘倍率 ✓）──────────────────────
        if (ARROW_AS_MAGIC.get()
                && event.getSource().getDirectEntity() instanceof DeathArrow arrow
                && arrow.getOwner() instanceof Apostle apostle) {
            float amount = event.getAmount();
            if (amount > 0.0F && !victim.level().isClientSide) {
                event.setCanceled(true);
                victim.invulnerableTime = 0;
                REHITTING = true;
                try {
                    victim.hurt(victim.damageSources().indirectMagic(apostle, apostle), amount);
                } finally {
                    REHITTING = false;
                }
            }
            return;
        }

        // ── #2：使徒自身在"瞬移后 2s"窗口内 ⇒ 减伤 60% ─────────────────────
        if (event.getEntity() instanceof Apostle apostle) {
            Long until = TELEPORT_SHIELD.get(apostle.getUUID());
            if (until != null && apostle.level().getGameTime() <= until) {
                double reduction = TELEPORT_DAMAGE_REDUCTION.get();
                event.setAmount((float) (event.getAmount() * (1.0D - reduction)));
            }
        }
    }

    /** 把一个玩家增益全清掉（#5 咆哮不用它 ✗；**#3 狱云** 用它 ✓ §857） */
    static void clearBeneficialEffects(LivingEntity target) {
        for (MobEffectInstance inst : target.getActiveEffects().stream().toList()) {
            if (inst.getEffect().isBeneficial()) target.removeEffect(inst.getEffect());
        }
    }

    /** 供将来组二/组三使用：把玩家沿"使徒→玩家"方向推开（#5 咆哮 ✓ 预留 ✓） */
    static void roarPush(Apostle apostle, Player player, double strength) {
        net.minecraft.world.phys.Vec3 dir = player.position().subtract(apostle.position());
        net.minecraft.world.phys.Vec3 flat = new net.minecraft.world.phys.Vec3(dir.x, 0.0D, dir.z);
        if (flat.lengthSqr() < 1.0E-4D) return;
        net.minecraft.world.phys.Vec3 push = flat.normalize().scale(strength);
        player.push(push.x, 0.6D, push.z);
        player.hurtMarked = true;
    }

    // ============================================================
    //  组二 / 组三 / #12（§854 一口气做完：全部走事件 + 我方状态表，不混入诡厄内部）
    // ============================================================

    /** 使徒 → 玩家 → 已在 2 格内待了多少 tick（#5） */
    private static final Map<UUID, Map<UUID, Integer>> CLOSE_TICKS = new ConcurrentHashMap<>();
    /** 使徒 → 玩家 → 鞘翅锁定累计 tick（#8） */
    private static final Map<UUID, Map<UUID, Integer>> ELYTRA_LOCK = new ConcurrentHashMap<>();
    /** 使徒 → 上次爆燃陷阱时刻（#9） */
    private static final Map<UUID, Long> TRAP_COOLDOWN = new ConcurrentHashMap<>();
    /** 已刷过袭击的使徒（#12 一次性） */
    private static final java.util.Set<UUID> RAID_DONE = ConcurrentHashMap.newKeySet();

    private static final double CLOSE_RANGE = 2.0D;
    private static final int CLOSE_LIMIT_TICKS = 100;        // 5 秒
    private static final int ELYTRA_LOCK_TICKS = 100;        // 5 秒锁定
    private static final int TRAP_COOLDOWN_TICKS = 300;      // 15 秒
    private static final double ELYTRA_DETECT_RANGE = 24.0D;
    private static final int RAID_WAVE_SIZE = 5;             // #12 一波 5 只（原版掠夺者）

    /** 组二/组三/#12 主循环（每 tick，仅服务端） */
    @SubscribeEvent
    public static void onServerTickGroup23(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !enabled()) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        long now = server.getTickCount();

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Apostle apostle) || !apostle.isAlive()) continue;
                boolean nether = level.dimension() == net.minecraft.world.level.Level.NETHER;

                // ── #10（回血部分）：主世界二阶段自回血（比下界慢 ✓）──
                if (!nether && apostle.isSecondPhase() && now % 20L == 0L) {
                    overworldRegen(apostle, now);
                }

                // ── §869 柱子生命周期：血量随时间流逝（每秒 2 点 ✓ 可配 ✓）+ 隔墙瞬移 + 箭雨 ──
                if (now % 20L == 0L) {
                    drainMonoliths(level, apostle);
                }
                if (now % 10L == 0L) {
                    wallTeleportBehind(level, apostle, now);
                }
                if (now % 20L == 0L) {
                    castArrowRain(level, apostle, now);
                }

                for (Player player : level.getEntitiesOfClass(Player.class, apostle.getBoundingBox().inflate(6.0D))) {
                    if (player.isCreative() || player.isSpectator()) continue;

                    // ── #5：近身 2 格超 5 秒 ⇒ 咆哮并把玩家推开 ──
                    Map<UUID, Integer> close = CLOSE_TICKS.computeIfAbsent(apostle.getUUID(), k -> new ConcurrentHashMap<>());
                    if (player.distanceTo(apostle) <= CLOSE_RANGE) {
                        int t = close.merge(player.getUUID(), 1, Integer::sum);
                        if (t >= CLOSE_LIMIT_TICKS) {
                            close.put(player.getUUID(), 0);
                            level.playSound(null, apostle.blockPosition(),
                                    net.minecraft.sounds.SoundEvents.RAVAGER_ROAR,
                                    net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 0.9F);
                            roarPush(apostle, player, 2.2D);
                            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                                    apostle.getX(), apostle.getY() + 1.0D, apostle.getZ(), 30, 1.0D, 0.6D, 1.0D, 0.1D);
                        }
                    } else {
                        close.remove(player.getUUID());
                    }

                    // ── #8：鞘翅飞行 ⇒ 5 秒锁定（大量黄色粒子）⇒ 拽向地面 ＋ 撞击动能伤害 ──
                    if (flag(ELYTRA_DRAG_DOWN, true) && player.isFallFlying()
                            && player.distanceTo(apostle) <= ELYTRA_DETECT_RANGE) {
                        Map<UUID, Integer> lock = ELYTRA_LOCK.computeIfAbsent(apostle.getUUID(), k -> new ConcurrentHashMap<>());
                        int t = lock.merge(player.getUUID(), 1, Integer::sum);
                        level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                                        new org.joml.Vector3f(1.0F, 0.9F, 0.2F), 1.2F),
                                player.getX(), player.getY() + 0.5D, player.getZ(), 12, 0.4D, 0.4D, 0.4D, 0.02D);
                        if (t >= ELYTRA_LOCK_TICKS) {
                            lock.remove(player.getUUID());
                            net.minecraft.world.phys.Vec3 v = player.getDeltaMovement();
                            player.setDeltaMovement(v.x * 0.2D, -2.2D, v.z * 0.2D);
                            player.hurtMarked = true;
                            float kinetic = (float) Math.max(6.0D, Math.abs(v.y) * 20.0D);
                            player.hurt(player.damageSources().flyIntoWall(), kinetic);
                            player.displayClientMessage(net.minecraft.network.chat.Component
                                    .literal("§c使徒之力将你拽向地面！"), true);
                        }
                    }
                }

                // ── #9：二阶段 5 格内随机爆燃陷阱（15 秒冷却）──
                Long nextTrap = TRAP_COOLDOWN.get(apostle.getUUID());
                if (flag(FIRE_BLAST_TRAP, true) && apostle.isSecondPhase() && (nextTrap == null || now >= nextTrap)) {
                    TRAP_COOLDOWN.put(apostle.getUUID(), now + TRAP_COOLDOWN_TICKS);
                    net.minecraft.core.BlockPos pos = apostle.blockPosition().offset(
                            apostle.getRandom().nextInt(11) - 5, 0, apostle.getRandom().nextInt(11) - 5);
                    com.Polarice3.Goety.common.entities.util.FireBlastTrap trap =
                            new com.Polarice3.Goety.common.entities.util.FireBlastTrap(level, pos.getX() + 0.5D,
                                    pos.getY(), pos.getZ() + 0.5D);
                    trap.setOwner(apostle);
                    level.addFreshEntity(trap);
                }

                // ── #12：主世界 10% 血 ⇒ 所在区块刷一波袭击（**诡厄仆从掠夺者** ✓ 全部认使徒为主人 ✓）──
                if (flag(RAID_WAVE, true) && !nether && !RAID_DONE.contains(apostle.getUUID())
                        && apostle.getHealth() <= apostle.getMaxHealth() * 0.10F) {
                    RAID_DONE.add(apostle.getUUID());
                    spawnRaidWave(level, apostle);
                }

                // ── #3：狱云范围内的玩家 ⇒ 清掉所有增益 ──
                if (flag(HELL_CLOUD_PURGE, true)) purgeBuffsInHellClouds(level, apostle);

                // ── #6：黑曜石柱召唤的猪灵蛮兵 ⇒ 全身下界合金甲 ＋ 下界合金斧 ──
                if (now % 20L == 0L) {
                    if (flag(NETHERITE_PIGLIN_BRUTE, true)) equipMonolithBrutes(level, apostle);
                    if (flag(MONOLITH_CAP_SIX, true)) topUpMonoliths(level, apostle, now);   // ── #10：柱子上限 4 → 6 ──
                }

                // ── 双头衔（§859）：第二头衔的持续效果每 tick 兜一次 ──
                keepSecondTitle(apostle);
            }
        }
    }

    // ============================================================
    //  双头衔使徒（§859，用户口径：生成时 10% ✓ 可配 ✓）
    // ============================================================

    /** 第二头衔存在我们自己的 NBT 里 ✓（诡厄只有一个 `titleNumber` 字段 ✗ 装不下两个 ✓） */
    private static final String KEY_SECOND_TITLE = "tinkersnewlife.apostle_second_title";
    /**
     * §1073 <b>双头衔的"血量已翻倍"标记</b> ✓ —— 防读档重复施加 ✗
     * （{@code MAX_HEALTH} 的基础值会随 NBT 存盘 ✓，不设标记的话每次读档都会再 ×2 ⇒ 变成 ×4 ✗）。
     */
    private static final String KEY_DUAL_HEALTH_DONE = "tinkersnewlife.apostle_dual_health_done";
    /** §1073 双头衔血量倍率 ✓（用户口径：「血量翻倍」✓） */
    private static final double DUAL_HEALTH_MULTIPLIER = 2.0D;
    /** §1073 双头衔在"我的 1.5 倍加强"之上**再乘**的倍率 ✓（1.5 × 1.5 ＝ 2.25 ✓ 用户口径 ✓） */
    private static final double DUAL_DAMAGE_EXTRA = 1.5D;
    /** "这只使徒已经掷过双头衔骰子"标记 ✓（保证**一辈子只掷一次** ✓ 与 `loadedFromDisk` 无关 ✓） */
    private static final String KEY_TITLE_ROLLED = "tinkersnewlife.apostle_title_rolled";
    /** 诡厄使徒头衔总数（`title.goety.0` ~ `title.goety.11` ✓ 已核语言文件 ✓） */
    private static final int APOSTLE_TITLE_COUNT = 12;
    /** 与主头衔**不冲突**的三个：不灭重生(0)／可怖之物(9)／荣耀之名(10) ✓ */
    private static final int[] NON_ARROW_TITLES = new int[]{0, 9, 10};

    /** 诡厄起名时用的就是 `title.goety.<n>` ✓ 我们照抄同一个键 ✓（`getString()` 两边走同一份 Language ✓ 口径一致 ✓） */
    private static String titleText(int index) {
        return net.minecraft.network.chat.Component.translatable("title.goety." + index).getString();
    }

    /**
     * 是不是"箭矢附着"类头衔（1~8、11 ✓）—— 这类头衔全都写**同一个** `arrowEffect`／`fireArrows` 字段 ✗
     * ⇒ 两个这类头衔一起给只会剩最后一个 ✓（`Apostle#TitleEffect` 就是 `switch` 里直接赋值 ✓ 已核反编译 `Apostle.java:736-791` ✓）。
     */
    private static boolean isArrowTitle(int index) {
        return (index >= 1 && index <= 8) || index == 11;
    }

    /**
     * 使徒**生成的那一刻**掷一次：{@link #DUAL_TITLE_CHANCE}（默认 10% ✓）⇒ 变成**双头衔使徒** ✓。
     * <p>做法（**不碰诡厄内部** ✓）：
     * <ol>
     *   <li>⚠ <b>不再用 `loadedFromDisk()` 当开关</b> ✗（§860：实测 17 只使徒 0 命中，且那 17 只是现场刷的还是读档进来的
     *       用日志分不出来 ✗）⇒ 改成我们自己的**一次性标记** {@link #KEY_TITLE_ROLLED} ✓
     *       ⇒ 每只使徒**一辈子只掷一次** ✓ 与它是怎么进世界的无关 ✓；</li>
     *   <li>只对"名字以主头衔结尾"的使徒生效 ✓（= 诡厄自己拼的名 ✓；玩家用命名牌改过名的 ⇒ 不硬塞 ✓）；
     *       名字**还没设**（罕见）⇒ 直接返回且**不落标记** ✓，留到下次装载再判 ✓；</li>
     *   <li>抽第二头衔 ✓；若与主头衔同属箭矢类 ⇒ 改抽 {@link #NON_ARROW_TITLES} ✓
     *       ⇒ **保证两个头衔都真生效** ✓（否则第二个纯摆设 ✗）；</li>
     *   <li>写进我们的 NBT ✓（重登不丢 ✓ 也不会重掷 ✓）＋ 调一次诡厄自己的
     *       {@code Apostle#TitleEffect(第二头衔)} ✓；</li>
     *   <li>名字追加第二头衔 ✓（头衔文本**自带前导空格** ✓ ⇒ 出来就是「麻风 毒蝎之尾 荣耀之名」✓）。</li>
     * </ol>
     * <p>⚠ 每次判定都会打一行 INFO 日志 ✓（命中／未命中／跳过 + 掷点 ✓）—— 这是 §860 为了能把"到底掷没掷"看死 ✓；
     * 确认没问题后可以删掉这几行 ✗（对性能无影响 ✓ 使徒不会刷满屏 ✓）。
     */
    @SubscribeEvent
    public static void onApostleSpawn(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (!enabled()) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Apostle apostle)) return;
        // ⭐⭐ §1209 **亚波伦（Apollyon）不走我们的加强** ✗
        //   （⭐ 用户口径 ✓ 2026-10-10：「**让我的使徒加强别对亚波伦生效**」✓）
        //   ⚠ 用**权威字段**判 ✗（⭐ 反射 ⭐ `allTitlesApostle_1_20_1$isApollyon()` ✓）
        //     ⭐ 而不是下面那个"名字以头衔结尾"的**间接**判据 ✓。
        if (com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isApollyon(apostle)) {
            return;
        }
        net.minecraft.nbt.CompoundTag data = apostle.getPersistentData();
        // §1073 已经是双头衔的（**含读档进来的** ✓）⇒ 先把血量翻倍补齐 ✓（幂等 ✓ 有标记 ✓）就返回 ✓ 不重掷 ✓
        if (data.contains(KEY_SECOND_TITLE)) {
            applyDualTitleBuffs(apostle);
            return;
        }
        if (data.contains(KEY_TITLE_ROLLED)) return;                    // 这只使徒已经掷过 ⇒ 不重掷 ✓
        double chance;
        try {
            chance = DUAL_TITLE_CHANCE.get();
        } catch (Throwable ignored) {
            chance = 0.10D;
        }
        if (chance <= 0.0D) return;                                     // 关了就不落标记 ⇒ 以后开了还能生效 ✓

        int primary = apostle.getTitleNumber();
        net.minecraft.network.chat.Component custom = apostle.getCustomName();
        if (custom == null) return;                                     // 名字还没设 ⇒ 不落标记，下次再判 ✓
        data.putBoolean(KEY_TITLE_ROLLED, true);                        // ⇒ 从此这只使徒只掷这一次 ✓
        if (!custom.getString().endsWith(titleText(primary))) {          // 玩家改过名 ⇒ 不给头衔 ✓ 也不再判 ✓
            LOGGER.info("[使徒补丁] 双头衔判定 ⇒ 跳过（不是诡厄原生名：「{}」）", custom.getString());
            return;
        }

        double roll = apostle.getRandom().nextDouble();
        if (roll >= chance) {
            LOGGER.info("[使徒补丁] 双头衔判定 ⇒ 未命中（掷 {}/{}，主头衔「{}」）",
                    String.format(java.util.Locale.ROOT, "%.3f", roll),
                    String.format(java.util.Locale.ROOT, "%.3f", chance), titleText(primary).trim());
            return;
        }

        int second = apostle.getRandom().nextInt(APOSTLE_TITLE_COUNT);
        if (second == primary) second = (second + 1) % APOSTLE_TITLE_COUNT;
        if (isArrowTitle(primary) && isArrowTitle(second)) {
            int pick = apostle.getRandom().nextInt(NON_ARROW_TITLES.length);
            second = NON_ARROW_TITLES[pick];
            if (second == primary) second = NON_ARROW_TITLES[(pick + 1) % NON_ARROW_TITLES.length];
        }
        data.putInt(KEY_SECOND_TITLE, second);
        applyDualTitleBuffs(apostle);                                   // §1073 血量 ×2 ✓（伤害 ×1.5 在受击侧 ✓）
        try {
            apostle.TitleEffect(second);
        } catch (Throwable ignored) {
        }
        apostle.setCustomName(net.minecraft.network.chat.Component
                .literal(custom.getString() + titleText(second)).withStyle(custom.getStyle()));
        LOGGER.info("[使徒补丁] 双头衔使徒 ✓ ⇒「{}」＋「{}」（命中率 {}%，掷 {}/{}）",
                custom.getString().trim(), titleText(second).trim(), (int) (chance * 100.0D),
                String.format(java.util.Locale.ROOT, "%.3f", roll),
                String.format(java.util.Locale.ROOT, "%.3f", chance));
    }

    /**
     * §1073 双头衔使徒**额外 roll 一次战利品** ✓（用户口径：「**掉落物品时 roll 两次战利品**」✓）。
     * <p>做法 ✓：拿实体自己的战利品表（{@code LivingEntity#getLootTable()} ✓）**再掷一次** ✓，
     * 把结果包成物品实体塞进 {@link net.minecraftforge.event.entity.living.LivingDropsEvent#getDrops()} ✓
     * —— 塞进事件而不是直接丢到世界里 ✓，这样其它模组的掉落修正／统计仍看得到 ✓。
     * <p>⚠ 只对**双头衔**使徒生效 ✓（以我们自己的 {@link #KEY_SECOND_TITLE} 为准 ✓）；
     * 任何异常一律吞掉 ✓（fail-safe ✓ 绝不影响正常掉落 ✓）。
     */
    @SubscribeEvent
    public static void onLivingDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (!enabled()) return;
        if (!(event.getEntity() instanceof Apostle apostle)) return;
        if (!isDualTitle(apostle)) return;
        if (!(apostle.level() instanceof ServerLevel level)) return;
        try {
            net.minecraft.resources.ResourceLocation tableId = apostle.getLootTable();
            if (tableId == null) return;
            net.minecraft.world.level.storage.loot.LootTable table =
                    level.getServer().getLootData().getLootTable(tableId);
            if (table == null) return;
            // ⚠ 1.20.1 的 API：先建 LootParams（Builder 吃 ServerLevel ✓），再拿它去 getRandomItems ✓
            //   （首次写成 new LootContext.Builder(level) 编译报"找不到合适的构造器" ✗ 已按报错改正 ✓）
            net.minecraft.world.level.storage.loot.LootParams params =
                    new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                            .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, apostle)
                            .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, apostle.position())
                            .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DAMAGE_SOURCE, event.getSource())
                            .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.KILLER_ENTITY, event.getSource().getEntity())
                            .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.DIRECT_KILLER_ENTITY, event.getSource().getDirectEntity())
                            .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.LAST_DAMAGE_PLAYER,
                                    event.getSource().getEntity() instanceof Player p ? p : null)
                            .withLuck(event.getSource().getEntity() instanceof Player killerP ? killerP.getLuck() : 0.0F)
                            .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.ENTITY);
            int added = 0;
            for (net.minecraft.world.item.ItemStack stack : table.getRandomItems(params)) {
                if (stack.isEmpty()) continue;
                net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(
                        level, apostle.getX(), apostle.getY() + 0.5D, apostle.getZ(), stack);
                item.setDefaultPickUpDelay();
                event.getDrops().add(item);
                added++;
            }
            LOGGER.info("[使徒补丁] §1073 双头衔使徒 ⇒ 额外 roll 一次战利品 ✓（表 {}，追加 {} 项）", tableId, added);
        } catch (Throwable ignored) {
            // fail-safe ✓
        }
    }

    /**
     * 主世界二阶段**自回血** ✓ —— ⚠ 必须绕开诡厄自己的"禁疗"，否则根本回不上 ✗（§860 实测根因）：
     * <pre>
     * // Apostle.java:1046-1050（反编译实查 ✓）
     * public void heal(float amount) { if (!this.isSmited()) super.heal(amount); }
     * </pre>
     * 而 {@code isSmited()} = {@code antiRegen > 0} ✓，{@code antiRegen} 在**被带「亡灵杀手」附魔的武器打中**时
     * 会被置 1~5 秒（`Apostle.java:986-990` ✓）⇒ 只要打使徒的武器带亡灵杀手，
     * 任何 {@code heal()} 都**永远被吞** ✗（旧实现 `heal(4.0F)` 就是这么一次都没生效的 ✗）。
     * <p>⇒ 改用 {@code setHealth} 直接写血 ✓（是否无视禁疗可配 ✓ 默认无视 ✓；配成 false 就还是尊重诡厄口径 ✓）。
     * <p>速率：每 **1 秒**回 `最大生命 × overworld_regen_percent`（默认 1% ✓ 下限 1 点 ✓ 满血不动 ✓）。
     */
    private static void overworldRegen(Apostle apostle, long now) {
        double percent;
        boolean ignoreSmite;
        try {
            percent = OVERWORLD_REGEN_PERCENT.get();
            ignoreSmite = OVERWORLD_REGEN_IGNORES_SMITE.get();
        } catch (Throwable ignored) {
            percent = 0.01D;
            ignoreSmite = true;
        }
        if (percent <= 0.0D) return;
        float max = apostle.getMaxHealth();
        float before = apostle.getHealth();
        if (before >= max) return;
        float amount = (float) Math.max(1.0D, max * percent);
        if (!ignoreSmite) {
            apostle.heal(amount);                                       // 尊重诡厄"亡灵杀手禁疗" ✓
        } else {
            apostle.setHealth(Math.min(max, before + amount));           // 绕过覆写 ✓
        }
        // ⚠ 临时诊断（§861 ✓ 每 5 秒最多一行 ✓ 确认没问题后可删 ✗）
        if (now % 100L == 0L && apostle.getHealth() > before) {
            LOGGER.info("[使徒补丁] 主世界二段自回血 ⇒ {} → {} / {}（每秒 {}{}）",
                    String.format(java.util.Locale.ROOT, "%.1f", before),
                    String.format(java.util.Locale.ROOT, "%.1f", apostle.getHealth()),
                    String.format(java.util.Locale.ROOT, "%.1f", max),
                    String.format(java.util.Locale.ROOT, "%.1f", amount),
                    ignoreSmite ? "，无视亡灵杀手禁疗 ✓" : "，尊重禁疗 ✓");
        }
    }

    /**
     * 第二头衔的**持续维护** ✓（每 tick 一次 ✓ 只对双头衔使徒做事 ✓ 开销可忽略 ✓）。
     * <p>为什么需要：诡厄自己的 {@code addTitleEffect()}`（每 tick 补 9／10 那两个状态 ✓）**只看主头衔** ✗
     * ⇒ 第二头衔是 0／9／10 时得我们自己兜 ✓（{@code TitleEffect} 本身幂等 ✓ 赋值 ＋ 挂 5 tick 状态 ✓）。
     */
    private static void keepSecondTitle(Apostle apostle) {
        net.minecraft.nbt.CompoundTag data = apostle.getPersistentData();
        if (!data.contains(KEY_SECOND_TITLE)) return;
        int second = data.getInt(KEY_SECOND_TITLE);
        if (isArrowTitle(apostle.getTitleNumber()) && isArrowTitle(second)) return;   // 主头衔优先 ✓
        try {
            apostle.TitleEffect(second);
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  #1 二阶段射箭顺带爆燃陷阱（§858 用户口径：30% 几率 ✓）
    // ============================================================

    /** 二阶段每次射箭 ⇒ 这个几率顺带在目标脚下放一发爆燃陷阱 ✓ */
    private static final double ARROW_TRAP_CHANCE = 0.30D;

    /**
     * 使徒的箭出手时 ⇒ **二阶段有 30% 几率**同时在目标脚下甩一发爆燃陷阱 ✓（= 用户口径的"射箭同时施法" ✓）。
     * <p>为什么这么实现：诡厄的"射箭"（`ApostleBowGoal` ✓）与"施法"（`CastingSpellGoal` 等 ✓）是两套争控制位的
     * Goal ✗ ⇒ 真·同时只能改写它的 AI ✗（风险最高 ✗）；用户选了"事件层替代" ✓ ⇒
     * 用**公开构造** `FireBlastTrap(level,x,y,z)` ＋ `setOwner(使徒)` ✓（我们在 #9 已在用同一个 ✓）⇒ 不混入内部 ✓。
     */
    @SubscribeEvent
    public static void onArrowSpawn(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (!enabled()) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof DeathArrow arrow)) return;
        if (!(arrow.getOwner() instanceof Apostle apostle) || !apostle.isSecondPhase()) return;
        if (!flag(ARROW_FIRE_BLAST_TRAP, true)) return;      // §869 开关（#1 射箭顺带施法 ✓ 已实现 ✓）
        if (apostle.getRandom().nextDouble() >= ARROW_TRAP_CHANCE) return;
        LivingEntity target = apostle.getTarget();
        if (target == null) return;
        try {
            com.Polarice3.Goety.common.entities.util.FireBlastTrap trap =
                    new com.Polarice3.Goety.common.entities.util.FireBlastTrap(
                            (ServerLevel) event.getLevel(), target.getX(), target.getY() + 0.25D, target.getZ());
            trap.setOwner(apostle);
            trap.setAreaOfEffect(1.5F);                            // 与诡厄非下界口径一致 ✓
            event.getLevel().addFreshEntity(trap);
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  #10 柱子同屏上限 4 → 6（§858 用户口径 ✓ 两个维度都生效 ✓）
    // ============================================================

    /** 诡厄原版上限是 4（`MonolithSpellGoal.canUse` 里 `j < 4` ✓ 已核反编译 ✓）⇒ 按用户口径提到 6 ✓ */
    private static final int MONOLITH_CAP = 6;
    /** 补位间隔（tick ✓ §869 用户口径：召唤速度**减慢一倍** ⇒ 默认 10 秒＝200 tick ✓ 可在配置里改 ✓） */
    private static int monolithTopUpTicks() {
        try {
            return Math.max(1, MONOLITH_TOPUP_SECONDS.get()) * 20;
        } catch (Throwable ignored) {
            return 200;
        }
    }
    /** 使徒 → 下次可补位的时刻 ✓ */
    private static final Map<UUID, Long> MONOLITH_TOPUP_AT = new ConcurrentHashMap<>();

    /**
     * 柱子同屏上限 4 → 6 ✓（用户口径 ✓ **主世界与下界都生效** ✓）。
     * <p>不走 Mixin ✓：诡厄那套 `MonolithSpellGoal` 只在 `j < 4` 时才施法 ✗；
     * 我们**照它自己的写法**（`Apostle.java:2198-2207` ✓ 已核反编译 ✓）在二阶段补位到 6 ✓：
     * 随机 12~24 格偏移 ✓ `BlockFinder.SummonRadiusSight` 找落点 ✓ `setTrueOwner` ＋ `finalizeSpawn` ＋ 入世界 ✓。
     * <p>只补**二阶段 且 有攻击目标**的使徒 ✓ 每 5 秒最多一根 ✓ ⇒ 不会一口气铺满 ✓ 也不会空场刷柱 ✓。
     */
    private static void topUpMonoliths(ServerLevel level, Apostle apostle, long now) {
        if (!apostle.isSecondPhase() || apostle.getTarget() == null) return;
        java.util.List<com.Polarice3.Goety.common.entities.neutral.AbstractObsidianMonolith> alive;
        try {
            alive = level.getEntitiesOfClass(com.Polarice3.Goety.common.entities.neutral.AbstractObsidianMonolith.class,
                    apostle.getBoundingBox().inflate(64.0D),
                    monolith -> monolith.isAlive() && monolith.getTrueOwner() == apostle);
        } catch (Throwable t) {
            return;
        }
        if (alive.size() >= MONOLITH_CAP) return;
        Long next = MONOLITH_TOPUP_AT.get(apostle.getUUID());
        if (next != null && now < next) return;
        MONOLITH_TOPUP_AT.put(apostle.getUUID(), now + monolithTopUpTicks());
        try {
            int k = (12 + apostle.getRandom().nextInt(12)) * (apostle.getRandom().nextBoolean() ? -1 : 1);
            int l = (12 + apostle.getRandom().nextInt(12)) * (apostle.getRandom().nextBoolean() ? -1 : 1);
            net.minecraft.core.BlockPos.MutableBlockPos around =
                    apostle.blockPosition().offset(k, 0, l).mutable();
            com.Polarice3.Goety.common.entities.hostile.servants.ObsidianMonolith monolith =
                    new com.Polarice3.Goety.common.entities.hostile.servants.ObsidianMonolith(
                            com.Polarice3.Goety.common.entities.ModEntityType.OBSIDIAN_MONOLITH.get(), level);
            net.minecraft.core.BlockPos pos = com.Polarice3.Goety.utils.BlockFinder
                    .SummonRadiusSight(around, apostle, monolith, level, 5);
            monolith.moveTo(pos, 0.0F, 0.0F);
            monolith.setTrueOwner(apostle);
            monolith.finalizeSpawn(level, level.getCurrentDifficultyAt(around),
                    net.minecraft.world.entity.MobSpawnType.MOB_SUMMONED, null, null);
            level.addFreshEntity(monolith);
            LOGGER.info("[使徒补丁] #10 柱子补位 ⇒ 同屏 {} → {}", alive.size(), alive.size() + 1);
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  #3 狱云清增益（§857）
    // ============================================================

    /**
     * 狱云（{@code HellCloud}）半径内的玩家 ⇒ **清掉所有增益** ✓。
     * <p>不走 Mixin ✓：云本身是实体 ✓ ⇒ 直接按半径找玩家 ✓ 用现成的
     * {@link #clearBeneficialEffects} ✓（诡厄自己的 {@code HellCloud#hurtEntities} 只加
     * {@code BURN_HEX} 减益 ✗ 不清增益 ✓ ⇒ 这是纯加码 ✓）。
     */
    private static void purgeBuffsInHellClouds(ServerLevel level, Apostle apostle) {
        java.util.List<com.Polarice3.Goety.common.entities.projectiles.HellCloud> clouds;
        try {
            clouds = level.getEntitiesOfClass(com.Polarice3.Goety.common.entities.projectiles.HellCloud.class,
                    apostle.getBoundingBox().inflate(64.0D),
                    cloud -> cloud.isAlive());
        } catch (Throwable t) {
            return;
        }
        for (com.Polarice3.Goety.common.entities.projectiles.HellCloud cloud : clouds) {
            double radius = cloud.getRadius() + 0.5D;
            for (Player player : level.getEntitiesOfClass(Player.class, cloud.getBoundingBox().inflate(radius))) {
                if (player.isCreative() || player.isSpectator()) continue;
                clearBeneficialEffects(player);
            }
        }
    }

    // ============================================================
    //  #6 黑曜石柱猪灵蛮兵 ⇒ 下界合金（§857）
    // ============================================================

    /**
     * 黑曜石柱召唤出来的猪灵蛮兵 ⇒ **全身下界合金甲 ＋ 下界合金斧** ✓。
     * <p>证据（反编译 {@code AbstractObsidianMonolith.java:501-507} ✓）：柱子刷的是
     * {@code ZPiglinServant} ✓，**二阶段有 25% 概率**换成
     * {@code ZPiglinBruteServant}（猪灵蛮兵 ✓）＋ {@code setTrueOwner(使徒)} ✓。
     * <p>不走 Mixin ✓：每 20 tick 扫一遍"主人是使徒的蛮兵"✓ 装备不对就补上 ✓
     * ⇒ 被 {@code finalizeSpawn} 冲掉也会自愈 ✓（只认**主人是使徒**这一种 ⇒ 不误伤别处的蛮兵 ✓）。
     */
    private static void equipMonolithBrutes(ServerLevel level, Apostle apostle) {
        java.util.List<com.Polarice3.Goety.common.entities.neutral.ZPiglinBruteServant> brutes;
        try {
            brutes = level.getEntitiesOfClass(com.Polarice3.Goety.common.entities.neutral.ZPiglinBruteServant.class,
                    apostle.getBoundingBox().inflate(96.0D),
                    brute -> brute.isAlive() && brute.getTrueOwner() == apostle);
        } catch (Throwable t) {
            return;
        }
        for (com.Polarice3.Goety.common.entities.neutral.ZPiglinBruteServant brute : brutes) {
            net.minecraft.world.entity.EquipmentSlot head = net.minecraft.world.entity.EquipmentSlot.HEAD;
            net.minecraft.world.entity.EquipmentSlot chest = net.minecraft.world.entity.EquipmentSlot.CHEST;
            net.minecraft.world.entity.EquipmentSlot legs = net.minecraft.world.entity.EquipmentSlot.LEGS;
            net.minecraft.world.entity.EquipmentSlot feet = net.minecraft.world.entity.EquipmentSlot.FEET;
            net.minecraft.world.entity.EquipmentSlot hand = net.minecraft.world.entity.EquipmentSlot.MAINHAND;
            boolean done = brute.getItemBySlot(hand).is(net.minecraft.world.item.Items.NETHERITE_AXE)
                    && brute.getItemBySlot(head).is(net.minecraft.world.item.Items.NETHERITE_HELMET)
                    && brute.getItemBySlot(chest).is(net.minecraft.world.item.Items.NETHERITE_CHESTPLATE)
                    && brute.getItemBySlot(legs).is(net.minecraft.world.item.Items.NETHERITE_LEGGINGS)
                    && brute.getItemBySlot(feet).is(net.minecraft.world.item.Items.NETHERITE_BOOTS);
            if (done) continue;                                  // 已经换好 ⇒ 不折腾 ✓
            brute.setItemSlot(hand, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_AXE));
            brute.setItemSlot(head, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_HELMET));
            brute.setItemSlot(chest, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_CHESTPLATE));
            brute.setItemSlot(legs, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_LEGGINGS));
            brute.setItemSlot(feet, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_BOOTS));
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                brute.setDropChance(slot, 0.0F);                 // 不给你捡 ✓（与诡厄自己口径一致 ✓）
            }
        }
    }

    /** #12：把使徒所在区块刷出来的袭击者**全部认使徒为主人**（诡厄仆从 ✓）（§856） */
    private static void spawnRaidWave(ServerLevel level, Apostle apostle) {
        int spawned = 0;
        int servants = 0;
        for (int i = 0; i < RAID_WAVE_SIZE * 8 && spawned < RAID_WAVE_SIZE; i++) {
            int x = apostle.getBlockX() + apostle.getRandom().nextInt(33) - 16;
            int z = apostle.getBlockZ() + apostle.getRandom().nextInt(33) - 16;
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x,
                    level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);

            // 优先刷**诡厄自己的仆从掠夺者**（只有它们能认使徒为主人 ✓）；一个都取不到才退回原版掠夺者 ✓
            net.minecraft.world.entity.Mob raider = createGoetyServant(level, spawned);
            if (raider != null) {
                servants++;
                setServantOwner(raider, apostle);      // ⭐ 必须先认主：诡厄的 finalizeSpawn 会按"有没有主人"给装备 ✓
            } else {
                raider = (spawned % 3 == 2)
                        ? net.minecraft.world.entity.EntityType.VINDICATOR.create(level)
                        : net.minecraft.world.entity.EntityType.PILLAGER.create(level);
            }
            if (raider == null) continue;
            raider.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    apostle.getRandom().nextFloat() * 360.0F, 0.0F);
            try {
                // 走一遍 finalizeSpawn ⇒ 掠夺者才有弩／武器 ✓（用 EVENT 类型：不触发诡厄的召唤粒子／寿命那套 ✓）
                raider.finalizeSpawn(level, level.getCurrentDifficultyAt(pos),
                        net.minecraft.world.entity.MobSpawnType.EVENT, null, null);
            } catch (Throwable ignored) {
            }
            raider.getPersistentData().putUUID(KEY_MASTER, apostle.getUUID());
            level.addFreshEntity(raider);
            spawned++;
        }
        if (spawned > 0) {
            level.playSound(null, apostle.blockPosition(), net.minecraft.sounds.SoundEvents.RAID_HORN.value(),
                    net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 1.0F);
            LOGGER.info("[使徒补丁] #12 使徒剩 {}% 血 ⇒ 刷了 {} 只袭击者（诡厄仆从 {} 只 ✓ 全部认使徒为主人 ✓）",
                    (int) (apostle.getHealth() / apostle.getMaxHealth() * 100.0F), spawned, servants);
        }
    }

    // ============================================================
    //  #12 认主（§856）
    // ============================================================

    /** 我们自己打在袭击者身上的"主人是谁"标记 ✓（便于排查／其它模组读 ✓） */
    private static final String KEY_MASTER = "tinkersnewlife.apostle_master";

    /**
     * 刷哪种袭击者 ✓ —— **写死诡厄自己的仆从掠夺者注册名** ✓（名单取自诡厄 {@code goety:raider_servants}
     * 标签 ✓ 已逐个核对 2.5.54.5 里都存在 ✓）；用 <b>注册名</b>取类型 ✓ 不引用具体类 ✗ ⇒ 跨版本安全 ✓。
     */
    private static final net.minecraft.resources.ResourceLocation[] RAID_SERVANT_IDS =
            new net.minecraft.resources.ResourceLocation[]{
                    new net.minecraft.resources.ResourceLocation("goety", "pillager_servant"),
                    new net.minecraft.resources.ResourceLocation("goety", "vindicator_servant"),
                    new net.minecraft.resources.ResourceLocation("goety", "mountaineer_servant"),
                    new net.minecraft.resources.ResourceLocation("goety", "crusher_servant"),
                    new net.minecraft.resources.ResourceLocation("goety", "piker_servant"),
                    new net.minecraft.resources.ResourceLocation("goety", "evoker_servant"),
                    new net.minecraft.resources.ResourceLocation("goety", "witch_servant"),
                    new net.minecraft.resources.ResourceLocation("goety", "ravager"),
            };

    private static boolean servantMissingLogged = false;

    /** 按注册名取一个诡厄仆从类型 ✓（一次一个 ✓ 取不到就换下一个 ✓ 全取不到 ⇒ null ⇒ 调用方退回原版 ✓） */
    private static net.minecraft.world.entity.EntityType<?> goetyServantType(int index) {
        int n = RAID_SERVANT_IDS.length;
        for (int i = 0; i < n; i++) {
            net.minecraft.resources.ResourceLocation id = RAID_SERVANT_IDS[(index + i) % n];
            try {
                net.minecraft.world.entity.EntityType<?> type =
                        net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(id);
                if (type != null) return type;
            } catch (Throwable ignored) {
            }
        }
        if (!servantMissingLogged) {
            servantMissingLogged = true;
            LOGGER.warn("[使徒补丁] #12 没找到诡厄仆从掠夺者 ⇒ 本次退回原版掠夺者 ✓");
        }
        return null;
    }

    private static net.minecraft.world.entity.Mob createGoetyServant(ServerLevel level, int index) {
        try {
            net.minecraft.world.entity.EntityType<?> type = goetyServantType(index);
            if (type == null) return null;
            Entity entity = type.create(level);
            return (entity instanceof net.minecraft.world.entity.Mob mob) ? mob : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 认主 ✓ —— 走诡厄 {@code IOwned#setTrueOwner} ✓（反射 ⇒ 不写死签名 ✓ 版本无关 ✓） */
    private static void setServantOwner(Entity servant, LivingEntity master) {
        try {
            if (!(servant instanceof com.Polarice3.Goety.api.entities.IOwned owned)) return;
            for (String name : new String[]{"setTrueOwner", "setOwner", "setMaster"}) {
                try {
                    owned.getClass().getMethod(name, LivingEntity.class).invoke(owned, master);
                    return;
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /*
     * ⚠ 已知行为，且**用户明确表示接受**（§856 用户原话：「算了，自杀就自杀吧」）：
     * 诡厄 {@code IOwned#ownedByServantTick()} 里有
     * {@code if (主人类型 ∈ goety:bosses / summon_kill && 主人已移除或濒死) mob.kill();}
     * （反编译 {@code IOwned.java:308-310} ✓），而**使徒同时挂在 {@code forge:bosses} 与
     * {@code goety:bosses} 两个标签里** ✓ ⇒ 使徒一死，认它为主的袭击者会**跟着一起没** ✓。
     * ⇒ 因此**不做**"主人死后解除归属让袭击者留下"那一套 ✗（早先的 #12 需求按用户本条口径作废 ✓）。
     */

    // ============================================================
    //  §869 新增：柱子生命周期 / 隔墙瞬移 / 箭雨
    // ============================================================

    /** 箭雨冷却表 ✓（使徒 → 下次可放箭雨的游戏刻 ✓） */
    private static final Map<UUID, Long> ARROW_RAIN_AT = new ConcurrentHashMap<>();

    /**
     * <b>黑曜石柱的生命周期</b> ✓（§869 用户口径）：血量随时间流逝，**每秒 2 点** ✓（可配 ✓）。
     * <p>流到 0 ⇒ 柱子自己死掉 ✓（用 {@code kill()} ⇒ 走正常死亡流程 ✓ 掉落/粒子照旧 ✓）。
     * <p>只动"主人是使徒"的柱子 ✓（别的模组/玩家放的不管 ✗）。
     */
    private static void drainMonoliths(ServerLevel level, Apostle apostle) {
        if (!flag(MONOLITH_LIFESPAN, true)) return;
        double perSecond;
        try {
            perSecond = MONOLITH_LIFESPAN_HP_PER_SECOND.get();
        } catch (Throwable ignored) {
            perSecond = 2.0D;
        }
        if (perSecond <= 0.0D) return;
        java.util.List<com.Polarice3.Goety.common.entities.neutral.AbstractObsidianMonolith> list;
        try {
            list = level.getEntitiesOfClass(com.Polarice3.Goety.common.entities.neutral.AbstractObsidianMonolith.class,
                    apostle.getBoundingBox().inflate(96.0D),
                    m -> m.isAlive() && m.getTrueOwner() == apostle);
        } catch (Throwable ignored) {
            return;
        }
        for (com.Polarice3.Goety.common.entities.neutral.AbstractObsidianMonolith monolith : list) {
            float left = monolith.getHealth() - (float) perSecond;
            if (left <= 0.0F) {
                monolith.kill();                                  // 寿命到 ⇒ 柱子自行崩解 ✓
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                        monolith.getX(), monolith.getY() + 1.0D, monolith.getZ(), 25, 0.4D, 0.6D, 0.4D, 0.02D);
            } else {
                monolith.setHealth(left);
            }
        }
    }

    /**
     * <b>隔墙瞬移</b> ✓（§869 用户口径）：使徒与玩家之间**没有视线**（隔着墙）时，
     * 尝试瞬移到**玩家身后**（沿玩家面朝的**反方向** 1.5 格 ✓）；
     * 身后没有落脚位置 ⇒ 直接瞬移到**玩家所在位置** ✓。
     * <p>只对"使徒当前的攻击目标且是玩家"生效 ✓ 10 tick 检查一次 ✓ 不碰任何诡厄内部状态 ✓。
     */
    private static void wallTeleportBehind(ServerLevel level, Apostle apostle, long now) {
        if (!flag(WALL_TELEPORT, true)) return;
        LivingEntity target = apostle.getTarget();
        if (!(target instanceof net.minecraft.server.level.ServerPlayer player)) return;
        if (player.isCreative() || player.isSpectator()) return;
        double dist = apostle.distanceTo(player);
        if (dist > 48.0D || dist < 2.0D) return;
        try {
            if (apostle.hasLineOfSight(player)) return;           // 看得见 ⇒ 不瞬移 ✓（只有隔墙才用这招 ✓）
        } catch (Throwable ignored) {
            return;
        }
        net.minecraft.world.phys.Vec3 behind = player.position().subtract(player.getLookAngle().scale(1.5D));
        net.minecraft.world.phys.Vec3 dest = canStandAt(level, apostle, behind) ? behind : player.position();
        try {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                    apostle.getX(), apostle.getY() + 1.0D, apostle.getZ(), 30, 0.3D, 0.6D, 0.3D, 0.3D);
            apostle.teleportTo(dest.x, dest.y, dest.z);
            apostle.hurtMarked = true;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                    dest.x, dest.y + 1.0D, dest.z, 30, 0.3D, 0.6D, 0.3D, 0.3D);
            level.playSound(null, apostle.blockPosition(), net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.8F);
        } catch (Throwable ignored) {
        }
    }

    /** 这个位置站得下吗 ✓（把使徒的碰撞箱搬过去问一次"有没有碰撞" ✓ 再要求脚下不是空的 ✓） */
    private static boolean canStandAt(ServerLevel level, LivingEntity entity, net.minecraft.world.phys.Vec3 pos) {
        try {
            net.minecraft.world.phys.AABB box = entity.getBoundingBox()
                    .move(pos.x - entity.getX(), pos.y - entity.getY(), pos.z - entity.getZ());
            if (!level.noCollision(entity, box)) return false;
            net.minecraft.core.BlockPos below = net.minecraft.core.BlockPos.containing(pos.x, pos.y - 0.1D, pos.z);
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(below);
            return !state.getCollisionShape(level, below).isEmpty() || !state.getFluidState().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * <b>箭雨</b> ✓（§869 用户口径）：给使徒的法术组加上诡厄「箭雨聚晶」的效果 ✓ ——
     * 直接**公开构造** {@code ArrowRainTrap(level, x, y, z)} ＋ {@code setOwner(使徒)} ✓
     * （已核反编译：它的构造就是这三参 ✓ `ArrowRainTrap.java:51-54` ✓，与 #9 的爆燃陷阱同一套写法 ✓）。
     * <p>一轮：在**当前目标脚下**放一处箭雨 ✓；冷却默认 20 秒 ✓ 可配 ✓；只对"有目标且在 32 格内"的使徒放 ✓。
     */
    private static void castArrowRain(ServerLevel level, Apostle apostle, long now) {
        if (!flag(ARROW_RAIN, true)) return;
        LivingEntity target = apostle.getTarget();
        if (target == null || apostle.distanceTo(target) > 32.0D) return;
        Long next = ARROW_RAIN_AT.get(apostle.getUUID());
        if (next != null && now < next) return;
        int cooldownSeconds;
        try {
            cooldownSeconds = ARROW_RAIN_COOLDOWN_SECONDS.get();
        } catch (Throwable ignored) {
            cooldownSeconds = 20;
        }
        ARROW_RAIN_AT.put(apostle.getUUID(), now + Math.max(1, cooldownSeconds) * 20L);
        try {
            com.Polarice3.Goety.common.entities.util.ArrowRainTrap rain =
                    new com.Polarice3.Goety.common.entities.util.ArrowRainTrap(
                            level, target.getX(), target.getY(), target.getZ());
            rain.setOwner(apostle);
            level.addFreshEntity(rain);
            LOGGER.info("[使徒补丁] 使徒释放【箭雨】⇒ 目标 {}（冷却 {} 秒 ✓）",
                    target.getName().getString(), cooldownSeconds);
        } catch (Throwable t) {
            LOGGER.warn("[使徒补丁] 箭雨释放失败：{}", t.toString());
        }
    }

}
