package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.item.DurandalSwordItem;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DurandalConversionHandler {

    private static final ResourceLocation DURANDAL_MATERIAL =
            new ResourceLocation(TinkersNewlife.MOD_ID, "durandal");

    private static final Set<ResourceLocation> ALLOWED_TOOLS = new HashSet<>();

    static {
        ALLOWED_TOOLS.add(new ResourceLocation(TinkersNewlife.MOD_ID, "flying_sword"));
        ALLOWED_TOOLS.add(new ResourceLocation("tconstruct", "sword"));
        ALLOWED_TOOLS.add(new ResourceLocation("tconstruct", "cleaver"));
    }

    private static final Set<UUID> convertedCache = new HashSet<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        if (player.tickCount % 20 != 0) return;

        ItemStack mainHand = player.getMainHandItem();
        ItemStack result = checkAndConvert(mainHand, player);
        if (result != null && result != mainHand) {
            player.setItemInHand(InteractionHand.MAIN_HAND, result);
            return;
        }

        ItemStack offHand = player.getOffhandItem();
        ItemStack resultOff = checkAndConvert(offHand, player);
        if (resultOff != null && resultOff != offHand) {
            player.setItemInHand(InteractionHand.OFF_HAND, resultOff);
        }
    }

    private static ItemStack checkAndConvert(ItemStack stack, Player player) {
        if (stack.isEmpty()) return stack;
        if (!(stack.getItem() instanceof IModifiable)) return stack;
        if (stack.getItem() instanceof DurandalSwordItem) return stack;

        UUID playerId = player.getUUID();
        if (convertedCache.contains(playerId)) return stack;

        // ✅ 使用 ToolHelper 安全获取，避免 "non-modifiable tool" 警告
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool == null || tool.isBroken()) return stack;

        // ✅ 使用 getId() 获取工具定义 ID
        ResourceLocation toolDefId = tool.getDefinition().getId();
        if (!ALLOWED_TOOLS.contains(toolDefId)) {
            return stack;
        }

        MaterialNBT materials = tool.getMaterials();
        if (materials.isEmpty()) return stack;

        for (MaterialVariant variant : materials) {
            if (!variant.getId().equals(DURANDAL_MATERIAL)) {
                return stack;
            }
        }

        // --- 转换 ---
        ItemStack newSword = new ItemStack(ModItems.DURANDAL_SWORD.get());

        CompoundTag oldTag = stack.getTag();
        if (oldTag != null) {
            CompoundTag newTag = oldTag.copy();
            newTag.remove("tic_materials");
            newTag.remove("tic_stats");
            newTag.remove("tic_modifiers");
            newTag.remove("tic_volatile_data");
            newTag.remove("tic_multipliers");
            newTag.remove("tic_broken");
            newSword.setTag(newTag);
        }

        CompoundTag tag = newSword.getOrCreateTag();
        tag.putBoolean("Unbreakable", true);

        convertedCache.add(playerId);

        player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.tinkersnewlife.durandal.converted")
                        .withStyle(net.minecraft.ChatFormatting.GOLD),
                false
        );

        TinkersNewlife.LOGGER.info("【杜兰达尔】玩家 {} 的工具 ({}) 已转换为杜兰达尔剑",
                player.getName().getString(), toolDefId);

        return newSword;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        convertedCache.remove(event.getEntity().getUUID());
    }
}