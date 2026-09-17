package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 双向认知阻碍面具：头饰（curios 头部槽 {@value #WEAR_SLOT}）。
 *
 * <h2>「双向」的两半</h2>
 * <ul>
 *   <li><b>向外（别人感知不到你）</b>：其他玩家看不到你的名字（{@code RenderNameTagEvent} 取消）；
 *       怪物锁定不到你（{@code LivingChangeTargetEvent} 取消 + 定期清扫已有锁定）；
 *       小地图雷达也显示不出你 —— 这三件事在 MC 里共用一个"隐身标记"
 *       （Xaero's 的雷达只看 {@code isInvisibleTo}，且它"隐藏隐身实体"的开关默认开着），
 *       所以佩戴者会被打上隐身标记，**但身体照常渲染**
 *       （{@code LivingEntityRendererMixin} 强制 {@code isBodyVisible = true}）——
 *       也就是"看得见人、看不到名字、锁不住、雷达上没有你" ✓；
 *       不想要雷达隐藏就关配置 {@code cognitive_mask.hide_from_radar} ✓（名字与索敌照旧屏蔽）。</li>
 *   <li><b>向内（你感知里的世界变了）</b>：你把<b>除玩家以外</b>的所有生物都<b>视为亡灵</b> ——
 *       咒具的亡灵特攻、反转术式"对亡灵造成伤害"、以及<b>咒灵操术的收服判定</b>
 *       （原本只收 {@code MobType.UNDEAD}，见 {@code CursedSpiritTechnique}）全部按亡灵算 ✓。
 *       玩家不受影响，所以对自己/同伴用反转术式依然是治疗 ✓。</li>
 * </ul>
 *
 * <p>只认头部槽（不吃通用饰品槽）——与同心戒同样的口径：写哪个槽就只认哪个槽 ✓。
 */
public class CognitiveMaskItem extends Item implements ICurioItem {

    /** 可佩戴的 curios 槽位：头部 */
    public static final String WEAR_SLOT = "head";

    public CognitiveMaskItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    // ============================================================
    //  ICurioItem：头饰
    // ============================================================

    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        return WEAR_SLOT.equals(context.identifier());
    }

    @Override
    public boolean canEquipFromUse(SlotContext context, ItemStack stack) {
        return canEquip(context, stack);
    }

    // ============================================================
    //  物品提示
    // ============================================================

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(LANG_PREFIX + ".out").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(LANG_PREFIX + ".in").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(LANG_PREFIX + ".flavor")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }

    // ============================================================
    //  查询：谁戴着面具
    // ============================================================

    /** 该实体是否戴着认知阻碍面具（遍历 curios 全部槽位） */
    public static boolean isWorn(@Nullable LivingEntity entity) {
        if (entity == null) return false;
        var curios = CuriosApi.getCuriosInventory(entity).resolve();
        if (curios.isEmpty()) return false;
        for (ICurioStacksHandler handler : curios.get().getCurios().values()) {
            IDynamicStackHandler stacks = handler.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() instanceof CognitiveMaskItem) return true;
            }
        }
        return false;
    }

    // ============================================================
    //  ⭐ 向内：把"除玩家以外"的一切视为亡灵
    // ============================================================

    /**
     * 对 {@code actor} 而言，{@code target} 是否"算亡灵"。
     *
     * <p>= 原本就是亡灵 <b>或</b>（actor 戴着面具 且 target <b>不是玩家</b>）。
     *
     * <p>用于替换各处裸的 {@code target.getMobType() == MobType.UNDEAD} 判定：
     * 咒具亡灵特攻（{@code CursedToolItem} / 天逆鉾 / 游云）、
     * 反转术式（对亡灵造成伤害）、咒灵操术的收服条件 ✓。
     */
    public static boolean treatedAsUndead(@Nullable LivingEntity actor, @Nullable LivingEntity target) {
        if (target == null) return false;
        if (target.getMobType() == MobType.UNDEAD) return true;
        // ⚠ 玩家永远不算亡灵：否则"对自己用反转术式"会变成自伤 ✗
        if (target instanceof Player) return false;
        return isWorn(actor);
    }

    /** 语言键前缀 */
    public static final String LANG_PREFIX = "item." + TinkersNewlife.MOD_ID + ".cognitive_mask";
}
