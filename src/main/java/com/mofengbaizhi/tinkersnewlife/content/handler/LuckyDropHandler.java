package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.YoYoEntity;
import com.mofengbaizhi.tinkersnewlife.util.ProjectileWeaponHelper;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

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

        // ✅ 统一获取攻击武器：悠悠球从球实体读取完整工具，否则弹射物/主手/副手
        ItemStack weaponStack = ItemStack.EMPTY;
        Entity directEntity = source.getDirectEntity();
        if (directEntity instanceof YoYoEntity yoYo) {
            weaponStack = yoYo.getReturnStack();
        } else if (directEntity instanceof Projectile projectile) {
            weaponStack = ProjectileWeaponHelper.getProjectileWeapon(projectile, player);
        }
        if (weaponStack.isEmpty()) {
            weaponStack = player.getMainHandItem();
        }
        if (weaponStack.isEmpty()) {
            weaponStack = player.getOffhandItem();
        }
        if (weaponStack.isEmpty()) return;

        // ✅ 使用 ToolHelper 安全获取，避免 "non-modifiable tool" 警告；
        // 主手/副手武器不含幸运特性时，兜底取佩戴的咒力核心
        ToolStack tool = ToolHelper.getToolWithModifier(player, ToolHelper.getToolStack(weaponStack), LUCKY_DROP);
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
            // 🎲 普通生物：判定成功后，额外在战利品表中 roll 选一次。
            // 不再复制已有掉落（原逻辑：生物没掉落时即使判定成功也什么都不掉），
            // 重新抽取可能抽到物品，也可能为空。
            if (entity.level() instanceof ServerLevel serverLevel) {
                ResourceLocation lootTableId = entity.getLootTable();
                LootTable lootTable = serverLevel.getServer().getLootData().getLootTable(lootTableId);
                if (lootTable != null) {
                    // ✅ 走正常掉落逻辑：与实体正常死亡掉落一致的 LootParams 上下文。
                    // 抢夺等级由 LootContext.getLootingModifier() 实时计算
                    // （ForgeHooks.getLootingLevel 基于 THIS_ENTITY/KILLER_ENTITY/DAMAGE_SOURCE），
                    // 自动包含原版 Looting 附魔、匠魂幸运与其他模组的掉落提升；
                    // 玩家幸运值随 LAST_DAMAGE_PLAYER 一并传入。
                    LootParams.Builder paramsBuilder = new LootParams.Builder(serverLevel);
                    paramsBuilder.withParameter(LootContextParams.ORIGIN, entity.position());
                    paramsBuilder.withOptionalParameter(LootContextParams.THIS_ENTITY, entity);
                    paramsBuilder.withParameter(LootContextParams.DAMAGE_SOURCE, source);
                    paramsBuilder.withOptionalParameter(LootContextParams.KILLER_ENTITY, source.getEntity());
                    paramsBuilder.withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, source.getDirectEntity());
                    if (player != null) {
                        paramsBuilder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);
                        paramsBuilder.withLuck(player.getLuck());
                    }
                    LootParams params = paramsBuilder.create(LootContextParamSets.ENTITY);
                    List<ItemStack> extraRolls = new ArrayList<>();
                    lootTable.getRandomItems(params, 0L, extraRolls::add);
                    for (ItemStack stack : extraRolls) {
                        if (stack.isEmpty()) continue;
                        ItemEntity newDrop = new ItemEntity(
                                entity.level(),
                                entity.getX() + (entity.level().random.nextDouble() - 0.5) * 0.5,
                                entity.getY() + 0.5,
                                entity.getZ() + (entity.level().random.nextDouble() - 0.5) * 0.5,
                                stack.copy()
                        );
                        newDrop.setPickUpDelay(10);
                        event.getDrops().add(newDrop);
                    }
                }
            }
        }
    }
}