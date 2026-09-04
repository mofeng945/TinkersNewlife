package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.DragonBloodTankTrait;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.function.Consumer;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public class DragonBloodTankHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DragonBloodTankHandler.class);
    private static final ResourceLocation DRAGON_MULTIPART = new ResourceLocation("iceandfire", "dragon_multipart");
    private static final boolean DEBUG = false;

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        handleEntityInteract(event.getEntity(), event.getTarget(), event.getItemStack(), event::setCanceled);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        handleEntityInteract(event.getEntity(), event.getTarget(), event.getItemStack(), event::setCanceled);
    }

    private static void handleEntityInteract(Player player, Entity target, ItemStack stack, Consumer<Boolean> cancelConsumer) {
        Level level = player.level();
        if (level.isClientSide) return;

        // 获取龙的主实体（处理 multipart）
        Entity mainDragon = getMainDragonEntity(target);
        if (mainDragon == null) return;

        DragonType dragonType = getDragonTypeFromEntity(mainDragon);
        if (dragonType == null) return;

        // 先判断是否模型死亡
        boolean isModelDead = isModelDead(mainDragon);
        if (!isModelDead) {
            // 活龙 → 静默返回
            return;
        }

        // 模型死亡 → 可采集，但检查是否已采集完
        int ageInDays = getAgeInDays(mainDragon);
        int deathStage = getDeathStage(mainDragon);
        int maxDeathStage = Math.max(1, ageInDays / 5);
        if (deathStage >= maxDeathStage) {
            player.displayClientMessage(
                Component.translatable("modifier.tinkersnewlife.dragon_blood_tank.depleted"),
                true
            );
            return;
        }

        // ✅ 先检查是否是匠魂工具（避免警告）
        if (!(stack.getItem() instanceof IModifiable)) return;
        // ✅ 使用 ToolHelper 安全获取，避免 "non-modifiable tool" 警告
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool == null) return;
        if (tool.getModifierLevel(DragonBloodTankTrait.MODIFIER_ID) <= 0) return;

        int capacity = DragonBloodTankTrait.getCapacity(stack);
        if (capacity <= 0) return;

        DragonBloodTankTrait.DragonBloodTankData data = DragonBloodTankTrait.getTankData(stack, capacity);
        if (data == null) {
            data = new DragonBloodTankTrait.DragonBloodTankData(capacity);
        }

        if (data.getRemaining() <= 0) {
            player.displayClientMessage(
                Component.translatable("modifier.tinkersnewlife.dragon_blood_tank.tank_full"),
                true
            );
            return;
        }

        int amount = 250;
        int filled = data.fill(dragonType.bloodType, amount);

        if (filled > 0) {
            DragonBloodTankTrait.setTankData(stack, data);
            setDeathStage(mainDragon, deathStage + 1);

            // 强制保持死亡状态，防止龙骨架复活
            forceKeepDead(mainDragon);

            level.playSound(null, mainDragon.getX(), mainDragon.getY(), mainDragon.getZ(),
                    net.minecraft.sounds.SoundEvents.BOTTLE_FILL,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);

            player.displayClientMessage(
                Component.translatable("modifier.tinkersnewlife.dragon_blood_tank.collected",
                    filled, dragonType.bloodType.name),
                true
            );

            if (cancelConsumer != null) {
                cancelConsumer.accept(true);
            }

            if (DEBUG) {
                LOGGER.debug("🐉 从 {} 采集了 {} mB 的 {}",
                        dragonType.displayName, filled, dragonType.bloodType.name);
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();
        ItemStack stack = event.getItemStack();
        BlockPos pos = event.getPos();

        if (level.isClientSide) return;

        // ✅ 先检查是否是匠魂工具
        if (!(stack.getItem() instanceof IModifiable)) return;
        // ✅ 使用 ToolHelper 安全获取，避免 "non-modifiable tool" 警告
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool == null) return;
        if (tool.getModifierLevel(DragonBloodTankTrait.MODIFIER_ID) <= 0) return;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return;

        IFluidHandler tankHandler = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER)
                .orElse(null);
        if (tankHandler == null) return;

        // 判断是否是排液孔
        boolean isDrain = isDrainBlock(level, pos);

        int capacity = DragonBloodTankTrait.getCapacity(stack);
        if (capacity <= 0) return;

        DragonBloodTankTrait.DragonBloodTankData data = DragonBloodTankTrait.getTankData(stack, capacity);
        if (data == null) {
            data = new DragonBloodTankTrait.DragonBloodTankData(capacity);
        }

        boolean transferred = transferFluids(tankHandler, data, stack, isDrain);
        if (transferred) {
            event.setCanceled(true);
        }
    }

    private static boolean transferFluids(IFluidHandler tankHandler,
                                          DragonBloodTankTrait.DragonBloodTankData toolData,
                                          ItemStack toolStack,
                                          boolean isDrain) {
        boolean transferred = false;

        // ===== 第1步：如果工具中有龙血，优先导出到容器 =====
        for (DragonBloodTankTrait.DragonBloodType type : DragonBloodTankTrait.DragonBloodType.values()) {
            int amount = toolData.getAmount(type);
            if (amount <= 0) continue;

            ResourceLocation fluidId = DragonBloodTankTrait.getFluidId(type);
            if (fluidId == null) continue;
            var fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
            if (fluid == null) continue;

            FluidStack toolFluid = new FluidStack(fluid, amount);
            int filled = tankHandler.fill(toolFluid, IFluidHandler.FluidAction.SIMULATE);
            if (filled <= 0) continue;

            toolData.setAmount(type, amount - filled);
            tankHandler.fill(new FluidStack(fluid, filled), IFluidHandler.FluidAction.EXECUTE);
            transferred = true;
            break;
        }

        // ===== 第2步：如果工具中无流体，尝试从容器导入龙血 =====
        if (!transferred) {
            int remaining = toolData.getRemaining();
            if (remaining <= 0) return false;

            if (isDrain) {
                // 🟢 排液孔：只查找龙血，忽略其他非龙血流体
                // 因为排液孔一次只能排一种流体，直接从槽位中找龙血
                DragonBloodTankTrait.DragonBloodType foundType = null;
                int availableAmount = 0;

                for (int i = 0; i < tankHandler.getTanks(); i++) {
                    FluidStack tankFluid = tankHandler.getFluidInTank(i);
                    if (tankFluid.isEmpty()) continue;

                    if (DragonBloodTankTrait.isDragonBlood(tankFluid)) {
                        foundType = DragonBloodTankTrait.getDragonBloodType(tankFluid);
                        availableAmount = tankFluid.getAmount();
                        break; // 只取第一个龙血槽位
                    }
                }

                if (foundType != null && availableAmount > 0) {
                    int toFill = Math.min(availableAmount, remaining);
                    ResourceLocation fluidId = DragonBloodTankTrait.getFluidId(foundType);
                    if (fluidId != null) {
                        var fluid = ForgeRegistries.FLUIDS.getValue(fluidId);
                        if (fluid != null) {
                            FluidStack drained = tankHandler.drain(new FluidStack(fluid, toFill), IFluidHandler.FluidAction.EXECUTE);
                            if (!drained.isEmpty()) {
                                toolData.fill(foundType, drained.getAmount());
                                transferred = true;
                            }
                        }
                    }
                }
            } else {
                // 🟡 普通容器：正常检查是否有龙血
                for (int i = 0; i < tankHandler.getTanks(); i++) {
                    FluidStack tankFluid = tankHandler.getFluidInTank(i);
                    if (tankFluid.isEmpty()) continue;
                    if (!DragonBloodTankTrait.isDragonBlood(tankFluid)) continue;

                    DragonBloodTankTrait.DragonBloodType type = DragonBloodTankTrait.getDragonBloodType(tankFluid);
                    if (type == null) continue;

                    int toFill = Math.min(tankFluid.getAmount(), remaining);
                    if (toFill <= 0) continue;

                    // ⭐ 明确指定要抽取的流体，避免在多流体容器中抽到错误的流体
                    FluidStack drained = tankHandler.drain(new FluidStack(tankFluid.getFluid(), toFill), IFluidHandler.FluidAction.EXECUTE);
                    if (drained.isEmpty()) continue;

                    toolData.fill(type, drained.getAmount());
                    transferred = true;
                    break;
                }
            }
        }

        if (transferred) {
            DragonBloodTankTrait.setTankData(toolStack, toolData);
        }

        return transferred;
    }

    // ==================== 工具方法 ====================

    /**
     * 判断方块是否是匠魂的排液孔/浇铸口
     */
    private static boolean isDrainBlock(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id == null) return false;
        String path = id.getPath();
        return path.equals("seared_drain") || path.equals("scorched_drain") ||
               path.equals("seared_channel") || path.equals("scorched_channel") ||
               path.startsWith("seared_") && path.endsWith("_drain") ||
               path.startsWith("scorched_") && path.endsWith("_drain");
    }

    @Nullable
    private static Entity getMainDragonEntity(Entity target) {
        if (target == null) return null;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        if (id != null && DRAGON_MULTIPART.equals(id)) {
            try {
                Method method = target.getClass().getMethod("getParent");
                return (Entity) method.invoke(target);
            } catch (Exception e) {
                // 忽略
            }
        }
        return target;
    }

    @Nullable
    private static DragonType getDragonTypeFromEntity(Entity entity) {
        if (entity == null) return null;
        String className = entity.getClass().getSimpleName();
        if (className.contains("FireDragon")) {
            return new DragonType(DragonBloodTankTrait.DragonBloodType.FIRE, "火龙");
        }
        if (className.contains("IceDragon")) {
            return new DragonType(DragonBloodTankTrait.DragonBloodType.ICE, "冰龙");
        }
        if (className.contains("LightningDragon")) {
            return new DragonType(DragonBloodTankTrait.DragonBloodType.LIGHTNING, "雷龙");
        }
        return null;
    }

    private static boolean isModelDead(Entity dragon) {
        try {
            Method method = dragon.getClass().getMethod("isModelDead");
            return (boolean) method.invoke(dragon);
        } catch (Exception e) {
            return false;
        }
    }

    private static int getAgeInDays(Entity dragon) {
        try {
            Method method = dragon.getClass().getMethod("getAgeInDays");
            return (int) method.invoke(dragon);
        } catch (Exception e) {
            return 100;
        }
    }

    private static int getDeathStage(Entity dragon) {
        try {
            Method method = dragon.getClass().getMethod("getDeathStage");
            return (int) method.invoke(dragon);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void setDeathStage(Entity dragon, int stage) {
        try {
            Method method = dragon.getClass().getMethod("setDeathStage", int.class);
            method.invoke(dragon, stage);
        } catch (Exception e) {
            // 忽略
        }
    }

    private static void forceKeepDead(Entity dragon) {
        try {
            Method setModelDead = dragon.getClass().getMethod("setModelDead", boolean.class);
            setModelDead.invoke(dragon, true);
        } catch (Exception ignored) {}

        if (dragon instanceof LivingEntity living) {
            living.setHealth(0);
        }
    }

    private static class DragonType {
        final DragonBloodTankTrait.DragonBloodType bloodType;
        final String displayName;
        DragonType(DragonBloodTankTrait.DragonBloodType bloodType, String displayName) {
            this.bloodType = bloodType;
            this.displayName = displayName;
        }
    }
}