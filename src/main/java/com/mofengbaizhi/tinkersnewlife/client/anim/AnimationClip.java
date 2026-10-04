package com.mofengbaizhi.tinkersnewlife.client.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <b>关键帧动画文件（§914）</b>—— 让"长矛姿势"这类东西**写在 JSON 里**，
 * 而不是写死在 Java 里 ✓（用户口径：「**如果我想自己写动画文件呢，怎么搞**」✓）。
 *
 * <h2>文件放哪</h2>
 * <pre>assets/tinkersnewlife/animations/&lt;名字&gt;.json</pre>
 * 例如 {@code assets/tinkersnewlife/animations/spear_first_person.json} ✓
 * （跟贴图一样打进模组资源里 ✓ 改完**重新加载资源**（F3+T）就生效 ✓ 不用重新编译 ✓）。
 *
 * <h2>文件格式（就是我下面这个 ✓ 极简 ✓）</h2>
 * <pre>
 * {
 *   "length": 20,                // 整条时间轴多长（tick ✓ 从 0 开始）
 *   "item": [                    // ★ 物品/手的轨道：位移(格) + 旋转(度) + 缩放
 *     { "time": 0,  "move": [0, 0, 0],          "rotate": [0, 0, 0] },
 *     { "time": 8,  "move": [0, -0.05, -0.30],  "rotate": [-90, 0, 0] },
 *     { "time": 12, "move": [0, -0.05, -0.45],  "rotate": [-105, 0, 0] }
 *   ],
 *   "arm": [                     // 手臂轨道（第三人称用 ✓）：只取 xRot/yRot/zRot（度）
 *     { "time": 0,  "rotate": [0, 0, 0] },
 *     { "time": 12, "rotate": [-70, 0, 0] }
 *   ]
 * }
 * </pre>
 * 规则（都是刻意做简单的 ✓）：
 * <ul>
 *   <li><b>时间单位 = tick</b> ✓（和游戏里一致 ✓ 好对着手感调）；</li>
 *   <li>两个关键帧之间是**线性插值** ✓（要缓动就在中间多加几个关键帧 ✓ 比在代码里写缓动直观 ✓）；</li>
 *   <li>时间超出 {@code length} ⇒ 停在最后一帧 ✓（"按住保持刺出"就是靠这个 ✓）；</li>
 *   <li>数组可以少写：{@code move}/{@code rotate}/{@code scale} **缺省 = 不动** ✓；</li>
 *   <li><b>收回</b>不用另写一条 ✓：播放时把时间**倒着走**即可（见调用方 ✓）。</li>
 * </ul>
 *
 * <h2>怎么被用上</h2>
 * 看 {@code client/handler/SpearFirstPersonHandler} ✓ —— 它按"蓄力/刺出/收回"算出时间 ✓
 * 再调 {@link #applyItem} 把这一帧套到手部的 PoseStack 上 ✓。
 *
 * <p>⚠ 加载失败（文件缺失/写错）⇒ 返回 {@code null} ✓ 调用方**回退到内置姿势** ✓ 不会崩 ✓
 * （所以你可以放心改文件 ✓ 改坏了最坏就是回到默认样子 ✓）。
 */
public final class AnimationClip {

    /** 一个关键帧 ✓ */
    public record Key(float time, float[] move, float[] rotate, float[] scale) {}

    private final float length;
    private final List<Key> item;
    private final List<Key> arm;

    private AnimationClip(float length, List<Key> item, List<Key> arm) {
        this.length = length;
        this.item = item;
        this.arm = arm;
    }

    public float length() {
        return length;
    }

    // ── 缓存（每帧都读文件太浪费 ✓ 但改完文件要能生效 ✓）──────────────────────────

    /** 长矛第一人称动画的文件位置 ✓ */
    public static final ResourceLocation SPEAR_FIRST_PERSON =
            new ResourceLocation("tinkersnewlife", "animations/spear_first_person.json");

    private static AnimationClip spearCache;
    private static boolean spearTried;

    /**
     * 取"长矛第一人称"动画 ✓。
     *
     * @return 文件不存在/写坏了 ⇒ {@code null} ✓（调用方回退到内置姿势 ✓）
     */
    public static AnimationClip spearFirstPerson() {
        if (!spearTried) {
            spearTried = true;
            spearCache = load(SPEAR_FIRST_PERSON);
        }
        return spearCache;
    }

    /** 资源包重载（含游戏内 **F3+T**）后清缓存 ✓ ⇒ 改文件即时生效 ✓ 见 {@code WizardArmorCacheReloadHandler} ✓ */
    public static void clearCache() {
        spearTried = false;
        spearCache = null;
    }

    /**
     * 从资源里读一条动画 ✓。
     *
     * @param id 例如 {@code tinkersnewlife:animations/spear_first_person.json} ✓
     * @return 读不到返回 {@code null} ✓（调用方回退 ✓）
     */
    public static AnimationClip load(ResourceLocation id) {
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
            if (res.isEmpty()) return null;
            try (Reader reader = res.get().openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                float length = root.has("length") ? root.get("length").getAsFloat() : 20.0F;
                List<Key> item = readTrack(root.getAsJsonArray("item"));
                List<Key> arm = readTrack(root.getAsJsonArray("arm"));
                return new AnimationClip(length, item, arm);
            }
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("TinkersNewlife/Anim")
                    .warn("[动画] 读取 {} 失败（回退到内置姿势）：{}", id, t.toString());
            return null;
        }
    }

    private static List<Key> readTrack(JsonArray arr) {
        List<Key> out = new ArrayList<>();
        if (arr == null) return out;
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            out.add(new Key(
                    o.has("time") ? o.get("time").getAsFloat() : 0.0F,
                    read3(o.getAsJsonArray("move")),
                    read3(o.getAsJsonArray("rotate")),
                    o.has("scale") ? read3(o.getAsJsonArray("scale")) : new float[]{1.0F, 1.0F, 1.0F}));
        }
        return out;
    }

    private static float[] read3(JsonArray arr) {
        float[] v = new float[]{0.0F, 0.0F, 0.0F};
        if (arr != null) {
            for (int i = 0; i < Math.min(3, arr.size()); i++) v[i] = arr.get(i).getAsFloat();
        }
        return v;
    }

    /** 在一条轨道上按时间取插值后的值 ✓（超出末端停在最后一帧 ✓） */
    private static float[] sample(List<Key> track, float time, int part) {
        if (track.isEmpty()) return null;
        if (time <= track.get(0).time()) return pick(track.get(0), part);
        for (int i = 0; i < track.size() - 1; i++) {
            Key a = track.get(i);
            Key b = track.get(i + 1);
            if (time <= b.time()) {
                float u = Mth.clamp(Mth.inverseLerp(time, a.time(), b.time()), 0.0F, 1.0F);
                float[] va = pick(a, part);
                float[] vb = pick(b, part);
                return new float[]{Mth.lerp(u, va[0], vb[0]), Mth.lerp(u, va[1], vb[1]), Mth.lerp(u, va[2], vb[2])};
            }
        }
        return pick(track.get(track.size() - 1), part);
    }

    private static float[] pick(Key key, int part) {
        return switch (part) {
            case 0 -> key.move();
            case 1 -> key.rotate();
            default -> key.scale();
        };
    }

    /**
     * 把这一帧套到**物品/手**的 PoseStack 上 ✓。
     *
     * @param pose  当前手部的姿态栈 ✓（像 {@code RenderHandEvent#getPoseStack()} ✓）
     * @param time  时间（tick ✓ 可以是小数 ✓；收回就传递减的时间 ✓）
     * @param arm   哪只手 ✓（左右手的侧向位移/旋转取反 ✓）
     */
    public void applyItem(PoseStack pose, float time, HumanoidArm arm) {
        int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
        float[] move = sample(item, time, 0);
        float[] rot = sample(item, time, 1);
        float[] scale = sample(item, time, 2);
        if (move != null) {
            pose.translate((double) ((float) invert * move[0]), (double) move[1], (double) move[2]);
        }
        if (rot != null) {
            if (rot[2] != 0.0F) pose.mulPose(Axis.ZP.rotationDegrees((float) invert * rot[2]));
            if (rot[1] != 0.0F) pose.mulPose(Axis.YP.rotationDegrees((float) invert * rot[1]));
            if (rot[0] != 0.0F) pose.mulPose(Axis.XP.rotationDegrees(rot[0]));
        }
        if (scale != null && (scale[0] != 1.0F || scale[1] != 1.0F || scale[2] != 1.0F)) {
            pose.scale(scale[0], scale[1], scale[2]);
        }
    }

    /** 取这一帧的**手臂**三轴旋转（弧度 ✓ 第三人称写回 {@code ModelPart} 用 ✓）；没写 arm 轨道返回 null ✓ */
    public float[] armRot(float time) {
        float[] rot = sample(arm, time, 1);
        if (rot == null) return null;
        float d = (float) Math.PI / 180.0F;
        return new float[]{rot[0] * d, rot[1] * d, rot[2] * d};
    }
}
