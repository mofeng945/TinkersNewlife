package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.util.ArmorModifierHelper;
import com.mofengbaizhi.tinkersnewlife.integration.vampirism.VampireIntegration;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 能力槽强化「贵族餐饮」的生效端。
 *
 * <p>触发点：{@link LivingEntityUseItemEvent.Finish}（物品"用完了"那一刻，吃东西也走这条 ✓）。
 * 判定链（全过才生效 ✓）：
 * <ol>
 *   <li>用的是**正常食物**（{@code getFoodProperties != null} 且营养值 &gt; 0）——血瓶、血药、纯净血液
 *       这些没有食物属性，天然被排除在外 ✓（静默跳过，不打扰 ✓）；</li>
 *   <li>身上任意一件护甲装了本强化（{@link ArmorModifierHelper#hasModifierOnArmor}，护甲损坏时不算 ✓）；</li>
 *   <li>血族模组在场（{@code ModList} 判定 —— **必须在碰 {@code VampireIntegration} 之前** ✓）；</li>
 *   <li>是 **5 级以上的血族**（用户口径 ✓）。</li>
 * </ol>
 * 生效内容：按食物的营养值加血液值（血液饱和度按食物的饱和度系数走 ✓）。
 *
 * <p>⚠ 诊断口径（§706 教训 ✓）：装了强化却"没反应"时，第 2 条之后的分支**每一处都打一条
 * {@code LOGGER.debug}**（写清等级/原因）✓ —— debug 级只会进 {@code logs/debug.log}，
 * 不会污染 {@code latest.log} ✓（§701 的干净口径不变 ✓）。
 * <p>⚠ 只加服务器端（{@code level().isClientSide} 直接返回 ✓），否则会双倍加血 ✗。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AristocraticDiningHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AristocraticDiningHandler.class);
    private static final String MODIFIER_ID = "aristocratic_dining";
    private static final String VAMPIRISM_MOD_ID = "vampirism";

    @SubscribeEvent
    public static void onFinishUsingItem(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.level().isClientSide) {
            return;
        }
        ItemStack stack = event.getItem();
        if (stack.isEmpty()) {
            return;
        }
        FoodProperties food = stack.getItem().getFoodProperties(stack, player);
        if (food == null || food.getNutrition() <= 0) {
            return;   // 只认"正常食物"：血瓶/药水之类没有营养值 ⇒ 静默不参与
        }
        if (!ArmorModifierHelper.hasModifierOnArmor(player, MODIFIER_ID)) {
            return;   // 没装这条强化：静默（否则 debug.log 会被每一口食物刷屏）
        }
        if (!ModList.get().isLoaded(VAMPIRISM_MOD_ID)) {
            LOGGER.debug("[贵族餐饮] {} 装了这一条，但血族模组不在场 ⇒ 不生效", player.getName().getString());
            return;   // ⭐ 必须先判 ModList，再去碰 VampireIntegration
        }
        int level = VampireIntegration.getVampireLevel(player);
        if (level < VampireIntegration.REQUIRED_LEVEL) {
            LOGGER.debug("[贵族餐饮] {} 当前血族等级 = {}（本强化要求 ≥ {}）⇒ 本次进食不加血",
                    player.getName().getString(), level, VampireIntegration.REQUIRED_LEVEL);
            return;
        }
        int nutrition = food.getNutrition();
        float saturation = food.getSaturationModifier();
        boolean added = VampireIntegration.addBlood(player, nutrition, saturation);
        LOGGER.debug("[贵族餐饮] {} 进食 {} → 营养 {} / 饱和系数 {} ⇒ 加血{}",
                player.getName().getString(), stack.getHoverName().getString(), nutrition, saturation,
                added ? "成功" : "失败（血族血液值写入不可用）");
    }
}
