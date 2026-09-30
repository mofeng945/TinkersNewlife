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
 * 本补丁自带一份 COMMON 配置 {@code tinkersnewlife-apostle.toml} ✓：
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

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("诡厄巫法·使徒改造补丁（§853）").push("apostle");
        ENABLED = b.comment("总开关（默认开启）").define("enabled", true);
        TELEPORT_DAMAGE_REDUCTION = b
                .comment("瞬移后 2 秒内的减伤比例（0.6 = 减伤 60%）")
                .defineInRange("teleport_damage_reduction", 0.6D, 0.0D, 1.0D);
        ARROW_AS_MAGIC = b.comment("使徒射出的箭造成魔法伤害（默认 true）").define("arrow_as_magic", true);
        b.pop();
        SPEC = b.build();
    }

    private ApostlePatch() {}

    /** 由 {@link GoetyIntegration#register} 调用 ✓ */
    public static void register() {
        try {
            ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "tinkersnewlife-apostle.toml");
        } catch (Throwable t) {
            LOGGER.warn("[使徒补丁] 配置注册失败（用默认值继续）: {}", t.toString());
        }
        MinecraftForge.EVENT_BUS.register(ApostlePatch.class);
        LOGGER.info("[使徒补丁] 已启用（默认开启 ✓ 配置 tinkersnewlife-apostle.toml ✓）：瞬移后 2s 减伤 60% ✓ 射箭改魔法伤害 ✓");
    }

    private static boolean enabled() {
        try {
            return ENABLED.get();
        } catch (Throwable ignored) {
            return true;                                  // 配置还没加载时按"默认开启"走 ✓
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
    }

    // ============================================================
    //  #2 减伤 ＋ #11 箭改魔法伤害
    // ============================================================

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!enabled()) return;

        // ── #11：使徒的箭 ⇒ 改成魔法伤害（同额度重发 ✓）──────────────────────
        if (ARROW_AS_MAGIC.get()
                && event.getSource().getDirectEntity() instanceof DeathArrow arrow
                && arrow.getOwner() instanceof Apostle apostle) {
            LivingEntity victim = event.getEntity();
            float amount = event.getAmount();
            if (amount > 0.0F && !victim.level().isClientSide) {
                event.setCanceled(true);
                victim.invulnerableTime = 0;
                victim.hurt(victim.damageSources().indirectMagic(apostle, apostle), amount);
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

    /** 供将来组二/组三使用：把一个玩家增益全清掉（#3 狱云 ✓ 预留 ✓） */
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
}
