package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
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
 * <h2>汇率（都换算到"灵魂"这一个公共单位上 ✓ 全局自洽 ✓）</h2>
 * <table border="1">
 *   <tr><th>换算</th><th>比率</th><th>出处</th></tr>
 *   <tr><td>1 法力 = 4 灵魂</td><td>{@link #SOULS_PER_MANA}</td><td>用户定案 ✓</td></tr>
 *   <tr><td>1 咒力 = 3 灵魂</td><td>{@link #SOULS_PER_CURSE}</td><td>沿用既有「咒力不足由灵魂兜底」的 1:3 ✓
 *       （见 {@link CursePowerHelper#payCurseWithSoulFallback}）</td></tr>
 *   <tr><td>⇒ 1 咒力 = 0.75 法力</td><td>—</td><td>由上两行推出 ✓</td></tr>
 * </table>
 *
 * <h2>三处注入点</h2>
 * <ol>
 *   <li><b>法力侧</b>：{@code mixin.AllPathsOneManaMixin}（铁魔法 {@code MagicData} ✓）
 *       —— {@code getMana()} 在<b>服务端</b>把"还能垫出来的法力"一并报出去 ✓（于是"法力够不够"的
 *       各处判定都会通过 ✓），随后 {@code setMana()} 里把账做正并真扣咒力/灵魂 ✓；</li>
 *   <li><b>灵魂侧</b>：{@code mixin.AllPathsOneSoulMixin}（诡厄 {@code SEHelper} ✓）
 *       —— 先让"灵魂够不够"的判定通过（只预测 ✓ 不扣费 ✓），真扣时再垫 ✓；</li>
 *   <li><b>咒力侧</b>：直接改 {@link CursePowerHelper#canPayCurse} 与
 *       {@link CursePowerHelper#payCurseWithSoulFallback}（我们自己的代码 ✓ 不用 mixin ✓），
 *       且<b>只在穿着本特性时</b>生效 ✓。</li>
 * </ol>
 *
 * <p>⚠ 软依赖铁律：铁魔法/诡厄的一切调用都经由 {@link IronSpellsSpellAccess} /
 * {@link SoulEnergyBridge} 的反射 ✓；两个 mixin 都用 {@code targets="包名"} 字符串 ✓
 * （不写类字面量 ⇒ 没装那两个 mod 时不会 NoClassDefFoundError ✓ 且 mixins.json 是
 * {@code required:false} ⇒ 注入不上也只是"该系不生效" ✓ 不会崩 ✓）。
 */
public class AllPathsOneTrait extends Modifier implements TooltipModifierHook {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "all_paths_one"));

    /** 1 法力 = 4 灵魂（用户口径 ✓） */
    public static final double SOULS_PER_MANA = 4.0D;

    /** 1 咒力 = 3 灵魂（既有口径 ✓） */
    public static final double SOULS_PER_CURSE = 3.0D;

    /**
     * 报给铁魔法"能垫多少法力"的上限 ✓。
     * <p>为什么要有上限：咒力可能是<b>无限</b>状态（{@code isCurseInfinite} ✓）⇒ 直接换算会得到
     * 天文数字 ⇒ 任何读 {@code getMana()} 的 mod 逻辑都会被噎住 ✗。一万点远超任何法术消耗 ✓。
     */
    public static final float POOL_CAP = 10000.0F;

    /** 我们自己在读/写"真实法力"时用的重入闸（避免被 {@link #manaPool} 的膨胀影响 ✓） */
    public static final ThreadLocal<Boolean> RAW_MANA = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 效果固定 ⇒ 显示名不带等级 ✓ */
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

    /** 该物品是否带万法有道 */
    public static boolean has(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var tool = ToolHelper.getToolStack(stack);
        return tool != null && tool.getModifierLevel(ID) > 0;
    }

    /** 身上穿了几件带万法有道的盔甲（0~4 ✓ 效果本身不按件叠加 ✓ 只看有没有 ✓） */
    public static int worn(@Nullable LivingEntity entity) {
        if (entity == null) return 0;
        int n = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            if (has(entity.getItemBySlot(slot))) n++;
        }
        return n;
    }

    /** 穿着任意一件即生效 ✓ */
    public static boolean active(@Nullable LivingEntity entity) {
        return worn(entity) > 0;
    }

    // ============================================================
    //  汇率
    // ============================================================

    public static double curseAsSouls(double curse) {
        return curse * SOULS_PER_CURSE;
    }

    public static double curseAsMana(double curse) {
        return curseAsSouls(curse) / SOULS_PER_MANA;
    }

    public static double manaAsSouls(double mana) {
        return mana * SOULS_PER_MANA;
    }

    // ============================================================
    //  可用量（"够不够"的判定用 ✓ 不扣费 ✓）
    // ============================================================

    public static int soulsOf(@Nullable Player player) {
        return player == null ? 0 : SoulEnergyBridge.getSouls(player);
    }

    /** 可用咒力（无限时给一个"很大但不会溢出"的值 ✓ 与 {@link CursePowerHelper#isCurseInfinite} 一致 ✓） */
    public static double curseOf(@Nullable Player player) {
        if (player == null) return 0.0D;
        if (CursePowerHelper.isCurseInfinite(player)) return 1.0E9D;
        return Math.max(0.0D, CursePowerHelper.getTotalCurse(player));
    }

    /** 读**真实**法力（-1 = 没装铁魔法 ✓） */
    public static int realManaOf(@Nullable LivingEntity entity) {
        if (entity == null) return -1;
        boolean old = RAW_MANA.get();
        RAW_MANA.set(Boolean.TRUE);
        try {
            return IronSpellsSpellAccess.manaOf(entity);
        } finally {
            RAW_MANA.set(old);
        }
    }

    /** 写**真实**法力（绕过我们自己的膨胀/修正 ✓ 用于"用垫来的法力去付灵魂" ✓） */
    public static void writeManaRaw(@Nullable LivingEntity entity, int value) {
        if (entity == null) return;
        boolean old = RAW_MANA.get();
        RAW_MANA.set(Boolean.TRUE);
        try {
            IronSpellsSpellAccess.setMana(entity, Math.max(0, value));
        } finally {
            RAW_MANA.set(old);
        }
    }

    /** 还能从咒力 + 灵魂里"垫"出多少法力（给 {@code getMana()} 膨胀用 ✓ 有上限 ✓） */
    public static float manaPool(@Nullable Player player) {
        if (player == null) return 0.0F;
        double pool = curseAsMana(curseOf(player)) + soulsOf(player) / SOULS_PER_MANA;
        return (float) Math.max(0.0D, Math.min(POOL_CAP, pool));
    }

    /** 直接扣"真实法力"（不可透支 ✓ 用于"用垫来的法力去付灵魂" ✓） */
    public static boolean spendMana(@Nullable LivingEntity entity, int amount) {
        if (amount <= 0) return true;
        int have = realManaOf(entity);
        if (have < 0) return false;                 // 没装铁魔法 ✓
        if (have < amount) return false;
        writeManaRaw(entity, have - amount);
        return true;
    }

    /** 咒力不够时，灵魂 + 法力能不能补上（给 {@link CursePowerHelper#canPayCurse} 用 ✓） */
    public static boolean canCoverCurse(Player player, double amount) {
        if (!active(player)) return false;
        double souls = soulsOf(player);
        double mana = Math.max(0, realManaOf(player));
        return curseOf(player) + souls / SOULS_PER_CURSE + manaAsSouls(mana) / SOULS_PER_CURSE >= amount;
    }

    // ============================================================
    //  垫付（真扣费 ✓ 返回"补上了多少目标资源" ✓）
    // ============================================================

    /**
     * 法力缺 {@code missing} 点 ⇒ 先吃咒力、再吃灵魂 ✓ 返回补上的<b>法力</b>点数 ✓。
     * <p>⚠ 只扣"确实拿到手"的部分：{@link CursePowerHelper#spendCurseShared} 返回的是
     * "仍然付不清的余量" ✓ 相减才是真扣到的咒力 ✓（同心戒共鸣也走同一条 ✓ 与全局口径一致 ✓）。
     */
    public static int payManaShortfall(Player player, int missing) {
        if (player == null || missing <= 0) return 0;
        int remaining = missing;

        // ① 咒力（1 咒力 = 0.75 法力）
        double curseNeed = Math.min(curseOf(player), remaining / curseAsMana(1.0D));
        if (curseNeed > 0.0D) {
            double left = CursePowerHelper.spendCurseShared(player, curseNeed);
            double used = curseNeed - Math.max(0.0D, left);
            remaining -= (int) Math.floor(curseAsMana(used));
        }
        if (remaining <= 0) return missing;

        // ② 灵魂（1 法力 = 4 灵魂）
        int soulsNeed = (int) Math.ceil(remaining * SOULS_PER_MANA);
        int useSouls = Math.min(soulsNeed, soulsOf(player));
        if (useSouls > 0 && SoulEnergyBridge.decreaseSouls(player, useSouls)) {
            remaining -= (int) Math.floor(useSouls / SOULS_PER_MANA);
        }
        return missing - Math.max(0, remaining);
    }

    /**
     * 灵魂缺 {@code missing} 点 ⇒ 先吃咒力、再吃法力 ✓ 返回补上的<b>灵魂</b>点数 ✓。
     * <p>调用方（灵魂侧 mixin）拿到这个数后会把灵魂<b>加回去</b> ✓ —— 于是诡厄自己那次
     * "扣灵魂"就能正常成功 ✓ 净效果 = 灵魂用光 + 咒力/法力被扣 ✓。
     */
    public static int paySoulShortfall(Player player, int missing) {
        if (player == null || missing <= 0) return 0;
        int remaining = missing;

        // ① 咒力（1 咒力 = 3 灵魂）
        double curseNeed = Math.min(curseOf(player), remaining / SOULS_PER_CURSE);
        if (curseNeed > 0.0D) {
            double left = CursePowerHelper.spendCurseShared(player, curseNeed);
            double used = curseNeed - Math.max(0.0D, left);
            remaining -= (int) Math.floor(curseAsSouls(used));
        }
        if (remaining <= 0) return missing;

        // ② 法力（1 法力 = 4 灵魂）
        int manaNeed = (int) Math.ceil(remaining / SOULS_PER_MANA);
        int manaHave = Math.max(0, realManaOf(player));
        int useMana = Math.min(manaNeed, manaHave);
        if (useMana > 0) {
            writeManaRaw(player, manaHave - useMana);
            remaining -= useMana * (int) SOULS_PER_MANA;
        }
        return missing - Math.max(0, remaining);
    }

    /** 灵魂不够时先垫再让诡厄扣（灵魂侧 mixin 调用 ✓） */
    public static void topUpSoulsFor(Player player, int amount) {
        if (player == null || amount <= 0) return;
        if (player.level().isClientSide) return;
        if (!active(player)) return;
        int missing = amount - soulsOf(player);
        if (missing <= 0) return;
        int paid = paySoulShortfall(player, missing);
        if (paid > 0) SoulEnergyBridge.addSouls(player, paid);
    }

    /** 手上（或正在用）的是诡厄巫法的物品吗 —— 用来把"灵魂检查"的预测限制在施法场景 ✓ */
    public static boolean holdingGoetyItem(@Nullable Player player) {
        if (player == null) return false;
        return isGoetyItem(player.getMainHandItem())
                || isGoetyItem(player.getOffhandItem())
                || isGoetyItem(player.getUseItem());
    }

    private static boolean isGoetyItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && "goety".equals(key.getNamespace());
    }
}
