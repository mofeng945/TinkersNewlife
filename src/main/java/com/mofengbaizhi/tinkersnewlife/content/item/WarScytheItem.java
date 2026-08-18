package com.mofengbaizhi.tinkersnewlife.content.item;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.server.ServerLifecycleHooks;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WarScytheItem extends ModifiableItem {

    public static final ToolDefinition WAR_SCYTHE_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "war_scythe"));

    private static final UUID REACH_MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID SPEED_MODIFIER_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f23456789012");

    private final float reachBonus;
    private final float speedPerFever;

    private static final String TAG_FEVER = "chaos_fever";
    private static final Random RANDOM = new Random();

    private static final float[] ULTIMATE_MULTIPLIERS = {0.8f, 0.8f, 1.2f, 0.9f, 2.0f};

    private static boolean isPerformingUltimate = false;

    public static boolean isPerformingUltimate() {
        return isPerformingUltimate;
    }

    public static int getFever(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return 0;
        return tag.getInt(TAG_FEVER);
    }

    public static void setFever(ItemStack stack, int fever) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_FEVER, Math.min(100, Math.max(0, fever)));
    }

    public WarScytheItem(Properties properties, float reachBonus, float speedPerFever) {
        super(properties, WAR_SCYTHE_DEFINITION);
        this.reachBonus = reachBonus;
        this.speedPerFever = speedPerFever;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map = ArrayListMultimap.create(super.getAttributeModifiers(slot, stack));
        if (slot == EquipmentSlot.MAINHAND) {
            Attribute reachAttr = BuiltInRegistries.ATTRIBUTE.get(new ResourceLocation("forge", "reach_distance"));
            if (reachAttr != null) {
                map.put(reachAttr, new AttributeModifier(REACH_MODIFIER_UUID,
                        "War Scythe Reach", reachBonus, AttributeModifier.Operation.ADDITION));
            }
            int fever = getFever(stack);
            if (fever > 0) {
                float speedBonus = fever * speedPerFever;
                map.put(Attributes.ATTACK_SPEED, new AttributeModifier(SPEED_MODIFIER_UUID,
                        "Fever Attack Speed", speedBonus, AttributeModifier.Operation.ADDITION));
            }
        }
        return map;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        int fever = getFever(stack);
        if (fever == 100 && player.isShiftKeyDown()) {
            performUltimate(level, player, stack);
            setFever(stack, 0);
            // 移除释放提示
            return InteractionResultHolder.success(stack);
        }
        // 移除提示（无任何消息）
        return InteractionResultHolder.pass(stack);
    }

    private void performUltimate(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        AABB aabb = new AABB(
                player.getX() - 4.0, player.getY() - 4.0, player.getZ() - 4.0,
                player.getX() + 4.0, player.getY() + 4.0, player.getZ() + 4.0
        );
        List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, aabb,
                e -> e != player && e.isAlive());

        if (targets.isEmpty()) {
            // 无目标时不释放大招，也不提示（保持安静）
            return;
        }

        ToolStack tool = ToolStack.from(stack);
        float baseDamage = tool.getStats().get(ToolStats.ATTACK_DAMAGE);

        isPerformingUltimate = true;
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        for (int i = 0; i < 5; i++) {
            final int index = i;
            final float damage = baseDamage * ULTIMATE_MULTIPLIERS[index];
            long delay = index * 200L;
            executor.schedule(() -> {
                ServerLifecycleHooks.getCurrentServer().execute(() -> {
                    if (player.isRemoved() || player.level() != level) {
                        return;
                    }
                    for (LivingEntity target : targets) {
                        if (target.isAlive() && target.level() == level) {
                            target.hurt(player.damageSources().playerAttack(player), damage);
                            target.invulnerableTime = 0;
                            serverLevel.sendParticles(
                                    ParticleTypes.SWEEP_ATTACK,
                                    target.getX(), target.getY() + target.getEyeHeight() * 0.5, target.getZ(),
                                    1, 0, 0, 0, 0
                            );
                        }
                    }
                    if (index == 4) {
                        executor.shutdown();
                        isPerformingUltimate = false;
                        // 移除提示
                    }
                });
            }, delay, TimeUnit.MILLISECONDS);
        }

        executor.schedule(() -> {
            if (!executor.isShutdown()) {
                executor.shutdown();
                isPerformingUltimate = false;
            }
        }, 1200, TimeUnit.MILLISECONDS);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        int fever = getFever(stack);
        tooltip.add(Component.translatable("tooltip.tinkersnewlife.war_scythe.fever", fever, 100));
        if (fever == 100) {
            tooltip.add(Component.translatable("tooltip.tinkersnewlife.war_scythe.ultimate"));
        }
    }
}