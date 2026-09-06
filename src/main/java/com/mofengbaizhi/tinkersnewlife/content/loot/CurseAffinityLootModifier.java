package com.mofengbaizhi.tinkersnewlife.content.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 咒力亲和战利品注入（+ 物品实体兜底注入）
 * <p>
 * 任意模组的饰品（curios 物品）生成时（战利品/掉落物实体）都有概率随机携带
 * 0~50 点的咒力亲和（NBT: {@code tinkersnewlife.curse_affinity}）。
 * 玩家佩戴这些饰品时，亲和会累加到玩家咒力亲和上。
 * <p>
 * 通道：
 * 1. 全局战利品 modifier（覆盖所有走 LootTable 的生成：箱子/实体/方块）；
 * 2. 兜底：ItemEntity 加入世界时补注入（覆盖不走全局 loot 的其它模组生成流程）。
 * 已带亲和键的物品跳过，不会二次掷骰。
 */
public class CurseAffinityLootModifier extends LootModifier {

    /** 注入概率 */
    public static final float CHANCE = 0.4F;
    /** 亲和上限 */
    public static final int MAX_AFFINITY = 50;

    private final float chance;

    public static final Supplier<Codec<CurseAffinityLootModifier>> CODEC = Suppliers.memoize(() ->
            RecordCodecBuilder.create(inst ->
                    codecStart(inst).and(
                            Codec.FLOAT.fieldOf("chance").orElse(CHANCE).forGetter(m -> m.chance)
                    ).apply(inst, CurseAffinityLootModifier::new)
            )
    );

    public CurseAffinityLootModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = chance;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        for (ItemStack stack : generatedLoot) {
            if (stack.isEmpty()) continue;
            if (stack.getTag() != null && stack.getTag().contains(CursePowerHelper.KEY_AFFINITY)) continue;
            if (!isCurioItem(stack, context.getLevel())) continue;
            if (context.getRandom().nextFloat() >= chance) continue;
            int affinity = context.getRandom().nextInt(MAX_AFFINITY + 1); // 0~50
            if (affinity > 0) {
                CursePowerHelper.setCurseAffinity(stack, affinity);
            }
        }
        return generatedLoot;
    }

    /**
     * 判定是否为饰品：只要能佩戴进 curios 饰品栏（curios 槽位）就算饰品。
     * 覆盖三类途径：
     * 1. 物品具有 curios 物品能力（ICurioItem 或通过 CuriosApi.registerCurio 注册）
     * 2. 能放入任意已注册的 curios 槽位（标签/槽位校验器判定，如 curios:ring 标签、自定义校验器）
     * 3. 兜底：带有任意 curios 命名空间物品标签
     */
    public static boolean isCurioItem(ItemStack stack, @Nullable Level level) {
        boolean hasCurio = false;
        boolean hasSlots = false;
        boolean hasTag = false;
        try {
            if (CuriosApi.getCurio(stack).isPresent()) hasCurio = true;
            if (!hasCurio && level != null) {
                Map<String, ISlotType> slots = CuriosApi.getItemStackSlots(stack, level);
                hasSlots = !slots.isEmpty();
            }
            if (!hasCurio && !hasSlots) {
                for (TagKey<Item> tag : stack.getTags().toList()) {
                    if (tag.location().getNamespace().equals("curios")) {
                        hasTag = true;
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            // Curios 判定异常（如 level 为空/槽数据未就绪）时退回纯标签判定
            hasCurio = false;
            hasSlots = false;
            for (TagKey<Item> tag : stack.getTags().toList()) {
                if (tag.location().getNamespace().equals("curios")) {
                    hasTag = true;
                    break;
                }
            }
        }
        boolean result = hasCurio || hasSlots || hasTag;
        logOnce(stack, hasCurio, hasSlots, hasTag, result);
        return result;
    }

    /** 每种物品首次遇到时打一次诊断日志，便于排查注入链路 */
    private static final Set<String> LOGGED = new HashSet<>();

    private static void logOnce(ItemStack stack, boolean curio, boolean slots, boolean tag, boolean result) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || id.getNamespace().equals(TinkersNewlife.MOD_ID)) return;
        if (!LOGGED.add(id.toString())) return;
        boolean hasKey = stack.getTag() != null && stack.getTag().contains(CursePowerHelper.KEY_AFFINITY);
        TinkersNewlife.LOGGER.info("[亲和注入] {} isCurioItem={} (curio={} slots={} curiosTag={}) 已带亲和键={}",
                id, result, curio, slots, tag, hasKey);
    }

    /** 兜底通道：物品实体加入世界（服务端）时补注入——覆盖不走全局 loot 的模组生成流程 */
    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class CurseAffinityEvents {

        @SubscribeEvent
        public static void onItemEntityJoin(EntityJoinLevelEvent event) {
            if (event.getLevel().isClientSide) return;
            if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) return;
            if (stack.getTag() != null && stack.getTag().contains(CursePowerHelper.KEY_AFFINITY)) return;
            if (!isCurioItem(stack, event.getLevel())) return;
            if (event.getLevel().getRandom().nextFloat() >= CHANCE) return;
            int affinity = event.getLevel().getRandom().nextInt(MAX_AFFINITY + 1);
            if (affinity > 0) {
                CursePowerHelper.setCurseAffinity(stack, affinity);
                itemEntity.setItem(stack);
            }
        }
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
