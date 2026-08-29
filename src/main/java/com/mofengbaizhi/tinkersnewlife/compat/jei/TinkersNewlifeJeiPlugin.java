package com.mofengbaizhi.tinkersnewlife.compat.jei;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * JEI 集成（可选依赖，非硬依赖）：古神事件材料「获取配方」。
 * <p>
 * 模组本体不依赖 JEI：本类只在 JEI 存在时由 {@code ForgePluginFinder} 扫描
 * {@code @JeiPlugin} 注解并反射实例化；未安装 JEI 时本类不会被加载，
 * 其引用缺失导致的 LinkageError 也会被 JEI 捕获忽略，不影响游戏。
 * <p>
 * 注册方式：为各材料物品注册 JEI 信息条目（物品详情页显示获取条件）。
 */
@JeiPlugin
public class TinkersNewlifeJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(TinkersNewlife.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registerInfo(registration, ModItems.RLYEH_CALL.get(), "jei.tinkersnewlife.acquire.rlyeh_call");
        registerInfo(registration, ModItems.NYARLATHOTEP_DESIRE.get(), "jei.tinkersnewlife.acquire.nyarlathotep_desire");
        registerInfo(registration, ModItems.YELLOW_KING_REMNANT.get(), "jei.tinkersnewlife.acquire.yellow_king_remnant");
        registerInfo(registration, ModItems.NICHOLAS_BLESSING.get(), "jei.tinkersnewlife.acquire.nicholas_blessing");
        registerInfo(registration, ModItems.YOG_SOTHOTH_GATE_KEY.get(), "jei.tinkersnewlife.acquire.yog_sothoth_gate_key");
        registerInfo(registration, ModItems.GHELOTH_REMAINS.get(), "jei.tinkersnewlife.acquire.gheloth_remains");
        registerInfo(registration, ModItems.ECHO_OF_THE_VOID.get(), "jei.tinkersnewlife.acquire.echo_of_the_void");
        registerInfo(registration, ModItems.ASTRAL_ANCHOR.get(), "jei.tinkersnewlife.acquire.astral_anchor");
        registerInfo(registration, ModItems.NEXUS_OF_SPACETIME.get(), "jei.tinkersnewlife.acquire.nexus_of_spacetime");
    }

    private void registerInfo(IRecipeRegistration registration, Item item, String key) {
        registration.addIngredientInfo(new ItemStack(item), VanillaTypes.ITEM_STACK,
                Component.translatable(key));
    }
}
