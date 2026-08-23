package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.modifier.util.ArmorModifierHelper;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nullable;
import java.util.*;

public class DreadsteelArmorTrait extends Modifier implements TooltipModifierHook {

    private static final Logger LOGGER = LoggerFactory.getLogger(DreadsteelArmorTrait.class);

    // ======================== 等级依赖参数 ========================
    private static final int COOLDOWN_BASE = 120;
    private static final int COOLDOWN_REDUCTION_PER_LEVEL = 20;

    private static final double RANGE_BASE = 16.0;
    private static final double RANGE_PER_LEVEL = 2.0;

    private static final int DURATION_SECONDS = 5;
    private static final int JUDGEMENT_COUNT_BASE = 5;
    private static final int JUDGEMENTS_PER_LEVEL = 1;
    private static final int BRIGHTNESS_THRESHOLD = 8;
    private static final double DAMAGE_MULTIPLIER_BASE = 5.0;
    private static final double DAMAGE_MULTIPLIER_PER_LEVEL = 0.5;
    private static final int MIN_SUCCESS = 30;
    private static final int MAX_SUCCESS = 90;
    private static final int XP_COST = 5;
    private static final int PASSIVE_EFFECT_DURATION = 220;

    private static final ResourceLocation KEY_DATA = new ResourceLocation(TinkersNewlife.MOD_ID, "dreadsteel_armor_data");
    private static final String KEY_COOLDOWN_END = "cooldown_end";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_ACTIVE_START = "active_start";
    private static final String KEY_JUDGEMENTS_LEFT = "judgements_left";
    private static final String KEY_DISQUALIFIED = "disqualified";

    private static final ModifierId DREADSTEEL_ARMOR_ID = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "dreadsteel_armor"));

    private static final Map<UUID, Long> LAST_MESSAGE_TIME = new HashMap<>();

    // ======================== 等级计算工具方法 ========================

    private static int getTotalLevel(Player player) {
        return ArmorModifierHelper.getTotalModifierLevelOnArmor(player, "dreadsteel_armor");
    }

    private static int getCooldownSeconds(int level) {
        if (level <= 0) level = 1;
        return Math.max(0, COOLDOWN_BASE - (level - 1) * COOLDOWN_REDUCTION_PER_LEVEL);
    }

    private static double getRange(int level) {
        if (level <= 0) level = 1;
        return RANGE_BASE + (level - 1) * RANGE_PER_LEVEL;
    }

    private static int getJudgementCount(int level) {
        if (level <= 0) level = 1;
        return JUDGEMENT_COUNT_BASE + (level - 1) * JUDGEMENTS_PER_LEVEL;
    }

    private static double getDamageMultiplier(int level) {
        if (level <= 0) level = 1;
        return DAMAGE_MULTIPLIER_BASE + (level - 1) * DAMAGE_MULTIPLIER_PER_LEVEL;
    }

    // ======================== 数据管理 ========================

    private static CompoundTag getData(IToolStackView tool) {
        var data = tool.getPersistentData();
        if (!data.contains(KEY_DATA)) {
            CompoundTag tag = new CompoundTag();
            data.put(KEY_DATA, tag);
            return tag;
        }
        return data.getCompound(KEY_DATA);
    }

    private static long getCooldownEnd(IToolStackView tool) {
        return getData(tool).getLong(KEY_COOLDOWN_END);
    }

    private static void setCooldownEnd(IToolStackView tool, long end) {
        getData(tool).putLong(KEY_COOLDOWN_END, end);
    }

    private static boolean isActive(IToolStackView tool) {
        return getData(tool).getBoolean(KEY_ACTIVE);
    }

    private static void setActive(IToolStackView tool, boolean active) {
        CompoundTag data = getData(tool);
        data.putBoolean(KEY_ACTIVE, active);
        if (!active) {
            data.remove(KEY_ACTIVE_START);
            data.remove(KEY_JUDGEMENTS_LEFT);
            data.remove(KEY_DISQUALIFIED);
        }
    }

    private static void startSkill(IToolStackView tool, long gameTime, int judgementCount) {
        CompoundTag data = getData(tool);
        data.putBoolean(KEY_ACTIVE, true);
        data.putLong(KEY_ACTIVE_START, gameTime);
        data.putInt(KEY_JUDGEMENTS_LEFT, judgementCount);
        data.putString(KEY_DISQUALIFIED, "");
    }

    private static int getJudgementsLeft(IToolStackView tool) {
        return getData(tool).getInt(KEY_JUDGEMENTS_LEFT);
    }

    private static void decrementJudgements(IToolStackView tool) {
        int left = getJudgementsLeft(tool);
        if (left > 0) {
            getData(tool).putInt(KEY_JUDGEMENTS_LEFT, left - 1);
        }
    }

    private static Set<UUID> getDisqualified(IToolStackView tool) {
        String str = getData(tool).getString(KEY_DISQUALIFIED);
        if (str.isEmpty()) return new HashSet<>();
        Set<UUID> set = new HashSet<>();
        for (String s : str.split(",")) {
            try { set.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
        return set;
    }

    private static void addDisqualified(IToolStackView tool, UUID uuid) {
        Set<UUID> set = getDisqualified(tool);
        set.add(uuid);
        StringBuilder sb = new StringBuilder();
        for (UUID id : set) {
            if (sb.length() > 0) sb.append(",");
            sb.append(id.toString());
        }
        getData(tool).putString(KEY_DISQUALIFIED, sb.toString());
    }

    // ======================== 钩子 ========================

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        if (player == null) return;
        long cooldownEnd = getCooldownEnd(tool);
        long currentTime = player.level().getGameTime();
        if (cooldownEnd > currentTime) {
            long remaining = (cooldownEnd - currentTime) / 20 + 1;
            tooltip.add(Component.translatable("modifier.tinkersnewlife.dreadsteel_armor.cooldown", remaining));
        } else {
            tooltip.add(Component.translatable("modifier.tinkersnewlife.dreadsteel_armor.ready"));
        }
    }

    // ======================== 事件处理器 ========================

    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
    public static class Handler {

        @SubscribeEvent
        public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.START) return;
            Player player = event.player;
            if (player.level().isClientSide) return;

            int level = getTotalLevel(player);
            if (level <= 0) return;

            // 被动效果
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, PASSIVE_EFFECT_DURATION, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, PASSIVE_EFFECT_DURATION, 0, false, false, true));
            if (ModEffects.DAMAGE_LIMIT.get() != null) {
                player.addEffect(new MobEffectInstance(ModEffects.DAMAGE_LIMIT.get(), PASSIVE_EFFECT_DURATION, 0, false, false, true));
            }

            if (player.getTicksFrozen() > 0) {
                player.setTicksFrozen(0);
            }

            boolean skillRequested = player.getPersistentData().getBoolean("dreadsteel_skill_request");
            player.getPersistentData().remove("dreadsteel_skill_request");
            if (!skillRequested) return;

            if (player.experienceLevel < XP_COST) {
                player.displayClientMessage(
                        Component.translatable("modifier.tinkersnewlife.dreadsteel_armor.no_xp", XP_COST), true);
                return;
            }

            long currentTime = player.level().getGameTime();
            boolean canTrigger = false;
            long cooldownEndForAll = 0;

            for (var stack : player.getArmorSlots()) {
                if (stack.isEmpty()) continue;
                // ✅ 使用 ToolHelper 安全获取
                ToolStack tool = ToolHelper.getToolStack(stack);
                if (tool == null) continue;
                if (tool.getModifierLevel(DREADSTEEL_ARMOR_ID) <= 0) continue;
                long cooldownEnd = getCooldownEnd(tool);
                if (cooldownEnd <= currentTime) {
                    canTrigger = true;
                    break;
                }
                if (cooldownEnd > cooldownEndForAll) cooldownEndForAll = cooldownEnd;
            }

            if (!canTrigger) {
                long remaining = (cooldownEndForAll - currentTime) / 20 + 1;
                player.displayClientMessage(
                        Component.translatable("modifier.tinkersnewlife.dreadsteel_armor.on_cooldown", remaining), true);
                return;
            }

            int judgementCount = getJudgementCount(level);

            for (var stack : player.getArmorSlots()) {
                if (stack.isEmpty()) continue;
                // ✅ 使用 ToolHelper 安全获取
                ToolStack tool = ToolHelper.getToolStack(stack);
                if (tool == null) continue;
                if (tool.getModifierLevel(DREADSTEEL_ARMOR_ID) <= 0) continue;
                player.giveExperienceLevels(-XP_COST);
                long cooldownEnd = currentTime + (long) getCooldownSeconds(level) * 20;
                setCooldownEnd(tool, cooldownEnd);
                startSkill(tool, currentTime, judgementCount);
            }

            player.displayClientMessage(
                    Component.translatable("modifier.tinkersnewlife.dreadsteel_armor.activated"), true);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WARDEN_ANGRY, SoundSource.PLAYERS, 1.0f, 0.5f);
        }

        @SubscribeEvent
        public static void onPlayerTickEnd(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Player player = event.player;
            if (player.level().isClientSide) return;

            int level = getTotalLevel(player);
            if (level <= 0) return;

            IToolStackView activeTool = null;
            for (var stack : player.getArmorSlots()) {
                if (stack.isEmpty()) continue;
                // ✅ 使用 ToolHelper 安全获取
                ToolStack tool = ToolHelper.getToolStack(stack);
                if (tool == null) continue;
                if (tool.getModifierLevel(DREADSTEEL_ARMOR_ID) <= 0) continue;
                if (isActive(tool)) {
                    activeTool = tool;
                    break;
                }
            }
            if (activeTool == null) return;

            long currentTime = player.level().getGameTime();
            long startTime = getData(activeTool).getLong(KEY_ACTIVE_START);
            int elapsed = (int)(currentTime - startTime);
            int totalDuration = DURATION_SECONDS * 20;
            int remainingSeconds = Math.max(0, (totalDuration - elapsed) / 20 + 1);

            UUID playerId = player.getUUID();
            long lastMsgTime = LAST_MESSAGE_TIME.getOrDefault(playerId, 0L);
            if (currentTime - lastMsgTime > 10 && remainingSeconds > 0) {
                float progress = 1.0f - (float) elapsed / totalDuration;
                String color = progress > 0.5 ? "§d" : "§c";
                player.displayClientMessage(
                        Component.literal(color + "◆ 悚域展开 §7" + remainingSeconds + "s"), true);
                LAST_MESSAGE_TIME.put(playerId, currentTime);
            }

            double range = getRange(level);
            spawnDreadParticles(player, range);

            if (elapsed > totalDuration) {
                setActive(activeTool, false);
                player.displayClientMessage(
                        Component.translatable("modifier.tinkersnewlife.dreadsteel_armor.ended"), true);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.WARDEN_ANGRY, SoundSource.PLAYERS, 0.6f, 0.3f);
                LAST_MESSAGE_TIME.remove(playerId);
                return;
            }

            int judgementsLeft = getJudgementsLeft(activeTool);
            if (judgementsLeft <= 0) {
                setActive(activeTool, false);
                LAST_MESSAGE_TIME.remove(playerId);
                return;
            }

            if (elapsed % 20 != 0) return;
            performJudgement(player, activeTool, range, level);
            decrementJudgements(activeTool);
        }

        private static void spawnDreadParticles(Player player, double range) {
            if (!(player.level() instanceof ServerLevel serverLevel)) return;
            RandomSource random = serverLevel.random;

            // ✅ 使用 ParticleTypes.SOUL_FIRE_FLAME 等
            for (int i = 0; i < 12; i++) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double radius = range * random.nextDouble() * 0.8;
                double x = player.getX() + radius * Math.cos(angle);
                double z = player.getZ() + radius * Math.sin(angle);
                double y = player.getY() + random.nextDouble() * 2 - 1;
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        x, y, z, 1, 0, 0.02, 0, 0);
            }
            for (int i = 0; i < 20; i++) {
                double x = player.getX() + (random.nextDouble() - 0.5) * range * 1.5;
                double z = player.getZ() + (random.nextDouble() - 0.5) * range * 1.5;
                double y = player.getY() + random.nextDouble() * 3 - 1;
                serverLevel.sendParticles(ParticleTypes.SOUL,
                        x, y, z, 1,
                        (random.nextDouble() - 0.5) * 0.05,
                        random.nextDouble() * 0.03,
                        (random.nextDouble() - 0.5) * 0.05, 0);
            }
            for (int i = 0; i < 20; i++) {
                double angle = i * Math.PI / 10;
                double radius = 2.0;
                double x = player.getX() + radius * Math.cos(angle + random.nextDouble() * 0.2);
                double z = player.getZ() + radius * Math.sin(angle + random.nextDouble() * 0.2);
                double y = player.getY() + 0.1;
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        x, y, z, 1, 0, 0.01, 0, 0);
            }
            for (int i = 0; i < 10; i++) {
                double x = player.getX() + (random.nextDouble() - 0.5) * range;
                double z = player.getZ() + (random.nextDouble() - 0.5) * range;
                double y = player.getY() + random.nextDouble() * 4;
                serverLevel.sendParticles(ParticleTypes.WHITE_ASH,
                        x, y, z, 1,
                        (random.nextDouble() - 0.5) * 0.02,
                        -0.01,
                        (random.nextDouble() - 0.5) * 0.02, 0);
            }
        }

        private static void performJudgement(Player player, IToolStackView tool, double range, int level) {
            Level levelObj = player.level();
            BlockPos center = player.blockPosition();
            AABB area = new AABB(center).inflate(range);
            List<LivingEntity> entities = levelObj.getEntitiesOfClass(LivingEntity.class, area,
                    e -> e != player && e.isAlive());

            Set<UUID> disqualified = getDisqualified(tool);
            int successCount = 0;
            double damageMultiplier = getDamageMultiplier(level);

            for (LivingEntity target : entities) {
                UUID id = target.getUUID();
                if (disqualified.contains(id)) continue;

                BlockPos pos = target.blockPosition();
                int light = levelObj.getBrightness(LightLayer.BLOCK, pos);
                if (light >= BRIGHTNESS_THRESHOLD) {
                    addDisqualified(tool, id);
                    continue;
                }

                int roll = levelObj.random.nextInt(100) + 1;
                if (roll >= MIN_SUCCESS && roll <= MAX_SUCCESS) {
                    float baseDamage = (float) player.getAttribute(Attributes.ATTACK_DAMAGE).getValue();
                    float damage = (float) (baseDamage * damageMultiplier);
                    target.hurt(target.damageSources().magic(), damage);
                    successCount++;

                    if (levelObj instanceof ServerLevel serverLevel) {
                        for (int i = 0; i < 15; i++) {
                            double dx = (levelObj.random.nextDouble() - 0.5) * 2.0;
                            double dy = (levelObj.random.nextDouble() - 0.5) * 2.0;
                            double dz = (levelObj.random.nextDouble() - 0.5) * 2.0;
                            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                                    target.getX() + dx, target.getY() + dy, target.getZ() + dz,
                                    1, 0, 0.01, 0, 0);
                        }
                        serverLevel.sendParticles(ParticleTypes.FLASH,
                                target.getX(), target.getY() + 0.5, target.getZ(),
                                1, 0, 0, 0, 0);
                    }
                }
            }

            if (successCount > 0 && LOGGER.isInfoEnabled()) {
                LOGGER.info("§d[悚域展开] 本次判定命中了 {} 个目标", successCount);
            }
        }
    }
}