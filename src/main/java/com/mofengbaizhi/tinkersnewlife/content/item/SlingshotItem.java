package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.StoneShotEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ForgeEventFactory;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.build.ConditionalStatModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.GeneralInteractionModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.ranged.BowAmmoModifierHook;
import slimeknights.tconstruct.library.tools.capability.EntityModifierCapability;
import slimeknights.tconstruct.library.tools.capability.PersistentDataCapability;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.helper.ModifierUtil;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.item.ranged.ModifiableBowItem;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import java.util.function.Predicate;

/**
 * <b>弹弓</b>（§983）—— 远程武器，部件 ＝ <b>两个弓臂 ＋ 一个弓弦</b> ✓。
 *
 * <h2>行为（用户口径）</h2>
 * <ol>
 *   <li><b>右键长按 = 拉弓</b> ✓ 松开射出 ✓ —— 直接继承匠魂的 {@link ModifiableBowItem}
 *       ⇒ 蓄力、{@code UseAnim.BOW} 动作、拉弓音效、{@code Draw Speed}/{@code Velocity} 面板、
 *       以及所有远程改装钩子全部白拿 ✓；</li>
 *   <li><b>弹药 = 背包里任何"石头/圆石"</b> ✓ —— 覆盖
 *       {@link #getAllSupportedProjectiles()}/{@link #getSupportedHeldProjectiles()} 指向本模组标签
 *       {@code tinkersnewlife:slingshot_ammo} ✓（标签里挂了 {@code #forge:stone} ＋ {@code #forge:cobblestone}
 *       ＋ 几个原版兜底项 ✓ 见 {@code data/tinkersnewlife/tags/items/slingshot_ammo.json} ✓）；</li>
 *   <li><b>射出的是真正的石头实体</b>（{@link StoneShotEntity}）✓ 而不是箭 ✗ ——
 *       {@code releaseUsing} 里匠魂原本写死了 {@code ArrowItem.createArrow} ✗（弹药不是 {@code ArrowItem}
 *       时会退化成普通箭 ✗）⇒ 本类**整段覆盖** {@code releaseUsing} 来换弹射物 ✓。</li>
 * </ol>
 *
 * <p>⚠ 与匠魂长弓的差别：**不做弩炮（ballista）那套** ✗ —— 我们的弹药不是"近战工具"，
 * 也没有"把武器本身射出去"的需求 ⇒ 砍掉整块 ballista 分支，代码短且行为明确 ✓。
 */
public class SlingshotItem extends ModifiableBowItem {

    /** 工具定义 id ＝ {@code tinkersnewlife:slingshot}（与 {@code tool_definitions/slingshot.json} 同名 ✓） */
    public static final ToolDefinition SLINGSHOT_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "slingshot"));

    /** 弹药标签：任何石头/圆石 ✓（用户口径 ✓ 想加别的石头直接往标签里塞 ✓） */
    public static final TagKey<Item> SLINGSHOT_AMMO =
            TagKey.create(Registries.ITEM, new ResourceLocation(TinkersNewlife.MOD_ID, "slingshot_ammo"));

    private static final Predicate<ItemStack> AMMO_PREDICATE = stack -> stack.is(SLINGSHOT_AMMO);

    /** 保底伤害：材料面板万一没给投射物伤害，也不至于打出 0 伤 ✓ */
    private static final float MIN_PROJECTILE_DAMAGE = 2.0F;

    public SlingshotItem(Properties properties) {
        super(properties, SLINGSHOT_DEFINITION);
    }

    // ============================================================
    //  §988 物品栏图标：左下角那颗"石头"
    //    匠魂工具的 ammo 模型块只在 tconstruct:drawback_ammo 里有**物品**时才画东西 ✓
    //    ⇒ 常态塞一颗圆石进去 ⇒ 图标（以及手里）左下角就一直能看到石头 ✓；
    //    拉弓时匠魂会用真正要射的那颗覆盖它 ✓（于是图标/手上显示的就是"当前弹药" ✓）。
    // ============================================================

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        try {
            ToolStack tool = ToolStack.from(stack);
            ModDataNBT data = tool.getPersistentData();
            if (!data.contains(KEY_DRAWBACK_AMMO, CompoundTag.TAG_COMPOUND)) {
                data.put(KEY_DRAWBACK_AMMO, new ItemStack(Items.COBBLESTONE).save(new CompoundTag()));
            }
        } catch (Throwable ignored) {
            // 任何异常都不该影响工具本身 ✓
        }
    }
    // ============================================================
    //  弹药口径
    // ============================================================

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return AMMO_PREDICATE;
    }

    @Override
    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return AMMO_PREDICATE;
    }

    @Override
    public int getDefaultProjectileRange() {
        return 12;
    }

    // ============================================================
    //  发射（覆盖匠魂长弓的整段逻辑，只为把弹射物换成石弹 ✓）
    // ============================================================

    @Override
    public void releaseUsing(ItemStack bow, Level level, LivingEntity living, int timeLeft) {
        ToolStack tool = ToolStack.from(bow);
        int duration = getUseDuration(bow);
        for (ModifierEntry entry : tool.getModifiers()) {
            entry.getHook(ModifierHooks.TOOL_USING).beforeReleaseUsing(tool, entry, living, duration, timeLeft, ModifierEntry.EMPTY);
        }
        if (tool.isBroken()) {
            return;
        }

        Player player = living instanceof Player p ? p : null;
        boolean creative = player != null && player.getAbilities().instabuild;

        // 先"看"有没有弹药（不消耗 ✓ 这与匠魂一致：没弹药就不该放空炮 ✓）
        ItemStack found = BowAmmoModifierHook.getAmmo(tool, bow, living, AMMO_PREDICATE);
        boolean hasAmmo = !found.isEmpty() || creative;

        int chargeTime = duration - timeLeft;
        if (player != null) {
            chargeTime = ForgeEventFactory.onArrowLoose(bow, level, player, chargeTime, hasAmmo);
        }
        if (!hasAmmo || chargeTime < 0) {
            return;
        }

        // 蓄力 → 力度（匠魂口径：charge × VELOCITY 面板 ✓）
        float charge = GeneralInteractionModifierHook.getToolCharge(tool, chargeTime);
        float velocity = ConditionalStatModifierHook.getModifiedStat(tool, living, ToolStats.VELOCITY);
        float power = charge * velocity;
        if (power < 0.1F) {
            return;
        }

        if (!level.isClientSide) {
            // 真正消耗弹药（1 颗 ✓）
            ItemStack ammo = BowAmmoModifierHook.consumeAmmo(tool, bow, living, player, AMMO_PREDICATE, 1);
            if (ammo.isEmpty()) {
                // 只可能是创造模式（上面 hasAmmo 已经放行）⇒ 用圆石兜底 ✓ 而不是匠魂那种"变出一支箭" ✗
                ammo = new ItemStack(Items.COBBLESTONE);
            }
            ItemStack single = ammo.copyWithCount(1);

            float projectileDamage = ConditionalStatModifierHook.getModifiedStat(tool, living,
                    ToolStats.PROJECTILE_DAMAGE, Math.max(MIN_PROJECTILE_DAMAGE, tool.getStats().get(ToolStats.PROJECTILE_DAMAGE)));
            float inaccuracy = ModifierUtil.getInaccuracy(tool, living);

            StoneShotEntity shot = new StoneShotEntity(level, living, single, projectileDamage);
            shot.shootFromRotation(living, living.getXRot(), living.getYRot(), 0.0F, power * 3.0F, inaccuracy);
            if (charge >= 1.0F) {
                shot.setCritArrow(true);
            }

            // 把工具上的改装全部转给弹射物 ＋ 跑远程钩子（海王之力/喜热这类"弹射物"效果就挂在这里 ✓）
            ModifierNBT modifiers = tool.getModifiers();
            EntityModifierCapability.getCapability(shot).addModifiers(modifiers);
            ModDataNBT shotData = PersistentDataCapability.getOrWarn(shot);
            for (ModifierEntry entry : modifiers.getModifiers()) {
                entry.getHook(ModifierHooks.PROJECTILE_LAUNCH).onProjectileLaunch(tool, entry, living, single, shot, shot, shotData, true);
            }

            level.addFreshEntity(shot);
            level.playSound(null, living.getX(), living.getY(), living.getZ(),
                    SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 1.0F,
                    1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + charge * 0.5F);
            ToolDamageUtil.damageAnimated(tool, 1, living, living.getUsedItemHand());
        }

        if (player != null) {
            player.awardStat(Stats.ITEM_USED.get(this));
        }
    }
}
