package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.entity.StarfallArrow;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * TNL879 星陨悲叹 Starfall Lament（★照搬参考实现 HomingBowItem ✓）
 *
 * <p>★用户口径：★"完全模仿参考实现做弓，只是把他那个天降的箭换成从弓射出去" ✓
 * ⇒ ★本类完全照搬参考：★蓄力弓 ✓、★``AutoFire`` NBT 标签自动连发 ✓、
 * ★Multishot 三箭 ✓、★**流动渐变名字** ✓。
 * ★与参考的差别只有：★箭是**从弓射出**（参考里另有一套天降逻辑 ✗ 我们不做 ✓）。
 */
public class StarfallLamentItem extends BowItem {

    private static final int NAME_COLOR_START = 0xFFFFFF;   // TNL884 ★白 ✓
    private static final int NAME_COLOR_END = 0x3A3A3A;     // TNL884 ★深灰 ✓
    private static final float FLOW_SPEED = 1.0F;

    public static final List<Enchantment> EXTRA_WHITELIST = new ArrayList<>();

    private static long TNL$LAST_LOG = 0L;

    /** TNL934 ★允许用**附魔书**打上去的原版弓附魔 ✓ */
    private static final java.util.Set<Enchantment> ALLOWED_BOOK = java.util.Set.of(
            Enchantments.POWER_ARROWS, Enchantments.PUNCH_ARROWS, Enchantments.FLAMING_ARROWS,
            Enchantments.INFINITY_ARROWS, Enchantments.UNBREAKING, Enchantments.MENDING);

    public StarfallLamentItem() {
        super(new Properties().stacksTo(1).durability(800).rarity(Rarity.EPIC));   // TNL934 ★用户：★耐久 800 ✓
    }

    @Override
    public Component getName(ItemStack stack) {
        return buildFlowGradient(super.getName(stack));
    }

    public static Component buildFlowGradient(Component base) {
        String text = base.getString();
        if (text.isEmpty()) {
            return base;
        }
        int len = text.length();
        double timeSec = System.currentTimeMillis() / 1000.0D;
        MutableComponent result = Component.literal("");
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                result.append(Component.literal(" "));
                continue;
            }
            double phase = i * 0.25D + timeSec * FLOW_SPEED;
            double t = (Math.sin(phase * Math.PI * 2.0D) + 1.0D) / 2.0D;
            int color = lerpColor(NAME_COLOR_START, NAME_COLOR_END, (float) t);
            result.append(Component.literal(String.valueOf(c)).setStyle(Style.EMPTY.withColor(color).withBold(true)));
        }
        return result;
    }

    private static int lerpColor(int c1, int c2, float f) {
        if (f < 0) {
            f = 0;
        }
        if (f > 1) {
            f = 1;
        }
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * f);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * f);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * f);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        // TNL934 ★★ 用户口径：★支持穿透/多重射击/无限/火矢/无限修补等 ✓
        //   ★通过 ``super`` 保留**原版弓能附的全部** ✓
        //   （★力量/冲击/火矢/无限/耐久/经验修补 ✓）+ ★另外放开**穿透** ✓
        return enchantment == Enchantments.PIERCING
                || enchantment == Enchantments.MULTISHOT
                || EXTRA_WHITELIST.contains(enchantment)
                || super.canApplyAtEnchantingTable(stack, enchantment);
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        var enchants = EnchantmentHelper.getEnchantments(book);
        for (Enchantment e : enchants.keySet()) {
            if (e != Enchantments.MULTISHOT && e != Enchantments.PIERCING && !EXTRA_WHITELIST.contains(e)
                    && !ALLOWED_BOOK.contains(e)) {
                return false;
            }
        }
        return true;
    }

    /**
     * TNL883 ★★ 关键：**绕过原版的“必须有箭”检查** ✓
     *
     * <p>★用户反馈：★"还是没自动放箭啊" ✓
     * ★根因 ✗：★原版 {@code BowItem#use}会先查弹药
     * （{@code player.getProjectile(stack)} ✗），
     * ★**背包里没箭就直接 FAIL** ✗ ⇒ ★根本没有
     * "开始使用"这个状态 ✗ ⇒ ★{@code onUseTick} 永远不会调到 ✗
     * ⇒ ★自动射击自然不生效 ✓。
     *
     * <p>★现在：★自己重写 {@code use} ✓，★**不要箭也能拉弓** ✓。
     */
    /**
     * TNL890 关键修正：★有箭就走**原版弓的通道**（动画/状态由引擎保证），没箭才手动进入使用状态。
     *
     * <p>日志证据（TNL889）：★``onUseTick useTime=30`` 之后又回到 ``useTime=10/0``
     * ⇒ ★使用状态被反复重置 ⇒ ★pull 永远≈0 ⇒ ★蓄力动画起不来 ✓
     * 原版 ``BowItem#use`` 的 ``startUsingItem`` 在引擎流程里，不会被这样打断 ✓
     */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean hasAmmo = !player.getProjectile(stack).isEmpty();
        if (hasAmmo) {
            return super.use(level, player, hand);            // ★原版通道 ⇒ 动画稳 ✓
        }
        player.startUsingItem(hand);                          // ★没箭的兜底（动画可能不标准）
        return net.minecraft.world.InteractionResultHolder.consume(stack);
    }

    @Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
        return net.minecraft.world.item.UseAnim.BOW;   // ★拉弓动画（★模型 pulling 也靠它 ✓）
    }

    /** TNL883 ★★自动射击**默认接上** ✓
     *
     * <p>★用户口径：★"把自动射击接上" ✓
     * ⇒ ★没有 NBT 时**默认开启** ✓（★参考实现是默认关 ✗）；
     * ★想关掉：★给弓加 NBT ``AutoFire: 0b`` ✓。
     */
    public static boolean isAutoFire(ItemStack stack) {
        return !stack.hasTag() || stack.getTag().getBoolean("AutoFire");
    }

    /**
     * TNL884 ★★ 工具提示（★用户**点名**要求 ✓）
     *
     * <p>⚠ 按备忘录 §812：★用户没点名一律不加 tooltip ✗；
     * ★这次是用户明确要求加 ✓（★原文照搬 ✓）。
     */
    @Override
    public void appendHoverText(ItemStack stack, Level level, java.util.List<net.minecraft.network.chat.Component> tip,
                                net.minecraft.world.item.TooltipFlag flag) {
        // TNL894 ★★ 用户口径：★"想让弓的工具提示也应用它名字那样的流光效果" ✓
        //   ★两行都走同一个 buildFlowGradient ✗ ★并保留斜体 ✓
        // TNL1065 user: "give the bow the category too" and "the position must match"
        // The Aeonian Order shows its category as the line right under the name, so the bow gets the
        // same line in the same slot (the item name is added by vanilla before this method runs).
        tip.add(net.minecraft.network.chat.Component.literal(
                "匠魂新生·其他物品")
                .withStyle(net.minecraft.ChatFormatting.BLUE));
        tip.add(buildFlowGradient(net.minecraft.network.chat.Component.translatable(
                "item.tinkersnewlife.starfall_lament.tooltip"))
                .copy().withStyle(net.minecraft.ChatFormatting.ITALIC));
        tip.add(buildFlowGradient(net.minecraft.network.chat.Component.translatable(
                "item.tinkersnewlife.starfall_lament.tooltip_author"))
                .copy().withStyle(net.minecraft.ChatFormatting.ITALIC));
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }



    /**
     * TNL892 ★★ 保留空实现（★不再发射 ✗）
     *
     * <p>★用户反馈：★"怎么变成双发弓了" ✓
     * ⇒ ★因为**两条路都开火** ✗：
     * ★新包路径（{@code PacketStarfallShoot} ✓）+
     * ★原版路径（本方法 ✗）。
     * ★现在：★**只有包路径发射** ✗ ⇒ ★一次一箭 ✓。
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        // ★什么都不做 ✓（★发射由客户端计数包负责 ✓）
    }

    @Override
    public boolean useOnRelease(ItemStack stack) {
        return true;   // TNL887 ★松手才发射 ✓
    }

    /** ★开一组箭（★与参考一致：★主箭 + Multishot 则三箭 ✓） */
    private void fireArrows(Player player, ItemStack stack, Level level, float power) {
        float velocity = power;
        boolean hasMultishot = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, stack) > 0;
        StarfallArrow main = new StarfallArrow(ModEntities.STARFALL_ARROW.get(), player, level);
        main.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, tnl$speed(power), 1.0F);
        main.pickup = AbstractArrow.Pickup.DISALLOWED;
        level.addFreshEntity(main);
        if (hasMultishot) {
            float off = 5.0F;
            StarfallArrow left = new StarfallArrow(ModEntities.STARFALL_ARROW.get(), player, level);
            left.shootFromRotation(player, player.getXRot(), player.getYRot() + off, 0.0F, tnl$speed(power), 1.0F);
            left.pickup = AbstractArrow.Pickup.DISALLOWED;
            level.addFreshEntity(left);
            StarfallArrow right = new StarfallArrow(ModEntities.STARFALL_ARROW.get(), player, level);
            right.shootFromRotation(player, player.getXRot(), player.getYRot() - off, 0.0F, tnl$speed(power), 1.0F);
            right.pickup = AbstractArrow.Pickup.DISALLOWED;
            level.addFreshEntity(right);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (System.currentTimeMillis() - TNL$LAST_LOG > 2000L) {   // TNL883 ★限流日志 ✓
            TNL$LAST_LOG = System.currentTimeMillis();
        }
    }

    /** TNL891 ★★ 服务端发射（★由 PacketStarfallShoot 触发 ✓）
     *  ★不依赖原版\使用状态 ✓（★那个状态在实机里被反复重置 ✗）。
     */
    public static void fireFromServer(net.minecraft.server.level.ServerPlayer player, int chargeTicks) {
        try {
            float power = getPowerForTime(chargeTicks);
            if (power < 0.1F) {
                return;
            }
            fireArrowsStatic(player, player.getMainHandItem(), player.level(), power);
        } catch (Throwable ignored) {
        }
    }

    /** ★静态版开一组箭（★与实例版同逻辑 ✓） */
    private static void fireArrowsStatic(Player player, ItemStack stack, Level level, float power) {
        // TNL934 ★★ 附魔：★力量（★每级 +10% 伤害 ✓）/★穿透/★火矢/★无限/★多重射击 ✓
        int ePower = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, stack);
        int ePierce = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PIERCING, stack);
        int eFlame = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, stack);
        int eInf = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, stack);
        int eMulti = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, stack);
        boolean creative = player.getAbilities().instabuild;

        // ★消耗箭矢：★没箭不发 ✓；★无限附魔不消耗 ✓
        ItemStack ammo = player.getProjectile(stack);
        if (ammo.isEmpty()) {
            return;
        }
        if (!creative && eInf <= 0) {
            ammo.shrink(1);
        }
        // ★耐久 -1 ✓（★耐久附魔由原版减免 ✓）
        if (!creative) {
            stack.hurtAndBreak(1, player, pl -> pl.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
        }

        StarfallArrow main = tnl$enchant(new StarfallArrow(ModEntities.STARFALL_ARROW.get(), player, level),
                ePower, ePierce, eFlame);
        main.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, tnl$speed(power), 1.0F);
        main.pickup = AbstractArrow.Pickup.DISALLOWED;
        level.addFreshEntity(main);
        if (eMulti > 0) {                                  // ★多重射击：★再补两支 ±10° ✓
            for (float off : new float[]{-10.0F, 10.0F}) {
                StarfallArrow extra = tnl$enchant(new StarfallArrow(ModEntities.STARFALL_ARROW.get(), player, level),
                        ePower, ePierce, eFlame);
                extra.shootFromRotation(player, player.getXRot(), player.getYRot() + off, 0.0F, tnl$speed(power), 1.0F);
                extra.pickup = AbstractArrow.Pickup.DISALLOWED;
                level.addFreshEntity(extra);
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** TNL934 ★把力量/穿透/火矢传给箭矢 ✓ */
    private static StarfallArrow tnl$enchant(StarfallArrow arrow, int power, int pierce, int flame) {
        arrow.tnl$setPower(Math.max(0, power));
        arrow.tnl$setPierce(Math.max(0, pierce));
        arrow.tnl$setFlame(flame > 0);
        return arrow;
    }

    private static int chargeTicksOf(float power) {
        return (int) (power * 20.0F);
    }

    /** TNL889 初速：原来 x3.0（太快 -> 来不及转弯撞方块）；现在 1.2 ~ 2.4（蓄力越满越快） */
    private static float tnl$speed(float power) {
        return 1.2F + power * 1.2F;
    }

    public static float getPowerForTime(int useTime) {
        float f = (float) useTime / 10.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        if (f > 1.0F) {
            f = 1.0F;
        }
        return f;
    }

    /** ★供以后写 Boss AI 用（★现在没用上 ✗） */
    public static StarfallArrow fireAt(Level level, LivingEntity shooter, LivingEntity target) {
        StarfallArrow arrow = new StarfallArrow(ModEntities.STARFALL_ARROW.get(), shooter, level);
        net.minecraft.world.phys.Vec3 eye = shooter.getEyePosition();
        net.minecraft.world.phys.Vec3 dir = target != null
                ? target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(eye).normalize()
                : shooter.getLookAngle();
        if (target != null) {
            arrow.tnl$setHomingTarget(target);
        }
        arrow.setPos(eye.x + dir.x * 0.6D, eye.y + dir.y * 0.6D, eye.z + dir.z * 0.6D);
        arrow.shoot(dir.x, dir.y, dir.z, 3.0F, 0.0F);
        level.addFreshEntity(arrow);
        return arrow;
    }
}
