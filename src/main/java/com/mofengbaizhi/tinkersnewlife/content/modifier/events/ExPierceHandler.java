package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.ExPierceModifier;
import com.mofengbaizhi.tinkersnewlife.util.ProjectileWeaponHelper;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 穿透EX（ex_pierce）结算器：把佩戴该修饰符的工具命中改写成「穿透」伤害源再结算。
 *
 * <p>为什么必须重打而不是在 LivingHurtEvent 里改数值：灾变 Boss 的无敌/限伤判断
 * （利维坦离水无敌、阶段二、LLibrary_Boss_Monster 的 damageBucket 伤害桶/伤害上限）
 * 都在实体 hurt() 的<b>最前面</b>，只看伤害源的
 * {@code #minecraft:bypasses_invulnerability} 标签——原版箭/近战源的标签固定，
 * 事件层根本到不了；凋灵出生无敌同样只认该标签、半血「免疫箭矢」则看 direct 实体是不是箭。
 * 因此只能在伤害发生前拦截（玩家近战 AttackEntityEvent、投射物 ProjectileImpactEvent），
 * 取消原始伤害后用带标签的自定义伤害源重打：
 * <ul>
 *   <li>近战：伤害 = 攻击者 ATTACK_DAMAGE 属性总值（含工具面板与多数词条加成）；</li>
 *   <li>远程：伤害 = 投射物 getBaseDamage()（TCon ModifiableArrow 发射时已被设为
 *       工具 PROJECTILE_DAMAGE 面板值），direct/causing 实体取射手（破凋灵半血箭免）；</li>
 *   <li>新源 type = tinkersnewlife:ex_pierce（数据包 damage_type + 两个标签）。</li>
 * </ul>
 * 玩家近战与任意射手（玩家/仆从）的远程有效；非玩家近战（怪/仆从挥砍）无预伤害事件，暂不覆盖。
 * 多段 Boss（灾变利维坦等）受击实体是 PartEntity → 先解析回主实体再重打（见 {@link #resolveTarget}）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ExPierceHandler {

    /** 穿透伤害类型：复用本 mod 早已打上两个 bypass 标签的 true_pierce（data/minecraft/tags/damage_type/） */
    private static final ResourceKey<DamageType> PIERCE_TYPE_KEY = ResourceKey.create(
            Registries.DAMAGE_TYPE, new ResourceLocation(TinkersNewlife.MOD_ID, "true_pierce"));

    /** 服务端 damage_type 注册表实例在单服务内恒定，惰性缓存 Holder */
    private static volatile Holder<DamageType> cachedPierceType = null;

    private ExPierceHandler() {
    }

    /** 玩家近战：主手工具带 ex_pierce → 取消原伤害，用穿透源重打 */
    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        LivingEntity target = resolveTarget(event.getTarget());
        if (target == null) return;
        if (!hasExPierce(player.getMainHandItem())) return;
        event.setCanceled(true);
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (damage <= 0) return;
        applyPierce(player, target, damage);
    }

    /** 投射物命中：射手（玩家/仆从等）的远程工具带 ex_pierce → 取消原命中，用穿透源重打 */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Entity projectile = event.getEntity();
        if (projectile.level().isClientSide) return;
        if (!(projectile instanceof AbstractArrow arrow)) return;
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        LivingEntity target = resolveTarget(hit.getEntity());
        if (target == null) return;
        if (!(arrow.getOwner() instanceof LivingEntity shooter)) return;
        ItemStack weapon;
        if (shooter instanceof Player playerShooter) {
            weapon = ProjectileWeaponHelper.getProjectileWeapon(arrow, playerShooter);
        } else {
            // 仆从等非玩家射手：弓在主手（goety 仆从 AI 用主手匠魂弓）
            weapon = shooter.getMainHandItem();
        }
        if (weapon.isEmpty() || !hasExPierce(weapon)) return;
        event.setCanceled(true);
        float damage = (float) arrow.getBaseDamage();
        if (damage <= 0) damage = 1.0f;
        applyPierce(shooter, target, damage);
        // 取消后原命中不结算；直接移除箭避免继续飞行二次命中
        projectile.discard();
    }

    /**
     * 两段式穿透结算：
     * <ol>
     *   <li>先用带攻击者的穿透源（direct/causing=攻击者）：保住击杀归属与依赖
     *       source.getEntity() 的伤害加成；可穿灾变限伤/离水、凋灵出生无敌/半血箭免、无敌帧。</li>
     *   <li>若被目标无条件挡下（hurt 返回 false）——典型如 Mowzie 钢铁守护者
     *       (EntityWroughtnaut)：vulnerable=false 无敌期与正面盾挡分支都要求「有攻击者」且
     *       不看标签；其标签豁免只在无直接攻击者时生效 → 改用<b>无主穿透源</b>
     *       (direct=null) 重打，走标签分支强制造成伤害。</li>
     * </ol>
     */
    private static void applyPierce(LivingEntity attacker, LivingEntity target, float damage) {
        if (!target.hurt(pierceSource(attacker), damage) && target.isAlive() && !target.isRemoved()) {
            target.hurt(anonymousPierceSource(target.level()), damage);
        }
    }

    /**
     * 解析实际受伤目标：多段 Boss（如灾变利维坦）的受击实体是 PartEntity（非 LivingEntity），
     * 其 hurt 仅转发到主实体（attackEntityFromPart → 主实体 hurt）。这里把 part 解析回主实体，
     * 再以穿透源重打主实体，才能让主实体 hurt() 内的无敌/限伤判断吃到 bypass 标签。
     */
    private static LivingEntity resolveTarget(Entity hit) {
        if (hit instanceof LivingEntity living) return living;
        if (hit instanceof net.minecraftforge.entity.PartEntity<?> part
                && part.getParent() instanceof LivingEntity parent) {
            return parent;
        }
        return null;
    }

    private static boolean hasExPierce(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(stack);
        return tool != null && tool.getModifierLevel(ExPierceModifier.ID) > 0;
    }

    /** 构造穿透伤害源：direct/causing 都取攻击者（凋灵半血箭免检查 direct 是否为箭） */
    private static DamageSource pierceSource(LivingEntity attacker) {
        return new DamageSource(pierceType(attacker), attacker, attacker);
    }

    /** 无主穿透源（direct/causing 均 null）：Mowzie 钢铁守护者等"有攻击者即免疫"的目标
     *  只有在无直接攻击者时才检查 bypasses_invulnerability 标签 → 走此源强制穿透 */
    private static DamageSource anonymousPierceSource(net.minecraft.world.level.Level level) {
        return new DamageSource(pierceTypeFromLevel(level), null, null);
    }

    private static Holder<DamageType> pierceType(Entity context) {
        return pierceTypeFromLevel(context.level());
    }

    private static Holder<DamageType> pierceTypeFromLevel(net.minecraft.world.level.Level level) {
        Holder<DamageType> cached = cachedPierceType;
        if (cached == null) {
            cached = level.registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(PIERCE_TYPE_KEY);
            cachedPierceType = cached;
        }
        return cached;
    }
}
