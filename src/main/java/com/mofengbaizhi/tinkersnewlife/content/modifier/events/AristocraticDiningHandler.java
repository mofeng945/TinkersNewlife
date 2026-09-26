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
 *   <li>用的是**正常食物**（{@code getFoodProperties != null}）——血瓶、血药、纯净血液这些没有食物属性，
 *       天然被排除在外 ✓；</li>
 *   <li>身上任意一件护甲装了本强化（{@link ArmorModifierHelper#hasModifierOnArmor}，护甲损坏时不算 ✓）；</li>
 *   <li>装的是血族模组（{@code ModList} 判定 —— **必须在碰 {@code VampireIntegration} 之前** ✓）；</li>
 *   <li>是 **5 级以上的血族**（用户口径 ✓）。</li>
 * </ol>
 * 生效内容：按食物的营养值加血液值（血液饱和度按食物的饱和度系数走 ✓）。
 * <p>
 * ⚠ 只加服务器端（{@code level().isClientSide} 直接返回 ✓），否则会双倍加血 ✗。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AristocraticDiningHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AristocraticDiningHandler.class);
    private static final String MODIFIER_ID = "aristocratic_dining";
    private static final String VAMPIRISM_MOD_ID = "vampirism";
    private static final boolean DEBUG = false;

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
            return;   // 只认"正常食物"：血瓶/药水之类没有营养值 ⇒ 不参与
        }
        if (!ArmorModifierHelper.hasModifierOnArmor(player, MODIFIER_ID)) {
            return;
        }
        if (!ModList.get().isLoaded(VAMPIRISM_MOD_ID)) {
            return;   // ⭐ 必须先判 ModList，再去碰 VampireIntegration（否则没装血族会 NoClassDefFoundError）
        }
        if (!VampireIntegration.isHighRankVampire(player)) {
            return;
        }
        int nutrition = food.getNutrition();
        float saturation = food.getSaturationModifier();
        if (VampireIntegration.addBlood(player, nutrition, saturation) && DEBUG) {
            LOGGER.debug("[贵族餐饮] {} 进食 {} → 血液 +{}（饱和度系数 {}）",
                    player.getName().getString(), stack.getHoverName().getString(), nutrition, saturation);
        }
    }
}
