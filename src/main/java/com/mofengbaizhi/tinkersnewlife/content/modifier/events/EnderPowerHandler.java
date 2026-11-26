package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.EnderPowerModifier;
import com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks.IronSpellsSpellAccess;
import com.mofengbaizhi.tinkersnewlife.util.IronSpellsReflector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>末影之力</b>（虚空金属工具自带，无等级）：把 <b>3 级传送术 + 3 级法术镣铐</b> 注入到手里的工具上。
 *
 * <h2>为什么是"每 20 tick 兜底一次"而不是事件</h2>
 * 与"破法"（{@code SpellBreakModifier}）完全同一套做法 ✓：
 * <ul>
 *   <li>工具可能在任何时候获得这个特性（工匠砧换部件、铸造台浇筑、指令…✓），逐个挂事件既散又容易漏 ✗；</li>
 *   <li>{@code IronSpellsSpellAccess#ensureInscribedPair} <b>只在没有容器时写入</b> ✓ ⇒ 每 20 tick 重复调用是幂等的，
 *       而且**不会覆盖玩家在奥术铁砧上的编辑** ✓；</li>
 *   <li>服务端跑、只看主手/副手 ⇒ 开销可忽略 ✓。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EnderPowerHandler {

    private EnderPowerHandler() {}

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (player.tickCount % 20 != 0) return;
        if (!IronSpellsReflector.isIronSpellsAvailable()) return;

        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            if (!EnderPowerModifier.has(stack)) continue;
            IronSpellsSpellAccess.ensureInscribedPair(stack,
                    EnderPowerModifier.SPELL_TELEPORT,
                    EnderPowerModifier.SPELL_SHACKLE,
                    EnderPowerModifier.INSCRIBED_LEVEL,
                    EnderPowerModifier.CONTAINER_SLOTS);
        }
    }
}
