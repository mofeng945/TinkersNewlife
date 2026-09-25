package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ⚠⚠ <b>临时诊断 mixin</b>（定位"充能苦力怕在冶炼炉里不熔" ⇒ 定位完**整个文件删掉** ✗，
 * 并从 {@code tinkersnewlife.mixins.json} 的 {@code mixins} 列表里移除 ✗）。
 *
 * <h2>为什么必须动 mixin（前面几轮事件层探针都不够）</h2>
 * 匠魂的实体熔炼判定是<b>私有方法</b>
 * {@code EntityMeltingModule#canMeltEntity(LivingEntity)} ✓，调用它的
 * {@code interactWithEntities()} 只在**通过之后**才会 {@code entity.hurt(...)} ✓。
 * 前面几轮我在 {@code LivingAttackEvent} 上探针 ⇒ 只能看到"没被攻击" ✗，
 * 看不到"**为什么**没被攻击"（是判据 false？还是压根没扫到这只？）✗。
 *
 * <p>这里直接在<b>判据返回处</b>打点 ⇒ 一刀切开：
 * <ul>
 *   <li>日志<b>有</b>且 {@code result=false} ⇒ 是判据把它挡了 ⇒ 看那三个子条件哪个为真；</li>
 *   <li>日志<b>有</b>且 {@code result=true} ⇒ 判据放行了 ⇒ 病在更后面（包围盒/伤害/填罐）；</li>
 *   <li>日志<b>一条都没有</b> ⇒ 它<b>根本没进这个循环</b>（不在包围盒里 / `isAlive` 为假）⇒ 病在扫描范围。</li>
 * </ul>
 *
 * <p>⚠ 只打苦力怕（含普通与充能 ⇒ 天然有对比组 ✓），不会刷屏 ✓。
 */
@Mixin(targets = "slimeknights.tconstruct.smeltery.block.entity.module.EntityMeltingModule")
public class EntityMeltingDiagMixin {

    /**
     * ⚠ **这一段是"mixin 到底注入成功没有"的信号** ✓ 必须留着 ✗：
     * {@code require = 0} 的注入失败是**静默**的 ✗（不报错、不注入）⇒
     * 若没有这条日志，"熔炼判据诊断一条都没有"就**无法区分**是"没进循环"还是"mixin 压根没生效" ✗
     * ⇒ 那会把我引向完全错误的结论 ✗（前几轮我已经栽过两次类似的坑 ✓）。
     */
    static {
        TinkersNewlife.LOGGER.info("[熔炼判据诊断] mixin 已注入 EntityMeltingModule ✓（若看不到本行 ⇒ mixin 没生效，别看后面的日志）");
    }

    /**
     * 在 {@code canMeltEntity} 返回处打点：把"判据结果 + 匠魂用到的三个子条件"一起打出来。
     *
     * <p>三个子条件照抄匠魂源码（{@code EntityMeltingModule.java:110-117}）：
     * <pre>
     * return !entity.isInvulnerableTo(entity.fireImmune() ? smelteryMagic() : smelteryHeat())
     *        && !(entity instanceof Player &amp;&amp; ((Player)entity).getAbilities().invulnerable)
     *        &amp;&amp; !entity.hasEffect(MobEffects.FIRE_RESISTANCE);
     * </pre>
     */
    // ⚠ `remap = false` **必须加** ✗：`canMeltEntity` 是**匠魂自己的方法名**（不是 MC 方法）✓
    //   ⇒ 不需要、也不能走 refmap 重映射 ✓。本模组的 refmap 是**手写**的
    //   （见 build.gradle:153-156：没用注解处理器 ⇒ 报 "Unable to locate obfuscation mapping"）✗
    //   ⇒ 若这里用默认的 remap=true，Mixin 会去 refmap 里找 `canMeltEntity` 的映射、找不到 ✗
    //   配合 `require = 0` 就会**静默不注入** ✗ ⇒ 表现为"一条日志都没有"，把我引向错误结论 ✗。
    //   包内先例：`JadeObjectNameMixin`（打 Jade 的 getEntityName）/ `AllPathsOneSoulMixin`（打 ISS 方法）都写 remap=false ✓。
    @Inject(method = "canMeltEntity", at = @At("RETURN"), require = 0, remap = false)
    private void tinkersnewlife$logCanMeltEntity(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!(entity instanceof Creeper creeper)) return;
            if (entity.level().isClientSide) return;
            boolean fireImmune = creeper.fireImmune();
            // 匠魂用 fireImmune() 在"高温"和"魔法"两个伤害源之间二选一 ⇒ 两个都问一遍 ✓
            boolean invHeat = creeper.isInvulnerableTo(
                    slimeknights.tconstruct.common.TinkerDamageTypes.source(
                            creeper.level().registryAccess(),
                            slimeknights.tconstruct.common.TinkerDamageTypes.SMELTERY_HEAT));
            boolean invMagic = creeper.isInvulnerableTo(
                    slimeknights.tconstruct.common.TinkerDamageTypes.source(
                            creeper.level().registryAccess(),
                            slimeknights.tconstruct.common.TinkerDamageTypes.SMELTERY_MAGIC));
            TinkersNewlife.LOGGER.info(
                    "[熔炼判据诊断] canMeltEntity -> {} | powered={} hp={}/{} uuid={} pos={},{},{} | "
                            + "fireImmune={} invulnToHeat={} invulnToMagic={} fireRes={} isRemoved={} invulnerable={}",
                    cir.getReturnValue(), creeper.isPowered(),
                    creeper.getHealth(), creeper.getMaxHealth(), creeper.getUUID(),
                    creeper.getBlockX(), creeper.getBlockY(), creeper.getBlockZ(),
                    fireImmune, invHeat, invMagic,
                    creeper.hasEffect(MobEffects.FIRE_RESISTANCE),
                    creeper.isRemoved(), creeper.isInvulnerable());
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[熔炼判据诊断] 打点自身出错（不影响游戏）：{}", t.toString());
        }
    }
}
