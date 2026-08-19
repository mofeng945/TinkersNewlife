package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LuckyDropHandler {

    private static final ModifierId LUCKY_DROP = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "lucky_drop"));
    private static final double DROP_CHANCE = 0.5;
    private static final String ICEANDFIRE_NAMESPACE = "iceandfire";

    private static final Map<ResourceLocation, ResourceLocation> RARE_DROP_MAP = new HashMap<>();

    static {
        RARE_DROP_MAP.put(new ResourceLocation("iceandfire", "ghost"), new ResourceLocation("iceandfire", "ghost_ingot"));
        RARE_DROP_MAP.put(new ResourceLocation("iceandfire", "hippogryph"), new ResourceLocation("iceandfire", "hippogryph_talon"));
        RARE_DROP_MAP.put(new ResourceLocation("iceandfire", "if_pixie"), new ResourceLocation("iceandfire", "pixie_wings"));
        RARE_DROP_MAP.put(new ResourceLocation("iceandfire", "hippocampus"), new ResourceLocation("iceandfire", "hippocampus_fin"));
        RARE_DROP_MAP.put(new ResourceLocation("iceandfire", "deathworm"), new ResourceLocation("iceandfire", "deathworm_tounge"));
        RARE_DROP_MAP.put(new ResourceLocation("iceandfire", "if_cockatrice"), new ResourceLocation("iceandfire", "cockatrice_eye"));
        RARE_DROP_MAP.put(new ResourceLocation("iceandfire", "siren"), new ResourceLocation("iceandfire", "siren_tear"));
    }

    // ==================== 辅助方法：从伤害源获取对应武器 ====================
    private static ItemStack getWeaponForDamageSource(DamageSource source, Player player) {
        Entity directEntity = source.getDirectEntity();
        // 如果是弹射物，尝试从弹射物本身获取物品
        if (directEntity instanceof Projectile projectile) {
            try {
                Method method = projectile.getClass().getMethod("getPickupItem");
                return (ItemStack) method.invoke(projectile);
            } catch (Exception ignored) {}
            try {
                Method method = projectile.getClass().getMethod("getItem");
                return (ItemStack) method.invoke(projectile);
            } catch (Exception ignored) {}
        }
        // 若无法获取，回退到玩家主手
        if (!player.getMainHandItem().isEmpty()) {
            return player.getMainHandItem();
        }
        return player.getOffhandItem();
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;

        DamageSource source = event.getSource();
        Player player = null;
        if (source.getEntity() instanceof Player p) {
            player = p;
        } else if (source.getDirectEntity() instanceof Player p) {
            player = p;
        }

        if (player == null) return;

        // ★ 使用辅助方法获取武器，兼容近战、弓箭、投掷武器
        ItemStack weaponStack = getWeaponForDamageSource(source, player);
        if (weaponStack.isEmpty()) return;

        ToolStack tool = ToolStack.from(weaponStack);
        if (tool == null) return;

        int level = tool.getModifierLevel(LUCKY_DROP);
        if (level <= 0) return;

        if (entity.level().random.nextDouble() >= DROP_CHANCE) return;

        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (entityId == null) return;

        ResourceLocation rareDropId = RARE_DROP_MAP.get(entityId);

        if (rareDropId != null) {
            boolean found = false;
            for (ItemEntity drop : event.getDrops()) {
                ResourceLocation dropItemId = ForgeRegistries.ITEMS.getKey(drop.getItem().getItem());
                if (dropItemId != null && dropItemId.equals(rareDropId)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                ItemStack forcedDrop = new ItemStack(ForgeRegistries.ITEMS.getValue(rareDropId), 1);
                ItemEntity newDrop = new ItemEntity(
                        entity.level(),
                        entity.getX() + (entity.level().random.nextDouble() - 0.5) * 0.5,
                        entity.getY() + 0.5,
                        entity.getZ() + (entity.level().random.nextDouble() - 0.5) * 0.5,
                        forcedDrop
                );
                newDrop.setPickUpDelay(10);
                event.getDrops().add(newDrop);
            }
        } else {
            List<ItemEntity> toCopy = new ArrayList<>(event.getDrops());
            for (ItemEntity original : toCopy) {
                ItemStack copy = original.getItem().copy();
                ItemEntity newDrop = new ItemEntity(
                        entity.level(),
                        entity.getX() + (entity.level().random.nextDouble() - 0.5) * 0.5,
                        entity.getY() + 0.5,
                        entity.getZ() + (entity.level().random.nextDouble() - 0.5) * 0.5,
                        copy
                );
                newDrop.setPickUpDelay(10);
                event.getDrops().add(newDrop);
            }
        }
    }
}