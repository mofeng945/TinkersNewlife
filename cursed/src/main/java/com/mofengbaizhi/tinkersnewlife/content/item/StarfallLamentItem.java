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
 * TNL879 \u661f\u9668\u60b2\u53f9 Starfall Lament\uff08\u2605\u7167\u642c\u53c2\u8003\u5b9e\u73b0 HomingBowItem \u2713\uff09
 *
 * <p>\u2605\u7528\u6237\u53e3\u5f84\uff1a\u2605"\u5b8c\u5168\u6a21\u4eff\u53c2\u8003\u5b9e\u73b0\u505a\u5f13\uff0c\u53ea\u662f\u628a\u4ed6\u90a3\u4e2a\u5929\u964d\u7684\u7bad\u6362\u6210\u4ece\u5f13\u5c04\u51fa\u53bb" \u2713
 * \u21d2 \u2605\u672c\u7c7b\u5b8c\u5168\u7167\u642c\u53c2\u8003\uff1a\u2605\u84c4\u529b\u5f13 \u2713\u3001\u2605``AutoFire`` NBT \u6807\u7b7e\u81ea\u52a8\u8fde\u53d1 \u2713\u3001
 * \u2605Multishot \u4e09\u7bad \u2713\u3001\u2605**\u6d41\u52a8\u6e10\u53d8\u540d\u5b57** \u2713\u3002
 * \u2605\u4e0e\u53c2\u8003\u7684\u5dee\u522b\u53ea\u6709\uff1a\u2605\u7bad\u662f**\u4ece\u5f13\u5c04\u51fa**\uff08\u53c2\u8003\u91cc\u53e6\u6709\u4e00\u5957\u5929\u964d\u903b\u8f91 \u2717 \u6211\u4eec\u4e0d\u505a \u2713\uff09\u3002
 */
public class StarfallLamentItem extends BowItem {

    private static final int NAME_COLOR_START = 0xFFFFFF;   // TNL884 \u2605\u767d \u2713
    private static final int NAME_COLOR_END = 0x3A3A3A;     // TNL884 \u2605\u6df1\u7070 \u2713
    private static final float FLOW_SPEED = 1.0F;

    public static final List<Enchantment> EXTRA_WHITELIST = new ArrayList<>();

    private static long TNL$LAST_LOG = 0L;

    /** TNL934 \u2605\u5141\u8bb8\u7528**\u9644\u9b54\u4e66**\u6253\u4e0a\u53bb\u7684\u539f\u7248\u5f13\u9644\u9b54 \u2713 */
    private static final java.util.Set<Enchantment> ALLOWED_BOOK = java.util.Set.of(
            Enchantments.POWER_ARROWS, Enchantments.PUNCH_ARROWS, Enchantments.FLAMING_ARROWS,
            Enchantments.INFINITY_ARROWS, Enchantments.UNBREAKING, Enchantments.MENDING);

    public StarfallLamentItem() {
        super(new Properties().stacksTo(1).durability(800).rarity(Rarity.EPIC));   // TNL934 \u2605\u7528\u6237\uff1a\u2605\u8010\u4e45 800 \u2713
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
        // TNL934 \u2605\u2605 \u7528\u6237\u53e3\u5f84\uff1a\u2605\u652f\u6301\u7a7f\u900f/\u591a\u91cd\u5c04\u51fb/\u65e0\u9650/\u706b\u77e2/\u65e0\u9650\u4fee\u8865\u7b49 \u2713
        //   \u2605\u901a\u8fc7 ``super`` \u4fdd\u7559**\u539f\u7248\u5f13\u80fd\u9644\u7684\u5168\u90e8** \u2713
        //   \uff08\u2605\u529b\u91cf/\u51b2\u51fb/\u706b\u77e2/\u65e0\u9650/\u8010\u4e45/\u7ecf\u9a8c\u4fee\u8865 \u2713\uff09\u002b \u2605\u53e6\u5916\u653e\u5f00**\u7a7f\u900f** \u2713
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
     * TNL883 \u2605\u2605 \u5173\u952e\uff1a**\u7ed5\u8fc7\u539f\u7248\u7684\u201c\u5fc5\u987b\u6709\u7bad\u201d\u68c0\u67e5** \u2713
     *
     * <p>\u2605\u7528\u6237\u53cd\u9988\uff1a\u2605"\u8fd8\u662f\u6ca1\u81ea\u52a8\u653e\u7bad\u554a" \u2713
     * \u2605\u6839\u56e0 \u2717\uff1a\u2605\u539f\u7248 {@code BowItem#use}\u4f1a\u5148\u67e5\u5f39\u836f
     * \uff08{@code player.getProjectile(stack)} \u2717\uff09\uff0c
     * \u2605**\u80cc\u5305\u91cc\u6ca1\u7bad\u5c31\u76f4\u63a5 FAIL** \u2717 \u21d2 \u2605\u6839\u672c\u6ca1\u6709
     * "\u5f00\u59cb\u4f7f\u7528"\u8fd9\u4e2a\u72b6\u6001 \u2717 \u21d2 \u2605{@code onUseTick} \u6c38\u8fdc\u4e0d\u4f1a\u8c03\u5230 \u2717
     * \u21d2 \u2605\u81ea\u52a8\u5c04\u51fb\u81ea\u7136\u4e0d\u751f\u6548 \u2713\u3002
     *
     * <p>\u2605\u73b0\u5728\uff1a\u2605\u81ea\u5df1\u91cd\u5199 {@code use} \u2713\uff0c\u2605**\u4e0d\u8981\u7bad\u4e5f\u80fd\u62c9\u5f13** \u2713\u3002
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
        return net.minecraft.world.item.UseAnim.BOW;   // \u2605\u62c9\u5f13\u52a8\u753b\uff08\u2605\u6a21\u578b pulling \u4e5f\u9760\u5b83 \u2713\uff09
    }

    /** TNL883 \u2605\u2605\u81ea\u52a8\u5c04\u51fb**\u9ed8\u8ba4\u63a5\u4e0a** \u2713
     *
     * <p>\u2605\u7528\u6237\u53e3\u5f84\uff1a\u2605"\u628a\u81ea\u52a8\u5c04\u51fb\u63a5\u4e0a" \u2713
     * \u21d2 \u2605\u6ca1\u6709 NBT \u65f6**\u9ed8\u8ba4\u5f00\u542f** \u2713\uff08\u2605\u53c2\u8003\u5b9e\u73b0\u662f\u9ed8\u8ba4\u5173 \u2717\uff09\uff1b
     * \u2605\u60f3\u5173\u6389\uff1a\u2605\u7ed9\u5f13\u52a0 NBT ``AutoFire: 0b`` \u2713\u3002
     */
    public static boolean isAutoFire(ItemStack stack) {
        return !stack.hasTag() || stack.getTag().getBoolean("AutoFire");
    }

    /**
     * TNL884 \u2605\u2605 \u5de5\u5177\u63d0\u793a\uff08\u2605\u7528\u6237**\u70b9\u540d**\u8981\u6c42 \u2713\uff09
     *
     * <p>\u26a0 \u6309\u5907\u5fd8\u5f55 \u00a7812\uff1a\u2605\u7528\u6237\u6ca1\u70b9\u540d\u4e00\u5f8b\u4e0d\u52a0 tooltip \u2717\uff1b
     * \u2605\u8fd9\u6b21\u662f\u7528\u6237\u660e\u786e\u8981\u6c42\u52a0 \u2713\uff08\u2605\u539f\u6587\u7167\u642c \u2713\uff09\u3002
     */
    @Override
    public void appendHoverText(ItemStack stack, Level level, java.util.List<net.minecraft.network.chat.Component> tip,
                                net.minecraft.world.item.TooltipFlag flag) {
        // TNL894 \u2605\u2605 \u7528\u6237\u53e3\u5f84\uff1a\u2605"\u60f3\u8ba9\u5f13\u7684\u5de5\u5177\u63d0\u793a\u4e5f\u5e94\u7528\u5b83\u540d\u5b57\u90a3\u6837\u7684\u6d41\u5149\u6548\u679c" \u2713
        //   \u2605\u4e24\u884c\u90fd\u8d70\u540c\u4e00\u4e2a buildFlowGradient \u2717 \u2605\u5e76\u4fdd\u7559\u659c\u4f53 \u2713
        // TNL1065 user: "give the bow the category too" and "the position must match"
        // The Aeonian Order shows its category as the line right under the name, so the bow gets the
        // same line in the same slot (the item name is added by vanilla before this method runs).
        tip.add(net.minecraft.network.chat.Component.literal(
                "\u5320\u9b42\u65b0\u751f\u00b7\u5176\u4ed6\u7269\u54c1")
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
     * TNL892 \u2605\u2605 \u4fdd\u7559\u7a7a\u5b9e\u73b0\uff08\u2605\u4e0d\u518d\u53d1\u5c04 \u2717\uff09
     *
     * <p>\u2605\u7528\u6237\u53cd\u9988\uff1a\u2605"\u600e\u4e48\u53d8\u6210\u53cc\u53d1\u5f13\u4e86" \u2713
     * \u21d2 \u2605\u56e0\u4e3a**\u4e24\u6761\u8def\u90fd\u5f00\u706b** \u2717\uff1a
     * \u2605\u65b0\u5305\u8def\u5f84\uff08{@code PacketStarfallShoot} \u2713\uff09\u002b
     * \u2605\u539f\u7248\u8def\u5f84\uff08\u672c\u65b9\u6cd5 \u2717\uff09\u3002
     * \u2605\u73b0\u5728\uff1a\u2605**\u53ea\u6709\u5305\u8def\u5f84\u53d1\u5c04** \u2717 \u21d2 \u2605\u4e00\u6b21\u4e00\u7bad \u2713\u3002
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        // \u2605\u4ec0\u4e48\u90fd\u4e0d\u505a \u2713\uff08\u2605\u53d1\u5c04\u7531\u5ba2\u6237\u7aef\u8ba1\u6570\u5305\u8d1f\u8d23 \u2713\uff09
    }

    @Override
    public boolean useOnRelease(ItemStack stack) {
        return true;   // TNL887 \u2605\u677e\u624b\u624d\u53d1\u5c04 \u2713
    }

    /** \u2605\u5f00\u4e00\u7ec4\u7bad\uff08\u2605\u4e0e\u53c2\u8003\u4e00\u81f4\uff1a\u2605\u4e3b\u7bad \u002b Multishot \u5219\u4e09\u7bad \u2713\uff09 */
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
        if (System.currentTimeMillis() - TNL$LAST_LOG > 2000L) {   // TNL883 \u2605\u9650\u6d41\u65e5\u5fd7 \u2713
            TNL$LAST_LOG = System.currentTimeMillis();
        }
    }

    /** TNL891 \u2605\u2605 \u670d\u52a1\u7aef\u53d1\u5c04\uff08\u2605\u7531 PacketStarfallShoot \u89e6\u53d1 \u2713\uff09
     *  \u2605\u4e0d\u4f9d\u8d56\u539f\u7248\\u4f7f\u7528\u72b6\u6001 \u2713\uff08\u2605\u90a3\u4e2a\u72b6\u6001\u5728\u5b9e\u673a\u91cc\u88ab\u53cd\u590d\u91cd\u7f6e \u2717\uff09\u3002
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

    /** \u2605\u9759\u6001\u7248\u5f00\u4e00\u7ec4\u7bad\uff08\u2605\u4e0e\u5b9e\u4f8b\u7248\u540c\u903b\u8f91 \u2713\uff09 */
    private static void fireArrowsStatic(Player player, ItemStack stack, Level level, float power) {
        // TNL934 \u2605\u2605 \u9644\u9b54\uff1a\u2605\u529b\u91cf\uff08\u2605\u6bcf\u7ea7 +10% \u4f24\u5bb3 \u2713\uff09/\u2605\u7a7f\u900f/\u2605\u706b\u77e2/\u2605\u65e0\u9650/\u2605\u591a\u91cd\u5c04\u51fb \u2713
        int ePower = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, stack);
        int ePierce = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PIERCING, stack);
        int eFlame = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, stack);
        int eInf = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, stack);
        int eMulti = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, stack);
        boolean creative = player.getAbilities().instabuild;

        // \u2605\u6d88\u8017\u7bad\u77e2\uff1a\u2605\u6ca1\u7bad\u4e0d\u53d1 \u2713\uff1b\u2605\u65e0\u9650\u9644\u9b54\u4e0d\u6d88\u8017 \u2713
        ItemStack ammo = player.getProjectile(stack);
        if (ammo.isEmpty()) {
            return;
        }
        if (!creative && eInf <= 0) {
            ammo.shrink(1);
        }
        // \u2605\u8010\u4e45 -1 \u2713\uff08\u2605\u8010\u4e45\u9644\u9b54\u7531\u539f\u7248\u51cf\u514d \u2713\uff09
        if (!creative) {
            stack.hurtAndBreak(1, player, pl -> pl.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
        }

        StarfallArrow main = tnl$enchant(new StarfallArrow(ModEntities.STARFALL_ARROW.get(), player, level),
                ePower, ePierce, eFlame);
        main.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, tnl$speed(power), 1.0F);
        main.pickup = AbstractArrow.Pickup.DISALLOWED;
        level.addFreshEntity(main);
        if (eMulti > 0) {                                  // \u2605\u591a\u91cd\u5c04\u51fb\uff1a\u2605\u518d\u8865\u4e24\u652f \u00b110\u00b0 \u2713
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

    /** TNL934 \u2605\u628a\u529b\u91cf/\u7a7f\u900f/\u706b\u77e2\u4f20\u7ed9\u7bad\u77e2 \u2713 */
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

    /** \u2605\u4f9b\u4ee5\u540e\u5199 Boss AI \u7528\uff08\u2605\u73b0\u5728\u6ca1\u7528\u4e0a \u2717\uff09 */
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
