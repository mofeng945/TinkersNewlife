package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.ChaosFlowModifier;
import com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks.IronSpellsSpellAccess;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.List;

/**
 * 特性「<b>混沌之流</b>」结算器（铁魔法联动，见 {@link ChaosFlowModifier}）。
 *
 * <p>做法：在 {@code LivingHurtEvent}（{@link EventPriority#LOWEST}，即别人都改完之后）里，
 * 如果这次伤害来自"带混沌之流的近战 / 弹射工具"：
 * <ol>
 *   <li><b>取消原始那一次</b> ✓；</li>
 *   <li>把总伤害均分成 <b>1 + 学派数</b> 段，先施加<b>一段物理</b>，
 *       再为<b>每个学派</b>各施加一段该学派的法术伤害 ✓；</li>
 *   <li>每段各按自己的伤害类型结算 → 目标的各项抗性分别生效 ✓（这正是这个特性的意义）。</li>
 * </ol>
 *
 * <h2>为什么学派是"动态"的</h2>
 * 学分数从铁魔法的<b>学派注册表</b>里现取（{@code SchoolRegistry}#getAllSchools），
 * 每个学派自带 {@code getDamageType()} ✓ —— 所以<b>附属模组新加的学派也自动算进来</b> ✓，
 * 不需要我们维护一张表 ✗。例：默认 9 个学派 → 共 10 段。
 *
 * <h2>几个必须处理的坑</h2>
 * <ul>
 *   <li><b>递归</b>：我们施加的每一段又会触发 {@code LivingHurtEvent} ✗ → 用
 *       {@link ThreadLocal} 标记把重入挡掉 ✓（我们施加的多段伤害<b>不再被拆分</b> ✓）；</li>
 *   <li><b>无敌帧</b>：第二段开始会被目标的 {@code invulnerableTime} 吃掉 ✗ →
 *       每段前后手动清零 ✓，结束时恢复成原版命中后的 20 tick ✓，所以"挨一下的无敌时间"手感不变 ✓；</li>
 *   <li><b>已死目标</b>：中途死去时后续段自然落空 ✓（不特殊处理）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChaosFlowHandler {

    private ChaosFlowHandler() {
    }

    /** 拆分中标记：防止我们施加的分段伤害又被自己拆一遍 */
    private static final ThreadLocal<Boolean> SPLITTING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (Boolean.TRUE.equals(SPLITTING.get())) return;

        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker)) return;
        if (attacker == target) return;

        ToolStack tool = ToolHelper.getCombatToolWith(source, attacker, ChaosFlowModifier.ID);
        if (tool == null || tool.getModifierLevel(ChaosFlowModifier.ID) <= 0) {
            logDiagnostic(attacker, tool);                       // 手里有匠魂工具但没这个特性 → 记一笔 ✓
            return;
        }

        float total = event.getAmount();
        if (total <= 0.0F) return;

        List<ResourceKey<DamageType>> schoolKeys = IronSpellsSpellAccess.schoolDamageKeys();
        if (schoolKeys.isEmpty()) {
            logOnce("[混沌之流] 学派注册表为空（铁魔法不在场或反射失败）→ 本次不拆分");
            return;
        }

        int segments = 1 + schoolKeys.size();
        float per = total / segments;
        if (per <= 0.0F) return;

        if (DEBUG) TinkersNewlife.LOGGER.info("[混沌之流] {} 的 {} 点伤害拆成 {} 段（每段 {}，学派 {} 个）",
                attacker.getName().getString(), total, segments, per, schoolKeys.size());

        event.setCanceled(true);                                 // 原始那一次不再结算 ✓
        SPLITTING.set(Boolean.TRUE);
        try {
            target.invulnerableTime = 0;
            target.hurt(physicalSource(target, attacker), per);   // ① 物理段
            for (ResourceKey<DamageType> key : schoolKeys) {
                DamageSource schoolSource = schoolSource(target, attacker, key);
                if (schoolSource == null) continue;
                if (target.isDeadOrDying()) break;
                target.invulnerableTime = 0;                      // ② 每段都清无敌帧
                target.hurt(schoolSource, per);
            }
            target.invulnerableTime = 20;                         // 恢复成原版命中后的无敌时间 ✓
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.debug("[混沌之流] 分段失败（已忽略）: {}", t.toString());
        } finally {
            SPLITTING.set(Boolean.FALSE);
        }
    }

    // ============================================================
    //  诊断（排查"为什么只看到一段伤害"用；限流，不刷屏）
    // ============================================================

    /** 诊断日志开关（排查"为什么只看到一段伤害"时改 true ✓；平时保持 false 不刷屏） */
    private static final boolean DEBUG = false;
    private static volatile long lastLogTime = 0L;

    /** 手里拿着匠魂工具、但这个工具上没有「混沌之流」→ 记一笔（5 秒最多一条） */
    private static void logDiagnostic(LivingEntity attacker, ToolStack tool) {
        if (!DEBUG) return;
        if (tool == null) return;
        long now = System.currentTimeMillis();
        if (now - lastLogTime < 5000L) return;
        lastLogTime = now;
        TinkersNewlife.LOGGER.info("[混沌之流] 攻击者 {} 手持 {} 但没有该特性（工具等级 0）→ 未拆分",
                attacker.getName().getString(), tool.getItem());
    }

    private static void logOnce(String message) {
        if (!DEBUG) return;
        long now = System.currentTimeMillis();
        if (now - lastLogTime < 5000L) return;
        lastLogTime = now;
        TinkersNewlife.LOGGER.info(message);
    }

    // ============================================================
    //  伤害源
    // ============================================================

    /** 物理段：沿用原版的物理类型（保留攻击者 → 击杀归属不丢 ✓） */
    private static DamageSource physicalSource(LivingEntity target, LivingEntity attacker) {
        Holder<DamageType> holder = target.damageSources().mobAttack(attacker).typeHolder();
        return new DamageSource(holder, attacker, attacker);
    }

    /** 学派段：用该学派的法术伤害类型（{@code SchoolType#getDamageType()}）✓ */
    private static DamageSource schoolSource(LivingEntity target, LivingEntity attacker, ResourceKey<DamageType> key) {
        try {
            if (!(target.level() instanceof ServerLevel serverLevel)) return null;
            Holder<DamageType> holder = serverLevel.registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
            return new DamageSource(holder, attacker, attacker);
        } catch (Throwable t) {
            return null;
        }
    }
}
