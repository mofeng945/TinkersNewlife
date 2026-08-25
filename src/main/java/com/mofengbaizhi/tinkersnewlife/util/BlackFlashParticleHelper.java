package com.mofengbaizhi.tinkersnewlife.util;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class BlackFlashParticleHelper {

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "BlackFlashParticle");
        t.setDaemon(true);
        return t;
    });

    private BlackFlashParticleHelper() {}

    /**
     * 播放黑闪粒子特效（扇形红黑闪电版）
     */
    public static void spawnBlackFlash(ServerLevel level, Vec3 pos, Vec3 direction) {
        double x = pos.x;
        double y = pos.y + 0.5;
        double z = pos.z;

        // ===== 第 1 波：瞬间爆发（主线程直接执行） =====
        level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);

        for (int i = 0; i < 80; i++) {
            double dx = (level.random.nextDouble() - 0.5) * 2.5;
            double dy = (level.random.nextDouble() - 0.5) * 2.5;
            double dz = (level.random.nextDouble() - 0.5) * 2.5;
            level.sendParticles(ParticleTypes.SMOKE,
                    x + dx, y + dy, z + dz,
                    1, 0.05, 0.05, 0.05, 0.03);
        }

        // ===== 第 2 波：扇形闪电迸发（主线程直接执行） =====
        double spreadAngle = Math.PI / 3;
        int rayCount = 40;

        for (int i = 0; i < rayCount; i++) {
            double thetaOffset = (level.random.nextDouble() - 0.5) * 2 * spreadAngle;
            double phiOffset = (level.random.nextDouble() - 0.5) * 0.8;

            Vec3 dir = rotateDirection(direction, thetaOffset, phiOffset);

            int segments = 5 + level.random.nextInt(4);
            double maxLength = 0.8 + level.random.nextDouble() * 2.0;
            double segLength = maxLength / segments;

            double px = x;
            double py = y;
            double pz = z;

            for (int j = 0; j < segments; j++) {
                double jitter = 0.2 + level.random.nextDouble() * 0.4;
                double nextX = px + dir.x * segLength + (level.random.nextDouble() - 0.5) * jitter;
                double nextY = py + dir.y * segLength + (level.random.nextDouble() - 0.5) * jitter;
                double nextZ = pz + dir.z * segLength + (level.random.nextDouble() - 0.5) * jitter;

                if (j % 3 == 0) {
                    level.sendParticles(ParticleTypes.DRAGON_BREATH, nextX, nextY, nextZ, 1, 0, 0, 0, 0);
                } else if (j % 3 == 1) {
                    level.sendParticles(ParticleTypes.SMOKE, nextX, nextY, nextZ, 1, 0.02, 0.02, 0.02, 0.01);
                } else {
                    level.sendParticles(ParticleTypes.SOUL, nextX, nextY, nextZ, 1, 0.01, 0.01, 0.01, 0.01);
                }

                if (j == 0 || j == segments - 1 || j % 2 == 0) {
                    level.sendParticles(ParticleTypes.END_ROD, nextX, nextY, nextZ, 1, 0, 0, 0, 0);
                }

                if (j > 0 && j % 2 == 0 && level.random.nextDouble() < 0.3) {
                    for (int k = 0; k < 2; k++) {
                        double branchAngle = (level.random.nextDouble() - 0.5) * 1.2;
                        Vec3 branchDir = rotateDirection(dir, branchAngle, 0);
                        double bx = nextX + branchDir.x * 0.4;
                        double by = nextY + branchDir.y * 0.4;
                        double bz = nextZ + branchDir.z * 0.4;
                        level.sendParticles(ParticleTypes.DRAGON_BREATH, bx, by, bz, 1, 0, 0, 0, 0);
                        level.sendParticles(ParticleTypes.SMOKE, bx, by, bz, 1, 0.02, 0.02, 0.02, 0.01);
                    }
                }

                px = nextX;
                py = nextY;
                pz = nextZ;
            }
        }

        // ===== 第 3~5 波：延迟任务（通过 server.execute 回调主线程） =====

        // 第 3 波：内圈爆裂（延迟 50ms）
        EXECUTOR.schedule(() -> {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            server.execute(() -> {
                if (level.isClientSide) return;
                for (int i = 0; i < 80; i++) {
                    double dx = (level.random.nextDouble() - 0.5) * 2.0;
                    double dy = (level.random.nextDouble() - 0.5) * 2.0;
                    double dz = (level.random.nextDouble() - 0.5) * 2.0;
                    level.sendParticles(ParticleTypes.SMOKE, x + dx, y + dy, z + dz, 1, 0.1, 0.1, 0.1, 0.05);
                    if (i % 5 == 0) {
                        level.sendParticles(ParticleTypes.END_ROD, x + dx, y + dy, z + dz, 1, 0, 0, 0, 0);
                    }
                    if (i % 7 == 0) {
                        level.sendParticles(ParticleTypes.SOUL, x + dx * 0.5, y + dy * 0.5, z + dz * 0.5, 1, 0, 0, 0, 0);
                    }
                }
            });
        }, 50, TimeUnit.MILLISECONDS);

        // 第 4 波：消散余烬（延迟 250ms）
        EXECUTOR.schedule(() -> {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            server.execute(() -> {
                if (level.isClientSide) return;
                for (int i = 0; i < 40; i++) {
                    double dx = (level.random.nextDouble() - 0.5) * 3.5;
                    double dz = (level.random.nextDouble() - 0.5) * 3.5;
                    double dy = level.random.nextDouble() * 1.5;
                    level.sendParticles(ParticleTypes.SMOKE, x + dx, y + dy, z + dz, 1, 0.15, 0.15, 0.15, 0.08);
                    if (i % 4 == 0) {
                        level.sendParticles(ParticleTypes.SOUL, x + dx * 0.6, y + dy * 0.6, z + dz * 0.6, 1, 0, 0.02, 0, 0.01);
                    }
                }
            });
        }, 250, TimeUnit.MILLISECONDS);

        // 第 5 波：第二道闪（延迟 100ms）
        EXECUTOR.schedule(() -> {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            server.execute(() -> {
                if (level.isClientSide) return;
                level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
            });
        }, 100, TimeUnit.MILLISECONDS);
    }

    private static Vec3 rotateDirection(Vec3 dir, double theta, double phi) {
        Vec3 normalized = dir.normalize();

        double cosT = Math.cos(theta);
        double sinT = Math.sin(theta);
        double x = normalized.x * cosT + normalized.z * sinT;
        double z = -normalized.x * sinT + normalized.z * cosT;
        double y = normalized.y;

        Vec3 horizontal = new Vec3(x, 0, z).normalize();
        if (horizontal.lengthSqr() < 0.0001) {
            horizontal = new Vec3(0, 0, 1);
        }
        Vec3 axis = new Vec3(0, 1, 0).cross(horizontal).normalize();
        if (axis.lengthSqr() < 0.0001) {
            axis = new Vec3(1, 0, 0);
        }

        Vec3 result = normalized.scale(Math.cos(phi))
                .add(axis.cross(normalized).scale(Math.sin(phi)))
                .add(axis.scale(axis.dot(normalized)).scale(1 - Math.cos(phi)));

        return result.normalize();
    }
}