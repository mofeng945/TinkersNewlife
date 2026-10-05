package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.TwinRingLink;
import com.mofengbaizhi.tinkersnewlife.content.item.WhipItem;
import com.mofengbaizhi.tinkersnewlife.util.GoetyBridge;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * <b>监工</b>（§1068）—— 装在本鞭子上的强化 ✓，把"抽击"从伤害改成<b>管理</b> ✓。
 * 用户口径见 {@link com.mofengbaizhi.tinkersnewlife.content.modifier.SupervisorModifier} ✓。
 *
 * <h2>效果（三条 ✓）</h2>
 * <ol>
 *   <li><b>抽击伤害降为 0</b> ✓ —— 由 {@code WhipLashEntity} 在命中判定里分流 ✓
 *       （{@link #hasSupervisor} 为真就**完全不造成伤害** ✓，也不叠 §1064 的"鞭痕" ✗
 *        —— 那些被打的可是自家牲口 ✓ 再给它们减速减攻就荒唐了 ✗）；</li>
 *   <li><b>抽打"自己人"⇒ 力量 ＋ 速度</b> ✓：<b>逐次 ＋1 级、最高 5 级</b> ✓（{@link #MAX_BUFF_LEVEL} ✓）、
 *       时长 {@link #BUFF_DURATION_TICKS} ✓ 每次抽中刷新 ✓。
 *       "自己人" ＝ <b>以你为主人的生物</b> ✓（{@link OwnableEntity} 系：狼/猫/鹦鹉/马… ✓）
 *       ＋ <b>诡厄仆从</b> ✓（{@link GoetyBridge#getServantOwner} ✓）
 *       ＋ <b>同心戒同伴本人</b> ✓（{@link TwinRingLink#findPartner} ✓）——
 *       并且按本仓 §832 的既有口径 ✓：<b>戴同心戒时，同伴的随从也算你的随从</b> ✓；</li>
 *   <li><b>抽打村民 ⇒ 5% 概率半量补货</b> ✓：概率 {@link #VILLAGER_RESTOCK_CHANCE} ✓、
 *       可用次数压到正常的一半 ✓（{@link #RESTOCK_FRACTION} ✓）、
 *       每人每天最多 {@link #VILLAGER_RESTOCK_PER_DAY} 次 ✓（计数器存在村民自己的持久化数据里 ✓，
 *       跨存档/跨重启都在 ✓，且**独立于原版"每天两次补货"** ✓ —— 原版是睡觉重置 ✓ 这里是自然日 ✓）。</li>
 * </ol>
 *
 * <p>⚠ 只影响**本鞭子的抽击** ✓：左键近战早已彻底取消 ✓（§1057 ✓）、完美格挡的反射照旧造成伤害 ✓
 * （那是"反击"不是"抽牲口" ✓ 用户没要求改 ✗）。
 */
public final class SupervisorHandler {

    /** 力量/速度时长（tick ✓ 20 秒 ✓），每次抽中刷新 ✓ */
    public static final int BUFF_DURATION_TICKS = 20 * 20;
    /** 最高 5 级 ✓（amplifier 0～4 ✓ 用户口径「最高5级」✓） */
    public static final int MAX_BUFF_LEVEL = 5;
    /** 村民被抽中后触发补货的概率 ✓ 5% ✓（用户口径 ✓） */
    public static final float VILLAGER_RESTOCK_CHANCE = 0.05F;
    /** 每名村民每天最多触发次数 ✓ 3 次 ✓（用户口径 ✓） */
    public static final int VILLAGER_RESTOCK_PER_DAY = 3;
    /** 补货数量 ＝ 正常补货的<b>一半</b> ✓（用户口径 ✓） */
    public static final float RESTOCK_FRACTION = 0.5F;

    /** 持久化数据键：上次触发的"天"（{@code level.getDayTime() / 24000} ✓） */
    private static final String KEY_DAY = "tnl_supervisor_day";
    /** 持久化数据键：当天已触发次数 ✓ */
    private static final String KEY_COUNT = "tnl_supervisor_count";

    private SupervisorHandler() {
    }

    // ==================== ① 是否带"监工" ====================

    /** 主人手里的工具是否带「监工」✓（本鞭子 ✓ 且读不到工具数据时一律当没有 ✓ 不抛错 ✓） */
    public static boolean hasSupervisor(LivingEntity owner) {
        if (owner == null) {
            return false;
        }
        ItemStack stack = owner.getMainHandItem();
        if (!(stack.getItem() instanceof WhipItem)) {
            return false;
        }
        try {
            return ToolStack.from(stack).getModifierLevel(Modifiers.SUPERVISOR.get()) > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ==================== ② 抽中实体时的分流 ====================

    /**
     * 鞭子抽到某个实体时调用 ✓（此时**不造成任何伤害** ✓ 用户口径 ✓）。
     *
     * @param owner   挥鞭的主人 ✓
     * @param target  被抽中的实体 ✓
     */
    public static void onWhipTouch(Player owner, LivingEntity target) {
        if (owner == null || target == null || owner.level().isClientSide) {
            return;
        }
        if (target == owner) {
            return;                                   // 抽自己不算 ✓
        }
        // ① 自己人（宠物／仆从）或同心戒同伴 ⇒ 力量 ＋ 速度 ✓
        if (isMineOrPartners(target, owner)) {
            buff(target);
            return;
        }
        // ② 村民 ⇒ 5% 半量补货 ✓（每村民每天 3 次 ✓）
        if (target instanceof Villager villager) {
            tryRestock(villager);
        }
    }

    // ==================== ③ 自己人判定（含 §832 同伴的随从 ✓） ====================

    /** 是否是"我的或我同心戒同伴的"宠物／仆从 ✓（同伴本人另算 ✓） */
    private static boolean isMineOrPartners(LivingEntity target, Player owner) {
        if (isTwinRingPartner(target, owner)) {
            return true;                              // 同心戒同伴本人 ✓ 用户口径「抽打同心戒指同伴时可有此效果」✓
        }
        Player partner = TwinRingLink.findPartner(owner);
        if (isPetOf(target, owner)) {
            return true;
        }
        if (partner != null && isPetOf(target, partner)) {
            return true;                              // ⭐ §832：戴同心戒时同伴的随从也算自己的 ✓
        }
        LivingEntity goetyOwner = GoetyBridge.getServantOwner(target);
        if (goetyOwner != null) {
            return goetyOwner.getUUID().equals(owner.getUUID())
                    || (partner != null && goetyOwner.getUUID().equals(partner.getUUID()));
        }
        return false;
    }

    /** 以 {@code owner} 为主人的生物 ✓（{@link OwnableEntity} 系 ＋ 驯服动物 ✓） */
    private static boolean isPetOf(LivingEntity target, Player owner) {
        if (target instanceof OwnableEntity ownable) {
            java.util.UUID id = ownable.getOwnerUUID();
            return id != null && id.equals(owner.getUUID());
        }
        if (target instanceof TamableAnimal tameable) {
            return tameable.isTame() && owner.getUUID().equals(tameable.getOwnerUUID());
        }
        return false;
    }

    /** 是否是同心戒同伴本人 ✓ */
    private static boolean isTwinRingPartner(LivingEntity target, Player owner) {
        if (!(target instanceof Player other)) {
            return false;
        }
        Player partner = TwinRingLink.findPartner(owner);
        return partner != null && partner.getUUID().equals(other.getUUID());
    }

    // ==================== ④ 力量/速度叠级 ====================

    /** 给自己人叠力量 ＋ 速度 ✓（逐次 ＋1 级 ✓ 封顶 5 级 ✓ 每次刷新时长 ✓） */
    private static void buff(LivingEntity target) {
        int strength = nextAmplifier(target.getEffect(MobEffects.DAMAGE_BOOST));
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, BUFF_DURATION_TICKS, strength, false, false, true));
        int speed = nextAmplifier(target.getEffect(MobEffects.MOVEMENT_SPEED));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, BUFF_DURATION_TICKS, speed, false, false, true));
    }

    /** 下一层的 amplifier ✓（第一次 ＝ 0 ＝ I 级 ✓ … 封顶 {@link #MAX_BUFF_LEVEL} ⇒ amplifier 4 ✓） */
    private static int nextAmplifier(MobEffectInstance current) {
        if (current == null) {
            return 0;
        }
        return Math.min(current.getAmplifier() + 1, MAX_BUFF_LEVEL - 1);
    }

    // ==================== ⑤ 村民半量补货 ====================

    /** 掷 5% ✓；中了一次就把可用次数补到"正常的一半" ✓；每人每天封顶 3 次 ✓ */
    private static void tryRestock(Villager villager) {
        CompoundTag data = villager.getPersistentData();
        long day = villager.level().getDayTime() / 24000L;
        if (data.getLong(KEY_DAY) != day) {
            data.putLong(KEY_DAY, day);               // 新的一天 ⇒ 计数清零 ✓
            data.putInt(KEY_COUNT, 0);
        }
        if (data.getInt(KEY_COUNT) >= VILLAGER_RESTOCK_PER_DAY) {
            return;                                   // 今天已经触发满 3 次 ✓
        }
        if (villager.getRandom().nextFloat() >= VILLAGER_RESTOCK_CHANCE) {
            return;                                   // 没中 ✓（不消耗次数 ✓）
        }
        data.putInt(KEY_COUNT, data.getInt(KEY_COUNT) + 1);
        restockHalf(villager);
    }

    /** 把每笔交易的可用次数补到 {@link #RESTOCK_FRACTION}（一半 ✓）—— 相当于"只有半个补货" ✓ */
    private static void restockHalf(Villager villager) {
        boolean any = false;
        for (MerchantOffer offer : villager.getOffers()) {
            int max = offer.getMaxUses();
            int half = Math.max(1, (int) (max * RESTOCK_FRACTION));   // 正常补货给 max 次 ✓ 这里只给一半 ✓
            int used = Math.max(0, max - half);                       // 目标"已用次数" ⇒ 可用 ＝ max − used ＝ half ✓
            if (offer.getUses() == used) {
                continue;
            }
            // ⚠ 1.20.1 的 MerchantOffer **没有** setUses ✗（编译报错后确认的 ✓）
            //   ⇒ 用原版这套：resetUses() 归零 ✓ 再 increaseUses() 补到目标已用次数 ✓
            //   （max 上限很小（一般 ≤ 12）⇒ 这个循环代价可以忽略 ✓）
            offer.resetUses();
            for (int i = 0; i < used; i++) {
                offer.increaseUses();
            }
            any = true;
        }
        if (!any) {
            return;
        }
        villager.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.2F);
        if (villager.level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    villager.getX(), villager.getY() + 1.0D, villager.getZ(), 6, 0.3D, 0.3D, 0.3D, 0.0D);
        }
    }
}
