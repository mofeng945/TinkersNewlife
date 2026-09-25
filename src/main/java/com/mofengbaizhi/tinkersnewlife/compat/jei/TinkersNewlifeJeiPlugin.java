package com.mofengbaizhi.tinkersnewlife.compat.jei;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.ModRecipeSerializers;
import com.mofengbaizhi.tinkersnewlife.content.handler.LightningRodConversionHandler;
import com.mofengbaizhi.tinkersnewlife.content.recipe.CurseCraftRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;

/**
 * JEI 集成（可选依赖，非硬依赖）：古神事件材料「获取配方」+ 咒力合成仪式分类。
 * <p>
 * 模组本体不依赖 JEI：本类只在 JEI 存在时由 {@code ForgePluginFinder} 扫描
 * {@code @JeiPlugin} 注解并反射实例化；未安装 JEI 时本类不会被加载，
 * 其引用缺失导致的 LinkageError 也会被 JEI 捕获忽略，不影响游戏。
 */
@JeiPlugin
public class TinkersNewlifeJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(TinkersNewlife.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new CurseCraftJeiCategory(registration.getJeiHelpers().getGuiHelper()),
                // ⭐ 避雷针雷击转化（烈焰血 → 液态闪电，5:4）
                new LightningRodConversionJeiCategory(registration.getJeiHelpers().getGuiHelper()));
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
        registerInfo(registration, ModItems.DURANDAL_SHARD.get(), "jei.tinkersnewlife.acquire.durandal_shard");
        // ⭐ 咒力核心：可熔炼回收为对应材料流体（万能材料熔化配方自动匹配）
        registerInfo(registration, ModItems.CURSE_CORE.get(), "jei.tinkersnewlife.acquire.curse_core_melt");

        // ⭐ 咒力合成仪式：从服务端配方管理器取（data/<ns>/recipes/curse_craft/*.json）
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            List<CurseCraftRecipe> recipes =
                    server.getRecipeManager().getAllRecipesFor(ModRecipeSerializers.CURSE_CRAFT_TYPE.get());
            if (!recipes.isEmpty()) {
                registration.addRecipes(CurseCraftJeiCategory.TYPE, recipes);
            }
        }

        // ⭐ 避雷针雷击转化（烈焰血 → 液态闪电，5:4）：机制不是真配方 ⇒ 手工造一条展示 ✓
        // ⚠ 输出流体是本模组的**铁魔法联动流体**（没装铁魔法就不注册 ✓）⇒ 查不到就整条不注册 ✓
        Fluid blazingBlood = ForgeRegistries.FLUIDS.getValue(LightningRodConversionHandler.BLAZING_BLOOD);
        Fluid liquidLightning = ForgeRegistries.FLUIDS.getValue(LightningRodConversionHandler.LIQUID_LIGHTNING);
        if (blazingBlood != null && liquidLightning != null) {
            registration.addRecipes(LightningRodConversionJeiCategory.TYPE, List.of(
                    new LightningRodConversionJeiCategory.Conversion(blazingBlood, liquidLightning,
                            LightningRodConversionJeiCategory.SHOW_INPUT_MB,
                            LightningRodConversionJeiCategory.SHOW_OUTPUT_MB)));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // 结构中心方块即催化剂：在 JEI 里右键格赫罗斯矿石就能看到这个分类
        registration.addRecipeCatalyst(new ItemStack(ModItems.GHELOTH_ORE.get()), CurseCraftJeiCategory.TYPE);
        // ⭐ 避雷针就是"雷击转化"的催化剂 ⇒ 右键避雷针能翻到那个分类 ✓
        registration.addRecipeCatalyst(new ItemStack(Items.LIGHTNING_ROD),
                LightningRodConversionJeiCategory.TYPE);
    }

    private void registerInfo(IRecipeRegistration registration, Item item, String key) {
        registration.addIngredientInfo(new ItemStack(item), VanillaTypes.ITEM_STACK,
                Component.translatable(key));
    }
}
