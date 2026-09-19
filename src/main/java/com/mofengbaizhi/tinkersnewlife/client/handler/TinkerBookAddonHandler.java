package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import slimeknights.mantle.client.book.BookLoader;
import slimeknights.mantle.client.book.data.BookData;
import slimeknights.mantle.client.book.repository.FileRepository;
import slimeknights.tconstruct.library.TinkerBookIDs;

/**
 * 把本模组的内容追加进<b>匠魂宝典</b>（{@code tconstruct:materials_and_you}）。
 *
 * <h2>为什么必须写 Java（纯资源做不到）</h2>
 * 匠魂的书不是 Patchouli，是 <b>Mantle</b> 的书：一本书的章节列表来自它<b>自己那一个</b>
 * {@code assets/<ns>/book/<bookId>/index.json}（{@code FileRepository#getSections()} 只读这一个文件 ✗），
 * 而 {@code index.json} 是**单文件** —— 资源包之间不会合并 JSON ✓ 只会"后者覆盖前者" ✗，
 * 所以我们<b>不能</b>往 {@code assets/tconstruct/book/materials_and_you/} 里塞文件来加章节。
 *
 * <p>正解是 Mantle 留的 API：{@code BookData#addRepository}（注释里明确标了 {@code // API} ✓）——
 * 一本 {@link BookData} 可以挂多个 {@link FileRepository}，{@code BookData#load()} 会把每个 repository 的
 * {@code getSections()} 结果 **addAll 合并** ✓，每个 repository 各自读自己的 {@code index.json} ✓
 * 于是我们只要有一个**自己命名空间下**的书根目录（{@code tinkersnewlife:book/tconstruct_addon} ✓）
 * 就能往匠魂的书里追加章节，且完全不用碰匠魂的文件 ✓。
 *
 * <h2>时序</h2>
 * 匠魂在<b>它自己的构造期</b>（{@code TinkerClient#onConstruct → TinkerBook#initBook}）注册这些书，
 * 所以我们要挂在更晚的 {@code FMLClientSetupEvent} 上 ✓；书的解析是**懒加载**（打开书才 load ✓），
 * 因此这个时机足够早 ✓。
 *
 * <h2>贴士</h2>
 * 我们的 repository <b>刻意不放 appearance.json</b> ✗ —— {@code BookData.load()} 里 appearance 是
 * "每个 repository 覆盖一次"，放了就会把匠魂的封面/配色顶掉 ✗。只放 index.json + sections + 语言 + 内容 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TinkerBookAddonHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TinkerBookAddonHandler.class);

    /** 我们自己的书根目录（放在本模组的命名空间下 ⇒ 与匠魂的 index.json 零冲突 ✓） */
    private static final ResourceLocation ADDON_REPOSITORY =
            new ResourceLocation(TinkersNewlife.MOD_ID, "book/tconstruct_addon");

    private TinkerBookAddonHandler() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(TinkerBookAddonHandler::appendToMaterialsAndYou);
    }

    private static void appendToMaterialsAndYou() {
        BookData book = BookLoader.getBook(TinkerBookIDs.MATERIALS_BOOK_ID);
        if (book == null) {
            // 匠魂不在场 / 注册顺序异常 —— 静默跳过（书内容缺失不影响游戏）
            LOGGER.warn("[TinkersNewlife] 找不到匠魂宝典（{}），本次不追加附加章节",
                    TinkerBookIDs.MATERIALS_BOOK_ID);
            return;
        }
        book.addRepository(new FileRepository(ADDON_REPOSITORY));
        LOGGER.debug("[TinkersNewlife] 已向匠魂宝典追加附加章节：{}", ADDON_REPOSITORY);
    }
}
