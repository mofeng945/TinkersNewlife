package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.util.ArmorModifierHelper;
import com.mofengbaizhi.tinkersnewlife.integration.vampirism.VampireIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * 防御槽强化「硬化皮肤」的生效端。
 *
 * <p>规格（用户口径 ✓）：**5 级以上的血族**站在太阳底下时 ——
 * <ul>
 *   <li>不吃 {@code vampirism:sun_damage}（血族自己的阳光伤害 ✓）；</li>
 *   <li>不会被"阳光点燃"（{@code vampirism:vampire_in_fire} / {@code vampire_on_fire}），
 *       已经烧起来的火也会被**每 tick 掐灭** ⇒ 客户端那圈"视野边缘的火焰光晕"不再出现 ✓。</li>
 * </ul>
 *
 * <p>判定边界（故意收窄，不做超出规格的免疫 ✗）：
 * <ul>
 *   <li>{@code sun_damage} 本身就是阳光伤害 ⇒ 一律免；</li>
 *   <li>着火类伤害只在"**真的在阳光下** 且 **不在岩浆里**"时免 ⇒ 掉岩浆、站在营火里照旧会烧 ✓；</li>
 *   <li>掐火同理：只在阳光下 + 不在岩浆里 + 身上确实着着火时才掐 ✓。</li>
 * </ul>
 *
 * <p>⚠ 伤害类型按**注册表键**比对（{@code DamageSource#is(ResourceKey)} ✓），不按 {@code getMsgId()} ——
 * 血族把 {@code sun_damage} 的 {@code message_id} 定成了 {@code "sun"}（见其 damage_type json ✓），
 * 按 msgId 比对既不可靠也容易撞到别的模组 ✗。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HardenedSkinHandler {

    private static final String MODIFIER_ID = "hardened_skin";
    private static final String VAMPIRISM_MOD_ID = "vampirism";

    /** 血的阳光伤害（一律免） */
    private static final ResourceKey<DamageType> SUN_DAMAGE =
            key("sun_damage");
    /** 血族"在火里/身上着火"那两种伤害（只在阳光下、且不在岩浆里时免） */
    private static final ResourceKey<DamageType> VAMPIRE_IN_FIRE =
            key("vampire_in_fire");
    private static final ResourceKey<DamageType> VAMPIRE_ON_FIRE =
            key("vampire_on_fire");

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(VAMPIRISM_MOD_ID, path));
    }

    // ============================================================
    //  一、免伤：LivingAttackEvent 上直接取消（比 LivingHurtEvent 更早 ✓）
    // ============================================================

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        DamageSource source = event.getSource();
        if (!isProtected(entity)) {
            return;
        }
        boolean sunlightOnlyFire = source.is(VAMPIRE_IN_FIRE) || source.is(VAMPIRE_ON_FIRE);
        if (!source.is(SUN_DAMAGE) && !sunlightOnlyFire) {
            return;
        }
        if (sunlightOnlyFire && !isBurningInSunlight(entity)) {
            return;   // 身上的火不是太阳点的（岩浆/营火…）⇒ 不免
        }
        event.setCanceled(true);
        entity.clearFire();
    }

    // ============================================================
    //  二、掐火：把"阳光点的火"每 tick 熄掉（火焰光晕靠这个消失 ✓）
    // ============================================================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide || !player.isOnFire()) {
            return;
        }
        if (!isProtected(player) || !isBurningInSunlight(player)) {
            return;
        }
        player.clearFire();
    }

    // ============================================================
    //  判定
    // ============================================================

    /** 装了本强化 + 是 5 级以上的血族（含 ModList 判定，未装血族直接 false ✓） */
    private static boolean isProtected(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        if (!ArmorModifierHelper.hasModifierOnArmor(player, MODIFIER_ID)) {
            return false;
        }
        if (!ModList.get().isLoaded(VAMPIRISM_MOD_ID)) {
            return false;   // ⭐ 先判 ModList，再碰 VampireIntegration
        }
        return VampireIntegration.isHighRankVampire(player);
    }

    /** 「太阳点的火」：真的暴露在阳光下，而且不在岩浆里（岩浆的火照旧烧 ✓） */
    private static boolean isBurningInSunlight(LivingEntity entity) {
        if (entity.isInLava()) {
            return false;
        }
        Level level = entity.level();
        if (!level.isDay()) {
            return false;
        }
        BlockPos pos = entity.blockPosition();
        return level.canSeeSky(pos) && !level.isRainingAt(pos);
    }
}
