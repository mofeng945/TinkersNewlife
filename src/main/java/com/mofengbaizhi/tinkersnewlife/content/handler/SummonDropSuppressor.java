package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 召唤物<b>不掉落</b>：荡蕴平线召唤的溺尸、十影式神 —— 死亡不掉任何战利品，也不给经验。
 *
 * <p><b>为什么</b>：它们都是"咒力变出来的临时单位"。让它们照常结算原版战利品，
 * 就是一条稳定的刷材料/刷经验路线 —— 式神里有牛/羊/铁傀儡（皮革、羊毛、铁锭…），
 * 荡蕴平线的溺尸还带腐肉。处理方式与无为转变出来的单位同款：在标准掉落/经验事件里取消。
 *
 * <p>识别方式：
 * <ul>
 *   <li><b>十影式神</b>：11 种式神实体都 {@code implements ShikigamiMob}，一个 {@code instanceof} 全覆盖；</li>
 *   <li><b>荡蕴平线的溺尸</b>：召唤时打了 {@code tnl_dangyun_owner} 持久标记
 *       （见 {@code DangYunPingXianDomain.isSummonedDrowned}）。</li>
 * </ul>
 *
 * <p>注意：这两类召唤物都不手动 {@code spawnAtLocation}，全部走标准掉落事件，所以在这里取消是完整的。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SummonDropSuppressor {

    private SummonDropSuppressor() {
    }

    /** 不掉战利品 */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (isNoDropSummon(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** 也不给经验（否则"刷式神"仍然划算） */
    @SubscribeEvent
    public static void onExperience(LivingExperienceDropEvent event) {
        if (isNoDropSummon(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** 该实体是不是"死亡不该掉任何东西"的召唤物 */
    public static boolean isNoDropSummon(LivingEntity entity) {
        if (entity == null) return false;
        // 十影式神：11 种实体都实现该接口
        if (entity instanceof com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiMob) return true;
        // 荡蕴平线召唤的溺尸：带专用持久标记
        return com.mofengbaizhi.tinkersnewlife.content.curse.domain.DangYunPingXianDomain
                .isSummonedDrowned(entity);
    }
}
