package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.interaction.KeybindInteractModifierHook;
import slimeknights.tconstruct.library.modifiers.impl.SingleLevelModifier;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 防御槽强化「闭耳塞听」：<b>只能装在头盔上</b>（材料标签 {@code tconstruct:modifiable/armor/helmets}）。
 * <p>
 * <b>无等级强化</b>（{@link SingleLevelModifier}：名字不带等级数字），<b>不占升级槽</b>，而是消耗
 * <b>1 个防御槽</b>。
 * <p>
 * 按<b>匠魂的头盔交互键</b>（TCon「头盔交互」，默认 Z）开关耳塞：
 * <ul>
 *   <li><b>开启时听不见任何声音</b>——客户端直接屏蔽全部声音（{@code EarplugSoundHandler}）；</li>
 *   <li><b>开启时无视咒言术</b>——不会被咒言选为目标、也吃不到咒言的范围效果
 *       （{@code CursedSpeechTechnique#findChantTargets} 会把塞着耳朵的目标排除）。</li>
 * </ul>
 * 开关状态记在<b>头盔自己的持久数据</b>里（跟随头盔，换头盔各记各的），
 * 并在切换时主动把该盔甲槽同步给客户端——客户端要靠它决定"要不要屏蔽声音"。
 * <p>
 * ⚠️ 注意：匠魂 3.11 的 {@code Modifier#getHook} 是 <b>final</b> 的、只查 {@code hooks} 表，
 * 所以"实现了钩子接口"还不够，必须在 {@link #registerHooks} 里显式登记
 * {@link ModifierHooks#ARMOR_INTERACT}——否则头盔交互键按下去<b>不会</b>调到本类（实测踩过）。
 */
public class EarplugModifier extends SingleLevelModifier implements KeybindInteractModifierHook {

    /** 强化 id */
    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "earplugs"));

    /** 头盔持久数据：耳塞是否处于开启状态 */
    public static final ResourceLocation KEY_ON =
            new ResourceLocation(TinkersNewlife.MOD_ID, "earplugs_on");

    // 注：不要再拿"玩家 Inventory 的物品索引"（36 靴 / 37 腿 / 38 胸 / 39 头）当菜单槽位号用 ——
    //     背包菜单里盔甲是 5~8（5 = 头），两者的 39 正好是"快捷栏第 4 格"，曾经因此把假头盔塞进物品栏。

    /** 登记钩子：头盔交互键（见类注释——不登记就收不到按键） */
    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.ARMOR_INTERACT);
    }

    // ============================================================
    //  头盔交互键：开 / 关
    // ============================================================

    @Override
    public boolean startInteract(IToolStackView tool, ModifierEntry modifier, Player player,
                                 EquipmentSlot slot, TooltipKey keyModifier) {
        if (slot != EquipmentSlot.HEAD) return false;
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.isEmpty()) return false;
        // ⭐ 用 ToolHelper 重新取"可写的 ToolStack"（钩子给的是只读视图 IToolStackView，
        //    直接强转不一定成立；拿不到工具数据就直接吃掉这次按键、不改状态）
        ToolStack stack = ToolHelper.getToolStack(helmet);
        if (stack == null || stack.getModifierLevel(ID) <= 0) return false;
        final boolean now = !stack.getPersistentData().getBoolean(KEY_ON);
        stack.getPersistentData().putBoolean(KEY_ON, now);
        stack.updateStack(helmet);

        if (player instanceof ServerPlayer sp) {
            // ⭐ 主动同步头盔槽：客户端要靠这件物品的持久数据决定是否屏蔽声音。
            //
            // ⚠⚠ 踩过的坑：这里以前发的是 ClientboundContainerSetSlotPacket(..., HELMET_MENU_SLOT=39, helmet)，
            //   而 39 是**玩家 Inventory 的物品索引**（36 靴 / 37 腿 / 38 胸 / 39 头），
            //   **不是**玩家背包菜单（InventoryMenu）的槽位号 —— 菜单里盔甲是 5~8（5 = 头）。
            //   于是那个包把头盔塞进了**菜单第 39 号槽 = 快捷栏第 4 格**，表现为：
            //   按头盔交互键后物品栏第 4 格变成一个"头盔假物品"（只是客户端显示，服务端没变），
            //   打开背包点一下它才复原。
            //   现在改成让菜单自己广播变更：不用手写槽位号，两个索引体系不会再搞混。
            sp.inventoryMenu.broadcastChanges();
            sp.displayClientMessage(Component.translatable(
                    now ? "message.tinkersnewlife.earplugs.on" : "message.tinkersnewlife.earplugs.off"), true);
            TinkersNewlife.LOGGER.debug("[闭耳塞听] {} 耳塞 -> {}", sp.getName().getString(), now ? "开" : "关");
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
        return tool != null && !tool.isBroken() && ToolHelper.getActiveModifierLevel(tool, ID) > 0;
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
        return tool != null && !tool.isBroken() && ToolHelper.getActiveModifierLevel(tool, ID) > 0 && isOn(tool);
    }
}
