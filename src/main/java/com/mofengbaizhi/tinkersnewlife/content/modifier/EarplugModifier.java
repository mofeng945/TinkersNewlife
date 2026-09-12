package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.interaction.KeybindInteractModifierHook;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 防御槽强化「闭耳塞听」：<b>只能装在头盔上</b>（材料标签 {@code tconstruct:modifiable/armor/helmets}）。
 * <p>
 * 单级、<b>不占升级槽</b>，而是消耗 <b>1 个防御槽</b>。
 * <p>
 * 按<b>匠魂的头盔交互键</b>（TCon「头盔交互」键位，默认绑定见匠魂设置）开关耳塞：
 * <ul>
 *   <li><b>开启时听不见任何声音</b>——客户端直接屏蔽全部声音（{@code EarplugSoundHandler}）；</li>
 *   <li><b>开启时无视咒言术</b>——不会被咒言选为目标、也吃不到咒言的范围效果
 *       （{@code CursedSpeechTechnique#findChantTargets} 会把塞着耳朵的目标排除）。</li>
 * </ul>
 * 开关状态记在<b>头盔自己的持久数据</b>里（跟随头盔，换头盔各记各的），
 * 并在切换时主动把该盔甲槽同步给客户端——客户端要靠它决定"要不要屏蔽声音"。
 */
public class EarplugModifier extends Modifier implements KeybindInteractModifierHook {

    /** 强化 id */
    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "earplugs"));

    /** 头盔持久数据：耳塞是否处于开启状态 */
    public static final ResourceLocation KEY_ON =
            new ResourceLocation(TinkersNewlife.MOD_ID, "earplugs_on");

    /** 玩家物品栏菜单里头盔槽的索引（0..8 快捷栏 / 9..35 背包 / 36 靴 / 37 腿 / 38 胸 / 39 头） */
    private static final int HELMET_MENU_SLOT = 39;

    // ============================================================
    //  头盔交互键：开 / 关
    // ============================================================

    @Override
    public boolean startInteract(IToolStackView tool, ModifierEntry modifier, Player player,
                                 EquipmentSlot slot, TooltipKey keyModifier) {
        if (slot != EquipmentSlot.HEAD) return false;
        final boolean now = !isOn(tool);
        if (tool instanceof ToolStack stack) {
            stack.getPersistentData().putBoolean(KEY_ON, now);
            stack.updateStack(player.getItemBySlot(EquipmentSlot.HEAD));
        }
        if (player instanceof ServerPlayer sp) {
            // 主动同步头盔槽：客户端要靠这件物品的持久数据决定是否屏蔽声音
            sp.connection.send(new ClientboundContainerSetSlotPacket(
                    sp.inventoryMenu.containerId, sp.inventoryMenu.incrementStateId(),
                    HELMET_MENU_SLOT, sp.getItemBySlot(EquipmentSlot.HEAD)));
            sp.displayClientMessage(Component.translatable(
                    now ? "message.tinkersnewlife.earplugs.on" : "message.tinkersnewlife.earplugs.off"), true);
        }
        return true;
    }

    // ============================================================
    //  查询
    // ============================================================

    /** 该工具（头盔）上的耳塞是否已开启（读不到工具数据 → false） */
    public static boolean isOn(IToolStackView tool) {
        return tool != null && tool.getPersistentData().getBoolean(KEY_ON);
    }

    /** 该物品是不是"装了闭耳塞听"的头盔 */
    public static boolean hasEarplugs(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(stack);
        return tool != null && !tool.isBroken() && tool.getModifierLevel(ID) > 0;
    }

    /**
     * 该实体是否"塞着耳朵"：戴着装了本强化、且已开启耳塞的头盔。
     * <p>
     * 客户端也适用（物品 NBT 会同步到客户端），所以声音屏蔽与咒言免疫共用这一个判断。
     */
    public static boolean isMuffled(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        ItemStack helmet = living.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(helmet);
        return tool != null && !tool.isBroken() && tool.getModifierLevel(ID) > 0 && isOn(tool);
    }
}
