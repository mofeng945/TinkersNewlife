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
        int count = 1 + r.nextInt(3); // 1..3 段
        for (int i = 0; i < count; i++) {
            // 按稀有度加权：权重 = 5 - rarity（稀有度 0..5 → 权重 5..0，最低取 1）
            int total = 0;
            int[] weights = new int[pool.size()];
            for (int j = 0; j < pool.size(); j++) {
                int w = Math.max(1, 5 - pool.get(j).rarity());
                weights[j] = w;
                total += w;
            }
            int roll = r.nextInt(total);
            int acc = 0;
            String chosen = null;
            for (int j = 0; j < pool.size(); j++) {
                acc += weights[j];
                if (roll < acc) {
                    chosen = pool.get(j).id();
                    break;
                }
            }
            if (chosen != null) {
                list.add(StringTag.valueOf(chosen));
            }
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
}
