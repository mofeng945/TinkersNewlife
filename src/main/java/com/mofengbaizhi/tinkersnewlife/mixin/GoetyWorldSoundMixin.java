package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.util.VoidArmorDiag;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>诡厄「世界音效包」的发送方点名</b>（§757）—— 用来把 {@code goety:void_touched_deactivate} 的**源头**钉死 ✓
 *
 * <h2>为什么加它（§756 的遗留矛盾 ✗）</h2>
 * §756 已确证：客户端每秒听到一次 {@code goety:void_touched_deactivate} ✓，
 * 调用栈显示它是**服务端**通过
 * {@code com.Polarice3.Goety.common.network.server.SPlayWorldSoundPacket} 发下来的 ✓。
 * 但同一场里我们挂在诡厄 {@code PotionEvents} 上的四个注入点**只看到别的效果**
 * （{@code vampirism:sunscreen}、{@code minecraft:fire_resistance} ✓）
 * 和一堆「被移除（无效果实例 ✗）」（约 40 次/秒 ✗），**没有一条点名 {@code goety:void_touched}** ✗
 * ⇒ "到底是谁在放这个音"仍然只能靠推断 ✗。
 * <p>⇒ 这一层直接钩**这个声音包自己的构造器** ✓：
 * 它是 {@code <init>(BlockPos, SoundEvent, float, float)}（javap 实核 ✓ <b>两版诡厄完全一致</b> ✓，
 * 且是诡厄自有方法 ⇒ 不会被 reobf 改名 ✓），
 * ⇒ <b>谁构造它、就一定是"放这个音"的那段代码</b> ✓✓，再配一条**调用栈**就彻底定案 ✓。
 *
 * <p>⚠ 构造成本很低（就是一个记录对象 ✓）且**每个声音 id 只打一次调用栈** ✓ 不会刷屏 ✓；
 * 记录键＝声音 id ✓ 仍由 {@link VoidArmorDiag} 每 5 秒限流 ✓。
 * <p>⚠ 只做诊断 ✓ **不改任何行为** ✓（关掉 {@link VoidArmorDiag#ENABLED} 就全部静音 ✓）。
 */
@Mixin(targets = "com.Polarice3.Goety.common.network.server.SPlayWorldSoundPacket", remap = false)
public class GoetyWorldSoundMixin {

    /** 已经打过调用栈的声音 id ✓ */
    @Unique
    private static final Set<String> tinkersnewlife$seenWorldSounds = ConcurrentHashMap.newKeySet();

    /**
     * ⚠ §760 修：原来写的是 {@code @At("HEAD")} ✗ —— Mixin 0.8.5 **不允许注入到构造器的 HEAD** ✗
     * （日志实证：{@code InvalidInjectionException: @At("HEAD") selector Found @Inject targetting a constructor} ✓，
     * 因为那一刻 {@code super()} 还没调用、字段也没初始化 ✓）。
     * ⇒ 改成 {@code @At("RETURN")} ✓：构造体跑完再记 ✓ 声音 id 与**发起方调用栈**一样拿得到 ✓
     * （调用栈里"谁在 new 这个包"那几帧照旧 ✓）。
     * <p>⚠ 这条也再次证明 {@code require = 1} 的价值 ✓：失败是**响亮**的 ✓ 而不是像 §751 那样静默 ✗。
     */
    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 1)
    private void tinkersnewlife$watchWorldSound(BlockPos pos, SoundEvent soundEvent, float volume, float pitch,
                                                CallbackInfo ci) {
        try {
            if (!VoidArmorDiag.ENABLED || soundEvent == null) return;
            ResourceLocation id = soundEvent.getLocation();
            if (id == null) return;
            VoidArmorDiag.log("worldsound:" + id, "📢 诡厄世界音效 {} ✓（音量 {} ／ 音调 {} ／ 位置 {}）",
                    id, volume, pitch, pos);
            if (tinkersnewlife$seenWorldSounds.add(id.toString())) {
                VoidArmorDiag.log("worldsound:stack:" + id, "🧭 音效 {} 的**发送方**调用栈（首次出现）{}",
                        id, VoidArmorDiag.shortStack(8));
            }
        } catch (Throwable t) {
            VoidArmorDiag.log("sniff:error:worldsound", "🐞 嗅探异常(SPlayWorldSoundPacket) {}", String.valueOf(t));
        }
    }
}
