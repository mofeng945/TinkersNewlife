package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.FlyingSwordEntity;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class FlyingSwordItem extends ModifiableItem implements ICurioItem {

    public static final ToolDefinition FLYING_SWORD_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "flying_sword"));

    private static final ModifierId FLYING_SWORD_MODIFIER =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "flying_sword"));
    private static final ModifierId UNBREAKABLE_MODIFIER =
            new ModifierId(new ResourceLocation("tconstruct", "unbreakable"));

    public static final ResourceLocation MODE_KEY =
            new ResourceLocation(TinkersNewlife.MOD_ID, "flying_sword_mode");

    private static final Random RANDOM = new Random();

    // ✅ 发射标志：标记当前正在右键发射飞剑的玩家
    public static final ThreadLocal<UUID> EMITTING_PLAYER = new ThreadLocal<>();

    public FlyingSwordItem(Properties properties) {
        super(properties, FLYING_SWORD_DEFINITION);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            try {
                // ✅ 设置发射标志
                EMITTING_PLAYER.set(player.getUUID());

                // ✅ 使用 ToolHelper 安全获取
                ToolStack tool = ToolHelper.getToolStack(stack);
                if (tool == null || tool.isBroken()) {
                    return InteractionResultHolder.fail(stack);
                }

                if (tool.getModifierLevel(FLYING_SWORD_MODIFIER) <= 0) {
                    return InteractionResultHolder.pass(stack);
                }

                boolean hasUnbreakable = tool.getModifierLevel(UNBREAKABLE_MODIFIER) > 0;
                int actualCost = 0;

                if (!hasUnbreakable) {
                    int oldDamage = tool.getDamage();
                    stack.hurt(20, player.getRandom(), (ServerPlayer) player);
                    // ✅ 使用 ToolHelper 安全获取
                    ToolStack refreshed = ToolHelper.getToolStack(stack);
                    if (refreshed == null || refreshed.isBroken()) {
                        return InteractionResultHolder.fail(stack);
                    }
                    actualCost = Math.max(0, refreshed.getDamage() - oldDamage);
                }

                ToolDataNBT persistentData = tool.getPersistentData();
                int mode = persistentData.getInt(MODE_KEY);
                boolean isChaseMode = mode == 1;

                float mainDamage = tool.getStats().get(ToolStats.ATTACK_DAMAGE);
                float projectileDamage = mainDamage * 0.6f;

                ItemStack randomSword = createRandomFlyingSword();

                FlyingSwordEntity sword = new FlyingSwordEntity(level, player, projectileDamage, randomSword);
                sword.setChaseMode(isChaseMode);
                sword.setLaunchDirection(player.getLookAngle());
                if (isChaseMode) {
                    sword.findAndSetTarget();
                }

                final int cost = actualCost;
                sword.setReturnCallback(hitCount -> {
                    if (!level.isClientSide && cost > 0) {
                        int repair = Math.max(0, cost - hitCount);
                        if (repair > 0) {
                            ItemStack currentStack = player.getItemInHand(hand);
                            // ✅ 使用 ToolHelper 安全获取
                            ToolStack currentTool = ToolHelper.getToolStack(currentStack);
                            if (currentTool != null && currentTool.getModifierLevel(FLYING_SWORD_MODIFIER) > 0) {
                                int newDamage = Math.max(0, currentTool.getDamage() - repair);
                                currentTool.setDamage(newDamage);
                                currentTool.updateStack(currentStack);
                            }
                        }
                    }
                });

                sword.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 0.75f, 1.0F);
                level.addFreshEntity(sword);

                return InteractionResultHolder.success(stack);
            } finally {
                // ✅ 清除发射标志
                EMITTING_PLAYER.remove();
            }
        }
        return InteractionResultHolder.success(stack);
    }

    private ItemStack createRandomFlyingSword() {
        List<IMaterial> available = new ArrayList<>();
        for (IMaterial mat : MaterialRegistry.getInstance().getAllMaterials()) {
            if (mat.getTier() >= 3) {
                available.add(mat);
            }
        }
        if (available.isEmpty()) {
            return new ItemStack(this);
        }
        MaterialVariant[] variants = new MaterialVariant[5];
        for (int i = 0; i < 5; i++) {
            IMaterial mat = available.get(RANDOM.nextInt(available.size()));
            variants[i] = MaterialVariant.of(mat);
        }
        MaterialNBT materials = MaterialNBT.of(variants);
        ItemStack stack = new ItemStack(this);
        // ✅ 使用 ToolHelper 安全获取
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool == null) return stack;
        tool.setMaterials(materials);
        tool.rebuildStats();
        tool.updateStack(stack);
        return stack;
    }

    public static int getMode(ItemStack stack) {
        // ✅ 使用 ToolHelper 安全获取
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool == null) return 0;
        return tool.getPersistentData().getInt(MODE_KEY);
    }

    // ---- ICurioItem ----
    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        return "feet".equals(context.identifier());
    }

    @Override
    public boolean canEquipFromUse(SlotContext context, ItemStack stack) {
        return false;
    }
}