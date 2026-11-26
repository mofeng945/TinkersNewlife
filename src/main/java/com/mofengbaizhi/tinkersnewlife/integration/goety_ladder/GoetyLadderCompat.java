package com.mofengbaizhi.tinkersnewlife.integration.goety_ladder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * 「诡厄巫法：阶梯」({@code goety_ladder}) 联动的最低限度桥接（虚空金属材料用）。
 *
 * <h2>为什么要一个专门的兼容层</h2>
 * 本模组对诡厄巫法家族**一律不硬依赖**（{@code util/GoetyBridge} 就是纯反射 ✓），
 * 阶梯同理 ⇒ 这里只放：<b>常量 id</b> ＋ <b>唯一的反射调用</b>（"这一发算不算虚空伤害" ✓）。
 * 没装阶梯时所有方法都安全退化成 null / false ✓。
 *
 * <h2>ID 都是实测来的（2026-09-27 查 {@code goety_ladder-1.1.6-half-fix.jar} / {@code goety-2.5.54.5.jar}）</h2>
 * <ul>
 *   <li>{@code goety_ladder:void_metal} —— 虚空金属锭（材料源 ✓）；</li>
 *   <li>{@code goety_ladder:void_wane}（<b>虚蚀</b>：每级 −20% 移速 / −4 伤害 / −10% 非虚空伤害 ✓）—— 虚空抚摸叠的就是它；</li>
 *   <li>{@code goety:void_touched}（<b>虚空之蚀</b>：每级使所受伤害翻倍、受伤后移除 ✓）—— 虚无恩宠免疫；</li>
 *   <li>{@code goety:void_fluid}（<b>液态虚空</b>方块）/ {@code goety:void_block}（<b>虚空块</b>，4 虚空瓶仪式 ⇒ 16 个）—— 虚无恩宠免疫。</li>
 * </ul>
 */
public final class GoetyLadderCompat {

    public static final String MOD_ID = "goety_ladder";

    /** 虚蚀（虚空抚摸施加 / 叠层） */
    public static final ResourceLocation VOID_WANE = new ResourceLocation(MOD_ID, "void_wane");
    /** 虚空核心·侵蚀（阶梯的另一个虚空 debuff，本材料暂不涉及，留作参考） */
    public static final ResourceLocation VOID_EROSION = new ResourceLocation(MOD_ID, "void_erosion");
    /** 虚空之蚀（诡厄本体） */
    public static final ResourceLocation VOID_TOUCHED = new ResourceLocation("goety", "void_touched");
    /** 液态虚空（诡厄本体的流体方块） */
    public static final ResourceLocation VOID_FLUID_BLOCK = new ResourceLocation("goety", "void_fluid");
    /** 虚空块（诡厄本体） */
    public static final ResourceLocation VOID_BLOCK = new ResourceLocation("goety", "void_block");

    /** 反射：阶梯自己的虚空伤害判定（只解析一次 ✓） */
    private static boolean resolved = false;
    @Nullable
    private static java.lang.reflect.Method isVoidDamage;

    private GoetyLadderCompat() {}

    /** 阶梯装了没（材料/特性全部以它为条件 ✓） */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    @Nullable
    public static MobEffect effect(ResourceLocation id) {
        return ForgeRegistries.MOB_EFFECTS.getValue(id);
    }

    @Nullable
    public static Block block(ResourceLocation id) {
        return ForgeRegistries.BLOCKS.getValue(id);
    }

    /**
     * 这一发算不算「虚空系伤害」。
     *
     * <p>⭐ 优先<b>直接问阶梯自己</b>：{@code com.mc_xiaoming.GoetyLadder.utils.DamageDetector#isVoidDamage} ✓
     * —— 它同时看自家伤害类型（{@code VOIDED}/{@code DOOM}）、{@code msgId} 里的 void/ender/teleport/end，
     * 以及来源实体注册名里的 void/ender/end ✓（连它的 {@code isVoidSpellDamage} 也是走这一条再叠 {@code SpellType.VOID} ✓）。
     * <p>拿不到那个类（没装阶梯 / 版本改了）时退回我们自己的一版等价判断 ✓ —— 宁可多算一点，也不要漏 ✓。
     */
    public static boolean isVoidDamage(@Nullable DamageSource source) {
        if (source == null) return false;
        if (!resolved) {
            resolved = true;
            try {
                Class<?> detector = Class.forName("com.mc_xiaoming.GoetyLadder.utils.DamageDetector");
                isVoidDamage = detector.getMethod("isVoidDamage", DamageSource.class);
            } catch (Throwable ignored) {
                isVoidDamage = null;
            }
        }
        if (isVoidDamage != null) {
            try {
                Object r = isVoidDamage.invoke(null, source);
                if (r instanceof Boolean b) return b;
            } catch (Throwable ignored) {
                // 落到下面的兜底
            }
        }
        String msgId = source.getMsgId();
        if (msgId.contains("void") || msgId.contains("ender") || msgId.contains("teleport") || msgId.contains("end")) {
            return true;
        }
        var direct = source.getDirectEntity();
        if (direct != null) {
            String type = direct.getType().toString();
            return type.contains("void") || type.contains("ender") || type.contains("end");
        }
        return false;
    }
}
