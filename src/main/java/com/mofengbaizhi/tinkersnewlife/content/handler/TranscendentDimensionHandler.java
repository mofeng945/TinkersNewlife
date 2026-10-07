package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.BeyondDimensionTrait;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import java.util.ArrayList;
import java.util.List;

/**
 * ⭐ §1118y <b>超越维度</b>的运行时（用户口径 ✓）—— 盔甲侧 ✓。
 *
 * <h2>做三件事 ✓</h2>
 * <ol>
 *   <li><b>击杀成长</b> ✓：身上每件带该特性的盔甲 ⇒ 随机挑一项它**拥有的**属性
 *       （耐久 ✓ 护甲值 ✓ 盔甲韧性 ✓）按 <b>0.1%×等级 ~ 1.5%×等级</b> 长一点 ✓
 *       **每项上限 1000%** ✓（与灵性以太同规则 ✓ 用户口径 ✓）；</li>
 *   <li><b>全类型减伤（DR）</b> ✓：每级**每次击杀** ＋<b>0.05%</b> ✓ **上限 80%** ✓
 *       （存在**每件护甲自己**的持久化数据里 ✓）；受伤时把**全身带该特性的护甲**的 DR **相加** ✓
 *       再封顶 80% ✓ 然后按它减免伤害 ✓
 *       —— ⚠ 用 {@code LivingHurtEvent} ✗ 不用 {@code LivingAttackEvent} ✓：
 *       前者**发生在护甲减伤之后** ✓ 正合"**在护甲减伤后再获得一次全类型伤害减免**" ✓（用户口径 ✓）；</li>
 *   <li>⚠ 免七咒 ＋ 提示改写 ＋ 三防（不掉落/不销毁/不可被他人穿戴 ✓）**不在这里** ✗ ⇒
 *       见 {@code SevenCursesWaiverHandler} ✓。</li>
 * </ol>
 *
 * <p>⚠ 与灵性以太同一套已验证的持久化写法 ✓：读 {@code IModDataView} ✓ 写 {@code ToolDataNBT} ✓
 * 键是 {@code ResourceLocation} ✓ 写完必须 {@code updateStack} 回写 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TranscendentDimensionHandler {

    private TranscendentDimensionHandler() {
    }

    private static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, BeyondDimensionTrait.ID));

    // ------------------------------------------------------------------ 击杀：成长 ＋ DR

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        try {
            if (event.getEntity().level().isClientSide) {
                return;
            }
            if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
                return;
            }
            for (ItemStack stack : player.getArmorSlots()) {
                if (stack.isEmpty()) {
                    continue;
                }
                try {
                    ToolStack tool = ToolStack.from(stack);
                    int level = ToolHelper.getActiveModifierLevel(tool, ID);
                    if (level <= 0) {
                        continue;
                    }
                    if (growOne(tool, level, player.getRandom())) {
                        tool.updateStack(stack);   // ⚠ 不回写等于白写 ✗
                    }
                    // ⭐ DR：**每级每次击杀 ＋0.05%** ✓（用户口径 ✓ 这一项是每杀必涨 ✓ 不是随机 ✓）
                    ToolDataNBT data = tool.getPersistentData();
                    float dr = data.getFloat(BeyondDimensionTrait.drKey());
                    float next = Math.min(BeyondDimensionTrait.DR_MAX,
                            dr + BeyondDimensionTrait.DR_PER_LEVEL_PER_KILL * level);
                    if (next > dr) {
                        data.putFloat(BeyondDimensionTrait.drKey(), next);
                        tool.updateStack(stack);
                    }
                } catch (Throwable ignored) {
                    // 非匠魂物品 ⇒ 跳过 ✓
                }
            }
        } catch (Throwable ignored) {
            // 击杀流程不能被我们打崩 ✓
        }
    }

    /** 随机给一项属性加成长 ✓（只从这件护甲**真正拥有**的统计里挑 ✓） */
    private static boolean growOne(ToolStack tool, int level, net.minecraft.util.RandomSource rand) {
        ToolDataNBT data = tool.getPersistentData();
        List<String> candidates = new ArrayList<>(3);
        if (tool.getStats().get(ToolStats.DURABILITY) > 0F
                && BeyondDimensionTrait.canGrow(data, BeyondDimensionTrait.KEYS[0])) {
            candidates.add(BeyondDimensionTrait.KEYS[0]);
        }
        if (tool.getStats().get(ToolStats.ARMOR) > 0F
                && BeyondDimensionTrait.canGrow(data, BeyondDimensionTrait.KEYS[1])) {
            candidates.add(BeyondDimensionTrait.KEYS[1]);
        }
        if (tool.getStats().get(ToolStats.ARMOR_TOUGHNESS) > 0F
                && BeyondDimensionTrait.canGrow(data, BeyondDimensionTrait.KEYS[2])) {
            candidates.add(BeyondDimensionTrait.KEYS[2]);
        }
        if (candidates.isEmpty()) {
            return false;   // 三项全满 1000% ✓
        }
        String key = candidates.get(rand.nextInt(candidates.size()));
        double span = BeyondDimensionTrait.GROWTH_MAX - BeyondDimensionTrait.GROWTH_MIN;
        double delta = (BeyondDimensionTrait.GROWTH_MIN + rand.nextDouble() * span) * level;
        float old = BeyondDimensionTrait.growthOf(data, key);
        float value = (float) Math.min(BeyondDimensionTrait.MAX_GROWTH, old + delta);
        if (value <= old) {
            return false;
        }
        data.putFloat(BeyondDimensionTrait.keyId(key), value);
        return true;
    }

    // ------------------------------------------------------------------ 受伤：全类型减伤

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        try {
            LivingEntity victim = event.getEntity();
            if (victim.level().isClientSide) {
                return;
            }
            if (!(victim instanceof Player player)) {
                return;   // ⚠ 该减伤只保护**穿戴者本人** ✓（而且是玩家 ✓ 别的生物穿不上 ✓ 见三防 ✓）
            }
            float dr = totalDr(player);
            if (dr <= 0F) {
                return;
            }
            event.setAmount(event.getAmount() * (1.0F - Math.min(dr, BeyondDimensionTrait.DR_MAX)));
        } catch (Throwable ignored) {
            // 伤害结算不能被我们打崩 ✓
        }
    }

    /** 全身带该特性的护甲 DR **相加** ✓ 再封顶 **80%** ✓（用户口径 ✓） */
    public static float totalDr(Player player) {
        float sum = 0F;
        for (ItemStack stack : player.getArmorSlots()) {
            if (stack.isEmpty()) {
                continue;
            }
            try {
                ToolStack tool = ToolStack.from(stack);
                if (ToolHelper.getActiveModifierLevel(tool, ID) <= 0) {
                    continue;
                }
                sum += tool.getPersistentData().getFloat(BeyondDimensionTrait.drKey());
            } catch (Throwable ignored) {
                // 跳过 ✓
            }
        }
        return Math.min(sum, BeyondDimensionTrait.DR_MAX);
    }

    /** 全身该特性的**总等级** ✓（免七咒判定要用 ✓ 用户口径「大于等于 4」✓） */
    public static int totalLevel(Player player) {
        int total = 0;
        for (ItemStack stack : player.getArmorSlots()) {
            if (stack.isEmpty()) {
                continue;
            }
            try {
                total += ToolHelper.getActiveModifierLevel(ToolStack.from(stack), ID);
            } catch (Throwable ignored) {
                // 跳过 ✓
            }
        }
        return total;
    }
}
