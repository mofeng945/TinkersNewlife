package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.entity.FlameArrowEntity;
import com.mofengbaizhi.tinkersnewlife.content.item.FlameArrowItem;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 术式「御厨子」：整合「解 / 捌 / 灶·开」三种招式于一个术式（占用 1 个术式槽）。
 * <p>
 * 操作（与原三术式完全一致的按键语义，只是并作一个术式）：
 * <ul>
 *   <li><b>C（术式键）按下</b>：解 = 对视线目标单发斩击；捌 = 对 3 格内接触目标六连斩；
 *       灶·开 = 主手换火焰箭开始蓄力。</li>
 *   <li><b>C 松开</b>：灶·开蓄力中 → 恢复主手并朝当前朝向发射火焰箭（解/捌即时，无松开动作）。</li>
 *   <li><b>F（反转键）</b>：在 解 → 捌 → 灶·开 间轮换当前招式（切走灶·开时自动取消蓄力）。</li>
 * </ul>
 * 伤害/消耗与原术式一致：解 ×1 / 捌 ×2 / 灶·开 ×10 基础咒力消耗。
 */
public final class YuchuziTechnique extends BaseTechnique {

    public static final YuchuziTechnique INSTANCE = new YuchuziTechnique();

    /** 招式：解 */
    public static final int MODE_KAI = 0;
    /** 招式：捌 */
    public static final int MODE_BA = 1;
    /** 招式：灶·开 */
    public static final int MODE_ZAO_KAI = 2;

    /** 玩家持久数据键：御厨子当前招式（0 解 / 1 捌 / 2 灶·开） */
    public static final String KEY_MODE = "tinkersnewlife.yuchuzi_mode";

    /** 「解」的伤害系数：共享伤害基底 × 70% */
    private static final double KAI_FACTOR = 0.70;
    /** 「捌」接触距离（格） */
    private static final double BA_RANGE = 3.0;
    /** 「捌」斩击总数：3 横向 + 3 纵向 */
    private static final int BA_SLASH_COUNT = 6;
    /** 「灶·开」火焰箭飞行速度（较慢，笔直） */
    private static final float ARROW_SPEED = 1.2F;

    /** 灶·开蓄力中：玩家 UUID → 原主手物品 */
    private static final Map<UUID, ItemStack> CHARGING = new ConcurrentHashMap<>();

    private YuchuziTechnique() {
        super(Modifiers.YUCHUZI.getId());
    }

    // ==================== 招式状态 ====================

    /** 当前招式（0 解 / 1 捌 / 2 灶·开） */
    public static int getMode(ServerPlayer player) {
        return Math.max(0, Math.min(MODE_ZAO_KAI, player.getPersistentData().getInt(KEY_MODE)));
    }

    private static void setMode(ServerPlayer player, int mode) {
        player.getPersistentData().putInt(KEY_MODE, Math.max(0, Math.min(MODE_ZAO_KAI, mode)));
    }

    /** 当前招式显示名（消息用） */
    private static Component modeName(int mode) {
        return switch (mode) {
            case MODE_BA -> Component.translatable("message.tinkersnewlife.yuchuzi.mode_ba");
            case MODE_ZAO_KAI -> Component.translatable("message.tinkersnewlife.yuchuzi.mode_zao_kai");
            default -> Component.translatable("message.tinkersnewlife.yuchuzi.mode_kai");
        };
    }

    // ==================== 按键钩子 ====================

    /** C 按下：解/捌 → 即时释放；灶·开 → 开始蓄力 */
    @Override
    public void onKeyPress(ServerPlayer player) {
        int mode = getMode(player);
        if (mode == MODE_ZAO_KAI) {
            if (CHARGING.containsKey(player.getUUID())) return; // 已在蓄力
            CHARGING.put(player.getUUID(), player.getMainHandItem());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FLAME_ARROW_ITEM.get()));
            return;
        }
        tryUse(player); // 解 / 捌 即时释放（tryUse 内按 mode 分派 onCast）
    }

    /** C 松开：灶·开蓄力中 → 恢复主手、扣咒力、发射火焰箭 */
    @Override
    public void onKeyRelease(ServerPlayer player) {
        ItemStack original = CHARGING.remove(player.getUUID());
        if (original == null) return;
        // 仅当主手仍是火焰箭时才恢复（防止蓄力中玩家自行换走物品）
        if (player.getMainHandItem().getItem() instanceof FlameArrowItem) {
            player.setItemInHand(InteractionHand.MAIN_HAND, original);
        }
        // 松开发射时扣除（解 ×10），不足则取消
        if (!payCost(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        FlameArrowEntity arrow = new FlameArrowEntity(player.level(), player);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, ARROW_SPEED, 0.0F);
        player.level().addFreshEntity(arrow);
    }

    /** F（反转键）：轮换招式 解 → 捌 → 灶·开；切走灶·开自动取消蓄力 */
    @Override
    public void onReverseKeyPress(ServerPlayer player) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        // 先取消灶·开蓄力（若正在蓄力）
        cancelCharge(player);
        int next = (getMode(player) + 1) % 3;
        setMode(player, next);
        player.displayClientMessage(Component.translatable(
                "message.tinkersnewlife.yuchuzi.switch", modeName(next)), true);
    }

    /** 取消灶·开蓄力（登出/死亡/切招式/天逆鉾打断时）：恢复原主手物品 */
    public static void cancelCharge(ServerPlayer player) {
        ItemStack original = CHARGING.remove(player.getUUID());
        if (original == null) return;
        if (player.getMainHandItem().getItem() instanceof FlameArrowItem) {
            player.setItemInHand(InteractionHand.MAIN_HAND, original);
        }
    }

    // ==================== 解 / 捌 即时释放 ====================

    /** 咒力消耗：解 ×1 / 捌 ×2 / 灶·开 ×10（灶·开经 tryUse 之外自行扣费） */
    @Override
    protected int getCost(ServerPlayer player) {
        int mode = getMode(player);
        int base = super.getCost(player);
        return switch (mode) {
            case MODE_BA -> base * 2;
            case MODE_ZAO_KAI -> base * 10;
            default -> base;
        };
    }

    /** 「捌」为接触招式：目标必须距玩家 3 格以内；解 / 灶·开不收紧 */
    @Override
    protected boolean isTargetInRange(ServerPlayer player, LivingEntity target) {
        if (getMode(player) == MODE_BA) {
            return player.distanceToSqr(target) <= BA_RANGE * BA_RANGE;
        }
        return true;
    }

    @Override
    protected void onCast(ServerPlayer player, LivingEntity target) {
        if (getMode(player) == MODE_BA) {
            castBa(player, target);
        } else {
            castKai(player, target);
        }
    }

    /** 解：单发斩击（共享伤害基底 × 70%），无视无敌帧、正常护甲结算 */
    private void castKai(ServerPlayer player, LivingEntity target) {
        double damage = amplifyTechniqueDamage(player, computeBaseDamage(player) * KAI_FACTOR);
        damage = com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper
                .applyCurseCoreTraits(player, target, damage);
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().mobAttack(player), (float) damage);
        com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper.afterCurseCoreHit(player, target, damage);
        spawnSlashParticles(player.serverLevel(), player.getEyePosition(), target.position());
    }

    /** 捌：3 横向 + 3 纵向共 6 道斩击，每道 = 解的二分之一 */
    private void castBa(ServerPlayer player, LivingEntity target) {
        double perSlash = amplifyTechniqueDamage(player,
                computeBaseDamage(player) * (KAI_FACTOR / 2.0));
        for (int i = 0; i < BA_SLASH_COUNT; i++) {
            double dmg = com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper
                    .applyCurseCoreTraits(player, target, perSlash);
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().mobAttack(player), (float) dmg);
            com.mofengbaizhi.tinkersnewlife.content.curse.CurseCoreTraitHelper.afterCurseCoreHit(player, target, dmg);
        }
        ServerLevel level = player.serverLevel();
        Vec3 pos = target.position();
        for (int i = 0; i < 3; i++) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 0.35 + i * 0.35, pos.z, 1, 0.8, 0, 0, 0);
        }
        for (int i = 0; i < 3; i++) {
            double angle = 2 * Math.PI * i / 3;
            double ox = Math.cos(angle) * 0.25;
            double oz = Math.sin(angle) * 0.25;
            for (int k = 0; k < 5; k++) {
                level.sendParticles(ParticleTypes.CRIT, pos.x + ox, pos.y + k * 0.3, pos.z + oz, 1, 0, 0, 0, 0);
            }
        }
    }
}
