package com.mofengbaizhi.tinkersnewlife.content.modifier.util;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ArmorModifierHelper {

    /** 缓存已解析的 ModifierId，避免每次调用都 new 一个 ResourceLocation */
    private static final Map<String, ModifierId> ID_CACHE = new ConcurrentHashMap<>();

    /** 被动效果时长：12 秒 = 240 tick */
    private static final int PASSIVE_EFFECT_DURATION = 12 * 20;
    /** 刷新阈值：剩余时长低于 11 秒（220 tick）时重新施加，避免效果图标闪烁 */
    private static final int PASSIVE_REFRESH_THRESHOLD = 11 * 20;
    /** 检查间隔：每 1 秒（20 tick）检查一次 */
    private static final int PASSIVE_CHECK_INTERVAL = 20;

    private static ModifierId getModifierId(String modifierId) {
        return ID_CACHE.computeIfAbsent(modifierId, id -> {
            String fullId = id.contains(":") ? id : TinkersNewlife.MOD_ID + ":" + id;
            return new ModifierId(new ResourceLocation(fullId));
        });
    }

    /**
     * 给实体施加持续的被动效果（盔甲特性用）。
     * <p>
     * 规格：每 1 秒检查一次；效果缺失或剩余时长 &lt; 11 秒时，重新施加 12 秒的效果。
     * 这样效果时长始终维持在 11~12 秒之间，不会出现低于 11 秒导致的图标闪烁。
     * <p>
     * 注意：必须在服务端调用（调用方负责 isClientSide 守卫）。
     *
     * @param entity    目标实体
     * @param effect    要维持的效果
     * @param amplifier 效果等级（0 = 1 级）
     */
    public static void addPassiveEffect(LivingEntity entity, MobEffect effect, int amplifier) {
        MobEffectInstance current = entity.getEffect(effect);
        // 效果缺失时立即补上（避免穿戴瞬间无效果）；已有效果则按 1 秒间隔检查
        if (current != null) {
            if (entity.tickCount % PASSIVE_CHECK_INTERVAL != 0) {
                return;
            }
            if (current.getDuration() >= PASSIVE_REFRESH_THRESHOLD) {
                return;
            }
        }
        entity.addEffect(new MobEffectInstance(effect, PASSIVE_EFFECT_DURATION, amplifier, false, false, true));
    }

    /**
     * 检查实体穿戴的盔甲上是否有指定修饰符
     * @param entity 实体
     * @param modifierId 修饰符ID（可带命名空间，如 "tinkersnewlife:dragonsteel_fire_armor" 或简写 "dragonsteel_fire_armor"）
     */
    public static boolean hasModifierOnArmor(LivingEntity entity, String modifierId) {
        ModifierId id = getModifierId(modifierId);

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmor()) continue;
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            // ✅ 使用 ToolHelper 安全获取（非匠魂工具返回 null，避免 "non-modifiable tool" 警告）
            ToolStack tool = ToolHelper.getToolStack(stack);
            if (tool == null) continue;
            if (tool.getModifierLevel(id) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取实体穿戴的盔甲上指定修饰符的总等级
     * @param entity 实体
     * @param modifierId 修饰符ID
     * @return 总等级
     */
    public static int getTotalModifierLevelOnArmor(LivingEntity entity, String modifierId) {
        ModifierId id = getModifierId(modifierId);

        int total = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmor()) continue;
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            // ✅ 使用 ToolHelper 安全获取
            ToolStack tool = ToolHelper.getToolStack(stack);
            if (tool == null) continue;
            total += tool.getModifierLevel(id);
        }
        return total;
    }
}
