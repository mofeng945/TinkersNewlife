package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.common.TinkerDamageTypes;

/**
 * 兼容处理：<b>让冶炼炉能熔「充能（闪电）苦力怕」，同时保住铁魔法赋予它的"火焰免疫"</b>。
 *
 * <h2>要解决的是什么（备忘录 §652 已查明 ✓）</h2>
 * 铁魔法（irons_spellbooks）的配置 {@code ServerConfigs.BETTER_CREEPER_THUNDERHIT}
 * （**默认 true** ✓）会让被雷劈过的苦力怕 <b>"被治疗 + 变成火焰免疫"</b> ✓。
 * 由于 {@code fireImmune()} 来自实体类型、模组改不了 ✗，它的实现方式是
 * {@code ServerPlayerEvents} 里把「**伤害带 {@code is_fire} 标签 + 目标充能苦力怕**」的
 * {@link LivingAttackEvent} <b>直接取消</b> ✗。
 * <p>
 * 而匠魂的 {@code tconstruct:smeltery_heat} <b>正好在 {@code minecraft:is_fire} 标签里</b> ✗
 * ⇒ 冶炼炉对充能苦力怕的每一次伤害都被取消 ⇒ 匠魂的 {@code hurt} 返回 false
 * ⇒ <b>{@code tank.fill} 不执行 ⇒ 什么流体都不出</b> ✗
 * （是「**根本不熔炼**」✓，不只是"不出液态闪电" ✗）。
 *
 * <h2>本类怎么做（外科式 ✓ 不动火免 ✓ 不需要 mixin ✓）</h2>
 * 在<b>最低优先级</b>、且 {@code receiveCanceled = true} 地监听 {@link LivingAttackEvent} ✓
 * —— 此时其它监听器（含铁魔法那个）都已跑完 ✓ ——
 * <b>只针对「伤害源恰好是 {@code tconstruct:smeltery_heat} + 目标是充能苦力怕」</b>
 * 把取消撤销（{@code setCanceled(false)}）✓。
 * <ul>
 *   <li>✅ <b>冶炼炉</b>（而且<b>只有</b>冶炼炉 ✓）能正常熔充能苦力怕 ⇒ 我们的配方就能出液态闪电 ✓；</li>
 *   <li>✅ <b>真正的火焰伤害照旧被铁魔法挡住</b> ✓ —— 火焰/岩浆/着火/火焰法术用的是
 *       {@code in_fire} / {@code on_fire} / {@code lava} / {@code fire_magic} 等
 *       <b>别的伤害源</b> ✓，不匹配下面那条判据 ✓
 *       ⇒ <b>火免没有被破坏</b> ✓（只是把"冶炼炉"从它的误伤范围里摘出来 ✓）；</li>
 *   <li>✅ <b>普通苦力怕不受影响</b> ✓（它本来就没被取消过 ✓ 本类只在 {@code isCanceled()} 时才动手 ✓）。</li>
 * </ul>
 *
 * <h2>⚠ 为什么可以这样做（不是"绕过别人的意图" ✗）</h2>
 * 铁魔法那条配置的意图是「<b>充能苦力怕免疫火焰</b>」✓，
 * 而冶炼炉用火焰伤害只是<b>匠魂的实现细节</b> ✗ —— 它并不是"火焰"这个玩法概念 ✓
 * （玩家在冶炼炉里熔怪 ≠ 用火烧它 ✓）。所以把「冶炼炉」这一类摘出来，
 * 既不违背铁魔法的意图 ✓，又让匠魂的熔炼按它自己的规则工作 ✓。
 *
 * <h2>⚠ 优先级为什么必须是 LOWEST</h2>
 * 铁魔法那个监听器用的是默认优先级（NORMAL）✓ ⇒ 跑在我们前面 ✓
 * 我们若用更高优先级，会在它<b>之前</b>执行、那时 {@code isCanceled()} 还是 false ✗ ⇒ 判据不成立 ✗。
 * <p>⚠ 同时必须 {@code receiveCanceled = true} ✗ ——
 * {@code @SubscribeEvent} <b>默认不接收已取消的事件</b> ✗（这个坑在 §651 让我误判过三轮 ✗）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChargedCreeperMeltingCompatHandler {

    private ChargedCreeperMeltingCompatHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void allowSmelteryToMeltChargedCreeper(LivingAttackEvent event) {
        // 只在"已经被取消"时才需要插手 ⇒ 普通情况零开销、也绝不会把别的模组的保护撤销 ✓
        if (!event.isCanceled()) return;
        if (!(event.getEntity() instanceof Creeper creeper)) return;
        if (creeper.level().isClientSide) return;
        if (!creeper.isPowered()) return;
        // ⚠ 判据必须**精确到匠魂那一种伤害源** ✗ ⇒ 别的火焰伤害（in_fire/on_fire/lava/fire_magic…）
        //   不匹配 ⇒ 照旧被铁魔法挡掉 ⇒ 火免保持不变 ✓
        if (!event.getSource().typeHolder().is(TinkerDamageTypes.SMELTERY_HEAT)) return;
        event.setCanceled(false);
    }
}
