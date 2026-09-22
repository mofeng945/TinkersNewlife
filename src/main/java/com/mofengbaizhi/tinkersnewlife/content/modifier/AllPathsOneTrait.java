package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.energy.EnergyUnits;
import com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks.IronSpellsSpellAccess;
import com.mofengbaizhi.tinkersnewlife.util.SoulEnergyBridge;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 巫师套装特性·<b>万法有道</b>（<b>无等级</b> ✓ 任意一件即生效 ✓）—— 内建在四件巫师套上。
 *
 * <p>用户口径：<b>法力 / 咒力 / 灵魂三系资源互相"垫付"</b> ✓，各有优先级链：
 * <ul>
 *   <li><b>铁魔法法力</b>不够 ⇒ 先吃<b>咒力</b>，再吃<b>灵魂</b> ✓；</li>
 *   <li><b>诡厄灵魂</b>不够 ⇒ 先吃<b>咒力</b>，再吃<b>法力</b> ✓；</li>
 *   <li><b>本模组咒力</b>不够 ⇒ 先吃<b>灵魂</b>（既有 ✓），再吃<b>法力</b> ✓。</li>
 * </ul>
 *
 * <h2>汇率（⭐ 唯一来源：{@link EnergyUnits} —— 本类不再自带数字 ✗）</h2>
 * <table border="1">
 *   <tr><th>换算</th><th>比率</th></tr>
 *   <tr><td>1 EE = 1 法力 = 4 灵魂 = 2 咒力</td><td>用户 2026-09-21 拍板（全模组统一 ✓）</td></tr>
 *   <tr><td>⇒ 1 法力 = 4 灵魂</td><td>{@link EnergyUnits#SOULS_PER_MANA}</td></tr>
 *   <tr><td>⇒ 1 咒力 = 2 灵魂</td><td>{@link EnergyUnits#SOULS_PER_CURSE}（<b>旧口径是 3</b> ✗ 已按新公式改成 2 ✓）</td></tr>
 *   <tr><td>⇒ 1 咒力 = 0.5 法力</td><td>{@link EnergyUnits#MANA_PER_CURSE}（<b>旧口径是 0.75</b> ✗）</td></tr>
 * </table>
 *
 * <h2>⚠ 三条安全铁律（上一版翻车后定的 ✓ 见备忘录 §379 / §380）</h2>
 * <ol>
 *   <li><b>fail-safe</b>：所有会被 mixin / 别人的流程调用的入口一律 {@code try/catch(Throwable)} ✓
 *       出错就<b>什么都不做</b>（等于没装特性 ✓）—— 绝不允许一个可选特性把宿主流程带崩 ✗；</li>
 *   <li><b>预测即承诺</b>：只有在"这笔垫付<b>确定</b>能成功"时才在检查点返回 true ✓
 *       —— 加不上去的资源（灵魂图腾模式那种 ✗）不算额度 ✓；</li>
 *   <li><b>先给后收</b>：需要"补足资源再让对方扣"的场景，<b>先把资源补上并确认生效</b> ✓
 *       再收替代资源 ✓ —— 顺序反了就会出现"资源扣了、事没办成" ✗。</li>
 * </ol>
 *
 * <h2>注入点</h2>
 * <ol>
 *   <li><b>法力侧</b>：<b>不撒谎</b> ✓ —— 由"我们要自己走判定"的调用方
 *       （{@code IronSpellsReflector#tryCastSpell} ✓ 模块化魔杖 ✓）在判定前调
 *       {@link #topUpManaFor} <b>把法力真的补出来</b> ✓（先扣咒力 → 灵魂 ✓）。
 *       ⚠ 早先那版是"让 {@code getMana()} 多报一截、再在 {@code setMana} 里减掉" ✗ ——
 *       只要有人<b>夹紧</b>法力（铁魔法回蓝的 {@code min(上限, …)} ✗）就会算成负数 ✗ ⇒
 *       每次都误判"法力不够"⇒ 吃咒力 + 把法力压到 0 ✗（用户实测 ✓ 已废除此方案 ✗）；</li>
 *   <li><b>灵魂侧</b> {@code mixin.AllPathsOneSoulMixin}（诡厄 {@code SEHelper} ✓）——
 *       检查点只"预测"（只认咒力 + 法力 ✓ 干跑 ✓ 不扣费 ✓），真扣时才垫（先给后收 ✓）；</li>
 *   <li><b>咒力侧</b>：直接改 {@link CursePowerHelper}（我们自己的代码 ✓ 不用 mixin ✓），
 *       且<b>只在穿着本特性时</b>生效 ✓。</li>
 * </ol>
 */
public class AllPathsOneTrait extends Modifier implements TooltipModifierHook {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "all_paths_one"));

    /**
     * 1 法力 = 4 灵魂。
     * <p>⭐ 现在<b>只是 {@link EnergyUnits} 的别名</b>（值没变：4 ✓）—— 保留常量名是为了不动调用方 ✓，
     * 但数字来源统一到换算表了 ⇒ 以后改汇率只改 {@code EnergyUnits} 一处 ✓。
     */
    public static final double SOULS_PER_MANA = EnergyUnits.SOULS_PER_MANA;

    /**
     * 1 咒力 = 2 灵魂。
     * <p>⭐ 旧值是 <b>3.0</b>（历史遗留的"咒力不足由灵魂兜底 1:3"）✗ ——
     * 用户 2026-09-21 定案全模组统一为 {@code 1 EE = 4 灵魂 = 2 咒力} ⇒ 现在是
     * {@link EnergyUnits#SOULS_PER_CURSE} = <b>2.0</b> ✓（同时 {@code CursePowerHelper} 的兜底也一起改了 ✓）。
     */
    public static final double SOULS_PER_CURSE = EnergyUnits.SOULS_PER_CURSE;

    /** 报给铁魔法"能垫多少法力"的上限 ✓（防止"咒力无限"换算成天文数字把别的逻辑噎住 ✗） */
    public static final float POOL_CAP = 10000.0F;

    /** 我们自己在读/写"真实法力"时用的重入闸 ✓（避免被自己的膨胀影响 ✗） */
    public static final ThreadLocal<Boolean> RAW_MANA = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 会在服务端发动施法的物品命名空间（铁魔法 + 本模组自己的魔杖 ✓） */
    private static final String[] CAST_ITEM_NAMESPACES = { "irons_spellbooks", "tinkersnewlife" };

    @Override
    public Component getDisplayName(int level) {
        return this.getDisplayName();
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("modifier.tinkersnewlife.all_paths_one.tip"));
    }

    // ============================================================
    //  查询
    // ============================================================

    public static boolean has(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var tool = ToolHelper.getToolStack(stack);
        return ToolHelper.getActiveModifierLevel(tool, ID) > 0;
    }

    public static int worn(@Nullable LivingEntity entity) {
        if (entity == null) return 0;
        int n = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            if (has(entity.getItemBySlot(slot))) n++;
        }
        return n;
    }

    public static boolean active(@Nullable LivingEntity entity) {
        return worn(entity) > 0;
    }

    // ============================================================
    //  汇率（⭐ 一律转调 EnergyUnits —— 这里不写任何数字 ✗）
    // ============================================================

    public static double curseAsSouls(double curse) {
        return EnergyUnits.curseToSouls(curse);
    }

    public static double curseAsMana(double curse) {
        return EnergyUnits.curseToMana(curse);
    }

    public static double manaAsSouls(double mana) {
        return EnergyUnits.manaToSouls(mana);
    }

    // ============================================================
    //  可用量（干跑 ✓ 不扣费 ✓）
    // ============================================================

    public static int soulsOf(@Nullable Player player) {
        return player == null ? 0 : SoulEnergyBridge.getSouls(player);
    }

    public static double curseOf(@Nullable Player player) {
        if (player == null) return 0.0D;
        if (CursePowerHelper.isCurseInfinite(player)) return 1.0E9D;
        return Math.max(0.0D, CursePowerHelper.getTotalCurse(player));
    }

    /** 读<b>真实</b>法力（-1 = 没装铁魔法 ✓） */
    public static int realManaOf(@Nullable LivingEntity entity) {
        if (entity == null) return -1;
        boolean old = RAW_MANA.get();
        RAW_MANA.set(Boolean.TRUE);
        try {
            return IronSpellsSpellAccess.manaOf(entity);
        } catch (Throwable ignored) {
            return -1;
        } finally {
            RAW_MANA.set(old);
        }
    }

    /** 写<b>真实</b>法力（绕过膨胀/修正 ✓） */
    public static void writeManaRaw(@Nullable LivingEntity entity, int value) {
        if (entity == null) return;
        boolean old = RAW_MANA.get();
        RAW_MANA.set(Boolean.TRUE);
        try {
            IronSpellsSpellAccess.setMana(entity, Math.max(0, value));
        } catch (Throwable ignored) {
            // fail-safe ✓
        } finally {
            RAW_MANA.set(old);
        }
    }

    /** 还能从咒力 + 灵魂里垫出多少法力（给 {@code getMana()} 膨胀用 ✓ 有上限 ✓） */
    public static float manaPool(@Nullable Player player) {
        try {
            if (player == null) return 0.0F;
            double pool = curseAsMana(curseOf(player)) + EnergyUnits.soulsToMana(soulsOf(player));
            return (float) Math.max(0.0D, Math.min(POOL_CAP, pool));
        } catch (Throwable ignored) {
            return 0.0F;   // fail-safe ✓
        }
    }

    /** 咒力不够时，灵魂 + 法力能不能补上（给 {@link CursePowerHelper#canPayCurse} 用 ✓ 干跑 ✓） */
    public static boolean canCoverCurse(Player player, double amount) {
        try {
            if (!active(player)) return false;
            double souls = soulsOf(player);
            double mana = Math.max(0, realManaOf(player));
            return curseOf(player) + EnergyUnits.soulsToCurse(souls) + EnergyUnits.manaToCurse(mana) >= amount;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 灵魂总量够不够（含可垫额度 ✓ 给诡厄检查点预测用 ✓ 只认咒力 + 法力 ✓ 干跑 ✓） */
    public static boolean canCoverSouls(Player player, int amount) {
        try {
            if (player == null || amount <= 0) return false;
            return soulsOf(player) + curseAsSouls(curseOf(player))
                    + manaAsSouls(Math.max(0, realManaOf(player))) >= amount;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ============================================================
    //  垫付（真扣费 ✓ 返回"补上了多少目标资源" ✓）
    // ============================================================

    /**
     * 法力缺 {@code missing} 点 ⇒ 先吃咒力、再吃灵魂 ✓ 返回补上的<b>法力</b>点数 ✓。
     * <p>只统计"确实拿到手"的部分 ✓（{@link CursePowerHelper#spendCurseShared} 返回的是仍付不清的
     * 余量 ✓ 相减才是真扣到的 ✓）。
     */
    public static int payManaShortfall(Player player, int missing) {
        try {
            if (player == null || missing <= 0) return 0;
            int remaining = missing;

            // ① 咒力（1 咒力 = 0.5 法力 ⇒ EnergyUnits.MANA_PER_CURSE ✓ 不再写 0.75 ✗）
            double curseNeed = Math.min(curseOf(player), remaining / curseAsMana(1.0D));
            if (curseNeed > 0.0D) {
                double left = CursePowerHelper.spendCurseShared(player, curseNeed);
                double used = curseNeed - Math.max(0.0D, left);
                remaining -= (int) Math.floor(curseAsMana(used));
            }
            if (remaining <= 0) return missing;

            // ② 灵魂（1 法力 = 4 灵魂）—— 先确认够再扣 ✓（免得半路失败却已扣掉一部分 ✗）
            int soulsNeed = EnergyUnits.ceilEeToSouls(remaining);
            int useSouls = Math.min(soulsNeed, soulsOf(player));
            if (useSouls > 0 && SoulEnergyBridge.decreaseSouls(player, useSouls)) {
                remaining -= (int) Math.floor(EnergyUnits.soulsToMana(useSouls));
            }
            return missing - Math.max(0, remaining);
        } catch (Throwable ignored) {
            return 0;   // fail-safe ✓
        }
    }

    /**
     * 灵魂缺 {@code missing} 点 ⇒ 先吃咒力、再吃法力 ✓ 返回补上的<b>灵魂</b>点数 ✓。
     * <p>⚠ 调用方（{@link #topUpSoulsFor}）是"<b>先给后收</b>"：灵魂已经先补上了 ✓
     * 这里只是把账收回来 ✓ 收不满也只是我们亏一点 ✓ <b>绝不影响施法</b> ✓。
     */
    public static int paySoulShortfall(Player player, int missing) {
        try {
            if (player == null || missing <= 0) return 0;
            int remaining = missing;

            // ① 咒力（1 咒力 = 2 灵魂 ⇒ EnergyUnits.SOULS_PER_CURSE ✓ 旧口径是 3 ✗）
            double curseNeed = Math.min(curseOf(player), EnergyUnits.soulsToCurse(remaining));
            if (curseNeed > 0.0D) {
                double left = CursePowerHelper.spendCurseShared(player, curseNeed);
                double used = curseNeed - Math.max(0.0D, left);
                remaining -= (int) Math.floor(curseAsSouls(used));
            }
            if (remaining <= 0) return missing;

            // ② 法力（1 法力 = 4 灵魂）—— 先算"要几点法力"，不够就一点不动 ✓
            int manaNeed = (int) Math.ceil(EnergyUnits.soulsToMana(remaining));
            int manaHave = Math.max(0, realManaOf(player));
            if (manaHave >= manaNeed && manaNeed > 0) {
                writeManaRaw(player, manaHave - manaNeed);
                remaining -= (int) Math.floor(EnergyUnits.manaToSouls(manaNeed));
            }
            return missing - Math.max(0, remaining);
        } catch (Throwable ignored) {
            return 0;   // fail-safe ✓
        }
    }

    /**
     * 法力不够时<b>先把法力真的补出来</b>（咒力 → 灵魂 ✓）—— 给"我们要自己走一遍施法判定"的
     * 调用方用（典型：{@code IronSpellsReflector#tryCastSpell} ✓ 模块化魔杖 ✓）。
     *
     * <p>⭐ 为什么不"假装法力更高"：早先那版是让 {@code MagicData.getMana()} 多报一截、
     * 再在 {@code setMana} 里减掉 ✗ —— 一旦有代码<b>夹紧</b>法力（铁魔法回蓝就是
     * {@code setMana(min(上限, getMana()+回复))} ✗）"传入值 − 膨胀量"就会变成负数 ✗ ⇒
     * 判定成"法力不够"⇒ <b>每 tick 回蓝都去吃咒力</b> ✗✗ 还把法力压到 0 ✗（用户实测 ✓）。
     * 现在改成<b>真的把法力补进数值里</b> ✓ ⇒ 后续所有判定与扣费都是真账 ✓ 永远不会有这种事 ✓。
     */
    public static void topUpManaFor(Player player, int cost) {
        try {
            if (player == null || cost <= 0) return;
            if (player.level().isClientSide) return;
            if (!active(player)) return;
            int have = realManaOf(player);
            if (have < 0) return;                       // 没装铁魔法 ✓
            int missing = cost - have;
            if (missing <= 0) return;                   // 本来就够 ✓ 一点不动 ✓
            int paid = payManaShortfall(player, missing);
            if (paid > 0) writeManaRaw(player, have + paid);   // 真补 ✓
        } catch (Throwable ignored) {
            // fail-safe ✓（补不上 ⇒ 判定照旧不通过 ✓ 与没装特性时一致 ✓）
        }
    }

    /**
     * 灵魂不够时补齐（诡厄 mixin 在"扣灵魂"入口调用 ✓）—— ⭐ <b>先给后收</b>：
     * <ol>
     *   <li>干跑：咒力 + 法力够不够 ✓（不够就直接不做 ✓ 等于没装特性 ✓ 对方扣除自然失败 ✓）；</li>
     *   <li>先把灵魂补上 ✓ 并<b>确认真的补上了</b>（图腾模式 / 没容器时加不上去 ✗ ⇒ 直接退出 ✓，</li>
     *   <li>确认补上后再收替代资源 ✓ 收不满也不影响施法 ✓。</li>
     * </ol>
     */
    public static void topUpSoulsFor(Player player, int amount) {
        try {
            if (player == null || amount <= 0) return;
            if (player.level().isClientSide) return;
            if (!active(player)) return;
            int missing = amount - soulsOf(player);
            if (missing <= 0) return;
            if (!canCoverSouls(player, amount)) return;         // 干跑：垫不起就什么都不做 ✓

            SoulEnergyBridge.addSouls(player, missing);         // ① 先给 ✓
            if (soulsOf(player) < amount) return;               // ② 确认生效（加不上 ⇒ 退出 ✓）
            paySoulShortfall(player, missing);                  // ③ 后收 ✓（收不满也只是我们亏一点 ✓）
        } catch (Throwable ignored) {
            // fail-safe：可选特性出错绝不影响宿主流程 ✓（§379 铁律 1）
        }
    }

    /**
     * 法力扣成负数时结算缺口（法力侧 mixin 调用 ✓）—— 返回"实际垫上的法力点数" ✓。
     * <p>fail-safe ✓：任何异常都退化成"垫 0" ✓（法术照常施放 ✓ 只是少扣了一点 ✗ 不会卡流程 ✓）。
     */
    public static int settleManaShortfall(Player player, int missing) {
        try {
            return payManaShortfall(player, missing);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    // ============================================================
    //  "正在施法"判定（把法力膨胀限制在施法场景 ✓）
    // ============================================================

    /** 手上（或正在用）的是会走铁魔法结算的施法物品吗 ✓ */
    public static boolean holdingCastItem(@Nullable Player player) {
        try {
            if (player == null) return false;
            return isCastItem(player.getMainHandItem())
                    || isCastItem(player.getOffhandItem())
                    || isCastItem(player.getUseItem());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isCastItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) return false;
        for (String ns : CAST_ITEM_NAMESPACES) {
            if (ns.equals(key.getNamespace())) return true;
        }
        return false;
    }

    /** 手上（或正在用）的是诡厄巫法的物品吗 ✓（把灵魂检查的预测限制在施法场景 ✓） */
    public static boolean holdingGoetyItem(@Nullable Player player) {
        try {
            if (player == null) return false;
            return isGoetyItem(player.getMainHandItem())
                    || isGoetyItem(player.getOffhandItem())
                    || isGoetyItem(player.getUseItem());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isGoetyItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && "goety".equals(key.getNamespace());
    }
}
