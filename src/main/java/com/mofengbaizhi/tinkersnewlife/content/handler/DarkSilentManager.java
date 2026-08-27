package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.content.item.DurandalSwordItem;
import com.mofengbaizhi.tinkersnewlife.content.item.SilentGloveItem;
import com.mofengbaizhi.tinkersnewlife.content.storage.SilentGloveHandler;
import com.mofengbaizhi.tinkersnewlife.util.GloveHelper;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.theillusivec4.curios.api.CuriosApi;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DarkSilentManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DarkSilentManager.class);
    private static final Set<UUID> DARK_SILENT_ACTIVE = ConcurrentHashMap.newKeySet();

    public static boolean isActive(UUID uuid) {
        return DARK_SILENT_ACTIVE.contains(uuid);
    }

    public static void checkAndTriggerOnStored(Player player, ItemStack stack) {
        if (player.level().isClientSide) return;
        if (!(stack.getItem() instanceof DurandalSwordItem)) return;
        if (isActive(player.getUUID())) return;

        int count = DurandalSwordItem.getHitCount(stack);
        if (count >= 9 && isWearingGlove(player)) {
            DurandalSwordItem.setHitCount(stack, 0);
            LOGGER.debug("[漆黑噤默] 玩家 {} 触发大招", player.getName().getString());
            triggerDarkSilent(player);
        }
    }

    public static void countHit(Player player, ItemStack usedWeapon) {
        if (player.level().isClientSide) return;
        if (!(usedWeapon.getItem() instanceof DurandalSwordItem)) return;
        if (isActive(player.getUUID())) return;

        int count = DurandalSwordItem.getHitCount(usedWeapon);
        if (count >= 9) return;
        DurandalSwordItem.setHitCount(usedWeapon, count + 1);
        LOGGER.debug("[杜兰达尔] 玩家 {} 计数: {}", player.getName().getString(), count + 1);
    }

    public static void removePlayer(UUID uuid) {
        DARK_SILENT_ACTIVE.remove(uuid);
    }

    public static float getWeaponDamage(Player player, ItemStack weapon) {
        if (weapon.isEmpty()) return 1.0f;
        if (weapon.getItem() instanceof ModifiableItem) {
            // ✅ 使用 ToolHelper 安全获取，避免 "non-modifiable tool" 警告
            ToolStack tool = ToolHelper.getToolStack(weapon);
            if (tool != null && !tool.isBroken()) {
                return tool.getStats().get(ToolStats.ATTACK_DAMAGE);
            }
        }
        AttributeInstance attr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attr != null) {
            return (float) attr.getValue();
        }
        return 1.0f;
    }

    // ===================== 大招执行 =====================

    private static void triggerDarkSilent(Player player) {
        if (player.level().isClientSide) return;
        UUID uuid = player.getUUID();
        if (isActive(uuid)) {
            LOGGER.warn("[漆黑噤默] 玩家 {} 已有大招正在执行，跳过", player.getName().getString());
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && !(mainHand.getItem() instanceof SilentGloveItem)) {
            boolean stored = GloveWeaponStorage.tryStoreInVault(player, mainHand);
            if (stored) {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            } else {
                ItemStack remaining = ItemHandlerHelper.insertItemStacked(
                        new InvWrapper(player.getInventory()),
                        mainHand, false
                );
                if (remaining.isEmpty()) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                } else {
                    if (player instanceof ServerPlayer sp) {
                        sp.connection.send(
                                new ClientboundSetActionBarTextPacket(
                                        Component.translatable("message.tinkersnewlife.dark_silent.no_space")
                                                .withStyle(ChatFormatting.RED)
                                )
                        );
                    }
                    LOGGER.warn("[漆黑噤默] 玩家 {} 背包已满，大招取消", player.getName().getString());
                    return;
                }
            }
        }

        DARK_SILENT_ACTIVE.add(uuid);
        try {
            // ⭐ 统一查找佩戴的手套（GloveHelper）
            ItemStack gloveStack = GloveHelper.findWornGlove(player);
            if (gloveStack.isEmpty()) {
                DARK_SILENT_ACTIVE.remove(uuid);
                return;
            }
            SilentGloveHandler vault = SilentGloveItem.getHandler(gloveStack);
            if (vault == null) {
                DARK_SILENT_ACTIVE.remove(uuid);
                return;
            }

            List<WeaponEntry> weaponEntries = new ArrayList<>();
            for (int i = 0; i < vault.getSlots(); i++) {
                ItemStack stack = vault.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    weaponEntries.add(new WeaponEntry(i, stack));
                }
            }
            if (weaponEntries.isEmpty()) {
                LOGGER.warn("[漆黑噤默] 玩家 {} 手套中没有武器，静默取消", player.getName().getString());
                DARK_SILENT_ACTIVE.remove(uuid);
                return;
            }

            if (player instanceof ServerPlayer sp) {
                Component actionBar = Component.literal("#")
                        .withStyle(ChatFormatting.OBFUSCATED, ChatFormatting.RED)
                        .append(Component.translatable("action.tinkersnewlife.dark_silent.furioso")
                                .withStyle(ChatFormatting.RED))
                        .append(Component.literal("*")
                                .withStyle(ChatFormatting.OBFUSCATED, ChatFormatting.RED));
                sp.connection.send(new ClientboundSetActionBarTextPacket(actionBar));
            }

            LOGGER.debug("[漆黑噤默] 玩家 {} 开始执行，共 {} 把武器", player.getName().getString(), weaponEntries.size());

            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "DarkSilentAttack");
                t.setDaemon(true);
                return t;
            });
            final int totalAttacks = weaponEntries.size();
            final int[] attackIndex = {0};

            executor.scheduleAtFixedRate(() -> {
                // ⭐ 服务器可能已关闭（getCurrentServer() 返回 null），此时直接结束，避免 NPE 且状态残留
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server == null) {
                    executor.shutdown();
                    DARK_SILENT_ACTIVE.remove(uuid);
                    return;
                }
                server.execute(() -> {
                    try {
                        if (player.isRemoved() || !player.isAlive()) {
                            executor.shutdown();
                            DARK_SILENT_ACTIVE.remove(uuid);
                            return;
                        }
                        if (attackIndex[0] >= totalAttacks) {
                            executor.shutdown();
                            DARK_SILENT_ACTIVE.remove(uuid);
                            LOGGER.debug("[漆黑噤默] 玩家 {} 大招完成", player.getName().getString());
                            return;
                        }

                        WeaponEntry entry = weaponEntries.get(attackIndex[0]);
                        int slot = entry.slot;
                        ItemStack weapon = vault.getStackInSlot(slot);
                        if (weapon.isEmpty()) {
                            attackIndex[0]++;
                            return;
                        }

                        LivingEntity target = findTarget(player);
                        if (target == null) {
                            LOGGER.warn("[漆黑噤默] 玩家 {} 周围 24 格内没有可攻击目标", player.getName().getString());
                            attackIndex[0]++;
                            return;
                        }

                        Vec3 behind = findBestBehindPosition(target, player);
                        if (behind == null) {
                            LOGGER.warn("[漆黑噤默] 玩家 {} 找不到目标 {} 身后的可用位置", player.getName().getString(), target.getName().getString());
                            attackIndex[0]++;
                            return;
                        }

                        player.teleportTo(behind.x, behind.y, behind.z);
                        float yaw = (float) Math.toDegrees(Math.atan2(target.getX() - player.getX(), target.getZ() - player.getZ()));
                        player.setYRot(yaw);
                        player.yHeadRot = yaw;

                        float damage = getWeaponDamage(player, weapon);
                        target.hurt(player.damageSources().playerAttack(player), damage);
                        player.swing(InteractionHand.MAIN_HAND);
                        // ★ 播放攻击音效
                        player.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 1.0f);

                        if (weapon.isDamageableItem()) {
                            weapon.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
                        }

                        LOGGER.debug("[漆黑噤默] 玩家 {} 对目标 {} 造成 {:.1f} 伤害", player.getName().getString(), target.getName().getString(), damage);
                        attackIndex[0]++;
                    } catch (Exception e) {
                        LOGGER.error("[漆黑噤默] 攻击任务异常", e);
                        executor.shutdown();
                        DARK_SILENT_ACTIVE.remove(uuid);
                    }
                });
            }, 0, 500, TimeUnit.MILLISECONDS);

            executor.schedule(() -> {
                if (!executor.isShutdown()) {
                    executor.shutdown();
                    DARK_SILENT_ACTIVE.remove(uuid);
                    LOGGER.warn("[漆黑噤默] 玩家 {} 大招超时强制结束", player.getName().getString());
                }
            }, totalAttacks * 500L + 2000, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            LOGGER.error("[漆黑噤默] 大招执行异常", e);
            DARK_SILENT_ACTIVE.remove(uuid);
        }
    }

    // ===================== 辅助数据结构 =====================

    private static class WeaponEntry {
        final int slot;
        final ItemStack stack;
        WeaponEntry(int slot, ItemStack stack) {
            this.slot = slot;
            this.stack = stack;
        }
    }

    // ===================== 目标与位置辅助 =====================

    private static LivingEntity findTarget(Player player) {
        Level level = player.level();
        AABB aabb = player.getBoundingBox().inflate(24.0);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, aabb,
                e -> e != player && e.isAlive() && !(e instanceof ArmorStand));
        if (entities.isEmpty()) return null;

        entities.sort(Comparator.comparingInt((LivingEntity e) -> {
                    if (e instanceof Monster) return 0;
                    if (e instanceof Mob mob && mob.getTarget() == player) return 0;
                    return 1;
                })
                .thenComparingDouble(e -> e.distanceToSqr(player)));
        return entities.get(0);
    }

    private static Vec3 findBestBehindPosition(LivingEntity target, Player player) {
        Vec3 lookVec = target.getLookAngle().normalize();
        Vec3 baseBehind = target.position().subtract(lookVec.scale(1.5));
        baseBehind = new Vec3(baseBehind.x, Math.max(target.getY(), baseBehind.y), baseBehind.z);

        if (isSafe(player, baseBehind)) return baseBehind;

        double[] offsets = {0, 0.5, -0.5, 1.0, -1.0, 1.5, -1.5};
        List<Vec3> candidates = new ArrayList<>();
        for (double dx : offsets) {
            for (double dz : offsets) {
                if (dx == 0 && dz == 0) continue;
                candidates.add(baseBehind.add(dx, 0, dz));
            }
        }
        for (double dy = 0.5; dy <= 2.0; dy += 0.5) {
            candidates.add(baseBehind.add(0, dy, 0));
        }

        final Vec3 base = baseBehind;
        candidates.sort(Comparator.comparingDouble(p -> p.distanceToSqr(base)));

        for (Vec3 pos : candidates) {
            if (isSafe(player, pos)) return pos;
        }

        LOGGER.warn("[漆黑噤默] 找不到目标 {} 身后的安全位置，将强制传送至基础位置", target.getName().getString());
        return baseBehind;
    }

    private static boolean isSafe(Player player, Vec3 pos) {
        return player.level().noCollision(player.getBoundingBox().move(pos.subtract(player.position())));
    }

    private static boolean isWearingGlove(Player player) {
        // ⭐ 统一判断（GloveHelper）
        return GloveHelper.isWearingGlove(player);
    }
}