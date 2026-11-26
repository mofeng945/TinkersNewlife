package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * <b>咒力伤害致死 → 统一死亡信息「……被诅咒致死」</b>。
 *
 * <h2>为什么要"打标记"而不是换伤害类型</h2>
 * 本模组的咒力伤害基本都借用了**原版伤害类型**（{@code magic()} / {@code mobAttack(player)} /
 * {@code playerAttack(player)} / {@code genericKill()}，见各术式/领域里的 {@code hurt()} 调用），
 * 靠伤害类型**分不出**"这是咒力打的"和"这是普通怪打的" ✗；
 * 换成一整套自定义伤害类型又会改动护甲/附魔/无敌帧的结算行为 ✗（用户只要改**死亡信息**）。
 * 所以这里只在伤害**发生时**记一笔"刚刚被咒力打过"，死亡信息生成时查这一笔 ✓
 * —— 数值、护甲、击退等一律原样不动 ✓。
 *
 * <h2>标记必须打在 hurt() **之前**</h2>
 * 死亡信息是在 {@code LivingEntity.hurt()} 内部（玩家是 {@code ServerPlayer.die()} 里
 * 取 {@code CombatTracker#getDeathMessage()} 塞进死亡包 + 广播）**当场**产生的 ✗ ——
 * 所以"事后补标记"（例如 {@code afterCurseCoreHit}）来不及 ✓；
 * 所有打标记的位置都在对应的 {@code hurt()} 调用**之前** ✓。
 *
 * <h2>覆盖方式（三层，尽量不漏）</h2>
 * <ol>
 *   <li>{@link CurseCoreTraitHelper#applyCurseCoreTraits} —— 本模组绝大多数术式/领域/咒具弹射物
 *       在结算伤害前都会调它（{@code hurt()} 之前 ✓），一处标记覆盖二十多处调用点 ✓；</li>
 *   <li>{@link #onLivingHurt} —— <b>本模组实体</b>造成的伤害（咒力弹/式神/傀儡/咒灵/幻翼…）
 *       统一按咒力伤害算 ✓（按实体注册命名空间判定，漏点也兜得住 ✓）；</li>
 *   <li>其余"伤害源是玩家、或根本没有来源实体"的调用点（各领域直伤、咒言、反转术式兜底、
 *       天逆鉾处决、狱门疆…）逐个显式调 {@link #mark} ✓。</li>
 * </ol>
 *
 * <p>文案：{@code death.attack.tinkersnewlife.curse} = 中「%1$s被诅咒致死」/ 英「%1$s was cursed to death」✓
 * （只带死者名字，不带凶手名 —— 用户要的是统一口径 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CurseDeath {

    private CurseDeath() {}

    /** 实体持久数据：咒力伤害标记的有效截止 tick（服务器 gameTime） */
    private static final String KEY_UNTIL = "tinkersnewlife.curse_hit_until";

    /** 统一死亡信息语言键（%1$s = 死者显示名） */
    public static final String MESSAGE_KEY = "death.attack.tinkersnewlife.curse";

    /**
     * 记一笔"这一下是咒力伤害"。
     * <p>⚠ <b>必须在 {@code hurt()} 之前调用</b>：死亡信息在 {@code hurt()} 内部就定下了 ✗。
     */
    public static void mark(@Nullable LivingEntity victim) {
        if (victim == null || victim.level().isClientSide) return;
        victim.getPersistentData().putLong(KEY_UNTIL, victim.level().getGameTime() + 1);
    }

    /** 该实体是不是"刚刚"（同一 tick 内）被咒力伤害打过 */
    public static boolean wasCursedRecently(@Nullable LivingEntity victim) {
        if (victim == null || victim.level().isClientSide) return false;
        return victim.getPersistentData().getLong(KEY_UNTIL) >= victim.level().getGameTime();
    }

    /** 咒力致死则返回统一文案，否则原样返回原版文案 ✓ */
    public static Component messageOrDefault(LivingEntity victim, Component original) {
        if (!wasCursedRecently(victim)) return original;
        return Component.translatable(MESSAGE_KEY, victim.getDisplayName());
    }

    /**
     * 本模组实体造成的伤害一律按"咒力伤害"记账（咒力弹/式神/傀儡/咒灵/幻翼/黑鸟…）。
     * <p>按<b>实体注册命名空间</b>判定：漏掉的调用点也兜得住 ✓；玩家自己动手（刀剑等）不算 ✗，
     * 那部分由各术式显式 {@link #mark} ✓。
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        if (attacker == null || attacker instanceof Player) return;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(attacker.getType());
        if (id != null && TinkersNewlife.MOD_ID.equals(id.getNamespace())) {
            mark(event.getEntity());
        }
    }
}
