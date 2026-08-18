package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.SilentGloveItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandlerModifiable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT)
public class SilentGloveSoundHandler {

    // 白名单：佩戴噤默手套时仍然允许播放的声音
    private static final Set<String> ALLOWED_SOUNDS = new HashSet<>();

    static {
        // ===== 挥砍/攻击音效 =====
        ALLOWED_SOUNDS.add("minecraft:entity.player.attack.weak");
        ALLOWED_SOUNDS.add("minecraft:entity.player.attack.strong");
        ALLOWED_SOUNDS.add("minecraft:entity.player.attack.sweep");
        ALLOWED_SOUNDS.add("minecraft:entity.player.attack.crit");
        ALLOWED_SOUNDS.add("minecraft:entity.player.attack.knockback");
        ALLOWED_SOUNDS.add("minecraft:entity.player.attack.nodamage");

        // ===== 拉弓/弩/射击音效 =====
        ALLOWED_SOUNDS.add("minecraft:item.crossbow.shoot");
        ALLOWED_SOUNDS.add("minecraft:item.crossbow.loading_start");
        ALLOWED_SOUNDS.add("minecraft:item.crossbow.loading_middle");
        ALLOWED_SOUNDS.add("minecraft:item.crossbow.loading_end");
        ALLOWED_SOUNDS.add("minecraft:entity.arrow.shoot");
        ALLOWED_SOUNDS.add("minecraft:entity.skeleton.shoot");

        // ===== 投掷音效 =====
        ALLOWED_SOUNDS.add("minecraft:entity.snowball.throw");
        ALLOWED_SOUNDS.add("minecraft:entity.egg.throw");
        ALLOWED_SOUNDS.add("minecraft:entity.ender_pearl.throw");
        ALLOWED_SOUNDS.add("minecraft:entity.potion.throw");
        ALLOWED_SOUNDS.add("minecraft:entity.tnt.primed");

        // ===== 玩家受伤/死亡（保留以便感知危险） =====
        ALLOWED_SOUNDS.add("minecraft:entity.player.hurt");
        ALLOWED_SOUNDS.add("minecraft:entity.player.hurt_drown");
        ALLOWED_SOUNDS.add("minecraft:entity.player.hurt_on_fire");
        ALLOWED_SOUNDS.add("minecraft:entity.player.hurt_freeze");
        ALLOWED_SOUNDS.add("minecraft:entity.player.death");

        // ===== 脚步声（新增） =====
        ALLOWED_SOUNDS.add("minecraft:entity.player.step");
        ALLOWED_SOUNDS.add("minecraft:block.grass.step");
        ALLOWED_SOUNDS.add("minecraft:block.stone.step");
        ALLOWED_SOUNDS.add("minecraft:block.sand.step");
        ALLOWED_SOUNDS.add("minecraft:block.wood.step");
        ALLOWED_SOUNDS.add("minecraft:block.gravel.step");
        ALLOWED_SOUNDS.add("minecraft:block.metal.step");
        ALLOWED_SOUNDS.add("minecraft:block.snow.step");
        ALLOWED_SOUNDS.add("minecraft:block.ladder.step");
        ALLOWED_SOUNDS.add("minecraft:block.vine.step");
        ALLOWED_SOUNDS.add("minecraft:block.nether_brick.step");
        ALLOWED_SOUNDS.add("minecraft:block.soul_sand.step");
        ALLOWED_SOUNDS.add("minecraft:block.honey_block.step");
        ALLOWED_SOUNDS.add("minecraft:block.slime_block.step");
        ALLOWED_SOUNDS.add("minecraft:block.sculk.step");
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (event.getSound() == null) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (!isWearingSilentGlove(player)) return;

        String soundPath = event.getSound().getLocation().toString();

        if (!ALLOWED_SOUNDS.contains(soundPath)) {
            event.setSound(null);
        }
    }

    private static boolean isWearingSilentGlove(Player player) {
        // 主手检查
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof SilentGloveItem) {
            return true;
        }

        // 副手检查
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof SilentGloveItem) {
            return true;
        }

        // Curios 槽位检查
        LazyOptional<ICuriosItemHandler> curiosOptional = CuriosApi.getCuriosInventory(player);
        AtomicBoolean found = new AtomicBoolean(false);

        curiosOptional.ifPresent(inventory -> {
            // 检查 hands 槽位
            inventory.getStacksHandler("hands").ifPresent(handler -> {
                IItemHandlerModifiable stacks = handler.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (stack.getItem() instanceof SilentGloveItem) {
                        found.set(true);
                    }
                }
            });

            if (!found.get()) {
                inventory.getStacksHandler("ring").ifPresent(handler -> {
                    IItemHandlerModifiable stacks = handler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        ItemStack stack = stacks.getStackInSlot(i);
                        if (stack.getItem() instanceof SilentGloveItem) {
                            found.set(true);
                        }
                    }
                });
            }
        });

        return found.get();
    }
}