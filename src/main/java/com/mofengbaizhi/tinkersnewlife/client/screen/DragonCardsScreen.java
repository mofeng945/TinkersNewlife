package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.network.dragon.DragonCards;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;

/** Client-only previews: do not add the detached dragon entities to the level. */
public final class DragonCardsScreen extends Screen {
    private static final String PREFIX = "screen.tinkersnewlife.dragon_cards.";
    private static final int TURN_TICKS = 8;
    private final List<CompoundTag> cards;
    private final UUID session;
    private final boolean pitAvailable;
    private final boolean pitRunning;
    private int remainingTicks;
    private boolean pitRequested;
    private final LivingEntity[] previews;
    private final boolean[] attempted;
    private int index;
    private int turnDirection;
    private int turnAge = TURN_TICKS;
    private boolean pressed;
    private boolean dragged;
    private double dragDistance;
    private float yaw;
    private float pitch;

    public DragonCardsScreen(List<CompoundTag> cards, UUID session, boolean pitAvailable, int remainingTicks) {
        super(text("title"));
        this.cards = cards.stream().map(CompoundTag::copy).toList();
        this.session = session;
        this.pitAvailable = pitAvailable;
        this.pitRunning = remainingTicks > 0;
        this.remainingTicks = remainingTicks;
        previews = new LivingEntity[cards.size()];
        attempted = new boolean[cards.size()];
    }

    private static Component text(String key, Object... args) { return Component.translatable(PREFIX + key, args); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void tick() {
        if (turnAge < TURN_TICKS) turnAge++;
        if (remainingTicks > 0) remainingTicks--;
    }

    @Override protected void init() {
        if (pitAvailable && !releaseLocked()) {
            int buttonWidth = Math.max(120, font.width(text("pit.start")) + 16);
            addRenderableWidget(Button.builder(text("pit.start"), button -> confirmPit())
                    .bounds((width - buttonWidth) / 2, 21, buttonWidth, 20)
                    .tooltip(Tooltip.create(text("pit.warning"))).build());
        }
    }

    private void confirmPit() {
        pressed = false;
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                pitRequested = true;
                TinkersNewlife.CHANNEL.sendToServer(new DragonCards.StartPit(session));
            }
            minecraft.setScreen(this);
        }, text("pit.confirm_title"), text("pit.warning"), text("pit.start"), Component.translatable("gui.cancel")) {
            @Override public boolean isPauseScreen() { return false; }
            @Override public boolean keyPressed(int key, int scan, int modifiers) {
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    minecraft.setScreen(DragonCardsScreen.this);
                    return true;
                }
                return super.keyPressed(key, scan, modifiers);
            }
        });
    }

    // Only the next server Open can unlock release, even when the local countdown reaches zero.
    private boolean releaseLocked() { return pitRunning || pitRequested; }
    private int headerExtra() { return pitAvailable || releaseLocked() ? 22 : 0; }
    private int cardWidth() { return Math.max(80, Math.min(218, width - 116)); }
    private int cardHeight() { return Math.max(112, Math.min(222, height - 54 - headerExtra())); }
    private int cardLeft() { return (width - cardWidth()) / 2; }
    private int top() { return Math.max(23 + headerExtra(), (height - cardHeight() + headerExtra()) / 2); }
    private int spacing() { return cardWidth() / 2 + sideWidth() / 2 + 7; }
    private int sideWidth() { return Math.max(21, Math.min(84, (width - cardWidth()) / 2 - 13)); }
    private int neighbor(int direction) { return Math.floorMod(index + direction, cards.size()); }

    private void turn(int direction) {
        if (cards.size() < 2 || pressed) return;
        index = neighbor(direction);
        yaw = pitch = 0;
        turnDirection = direction;
        turnAge = 0;
    }

    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui);
        gui.drawCenteredString(font, title, width / 2, 8, 0xF8E2B2);
        if (releaseLocked()) {
            Component pitStatus = remainingTicks > 0 ? text("pit.countdown", (remainingTicks + 19) / 20)
                    : text(pitRequested ? "pit.starting" : "pit.waiting");
            centeredFit(gui, pitStatus, width / 2, 27, width - 16, 0xF0CA8D);
        }
        if (cards.isEmpty()) {
            gui.drawCenteredString(font, text("empty"), width / 2, height / 2 - 4, 0xDDCDB0);
            super.render(gui, mouseX, mouseY, partialTick);
            return;
        }
        int mid = width / 2;
        float progress = Mth.clamp((turnAge + partialTick) / TURN_TICKS, 0f, 1f);
        float easing = 1 - (1 - progress) * (1 - progress) * (1 - progress);
        drawSide(gui, -1);
        drawSide(gui, 1);
        // Slide the next card in and narrow it briefly to suggest a card turning on its vertical axis.
        gui.pose().pushPose();
        try {
            gui.pose().translate(mid + turnDirection * 24 * (1 - easing), 0, 0);
            gui.pose().scale(.73f + .27f * easing, 1, 1);
            gui.pose().translate(-mid, 0, 0);
            drawMain(gui);
        } finally { gui.pose().popPose(); }
        gui.drawCenteredString(font, text("page", index + 1, cards.size()), mid, top() + cardHeight() + 3, 0xF8E2B2);
        gui.drawCenteredString(font, text("navigate"), mid, height - 11, 0xC7B89C);
        super.render(gui, mouseX, mouseY, partialTick);
    }

    private void drawSide(GuiGraphics gui, int direction) {
        int w = sideWidth(), h = cardHeight() * 3 / 4;
        int left = width / 2 + spacing() * direction - w / 2;
        int y = top() + (cardHeight() - h) / 2;
        boolean present = cards.size() > 1;
        frame(gui, left, y, w, h, present ? 0xB5665545 : 0x85504642, 0xE81E1719);
        if (!present) return;
        CompoundTag card = cards.get(neighbor(direction));
        centeredFit(gui, name(card), left + w / 2, y + 8, w - 8, 0xDBC8A3);
        gui.drawCenteredString(font, direction < 0 ? "‹" : "›", left + w / 2, y + h / 2, 0xF8DFA7);
        centeredFit(gui, status(card), left + w / 2, y + h - 16, w - 8, 0xD9BA89);
    }

    private void drawMain(GuiGraphics gui) {
        CompoundTag card = cards.get(index);
        int x = cardLeft(), y = top(), w = cardWidth(), h = cardHeight();
        int mid = width / 2;
        frame(gui, x, y, w, h, 0xFFE0B673, 0xFC241A1B);
        centeredFit(gui, name(card), mid, y + 8, w - 16, 0xFFE4AE);
        centeredFit(gui, status(card), mid, y + 20, w - 16, stored(card) ? 0x9BDBAD : 0xE9B582);
        int statsTop = y + h - 60;
        int previewTop = y + 32;
        if (statsTop - previewTop >= 14) drawDragon(gui, mid, previewTop, statsTop - 2, card);
        centeredFit(gui, text("identity", text(card.getBoolean("male") ? "male" : "female"), card.getInt("stage"), card.getInt("days")), mid, statsTop, w - 12, 0xE9D8B5);
        centeredFit(gui, text("health", stat(card, "hp"), stat(card, "maxHp")), mid, statsTop + 11, w - 12, 0xF4B1A6);
        centeredFit(gui, text("defense", stat(card, "armor"), stat(card, "toughness")), mid, statsTop + 22, w - 12, 0xB9D1ED);
        centeredFit(gui, text("attack", stat(card, "attack")), mid, statsTop + 33, w - 12, 0xF0CA8D);
        boolean canRelease = stored(card) && !releaseLocked();
        String releaseLabel = canRelease ? "release" : stored(card) ? "pit.browse_only" : "unavailable";
        centeredFit(gui, text(releaseLabel), mid, statsTop + 46, w - 12, canRelease ? 0xFFF2C7 : 0xB5A395);
    }

    private void drawDragon(GuiGraphics gui, int mid, int y1, int y2, CompoundTag card) {
        LivingEntity dragon = preview(index, card);
        if (dragon == null) {
            centeredFit(gui, text("preview_unavailable"), mid, (y1 + y2) / 2, cardWidth() - 14, 0xB5A395);
            return;
        }
        double size = Math.max(.5, Math.max(dragon.getBbWidth(), dragon.getBbHeight()));
        int scale = Mth.clamp((int)(Math.min(cardWidth() * .36, (y2 - y1) * .48) / size), 1, 36);
        Quaternionf rotation = new Quaternionf().rotationZ((float)Math.PI)
                .rotateX((float)Math.toRadians(pitch - 8)).rotateY((float)Math.toRadians(yaw + 30));
        // Keep the models out of the header/stats even where Ice and Fire's wings exceed the hitbox.
        gui.enableScissor(cardLeft() + 4, y1, cardLeft() + cardWidth() - 4, y2);
        try {
            InventoryScreen.renderEntityInInventory(gui, mid, y1 + (int)((y2 - y1) * .72), scale, rotation, null, dragon);
        } finally { gui.disableScissor(); }
    }

    private LivingEntity preview(int slot, CompoundTag card) {
        if (attempted[slot] || minecraft == null || minecraft.level == null) return previews[slot];
        attempted[slot] = true;
        CompoundTag nbt = card.getCompound("data").copy();
        if (!nbt.contains("id", 8)) nbt.putString("id", card.getString("type"));
        nbt.remove("Passengers");
        try {
            EntityType.create(nbt, minecraft.level).ifPresent(entity -> {
                if (entity instanceof LivingEntity living) {
                    living.setPos(0, 0, 0);
                    living.setYRot(0);
                    living.setXRot(0);
                    living.yBodyRot = living.yBodyRotO = 0;
                    living.yHeadRot = living.yHeadRotO = 0;
                    previews[slot] = living;
                }
            });
        } catch (RuntimeException failure) {
            TinkersNewlife.LOGGER.debug("Cannot render detached dragon preview", failure);
        }
        return previews[slot];
    }

    private static boolean stored(CompoundTag card) { return card.getInt("state") == 1; }
    private static Component status(CompoundTag card) { return text(stored(card) ? "stored" : card.getBoolean("live") ? "released" : "snapshot"); }
    private static Component name(CompoundTag card) {
        String name = card.getString("name");
        Component label = name.isBlank() ? text("unnamed") : Component.literal(name);
        return card.getBoolean("pitWinner") ? text("pit.winner", label) : label;
    }
    private static String stat(CompoundTag card, String key) {
        if (!card.contains(key, 99)) return "—";
        double n = card.getDouble(key);
        return Double.isFinite(n) ? String.format(java.util.Locale.ROOT, "%.1f", n) : "—";
    }
    private void centeredFit(GuiGraphics gui, Component line, int x, int y, int maxWidth, int color) {
        int length = font.width(line);
        if (length <= maxWidth) { gui.drawCenteredString(font, line, x, y, color); return; }
        float scale = Math.max(.58f, (float)maxWidth / length);
        String clip = font.plainSubstrByWidth(line.getString(), (int)(maxWidth / scale));
        gui.pose().pushPose();
        try {
            gui.pose().translate(x, y, 0);
            gui.pose().scale(scale, scale, 1);
            gui.drawString(font, clip, -font.width(clip) / 2, 0, color);
        } finally { gui.pose().popPose(); }
    }
    private static void frame(GuiGraphics gui, int x, int y, int w, int h, int edge, int background) {
        gui.fill(x, y, x + w, y + h, edge);
        gui.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, background, 0xFC120D16);
    }
    private boolean mainContains(double x, double y) {
        return x >= cardLeft() && x < cardLeft() + cardWidth() && y >= top() && y < top() + cardHeight();
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && !cards.isEmpty()) {
            if (mainContains(x, y)) {
                pressed = true;
                dragged = false;
                dragDistance = 0;
                return true;
            }
            if (cards.size() > 1 && y >= top() && y < top() + cardHeight()) {
                for (int dir : new int[]{-1, 1}) {
                    int side = width / 2 + spacing() * dir;
                    if (Math.abs(x - side) < sideWidth() / 2.0) { turn(dir); return true; }
                }
            }
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && pressed) {
            dragDistance += Math.abs(dx) + Math.abs(dy);
            dragged |= dragDistance >= 3;
            if (dragged) {
                yaw = (yaw + (float)dx * 1.7f) % 360;
                pitch = Mth.clamp(pitch + (float)dy, -58, 58);
            }
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && pressed) {
            pressed = false;
            if (!dragged && mainContains(x, y) && turnAge >= TURN_TICKS) release();
            return true;
        }
        return super.mouseReleased(x, y, button);
    }
    private void release() {
        if (releaseLocked()) return;
        var card = cards.get(index);
        if (!stored(card)) return;
        UUID dragon;
        try { dragon = UUID.fromString(card.getString("uuid")); }
        catch (IllegalArgumentException invalid) { return; }
        TinkersNewlife.CHANNEL.sendToServer(new DragonCards.Release(session, dragon));
        onClose();
    }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (!cards.isEmpty() && amount != 0) { turn(amount > 0 ? -1 : 1); return true; }
        return super.mouseScrolled(x, y, amount);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) { turn(-1); return true; }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) { turn(1); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
}
