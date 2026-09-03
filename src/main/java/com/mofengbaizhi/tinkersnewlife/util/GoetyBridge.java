package com.mofengbaizhi.tinkersnewlife.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
 * 辅助墨默"破保护链"：邪教徒 → 黑曜石柱 → 目标，并对受限 Boss 使用绕过伤害。
 */
public final class GoetyBridge {

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
            if (!goetyPresent) return;
            for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES) {
                String path = ForgeRegistries.ENTITY_TYPES.getKey(type).getPath().toLowerCase();
                if (path.contains("apollyon") || path.contains("apostle")) {
                    BOSS_TYPES.add(type);
                } else if (path.contains("pillar") || path.contains("obsidian")) {
                    PILLAR_TYPES.add(type);
                } else if (path.contains("cultist") || path.contains("sect")) {
                    CULTIST_TYPES.add(type);
                }
            }
        } catch (Throwable ignored) {
            goetyPresent = false;
        }
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
}
