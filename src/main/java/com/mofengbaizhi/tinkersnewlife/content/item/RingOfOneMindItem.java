package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 同心戒：戒指槽饰品，<b>永远成对</b>存在。
 *
 * <h2>成对绑定</h2>
 * 一次合成 / 一次购买产出的是<b>一对两枚</b>（两个各自独立的物品栈，见 {@link #newPairStacks}），
 * 两枚共用一个<b>成对印记</b> NBT（{@value #KEY_PAIR}）。
 * 玩家把两枚分给彼此后，印记仍是一样的 —— 也就是说"是不是同一对"完全由这个 NBT 判定 ✓。
 * <p>⚠ 本物品 {@code stacksTo(1)}：一枚戒指能堆叠的话，Curios 的"右键直接戴"
 * （不拆分整栈）会把一对一起塞进同一个戒指槽 ✗ —— 详见构造器与 {@link #newPairStacks} 的注释。
 *
 * <p>印记何时写入：
 * <ul>
 *   <li>咒力合成仪式产出时（{@code CurseCraftRitualHandler} 调 {@link #newPairStacks}）；</li>
 *   <li>墨默交易成交时（{@code MomoMerchant#buyFrom} 同）——
 *       每次购买都结一对<b>新印记</b>，所以"买两次"得到的是<b>两对</b>，不会三枚同对 ✗；</li>
 *   <li>兜底：{@link #inventoryTick} 发现没印记就补一个（创造栏/指令刷出来的单枚也不会是"死戒指"）。</li>
 * </ul>
 *
 * <h2>效果</h2>
 * 两名玩家各自戴上同一对里的一枚时，他们共享咒力与术式
 * （服务端解析见 {@code com.mofengbaizhi.tinkersnewlife.content.curse.TwinRingLink}）。
 *
 * <p>同一名玩家身上<b>不允许同时戴同一对的两枚</b>（{@link #canEquip} 拒绝）：
 * 那样等于把一对浪费在一个人的两个戒指槽上，谁也共享不到 ✗。
 */
public class RingOfOneMindItem extends Item implements ICurioItem {

    /** 物品 NBT：成对印记（string，一对两枚同值） */
    public static final String KEY_PAIR = "tinkersnewlife.twin_ring_pair";

    /** 一对的枚数 */
    public static final int MAX_PAIR = 2;

    public RingOfOneMindItem() {
        // ⚠ 必须是 stacksTo(1)：Curios 的"右键直接戴"是
        //   `setStackInSlot(slot, stack.copy())` + `stack.shrink(整栈数量)`（不拆分！）——
        //   若一枚戒指能堆叠，右键会把**一对两枚**一起塞进同一个戒指槽、手上整栈清空，
        //   于是一对废在一个人身上（同伴恒为空）✗。
        //   所以"一对"永远是**两枚各自独立的一栈**（见 newPairStacks）✓。
        super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    // ============================================================
    //  ICurioItem：戒指槽
    // ============================================================

    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        String id = context.identifier();
        if (!"ring".equals(id) && !"curio".equals(id)) return false;
        // 同一对的两枚不许戴在同一个人身上（那样就没有"另一名佩戴者"了）
        String pair = pairId(stack);
        return pair == null || !wearsPair(context.entity(), pair, stack);
    }

    @Override
    public boolean canEquipFromUse(SlotContext context, ItemStack stack) {
        return canEquip(context, stack);
    }

    // ============================================================
    //  兜底盖章
    // ============================================================

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity,
                              int slotId, boolean isSelected) {
        // 没印记就补一个：保证"存在即属于某一对"（有没有同伴戴上另一枚是另一回事）
        if (!level.isClientSide && pairId(stack) == null) {
            stampNewPair(stack);
        }
    }

    // ============================================================
    //  物品提示
    // ============================================================

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(LANG_PREFIX + ".effect")
                .withStyle(ChatFormatting.GRAY));
        String pair = pairId(stack);
        if (pair == null) {
            tooltip.add(Component.translatable(LANG_PREFIX + ".no_pair")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable(LANG_PREFIX + ".pair", mark(pair))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.add(Component.translatable(LANG_PREFIX + ".flavor")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }

    // ============================================================
    //  成对数据层
    // ============================================================

    /** 该物品栈的成对印记（无印记返回 null） */
    @Nullable
    public static String pairId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(KEY_PAIR)) return null;
        String v = tag.getString(KEY_PAIR);
        return v.isEmpty() ? null : v;
    }

    /** 给该物品栈盖一个**新的**成对印记（同一对的两枚共用同一个印记 ✓） */
    public static void stampNewPair(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        stack.getOrCreateTag().putString(KEY_PAIR, UUID.randomUUID().toString());
    }

    /**
     * 产出<b>新的一对</b>：{@link #MAX_PAIR} 枚 {@code count = 1} 的独立物品栈，共用同一个新印记。
     *
     * <p>⭐ <b>成对物品唯一的发放形式</b>（仪式产物 / 墨默成交 / 创造栏都走这里）：
     * 一枚戒指 <b>不能堆叠</b>（理由见构造器注释 —— Curios 右键装备不拆分整栈），
     * 所以"一对"必须是两个独立栈；两栈印记相同，因此拆开分给彼此后仍然互相认得出 ✓。
     */
    public static List<ItemStack> newPairStacks(Item item) {
        List<ItemStack> out = new ArrayList<>(MAX_PAIR);
        String mark = UUID.randomUUID().toString();
        for (int i = 0; i < MAX_PAIR; i++) {
            ItemStack stack = new ItemStack(item, 1);
            stack.getOrCreateTag().putString(KEY_PAIR, mark);
            out.add(stack);
        }
        return out;
    }

    /**
     * 该实体佩戴的同心戒的成对印记（戒指槽/通用槽都算）；没戴返回 null。
     * <p>一名玩家理论上可以戴多对（多戒指槽），这里取<b>第一个</b>带印记的。
     */
    @Nullable
    public static String wornPairId(LivingEntity entity) {
        for (ItemStack stack : wornRings(entity)) {
            String pair = pairId(stack);
            if (pair != null) return pair;
        }
        return null;
    }

    /** 该实体佩戴的全部同心戒（按 curios 槽位顺序；没装 curios 返回空表） */
    public static List<ItemStack> wornRings(LivingEntity entity) {
        List<ItemStack> out = new ArrayList<>();
        if (entity == null) return out;
        var curios = CuriosApi.getCuriosInventory(entity).resolve();
        if (curios.isEmpty()) return out;
        for (ICurioStacksHandler handler : curios.get().getCurios().values()) {
            IDynamicStackHandler stacks = handler.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() instanceof RingOfOneMindItem) out.add(stack);
            }
        }
        return out;
    }

    /** 该实体身上是否已经戴着同一对的另一枚（{@code except} 为正在判定装备的那一枚） */
    private static boolean wearsPair(LivingEntity entity, String pair, ItemStack except) {
        for (ItemStack stack : wornRings(entity)) {
            if (stack == except) continue;
            if (pair.equals(pairId(stack))) return true;
        }
        return false;
    }

    /** 印记的短显示（前 8 位，够认对且不刷屏） */
    public static String mark(String pair) {
        if (pair == null) return "";
        return pair.length() <= 8 ? pair : pair.substring(0, 8);
    }

    /** 语言键前缀（自检用） */
    public static final String LANG_PREFIX = "item." + TinkersNewlife.MOD_ID + ".ring_of_one_mind";

    /** 便捷判定：该物品栈是不是同心戒 */
    public static boolean isRing(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof RingOfOneMindItem;
    }

    /** 便捷判定：该实体是否戴着同心戒（不看有没有同伴） */
    public static boolean isWornBy(LivingEntity entity) {
        return !wornRings(entity).isEmpty();
    }

    /** 供外部（交易/仪式）判断：玩家身上是否已有该印记（避免把同一对塞给同一个人两次） */
    public static boolean playerWearsPair(Player player, String pair) {
        return player != null && pair != null && wearsPair(player, pair, null);
    }
}
