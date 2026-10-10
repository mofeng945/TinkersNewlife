package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.UUID;

/**
 * §1294 咒力亲和：注册成**真正的玩家属性**（模仿神化的"生成时带词条"）。
 *
 * <p>要点：
 * <ul>
 *   <li>属性 {@code tinkersnewlife:curse_affinity}，范围 -1024~1024，默认 0，可同步；</li>
 *   <li>饰品的亲和存在物品的 {@code AttributeModifiers} 里（原版格式 ⇒ 属性面板/提示都能显示）；</li>
 *   <li>旧存档里存在自定义 NBT {@code tinkersnewlife.curse_affinity} 的物品，登录/刷新时**一次性迁移**成属性修饰符；</li>
 *   <li>饰品槽不会自动应用物品属性 ⇒ 每 20 tick 扫一次 Curios，把已装备饰品的本属性修饰符同步到玩家身上。</li>
 * </ul>
 */
public final class CurseAffinityAttribute {

    public static final String NAME = "curse_affinity";
    public static final String LEGACY_KEY = CursePowerHelper.KEY_AFFINITY; // tinkersnewlife.curse_affinity

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, TinkersNewlife.MOD_ID);

    public static final RegistryObject<Attribute> CURSE_AFFINITY = ATTRIBUTES.register(NAME,
            () -> new RangedAttribute("attribute.tinkersnewlife." + NAME, 0.0D, -1024.0D, 1024.0D).setSyncable(true));

    /** 物品修饰符统一用这个名字前缀 ⇒ 同步时能安全地"先清后加" */
    public static final String MOD_NAME = "tinkersnewlife:" + NAME;

    private CurseAffinityAttribute() {
    }

    /* ------------------------------------------------------------------ 注册 */

    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void onAttributeModification(EntityAttributeModificationEvent event) {
            event.add(EntityType.PLAYER, CURSE_AFFINITY.get());
        }
    }

    /* ------------------------------------------------------------------ 物品侧 */

    /**
     * §1295 用户口径：亲和**不**写进原版 `AttributeModifiers`（那样不写 Slot 会在
     * "主手/副手/头盔/胸甲/护腿/靴子"六行里各刷一次），而是写我们自己的 NBT 键，
     * 由 {@link CurseAffinityTooltip} 只显示**一行**；玩家的属性值由 20 tick 镜像承担。
     * 顺手清掉老版本写进 AttributeModifiers 的条目（迁移）。
     */
    public static void setItemAffinity(ItemStack stack, double amount) {
        try {
            CompoundTag root = stack.getOrCreateTag();
            stripVanillaModifier(root);
            root.putInt(LEGACY_KEY, (int) Math.round(amount));
        } catch (Throwable ignored) {
        }
    }

    /** 清掉老版本写进原版 AttributeModifiers 的"我们的"那一条 */
    public static void stripVanillaModifier(CompoundTag root) {
        try {
            if (root == null || !root.contains("AttributeModifiers", 9)) {
                return;
            }
            ListTag list = root.getList("AttributeModifiers", 10);
            ListTag out = new ListTag();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                if (!MOD_NAME.equals(e.getString("Name"))) {
                    out.add(e);
                }
            }
            if (out.isEmpty()) {
                root.remove("AttributeModifiers");
            } else {
                root.put("AttributeModifiers", out);
            }
        } catch (Throwable ignored) {
        }
    }
    /** 固定 UUID（★所有物品共用同一个 ✓ 因为每件物品只可能有一条本属性词条 ✓） */
    public static final UUID ITEM_UUID = UUID.fromString("6d1f7e30-9c4b-4a1e-9b6d-0f2a7c5e8b41");

    /** 把旧 NBT 亲和迁移成属性修饰符（★幂等：迁完就删旧键 ✓） */
    public static void migrate(ItemStack stack) {
        try {
            if (stack.isEmpty()) {
                return;
            }
            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.contains(LEGACY_KEY)) {
                return;
            }
            int legacy = tag.getInt(LEGACY_KEY);
            tag.remove(LEGACY_KEY);
            if (legacy != 0) {
                stack.getOrCreateTag().putInt(LEGACY_KEY, legacy);
            }
            stripVanillaModifier(tag);
        } catch (Throwable ignored) {
        }
    }

    /** §1296 每 tick 清理：把老版本写进原版 AttributeModifiers 的那一条删掉（★六行就是这么来的 ✓） */
    public static void stripNow(ItemStack stack) {
        try {
            if (stack.isEmpty()) {
                return;
            }
            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.contains("AttributeModifiers", 9)) {
                return;
            }
            stripVanillaModifier(tag);
        } catch (Throwable ignored) {
        }
    }
    /** 读物品上的亲和（★从 AttributeModifiers 里读 ✓ 迁移前也能读旧键 ✓） */
    public static int getItemAffinity(ItemStack stack) {
        try {
            CompoundTag tag = stack.getTag();
            if (tag == null) {
                return 0;
            }
            if (tag.contains(LEGACY_KEY)) {
                return tag.getInt(LEGACY_KEY);
            }
            if (!tag.contains("AttributeModifiers", 9)) {
                return 0;
            }
            ListTag list = tag.getList("AttributeModifiers", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                if (MOD_NAME.equals(e.getString("Name"))) {
                    return (int) Math.round(e.getDouble("Amount"));
                }
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /* ------------------------------------------------------------------ 玩家侧 */

    /** 每 20 tick：迁移背包物品 ＋ 把已装备饰品的修饰符同步到玩家属性 */
    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Runtime {
        private Runtime() {
        }

        @SubscribeEvent
        public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Player player = event.player;
            if (!(player instanceof ServerPlayer sp)) {
                return;
            }
            boolean deep = sp.tickCount % 20 == 0;   // §1296 属性同步每 20 tick；清理每 tick（★让老 NBT 立刻消失 ✓）
            try {
                // ① 背包（含饰品栏）里的旧 NBT 一次性迁移
                for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                    stripNow(sp.getInventory().getItem(i));   // ★每 tick 都清 ✓
                }
                // ② 收集"当前装备的饰品"上的亲和
                int fromCurios = 0;
                var resolved = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(sp).resolve();
                if (resolved.isPresent()) {
                    for (var handler : resolved.get().getCurios().values()) {
                        var stacks = handler.getStacks();
                        for (int i = 0; i < stacks.getSlots(); i++) {
                            ItemStack s = stacks.getStackInSlot(i);
                            if (s.isEmpty()) {
                                continue;
                            }
                            stripNow(s);
                            if (deep) {
                                fromCurios += getItemAffinity(s);
                            }
                        }
                    }
                }
                // ③ 同步到属性：先清掉我们自己加的那些，再加当前这一份
                AttributeInstance inst = sp.getAttribute(CURSE_AFFINITY.get());
                if (inst != null) {
                    for (AttributeModifier m : new java.util.ArrayList<>(inst.getModifiers())) {
                        if (MOD_NAME.equals(m.getName())) {
                            inst.removeModifier(m);
                        }
                    }
                    if (fromCurios != 0) {
                        inst.addTransientModifier(new AttributeModifier(ITEM_UUID, MOD_NAME, fromCurios,
                                AttributeModifier.Operation.ADDITION));
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }
}