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
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * <b>数据驱动的关键帧动画（§914 / §915 / §916）</b>—— 让"长矛姿势"这类东西**写在 JSON 里**，
 * 而不是写死在 Java 里 ✓（用户口径：「**如果我想自己写动画文件呢，怎么搞**」✓
 * ＋「**这动画文件能用 blockbench 修改吗**」✓）。
 *
 * <p>本类**同时支持两种文件格式** ✓，靠内容自动判别 ✓（不靠文件名 ✓）：
 *
 * <h2>格式 A：本模组的极简格式（手写友好 ✓）</h2>
 * <pre>
 * {
 *   "length": 20,                // 时间轴长度（tick）
 *   "item": [                    // 物品/手轨道：[侧向, 上下, 前后] 位移(格) + 旋转(度) + 缩放
 *     { "time": 0,  "move": [0, 0, 0],          "rotate": [0, 0, 0] },
 *     { "time": 8,  "move": [0, -0.05, -0.30],  "rotate": [-90, 0, 0] }
 *   ],
 *   "arm": [                     // 手臂轨道（第三人称用 ✓）：只取 [xRot, yRot, zRot]（度）
 *     { "time": 12, "rotate": [-70, 0, 0] }
 *   ]
 * }
 * </pre>
 * 规则：时间单位 **tick** ✓；关键帧之间**线性插值** ✓；超出末端**停在最后一帧** ✓；
 * `move`/`rotate`/`scale` 可省 ✓；<b>收回不用写第二条</b> ✓（播放时时间倒着走 ✓）；
 * 自定义键（`_comment` 之类 ✓）一律忽略 ✓。
 *
 * <h2>格式 B：Blockbench / 基岩版动画（★ 可以直接用 Blockbench 画 ✓）</h2>
 * 判据：根对象里有 <code>"animations"</code> ✓（基岩格式自带 ✓）。
 * <pre>
 * {
 *   "format_version": "1.8.0",
 *   "animations": {
 *     "animation.spear.first_person": {
 *       "animation_length": 0.75,     // ★ 单位是**秒**（本类自动 ×20 换成 tick ✓）
 *       "loop": false,
 *       "bones": {
 *         "item": {                    // 组名必须叫 item ✓（= 手/物品）
 *           "rotation": { "0.0": [0,0,0], "0.25": [62.5,0,0] },
 *           "position": { "0.0": [0,0,0], "0.5": [0, 0.85, -1.92] },   // 单位 1/16 格 ✓ 自动换算 ✓
 *           "scale":    { "0.0": 1.0 }
 *         },
 *         "arm":  { "rotation": { "0.25": [-37.5,0,0] } }              // 组名必须叫 arm ✓（第三人称手臂）
 *       }
 *     }
 *   }
 * }
 * </pre>
 * ⇒ <b>Blockbench 工作流</b>：新建 <i>Bedrock Entity</i> 工程 → 建两个组（空组也行 ✓）
 * 名字分别叫 <code>item</code> / <code>arm</code> → 在动画时间轴摆关键帧 → 导出动画
 * → 丢进 <code>assets/tinkersnewlife/tnl_anim/</code> ✓ → 游戏内 <b>F3+T</b> ✓（不用重编译 ✓）。
 *
 * <p>⚠⚠ <b>目录名不能叫 {@code animations/}</b> ✗（§917 真崩过一次 ✗）：那个路径是 **GeckoLib 的保留目录** ✓，
 * 整合包里只要装了 GeckoLib，它就会把所有 {@code assets/<任意命名空间>/animations/*.json}
 * 当成**它自己的**动画格式去读 ✓ ⇒ 遇到我们这种"没有 {@code animations} 字段"的文件
 * 直接抛 <code>JsonSyntaxException: Missing animations</code> ✗ ⇒ **启动阶段整局崩掉** ✗。
 * 所以本模组一律用 {@code tnl_anim/} ✓。
 * <b>Blockbench 默认导出名 {@code *.animation.json} 的那份优先</b> ✓（见 {@link #SPEAR_FIRST_PERSON_BB} ✓）。
 *
 * <p>已处理的基岩细节 ✓：时间键是**秒**（×20 ✓）、`position` 是 **1/16 格**（×{@link #BEDROCK_POS_SCALE} ✓）、
 * 关键帧可以是**数组** / **单个数字**（缩放 ✓）/ **`{"post":…}`、`{"pre":…}`** 对象 ✓、
 * 第一条关键帧**之前** = 该通道默认值（位移/旋转 0 ✓ 缩放 1 ✓ 基岩就是这么回事 ✓）、
 * 某条通道**缺某个时间点** ⇒ 取它前面最近的一帧（保持 ✓ 而不是掉回 0 ✓）、
 * `animation_length` 缺失 ⇒ 用最大关键帧时间 ✓、`loop: true` ⇒ 时间取模循环 ✓。
 *
 * <p>若 1/16 的换算不对（实机看着位移大/小 16 倍 ✗）⇒ 在文件里加一个覆盖键即可 ✓（不用重编译 ✓）：
 * <pre>{ "_tnl_position_scale": 1.0 }   // 放在 animations 里面那条动画上，或放在根上都认</pre>
 *
 * <p>⚠ 已知限制（诚实说 ✓）：<b>Molang 表达式（`math.*` / `query.*`）无法求值</b> ✗
 * ⇒ 关键帧里请填**数字** ✓；Blockbench 的 <i>smooth/catmullrom</i> 缓动本类**按线性近似** ✗
 * （基岩官方文档也只支持线性 ✓；要精确缓动就在中间多加关键帧 ✓）。
 *
 * <p>⚠ 加载失败（文件缺失/写错）⇒ 返回 {@code null} ✓ 调用方**回退内置姿势** ✓ 不会崩 ✓
 * ⇒ 你可以放心改文件 ✓ 最坏就是回到默认样子 ✓。
 */
public final class AnimationClip {

    /** 一个关键帧 ✓ */
    public record Key(float time, float[] move, float[] rotate, float[] scale) {}

    private final float length;
    private final List<Key> item;
    private final List<Key> arm;
    private final boolean loop;

    private AnimationClip(float length, List<Key> item, List<Key> arm, boolean loop) {
        this.length = length;
        this.item = item;
        this.arm = arm;
        this.loop = loop;
    }

    public float length() {
        return length;
    }

    // ── 缓存（每帧都读文件太浪费 ✓ 但改完文件要能生效 ✓）──────────────────────────

    /** Blockbench 默认导出名 ✓ —— ★ 这一份**优先** ✓（用户导出的东西应当直接生效 ✓） */
    public static final ResourceLocation SPEAR_FIRST_PERSON_BB =
            new ResourceLocation("tinkersnewlife", "tnl_anim/spear_first_person.animation.json");

    /** 本模组手写的极简格式 ✓ —— 上一条不存在时用它 ✓（兜底默认姿势 ✓） */
    public static final ResourceLocation SPEAR_FIRST_PERSON =
            new ResourceLocation("tinkersnewlife", "tnl_anim/spear_first_person.json");

    private static AnimationClip spearCache;
    private static boolean spearTried;

    /**
     * 取"长矛第一人称"动画 ✓。
     *
     * @return 两个文件都没有/都写坏了 ⇒ {@code null} ✓（调用方回退到内置姿势 ✓）
     */
    public static AnimationClip spearFirstPerson() {
        if (!spearTried) {
            spearTried = true;
            spearCache = load(SPEAR_FIRST_PERSON_BB);      // ★ Blockbench 导出优先 ✓
            if (spearCache == null) spearCache = load(SPEAR_FIRST_PERSON);
        }
        return spearCache;
    }

    /** 资源包重载（含游戏内 **F3+T**）后清缓存 ✓ ⇒ 改文件即时生效 ✓ 见 {@code WizardArmorCacheReloadHandler} ✓ */
    public static void clearCache() {
        spearTried = false;
        spearCache = null;
    }

    /**
     * 从资源里读一条动画 ✓（自动判别 {@link AnimationClip 格式 A} / 基岩格式 B ✓）。
     *
     * @param id 例如 {@code tinkersnewlife:tnl_anim/spear_first_person.json} ✓
     * @return 读不到返回 {@code null} ✓（调用方回退 ✓）
     */
    public static AnimationClip load(ResourceLocation id) {
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
            if (res.isEmpty()) return null;
            try (Reader reader = res.get().openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.has("animations")) return fromBedrock(root, id);   // ★ 基岩 / Blockbench ✓
                float length = root.has("length") ? root.get("length").getAsFloat() : 20.0F;
                return new AnimationClip(length, readTrack(root.getAsJsonArray("item")),
                        readTrack(root.getAsJsonArray("arm")), false);
            }
        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("TinkersNewlife/Anim")
                    .warn("[动画] 读取 {} 失败（回退到内置姿势）：{}", id, t.toString());
            return null;
        }
    }

    // ── 格式 A：本模组极简格式 ────────────────────────────────────────────────

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

    // ── 格式 B：基岩 / Blockbench 动画 ────────────────────────────────────────

    /**
     * 基岩动画里 {@code position} 的单位换算 ✓。
     *
     * <p>基岩动画的位移量是**模型单位 = 1/16 格** ✓（和几何里那套单位一致 ✓）⇒ 换成方块要 <b>÷16</b> ✓。
     * ⚠ 若实机发现位移**大了/小了 16 倍** ✗ ⇒ 不用重编译 ✓，在动画 json 里写
     * <code>"_tnl_position_scale": 1.0</code> 覆盖即可 ✓。
     */
    public static final float BEDROCK_POS_SCALE = 1.0F / 16.0F;

    /** 基岩动画的时间单位是**秒** ✓；游戏 tick 是 1/20 秒 ✓ */
    public static final float SECONDS_TO_TICKS = 20.0F;

    /** 覆盖位移换算的键名 ✓（写在动画对象上或根对象上都认 ✓ 用户不用重编译就能试 ✓） */
    public static final String POS_SCALE_KEY = "_tnl_position_scale";

    private static AnimationClip fromBedrock(JsonObject root, ResourceLocation id) {
        JsonObject anims = root.getAsJsonObject("animations");
        if (anims == null || anims.size() == 0) return null;
        JsonObject anim = pickBedrockAnimation(anims, id);
        if (anim == null) return null;

        float posScale = BEDROCK_POS_SCALE;
        if (anim.has(POS_SCALE_KEY)) posScale = anim.get(POS_SCALE_KEY).getAsFloat();
        else if (root.has(POS_SCALE_KEY)) posScale = root.get(POS_SCALE_KEY).getAsFloat();

        float length = 20.0F;
        if (anim.has("animation_length")) {
            length = Math.max(0.05F, anim.get("animation_length").getAsFloat() * SECONDS_TO_TICKS);
        }
        boolean loop = anim.has("loop") && anim.get("loop").getAsBoolean();

        List<Key> item = new ArrayList<>();
        List<Key> arm = new ArrayList<>();
        JsonObject bones = anim.getAsJsonObject("bones");
        if (bones != null) {
            if (bones.has("item")) item = bedrockTrack(bones.getAsJsonObject("item"), posScale);
            if (bones.has("arm")) arm = bedrockTrack(bones.getAsJsonObject("arm"), posScale);
        }
        // animation_length 缺失 ⇒ 用最大关键帧时间兜底 ✓
        if (!anim.has("animation_length")) {
            for (Key k : item) length = Math.max(length, k.time());
            for (Key k : arm) length = Math.max(length, k.time());
        }
        // 一条轨道都没有 ⇒ 当读失败 ✓（免得"文件在但没效果"让人困惑 ✓ 日志里会有一行 warn ✓）
        if (item.isEmpty() && arm.isEmpty()) {
            org.slf4j.LoggerFactory.getLogger("TinkersNewlife/Anim")
                    .warn("[动画] {} 是基岩格式但没找到 item/arm 组（组名必须叫 item / arm）", id);
            return null;
        }
        return new AnimationClip(length, item, arm, loop);
    }

    /** 一个文件里可能有多条动画 ✓（Blockbench 一个工程能存好几条 ✓）⇒ 按文件名挑 ✓ 挑不到就用第一条 ✓ */
    private static JsonObject pickBedrockAnimation(JsonObject anims, ResourceLocation id) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        String base = (slash >= 0 ? path.substring(slash + 1) : path).replace(".animation.json", "")
                .replace(".json", "");
        for (Map.Entry<String, JsonElement> e : anims.entrySet()) {
            if (e.getKey().toLowerCase().contains(base.toLowerCase())) return e.getValue().getAsJsonObject();
        }
        return anims.entrySet().iterator().next().getValue().getAsJsonObject();
    }

    /** 把一个基岩"组"（bone）的三条通道合成关键帧列表 ✓ */
    private static List<Key> bedrockTrack(JsonObject bone, float posScale) {
        TreeMap<Float, float[]> move = new TreeMap<>();
        TreeMap<Float, float[]> rot = new TreeMap<>();
        TreeMap<Float, float[]> scale = new TreeMap<>();
        readBoneChannel(bone.get("position"), move, posScale);
        readBoneChannel(bone.get("rotation"), rot, 1.0F);
        readBoneChannel(bone.get("scale"), scale, 1.0F);

        TreeSet<Float> times = new TreeSet<>();
        times.addAll(move.keySet());
        times.addAll(rot.keySet());
        times.addAll(scale.keySet());

        List<Key> out = new ArrayList<>();
        for (float t : times) {
            out.add(new Key(t, holdAt(move, t, new float[]{0.0F, 0.0F, 0.0F}),
                    holdAt(rot, t, new float[]{0.0F, 0.0F, 0.0F}),
                    holdAt(scale, t, new float[]{1.0F, 1.0F, 1.0F})));
        }
        return out;
    }

    private static void readBoneChannel(JsonElement channel, TreeMap<Float, float[]> out, float mul) {
        if (channel == null || !channel.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : channel.getAsJsonObject().entrySet()) {
            float t;
            try {
                t = Float.parseFloat(e.getKey().trim()) * SECONDS_TO_TICKS;   // ★ 秒 → tick ✓
            } catch (NumberFormatException ex) {
                continue;
            }
            float[] v = readVec(e.getValue());
            if (v != null) out.put(t, new float[]{v[0] * mul, v[1] * mul, v[2] * mul});
        }
    }

    /** 关键帧取值：数组 ✓ / 单个数字（缩放 ✓）/ {@code {"post":…}}、{@code {"pre":…}} 对象 ✓ */
    private static float[] readVec(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonArray()) {
            JsonArray a = el.getAsJsonArray();
            float[] v = new float[]{0.0F, 0.0F, 0.0F};
            for (int i = 0; i < Math.min(3, a.size()); i++) v[i] = a.get(i).getAsFloat();
            return v;
        }
        if (el.isJsonPrimitive()) {
            float f = el.getAsFloat();
            return new float[]{f, f, f};
        }
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("post")) return readVec(o.get("post"));
            if (o.has("pre")) return readVec(o.get("pre"));
        }
        return null;
    }

    /**
     * 某条通道在 t 时刻的值 ✓。
     *
     * <p>⚠ §916 修的 bug ✗→✓：<b>第一条关键帧之前必须是"通道默认值"</b>（位移/旋转 0 ✓ 缩放 1 ✓），
     * ✗ **不是**第一条关键帧的值 ✓。
     * 原来写的是 {@code floorEntry==null ⇒ firstEntry()} ✗ ⇒ 像
     * {@code "position": {"0.5": [...]}} 这种"位移只在 0.5s 打了一帧"的写法 ✓
     * 会让物品**从第 0 tick 起就一直待在终点位置** ✗（用户 Blockbench 导出那份正是这种写法 ✓）。
     */
    private static float[] holdAt(TreeMap<Float, float[]> ch, float t, float[] fallback) {
        if (ch.isEmpty()) return fallback;
        Map.Entry<Float, float[]> e = ch.floorEntry(t);
        return e == null ? fallback : e.getValue();     // ★ 首帧之前 ⇒ 默认值 ✓
    }

    // ── 采样与播放 ────────────────────────────────────────────────────────

    /** 在一条轨道上按时间取插值后的值 ✓（超出末端停在最后一帧 ✓） */
    private static float[] sample(List<Key> track, float time, int part) {
        if (track.isEmpty()) return null;
        if (time <= track.get(0).time()) return pick(track.get(0), part);
        for (int i = 0; i < track.size() - 1; i++) {
            Key a = track.get(i);
            Key b = track.get(i + 1);
            if (time <= b.time()) {
                return interp(pick(a, part), pick(b, part),
                        Mth.clamp(Mth.inverseLerp(time, a.time(), b.time()), 0.0F, 1.0F));
            }
        }
        return pick(track.get(track.size() - 1), part);
    }

    private static float[] interp(float[] a, float[] b, float u) {
        return new float[]{Mth.lerp(u, a[0], b[0]), Mth.lerp(u, a[1], b[1]), Mth.lerp(u, a[2], b[2])};
    }

    private static float[] pick(Key key, int part) {
        return switch (part) {
            case 0 -> key.move();
            case 1 -> key.rotate();
            default -> key.scale();
        };
    }

    /** {@code loop: true} 时把时间绕回时间轴内 ✓（也顺手防负数 ✓） */
    private float wrap(float time) {
        if (!loop || length <= 0.0F) return Math.max(0.0F, time);
        float m = time % length;
        return m < 0.0F ? m + length : m;
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
        float t = wrap(time);
        float[] move = sample(item, t, 0);
        float[] rot = sample(item, t, 1);
        float[] scale = sample(item, t, 2);
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
        float[] rot = sample(arm, wrap(time), 1);
        if (rot == null) return null;
        float d = (float) Math.PI / 180.0F;
        return new float[]{rot[0] * d, rot[1] * d, rot[2] * d};
    }
}
