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
 *   <li><b>蓄力中换格（滚轮 / 数字键）</b>：视为取消蓄力 ✓ 把原物品<b>放回原来那一格</b> ✓ 不发射、不扣费 ✓（见 {@link #tickChargingGuards} ✓ §625 修的 bug ✓）。</li>
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

    /**
     * 灶·开蓄力中：玩家 UUID → <b>原主手物品 + 它当时所在的热键栏格号</b>。
     *
     * <p>⚠ 为什么必须记格号（§625 修的一个真 bug）：蓄力是"把<b>那一格</b>换成火焰箭"，
     * 而不是"绑在手上"的 ✗ ⇒ 玩家一滚轮切格，火焰箭就留在原格里、
     * 而松手时只检查"当前主手是不是火焰箭" ⇒ <b>火焰箭永久残留</b> ✗（用户实测 ✓）。
     * 记下格号之后：无论玩家切到哪一格，都能把原物品**放回原格**、把火焰箭收回 ✓。
     */
    private static final Map<UUID, Charge> CHARGING = new ConcurrentHashMap<>();

    /** 一次灶·开蓄力的现场：原物品 + 它所在的背包格号（热键栏 0..8） */
    private record Charge(ItemStack original, int slot) {}

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
            // 记下"原物品 + 当时选中的那一格"，蓄力结束/切格时都要按格号还原 ✓
            CHARGING.put(player.getUUID(),
                    new Charge(player.getMainHandItem(), player.getInventory().selected));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FLAME_ARROW_ITEM.get()));
            return;
        }
        tryUse(player); // 解 / 捌 即时释放（tryUse 内按 mode 分派 onCast）
    }

    /** C 松开：灶·开蓄力中 → 恢复主手、扣咒力、发射火焰箭 */
    @Override
    public void onKeyRelease(ServerPlayer player) {
        Charge charge = CHARGING.remove(player.getUUID());
        if (charge == null) return;
        restoreAt(player, charge);
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

    /** 取消灶·开蓄力（登出/死亡/切招式/天逆鉾打断/换格时）：把原主手物品放回原格 */
    public static void cancelCharge(ServerPlayer player) {
        Charge charge = CHARGING.remove(player.getUUID());
        if (charge == null) return;
        restoreAt(player, charge);
    }

    /**
     * 把 {@link Charge#original} 放回它原来那一格，并收走残留的火焰箭 ✓。
     *
     * <p>规则：
     * <ul>
     *   <li>那一格<b>仍是火焰箭</b> ⇒ 直接换回原物品 ✓（正常路径 ✓）；</li>
     *   <li>那一格<b>已被玩家放上别的东西</b>（把火焰箭扔了/挪走了）⇒ <b>只收走残留的火焰箭，不覆盖玩家放的东西</b> ✗
     *       然后尽量把原物品塞回背包 ✓（塞不下就掉在脚下 ✓ 总比凭空消失好 ✓）。</li>
     * </ul>
     */
    private static void restoreAt(ServerPlayer player, Charge charge) {
        var inv = player.getInventory();
        int slot = Math.max(0, Math.min(inv.items.size() - 1, charge.slot()));
        ItemStack there = inv.getItem(slot);
        if (there.getItem() instanceof FlameArrowItem) {
            inv.setItem(slot, charge.original());
            return;
        }
        // 那一格已经不是火焰箭了：先把"别处的"残留火焰箭清掉（防止永久残留 ✓），再安置原物品
        discardLeftoverArrows(player);
        if (!charge.original().isEmpty() && !inv.add(charge.original())) {
            player.drop(charge.original(), false);
        }
    }

    /**
     * 清掉玩家身上残留的火焰箭（只动<b>热键栏 0..8</b> ✓ —— 蓄力只会写那一格 ✓，
     * 不碰主背包 ✗ 也不会误伤"恰好也叫这个名字"的东西 ✓ 其实整个模组就这一个来源 ✓）。
     */
    private static void discardLeftoverArrows(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.getItem() instanceof FlameArrowItem) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    /**
     * 每 tick 守护：<b>蓄力期间一旦玩家换了格（滚轮/数字键）就立刻取消蓄力</b> ✓（§625 修的 bug ✓）。
     *
     * <p>为什么必须每 tick 查：蓄力是"占了某一格" ✗ 而玩家可以随时把选中格切走 ✓
     * —— 只有每次都比对"当前选中格 == 蓄力格"才能及时收尾 ✓。
     *
     * <p>语义：换格 = 取消蓄力 ✓（与"切招式 / 天逆鉾打断 / 登出"同一个待遇 ✓ 不发射 ✓ 不扣费 ✓）；
     * 原物品**回到原来那一格** ✓ ⇒ 不会再出现"火焰箭永久残留" ✗。
     */
    public static void tickChargingGuards(ServerPlayer player) {
        if (CHARGING.isEmpty()) return;                       // 常态零开销 ✓
        Charge charge = CHARGING.get(player.getUUID());
        if (charge == null) return;
        var inv = player.getInventory();
        boolean switchedAway = inv.selected != charge.slot();
        boolean slotNotArrow = !(inv.getItem(charge.slot()).getItem() instanceof FlameArrowItem);
        if (switchedAway || slotNotArrow) {
            cancelCharge(player);
            player.displayClientMessage(
                    Component.translatable("message.tinkersnewlife.yuchuzi.charge_cancelled"), true);
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
