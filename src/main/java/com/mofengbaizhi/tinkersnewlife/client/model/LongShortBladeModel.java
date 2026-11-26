package com.mofengbaizhi.tinkersnewlife.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.BakedModelWrapper;

import javax.annotation.Nullable;

/**
 * ⭐ §1146／§1154 <b>长短刃：按「形态 × 场景」切模型</b>（用户口径 ✓）。
 *
 * <h2>⭐⭐⭐ 两个钩子的**调用顺序**决定了分工（⚠ 我第一版搞反了 ✗）</h2>
 * ⭐ 原版 {@code ItemRenderer} 的顺序是：
 * <pre>
 *   model = shaper.getItemModel(stack);                                  // ① 顶层模型
 *   model = model.getOverrides().resolve(model, stack, level, entity, s); // ② ⭐ **先** resolve（有 stack ✓）
 *   model = model.applyTransform(ctx, pose, leftHand);                    // ③ ⭐ **后** transform（有场景 ✓）
 *   … 用 ③ 返回的模型渲染 …
 * </pre>
 * ⇒ ⚠⚠ **`applyTransform` 拿不到物品栈** ✗（⭐ ③ 已经离 ② 很远了 ✓）
 * ⇒ ⭐ **形态（长／短）必须在 ② `resolve` 里定** ✓
 * ⇒ ⭐ **场景（物品栏／手持）必须在 ③ `applyTransform` 里定** ✓
 * <p>⚠ 我第一版**反了** ✗：⭐ 在 `applyTransform` 里挑形态 ✗ ⇒ ⭐ 它拿不到 stack ✗
 * ⇒ ⭐ 只能固定用长刀那一侧 ⇒ ⭐ **长短刀都会显示长刀那套** ✗ ✓（⭐ 已重写 ✓）。
 *
 * <h2>⭐ 于是分两级（级别含义与第一版相反 ✓）</h2>
 * <ol>
 *   <li>⭐ 第 1 级（本类 ✓）：⭐ 唯一入口 ✓ ⇒ {@link #getOverrides()} 的 `resolve` **读 stack 定形态** ✗
 *       ⇒ ⭐ 返回第 2 级 ✓；</li>
 *   <li>⭐ 第 2 级（{@link ContextPickModel} ✓）：⭐ **已经知道形态** ✓ ⇒ 只剩"物品栏／手持"两选一 ✓
 *       ⇒ 它的 `overrides` 返回**空的**（⭐ 别让原版再解析一轮 ✗ 否则会循环 ✓）。</li>
 * </ol>
 *
 * <h2>⚠ 为什么第 2 级的 overrides 要给一个空实现</h2>
 * ⭐ 原版拿 ③ 的返回值后**还会再调一次** `getOverrides().resolve(...)` ✓（⭐ `ItemRenderer` 内部 ✓）
 * ⇒ ⚠ 若第 2 级的 `resolve` 又返回"需要形态判断"的模型 ✗ ⇒ ⭐ **形态信息已经丢了** ✗
 * ⇒ ⭐ 会给错 ✓（⭐ 甚至无限套娃 ✓）⇒ ⭐ 返回**默认实现**（⭐ 原样返回传入的模型 ✓）✓。
 */
public class LongShortBladeModel extends BakedModelWrapper<BakedModel> {

    /** ⭐ 物品栏·长刀 ✓ */
    private final BakedModel guiLong;
    /** ⭐ 手持·长刀 ✓ */
    private final BakedModel heldLong;
    /** ⭐ 短刀（物品栏与手持**共用** ✓ 用户口径 ✓） */
    private final BakedModel shortSide;
    /** ⭐ 物品栏·**未手持**（⭐ 还没配成一对时 ✓ 用户成品图「物品栏-未手持」✓） */
    private final BakedModel idle;

    public LongShortBladeModel(BakedModel fallback,
                               BakedModel idle,
                               BakedModel guiLong, BakedModel shortSide, BakedModel heldLong) {
        super(fallback);
        // ⚠ 缺哪个就回退到哪一个 ✗ —— ⭐ 用户可以只画一部分 ✓（⭐ 没画的沿用默认 ✓ 不崩 ✓）
        BakedModel def = fallback;
        this.idle = idle != null ? idle : def;
        this.guiLong = guiLong != null ? guiLong : this.idle;
        this.heldLong = heldLong != null ? heldLong : this.guiLong;
        this.shortSide = shortSide != null ? shortSide : this.guiLong;
    }

    /** ⭐ 这四个"拿在手里"的场景才用 held ✓ 其它（GUI／展示框／掉落／头顶…）一律用 gui ✓ */
    static boolean heldContext(ItemDisplayContext ctx) {
        return ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    @Override
    public ItemOverrides getOverrides() {
        return new PickerOverrides(this);
    }

    /** ⭐ 读 {@code lnb_form} ✓（⚠ 复用物品类里的判断 ✓ 免得两边各写一个字符串 ✗） */
    private static boolean isLongForm(ItemStack stack) {        try {
            return com.mofengbaizhi.tinkersnewlife.content.item.LongShortBladeItem.isLong(stack);
        } catch (Throwable ignored) {
            // ⚠ 读不到就当长刀 ✓（⭐ 与 `LongShortBladeItem` 里的默认值一致 ✓）
            return true;
        }
    }

    /** ⭐ 第 1 级：⭐ 这里**有 stack** ✓ ⇒ ⭐ **定形态** ✗ ⇒ 交给第 2 级去定场景 ✓ */
    private static final class PickerOverrides extends ItemOverrides {

        private final LongShortBladeModel parent;

        PickerOverrides(LongShortBladeModel parent) {
            this.parent = parent;
        }

        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                  @Nullable LivingEntity entity, int seed) {
            // ⭐⭐ **未手持 ⇒ 物品栏·未手持那一套**（⭐ 用户口径 2026-10-09：
            //   「**没配对指的是没有拿在手里吧**」✓ —— ⚠ 我上一版按 `lnb_pair`（配成对）理解**错了** ✗）
            //   ⇒ ⭐ 判据改为 ⭐ **客户端玩家的主手/副手是不是"这一把栈"** ✗
            //     （⭐ 同一个实例比较 ✓ 不用 `matches` ✓ 免得被 NBT 差异骗到 ✓）
            //   ⚠ 只能在**客户端**用 `Minecraft` ✗ —— 本类在 `client/model/` 下 ✓ 安全 ✓。
            // ⭐⭐ **关键一步：把选中的模型再过一遍"它自己的 overrides"** ✗
            //   ⭐ 匠魂的按材料上色**不是**烘焙时定死的 ✗ —— ⭐ 它是那张模型的
            //   `getOverrides()`（`MaterialOverrideHandler` ✓）在**拿到物品栈之后**才换上
            //   `<部件>_<真实材料>` 那套贴图 ✓（⭐ 也就是"同一个模型 → 按材料换图" ✓）。
            //   ⚠⚠ 我前面一直**直接返回裸模型** ✗ ⇒ ⭐ 它自己的 overrides **从没被调用** ✗
            //   ⇒ ⭐ 于是永远停在烘焙时那套 `tconstruct:unknown`（⭐ 没有材质颜色 ⇒ 灰白 ✓）
            //   —— ⭐ **这就是"没上色"的真正原因** ✓。
            // ⭐⭐⚠⚠ **判据：优先用 `resolve` 自带的 `entity`** ✗✗
            //   （⭐ 用户实测 2026-10-09 ✓：「**其他玩家看我手持双刀时双手都拿着未手持的物品栏模型**」✓）
            //   ⚠ 根因：⭐ 我原来只看 ⭐ **本地玩家**（`Minecraft.getInstance().player` ✓）
            //   ⇒ ⭐ 在**别人的客户端**上，"本地玩家"是**他自己** ✗ ⇒ ⭐ 他那把刀永远
            //     `isHeldByLocalPlayer == false` ⇒ ⭐ **回退到 `ctx_idle`（未手持那套）** ✓
            //   ⇒ ⭐ 别人看你双手都是"物品栏未手持"模型 ✓ ✓（⭐ 与现象完全一致 ✓）。
            //   ⭐ 修法：⭐ `resolve(...)` **本来就带 `entity`** ✓ ⇒
            //     ⭐ 直接看 ⭐ **"这把栈是不是 `entity` 的主手/副手"** ✗
            //     ⭐ 这样 ⭐ **看别人 / 别人看你 / 自己看自己** 三种情况都对 ✓。
            //   ⚠⚠ `entity` 为 null 的场景（⭐ 物品栏渲染 / GUI / 掉落物 ✓）⇒ ⭐ 回落原来的本地玩家判断 ✓
            //     （⭐ 那时"物品栏 vs 手持"本来就是靠本地玩家区分的 ✓）。
            boolean held = isHeldBy(stack, entity);
            if (!held) {
                BakedModel idleM = resolveMaterial(this.parent.idle, stack, level, entity, seed);
                return new ContextPickModel(idleM, idleM);
            }
            // ⭐ 拿在手里 ⇒ ⭐ 按形态分 ✗ ⇒ ⭐ 长／短各自一套 ✓
            //   （⭐ 短刀的**物品栏与手持同图** ✓ 用户口径 ✓）
            boolean longForm = isLongForm(stack);
            BakedModel guiM = resolveMaterial(
                    longForm ? this.parent.guiLong : this.parent.shortSide, stack, level, entity, seed);
            BakedModel heldM = resolveMaterial(
                    longForm ? this.parent.heldLong : this.parent.shortSide, stack, level, entity, seed);
            return new ContextPickModel(guiM, heldM);
        }
    }

    /**
     * ⭐ 让**被选中的那张模型**再走一遍它自己的 `getOverrides().resolve(...)` ✓ ——
     * ⭐ 匠魂的 `MaterialOverrideHandler` 就在这里把 ⭐ `<部件>_<材料>` 换成真实材料那套 ✓
     * （⭐ 所以这是"按材料上色"能不能生效的**唯一开关** ✓）。
     *
     * <p>⚠ 任何异常都回退到原模型 ✗ —— ⭐ 上色失败最多是灰白 ✓ 不能连累渲染 ✓。
     */
    private static BakedModel resolveMaterial(BakedModel model, ItemStack stack,
                                              @Nullable ClientLevel level,
                                              @Nullable LivingEntity entity, int seed) {
        if (model == null) {
            return null;
        }
        try {
            ItemOverrides ov = model.getOverrides();
            if (ov == null) {
                return model;
            }
            BakedModel out = ov.resolve(model, stack, level, entity, seed);
            return out != null ? out : model;
        } catch (Throwable ignored) {
            return model;
        }
    }

    /**
     * ⭐⭐ 这一把此刻是否 ⭐ **正拿在手里** ✗ —— ⭐ **优先看传入的 `entity`** ✓（⭐ 修"别人看你"的 bug ✓）。
     *
     * <h2>⚠ 为什么必须要 `entity`（⭐ 用户实测 ✓ 2026-10-09）</h2>
     * ⭐ 原实现在这里看 ⭐ **本地玩家** ✗ ⇒ ⚠ 在**别人的客户端**上"本地玩家"是**他自己** ✗
     * ⇒ ⭐ 你那把刀被判成"没拿在手里" ⇒ ⭐ **回退到物品栏未手持那套模型** ✓
     * —— ⭐ 正是「**其他玩家看我手持双刀时双手都拿着未手持的物品栏模型**」✓。
     * <p>⭐ `resolve(model, stack, level, entity, seed)` 里的 `entity` **就是"正在渲染谁"** ✓ ⇒
     * ⭐ 直接问它 ⭐ **"这把栈是不是你的主手或副手"** ✗ ⭐ 三种视角就都对了 ✓：
     * ⭐ 你看自己 ✓ ⭐ 你被别人看 ✓ ⭐ 你看别人 ✓。
     *
     * <p>⚠ `entity == null` 时（⭐ 物品栏 / GUI / 掉落物渲染 ✓）⇒ ⭐ **回落到"本地玩家"判断** ✓
     * （⭐ 那时"物品栏 vs 手持"本来也只能靠本地玩家区分 ✓）。
     */
    private static boolean isHeldBy(ItemStack stack, @Nullable LivingEntity entity) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        // ① ⭐ 有实体 ⇒ 以**那个实体**为准 ✓（⭐ 关键 ✓）
        if (entity != null) {
            try {
                return entity.getMainHandItem() == stack || entity.getOffhandItem() == stack;
            } catch (Throwable ignored) {
                // 掉到下面用本地玩家兜底 ✓
            }
        }
        // ② ⭐ 没有实体 ⇒ 回落到本地玩家 ✓
        return isHeldByLocalPlayer(stack);
    }

    /**
     * ⭐ 这一把此刻是否 ⭐ **正拿在（本地玩家的）手里** ✗ ——
     * ⚠ 用**栈实例**比较 ✗（⭐ 同一把工具在物品栏与手上是**同一份 `ItemStack` 实例** ✓）
     * 而不是 `ItemStack.matches` ✗（⭐ 后者会把"另一把同 NBT 的"也算成拿着 ✓ 会误判 ✓）。
     */
    private static boolean isHeldByLocalPlayer(ItemStack stack) {
        try {
            net.minecraft.client.player.LocalPlayer p =
                    net.minecraft.client.Minecraft.getInstance().player;
            if (p == null || stack == null || stack.isEmpty()) {
                return false;
            }
            return p.getMainHandItem() == stack || p.getOffhandItem() == stack;
        } catch (Throwable ignored) {
            // ⚠ 拿不到上下文就当作"拿在手里" ✓（⭐ 宁可显示正形 ✓ 不要满背包都是"未手持" ✓）
            return true;
        }
    }

    /**
     * ⭐ 第 2 级：⭐ **形态已经定死** ✓ ⇒ 只剩"物品栏／手持"两选一 ✓。
     * <p>⚠ 它的 {@code overrides} 必须是**空实现** ✗ —— ⭐ 否则原版拿到它之后又解析一轮 ✗
     * 会把刚定好的形态丢掉 ✓（⭐ 见类注释 ✓）。
     */
    public static final class ContextPickModel extends BakedModelWrapper<BakedModel> {

        private final BakedModel guiSide;
        private final BakedModel heldSide;

        ContextPickModel(BakedModel guiSide, BakedModel heldSide) {
            super(guiSide != null ? guiSide : heldSide);
            this.guiSide = guiSide;
            this.heldSide = heldSide;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext ctx, PoseStack pose, boolean leftHand) {
            BakedModel picked = heldContext(ctx) ? this.heldSide : this.guiSide;
            if (picked == null) {
                picked = this.guiSide != null ? this.guiSide : this.heldSide;
            }
            // ⭐ 交给被选中的那张自己走它的显示变换 ✓（⭐ 匠魂的 `tconstruct:tool` 模型也在这里做事 ✓）
            return picked.applyTransform(ctx, pose, leftHand);
        }

        @Override
        public ItemOverrides getOverrides() {
            // ⚠ 空实现（⭐ 默认就是"原样返回传入的模型" ✓）—— ⚠ 绝不能返回会再判形态的东西 ✗
            return new ItemOverrides() {
            };
        }
    }
}
