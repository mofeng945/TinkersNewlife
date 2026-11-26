package com.mofengbaizhi.tinkersnewlife.content;
import com.mofengbaizhi.tinkersnewlife.content.curse.binding.BindingStateHandler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.data.ClientCurseData;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.item.CurseCoreItem;
import com.mofengbaizhi.tinkersnewlife.content.item.FlyingSwordItem;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 物品额外描述提示（仅客户端）。
 * <p>
 * 注意：使用 I18n.get() 判断翻译键是否存在，而不是 Component.translatable().getString()——
 * 后者对含 % 占位符的翻译值会抛 MissingFormatArgumentException 导致崩溃。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public class ModTooltips {

    /** 翻译键是否存在（I18n.get 缺失时返回键本身，且不做 % 格式化） */
    private static boolean hasKey(String key) {
        return !key.equals(I18n.get(key));
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();

        // ⭐ §813：先把提示里的"通用词"（术式键 / 反转键 / 形态选择键）替换成**当前实际按键** ✓
        //    （用户口径：改了快捷键，提示也跟着变 ✓）
        applyKeyHints(event.getToolTip());

        // ============================================================
        //  TNL1307 用户口径：「之前删工具提示的时候不小心把龙血储罐的提示也删了，补回来」
        //  ⇒ 匠魂钩子（DragonBloodTankTrait#addTooltip）现在**还在**，但为防"钩子没挂上/被别的
        //    模组的工具提示流程吞掉"这类情况，这里再做一层**保底补画**：
        //    只有"工具上真有龙血储罐、且提示里还没有那一行"时才补 ⇒ 正常情况下**不会重复** ✓
        // ============================================================
        tnl$ensureDragonBloodTankTooltip(event);

        // ⭐ 咒力核心：只额外提示当前总咒力亲和与咒力值（其余机制见帕秋莉手册/JEI）
        if (stack.getItem() instanceof CurseCoreItem) {
            Player player = event.getEntity();
            if (player != null) {
                int affinity = CursePowerHelper.getCurseAffinity(player);
                ToolStack tool = ToolHelper.getToolStack(stack);
                if (tool != null) {
                    int output = tool.getModifierLevel(Modifiers.CURSE_OUTPUT.getId());
                    int total = tool.getModifierLevel(Modifiers.CURSE_TOTAL.getId());
                    // ⭐ 天与咒缚·咒力：佩戴该核心时总量/输出各 +1（束缚标识已同步到客户端，tooltip 一并计入）
                    if (com.mofengbaizhi.tinkersnewlife.content.curse.binding.BindingStateHandler.isBound(player)
                            && CursePowerHelper.isEquippedCurseCore(player, stack)) {
                        if (output > 0) output++;
                        if (total > 0) total++;
                    }
                    double max = total * (output + affinity / 10.0) * 500.0;
                    int curse = (int) Math.floor(ClientCurseData.getCurse());
                    event.getToolTip().add(Component.translatable("tooltip.tinkersnewlife.curse_affinity", affinity)
                            .withStyle(ChatFormatting.LIGHT_PURPLE));
                    event.getToolTip().add(Component.translatable("tooltip.tinkersnewlife.curse_amount", curse, (int) max)
                            .withStyle(ChatFormatting.GOLD));
                }
            }
            return;
        }

        // ⭐ 咒力亲和（战利品生成的饰品可能携带）
        int affinity = CursePowerHelper.getCurseAffinity(stack);
        if (affinity > 0) {
            // §1301 用户口径：饰品提示固定两行 —— 标题 + 数值（模仿原版属性提示的排版）
            //   （Curios 5.14.1 只应用属性、不画这行提示，所以由我们自己写 ✓）
            event.getToolTip().add(Component.translatable("tooltip.tinkersnewlife.curse_affinity.slot")
                    .withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.translatable("tooltip.tinkersnewlife.curse_affinity.value", affinity)
                    .withStyle(ChatFormatting.BLUE));
        }

        ResourceLocation regName = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (regName == null) {
            return;
        }

        if (stack.getItem() instanceof FlyingSwordItem) {
            int mode = FlyingSwordItem.getMode(stack);
            String modeKey = mode == 0 ?
                "tooltip.tinkersnewlife.flying_sword.mode.normal" :
                "tooltip.tinkersnewlife.flying_sword.mode.chase";
            event.getToolTip().add(Component.translatable(modeKey).withStyle(ChatFormatting.GOLD));
            // ⭐ §813：键位提示**实时读当前按键**（用户口径：改了快捷键，提示也跟着变 ✓）
            //    —— 原来写死"按 R 键"，而实际绑定早就改成 Z 了 ✗（KeyBindings 里 Z 才是飞剑切换 ✓）
            event.getToolTip().add(Component.translatable("tooltip.tinkersnewlife.flying_sword.switch_hint",
                    com.mofengbaizhi.tinkersnewlife.client.input.KeyBindings.SWITCH_FLYING_SWORD_MODE.get()
                            .getTranslatedKeyMessage())
                .withStyle(ChatFormatting.GRAY));
            return;
        }

        String path = regName.getPath();

        // ⚠ §1099 <b>唱片不要在这里再补一行</b> ✗——原版 {@code RecordItem#appendHoverText} **自己**就会加
        //   同一个键 {@code item.<id>.desc}（唱片那行"作者 - 曲目"灰字 ✓）
        //   ⇒ 我们再补一遍 ⇒ **两行一模一样** ✗（用户实测截图 ✓）。
        //   ⇒ 唱片一律交给原版 ✓（它的行本来就来自同一个键 ✓ 文案不用改 ✓）。
        if (stack.getItem() instanceof net.minecraft.world.item.RecordItem) {
            return;
        }

        // 尝试 item 前缀
        String descKey = "item." + TinkersNewlife.MOD_ID + "." + path + ".desc";
        if (hasKey(descKey)) {
            event.getToolTip().add(Component.translatable(descKey).withStyle(ChatFormatting.GRAY));
            return;
        }

        // 尝试 block 前缀（方块物品）
        descKey = "block." + TinkersNewlife.MOD_ID + "." + path + ".desc";
        if (hasKey(descKey)) {
            event.getToolTip().add(Component.translatable(descKey).withStyle(ChatFormatting.GRAY));
        }
    }

    // ============================================================
    //  §813 键位提示：把"通用词"换成当前实际按键（用户口径 ✓）
    // ============================================================

    /**
     * 把物品提示里出现的"通用词"（术式键 / 反转键 / 形态选择键）替换成<b>当前绑定的按键</b> ✓。
     *
     * <p>为什么要在提示这一层做：术式那类长描述是<b>匠魂自己</b>从 {@code modifier.<id>.description}
     * 渲染的 ✓ 我们没法给它传参数 ✗；而同一份文本在<b>匠魂自己的界面</b>里也会显示 ⇒
     * 语言文件里塞占位符会在那边露出来 ✗。所以：
     * <ul>
     *   <li>语言文件里写<b>通用词</b>（＝按键设置里那个名字 ✓ 任何界面都不失效 ✓）；</li>
     *   <li>只在<b>物品提示</b>这一层把通用词换成玩家真正绑定的键 ✓ ⇒ 改键后立刻跟着变 ✓。</li>
     * </ul>
     */
    private static void applyKeyHints(java.util.List<Component> tooltip) {
        try {
            for (int i = 0; i < tooltip.size(); i++) {
                Component line = tooltip.get(i);
                String text = line.getString();
                if (text.isEmpty()) continue;
                String replaced = swap(text, "key.tinkersnewlife.hint.technique",
                        com.mofengbaizhi.tinkersnewlife.util.KeyHintHelper.TECHNIQUE);
                replaced = swap(replaced, "key.tinkersnewlife.hint.reverse",
                        com.mofengbaizhi.tinkersnewlife.util.KeyHintHelper.REVERSE);
                replaced = swap(replaced, "key.tinkersnewlife.hint.form",
                        com.mofengbaizhi.tinkersnewlife.util.KeyHintHelper.FORM);
                if (!replaced.equals(text)) {
                    // 保留本行原有样式（描述是灰字 ✓）
                    tooltip.set(i, Component.literal(replaced).withStyle(line.getStyle()));
                }
            }
        } catch (Throwable ignored) {
            // 替换失败就保留通用词 ✓ 绝不影响提示显示 ✓
        }
    }

    /** 把某个"通用词"（按当前语言取 ✓）换成该键位当前绑定的名字 ✓ */
    private static String swap(String text, String wordKey, String keyId) {
        String word = I18n.get(wordKey);
        if (word == null || word.isEmpty() || word.equals(wordKey) || !text.contains(word)) {
            return text;
        }
        String live = com.mofengbaizhi.tinkersnewlife.util.KeyHintHelper.display(keyId);
        return (live == null || live.isEmpty()) ? text : text.replace(word, live);
    }

    /**
     * TNL1307 龙血储罐提示保底：匠魂的 {@code TooltipModifierHook} 正常时**已经有这一行**，
     * 这里按"提示里是否已存在同一个翻译键"去重，只在缺失时补 ✓
     */
    private static void tnl$ensureDragonBloodTankTooltip(
            net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        try {
            ItemStack stack = event.getItemStack();
            if (stack == null || stack.isEmpty()) {
                return;
            }
            ToolStack tool = ToolHelper.getToolStack(stack);
            if (tool == null) {
                return;
            }
            int level = tool.getModifierLevel(
                    com.mofengbaizhi.tinkersnewlife.content.modifier.DragonBloodTankTrait.MODIFIER_ID);
            if (level <= 0) {
                return;
            }
            // 去重：匠魂钩子已经加过 ⇒ 什么都不做
            if (tnl$hasTranslatableKey(event.getToolTip(),
                    "modifier.tinkersnewlife.dragon_blood_tank.capacity")) {
                return;
            }
            int capacity = level
                    * com.mofengbaizhi.tinkersnewlife.content.modifier.DragonBloodTankTrait.CAPACITY_PER_LEVEL;
            com.mofengbaizhi.tinkersnewlife.content.modifier.DragonBloodTankTrait.DragonBloodTankData data =
                    com.mofengbaizhi.tinkersnewlife.content.modifier.DragonBloodTankTrait.getTankData(tool, capacity);
            if (data == null) {
                data = new com.mofengbaizhi.tinkersnewlife.content.modifier.DragonBloodTankTrait
                        .DragonBloodTankData(capacity);
            }
            event.getToolTip().add(Component.translatable(
                    "modifier.tinkersnewlife.dragon_blood_tank.capacity",
                    data.getTotalFilled(), capacity));
            boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
            if (shift) {
                if (data.getFireAmount() > 0) {
                    event.getToolTip().add(Component.translatable(
                            "modifier.tinkersnewlife.dragon_blood_tank.fire", data.getFireAmount()));
                }
                if (data.getIceAmount() > 0) {
                    event.getToolTip().add(Component.translatable(
                            "modifier.tinkersnewlife.dragon_blood_tank.ice", data.getIceAmount()));
                }
                if (data.getLightningAmount() > 0) {
                    event.getToolTip().add(Component.translatable(
                            "modifier.tinkersnewlife.dragon_blood_tank.lightning", data.getLightningAmount()));
                }
                int remaining = capacity - data.getTotalFilled();
                if (remaining > 0) {
                    event.getToolTip().add(Component.translatable(
                            "modifier.tinkersnewlife.dragon_blood_tank.remaining", remaining));
                }
            } else if (data.getTotalFilled() > 0) {
                event.getToolTip().add(Component.translatable(
                        "modifier.tinkersnewlife.dragon_blood_tank.shift_hint"));
            }
        } catch (Throwable ignored) {
            // 工具提示出错绝不崩客户端
        }
    }

    /** 提示里是否已经有某个翻译键（★只认最外层的 TranslatableContents ★） */
    private static boolean tnl$hasTranslatableKey(java.util.List<Component> tooltip, String key) {
        for (Component line : tooltip) {
            if (line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                    && key.equals(tc.getKey())) {
                return true;
            }
        }
        return false;
    }
}
