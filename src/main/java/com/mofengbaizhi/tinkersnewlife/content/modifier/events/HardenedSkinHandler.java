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
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 防御槽强化「硬化皮肤」的生效端。
 *
 * <p>规格（用户口径 ✓）：**5 级以上的血族**站在太阳底下时 ——
 * 不受阳光伤害、不会反胃、不会虚弱、不会被阳光点着（视野边缘那圈火光晕随之消失 ✓）。
 *
 * <p><b>主机制：把血族自己的「防晒」（{@code vampirism:sunscreen}）拉满 ✓</b> ——
 * 这是游戏原生、也是官方阳伞的做法（反编译 {@code VampiresNeedUmbrellas} 的 {@code SunscreenEffectInstance}：
 * 持续 21 tick、**等级 5**、每 tick 刷新 ✓）。血族 {@code VampirePlayer#handleSunDamage} 的三道判定分别是：
 * <ul>
 *   <li>反胃（{@code MobEffects.CONFUSION}）：要求 {@code sunscreen == -1} ⇒ 任何防晒都能挡 ✓；</li>
 *   <li>虚弱（{@code MobEffects.WEAKNESS}）：要求 {@code sunscreen < 5} ⇒ **必须 5 级**才挡得住 ✓；</li>
 *   <li>阳光伤害：要求 {@code ticksInSun ≥ 100}，而 {@code sunscreen ≥ 4} 会把 {@code ticksInSun} 卡在 50 ✓；
 *       另外 5 级防晒还把「阳光伤害」属性按每级 −50% 叠成负值 ⇒ 伤害直接为 0 ✓。</li>
 * </ul>
 * 所以我们只做一件事：**在阳光下把 5 级防晒维持住** ✓（离开阳光后 3 秒自然过期 ✓）。
 *
 * <p><b>双保险</b>：{@code VampirismSunHelperMixin} 另外把血族的 {@code Helper#gettingSundamge} 对受保护玩家压成
 * {@code false} ✓ —— 这样连"以阳光状态为条件的其它逻辑"也一并停掉（万一血族以后改了防晒数值，这条也还在 ✓）。
 *
 * <p>判定边界（故意收窄，不做超出规格的免疫 ✗）：只在**真的暴露在阳光下**（白天 ＋ 头顶见天 ＋ 头顶没下雨）时生效；
 * 掉岩浆、走营火照旧会烧 ✓。
 *
 * <p>⚠ 伤害类型按**注册表键**比对（{@code DamageSource#is(ResourceKey)} ✓），不按 {@code getMsgId()} ——
 * 血族把 {@code sun_damage} 的 {@code message_id} 定成了 {@code "sun"}（见其 damage_type json ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HardenedSkinHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(HardenedSkinHandler.class);
    private static final String MODIFIER_ID = "hardened_skin";
    private static final String VAMPIRISM_MOD_ID = "vampirism";

    /** 血族的「防晒」效果（官方阳伞给的就是它 ✓） */
    private static final ResourceLocation SUNSCREEN_EFFECT_ID = new ResourceLocation(VAMPIRISM_MOD_ID, "sunscreen");
    /** 防晒等级：**5** —— 反胃/虚弱/阳光伤害三道全挡的最小值 ✓（与官方阳伞一致 ✓） */
    private static final int SUNSCREEN_AMPLIFIER = 5;
    /** 单次施加的时长（每 tick 检查，快过期才续 ⇒ 不刷包 ✓；离开阳光 ~3 秒后自然消失 ✓） */
    private static final int SUNSCREEN_DURATION = 60;

    /** 血族的阳光伤害（一律免） */
    private static final ResourceKey<DamageType> SUN_DAMAGE = key("sun_damage");
    /** 血族"在火里/身上着火"那两种伤害（只在阳光下、且不在岩浆里时免） */
    private static final ResourceKey<DamageType> VAMPIRE_IN_FIRE = key("vampire_in_fire");
    private static final ResourceKey<DamageType> VAMPIRE_ON_FIRE = key("vampire_on_fire");

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
        boolean sunlightDamage = source.is(SUN_DAMAGE);
        boolean sunlightFire = source.is(VAMPIRE_IN_FIRE) || source.is(VAMPIRE_ON_FIRE);
        if (!sunlightDamage && !sunlightFire) {
            return;   // 跟阳光无关的伤害：静默放行
        }
        if (!(entity instanceof Player player) || !ArmorModifierHelper.hasModifierOnArmor(player, MODIFIER_ID)) {
            return;   // 没装这条强化：静默（否则每次阳光伤害都刷一条）
        }
        if (!ModList.get().isLoaded(VAMPIRISM_MOD_ID)) {
            LOGGER.debug("[硬化皮肤] {} 装了这一条，但血族模组不在场 ⇒ 不免疫 {}", player.getName().getString(), source.getMsgId());
            return;
        }
        if (!VampireIntegration.isHighRankVampire(player)) {
            LOGGER.debug("[硬化皮肤] {} 当前血族等级 = {}（本强化要求 ≥ {}）⇒ 不免疫 {}",
                    player.getName().getString(), VampireIntegration.getVampireLevel(player),
                    VampireIntegration.REQUIRED_LEVEL, source.getMsgId());
            return;
        }
        if (sunlightFire && !isBurningInSunlight(entity)) {
            return;   // 身上的火不是太阳点的（岩浆/营火…）⇒ 不免（这一条故意不刷日志 ✓ 岩浆里着火很常见）
        }
        event.setCanceled(true);
        entity.clearFire();
        LOGGER.debug("[硬化皮肤] {} 免疫了 {}（阳光 ✓）", player.getName().getString(), source.getMsgId());
    }

    // ============================================================
    //  二、每 tick：维持 5 级「防晒」＋ 掐掉阳光点的火
    // ============================================================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide) {
            return;   // 服务器施加，客户端自动同步 ✓
        }
        if (!isProtected(player) || !inSunlight(player)) {
            return;
        }
        applyFullSunscreen(player);
        if (player.isOnFire() && !player.isInLava()) {
            player.clearFire();
            LOGGER.debug("[硬化皮肤] {} 在阳光下被点着了 ⇒ 已掐灭", player.getName().getString());
        }
    }

    /**
     * 把「防晒」维持到 5 级（官方阳伞同款 ✓）。
     * <p>已经够强就不再重复施加 ⇒ 不刷效果包、也不让图标闪烁 ✓。
     */
    private static void applyFullSunscreen(Player player) {
        MobEffect sunscreen = ForgeRegistries.MOB_EFFECTS.getValue(SUNSCREEN_EFFECT_ID);
        if (sunscreen == null) {
            return;   // 血族没装 / 该效果不存在 ⇒ 静默跳过（Mixin 那条仍在兜底 ✓）
        }
        MobEffectInstance current = player.getEffect(sunscreen);
        if (current != null && current.getAmplifier() >= SUNSCREEN_AMPLIFIER && current.getDuration() > 20) {
            return;
        }
        player.addEffect(new MobEffectInstance(sunscreen, SUNSCREEN_DURATION, SUNSCREEN_AMPLIFIER, false, false, true));
        LOGGER.debug("[硬化皮肤] {} 在阳光下 ⇒ 已维持 5 级防晒", player.getName().getString());
    }

    // ============================================================
    //  判定
    // ============================================================

    /**
     * 「阳光免疫」判定：装了本强化 + 是 5 级以上的血族（含 ModList 判定 ✓）。
     *
     * <p>⚠ 这个方法同时被 **Mixin {@code VampirismSunHelperMixin}** 调用（它把血族的
     * {@code Helper#gettingSundamge} 直接压成 false ✓）—— 所以这里必须是 **public static** ✓，
     * 而且**不能**依赖任何"只在事件里才成立"的前提 ✗（客户端也会走这条路，物品 NBT / 阵营数据都已同步 ✓）。
     */
    public static boolean isProtectedFromSun(Player player) {
        if (player == null) {
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

    /** 装了本强化 + 是 5 级以上的血族（含 ModList 判定，未装血族直接 false ✓） */
    private static boolean isProtected(LivingEntity entity) {
        return entity instanceof Player player && isProtectedFromSun(player);
    }

    /** 真的暴露在阳光下：白天 ＋ 头顶见天 ＋ 头顶没下雨 ✓ */
    private static boolean inSunlight(LivingEntity entity) {
        Level level = entity.level();
        if (!level.isDay()) {
            return false;
        }
        BlockPos pos = entity.blockPosition();
        return level.canSeeSky(pos) && !level.isRainingAt(pos);
    }

    /** 「太阳点的火」：暴露在阳光下，而且不在岩浆里（岩浆的火照旧烧 ✓） */
    private static boolean isBurningInSunlight(LivingEntity entity) {
        return !entity.isInLava() && inSunlight(entity);
    }
}
