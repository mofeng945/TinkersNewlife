package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.content.cursespeech.CursedSpeechRegistry;
import com.mofengbaizhi.tinkersnewlife.content.cursespeech.CursedSpeechState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 古代咒术残卷：战利品箱中随机开出。
 * <p>
 * NBT {@code words} 存 1~3 个随机词条 id（按稀有度加权）；右键使用不消耗，
 * 把未学过的词条加入玩家词库；若全部已学则提示（且工具提示标明）。
 */
public class AncientCursedScrollItem extends Item {

    public static final String KEY_WORDS = "tnl_cursed_words";

    public AncientCursedScrollItem() {
        super(new Item.Properties().stacksTo(1));
    }

    /** 生成一张残卷：1~3 段随机词条，稀有度越高开出概率越低 */
    public static ItemStack roll() {
        ItemStack stack = new ItemStack(com.mofengbaizhi.tinkersnewlife.content.ModItems.ANCIENT_CURSED_SCROLL.get());
        ListTag list = new ListTag();
        List<CursedSpeechRegistry.Word> pool = CursedSpeechRegistry.all();
        Random r = new Random();
        // §1274 规范刷新：**按组成部分**（六个槽位）逐个掷，每部分**至多一个**词；
        //   每中一个部分，下一部分的命中率**逐级衰减** ⇒ **词条越多越稀有** ✓
        //   （原来是"扁平 1~3 个、按稀有度加权"，会出现同一部分两个词 ⇒ 与用户口径不符）
        double partChance = 0.85D;
        for (CursedSpeechRegistry.Part part : CursedSpeechRegistry.Part.values()) {
            boolean hit = r.nextDouble() <= partChance;
            partChance *= 0.62D;                // 无论中没中都衰减 ⇒ 越多越稀有
            if (!hit) continue;
            List<CursedSpeechRegistry.Word> partPool = CursedSpeechRegistry.of(part);
            if (partPool.isEmpty()) continue;
            int total = 0;
            int[] weights = new int[partPool.size()];
            for (int j = 0; j < partPool.size(); j++) {
                int w = Math.max(1, 5 - partPool.get(j).rarity());
                weights[j] = w;
                total += w;
            }
            int roll = r.nextInt(total);
            int acc = 0;
            for (int j = 0; j < partPool.size(); j++) {
                acc += weights[j];
                if (roll < acc) {
                    list.add(StringTag.valueOf(partPool.get(j).id()));
                    break;
                }
            }
        }
        if (list.isEmpty()) {
            List<CursedSpeechRegistry.Word> fallback = CursedSpeechRegistry.of(CursedSpeechRegistry.Part.EXCLAMATION);
            if (!fallback.isEmpty()) list.add(StringTag.valueOf(fallback.get(0).id()));
        }
        stack.getOrCreateTag().put(KEY_WORDS, list);
        return stack;
    }

    /** 残卷上的词条 id */
    public static List<String> wordsOn(ItemStack stack) {
        List<String> out = new ArrayList<>();
        if (stack.hasTag() && stack.getTag().contains(KEY_WORDS)) {
            ListTag list = stack.getTag().getList(KEY_WORDS, 8);
            for (int i = 0; i < list.size(); i++) {
                String id = list.getString(i);
                if (CursedSpeechRegistry.exists(id)) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.pass(stack);
        }
        List<String> words = wordsOn(stack);
        if (words.isEmpty()) {
            sp.displayClientMessage(Component.translatable("message.tinkersnewlife.cursed_scroll.empty"), true);
            return InteractionResultHolder.success(stack);
        }
        List<String> newly = new ArrayList<>();
        for (String id : words) {
            if (CursedSpeechState.learn(sp, id)) {
                newly.add(id);
            }
        }
        if (newly.isEmpty()) {
            sp.displayClientMessage(Component.translatable("message.tinkersnewlife.cursed_scroll.known"), true);
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < newly.size(); i++) {
                if (i > 0) sb.append("、");
                CursedSpeechRegistry.Word w = CursedSpeechRegistry.get(newly.get(i));
                sb.append(Component.translatable(w.langKey()).getString());
            }
            sp.displayClientMessage(Component.translatable("message.tinkersnewlife.cursed_scroll.learn", sb.toString()), false);
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                    net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.2F);
        }
        return InteractionResultHolder.success(stack); // 不消耗
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        List<String> words = wordsOn(stack);
        if (!words.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < words.size(); i++) {
                if (i > 0) sb.append("、");
                CursedSpeechRegistry.Word w = CursedSpeechRegistry.get(words.get(i));
                if (w != null) sb.append(Component.translatable(w.langKey()).getString());
            }
            tooltip.add(Component.translatable("item.tinkersnewlife.cursed_scroll.words", sb.toString()));
        }
        tooltip.add(Component.translatable("item.tinkersnewlife.cursed_scroll.hint"));
    }

    // ============================================================
    //  §1275 左键直接吟唱（用户口径）
    //  - 手持残卷左键攻击目标时触发 onLeftClickEntity（不用新建网络包）
    //  - 缺槽 ⇒ 该槽"最低两级"里随机补一个词
    //  - 咒力消耗 = 有咒言术时的 5 倍（由 CursedSpeechTechnique 读标记 tnl_scroll_cast）
    //  - 释放后卷轴消失
    // ============================================================
    public static final String KEY_SCROLL_CAST = "tnl_scroll_cast";

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, net.minecraft.world.entity.Entity entity) {
        if (player.level().isClientSide() || !(player instanceof ServerPlayer sp)) {
            return false;
        }
        castFromScroll(sp, stack);
        return false;
    }

    /** §1277 残卷施法本体：左键命中与空挥共用 */
    public static void castFromScroll(ServerPlayer sp, ItemStack stack) {
        if (sp.level().isClientSide() || stack.isEmpty()) {
            return;
        }
        String[] chant = fillChant(stack, sp);
        for (int i = 0; i < chant.length; i++) {
            if (chant[i] != null && !chant[i].isEmpty()) {
                CursedSpeechState.learn(sp, chant[i]);
                CursedSpeechState.setChantPart(sp, i, chant[i]);
            }
        }
        sp.getPersistentData().putBoolean(KEY_SCROLL_CAST, true);
        try {
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpeechTechnique.INSTANCE.onKeyPress(sp);
        } catch (Throwable ignored) {
        } finally {
            sp.getPersistentData().remove(KEY_SCROLL_CAST);
        }
        if (com.mofengbaizhi.tinkersnewlife.content.cursespeech.CurseChant.isChanting(sp)) {
            stack.shrink(1);
        }
    }

    /** 六个槽位：卷上有词就用词；否则用玩家已学的；再缺 ⇒ 该槽"最低两级"随机补一个 */
    public static String[] fillChant(ItemStack stack, ServerPlayer player) {
        CursedSpeechRegistry.Part[] parts = CursedSpeechRegistry.Part.values();
        String[] out = new String[parts.length];
        List<String> onScroll = wordsOn(stack);
        List<String> learned = CursedSpeechState.learned(player);
        for (int i = 0; i < parts.length; i++) {
            CursedSpeechRegistry.Part part = parts[i];
            String pick = firstOfPart(onScroll, part);
            if (pick == null) {
                pick = firstOfPart(learned, part);
            }
            if (pick == null) {
                pick = lowestTwoRandom(part);
            }
            out[i] = pick;
        }
        return out;
    }

    private static String firstOfPart(List<String> ids, CursedSpeechRegistry.Part part) {
        for (String id : ids) {
            CursedSpeechRegistry.Word w = CursedSpeechRegistry.get(id);
            if (w != null && w.part() == part) {
                return id;
            }
        }
        return null;
    }

    /** 该组成部分里"最低两个等级"的词里随机取一个 */
    private static String lowestTwoRandom(CursedSpeechRegistry.Part part) {
        List<CursedSpeechRegistry.Word> pool = CursedSpeechRegistry.of(part);
        if (pool.isEmpty()) {
            return "";
        }
        List<Integer> tiers = new ArrayList<>();
        for (CursedSpeechRegistry.Word w : pool) {
            if (!tiers.contains(w.rarity())) {
                tiers.add(w.rarity());
            }
        }
        java.util.Collections.sort(tiers);
        int low = tiers.get(0);
        int second = tiers.size() > 1 ? tiers.get(1) : low;
        List<CursedSpeechRegistry.Word> cand = new ArrayList<>();
        for (CursedSpeechRegistry.Word w : pool) {
            if (w.rarity() == low || w.rarity() == second) {
                cand.add(w);
            }
        }
        return cand.get(new Random().nextInt(cand.size())).id();
    }}
