package com.mofengbaizhi.tinkersnewlife.util;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * <b>万能穿透</b>：本模组所有"真伤/穿透"路径的**唯一实现**。
 *
 * <p>以前有三套各写各的：穿透EX（自有 {@code true_pierce} 源 + 两段式）、
 * 天逆鉾（Goety 真伤源 + 分块连打 / 差额直补）、墨默（直接调 {@code GoetyBridge.pierceFullDamage}）。
 * 现在统一到这里，任何武器（天逆鉾 / 游云 / 带穿透EX 的工具 / 墨默的挥击）都调 {@link #apply}。
 *
 * <h2>为什么"穿透"要分两层做</h2>
 * <ol>
 *   <li><b>{@code hurt()} 内部</b>（无敌、护甲、抗性、无敌帧、盾牌…）→ 靠伤害源的
 *       {@code #minecraft:bypasses_*} 标签穿。本类默认用 Goety 的真伤源（拿不到时用本模组的
 *       {@code tinkersnewlife:true_pierce}，两者都带 {@code bypasses_invulnerability}）。</li>
 *   <li><b>事件层</b>（别的 mod 在 {@code LivingHurtEvent}/{@code LivingDamageEvent} 里改数值，
 *       例如 akaishi 把监守者单次受击压到 24、启示录给亚波伦加单次 20 上限）→ 标签**完全无用**。
 *       对策是两条：① 每段打击前 {@link #markIntent 登记"想造成多少"}，在 LOWEST 优先级把数值放回去；
 *       ② 打完按"应打 − 已打"的**差额直接 setHealth 补齐**。</li>
 * </ol>
 *
 * <h2>做法（顺序即优先级）</h2>
 * <pre>
 *   ⓪ 绝对防御穿透：把"不看伤害标签、只看攻击者属性"的免疫临时失效
 *      （{@link AbsoluteDefense}：潘多拉之咒·现实压制在 LivingAttackEvent 里直接取消打击，
 *       标签与事件层顶开都够不着 → 只能把攻击者的"现实指数"临时抬过阈值）
 *   ① 命灯指轮共存：攻击者戴着命灯 → 本次最多打到"只剩一点血"（不杀死）
 *   ② 先禁疗 + 清掉本模组自己的"伤害限幅"效果（否则会把多段伤害吞掉）
 *   ③ 下界亚波伦这类"hurt() 管线全被取消"的 Boss → 直接 setHealth 扣血 + 主动 die()
 *   ④ 其余目标：分块连打（每段 ≤19，上限 64 段），每段登记意图；
 *      若"带攻击者的源"完全打不动 → 换**无主源**再打一遍（Mowzie 钢铁守护者那类"有攻击者即免疫"）
 *   ⑤ 差额直补：被免伤窗/限伤吃掉的部分直接按血量差补上；致死时补 die()，让掉落与击败逻辑正常走
 * </pre>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TruePierce {

    private TruePierce() {
    }

    /** 本模组的真伤类型（带 7 个 bypasses_* 标签，见 data/minecraft/tags/damage_type/） */
    private static final ResourceKey<DamageType> PIERCE_TYPE_KEY = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(TinkersNewlife.MOD_ID, "true_pierce"));

    private static volatile Holder<DamageType> cachedPierceType = null;

    /** 单段伤害上限：低于常见的"单次限伤"阈值，保证每段都能落地 */
    private static final float CHUNK = 19.0F;
    /** 分块段数上限（19 × 64 ≈ 1200 点，足够任何 Boss） */
    private static final int MAX_CHUNKS = 64;

    /** 一次穿透"想造成多少"（key = 目标 UUID，带 tick 防残留） */
    private record Intent(float amount, long tick) {
    }

    private static final Map<UUID, Intent> INTENT = new ConcurrentHashMap<>();

    // ============================================================
    //  ⭐⭐ 逆向改血（⭐ 用户口径 ✓ 2026-10-10 ✓）
    // ============================================================

    /**
     * ⭐⭐ <b>逆向改血</b>：⭐ 绕过 {@code setHealth}，**直接改血量的真身字段** ✗。
     *
     * <h2>⭐ 为什么需要它（⭐ 用户口径 ✓）</h2>
     * ⭐ 用户定义 ✓：⭐「**逆向改血通常指对抗或绕过目标自身的"锁血"或"防清除"机制。
     * 它的目标不是造成伤害，而是强制修改目标的血量，使其保护逻辑失效**」✓
     * <p>⚠ 原理 ✓：⭐ 血量真身是 ⭐ `LivingEntity#health`（⭐ **`private float`** ✓）
     * ⭐ 而 ⭐ `setHealth(float)` ⭐ **不是 `final`** ✗ ⇒ ⭐ 别人可以**覆写它**做"锁血" ✓
     * （⭐ 或者在 `LivingHurtEvent` 里把数值改回来 ✓ ⭐ 或者"单次限伤" ✓）
     * ⇒ ⭐ **只要还在用 `setHealth`，就永远可能被拦** ✗
     * ⇒ ⭐ 那就 ⭐ **反射直写那个字段** ✗ ⭐ 让它的保护逻辑**读不到"你是在改血"** ✓ ✓。
     *
     * <h2>⚠⚠ 三个必须处理的细节</h2>
     * <ol>
     *   <li>⭐ **SRG 名** ✗：⭐ 生产环境字段名是 ⭐ `f_20920_` ✗ ⭐ 开发环境是 `health` ✓
     *       ⇒ ⭐ **两个名字都要试** ✓（⭐ 都找不到 ⇒ ⭐ 退回 `setHealth` ✓）；</li>
     *   <li>⭐ **必须 `hurtMarked = true`** ✗✗ —— ⭐ 直写字段**不会同步客户端** ✗
     *       ⭐ 不写这句 ⭐ 服务端血没了但**客户端血条不动** ✓（⭐ 看着像没伤害 ✓）；</li>
     *   <li>⭐ **缓存 `Field`** ✗ —— ⭐ 反射查找很贵 ✓ ⭐ 只找一次 ✓ ⭐ 之后直接 `set` ✓。</li>
     * </ol>
     *
     * <h2>⚠ 局限（⭐ 如实 ✓）</h2>
     * ⚠ ⭐ 反射写字段 ⭐ **跨版本必挂** ✗（⭐ 字段映射一变就失效 ✓ ⭐ 那时自动退回 `setHealth` ✓）；
     * ⚠ ⭐ 若目标有 ⭐ **"血量为 0 也不死"** 的保护 ✗ ⭐ 写字段**也没辙** ✓
     * （⭐ 那种只能靠"实体清除链" ✓ ⭐ 见 §1185／§1187 ✓）。
     */
    private static java.lang.reflect.Field HEALTH_FIELD = null;
    /** ⭐ 0 ＝ 还没找过 ✓ ⭐ 1 ＝ 找到了 ✓ ⭐ -1 ＝ 找不到（⭐ 永远退回 `setHealth` ✓） */
    private static volatile int HEALTH_FIELD_STATE = 0;

    // ============================================================
    //  ⭐⭐⭐⭐ §1214 "额外血量通道"（⭐ 启示录亚波伦 ✗ 与它同类的东西 ✓）
    // ============================================================

    /**
     * §1228 "累计未落地伤害处决"开关 ✗ —— ⭐ 默认关闭 ✓。
     * <p>⭐ 打开后：没落地的差额会累计，累计 ≥ 当前报的血 ⇒ 直接处决 ✓（适用于"任何通道都写不进血"的目标 ✓）。
     */
    private static final boolean CUMULATIVE_EXECUTE = false;

    /**
     * ⭐⭐⭐⭐ §1232 <b>"锁期间关闭客户端同步"开关</b> ✗ —— ⭐ 用户实测逼出来的 ✓。
     *
     * <p>⚠ 用户口径 ✓ 2026-10-10：「**怎么打一次又弹了3段伤害？**」✓
     * <p>⚠ 根因 ✗：⭐ 跨 tick 锁 ⭐ **每 tick 都写一次** ✓（⭐ 写 → 它回血 → 再写 ✓）
     * ⭐ 而 ⭐ §1231 新加的客户端同步 ＋ ⭐ `rawSetHealthInner` 自带的 `setHealth`／`hurtMarked`
     * ⇒ ⭐ 客户端**每 tick 都看到血量变一次** ⇒ ⭐ 伤害数字**连弹** ✓ ✓
     * <p>⭐ 修法 ✗：⭐ 锁 ⭐ **只服务端压血** ✓ ✗ ⭐ **不同步** ✓
     * （⭐ 那一发的同步交给 ⭐ **第一次 `writeAndVerify`** ✓ ⭐ 一次命中 ⇒ ⭐ 一个数字 ✓）。
     */
    private static volatile boolean SUPPRESS_CLIENT_SYNC = false;

    /** ⭐⭐⭐⭐ §1231 **"合成事件"标记** ✗ —— ⭐ 防递归 ＋ ⭐ 防伤害翻倍 ✓ */
    private static volatile boolean SYNTHETIC_HURT = false;

    /**
     * ⭐⭐ §1231 <b>要不要 Post 合成 `LivingHurtEvent`</b> ✗ —— ⭐ 默认开 ✓ ⚠ 出问题可关 ✓。
     *
     * <p>⭐ 用户口径 ✓ 2026-10-10：「**记得改血后补上受击事件和效果并返回对应伤害**」✓
     * <p>⚠ <b>如实风险</b> ✗：⭐ 仓库里**有几十个** `LivingHurtEvent` 处理器 ✓
     * ⭐ 我们 post 的这一发 ⭐ **会把它们全叫一遍** ✗
     * （⭐ 大多数自家增幅器 ⭐ 会因 `DamagePipeline.skipNested()` **早退** ✓
     *  ⚠ 但**个别**没写早退的 ✗ ⭐ 可能在合成事件上**再补一次效果** ✓）
     * ⇒ ⭐ 若发现 ⭐ "伤害/效果翻倍" ✗ ⭐ 把这里改 `false` 即可 ✓ ✓
     * （⭐ **客户端血量同步不受影响** ✗ ⭐ 那才是两个伤害显示 mod 的主通道 ✓）。
     */
    private static final boolean POST_SYNTHETIC_HURT_EVENT = false;

    /**
     * ⭐ 此刻是不是 ⭐ **我们自己 post 的合成 `LivingHurtEvent`** 回调 ✗。
     * <p>⭐ `apply` 内部已经挡了一道 ✓ ✗ ⭐ 别的自家 `LivingHurtEvent` 处理器
     * ⭐ 也可以加这一句 ⭐ **早退** ✓（⭐ 免得在合成事件上重复生效 ✓）。
     */
    public static boolean isSyntheticHurt() {
        return SYNTHETIC_HURT;
    }

    /** ⭐⭐ §1238 **"上一发开始时它报的血"** ✗ —— ⭐ 用来分辨"血会不会自己弹回去" ✓ */
    private static final java.util.Map<java.util.UUID, Float> LAST_START_HP =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * ⭐⭐ §1228 <b>"累计未落地伤害"账</b> ✗ —— ⭐ 用于 ⭐ "免疫期处决" ✓。
     *
     * <p>⚠ 为什么需要它 ✗：⭐ §1221／⭐ §1226 已**穷举实证** ⭐ 那个 Boss 的血
     * ⭐ **任何反射通道都写不动** ✓（⭐ 且 ⭐ 非隐藏类 ✓）
     * ⇒ ⭐ 于是 ⭐ **不跟"它的血"较劲** ✗ ⭐ 改成 ⭐ **记"我们打掉了多少"** ✓
     * ⭐ 打够（⭐ ≥ 它当前报的血 ✓）⇒ ⭐ **处决** ✓ ✓。
     * <p>⭐ 普通 Boss ⭐ `dealt ≈ want` ⇒ ⭐ `deficit ≈ 0` ⇒ ⭐ **这个账永远是 0** ✓ ✓。
     */
    private static final java.util.Map<java.util.UUID, Float> DAMAGE_OWED =
            new java.util.concurrent.ConcurrentHashMap<>();

    // ============================================================
    //  ⭐⭐⭐⭐ §1221 **循环改血**（⭐ 用户点名要 ✓）
    // ============================================================

    /** ⭐ 找到的"真能改血"的槽位 ✗（⭐ 字段 ＋ 它属于谁 ✓） */
    private static java.lang.reflect.Field healthField() {
        return HEALTH_FIELD_STATE == 1 ? HEALTH_FIELD : null;
    }

    /** ⭐ 按名字取字段 ✗ 取不到返回 null ✓ */
    private static java.lang.reflect.Field fieldOrNull(String name) {
        try {
            return net.minecraft.world.entity.LivingEntity.class.getDeclaredField(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * ⭐⭐ §1201 <b>按值校验／反查血量字段</b> ✗ —— ⭐ 拿实体实测一次 ✓
     * （⭐ 探针发现"字段 ≠ `getHealth()`" ⇒ ⭐ 名字不可信 ✓）。
     *
     * @return ⭐ 找到的字段（⭐ 已 `setAccessible` ✓）⭐ 或 null ✓
     */
    private static java.lang.reflect.Field detectHealthField(LivingEntity sample) {
        // TNL1402 按值探测不可靠（多个字段可能同时等于血量）✗ ⇒ 改成**写入探测** ✓：
        //   逐个 float 字段临时写 `原值 + 1`，看 `getHealth()` 变不变；变了就是它 ✓（写完全部还原 ✓）
        for (Class<?> cls = sample.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                if (f.getType() != float.class) continue;
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    float orig = f.getFloat(sample);
                    try {
                        f.setFloat(sample, orig + 1.0F);
                        if (Math.abs(sample.getHealth() - (orig + 1.0F)) < 0.01F) {
                            f.setFloat(sample, orig);
                            TinkersNewlife.LOGGER.debug(
                                    "[真伤·字段探测] {} 的血量字段 = {}（{}）✓",
                                    sample.getName().getString(), f.getName(), f.getDeclaringClass().getSimpleName());
                            return f;
                        }
                    } finally {
                        try {
                            f.setFloat(sample, orig);
                        } catch (Throwable ignored) {
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    /** ⭐ **逆向读血**：⭐ 直读字段 ✓（⭐ 拿不到就退回 `getHealth()` ✓） */
    public static float rawHealth(LivingEntity target) {
        // ⚠⚠ §1222 **影子血已回退** ✗（⭐ 用户实测「**不掉血了？？？**」✓）
        //   ⭐ 教训：⭐ **"记账代替真写"会让真身彻底不掉血** ✗ ⭐ 绝不能这么兜底 ✓。
        float viaApi = target.getHealth();
        // TNL1407 "亚波伦额外血量通道"已按用户要求整段移除 ⇒ 1.20.1 只认 getHealth() ✓
        // ⭐⭐ §1201 **字段按值反查**（⭐ 只做一次 ✓ 用**这个**实体当样本 ✓）
        java.lang.reflect.Field f = healthFieldFor(target);
        if (f != null) {
            try {
                float viaField = f.getFloat(target);
                // ⭐⭐⚠⚠ **两个通道取大的** ✗✗（⭐ 用户实测 2026-10-10 ✓ 探针实证 ✓）：
                //   ⭐ 日志显示 ⭐ `[真伤] want=30.50 startHp=0.00 … 目标存活=true` ✗
                //   ⇒ ⭐ 字段读出 **0** ✗ ⭐ 而 ⭐ 它还**活着** ✓ ⇒ ⭐ **矛盾** ✓
                //   ⇒ ⭐ 说明 ⭐ **有些 Boss 的血不在那个字段里** ✗
                //   ⇒ ⭐ **两个都读、取较大的那个** ✓ ✓ —— ⭐ 谁都不能骗过这一条 ✓。
                return Math.max(viaField, viaApi);
            } catch (Throwable ignored) {
            }
        }
        return viaApi;
    }

    /**
     * ⭐⭐ §1201 <b>取"这个实体"的血量字段</b> ✗ —— ⭐ 按值反查 ＋ 缓存 ✓。
     * <p>⚠ 为什么要**按实体**查 ✗：⭐ 不同实体类的真身字段**可能不同** ✗
     * （⭐ 有的 mod 会加自己的 `health` 遮蔽父类的 ✓ ⭐ 探针就抓到过一个假的 ✓）
     * ⇒ ⭐ 用"**值等于 `getHealth()`**"来认，最稳 ✓。
     */
    private static final java.util.Map<Class<?>, java.lang.reflect.Field> FIELD_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * TNL1403 <b>1.20.1 没有 `health` 字段</b> ✗
     *
     * <p>⚠ 实测（2026-10-31，Mowzie 钢铁守护者）：
     * 1.20.1 的 `LivingEntity` **没有** `float health` 字段 ✗ ——
     * `getHealth()` 读的是 `entityData.get(DATA_HEALTH_ID)` ✓；
     * `setHealth()` 写 `entityData.set(DATA_HEALTH_ID, clamp(...))` ✓。
     * （我用 SRG jar `javap` 核对过：`LivingEntity` 的全部 float 字段里根本没有 `health` ✓）
     *
     * <p>⇒ 以前代码里"`f_20920_` 就是 health"是**旧版本残留的错映射** ✗
     *（`f_20920_` 实际是 `oAttackAnim` ✗ 往那里写血 = 血一点不掉 ✓）；
     * ⇒ 1.20.1 只能走 `setHealth`／`entityData` ✓ —— 直接返回 {@code null} 让调用方回落 ✓
     */
    private static java.lang.reflect.Field healthFieldFor(LivingEntity target) {
        return null;
    }

    /** 旧实现保留（已不再被调用）：按值/写入探测血字段 */
    private static java.lang.reflect.Field healthFieldForLegacy(LivingEntity target) {
        Class<?> cls = target.getClass();
        java.lang.reflect.Field cached = FIELD_CACHE.get(cls);
        if (cached != null) {
            return cached;
        }
        java.lang.reflect.Field found = detectHealthField(target);
        if (found == null) {
            found = healthField();   // ⚠ 兜底：⭐ 用类级那个 ✓
        }
        if (found != null) {
            FIELD_CACHE.put(cls, found);
        }
        return found;
    }

    /**
     * ⭐⭐ **逆向改血**：⭐ 直写字段 ＋ ⭐ 强制同步 ✓。
     *
     * @return ⭐ true ＝ 真的**绕过 `setHealth`** 写进去了 ✓ ⭐ false ＝ 退回 `setHealth` ✓
     */
    public static boolean rawSetHealth(LivingEntity target, float value) {
        boolean ok = rawSetHealthInner(target, value);
        // TNL1401 诊断：写完读回不一致 ⇒ 打一条 INFO 把每条通道的值都列出来
        try {
            float back = rawHealth(target);
            if (Math.abs(back - value) > 0.05F) {
                long nowMs = System.currentTimeMillis();
                Long last = TNL_LAST_WRITE_FAIL.get(target.getUUID());
                if (last == null || nowMs - last > 1000L) {
                    TNL_LAST_WRITE_FAIL.put(target.getUUID(), nowMs);
                    java.lang.reflect.Field f = healthFieldFor(target);
                    float fieldVal = Float.NaN;
                    if (f != null) {
                        try {
                            fieldVal = f.getFloat(target);
                        } catch (Throwable ignored) {
                        }
                    }
                    Pending pd = PENDING.get(target.getUUID());
                    TinkersNewlife.LOGGER.info(
                            "[真伤·写不进] 目标={}（{}）写入={} 读回={} getHealth={} 字段={}:{} ok={} 锁={} 抑制同步={}",
                            target.getName().getString(),
                            net.minecraft.world.entity.EntityType.getKey(target.getType()),
                            String.format(java.util.Locale.ROOT, "%.2f", value),
                            String.format(java.util.Locale.ROOT, "%.2f", back),
                            String.format(java.util.Locale.ROOT, "%.2f", target.getHealth()),
                            f == null ? "null" : f.getName(),
                            String.format(java.util.Locale.ROOT, "%.2f", fieldVal),
                            ok,
                            pd == null ? "none" : String.format(java.util.Locale.ROOT, "%.2f", pd.value()),
                            SUPPRESS_CLIENT_SYNC);
                }
            }
        } catch (Throwable ignored) {
        }
        // ⭐⭐⭐⭐ §1231 **每次改血后都把血量同步给客户端** ✗✗（⭐ 关键 ✓）
        //   ⚠ 用户口径 ✓：「**我想让我两个包的两种伤害显示都兼容**」✓
        //   ⭐ 两包各有一个显示 mod ✗ ⭐ 而 ⭐ **两个都是靠"客户端血量变化"** ✓：
        //     ⭐ 测试包 ⭐ `[伤害引擎]` ✗ ⭐ `ClientHealthMonitor`／⭐ `ClientHealthTracker`
        //       ＋ ⭐ `RenderLevelStageEvent` ✓（⭐ 服务端那份听 `LivingHurtEvent` ✓ 已补 ✓）
        //     ⭐ NL 包 ⭐ `damagenumbers` ✗ ⭐ `MixinLivingEntity#tick` ⭐ **每 tick 比 `previousHealth`** ✓
        //   ⚠ 而我们"逆向改血"是 ⭐ **直写字段** ✗ ⭐ 客户端**根本收不到** ✓ ⇒ ⭐ **数字看不见** ✓
        //   ⇒ ⭐ 所以 ⭐ 每次写完都 ⭐ **显式同步** ✗：
        //     ⭐ `setHealth`（⭐ 走原版口径 ✓）＋ ⭐ **直写 `DATA_HEALTH_ID` 同步访问器** ✓
        //     ＋ ⭐ `hurtMarked = true` ✓。
        syncHealthToClient(target);
        return ok;
    }

    /** ⭐ §1231 **客户端血量同步** ✗ —— ⭐ 两个伤害显示 mod 都靠它 ✓ */
    private static volatile java.lang.reflect.Field DATA_HEALTH_FIELD = null;
    private static volatile boolean DATA_HEALTH_RESOLVED = false;

    /** TNL1404 取 `LivingEntity.DATA_HEALTH_ID` 同步访问器（1.20.1 的血量就存在它里面 ✓） */
    private static volatile Object DATA_HEALTH_ACCESSOR = null;

    /**
     * TNL1405 <b>同步"实体自己缓存的血量"</b> ✗
     *
     * <p>⚠ 为什么：我们直写 `DATA_HEALTH_ID` 绕过 `setHealth` ✓，
     * 而有的实体（⭐试验假人 `TargetDummyEntity`）在 `setHealth`/自身 tick 里维护
     * 一份 `lastHealth` 缓存 ✗；它 `tick` 里用 `lastHealth - getHealth()` 判定"**真伤**"：
     * ```java
     * if (lastTickActuallyDamaged + 1 == tickCount) {
     *     float trueDamage = lastHealth - getHealth();
     *     if (trueDamage > 0) onActuallyDamagedOrTrueDamageDetected(trueDamage, null); // ← 再弹一个红色值
     * }
     * ```
     * ⇒ ⭐把缓存一起改掉 ⇒ 它看不到"差" ⇒ ⭐不会再弹第二个值 ✓
     *
     * <p>⭐ 只动**模组自己的类**里的 float 字段 ✓（跳过 `net.minecraft.*` ✓），
     * 且只改「值 == 旧血量」的那些 ⇒ 不会乱改别的字段 ✓
     */
    /**
     * TNL1406 <b>目标是否自己覆写了 `setHealth`</b> ✗
     *
     * <p>⭐ 用来识别"有自己的血量语义"的实体 ✓（如试验假人：无限血量时只记伤害不扣血 ✓）。
     * ⭐ 只看**模组自己的类**（跳过 `net.minecraft.*` ✓），匹配 SRG/名字两种写法 ✓。
     */
    private static boolean tnl$hasSetHealthOverride(LivingEntity target) {
        for (Class<?> cls = target.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            if (cls.getName().startsWith("net.minecraft.")) {
                continue;
            }
            for (java.lang.reflect.Method m : cls.getDeclaredMethods()) {
                if (m.getParameterCount() != 1 || m.getParameterTypes()[0] != float.class) continue;
                String n = m.getName();
                if (n.equals("m_21153_") || n.equals("setHealth")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void mirrorHealthCaches(LivingEntity target, float before, float after) {
        if (Math.abs(before - after) < 0.01F) {
            return;
        }
        for (Class<?> cls = target.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            if (cls.getName().startsWith("net.minecraft.")) {
                continue;   // ⭐ 原版字段不动 ✓
            }
            for (java.lang.reflect.Field f : cls.getDeclaredFields()) {
                if (f.getType() != float.class) continue;
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    if (Math.abs(f.getFloat(target) - before) < 0.01F) {
                        f.setFloat(target, after);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static Object healthAccessor() {
        if (!DATA_HEALTH_RESOLVED) {
            DATA_HEALTH_RESOLVED = true;
            for (String n : new String[]{"f_20961_", "DATA_HEALTH_ID"}) {
                try {
                    java.lang.reflect.Field f = net.minecraft.world.entity.LivingEntity.class
                            .getDeclaredField(n);
                    f.setAccessible(true);
                    DATA_HEALTH_FIELD = f;
                    DATA_HEALTH_ACCESSOR = f.get(null);
                    break;
                } catch (Throwable ignored) {
                }
            }
        }
        if (DATA_HEALTH_ACCESSOR == null && DATA_HEALTH_FIELD != null) {
            try {
                DATA_HEALTH_ACCESSOR = DATA_HEALTH_FIELD.get(null);
            } catch (Throwable ignored) {
            }
        }
        return DATA_HEALTH_ACCESSOR;
    }

    private static void syncHealthToClient(LivingEntity target) {
        try {
            // ⭐⭐ §1232 **锁期间不同步** ✗（⭐ 否则每 tick 一变 ⇒ ⭐ 数字连弹 ✓）
            if (SUPPRESS_CLIENT_SYNC) {
                return;
            }
            if (target == null || !(target.level() instanceof net.minecraft.server.level.ServerLevel)) {
                return;
            }
            float value = rawHealth(target);
            // ⭐ ① 走原版 `setHealth` ✓（⭐ 普通实体：同时更新字段（＋ 可能的同步数据）✓）
            try {
                if (Math.abs(target.getHealth() - value) > 1.0E-4F) {
                    target.setHealth(value);
                }
            } catch (Throwable ignored) {
            }
            // ⭐ ② **直写同步访问器 `DATA_HEALTH_ID`** ✗ ⇒ ⭐ 客户端**一定收到** ✓
            Object acc = healthAccessor();
            if (acc != null) {
                if (acc != null) {
                    float max = Math.max(1.0F, target.getMaxHealth());
                    float clamped = net.minecraft.util.Mth.clamp(value, 0.0F, max);
                    target.getEntityData().set(
                            (net.minecraft.network.syncher.EntityDataAccessor) acc, clamped);
                }
            }
            // ⭐ ③ 标脏 ✓
            target.hurtMarked = true;
        } catch (Throwable ignored) {
            // ⭐ 同步失败不影响服务端血量 ✓
        }
    }

    private static boolean rawSetHealthInner(LivingEntity target, float value) {
        if (target == null || target.level().isClientSide) {
            return false;
        }
        // TNL1407 "亚波伦额外血量通道"/"暴力改血"两条针对逻辑已整段移除 ✓
        // ⭐⭐⭐⭐ §1221 **循环改血**（⭐ 用户口径 ✓ 2026-10-10：
        //   「**这个循环改血的逻辑不错，可以加到我真伤兜底链条里面**」✓）
        //   ⭐ 位置 ✗：⭐ 排在 ⭐ "已知 setter／⭐ 访问器"**之后** ✗ ⭐ "影子血"**之前** ✓
        //   ⭐ 即 ⭐ **真血写不进去时，先暴力找一个能改血的通道** ✗ ⭐ 实在没有才自行记账 ✓。
        // ⭐⭐⭐⭐⚠⚠ §1222 **影子血已回退** ✗✗（⭐ 用户实测 2026-10-10：「**不掉血了？？？**」✓）
        //   ⚠ 根因：⭐ 影子血让 `rawSetHealth` **直接 `return true`** ✗
        //     ⇒ ⭐ **真身那一笔再也没写** ✓ ⇒ ⭐ 它真身的血**不再下降** ✓ ✓（⭐ 我把它搞坏了 ✓）
        //   ⚠ 而且 ⭐ 我们的 `LivingEntityGetHealthMixin` **对使徒无效** ✗：
        //     ⭐ 它自己 **覆写了 `getHealth()`**（⭐ `ApollyonMixin.m_21223_` ✓）
        //     ⇒ ⭐ 虚分派**不会走父类那个注入点** ✓ ⇒ ⭐ 影子血**连界面都影响不到** ✓ ✓
        //   ⇒ ⭐ 结论：⭐ **绝不能"记账代替真写"** ✗ ⭐ 只能**想办法真写** ✓。
        // ⭐⭐ §1201 **用"这个实体"的字段**（⭐ 按值反查过 ✓ 不是按名字猜的 ✓）
        java.lang.reflect.Field f = healthFieldFor(target);
        // ⭐ TNL1404 1.20.1：血量 = `DATA_HEALTH_ID` 同步数据 ✗
        //   ⚠ 不能直接调 `setHealth(value)` ✗ —— 有的实体**覆写了 `setHealth`** ✓
        //     ⭐例如试验假人 `TargetDummyEntity#m_21153_`：会把"我们改血"当成**一次新的真伤** ✗
        //     ⇒ ⭐它自己先记一次（`hurt`）＋我们把血一写又记一次 ⇒ **两段伤害** ✓
        //   ⇒ ⭐正解：**直接写同步数据** ✗（只改血，不惊动任何 `setHealth` 覆写 ✓）
        //     ⭐`getHealth()` 读的就是它 ✓ ⭐客户端也会同步 ✓ ⭐视觉上就掉一次 ✓
        if (f == null) {
            Object acc = healthAccessor();
            if (acc != null) {
                try {
                    float before = target.getHealth();
                    float clamped = net.minecraft.util.Mth.clamp(value, 0.0F,
                            Math.max(1.0F, target.getMaxHealth()));
                    target.getEntityData().set(
                            (net.minecraft.network.syncher.EntityDataAccessor) acc, clamped);
                    // ⭐ TNL1405 直写同步数据不经过 `setHealth` ✗
                    //   ⇒ ⭐有些实体会**自己缓存一份血量**（⭐试验假人的 `lastHealth` ✓）
                    //     ⭐它的 `tick` 用 `lastHealth - getHealth()` 当"真伤"检测 ✗
                    //     ⇒ ⭐我们不更新缓存 ⇒ 它下一 tick 以为挨了一记真伤 ⇒ **再弹一个红色数值** ✓
                    //   ⇒ ⭐写完之后，把"模组自己声明、值还等于旧血量"的 float 字段一起改掉 ✓
                    mirrorHealthCaches(target, before, clamped);
                    target.hurtMarked = true;
                    return true;
                } catch (Throwable ignored) {
                }
            }
            // 拿不到同步访问器 ⇒ 只能退回 setHealth ✓（可能会被覆写方重复记账，但至少能改血 ✓）
            try {
                target.setHealth(value);
                target.hurtMarked = true;
            } catch (Throwable ignored) {
            }
            return false;
        }
        boolean raw = false;
        if (f != null) {
            try {
                f.setFloat(target, value);
                raw = true;
            } catch (Throwable ignored) {
                raw = false;
            }
        }
        // ⭐⭐⚠⚠ **两个通道都要写** ✗✗（⭐ 探针实证 ✓ 见 {@link #rawHealth} 的说明 ✓）：
        //   ⭐ 有些 Boss 的血 ⭐ **不在字段里** ✗
        //   ⇒ ⭐ 只写字段 ⇒ ⭐ **写进了一个"没人读的地方"** ✗ ⭐ 等于没改 ✓
        //   ⇒ ⭐ 所以 ⭐ **再走一次 `setHealth`** ✗ ⭐ 两条路都覆盖 ✓ ✓
        try {
            // ⚠⚠ §1232 **锁期间不标脏 ✗ 也不调 `setHealth`** ✓（⭐ 用户实测：
            //   ⭐「**怎么打一次又弹了3段伤害？**」✓ ⭐ 就是这里每 tick 同步造成的 ✓）
            // TNL1402：字段没写成功（或压根没字段）⇒ **必须**回落 setHealth ✓
            //   ⚠ 不能再被 SUPPRESS_CLIENT_SYNC 挡住门 ✗ —— 那会让真伤彻底不掉血 ✓
            boolean fieldWorked = f != null && Math.abs(rawHealth(target) - value) <= 0.05F;
            if (Math.abs(target.getHealth() - value) > 1.0E-4F && (!fieldWorked || !SUPPRESS_CLIENT_SYNC)) {
                target.setHealth(value);
            }
        } catch (Throwable ignored) {
        }
        // ⚠⚠ **必须标脏** ✗ —— ⭐ 否则客户端血条不同步 ✓
        try {
            target.hurtMarked = true;
        } catch (Throwable ignored) {
        }
        // ⭐⭐⭐⭐ §1237 **写完复查 ⇒ 没落进去就"循环改血"兜底** ✗✗
        //   （⭐ 用户口径 ✓ 2026-10-10：「**使徒免疫窗怎么又破不了了，不是说直接改血吗**」✓）
        //   ⚠ 背景 ✗：⭐ 免疫窗（⭐ `moddedInvul` ✓）会让 ⭐ `hurt()` **整个无效** ✓
        //     ⇒ ⭐ 只能靠 ⭐ **差额改血** ✓ ⚠ 而 ⭐ "改血到底落没落进去" ✗ ⭐ 光看代码看不出来 ✓
        //   ⇒ ⭐ 所以 ⭐ **写完立刻复查** ✗：
        //     ⭐ 落进去了 ⇒ ⭐ 收工 ✓（⭐ 普通目标／⭐ 多数使徒 ✓）；
        //     ⭐ 没落进去 ⇒ ⭐ **自动去找"能改血的那个通道"** ✓
        //       （⭐ 就是用户点名要加进兜底链的 ⭐ §1221 循环改血 ✓
        //        ⭐ 它靠 ⭐ **"写了看 `getHealth()` 变不变"** 认通道 ✗
        //        ⭐ 完全不看名字／⭐ 类型 ✓ ⭐ 混淆与改名都拦不住 ✓）。
        return raw;
    }

    /**
     * ⭐⭐ <b>逆向改血：⭐ 扣血（⭐ 用户口径的"真伤穿透"用这个 ✓）</b>
     * —— ⭐ 目标血量 ⭐ 直接减 {@code amount} ✗ ⭐ 夹到 ⭐ `[0, maxHealth]` ✓。
     *
     * @return ⭐ 实际扣掉的血量 ✓（⭐ 已经 ≤ 0 的目标返回 0 ✓）
     */
    public static float reverseDrain(LivingEntity target, float amount) {
        if (target == null || amount <= 0.0F) {
            return 0.0F;
        }
        float now = rawHealth(target);
        if (now <= 0.0F) {
            return 0.0F;
        }
        float capped = Math.min(amount, target.getMaxHealth());
        float after = Math.max(0.0F, now - capped);
        float dealt = now - after;
        rawSetHealth(target, after);
        return dealt;
    }

    // ============================================================
    //  对外入口
    // ============================================================

    /**
     * 万能穿透：{@code attacker} 对 {@code target} 造成 {@code damage} 点"真伤"。
     *
     * @param attacker 攻击者（可为 null：无主穿透，来源归属会丢失，但能穿"有攻击者即免疫"的目标）
     * @param target   受击者
     * @param damage   期望造成的伤害（会被逐段结算 + 差额补齐）
     */
    public static float apply(@Nullable LivingEntity attacker, LivingEntity target, float damage) {
        // ⭐⭐⭐⭐ §1231 **"合成事件"的回调 ⇒ 直接忽略** ✗✗（⭐ 防递归 ＋ ⭐ 防翻倍 ✓）
        //   ⚠ 我们在下面会 ⭐ **手动 post 一个 `LivingHurtEvent`** ✗（⭐ 用户口径：
        //     「**记得改血后补上受击事件和效果**」✓）
        //   ⚠ 而 ⭐ `TianNiHuoPierceHandler`／⭐ `ExPierceHandler` 这些
        //     ⭐ **自己就是 `LivingHurtEvent` 处理器** ✗ ⭐ 收到后会**再调 `apply`** ✓
        //   ⇒ ⭐ 没有这道门 ⭐ **无限递归 ＋ 伤害翻倍** ✓ ✓。
        if (SYNTHETIC_HURT) {
            return 0.0F;
        }
        if (target == null || damage <= 0.0F) return 0.0F;
        if (target.level().isClientSide || target.isRemoved()) return 0.0F;
        // ⭐ 同心戒：互为同伴的两名玩家互相免疫术式与领域效果 —— 穿透（真伤）也不例外。
        //    必须挡在这里：本方法是"差额直接 setHealth 补齐"的，事件层（LivingAttackEvent）拦不住它 ✗
        if (com.mofengbaizhi.tinkersnewlife.content.curse.TwinRingLink.arePaired(attacker, target)) return 0.0F;
        // ⭐ 咒力伤害记账（死亡信息统一成"被诅咒致死"）：穿透走的是"差额 setHealth 补齐"，
        //    死亡可能不在 hurt() 里发生，但标记在 hurt() 之前打上就够 ——
        //    见 CurseDeath 的说明（天逆鉾/游云/领域/术式的穿透都路过这里 ✓）
        com.mofengbaizhi.tinkersnewlife.content.curse.CurseDeath.mark(target);
        // ⭐ 击杀归属：穿透是"分块连打 → 带攻击者的源打不动就换**无主源** → 差额 setHealth 补齐"，
        //    收尾那一下的伤害源可能根本没有攻击者 ✗（死亡事件只认 getSource().getEntity()）→
        //    在这里把"这一击是谁打的"记下来，死亡时按最后一击回溯归属 ✓（见 KillAttribution）
        com.mofengbaizhi.tinkersnewlife.content.curse.KillAttribution.remember(target, attacker);
        // ⭐⭐ 用户实测（命灯指轮 + 带穿透的法杖打怪**什么都不掉**）：
        //   命灯指轮的"慈悲"会把致死伤害截到留 1 血，但**特意豁免了穿透** ⇒ 于是这一击成了收尾一击 ✓，
        //   而穿透的收尾段常常是**无主源**（本方法第 ③ 条分支甚至直接 setHealth + die()，
        //   **根本不经过 LivingHurtEvent** ✗）⇒ 目标的 lastHurtByPlayer 永远是空的 ✗
        //   ⇒ 战利品表里 `killed_by_player` 的条件不成立 ⇒ 一件都不掉 ✗✗
        //   ⇒ 这里**直接把归属写进目标本身**（不依赖任何事件），三条分支全覆盖 ✓。
        if (attacker instanceof net.minecraft.world.entity.player.Player pierceOwner) {
            target.setLastHurtByPlayer(pierceOwner);
            target.setLastHurtByMob(pierceOwner);
        }

        // ⓪ 绝对防御穿透：把"根本不看伤害标签、只看攻击者属性"的免疫（潘多拉之咒·现实压制）
        //    临时失效 —— 它是在 LivingAttackEvent 里直接取消打击的，标签和事件层顶开都够不着。
        //    必须包住整个打击过程，且无论从哪个分支返回都要摘掉临时修饰符。
        boolean transcend = AbsoluteDefense.begin(attacker);
        AbsoluteDefense.noteSunblockTarget(target);
        float hpBefore = target.getHealth();
        // ⭐⭐ §1231 **穿透前记一次"真身血"** ✗ —— ⭐ 用来算"这一击实际打掉多少" ✓
        float rawBefore = rawHealth(target);
        try {
            applyInner(attacker, target, damage);
        } finally {
            if (transcend) AbsoluteDefense.end(attacker);
        }

        // ⭐ 穿透"告知"（见 {@link PierceAware}）：穿透是**分块连打 + 差额直补**，
        //   目标常常**根本不经过 hurt()** ✗ ⇒ 想在"这一击"上做反馈/记账的实体，
        //   例如墨默（一击必杀判定用 lastDamageTaken、受击语音在 hurt() 里播 ✗）会静音、记错账 ✗。
        //   只在**真的掉血或已死**时通知 ✓（免疫时不该有受击音效/记账 ✗）。
        if (target instanceof PierceAware aware) {
            try {
                if (target.getHealth() < hpBefore || target.isDeadOrDying()) {
                    aware.onTruePierce(damage, source(target.level(), attacker));
                }
            } catch (Throwable ignored) {
                // 告知失败不影响伤害结算 ✓
            }
        }
        // ⭐⭐ §1231 **返回"这一击实际打掉的血"** ✗（⭐ 用户口径 ✓：
        //   「**记得改血后补上受击事件和效果并返回对应伤害**」✓）
        //   ⚠ 用 ⭐ `rawHealth` 的**前后差** ✗ ⭐ 而不是 ⭐ `want` ✓ ——
        //     ⭐ 被免疫/被拦时它能 ⭐ **如实反映"其实没打掉"** ✓ ✓。
        float tnlDealt = Math.max(0.0F, rawBefore - rawHealth(target));
        // TNL1400 诊断：真伤一点都没落地 ⇒ 打一条 INFO（每目标 1 秒最多一条）
        //   若这行**完全没出现** ⇒ 说明真伤引擎根本没被调用（不是这个 Boss 免疫的事）
        if (tnlDealt < 0.05F && !tnl$hasSetHealthOverride(target)) {
            try {
                long nowMs = System.currentTimeMillis();
                Long last = TNL_LAST_NO_LAND.get(target.getUUID());
                if (last == null || nowMs - last > 1000L) {
                    TNL_LAST_NO_LAND.put(target.getUUID(), nowMs);
                    TinkersNewlife.LOGGER.info(
                            "[真伤·未落地] 目标={}（{}）want={} 血前={} 血后={} 字段通道={}",
                            target.getName().getString(),
                            net.minecraft.world.entity.EntityType.getKey(target.getType()),
                            String.format(java.util.Locale.ROOT, "%.2f", damage),
                            String.format(java.util.Locale.ROOT, "%.2f", rawBefore),
                            String.format(java.util.Locale.ROOT, "%.2f", rawHealth(target)),
                            HEALTH_FIELD_STATE);
                }
            } catch (Throwable ignored) {
            }
        }
        return tnlDealt;
    }

    /**
     * 穿透"告知"接口：实现它的实体会在**每次穿透命中**收到
     * {@code (这一击的总伤害, 伤害源)} ✓ —— 用来补"因为不经过 {@code hurt()} 而拿不到的反馈/记账" ✓。
     */
    public interface PierceAware {
        void onTruePierce(float totalDamage, net.minecraft.world.damagesource.DamageSource source);
    }

    private static void applyInner(@Nullable LivingEntity attacker, LivingEntity target, float damage) {
        // ① 穿透是"真伤"：**不受命灯指轮的慈悲效果约束** —— 带穿透的近战/投射物可以照常杀死目标。
        //    命灯那边也有对应的早退（LifeLampRingHandler 见到真伤源直接放行），两端一致、与事件顺序无关。
        float want = damage;


        // ② 禁疗（否则再生会把伤害吃回去）+ 清掉本模组自己的"伤害限幅"效果（它会取消多段伤害）
        suppressRegen(target);
        clearOwnDamageLimit(target);
        // ⭐⭐ §1197 **删掉"破使徒的免疫窗"那一步** ✗（⭐ 用户口径 ✓ 2026-10-10：
        //   「**moddedInvul清这个不就是针对了吗**」✓ —— ⭐ 用户说得对 ✓）
        //   ⚠ 原来这里调 ⭐ `GoetyBridge.clearApostleInvul(target)` ✗
        //   ⭐ 那个方法里有 ⭐ `isGoetyApostle` 门 ✗ ⭐ **只对诡厄的使徒生效** ✓
        //   ⇒ ⭐ 那就是"**给某个 Boss 写特判**" ✓ ⭐ 与"一视同仁"冲突 ✓
        //   ⚠ 而 ⭐ 反编译也证明 ⭐ **没有通用的破窗办法** ✗
        //   （⭐ `BYPASSES_INVULNERABILITY` 只管 `ApostleDamageCap` ✗ ⭐ 管不了 `moddedInvul` ✓）
        //   ⇒ ⭐ **"破窗"与"不针对"只能选一个** ✗ ⭐ 按用户口径 ⭐ **选不针对** ✓
        //   ⇒ ⭐ 所以这里**什么都不做** ✗ ⭐ 通用流程只保证"**差额一律逆向补掉**" ✓ ✓。

        DamageSource withAttacker = source(target.level(), attacker);
        DamageSource anonymous = source(target.level(), null);

        // ③ ⭐⭐ §1230 **下界亚波伦的特判已撤掉** ✗✗（⭐ 用户口径 ✓ 2026-10-10：
        //   「**算了不针对下界亚波伦了，破不了就破不了吧**」✓）
        //   ⚠ 原来这里 ⭐ `if (isNetherApollyon(target)) { directDamage(...); return; }` ✓
        //   ⇒ ⭐ 那是 ⭐ **给一个 Boss 写特判** ✗ ⭐ 与"一视同仁"冲突 ✓
        //   ⭐ 现在 ⭐ **它和别的目标走完全一样的路** ✓：⭐ `hurt` ＋ ⭐ 差额逆向补 ✓
        //   ⚠ 它能挡住 ⇒ ⭐ **就让它挡住** ✓ ⭐ 不再为它单开一条通道 ✓ ✓。

        // ④ ⭐⭐ §1189 **删掉分段**（⭐ 用户口径 ✓ 2026-10-10：「**删掉分段，然后调用受击效果和伤害音效**」✓）
        //   ⚠ 原来是 ⭐ `chunkedHurt(带攻击者源)` ✗ ⭐ 打不动再 ⭐ `chunkedHurt(无主源)` ✓
        //   —— ⭐ 那是"分 19 点一段连打"✗ ⭐ 目的是绕开单次限伤／免疫窗 ✓
        //   ⚠ 但它有代价 ✗：⭐ 每一段都**触发一次别人的受击事件** ✓
        //   （⭐ 反伤／吸血被触发 N 次 ✓ ⭐ 别的模组的增幅器逐段跑 ✓ —— `DamagePipeline` 只挡得住**自家的** ✓）
        //   ⇒ ⭐ 既然 ⑤ 已经能 ⭐ **逆向改血**（⭐ 直写血量真身 ✓ 谁也拦不住 ✓）
        //   ⇒ ⭐ **分段就没有必要了** ✗ ⭐ 直接全额逆向改血 ✓ 更干净、更可控 ✓。
        //   ⚠ 代价（⭐ 如实 ✓）：⭐ 伤害**不再触发** `LivingHurtEvent` 等事件 ✓
        //   ⇒ ⭐ 所以"受击红闪"与"伤害音效"由下面 ④.5 ⭐ **手动补上** ✓。
        // ⭐⭐ §1191 **先试一次正常的 `hurt`，看它有没有真的突破保护** ✗
        //   （⭐ 用户口径 ✓ 2026-10-10：「**在此前先做hurt判断，如果伤害额未突破保护就改为逆向改血，
        //     直接改血可能会导致一些问题**」✓）
        //
        //   ⚠ 为什么不能一上来就逆向改血 ✗（⭐ 用户的顾虑是对的 ✓）：
        //   ⭐ 直写字段会 ⭐ **完全绕过伤害管线** ✗ ⇒
        //   ⭐ 反伤／吸血／受击音效／受击动画／⭐ **Boss 阶段推进** 全都不触发 ✓
        //   ⇒ ⭐ 正常情况（⭐ 打得动 ✓）应该 ⭐ **走正常 `hurt`** ✓ ⭐ 只有被拦时才逆向 ✓。
        //
        //   ⭐ 流程 ✓：
        //   ① ⭐ 记下 `hurt` 前的血量 ✗；
        //   ② ⭐ 先 ⭐ `hurt(带攻击者源)` ✗ ⭐ 如果一点没打动 ⭐ 再 ⭐ `hurt(无主源)` ✓
        //      （⭐ 后者的用途照旧：⭐ 有些目标"有攻击者才免疫" ✓）；
        //   ③ ⭐ 读差值 ⇒ ⭐ **实际打掉了多少** ✗；
        //   ④ ⭐ 没打满 ⇒ ⭐ **只补差额**，且 ⭐ **补之前先手动补受击反馈** ✗
        //      （⭐ 因为被拦时 `hurt` 往往连音效都没播 ✓）。
        // ⭐⭐ §1197 **去掉"针对"** ✗（⭐ 用户口径 ✓ 2026-10-10：
        //   「**moddedInvul清这个不就是针对了吗**」✓）
        //   ⚠ 用户说得对 ✗ —— `clearApostleInvul` 里有 `isGoetyApostle` 门 ✗
        //   ⭐ **只对诡厄的使徒生效** ✓ ⇒ ⭐ 那就是"针对某个 Boss 写特判" ✓
        //   ⚠ 而 ⭐ 反编译也证明 ⭐ **没有通用的"破窗"办法** ✗：
        //   ⭐ `BYPASSES_INVULNERABILITY` 标签只管 `ApostleDamageCap` ✗
        //   ⭐ **管不了 `moddedInvul`** ✓ ⇒ ⭐ "破窗"与"不针对"**只能选一个** ✓
        //   ⇒ ⭐ 按用户口径 ⭐ **选不针对** ✗：⭐ **不跟它的免疫较劲** ✓
        //   ⇒ ⭐ 通用流程＝⭐ 调一次 `hurt`（⭐ 能进就进 ✓ 事件/音效/反伤白赚 ✓）
        //     ＋ ⭐ **差额一律逆向补掉** ✓ ⇒ ⭐ **血永远按 want 掉** ✓ ⭐ 对任何目标一视同仁 ✓ ✓。
        //   ⚠ 代价（⭐ 如实 ✓）：⭐ 使徒这类拦得住 `hurt` 的目标 ✗
        //   ⭐ **它的"免疫窗"确实没被破** ✗ ⭐ 只是血被我们从侧面扣掉了 ✓ ✓
        //     —— ⭐ 这正是用户要的语义 ✓（⭐ "无视免疫窗直接改血" ✓）。
        float startHp = rawHealth(target);
        // ⭐⭐⭐⭐ §1238 **记住"这一发开始时它报的血"** ✗✗
        //   （⭐ 用户口径 ✓ 2026-10-10：「**一定要无主源才能破吗**」✓ ⇒ ⭐ **答案是"不一定"** ✓）
        //   ⚠ 发现 ✗：⭐ §1234 的 `quiet` 判据是 ⭐ `dealt ≤ 0.01` ✓
        //     ⭐ 而 ⭐ **免疫窗那一击** ⭐ `hurt` 被拦 ⇒ ⭐ `dealt ＝ 0` ✓
        //     ⇒ ⭐ `quiet ＝ true` ✗ ⇒ ⭐ **客户端同步被掐掉** ✓
        //     ⇒ ⭐ **服务端血其实掉了 ✗ 客户端收不到** ✓ ⇒ ⭐ 看起来"**免疫窗破不了**" ✓ ✓
        //   ⇒ ⭐ 正确判据 ✗：⭐ **"它的血会不会自己弹回去"** ✓
        //     ⭐ 会弹回去（⭐ 假人每发都回满 ✓ 日志 `startHp=666` 每发都一样 ✓）
        //       ⇒ ⭐ 我们那笔写了也白搭 ⇒ ⭐ **别打扰客户端** ✓；
        //     ⭐ 不回弹（⭐ 免疫窗的使徒／⭐ 普通怪 ✓）⇒ ⭐ **同步出去** ✓ ⇒ ⭐ 玩家看得见掉血 ✓ ✓。
        final Float prevStartHp = LAST_START_HP.put(target.getUUID(), startHp);
        if (LAST_START_HP.size() > 512) {
            LAST_START_HP.clear();
        }
        float dealt = 0.0F;
        try {
            target.invulnerableTime = 0;
            // ⚠⚠ **绝对不要在这里 `markIntent`** ✗✗（⭐ 用户实测 2026-10-10 ✓
            //   「**好像没成功，连使徒限伤都破不了了**」✓）
            //   ⭐ `markIntent` ＋ ⭐ 事件层那个 `force(...)` 的用途是
            //   ⭐ **"把被别人改小的数值强行放回 `want`"** ✗ —— ⭐ 那是给**分块穿透**设计的 ✓
            //   ⚠ 现在流程换成了"先 `hurt` 再看有没有突破" ✗
            //   ⇒ ⭐ `markIntent` 会 ⭐ **替我们把保护顶开** ✗ ⇒ ⭐ `hurt` 打满 ✗
            //     ⇒ ⭐ `dealt ＝ want` ⇒ ⭐ `shortfall ＝ 0` ⇒ ⭐ **直接 return ✗ 根本不逆向补** ✓
            //     ⚠ 而那个"打满"如果其实**没真落地**（⭐ 被别人回血／取消 ✓）✗
            //     ⇒ ⭐ 我们就 ⭐ **白白不补** ✓ ✓ —— ⭐ 正是用户遇到的现象 ✓。
            //   ⇒ ⭐ 删掉它 ✗ ⇒ ⭐ `hurt` 才会体现 ⭐ **保护之后的真实结果** ✓
            //     ⇒ ⭐ `dealt` 才准 ✓ ⭐ 差额才会真的被逆向补上 ✓。
            // ⭐⭐⭐⭐ §1235 **只打一次 `hurt`** ✗✗（⭐ 用户口径 ✓ 2026-10-10：
            //   「**哪来的无主源，我不是说逆向改血吗**」✓）
            //   ⭐ 用户说得对 ✗ —— ⭐ 本引擎的设计是 ⭐
            //     ⭐ **一次 `hurt`**（⭐ 拿事件／⭐ 音效／⭐ 反伤 ✓）
            //     ＋ ⭐ **差额一律"逆向改血"** ✓ ✓
            //   ⚠ ⭐ **不该再有"无主源补第二下"** ✗：
            //     ⭐ 那会让"一次挥击"变成 ⭐ **两次命中** ✓
            //     ⇒ ⭐ 假人**记两次** ✗ ⭐ 显示也就**多段** ✓（⭐ 用户实测 ✓）。
            boolean hurtAccepted = target.hurt(withAttacker, want);
            dealt = Math.max(0.0F, startHp - rawHealth(target));
            // ⭐ TNL1406 目标自己覆写了 `setHealth`（如试验假人 `TargetDummyEntity`）✗
            //   ⇒ ⭐它有自己的"记账/无限血量"语义 ✓（假人无限血量时**故意不扣血** ✓ 只弹数字 ✓）
            //   ⇒ ⭐我们已经让 `hurt` 走完了 ⇒ **不要再改它的血** ✓
            //     否则：① 会把无限血量假人打死 ✗ ② 它下一 tick 还会把差额当"真伤"再弹红字 ✗
            if (hurtAccepted && tnl$hasSetHealthOverride(target)) {
                return;
            }
            // ⚠⚠ §1235 **不再有"无主源重打"** ✗（⭐ 见上面 ⭐ 用户口径 ✓）
        } catch (Throwable t) {
            // ⚠ 伤害调用被外部异常打断（⭐ §1118l 那类 ✓）⇒ ⭐ 当作"没打动" ✓ 后面逆向补 ✓
            TinkersNewlife.LOGGER.debug("[真伤] hurt 阶段被外部异常打断（转逆向改血）：{}", t.toString());
        }
        // ⭐⭐ §1197 **不再"打完再清免疫窗"** ✗（⭐ 那是"针对" ✓ ⭐ 见上面那段说明 ✓）
        //   ⭐ 通用流程只做一件事 ✗：⭐ **差额一律逆向补掉** ✓ ⇒ ⭐ 血永远按 want 掉 ✓ ✓
        // ⚠⚠ 探针用 **INFO** ✗ 不用 `debug` ✗ ——
        //   （⭐ 用户实测 2026-10-10 ✓：⭐ 我原来写的是 `debug` ＋ `isDebugEnabled` ✓
        //    ⇒ ⭐ **默认级别根本不输出** ✗ ⇒ ⭐ "0 行"被我**误读成"没被调用"** ✓ ⭐ 差点查偏 ✓）
        TinkersNewlife.LOGGER.debug("[真伤] want={} startHp={} dealt={} 字段可用={}",
                String.format(java.util.Locale.ROOT, "%.2f", want),
                String.format(java.util.Locale.ROOT, "%.2f", startHp),
                String.format(java.util.Locale.ROOT, "%.2f", dealt),
                HEALTH_FIELD_STATE == 1);


        // ⑤ 差额直补 —— ⭐⭐ §1188 改成 ⭐ **逆向改血**（⭐ 用户口径 ✓ 2026-10-10 ✓）✗：
        //   ⚠ 原来用 `getHealth()` ／ `setHealth()` ✗ —— ⭐ 两个都**可以被别人覆写** ✓
        //   （⭐ "锁血"就是这么做的：⭐ 覆写 `setHealth` ✗ 或 ⭐ 在事件里把数值改回来 ✓）
        //   ⭐ 现在改成 ⭐ **反射直写血量真身字段** ✗ ＋ ⭐ 标脏同步 ✓
        //   ⇒ ⭐ 它的保护逻辑 ⭐ **读不到"有人在改血"** ✓ ✓（⭐ 这就是"逆向改血" ✓）。
        float shortfall = want - Math.max(dealt, 0.0F);
        // ⭐ 探针（⭐ INFO ✓ 一定可见 ✓）：⭐ 决定"补不补、补多少" ✗
        TinkersNewlife.LOGGER.debug("[真伤] shortfall={} 目标存活={} 已移除={}",
                String.format(java.util.Locale.ROOT, "%.2f", shortfall),
                target.isAlive(), target.isRemoved());
        if (shortfall <= 0.01F) return;
        if (!target.isAlive() || target.isRemoved()) return;
        // ⭐⭐ §1191 走**逆向改血**之前 ⭐ **先手动补受击反馈** ✗
        //   （⭐ 用户口径 ✓「**然后调用受击效果和伤害音效**」✓）
        //   ⚠ 为什么补在这里 ✗：⭐ 能走到这说明 ⭐ **`hurt` 没打满** ✗
        //   ⇒ ⭐ 被保护拦住时 ⭐ `hurt` 往往**连受击音效都没播** ✓
        //   ⇒ ⭐ 这一下"补的伤害"如果不补反馈 ⭐ 看上去就像**凭空掉血** ✓。
        //   ⚠ 反之 ⭐ 如果 `hurt` 打满了 ✗ ⭐ 上面就 `return` 了 ✓ ⭐ **不会多播一次** ✓（⭐ 不会重复音效 ✓）。
        playHurtFeedback(target, withAttacker, shortfall);
        // ⭐ 逆向**读**血 ✗（⭐ 不用 `getHealth()` ✓ ⭐ 它也可能被覆写 ✓）
        float hp = rawHealth(target) - shortfall;
        // ⭐⭐⭐⭐ §1234 **"接受了伤害但血没动"的目标 ⇒ 改血不要打扰客户端** ✗✗
        //   （⭐ 用户实测 ✓ 2026-10-10：「**打假人还有两段伤害**」✓）
        //   ⚠ 两段的来源 ✗：⭐ 假人**自己会显示一次**（⭐ 它记录伤害 ✓）
        //     ＋ ⭐ 我们"改血"**又同步了一次**给客户端 ✓ ⇒ ⭐ **两段** ✓ ✓
        //   ⇒ ⭐ 这种目标 ⭐ 改血**只压服务端** ✗ ⭐ **不同步** ✓ ⇒ ⭐ **只剩它自己那一段** ✓ ✓。
        // ⚠⚠ §1238 **判据升级** ✗：⭐ 原来只看 `dealt ≤ 0.01` ✓
        //   ⚠ 那会把 ⭐ **免疫窗那一击**（⭐ `dealt ＝ 0` ✓）也静默掉 ✓
        //   ⇒ ⭐ 现在**再看"它的血会不会自己弹回去"** ✗：
        //     ⭐ 上一发开始时是 X ✗ ⭐ 这一发开始时**还是 X** ✓ ⇒ ⭐ 会回弹（⭐ 假人 ✓）⇒ ⭐ 静默 ✓；
        //     ⭐ 变了 ⇒ ⭐ 不回弹 ⇒ ⭐ **正常同步** ✓ ✓（⭐ 免疫窗的使徒就属于这一类 ✓）。
        boolean quiet = dealt <= 0.01F && prevStartHp != null
                && Math.abs(prevStartHp - startHp) < 0.01F;
        if (quiet) {
            SUPPRESS_CLIENT_SYNC = true;
        }
        try {
        if (hp <= 0.0F) {
            // ⭐⭐⚠⚠ **补击杀归属**（用户实测 ✓ 2026-10-09：
            //   「**很奇怪，不知道为什么杀了两遍末影龙都没有判定是我杀的**」✓）
            //   ⚠ 根因：⭐ `KillAttribution` 补 {@code lastHurtByPlayer} 的动作**只在
            //   `LivingHurtEvent` 里做** ✗ ⭐ 而**这一条收尾路径**是
            //   ⭐ **`setHealth(0)` ＋ `die()`** ✗ ⇒ ⭐ **根本不经过那个事件** ✓
            //   ⇒ ⭐ 记忆（`remember` ✓）写了却**从没被应用** ✓
            //   ⇒ ⭐ 末影龙死时 ⭐ `EnderDragon#getKillCredit()`（＝ `lastHurtByPlayer` ✓）**是 null**
            //     ⇒ ⭐ `DragonFight` **不判定玩家击杀** ✗（⭐ 成就/掉落/经验都拿不到 ✓）
            //   ⇒ ⭐ 修法：⭐ 在 `die()` **之前**显式调 {@code KillAttribution.credit} ✗
            //     ⭐ 它会立刻把 {@code lastHurtByPlayer/Mob} 补到目标身上 ✓
            //     ⚠ 这样 `die()` 之后的一切死亡结算（⭐ 龙战 ✓ 战利品 `killed_by_player` ✓
            //       成就 `player_killed_entity` ✓）都能认出玩家 ✓。
            com.mofengbaizhi.tinkersnewlife.content.curse.KillAttribution.credit(target, attacker);
            // ⭐⭐ **逆向改血到 0**（⭐ §1188 ✓）—— ⭐ 先直写字段 ✓ ⭐ 再 `die()` 走死亡结算 ✓
            rawSetHealth(target, 0.0F);
            if (!target.isRemoved()) target.die(withAttacker);
        } else {
            // ⭐⭐ **逆向改血**：⭐ 直写血量真身字段 ✗ ⭐ **不走 `setHealth`** ✓（⭐ §1188 ✓）
            rawSetHealth(target, hp);
            // ⭐⭐⭐⭐ §1228 **累计伤害处决**（⭐ 非 mixin ✓ 用户口径 ✓「真没有不mixin的办法了吗」✓）
            //   ⚠ 动机 ✗：⭐ §1221／⭐ §1226 **穷举实证** —— ⭐ 这个 Boss 的血**任何反射通道都写不动** ✓
            //     （⭐ 字段 ✗ 同步访问器 ✗ EC ✗ 方法 ✗ 静态字段 ✗ 属性 ✗ ⭐ 且 ⭐ 非隐藏类 ✓）
            //   ⇒ ⭐ 那就 ⭐ **不再跟"它的血"较劲** ✗：
            //     ⭐ 记 ⭐ **"我们打了多少"** ✗ ⭐ 其中**没落地的部分**（⭐ `deficit` ✓）
            //     ⭐ 一旦 ⭐ **累计没落地的伤害 ≥ 它当前报的血** ✗
            //       ⇒ ⭐ **判定它挨够了 ⇒ 处决** ✓ ✓
            //   ⭐ 为什么这个规则**通用且安全** ✗：
            //     ⭐ 普通 Boss ⭐ `dealt ≈ want` ⇒ ⭐ `deficit ≈ 0` ⇒ ⭐ **永不累计** ✓ ✓
            //     ⭐ 只有 ⭐ "**伤害落不了地**"的目标（⭐ 免疫期／⭐ 锁血 ✓）才会积累 ✓
            //     ⇒ ⭐ 效果就是 ⭐ **"它的无敌能挡伤害，但挡不住死亡"** ✓ ✓。
            try {
                float deficit = CUMULATIVE_EXECUTE ? Math.max(0.0F, want - Math.max(dealt, 0.0F)) : 0.0F;
                if (deficit > 0.01F) {
                    float owed = DAMAGE_OWED.merge(target.getUUID(), deficit, Float::sum);
                    float cur = rawHealth(target);
                    if (owed >= cur) {
                        DAMAGE_OWED.remove(target.getUUID());
                        TinkersNewlife.LOGGER.debug(
                                "[真伤·累计处决] 累计未落地伤害 {} ≥ 当前报的血 {} ⇒ 处决 {} ✓",
                                String.format(java.util.Locale.ROOT, "%.2f", owed),
                                String.format(java.util.Locale.ROOT, "%.2f", cur),
                                target.getName().getString());
                        com.mofengbaizhi.tinkersnewlife.content.curse.KillAttribution.credit(target, attacker);
                        rawSetHealth(target, 0.0F);
                        if (!target.isRemoved()) {
                            target.die(withAttacker);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        } finally {
            if (quiet) {
                SUPPRESS_CLIENT_SYNC = false;
            }
        }
        // ⚠⚠ §1235 **"补一次无主源"也撤掉了** ✗（⭐ 用户口径 ✓：「**哪来的无主源**」✓）
        //   ⭐ 现在只有一条路 ✗：⭐ **一次 `hurt` ＋ ⭐ 差额"逆向改血"** ✓ ✓
        // 顺带：低血阶段的"受击全额回血"免伤（启示录使徒那类），若差额直补也吃不动 → 走处决兜底
    }

    // ============================================================
    //  内部实现
    // ============================================================

    /** 分块连打，返回实际造成的血量差（每段都登记意图供事件层顶开） */
    private static float chunkedHurt(LivingEntity target, DamageSource src, float want) {
        float startHp = target.getHealth();
        float remaining = want;
        int guard = 0;
        // ⭐⭐⚠⚠ **分段期间必须打上 `DamagePipeline` 的"嵌套"标记** ✗✗
        //   （⭐ 用户实测 2026-10-10 ✓：「**穿透处理分割伤害后，黑闪按单段计算增幅，
        //     导致总伤害远低于预期**」✓）
        //
        //   <h3>⚠ 为什么（⭐ 这是**系统性**问题 ✗ 不止黑闪一个 ✓）</h3>
        //   ⭐ 玩家那一击 ⭐ **已经在外层被我们的各种增幅器作用过一次** ✓
        //   ⭐ 而 ⭐ 分段只是为了绕开 Boss 的限伤／免疫窗 ✗ ⭐ **不该再放大一遍** ✓
        //   ⚠ 但 ⭐ 原来这里**没有开标记** ✗ ⇒ ⭐ 目录下这些自家增幅器
        //   ⭐ **在每一段里都各跑一次** ✓：
        //     `BlackFlashHandler`（⭐ `^2.5` ✓ 就是用户报的那个 ✓）、
        //     `ChildOfTheStarsHandler`、`CorruptionHandler`、`FormlessIceHandler`、
        //     `PyriumHandler`、`IronSpellsArcaneHandler`、`LightningManipulationTechnique`、
        //     `DragonStaffHandler`、`WizardArmorSetHandler`、`ModularStaffModifier`、
        //     `ExecutionDomain` ✓ —— ⭐ 它们**都已经**写了 `skipNested()` 早退 ✓
        //     ⭐ 只差**这里把标记打开** ✓。
        //
        //   <h3>⭐ 打开之后的效果</h3>
        //   ⭐ 分段期间 ⭐ 自家增幅器**全部早退** ✓ ⇒ ⭐ 剩下的只有
        //   ⭐ **外层那一次放大** ✓ ⇒ ⭐ 数值正好落在"整次攻击"的语义上 ✓ ✓
        //   ⚠ 而 ⭐ **别的模组**的增幅器**看不到这个标记** ✗（⭐ 别的 classloader ✓
        //     见 `DamagePipeline` 的注释 ✓）⇒ ⭐ 那些仍会逐段跑 ✓ ⭐ 我们管不了 ✓。
        //
        //   ⚠⚠ **不要**在这里处理"命灯"（`LifeLampRingHandler` ✓）——
        //   ⭐ 用户口径（2026-10-10 ✓）：「**我故意穿透可以破命灯的保护**」✓
        //   ⇒ ⭐ 那是有意设计 ✗ ⭐ 别顺手"修"掉 ✓。
        com.mofengbaizhi.tinkersnewlife.util.DamagePipeline.enter();
        try {
            while (remaining > 0.0F && target.isAlive() && !target.isRemoved() && guard++ < MAX_CHUNKS) {
                float part = Math.min(remaining, CHUNK);
                target.invulnerableTime = 0;
                markIntent(target, part);
                target.hurt(src, part);
                remaining -= part;
            }
        } finally {
            // ⭐ 无论如何都要还原 ✓（⭐ 否则后面所有伤害都进不了自家增幅器 ✗）
            com.mofengbaizhi.tinkersnewlife.util.DamagePipeline.exit();
        }
        return startHp - target.getHealth();
    }

    /**
     * ⭐⭐ §1189 <b>手动补"受击效果 ＋ 伤害音效"</b>
     * （⭐ 用户口径 ✓ 2026-10-10：「**删掉分段，然后调用受击效果和伤害音效**」✓）。
     *
     * <h2>⚠ 为什么必须手动做（⭐ 因为 §1189 删了分段 ✗ 不再走 `hurt()` ✓）</h2>
     * ⭐ `LivingEntity#hurt` 顺带做这些事 ✗：
     * <ul>
     *   <li>⭐ `hurtTime ＝ hurtDuration ＝ 10` ✗ ⇒ ⭐ **客户端红闪** ✓；</li>
     *   <li>⭐ `level.broadcastEntityEvent(this, EntityEvent.HURT)` ✗
     *       ⇒ ⭐ 客户端 ⭐ `handleEntityEvent` ⭐ 自己调 ⭐ `getHurtSound(source)`
     *       ⭐ **播受击音效 ＋ 摆受击姿态** ✓。</li>
     * </ul>
     * ⚠ 不走 `hurt` 之后这些就**全都没有** ✗ ⇒ ⭐ 打上去像"凭空掉血" ✓ ⇒ ⭐ 这里补回来 ✓。
     *
     * <h2>⚠ 为什么用 `broadcastEntityEvent` 而不是自己播声音 ✗</h2>
     * ⭐ `LivingEntity#getHurtSound(DamageSource)` 是 ⭐ **`protected`** ✗ ⭐ **外部调不到** ✓
     * ⇒ ⭐ 自己播就得**猜**一个通用音效 ✗（⭐ 那会"僵尸发出玩家的声音" ✓）
     * ⇒ ⭐ `broadcastEntityEvent(EntityEvent.HURT)` 让 ⭐ **每个实体用它自己的音** ✓ ✓ ——
     * ⭐ 这是**唯一**正确取音的办法 ✓。
     *
     * <p>⚠ 两个字段（⭐ `hurtTime`／`hurtDuration` ✓）在 1.20.1 都是 ⭐ **`public int`** ✓ ⇒ ⭐ 可直接写 ✓。
     */
    private static void playHurtFeedback(LivingEntity target, DamageSource src, float amount) {
        try {
            // ⭐ ① 受击红闪（⭐ 客户端读这两个字段决定闪不闪、闪多久 ✓）
            target.hurtTime = 10;
            target.hurtDuration = 10;
        } catch (Throwable ignored) {
        }
        try {
            // ⭐ ② 受击事件广播 ⇒ ⭐ 客户端播"该实体自己的"受伤音 ＋ 摆受击姿态 ✓
            //   ⚠ 1.20.1 里 ⭐ `EntityEvent` **没有 `HURT` 这个常量** ✗（⭐ 编译实测 ✓）
            //     ⇒ ⭐ 直接用**字面值 `(byte) 2`** ✓ —— ⭐ 它就是"受击"那个事件号 ✓
            //       （⭐ `LivingEntity#handleEntityEvent` 的 `case 2` ⭐ ⇒ ⭐ 播 `getHurtSound` ✓）。
            target.level().broadcastEntityEvent(target, (byte) 2);
        } catch (Throwable ignored) {
            // ⭐ 广播失败也不影响掉血 ✓
        }
        // ⭐⭐ §1231 **补"最后攻击者"** ✗ —— ⭐ 一是判定玩家击杀 ✗ ⭐ 二是别的 mod 记账要用 ✓
        try {
            if (src != null && src.getEntity() instanceof net.minecraft.world.entity.player.Player p) {
                target.setLastHurtByPlayer(p);
                target.setLastHurtByMob(p);
            } else if (src != null && src.getEntity() instanceof LivingEntity le) {
                target.setLastHurtByMob(le);
            }
        } catch (Throwable ignored) {
        }
        // ⭐⭐ §1231 **补一次战斗记录** ✗ —— ⭐ 死亡消息／⭐ 战斗追踪／⭐ 某些 mod 的统计要用 ✓
        try {
            target.getCombatTracker().recordDamage(src, amount);
        } catch (Throwable ignored) {
        }
        // ⭐⭐⭐⭐ §1231 **手动补一次 `LivingHurtEvent`** ✗✗（⭐ 用户口径 ✓「**补上受击事件**」✓）
        //   ⚠ 为什么必须补 ✗：⭐ 我们是 ⭐ **"逆向改血"** ✗ ⭐ **压根不经过 `hurt()`** ✓
        //     ⇒ ⭐ 听 `LivingHurtEvent` 的 mod ⭐ **完全看不见这一下** ✓
        //     （⭐ 测试包 ⭐ `[伤害引擎]` 的 ⭐ `DamageEngine` ⭐ **就是听它的** ✓
        //       ⭐ NL 包 ⭐ `damagenumbers` ⭐ 则是 ⭐ **纯看客户端血量变化** ✓）
        //   ⭐ 两道防护 ✗：
        //     ⭐ ① ⭐ `SYNTHETIC_HURT` 门 ✗ ⭐ 我们自己的处理器收到后**直接忽略** ✓（⭐ 防递归 ✓）；
        //     ⭐ ② ⭐ `DamagePipeline.enter/exit` ✗ ⭐ 自家那些"增幅器"靠 `skipNested()` **自动早退** ✓
        //       （⭐ 否则这一下会被**再增幅一遍** ✓ ⭐ 与 §1189 的教训一致 ✓）。
        if (amount > 0.01F && !SYNTHETIC_HURT && POST_SYNTHETIC_HURT_EVENT) {
            SYNTHETIC_HURT = true;
            com.mofengbaizhi.tinkersnewlife.util.DamagePipeline.enter();
            try {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                        new net.minecraftforge.event.entity.living.LivingHurtEvent(target, src, amount));
            } catch (Throwable ignored) {
                // ⭐ 事件层出错不影响我们已经改好的血 ✓
            } finally {
                com.mofengbaizhi.tinkersnewlife.util.DamagePipeline.exit();
                SYNTHETIC_HURT = false;
            }
        }
    }

    /** 不经 hurt() 的直伤（下界亚波伦）：⭐ **逆向改血** 扣血 ＋ 打空后主动 die()，让击败状态机正常结算 */
    private static void directDamage(LivingEntity target, float dmg, DamageSource src) {
        // ⭐ §1188：⭐ 读写都走"逆向改血" ✗（⭐ 绕过可能被覆写的 `getHealth`／`setHealth` ✓）
        float before = rawHealth(target);
        float hp = before - dmg;
        if (hp <= 0.0F) {
            rawSetHealth(target, 0.0F);
            if (!target.isRemoved()) target.die(src);           // 同上 ✗
        } else {
            // ⭐⭐ §1199 **写入后复查** ✗（⭐ 用户实测 2026-10-10：
            //   「**主世界亚波伦也破了，但是下界亚波伦好像有血量回弹？**」✓）
            //   ⚠ 原因：⭐ 有些 Boss（⭐ 下界亚波伦那类 ✓）**在同一个 tick 里靠自己回血**
            //   （⭐ 启示录的头衔系统 ⭐ 每 tick `heal(maxHealth × 2.5%)` ✓
            //     ⭐ 或 ⭐ 换阶段直接回满 ✓）⇒ ⭐ 我们写完它**又弹回去** ✓
            //   ⇒ ⭐ 通用做法：⭐ **写完立刻复查** ✗ ⭐ 若被弹回 ⇒
            //     ⭐ **再压一次回血** ＋ ⭐ **再写一次** ＋ ⭐ 再复查 ✓
            //     ⚠ 全程**不判断目标类型** ✓（⭐ 谁都会走这一遍 ✓ 普通怪一次就过 ✓）。
            writeAndVerify(target, hp);
            // ⭐⭐⭐⭐ §1229 **累计伤害处决 ⭐ 这条分支也必须做** ✗✗
            //   ⚠ 用户实测 ✓ 2026-10-10：「**没用**」＋ 日志 ⭐ `[真伤·累计处决]` **0 行** ✓
            //   ⇒ ⭐ 根因 ✗：⭐ §1228 我把累计逻辑**只加在 `applyInner` 的通用分支**里 ✓
            //     ⚠ 而 ⭐ **下界亚波伦走的是这条 `directDamage`** ✗
            //     ⇒ ⭐ **两条路只补了一条** ✓ ✓（⭐ 与 §1213 同一个毛病：⭐ 改了 A 忘了耦合的 B ✓）
            //   ⭐ 规则同 §1228 ✓：⭐ 没落地的伤害累计 ≥ 它当前报的血 ⇒ ⭐ 处决 ✓。
            try {
                float after = rawHealth(target);
                float dealt = Math.max(0.0F, before - after);
                float deficit = CUMULATIVE_EXECUTE ? Math.max(0.0F, dmg - dealt) : 0.0F;
                if (deficit > 0.01F) {
                    float owed = DAMAGE_OWED.merge(target.getUUID(), deficit, Float::sum);
                    if (owed >= after) {
                        DAMAGE_OWED.remove(target.getUUID());
                        TinkersNewlife.LOGGER.debug(
                                "[真伤·累计处决] 累计未落地伤害 {} ≥ 当前报的血 {} ⇒ 处决 {} ✓",
                                String.format(java.util.Locale.ROOT, "%.2f", owed),
                                String.format(java.util.Locale.ROOT, "%.2f", after),
                                target.getName().getString());
                        try {
                            com.mofengbaizhi.tinkersnewlife.content.curse.KillAttribution.credit(
                                    target, src.getEntity());
                        } catch (Throwable ignored) {
                        }
                        rawSetHealth(target, 0.0F);
                        if (!target.isRemoved()) {
                            target.die(src);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * ⭐⭐ §1199 <b>写入血量 ＋ 复查 ＋ 必要时重试</b>（⭐ 通用：⭐ 任何目标都走这一遍 ✓）。
     *
     * <p>⚠ 为什么要复查 ✗：⭐ 逆向改血走的是 ⭐ **绕过 `hurt` 的直写** ✗ ⇒
     * ⭐ 目标那些"**每 tick 自己回血**"的逻辑 ⭐ **不会被我们的伤害事件打断** ✓
     * ⇒ ⭐ 写完可能**立刻被弹回去** ✓（⭐ 用户实测：⭐ 下界亚波伦"血量回弹" ✓）
     * ⇒ ⭐ 所以 ⭐ 写 → 读 → 被弹就 ⭐ **压回血 ＋ 重写** ✓。
     *
     * <p>⭐ 最多试 {@value #WRITE_RETRIES} 次 ✓ ⭐ 都失败就记一条日志 ✓（⭐ 便于定位 ✓）。
     */
    private static final int WRITE_RETRIES = 3;

    /**
     * ⭐⭐ §1202 <b>"锁血"对策：⭐ 跨 tick 连续压制</b>
     * （⭐ 用户实测 ✓ 2026-10-10：「**好像还是没显著效果**」✓）。
     *
     * <h2>⚠ 探针实证的现象</h2>
     * ⭐ 血**确实在掉** ✗（⭐ WARN 序列 `406 → 386 → 366 → … → 276` ✓ ⭐ 每发约 20 ✓）
     * ⚠ **但每次都被拉回一个"档位下界"** ✗（⭐ 探针里 `写=56.50 弹回=87.00` ✓）
     * ⇒ ⭐ 那是 ⭐ **"锁血"** ✗ ⭐ 不是"回血" ✓ ——
     * ⭐ 反编译的 ⭐ `titleNumber(health)` ⭐ 把血量分 **14 档** ✗ ⭐ 每档有下界 ✓
     * ⇒ ⭐ 我们的写入**被夹回档位下界** ✓ ⇒ ⭐ 每发**只能推进一档** ✓ ⇒ ⭐ 看起来"没效果" ✓。
     *
     * <h2>⭐ 对策</h2>
     * ⭐ **同一个 tick 里写多少次都没用** ✗（⭐ 它在**后面**才拉回 ✓）
     * ⇒ ⭐ 只能用 ⭐ **跨 tick 的待补队列** ✗ ⭐ 在接下来 {@value #PENDING_TICKS} 个 tick 里
     * ⭐ **每 tick 再写一次** ✓ ⇒ ⭐ 把档位一次次顶穿 ✓ ✓。
     *
     * <p>⚠ 通用 ✗：⭐ 任何目标都会走这一遍 ✓ ⭐ 普通怪第一次就写住 ⇒ ⭐ 立刻出队 ✓
     * ⭐ 只有"锁血"的目标才会被连续按几 tick ✓ ✓。
     */
    /**
     * ⭐⭐ §1204 <b>"以锁制锁"：⭐ 每 tick 强制写回目标值</b>
     * （⭐ 用户口径 ✓ 2026-10-10：「**他们能一直把血量压回目标线，我们能不能让血量始终锁在
     * 我们的目标值，不让任何行为改动？**」✓）。
     *
     * <h2>⭐⭐ 为什么我们一定赢</h2>
     * ⭐ 目标的"锁血／回血"是在 ⭐ **它自己的 `tick` 里**跑的 ✗
     * ⭐ 而本处理挂在 ⭐ **`ServerTickEvent.END`** ✗
     * ⇒ ⭐ **实体 tick 早已结束** ✓ ⇒ ⭐ **我们写的是最后一下** ✓ ✓
     * ⇒ ⭐ 只要 ⭐ **每 tick 都写一次** ✗ ⭐ 对外表现就是 ⭐ **锁死在我们给的值上** ✓
     * ⭐ **任何行为都改不动** ✓ ✓（⭐ 包括它的锁血、回血、头衔切换 ✓）。
     *
     * <h2>⚠ 通用边界（⭐ 不是"针对某个 Boss" ✓）</h2>
     * ⭐ 触发条件只有一条 ✗：⭐ **"我们打过它、而且写不住"** ✓
     * ⭐ 不认实体类型 ✓ ⭐ 不认模组 ✓ ⭐ 而且 ⭐ **有期限**（{@value #LOCK_TICKS} tick ✓）
     * ⭐ 到 0 或到期就**自动解锁** ✓ ⇒ ⭐ 不是永久枷锁 ✓。
     */
    private static final int LOCK_TICKS = 5;

    /**
     * ★ TNL1399 <b>回血压制延长</b>（用户口径：真伤就是掉血，不能被回血吃回去）
     *
     * <p>⚠ 现象（Mowzie 钢铁守护者那类 Boss 实证 ✓）：
     * 它 `active == false`（脱战）时**每 tick 自己 `heal(0.3)`（= 6 血/秒）**✗
     * 而它的 `active` 只会在"有目标 **且距离 ≤ 4.5 格**"时才切 true ✓
     * ⇒ 远程/无源真伤把血写下去后，5 tick 锁一到期就被它**每 tick 回 0.3** 慢慢填满 ✗
     * ⇒ 看起来"真伤破不了它的无敌" ✓。
     *
     * <p>⭐ 通用规则（不认实体、不认模组 ✓）：**锁期间发现"上一 tick 我们自己写下去的血
     * 又被它回上来了" ⇒ 自动续锁** ✓（最多续到 {@link #MAX_LOCK_TICKS} = 10 秒 ✓）。
     * 它只要还在回血 ⇒ 我们每 tick 把血再压回去 ✓ ⇒ 真伤落地可见 ✓。
     */
    private static final int EXTEND_TICKS = 40;

    /** 续锁总上限（从第一次 enqueue 起算，10 秒；防止永久禁疗 ✓） */
    private static final int MAX_LOCK_TICKS = 200;

    /**
     * ⭐⭐ §1205 <b>还原被压的血量上限</b> ✗ —— ⭐ 锁到期时必须调 ✓
     * （⭐ 用户实测 2026-10-10：「**怎么还是锁住了**」✓）。
     *
     * <h2>⚠ 为什么必须还原</h2>
     * ⚠ ⭐ §1203 的 `clampMaxHealth` 给 `MAX_HEALTH` 加了个**永久**修饰符 ✗ ⭐ 而**从不移除** ✓
     * ⇒ ⭐ 即使锁过期 ✗ ⭐ 它的上限**还是低的** ✓ ⇒ ⭐ **回不满** ✓ ⇒ ⭐ 看起来"还锁着" ✓ ✓
     * ⇒ ⭐ 所以到期第一件事就是 ⭐ **移除那个修饰符** ✓ ✓。
     */
    private static void releaseClamp(LivingEntity target) {
        try {
            var attr = target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (attr != null) {
                attr.removeModifier(CLAMP_ID);
            }
        } catch (Throwable ignored) {
        }
    }

    private record Pending(java.util.UUID id, float value, int left, int spent) {
    }

    /** TNL1400 真伤"一点没落地"诊断限频（每目标 1 秒一条） */
    private static final java.util.Map<java.util.UUID, Long> TNL_LAST_NO_LAND =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** TNL1401 写不进诊断限频 */
    private static final java.util.Map<java.util.UUID, Long> TNL_LAST_WRITE_FAIL =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<java.util.UUID, Pending> PENDING =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** ⭐ 登记一个"跨 tick 待补"✗（⭐ 同目标覆盖 ✓ 取更低的那个值 ✓） */
    private static void enqueuePending(LivingEntity target, float value) {
        PENDING.put(target.getUUID(), new Pending(target.getUUID(), value, LOCK_TICKS, 0));
    }

    /** ⭐ "压上限"用的固定修饰符 UUID ✗（⭐ 稳定 ⇒ 可反复覆盖与移除 ✓） */
    private static final java.util.UUID CLAMP_ID =
            java.util.UUID.nameUUIDFromBytes("tinkersnewlife:pierce_health_clamp".getBytes());

    /**
     * ⭐⭐ §1203 <b>把 `MAX_HEALTH` 压到目标值</b> ✗ —— ⭐ 对付"禁疗开着也回血"的目标 ✓。
     *
     * <h2>⚠ 为什么需要它（⭐ 探针实证 ✓）</h2>
     * ⭐ 回弹量 ⭐ **恒 ≈ 当前血量的 2.5%** ✗ ⇒ ⭐ 目标的回血**不走 `Apostle.heal`** ✓
     *（⭐ 启示录自己的重定向／⭐ 头衔切换回满／⭐ 再生效果 ✓）⇒ ⭐ 禁疗再狠也拦不住 ✓
     * ⇒ ⭐ 那就 ⭐ **压它的上限** ✗ ⇒ ⭐ 它**回满也只能回到我们给的值** ✓ ✓。
     *
     * <p>⚠ 副作用（⭐ 如实 ✓）：⭐ 它的血条上限会**变小** ✓（⭐ 对 Boss 来说这本来就是"被打残"的表现 ✓）。
     * ⭐ 用 ⭐ **固定 UUID 的 `AttributeModifier`** ✗ ⇒ ⭐ 不叠加 ✓ ⭐ 且可被移除 ✓。
     */
    private static void clampMaxHealth(LivingEntity target, float value) {
        try {
            var attr = target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (attr == null) return;
            float want = Math.max(1.0F, value);
            // ⭐ 先移除旧的（⭐ 同 UUID ✓）再按当前基础值算差额 ✓
            attr.removeModifier(CLAMP_ID);
            double base = attr.getBaseValue();
            double delta = want - base;
            if (delta < -0.01D) {
                attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        CLAMP_ID, "tinkersnewlife:pierce_health_clamp", delta,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
            }
        } catch (Throwable ignored) {
            // ⭐ 压上限失败不影响写血 ✓
        }
    }

    /**
     * ⭐ 每 tick 处理待补队列 ✗ —— ⭐ 对"锁血"目标反复顶 ✗ ⭐ 直到写住或次数用完 ✓。
     */
    @SubscribeEvent
    public static void onServerTickPending(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        try {
            var server = event.getServer();
            if (server == null) return;
            // ⭐⭐ §1207 **两个时机都写** ✗（⭐ 用户实测 2026-10-10：⭐ 连 `ServerLevel#tick`
            //   的 TAIL 都被改回去了 ✓ ⇒ ⭐ 说明"哪个更晚"取决于 Forge 调度 ✓
            //   ⇒ ⭐ **两边都挂** ✗ ⭐ 总有一个在它后面 ✓ ⭐ 且 priority 提到 3000 ✓）
            //   ⭐ 本处保留原来的逐维度查找逻辑 ✓（⭐ 作为 `ServerTickEvent.END` 那一侧的兜底 ✓）
            for (var lvl : server.getAllLevels()) {
                applyHealthLocks(lvl);
            }
        } catch (Throwable ignored) {
            // ⭐ 待补处理出错绝不能连累玩法 ✗
        }
    }

    /**
     * ⭐⭐ §1206 <b>在 {@code ServerLevel#tick} 的 TAIL 里应用血量锁</b> ✗
     * （⭐ 用户口径 ✓ 2026-10-10：「**加校验，写mixin**」✓）。
     *
     * <h2>⭐⭐ 为什么这个时机"包赢"</h2>
     * ⭐ `ServerLevel#tick` ⭐ **内部 tick 掉这个世界所有实体** ✗
     * ⭐ 而本方法由 ⭐ `ServerLevelTickTailMixin` 在 ⭐ **同一个方法的 `TAIL`** 调用 ✓
     * ⇒ ⭐ **所有实体都已经 tick 完（⭐ 回血/锁血都跑过了 ✓）** ✓
     * ⇒ ⭐ **我们写的就是最后一下** ✓ ✓（⭐ 比原来的 `ServerTickEvent.END` 更确定 ✓）。
     *
     * <h2>⭐ 顺便做"事后校验"（⭐ 用户要的 ✓）</h2>
     * ⚠ ⭐ 如果连"最晚写"都还是被改 ✗ ⇒ ⭐ **一定有人在更晚的地方／别的线程改血** ✓
     * ⇒ ⭐ 打一条日志把 ⭐ **当前值 ／ 我们的值 ／ 目标名** 报出来 ✓ ⭐ 便于一次定位 ✓。
     *
     * <p>⚠ 只处理**这个世界**的实体 ✓ ⭐ 用 `level.getEntity(uuid)` 查 ✓
     * ⇒ ⭐ 跨维度不会白跑 ✓ ⭐ 实体不在/死了 ⇒ ⭐ 出队 ✓。
     */
    public static void applyHealthLocks(net.minecraft.server.level.ServerLevel level) {
        if (PENDING.isEmpty()) {
            return;
        }
        var it = PENDING.entrySet().iterator();
        // ⭐⭐⭐⭐ §1232 **锁期间关闭"客户端同步"** ✗✗（⭐ 用户实测 ✓ 2026-10-10：
        //   「**怎么打一次又弹了3段伤害？**」✓）
        //   ⚠ 根因：⭐ 锁是"每 tick 都写"✗（⭐ 写 → 它回血 → 再写 ✓）
        //     ⭐ 而 ⭐ `rawSetHealthInner` **自己会调 `setHealth` ＋ 标脏** ✓
        //     ⇒ ⭐ 客户端**每 tick 都看到血量变一次** ⇒ ⭐ 数字**连弹** ✓ ✓
        //   ⇒ ⭐ 锁 ⭐ **只负责服务端压血** ✗ ⭐ 同步交给 ⭐ "第一次那一发"（`writeAndVerify` ✓）✓
        SUPPRESS_CLIENT_SYNC = true;
        boolean tnlSuppressReset = false;
        try {
        while (it.hasNext()) {
            var e = it.next();
            Pending pd = e.getValue();
            net.minecraft.world.entity.Entity ent = level.getEntity(pd.id());
            if (ent == null) {
                continue;   // ⚠ 可能它在别的维度 ⇒ ⭐ 留给那个维度处理 ✓
            }
            if (!(ent instanceof LivingEntity living) || living.isRemoved() || !living.isAlive()) {
                it.remove();
                continue;
            }
            float target = pd.value();
            // ★ TNL1399 写入前先读一次：若比我们上次写的值高 ⇒ 它刚自己回过血 ⇒ 待会儿续锁 ✓
            float beforeWrite = rawHealth(living);
            // ⭐ 死线以下 ⇒ ⭐ 走死亡流程 ✓
            if (target <= 0.0F) {
                try {
                    // ⚠⚠ §1232 **这里用"不触发客户端同步"的写法** ✗✗
                    //   ⭐ 用户实测 ✓ 2026-10-10：「**怎么打一次又弹了3段伤害？**」✓
                    //   ⚠ 根因：⭐ 锁是 ⭐ **每 tick 都写一次**（⭐ 写 → 它回血 → 再写 ✓）
                    //     ⭐ 配上 ⭐ §1231 新加的客户端同步 ✗
                    //     ⇒ ⭐ 客户端**每 tick 都看到血量变一次** ⇒ ⭐ 伤害数字**连弹** ✓ ✓
                    //   ⇒ ⭐ 锁**只负责"服务端压血"** ✗ ⭐ 同步交给 ⭐ 第一次 `writeAndVerify` 那一次 ✓
                    rawSetHealthInner(living, 0.0F);
                    if (!living.isRemoved()) living.die(living.damageSources().genericKill());
                } catch (Throwable ignored) {
                }
                it.remove();
                continue;
            }
            // ⭐ 压上限 ＋ 写血 ✓
            // TNL1399: clamp MAX_HEALTH only on the first lock tick, otherwise the
            // client health bar shows "max = locked value" and looks full.
            if (pd.spent() == 0) {
                clampMaxHealth(living, target);
            }
            // ⚠⚠ §1232 **同上：只压服务端 ✗ 不再每 tick 同步客户端** ✓（⭐ 免得伤害数字连弹 ✓）
            rawSetHealthInner(living, target);
            // ⭐⭐ **事后校验** ✗ —— ⭐ 读回来若不是我们的值 ⇒ ⭐ 说明有人"更晚"改 ✓
            float now = rawHealth(living);
            if (now > target + 0.01F) {
                TinkersNewlife.LOGGER.debug(
                        "[真伤·校验] 最晚写入后仍被改：{}（{}）我们的值={} 当前={} ⇒ 有人在更晚处改血 ✓",
                        living.getName().getString(),
                        net.minecraft.world.entity.EntityType.getKey(living.getType()).toString(),
                        String.format(java.util.Locale.ROOT, "%.2f", target),
                        String.format(java.util.Locale.ROOT, "%.2f", now));
            }
            suppressRegen(living);
            int left = pd.left() - 1;
            int spent = pd.spent() + 1;
            // ★ TNL1399 它在我们上次写入后又回血了 ⇒ 续锁（最多 MAX_LOCK_TICKS）✓
            if (beforeWrite > target + 0.05F && spent < MAX_LOCK_TICKS) {
                left = Math.max(left, EXTEND_TICKS);
                if (spent == 1 || spent % 40 == 0) {
                    TinkersNewlife.LOGGER.debug(
                            "[真伤·续锁] {} 在回血（{} > {}）⇒ 续锁 {} tick（已 {}）✓",
                            living.getName().getString(),
                            String.format(java.util.Locale.ROOT, "%.2f", beforeWrite),
                            String.format(java.util.Locale.ROOT, "%.2f", target),
                            left, spent);
                }
            }
            if (left <= 0) {
                releaseClamp(living);
                it.remove();
            } else {
                e.setValue(new Pending(pd.id(), target, left, spent));
            }
        }
        } finally {
            // ⭐⭐ §1232 **锁跑完 ⇒ 恢复同步** ✗ ＋ TNL1402 **异常也必须复位** ✓
            //   （⚠ 原来复位语句在循环后：循环里一抛异常就永远卡在 true ⇒ 后面所有真伤都不同步 ✗）
            SUPPRESS_CLIENT_SYNC = false;
            tnlSuppressReset = true;
        }
        if (!tnlSuppressReset) {
            SUPPRESS_CLIENT_SYNC = false;
        }
    }

    /**
     * ⭐⭐ §1208 <b>锁定期内 {@code getHealth()} 该返回的值</b> ✗ —— ⭐ 供 mixin 调用 ✓。
     *
     * @return ⭐ **≥ 0** ＝ 这个实体被锁着，返回锁定值 ✓ ⭐ **-1** ＝ 没锁（⭐ 正常走原版 ✓）
     */
    public static float lockedHealthFor(LivingEntity entity) {
        if (entity == null) {
            return -1.0F;
        }
        // ⚠⚠ §1222 **影子血已回退** ✗（⭐ 见 `rawSetHealth` 的说明 ✓）
        // ⚠⚠ **空判先行** ✗ —— ⭐ `getHealth()` 是热点方法 ✓
        //   ⭐ 绝大多数实体都没被锁 ⇒ ⭐ 一次 `isEmpty()` 就返回 ⇒ **零开销** ✓ ✓
        if (PENDING.isEmpty()) {
            return -1.0F;
        }
        try {
            Pending pd = PENDING.get(entity.getUUID());
            return pd == null ? -1.0F : pd.value();
        } catch (Throwable ignored) {
            return -1.0F;
        }
    }

    /** ⭐⭐⭐⭐ §1220 **影子血**：⭐ 真血写不进去的 Boss（⭐ 亚波伦那类 ✓）由我们记账 ✓ */
    private static final java.util.Map<java.util.UUID, Float> SHADOW_HEALTH =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicBoolean SHADOW_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /** ⭐ 读影子血 ✗（⭐ 没有 ⇒ -1 ✓） */
    private static float shadowHealthOf(LivingEntity e) {
        if (SHADOW_HEALTH.isEmpty() || e == null) {
            return -1.0F;
        }
        try {
            Float v = SHADOW_HEALTH.get(e.getUUID());
            return v == null ? -1.0F : v;
        } catch (Throwable ignored) {
            return -1.0F;
        }
    }

    /** ⭐ 这个实体有没有"额外血量通道"✗（⭐ = ⭐ 它的血是算出来的 ✓ ⇒ ⭐ 只能记账 ✓） */

    /**
     * ⭐⭐⭐⭐ §1240 <b>统一清理"按 UUID 的记账表"</b> ✗✗
     * （⭐ 用户口径 ✓ 2026-10-10：「**检查一下收回和封印之类的逻辑有没有需要优化的地方**」✓ 六项全做 ✓）
     *
     * <h2>⚠ 为什么要统一 ✗</h2>
     * ⭐ 真伤线现在有 ⭐ **四张按 UUID 记的表** ✗：
     * ⭐ `LAST_START_HP`（⭐ 上一发的血 ✓）✗ ⭐ `DAMAGE_OWED`（⭐ 累计未落地伤害 ✓）✗
     * ⭐ `SHADOW_HEALTH`（⭐ 影子血 ✗ 现已停用 ✓）✗ ⭐ `PENDING`（⭐ 跨 tick 锁 ✓）
     * ⚠ ⭐ 而 ⭐ **实体死亡／⭐ 移除／⭐ 被收回／⭐ 被封印**时 ⭐ 没有任何一处**统一清它们** ✓
     * ⇒ ⭐ 以前靠 ⭐ `size > 512` 就 `clear()` ✗ ⭐ 那是**粗暴地连别人一起抹** ✓
     * ⭐ ⇒ 现在 ⭐ **按实体精确清** ✗ ⭐ 挂在 ⭐ **死亡事件** ＋ ⭐ **收回** ＋ ⭐ **封印** ✓ ✓。
     */
    public static void forgetEntity(LivingEntity e) {
        if (e == null) {
            return;
        }
        try {
            java.util.UUID id = e.getUUID();
            LAST_START_HP.remove(id);
            DAMAGE_OWED.remove(id);
            SHADOW_HEALTH.remove(id);
            PENDING.remove(id);
        } catch (Throwable ignored) {
        }
    }

    /** ⭐ 旧名保留 ✗（⭐ 现在统一走 {@link #forgetEntity} ✓） */
    public static void forgetShadow(LivingEntity e) {
        forgetEntity(e);
    }

    /** ⭐⭐ §1240 死亡 ⇒ ⭐ **精确清理这个实体的记账** ✓（⭐ 免得表越攒越大 ✓） */
    @SubscribeEvent
    public static void onLivingDeathCleanup(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        try {
            forgetEntity(event.getEntity());
        } catch (Throwable ignored) {
        }
    }

    private static void writeAndVerify(LivingEntity target, float value) {
        for (int i = 0; i < WRITE_RETRIES; i++) {
            rawSetHealth(target, value);
            float back = rawHealth(target);
            if (back <= value + 0.01F) {
                return;   // ⭐ 写住了 ✓
            }
            // ⚠⚠ **回弹探针**（⭐ 用户口径 ✓ 2026-10-10：「**加个探针，附带检测血量**」✓）
            //   ⭐ 打出：⭐ 第几次 / ⭐ 目标 / ⭐ 写入值 / ⭐ 弹回值 / ⭐ `getHealth` 与字段两条通道
            //   ＋ ⭐ 诡厄的禁疗状态（`antiRegen` ＋ `isSmited` ＋ `moddedInvul` ✓）
            //   ⇒ ⭐ 一眼看出是"**禁疗失效**"还是"**换阶段直接回满**" ✓。
            try {
                TinkersNewlife.LOGGER.debug(
                        "[真伤·回弹] 第{}次 {}（{}）写={} 弹回={} | getHealth={} 字段={} | antiRegen={} isSmited={} moddedInvul={}",
                        (i + 1),
                        target.getName().getString(),
                        net.minecraft.world.entity.EntityType.getKey(target.getType()).toString(),
                        String.format(java.util.Locale.ROOT, "%.2f", value),
                        String.format(java.util.Locale.ROOT, "%.2f", back),
                        String.format(java.util.Locale.ROOT, "%.2f", target.getHealth()),
                        String.format(java.util.Locale.ROOT, "%.2f", fieldHealth(target)),
                        GoetyBridge.readAntiRegen(target),
                        GoetyBridge.readIsSmited(target),
                        GoetyBridge.readModdedInvul(target));
            } catch (Throwable ignored) {
            }
            // ⚠ 被弹回 ⇒ ⭐ 再压一次它的再生（⭐ 通用调用 ✓ 非诡厄目标内部会 no-op ✓）
            suppressRegen(target);
        }
        if (rawHealth(target) > value + 0.01F) {
            TinkersNewlife.LOGGER.debug(
                    "[真伤] 逆向改血被回弹 {} 次仍未写住：{}（{}）当前={} 目标={}",
                    WRITE_RETRIES, target.getName().getString(),
                    net.minecraft.world.entity.EntityType.getKey(target.getType()).toString(),
                    String.format(java.util.Locale.ROOT, "%.2f", rawHealth(target)),
                    String.format(java.util.Locale.ROOT, "%.2f", value));
            // ⭐⭐ §1202 **同一 tick 写不住 ⇒ 登记"跨 tick 待补"** ✗
            //   （⭐ 那是"锁血"：⭐ 它在后面把血夹回档位下界 ✓ ⭐ 只能跨 tick 一直顶 ✓）
            enqueuePending(target, value);
        }
    }

    /** ⭐ 只读**字段**那条通道 ✗（⭐ 与 `getHealth()` 对比 ⇒ ⭐ 看两条通道是否一致 ✓） */
    private static float fieldHealth(LivingEntity target) {
        java.lang.reflect.Field f = healthField();
        if (f != null) {
            try {
                return f.getFloat(target);
            } catch (Throwable ignored) {
            }
        }
        return -1.0F;
    }

    private static void suppressRegen(LivingEntity target) {
        try {
            GoetyBridge.suppressApostleRegen(target);
        } catch (Throwable ignored) {
        }
    }

    /** 清掉本模组"伤害限幅"效果：它会把多段穿透伤害整段取消 */
    private static void clearOwnDamageLimit(LivingEntity target) {
        try {
            var dl = com.mofengbaizhi.tinkersnewlife.content.ModEffects.DAMAGE_LIMIT.get();
            if (dl != null && target.hasEffect(dl)) target.removeEffect(dl);
        } catch (Throwable ignored) {
        }
    }

    /** 真伤源：优先用 Goety 那支（部分 Boss 认它），拿不到时退回本模组的 true_pierce */
    private static DamageSource source(net.minecraft.world.level.Level level, @Nullable LivingEntity attacker) {
        if (attacker != null) {
            DamageSource goety = GoetyBridge.truePierceSource(level);
            if (goety != null) {
                return new DamageSource(goety.typeHolder(), attacker, attacker);
            }
            return new DamageSource(pierceType(level), attacker, attacker);
        }
        DamageSource goety = GoetyBridge.truePierceSource(level);
        if (goety != null) return new DamageSource(goety.typeHolder(), null, null);
        return new DamageSource(pierceType(level), null, null);
    }

    private static Holder<DamageType> pierceType(net.minecraft.world.level.Level level) {
        Holder<DamageType> cached = cachedPierceType;
        if (cached == null) {
            cached = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(PIERCE_TYPE_KEY);
            cachedPierceType = cached;
        }
        return cached;
    }

    /** 登记"这一击想造成多少"（供事件层顶开；也可给别的穿透路径复用） */
    public static void markIntent(LivingEntity target, float amount) {
        if (target == null || target.level().isClientSide) return;
        INTENT.put(target.getUUID(), new Intent(amount, target.level().getGameTime()));
    }

    // ============================================================
    //  事件层顶开：在最后一刻把数值按原意放回去
    // ============================================================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPierceHurt(LivingHurtEvent event) {
        force(event.getEntity(), event.getSource(), event::setAmount);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPierceDamage(LivingDamageEvent event) {
        force(event.getEntity(), event.getSource(), event::setAmount);
    }

    private static void force(LivingEntity target, DamageSource source, Consumer<Float> setter) {
        if (target == null || target.level().isClientSide) return;
        Intent it = INTENT.remove(target.getUUID());
        if (it == null || it.tick() != target.level().getGameTime()) return;   // 不是"刚刚那一击"
        if (!isPierceLike(source)) return;
        setter.accept(it.amount());
    }

    /** 真伤源判定：本模组 true_pierce，或任何带 {@code bypasses_invulnerability} 的源（含 Goety 那支） */
    private static boolean isPierceLike(DamageSource source) {
        if (source == null) return false;
        return source.is(PIERCE_TYPE_KEY) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /** 便于外部检查"某个源是不是真伤源" */
    public static boolean isTruePierce(DamageSource source) {
        return isPierceLike(source);
    }

    /**
     * TNL978 ★★ 把"部件"换算回本体 ✓
     *
     * <p>★用户：★"命中多部位实体的非主碰撞箱时，没有造成伤害" ✓
     * ＋★"是不是只要是我的真实伤害都有这个问题" ✓
     *
     * <p>★真因 ✗：★巨龙类多部位实体命中的是
     * ``PartEntity`` ✓，★它**不是** ``LivingEntity`` ✗
     * ⇒ ★所有"命中后取目标"的写法都会**静默跳过** ✓。
     *
     * <p>★不会踩的 ✓：★事件型（★``event.getSource().getEntity()`` ✓）
     * ——★那是**攻击者** ✓ ⇒ ★与部件无关 ✓。
     */
    public static net.minecraft.world.entity.Entity resolveVictim(net.minecraft.world.entity.Entity e) {
        // TNL978 ★★ 返回类型故意用 **Entity** ✓
        //   ★否则 ``resolveVictim(x) instanceof LivingEntity y`` 会报
        //   ★"模式类型是表达式类型的子类" ✗（★编译器已知它就是 LivingEntity ✓）
        try {
            if (e instanceof net.minecraftforge.entity.PartEntity<?> part) {
                e = part.getParent();
            } else if (!(e instanceof net.minecraft.world.entity.LivingEntity)) {
                // TNL979 ★★ 用户：★"命中冰火传说龙的翅膀无法对龙造成伤害" ✓
                //   ★原因 ✗：★冰火（★Citadel ✓）的部件**不一定继承 Forge 的 ``PartEntity``** ✗
                //   ★现在：★反射找它的“父”（★getParent / getOwner / getRoot ✓）
                boolean done = false;
                for (String m : new String[]{"getParent", "getOwner", "getRoot"}) {
                    try {
                        Object r = e.getClass().getMethod(m).invoke(e);
                        if (r instanceof net.minecraft.world.entity.Entity pe && pe != e) {
                            e = pe;
                            done = true;
                            break;
                        }
                    } catch (Throwable ignored) {
                    }
                }
                // TNL981 ★★ 用户：★"命中冰火传说龙的翅膀无法对龙造成伤害" ✓
                //   ★日志证据：★``EntityDragonPart (entity.iceandfire.dragon_multipart)`` ✗
                //     ★它**不是 Forge 的 ``PartEntity``** ✗ ⇒ ★上面那几个方法也没命中 ✓
                //   ★现在：★最后一招 ✓：★**扫它自己的字段** ✓
                //     （★找类型是 Entity 且值是 LivingEntity 的字段 ✓）
                if (!done) {
                    Class<?> c2 = e.getClass();
                    while (c2 != null && c2 != Object.class && !done) {
                        for (java.lang.reflect.Field f2 : c2.getDeclaredFields()) {
                            if (!net.minecraft.world.entity.Entity.class.isAssignableFrom(f2.getType())) {
                                continue;
                            }
                            try {
                                f2.setAccessible(true);
                                Object v = f2.get(e);
                                if (v instanceof net.minecraft.world.entity.LivingEntity le2 && le2 != e) {
                                    e = le2;
                                    done = true;
                                    break;
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                        c2 = c2.getSuperclass();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return e;
    }
}
