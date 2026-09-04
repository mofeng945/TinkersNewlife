package com.mofengbaizhi.tinkersnewlife.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 诡厄巫法（Goety 及其扩展，如 Goety: Revelation）可选集成桥：
 * <p>
 * 不强依赖：仅当环境中存在 modid 含 "goety" 的模组时，按注册表实体 id 关键字
 * （boss=apollyon/apostle、黑曜石柱=pillar/obsidian、邪教徒=cultist）识别其 Boss 战元素；
 * 未安装时所有方法安全返回空/否，墨默只走通用逻辑。
 * <p>
 * 职责：识别亚波伦/使徒（伤害限制对象）、黑曜石柱、邪教徒；
 * 辅助墨默"破保护链"：邪教徒 → 黑曜石柱 → 目标，并对受限 Boss 使用绕过伤害；
 * 反射读取/清零使徒(亚波伦)的无敌计时（moddedInvul/obsidianInvul）与
 * 启示录 Apollyon 的受击冷却（hitCooldown，下界 1.5s 免疫窗）。
 * <p>
 * 反编译实证（goety-2.5.57.3 + GoetyRevelation-2.3.3fix）：
 * <ul>
 *   <li>黑曜石柱 = 使徒召唤的 ObsidianMonolith（注册 goety:obsidian_monolith），
 *       存活时每 tick 把 {@code Apostle.obsidianInvul} 置 10；
 *       {@code Apostle.hurt()} 在 obsidianInvul/moddedInvul &gt; 0 时直接返回 false（全程免伤）。</li>
 *   <li>{@code moddedInvul}（BossInvulnerabilityTime=15）：被带直接实体的伤害命中后置 15。
 *       我们的 genericKill 无直接实体，不会触发；但其他玩家先手会留下它，需一并清零。</li>
 *   <li>启示录 LivingEntityMixin：使徒处于 Apollyon 状态时，actuallyHurt 里把每次伤害
 *       clamp 到 apollyon_hurt_limit(=20)（不看伤害类型 tag）；下界另有 hitCooldown：
 *       每次 actuallyHurt 置 30，期间 hurt() 被 canHurt 直接取消（1.5s 免疫窗）。</li>
 *   <li>goety ApostleDamageCap(=20) 仅在不带 bypasses_invulnerability 的伤害下生效，
 *       genericKill 属于该 tag，天然绕过——只剩启示录的 20 clamp 需多段拆分。</li>
 *   <li>Apostle.hurt() 还有两道隐藏减伤：下界 apostleNetherDamageReduction(默认50%)；
 *       附近 32 格有非创造/旁观玩家 且 伤害直接实体非玩家 时再减半
 *       （genericKill 无直接实体 → 墨默/天逆鉾都会中招，需按同一条件补偿）。</li>
 * </ul>
 */
public final class GoetyBridge {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TinkersNewlife");

    private GoetyBridge() {}

    private static boolean resolved = false;
    private static boolean goetyPresent = false;
    private static final Set<EntityType<?>> BOSS_TYPES = new HashSet<>();
    private static final Set<EntityType<?>> PILLAR_TYPES = new HashSet<>();
    private static final Set<EntityType<?>> CULTIST_TYPES = new HashSet<>();

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            goetyPresent = ModList.get().getMods().stream()
                    .anyMatch(m -> m.getModId().toLowerCase().contains("goety"));
            if (!goetyPresent) {
                LOGGER.info("[GoetyBridge] 未检测到 goety 系模组（mods=" + ModList.get().getMods().size() + "），Goety 相关逻辑全部停用");
                return;
            }
            for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
                String path = ForgeRegistries.ENTITY_TYPES.getKey(type).getPath().toLowerCase();
                // summon_apostle 只是仪式用的召唤器实体，不是受限 Boss，排除
                if ((path.contains("apollyon") || path.contains("apostle")) && !path.contains("summon")) {
                    BOSS_TYPES.add(type);
                } else if (path.contains("pillar") || path.contains("obsidian")) {
                    PILLAR_TYPES.add(type);
                } else if (path.contains("cultist") || path.contains("sect")) {
                    CULTIST_TYPES.add(type);
                }
            }
            LOGGER.info("[GoetyBridge] 检测到 goety；受限Boss=" + typeIds(BOSS_TYPES)
                    + " 黑曜石柱=" + typeIds(PILLAR_TYPES)
                    + " 邪教徒=" + typeIds(CULTIST_TYPES));
        } catch (Throwable t) {
            goetyPresent = false;
            LOGGER.warn("[GoetyBridge] resolve 异常", t);
        }
    }

    private static String typeIds(Set<EntityType<?>> set) {
        StringBuilder sb = new StringBuilder("[");
        for (EntityType<?> t : set) {
            sb.append(ForgeRegistries.ENTITY_TYPES.getKey(t)).append(' ');
        }
        return sb.append(']').toString();
    }

    /** Goety 家族是否存在于环境且识别到相关实体 */
    public static boolean isAvailable() {
        resolve();
        return goetyPresent
                && (!BOSS_TYPES.isEmpty() || !PILLAR_TYPES.isEmpty() || !CULTIST_TYPES.isEmpty());
    }

    /** 是否为受限 Boss（亚波伦/使徒类） */
    public static boolean isDamageLimitedBoss(LivingEntity e) {
        resolve();
        return BOSS_TYPES.contains(e.getType());
    }

    private static boolean isType(Set<EntityType<?>> set, Entity e) {
        resolve();
        return e != null && set.contains(e.getType());
    }

    public static boolean isPillar(Entity e) {
        return isType(PILLAR_TYPES, e);
    }

    public static boolean isCultist(LivingEntity e) {
        return isType(CULTIST_TYPES, e);
    }

    /** 半径内最近的符合条件的实体（用于找柱/邪教徒/目标） */
    @Nullable
    private static Entity nearest(Level level, double x, double y, double z, double radius,
                                  Predicate<Entity> filter) {
        java.util.List<Entity> hits = level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                new net.minecraft.world.phys.AABB(x - radius, y - radius, z - radius,
                        x + radius, y + radius, z + radius), filter);
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : hits) {
            double d = e.distanceToSqr(x, y, z);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /** 目标周围的保护柱（8 格） */
    @Nullable
    public static Entity pillarProtecting(Entity target) {
        if (target == null || !isAvailable()) return null;
        return nearest(target.level(), target.getX(), target.getY(), target.getZ(), 8.0,
                e -> isPillar(e) && e.isAlive());
    }

    /** 黑曜石柱周围的邪教徒（6 格，取最近） */
    @Nullable
    public static LivingEntity cultistNearPillar(Entity pillar) {
        if (pillar == null || !isAvailable()) return null;
        Entity c = nearest(pillar.level(), pillar.getX(), pillar.getY(), pillar.getZ(), 6.0,
                e -> e instanceof LivingEntity le && isCultist(le) && le.isAlive());
        return c instanceof LivingEntity le ? le : null;
    }

    /** 以 center 为中心 radius 内最近的受限 Boss */
    @Nullable
    public static LivingEntity nearestLimitedBoss(LivingEntity center, double radius) {
        if (center == null || !isAvailable()) return null;
        Entity b = nearest(center.level(), center.getX(), center.getY(), center.getZ(), radius,
                e -> e instanceof LivingEntity le && isDamageLimitedBoss(le) && le.isAlive());
        return b instanceof LivingEntity le ? le : null;
    }

    /** 以 center 为中心 radius 内最近的邪教徒 */
    @Nullable
    public static LivingEntity nearestCultist(LivingEntity center, double radius) {
        if (center == null || !isAvailable()) return null;
        Entity c = nearest(center.level(), center.getX(), center.getY(), center.getZ(), radius,
                e -> e instanceof LivingEntity le && isCultist(le) && le.isAlive());
        return c instanceof LivingEntity le ? le : null;
    }

    /** 以 center 为中心 radius 内最近的黑曜石柱（LivingEntity 才可由墨默近战攻击） */
    @Nullable
    public static LivingEntity nearestPillar(LivingEntity center, double radius) {
        if (center == null || !isAvailable()) return null;
        Entity p = nearest(center.level(), center.getX(), center.getY(), center.getZ(), radius,
                e -> e instanceof LivingEntity le && isPillar(le) && le.isAlive());
        return p instanceof LivingEntity le ? le : null;
    }

    // =====================================================================
    //  反射通道（对 Goety/Revelation 类做可选访问，全部静默兜底）
    // =====================================================================

    private static boolean refResolved = false;
    private static Class<?> apostleClass;          // com.Polarice3.Goety.common.entities.boss.Apostle
    private static Field obsidianInvulField;       // public int obsidianInvul（黑曜石柱免伤）
    private static Field moddedInvulField;         // public int moddedInvul（受击后无敌帧）
    private static Class<?> mobsConfigClass;       // com.Polarice3.Goety.config.MobsConfig
    private static Field netherReductionField;     // ConfigValue<Integer> ApostleNetherDamageReduction（下界减伤 %）
    private static Class<?> ownedIface;            // com.Polarice3.Goety.common.entities.neutral.Owned（仆从归属）
    private static Method getTrueOwnerMethod;      // Owned.getTrueOwner()
    private static Class<?> apollyonHelperIface;   // z1gned.goetyrevelation.util.ApollyonAbilityHelper（mixin 注入使徒）
    private static Method setHitCooldownMethod;    // allTitlesApostle_1_20_1$setHitCooldown(int)
    private static Method isApollyonMethod;        // allTitlesApostle_1_20_1$isApollyon()
    private static Method isShootingMethod;        // allTitlesApostle_1_20_1$isShooting()（下界箭雨施放中）

    private static void resolveReflection() {
        resolve(); // 确保 goetyPresent 先判定，否则可能提前退出导致永不解析
        if (refResolved) return;
        refResolved = true;
        try {
            if (!goetyPresent) return;
            try {
                apostleClass = Class.forName("com.Polarice3.Goety.common.entities.boss.Apostle");
                obsidianInvulField = fieldOf(apostleClass, "obsidianInvul");
                moddedInvulField = fieldOf(apostleClass, "moddedInvul");
            } catch (Throwable ignored) {
                // 主 Goety 缺失或字段改名：以下全部降级为 no-op
            }
            try {
                mobsConfigClass = Class.forName("com.Polarice3.Goety.config.MobsConfig");
                netherReductionField = fieldOf(mobsConfigClass, "ApostleNetherDamageReduction");
            } catch (Throwable ignored) {
                // 配置字段读不到：下界减伤按默认 50 兜底
            }
            try {
                ownedIface = Class.forName("com.Polarice3.Goety.common.entities.neutral.Owned");
                getTrueOwnerMethod = methodOf(ownedIface, "getTrueOwner");
            } catch (Throwable ignored) {
                // 归属接口读不到：碎柱时退化为按距离判定
            }
            try {
                apollyonHelperIface = Class.forName("z1gned.goetyrevelation.util.ApollyonAbilityHelper");
                setHitCooldownMethod = methodOf(apollyonHelperIface, "allTitlesApostle_1_20_1$setHitCooldown", int.class);
                isApollyonMethod = methodOf(apollyonHelperIface, "allTitlesApostle_1_20_1$isApollyon");
                isShootingMethod = methodOf(apollyonHelperIface, "allTitlesApostle_1_20_1$isShooting");
            } catch (Throwable ignored) {
                // 启示录缺失：下界受击冷却相关 no-op
            }
            LOGGER.info("[GoetyBridge] 反射通道: apostleClass=" + (apostleClass != null)
                    + " obsidianInvulField=" + (obsidianInvulField != null)
                    + " moddedInvulField=" + (moddedInvulField != null)
                    + " netherReductionField=" + (netherReductionField != null)
                    + " ownedIface=" + (ownedIface != null)
                    + " apollyonHelperIface=" + (apollyonHelperIface != null)
                    + " hitCooldown=" + (setHitCooldownMethod != null)
                    + " isShooting=" + (isShootingMethod != null));
        } catch (Throwable ignored) {
        }
    }

    /** 读使徒 moddedInvul 当前值（诊断用） */
    public static int readModdedInvul(LivingEntity e) {
        resolveReflection();
        if (!isGoetyApostle(e)) return -1;
        try {
            return moddedInvulField.getInt(e);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    // =====================================================================
    //  使徒禁疗（antiRegen）：使徒在部分称号阶段高速再生（heal 每次 2.5% 最大生命），
    //  正常打法是"亡灵杀手"附魔触发 antiRegen；我们的穿透不走普通攻击，需手动挂上
    // =====================================================================

    private static java.lang.reflect.Field antiRegenField = null;
    private static java.lang.reflect.Field antiRegenTotalField = null;

    /** 使徒禁疗 10 秒（antiRegen>0 → isSmited() → 其 heal() 被整体禁用，含负数 heal 与回血效果） */
    public static void suppressApostleRegen(LivingEntity e) {
        if (!isGoetyApostle(e)) return;
        try {
            if (antiRegenField == null) {
                antiRegenField = fieldOf(apostleClass, "antiRegen");
                antiRegenTotalField = fieldOf(apostleClass, "antiRegenTotal");
            }
            if (antiRegenField != null) {
                if (antiRegenField.getInt(e) < 200) {
                    antiRegenField.setInt(e, 200);
                    if (antiRegenTotalField != null) antiRegenTotalField.setInt(e, 200);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // =====================================================================
    //  天逆鉾"真伤"伤害类型：带 bypasses_cooldown 等全部豁免 tag（数据包 data/minecraft/tags/damage_type/），
    //  绕过原版 1.20.1 伤害冷却闸门（LivingEntity.hurt 内 f_19802_>10 且伤害≤上次伤害则 return false）
    // =====================================================================

    private static net.minecraft.core.Holder<net.minecraft.world.damagesource.DamageType> TRUE_PIERCE_HOLDER = null;

    /** 获取 tinkersnewlife:true_pierce 伤害类型的 Holder；数据包缺失时返回 null（调用方回退 genericKill） */
    @Nullable
    public static net.minecraft.world.damagesource.DamageSource truePierceSource(Level level) {
        try {
            if (!(level instanceof net.minecraft.server.level.ServerLevel sl)) return null;
            if (TRUE_PIERCE_HOLDER == null || !TRUE_PIERCE_HOLDER.isBound()) {
                var reg = sl.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
                TRUE_PIERCE_HOLDER = reg.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                        new net.minecraft.resources.ResourceLocation("tinkersnewlife", "true_pierce")));
            }
            if (TRUE_PIERCE_HOLDER != null && TRUE_PIERCE_HOLDER.isBound()) {
                return new net.minecraft.world.damagesource.DamageSource(TRUE_PIERCE_HOLDER, null, null);
            }
        } catch (Throwable t) {
            LOGGER.warn("[GoetyBridge] truePierceSource 获取失败，回退 genericKill", t);
        }
        return null;
    }

    /** 直接写启示录注入使徒的 SynchedEntityData HIT_COOLDOWN（绕过接口层，直击 canHurt 读取的存储） */
    private static java.lang.reflect.Field HIT_COOLDOWN_ACCESSOR_FIELD = null;

    public static void clearApollyonCooldownDirect(LivingEntity e) {
        if (!isGoetyApostle(e)) return;
        try {
            if (HIT_COOLDOWN_ACCESSOR_FIELD == null) {
                for (String name : new String[]{"allTitlesApostle_1_20_1$HIT_COOLDOWN", "HIT_COOLDOWN"}) {
                    try {
                        HIT_COOLDOWN_ACCESSOR_FIELD = apostleClass.getDeclaredField(name);
                        HIT_COOLDOWN_ACCESSOR_FIELD.setAccessible(true);
                        break;
                    } catch (NoSuchFieldException ignored) {
                    }
                }
            }
            if (HIT_COOLDOWN_ACCESSOR_FIELD != null) {
                Object acc = HIT_COOLDOWN_ACCESSOR_FIELD.get(null);
                if (acc instanceof net.minecraft.network.syncher.EntityDataAccessor<?> eda) {
                    // noinspection unchecked
                    e.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor<Integer>) eda, 0);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 对受限 Boss 的"全额穿透"结算（墨默/天逆鉾共用）：
     * ① 下界亚波伦（启示录 30t 免疫窗/单次 20 上限）：走破限直伤——不经 hurt()/actuallyHurt()，
     *    直接 setHealth 扣血、血量打空主动 die()（梦幻终焉湮灭射线同思路，见 isNetherApollyon 分支）；
     * ② 其余受限 Boss：多段 ≤19 的 hurt()（带 true_pierce 类型）——第一段触发完整受击事件/阶段逻辑；
     * ③ 若被各类免伤窗（原版伤害冷却等）吞掉部分，按"应打-已打"差额直接 setHealth 补齐；
     * ④ 差额致死时补 die() 死亡流程（掉落/击败逻辑正常）。
     */
    public static void pierceFullDamage(LivingEntity target, float dmg) {
        if (target == null || target.level().isClientSide || target.isRemoved()) return;
        suppressApostleRegen(target); // 先禁疗：否则其再生会吃掉我们的伤害
        // 清障：若目标带我们自己的"伤害限幅"效果(0.5s/30 上限、超限 setCanceled)会吞掉多段伤害
        try {
            var dl = com.mofengbaizhi.tinkersnewlife.content.ModEffects.DAMAGE_LIMIT.get();
            if (dl != null && target.hasEffect(dl)) {
                target.removeEffect(dl);
            }
        } catch (Throwable ignored) {
        }
        net.minecraft.world.damagesource.DamageSource src = truePierceSource(target.level());
        if (src == null) src = target.damageSources().genericKill();

        // ⭐ 下界亚波伦破限直伤（梦幻终焉「湮灭射线」同思路，墨默/天逆鉾共用）：
        // 启示录的 canHurt 取消 / 单次 20 上限 / 30t 免疫窗全部挂靠在 hurt()/actuallyHurt() 管线，
        // 这里完全不调用它们——直接 setHealth 扣血，血量打空后主动 die() 让击败状态机正常结算。
        if (isNetherApollyon(target)) {
            target.invulnerableTime = 0;
            float hp = target.getHealth() - dmg;
            if (hp <= 0.0F) {
                target.setHealth(0.0F);
                if (target.isAlive() && !target.isRemoved()) {
                    target.die(src);
                }
            } else {
                target.setHealth(hp);
            }
            return;
        }

        float startHp = target.getHealth();
        float remaining = dmg;
        int guard = 0;
        while (remaining > 0 && target.isAlive() && !target.isRemoved() && guard++ < 64) {
            float part = Math.min(remaining, 19.0F);
            target.invulnerableTime = 0;
            target.hurt(src, part);
            remaining -= part;
        }
        // 差额补齐（免伤窗吞掉的部分直接按血量差补上）
        if (target.isAlive() && !target.isRemoved() && target.getHealth() > 0.0F) {
            float dealt = startHp - target.getHealth();
            float shortfall = dmg - Math.max(dealt, 0.0F);
            if (shortfall > 0.5F) {
                float hp = target.getHealth() - shortfall;
                if (hp <= 0.0F) {
                    target.setHealth(0.0F);
                    target.die(src);
                } else {
                    target.setHealth(hp);
                }
            } else if (shortfall < -0.5F && target.getHealth() > 0.0F) {
                target.setHealth(Math.min(target.getMaxHealth(), target.getHealth() - shortfall));
            }
        }
        // 处决兜底：Boss 已被打到低血量阶段（≤18% 血）且本次伤害被完全免疫吞掉（已打≈0）
        // → 不灭重生/万众归一/末日终结等称号的"受击全额回血"免伤阶段，直接执行死亡流程
        if (isGoetyApostle(target) && target.isAlive() && !target.isRemoved()
                && target.getHealth() <= target.getMaxHealth() * 0.18F) {
            float dealt = startHp - target.getHealth();
            if (dealt < 5.0F) {
                target.setHealth(0.0F);
                if (!target.isRemoved()) {
                    target.die(src);
                }
            }
        }
    }

    private static Field fieldOf(Class<?> clazz, String name) {
        try {
            Field f = clazz.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Throwable t) {
            try {
                Field f = clazz.getField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static Method methodOf(Class<?> clazz, String name, Class<?>... params) {
        try {
            Method m = clazz.getMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 目标是否为 Goety 的使徒（Apostle，含启示录 Apollyon 状态） */
    public static boolean isGoetyApostle(LivingEntity e) {
        resolveReflection();
        return e != null && apostleClass != null && apostleClass.isAssignableFrom(e.getClass());
    }

    /** 读使徒当前黑曜石柱免伤计时（>0 表示柱保护中，全程免伤）；非使徒/未装 Goety 返回 0 */
    public static int readObsidianInvul(LivingEntity e) {
        if (!isGoetyApostle(e)) return 0;
        try {
            return obsidianInvulField.getInt(e);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** 写使徒黑曜石柱免伤计时（天逆鉾破保护用：打前清零、打完还原） */
    public static void setObsidianInvul(LivingEntity e, int v) {
        if (!isGoetyApostle(e)) return;
        try {
            obsidianInvulField.setInt(e, v);
        } catch (Throwable ignored) {
        }
    }

    /** 清零使徒受击无敌帧（其他带直接实体的攻击留下的 moddedInvul=15 会挡我们的多段伤害） */
    public static void clearModdedInvul(LivingEntity e) {
        if (!isGoetyApostle(e)) return;
        try {
            moddedInvulField.setInt(e, 0);
        } catch (Throwable ignored) {
        }
    }

    /** 清零启示录下界 Apollyon 的受击冷却（30tick 免疫窗），使多段伤害能连续命中 */
    public static void clearApollyonHitCooldown(LivingEntity e) {
        resolveReflection();
        if (e == null || apollyonHelperIface == null) return;
        try {
            if (apollyonHelperIface.isInstance(e) && setHitCooldownMethod != null) {
                if (clearLogCount < 10) {
                    int before = readApollyonHitCooldown(e);
                    setHitCooldownMethod.invoke(e, 0);
                    int after = readApollyonHitCooldown(e);
                    LOGGER.info("[GoetyBridge] 冷却清零 {}→{} (isInstance={} method={})", before, after,
                            apollyonHelperIface.isInstance(e), setHitCooldownMethod != null);
                    clearLogCount++;
                } else {
                    setHitCooldownMethod.invoke(e, 0);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[GoetyBridge] 冷却清零异常", t);
        }
    }

    private static int clearLogCount = 0;

    /** 读启示录 Apollyon 受击冷却当前值（诊断用） */
    public static int readApollyonHitCooldown(LivingEntity e) {
        resolveReflection();
        if (e == null || apollyonHelperIface == null) return -1;
        try {
            if (!apollyonHelperIface.isInstance(e)) return -1;
            Method m = methodOf(apollyonHelperIface, "allTitlesApostle_1_20_1$getHitCooldown");
            if (m == null) return -1;
            Object v = m.invoke(e);
            return v instanceof Integer i ? i : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /** 目标当前是否处于启示录的 Apollyon 状态（使徒 + isApollyon 标志） */
    public static boolean isApollyonState(LivingEntity e) {
        resolveReflection();
        if (e == null || apollyonHelperIface == null || isApollyonMethod == null) return false;
        try {
            if (!apollyonHelperIface.isInstance(e)) return false;
            return Boolean.TRUE.equals(isApollyonMethod.invoke(e));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 是否下界亚波伦（下界维度 + Apollyon 状态）——启示录 30t 免疫窗/限伤只对它生效 */
    public static boolean isNetherApollyon(LivingEntity e) {
        if (e == null || e.level() == null || e.level().isClientSide) return false;
        if (e.level().dimension() != net.minecraft.world.level.Level.NETHER) return false;
        return isApollyonState(e);
    }

    /**
     * 目标是否正处于启示录 Apollyon 的"死亡箭雨"施放中（isShooting）：
     * 仅下界 + Apollyon 状态触发，施放期约 100 tick，期间每 tick 射一支 DeathArrow，
     * 每支命中再扣目标 5% 最大生命的虚空伤害（heal(-)，无视护甲/无敌帧/格挡）。
     */
    public static boolean isApollyonBarraging(LivingEntity e) {
        resolveReflection();
        if (e == null || apollyonHelperIface == null || isShootingMethod == null) return false;
        try {
            if (!apollyonHelperIface.isInstance(e)) return false;
            return Boolean.TRUE.equals(isShootingMethod.invoke(e));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 诊断：描述目标在 Goety 眼中的身份（类型/受限Boss/使徒类/柱保护/启示录状态/箭雨） */
    public static String debugDescribe(LivingEntity e) {
        resolveReflection();
        if (e == null) return "null";
        String type = ForgeRegistries.ENTITY_TYPES.getKey(e.getType()) != null
                ? ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).toString() : e.getType().toString();
        return "type=" + type
                + " limited=" + isDamageLimitedBoss(e)
                + " goetyApostle=" + isGoetyApostle(e)
                + " obsidianInvul=" + readObsidianInvul(e)
                + " apollyon=" + isApollyonState(e)
                + " barraging=" + isApollyonBarraging(e);
    }

    // =====================================================================
    //  使徒隐藏减伤的补偿（让墨默/天逆鉾的穿透打出"名义伤害"）
    // =====================================================================

    /** 读取主诡厄下界减伤配置（0-100），读不到回退默认 50（与游戏默认一致） */
    public static int readApostleNetherReduction() {
        resolveReflection();
        if (netherReductionField == null) return 50;
        try {
            Object configValue = netherReductionField.get(null);
            if (configValue instanceof net.minecraftforge.common.ForgeConfigSpec.ConfigValue<?> cv) {
                Object v = cv.get();
                if (v instanceof Integer i) {
                    return Math.max(0, Math.min(100, i));
                }
            }
        } catch (Throwable ignored) {
        }
        return 50;
    }

    /** 附近 32 格内是否有非创造/非旁观玩家（主诡厄 Apostle.hurt 会因此把非玩家直接实体的伤害减半） */
    private static boolean nearbyNonCreativePlayer(LivingEntity target) {
        if (target.level() == null || target.level().isClientSide) return false;
        net.minecraft.world.phys.AABB box = target.getBoundingBox().inflate(32.0);
        for (net.minecraft.world.entity.player.Player p : target.level().players()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative()) continue;
            if (box.intersects(p.getBoundingBox())) return true;
        }
        return false;
    }

    /**
     * 使徒两道"减伤"的补偿倍数（仅对受限 Boss 的多段穿透有意义）：
     * ① 下界减伤 apostleNetherDamageReduction（默认 50%）：Apostle.hurt() 对下界所有伤害折半；
     * ② 附近 32 格有非创造/旁观玩家 且 伤害直接实体非玩家 → 再减半
     *    （genericKill 无直接实体，墨默/天逆鉾都会命中这条）。
     * 返回 &gt;1 表示我方多送该倍数的伤害、落血仍是名义值；无需补偿时返回 1。
     * 下界减伤配置为 100（设计上免伤）时返回 1、不做补偿（也补不动）。
     */
    public static float apostleDamageCompensation(LivingEntity target) {
        if (!isGoetyApostle(target)) return 1.0F;
        float factor = 1.0F;
        if (target.level() != null
                && target.level().dimension() == net.minecraft.world.level.Level.NETHER) {
            int r = readApostleNetherReduction();
            if (r >= 100) return 1.0F;
            if (r > 0) factor /= (1.0F - r / 100.0F);
        }
        if (nearbyNonCreativePlayer(target)) {
            factor *= 2.0F;
        }
        return factor;
    }

    // =====================================================================
    //  天逆鉾"碎柱"：穿透黑曜石柱保护时把柱直接打碎
    // =====================================================================

    /** 柱子是否归属于指定 Boss（读不到 Owned 接口时按距离放行） */
    private static boolean pillarOwnedBy(LivingEntity pillar, LivingEntity boss) {
        resolveReflection();
        if (ownedIface == null || getTrueOwnerMethod == null) return true;
        try {
            if (!ownedIface.isInstance(pillar)) return false;
            Object owner = getTrueOwnerMethod.invoke(pillar);
            return owner == boss;
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * 把 boss 周围 16 格内、归属于它的存活黑曜石柱全部"击碎"：
     * 用 ≤19 的多段 genericKill 正常打到死——柱子死亡走诡厄自身流程
     * （silentDie：黑曜碎裂粒子/音效 + 使徒 monolithCoolDown=1 分钟，短时间召不出新柱）。
     * 返回是否至少碎掉了一根（调用方据此决定是否还原 boss 的 obsidianInvul）。
     */
    /**
     * 把 boss 周围 searchRadius 格内、归属于它的存活黑曜石柱全部"击碎"：
     * 用 ≤19 的多段 genericKill 正常打到死——柱子死亡走诡厄自身流程
     * （silentDie：黑曜碎裂粒子/音效 + 使徒 monolithCoolDown=1 分钟，短时间召不出新柱）。
     * 注意：柱由使徒在 12~24 格外召唤、且可能因场地狭窄长期不瞬移贴身，
     * 所以搜索半径要够大（实测柱常在 16 格外仍持续给 Boss 免伤）。
     * 返回是否至少碎掉了一根（调用方据此决定是否还原 boss 的 obsidianInvul）。
     */
    public static boolean shatterProtectingPillars(LivingEntity boss) {
        return shatterProtectingPillars(boss, 64.0);
    }

    public static boolean shatterProtectingPillars(LivingEntity boss, double searchRadius) {
        if (!isAvailable() || boss == null || boss.level() == null || boss.level().isClientSide) {
            return false;
        }
        boolean any = false;
        int outer = 0;
        while (outer++ < 8) {
            Entity p = nearest(boss.level(), boss.getX(), boss.getY(), boss.getZ(), searchRadius,
                    e -> e instanceof LivingEntity le && isPillar(le) && le.isAlive()
                            && pillarOwnedBy(le, boss));
            if (!(p instanceof LivingEntity pillar)) break;
            any = true;
            int guard = 0;
            while (pillar.isAlive() && !pillar.isRemoved() && guard++ < 64) {
                pillar.invulnerableTime = 0;
                pillar.hurt(pillar.damageSources().genericKill(), 19.0F);
            }
        }
        return any;
    }
}
