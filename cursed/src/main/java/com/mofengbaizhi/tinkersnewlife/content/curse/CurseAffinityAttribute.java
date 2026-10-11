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
     * §1297 用户口径：提示只显示"作为饰品时：+xx 咒力亲和"这两行。
     * 这正是 **Curios 自己的**属性提示格式 ⇒ 所以把亲和写进物品的 `AttributeModifiers`，
     * 并带上 `Slot = "curio"`：
     *   · 原版找不到匹配的装备槽 ⇒ 不再刷"主手/副手/头/胸/腿/靴"六行；
     *   · Curios 认这个伪槽位 ⇒ 按它自己的文案显示"作为饰品时：+xx 咒力亲和"。
     */
    /**
     * §1300 挂原版/Curios 的机制：亲和写成原版 `AttributeModifiers`，`Slot` 必须是
     * **该物品真实的饰品槽 id**（necklace / ring …）。猜成 "curio" 这种伪槽位 Curios 不认，
     * 所以之前什么都不显示；写对槽 id 后，提示由 Curios/原版自己渲染成"作为饰品时：+xx 咒力亲和"。
     */
    public static void setItemAffinity(ItemStack stack, double amount) {
        setItemAffinity(stack, amount, null);
    }

    public static void setItemAffinity(ItemStack stack, double amount, net.minecraft.world.level.Level level) {
        try {
            CompoundTag root = stack.getOrCreateTag();
            stripVanillaModifier(root);
            root.remove(LEGACY_KEY);
            if (amount == 0.0D) {
                return;
            }
            java.util.List<String> slots = curioSlots(stack, level);
            if (slots.isEmpty()) {
                root.putInt(LEGACY_KEY, (int) Math.round(amount));
                return;
            }
            ListTag list = root.contains("AttributeModifiers", 9) ? root.getList("AttributeModifiers", 10) : new ListTag();
            AttributeModifier mod = new AttributeModifier(ITEM_UUID, MOD_NAME, amount, AttributeModifier.Operation.ADDITION);
            CompoundTag e = mod.save();
            e.putString("AttributeName", ForgeRegistries.ATTRIBUTES.getKey(CURSE_AFFINITY.get()).toString());
            e.putString("Slot", slots.get(0));
            list.add(e);
            root.put("AttributeModifiers", list);
        } catch (Throwable ignored) {
        }
    }

    /** 问 Curios：这件物品能戴进哪些槽（★返回槽 id ✓） */
    public static java.util.List<String> curioSlots(ItemStack stack, net.minecraft.world.level.Level level) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            var slots = top.theillusivec4.curios.api.CuriosApi.getItemStackSlots(stack, level);
            if (slots != null) {
                out.addAll(slots.keySet());
            }
        } catch (Throwable ignored) {
        }
        return out;
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
                boolean ours = MOD_NAME.equals(e.getString("Name"));
                boolean slotless = e.getString("Slot").isEmpty();
                if (!(ours && slotless)) {
                    out.add(e);   // ★保留 Slot=curio 那条 ✓ 只清老版本没写 Slot 的 ✓
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

    /** §1299 "已掷过骰"标记：★同一件物品只掷一次 ⇒ 丢地上再捡不会变异 ✓ 而创造刷出来的也会掷 ✓ */
    public static final String MARKER_ROLLED = "tnl_affinity_rolled";

    /** 首次见到某件饰品时掷一次骰（40% ⇒ 0~50） */
    public static void rollIfNew(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        try {
            if (stack.isEmpty()) {
                return;
            }
            /*
             * §1367 ★★★致命修正：**绝不能在门槛之前 getOrCreateTag()** ✗✗
             *   原写法：`CompoundTag tag = stack.getOrCreateTag();` 写在「是不是饰品」判定**之前** ✗
             *   ⇒ ★**背包里每一件物品（包括钓上来的鱼）都被写上一个空标签 `{}`** ✗✗
             *   ⇒ 原版 `isSameItemSameTags` 比 `Objects.equals(tag, tag)` ⇒ `{}` ≠ `null`
             *   ⇒ ★**同款物品永远合不到一起** ✗（用户实测：钓上来的鱼不堆叠 ✗）
             *   ★现在：只用 `getTag()` **读** ✓；不是饰哈就**一个字节都不写** ✓；落空也不写 ✓
             */
            CompoundTag tag = stack.getTag();   // ★只读，不创建 ✓
            if (tag != null && tag.getBoolean(MARKER_ROLLED)) {
                return;
            }
            if (!com.mofengbaizhi.tinkersnewlife.content.loot.CurseAffinityLootModifier
                    .isCurioItem(stack, player.level())) {
                return;   // ★非饰品：到此为止，**不碰 NBT** ✓
            }
            if (player.level().getRandom().nextFloat() >= 0.4F) {
                return;   // ★落空也不写标记 ✓（★与 §1365 同一口径 ✓）
            }
            int affinity = player.level().getRandom().nextInt(51);
            if (affinity > 0) {
                setItemAffinity(stack, affinity, player.level());
                stack.getOrCreateTag().putBoolean(MARKER_ROLLED, true);   // ★只有真中且 >0 才落标记 ✓
            }
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
                    rollIfNew(sp, sp.getInventory().getItem(i));   // ★首次见到就掷一次 ✓
                    stripNow(sp.getInventory().getItem(i));        // ★每 tick 清掉老的原版条目 ✓
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
                            rollIfNew(sp, s);
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