package com.mofengbaizhi.tinkersnewlife.client.screen;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.network.dragon.DragonWar;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Tactical controls over the real client level; all accepted orders remain server-authoritative. */
public final class DragonWarScreen extends Screen {
    private static final String PREFIX = "screen.tinkersnewlife.dragon_war.";
    private static final double SIZE = 65, MIN_SPAN = 10;
    private static final int DEPLOY = 0, MOVE = 1, ATTACK = 2, BREATH = 3, FOLLOW = 4, CANCEL = 5;
    private static final int FRIEND = 0xFF69E8C3, OTHER = 0xFFFF826E, PICKED = 0xFFFFDE91;
    private final DragonCardsScreen parent;
    private final UUID token;
    private final BlockPos center;
    private final UUID deployment;
    private Set<UUID> allies;
    private UUID selected;
    private boolean aimBreath;
    private boolean pressed, dragged;
    private double pressX, pressY, panX, panZ, span = SIZE;
    private DragonWarCamera.Frame frame;
    private Button breath, follow, cancel;

    public DragonWarScreen(DragonCardsScreen parent, UUID token, BlockPos center, UUID deployment, Set<UUID> allies) {
        super(text("title"));
        this.parent = parent;
        this.token = token;
        this.center = center.immutable();
        this.deployment = deployment;
        this.allies = Set.copyOf(allies);
    }

    /** Call from the server-open packet's main-thread client handler. */
    public static void open(DragonCardsScreen parent, UUID token, BlockPos center, UUID deployment, Set<UUID> allies) {
        Minecraft.getInstance().setScreen(new DragonWarScreen(parent, token, center, deployment, allies));
    }

    /** Replace the session's commandable dragons with the server's authoritative list. */
    public static void updateAllies(UUID token, Set<UUID> allies) {
        if (Minecraft.getInstance().screen instanceof DragonWarScreen screen && screen.token.equals(token)) {
            screen.allies = Set.copyOf(allies);
            if (screen.selected != null && !screen.allies.contains(screen.selected)) {
                screen.selected = null;
                screen.aimBreath = false;
            }
        }
    }

    private static Component text(String key) { return Component.translatable(PREFIX + key); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override protected void init() {
        frame = null;
        pressed = dragged = false;
        int w = Math.min(108, Math.max(24, (width - 20) / 3));
        int x = (width - 3 * w - 8) / 2;
        int y = height - 25;
        breath = addRenderableWidget(Button.builder(text("breath"), button -> aimBreath = !aimBreath)
                .bounds(x, y, w, 20).build());
        follow = addRenderableWidget(Button.builder(text("follow"), button -> immediate(FOLLOW))
                .bounds(x + w + 4, y, w, 20).build());
        cancel = addRenderableWidget(Button.builder(text("cancel"), button -> immediate(CANCEL))
                .bounds(x + (w + 4) * 2, y, w, 20).build());
        refreshButtons();
    }

    private int side() { return Math.max(1, Math.min(width - 12, height - 76)); }
    private int left() { return (width - side()) / 2; }
    private int top() { return (height - side()) / 2; }
    private boolean inView(double x, double y) {
        return x >= left() && x < left() + side() && y >= top() && y < top() + side();
    }

    Vec3 cameraPosition() {
        double heightFactor = minecraft.getWindow().getHeight() / minecraft.getWindow().getGuiScale() / side();
        double altitude = SIZE * heightFactor / (2 * Math.tan(Math.toRadians(DragonWarCamera.FOV / 2)));
        return new Vec3(center.getX() + .5 + panX, center.getY() + altitude, center.getZ() + .5 + panZ);
    }

    double cameraFov() {
        return Math.toDegrees(2 * Math.atan(span / SIZE * Math.tan(Math.toRadians(DragonWarCamera.FOV / 2))));
    }

    void setFrame(DragonWarCamera.Frame frame) { this.frame = frame; }

    private boolean inArea(BlockPos p) {
        return Math.abs((long) p.getX() - center.getX()) <= 32 && Math.abs((long) p.getZ() - center.getZ()) <= 32;
    }

    private void clampPan() {
        double limit = (SIZE - span) / 2;
        panX = Mth.clamp(panX, -limit, limit);
        panZ = Mth.clamp(panZ, -limit, limit);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (!inView(x, y)) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pressed = true;
            dragged = false;
            pressX = x;
            pressY = y;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            BlockPos point = ground(x, y);
            if (point != null && deployment != null && !allies.contains(deployment)) {
                send(DEPLOY, deployment, point, -1);
            }
            return true;
        }
        return false;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (pressed && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (!dragged && Math.hypot(x - pressX, y - pressY) >= 3) {
                dragged = true;
                dx = x - pressX;
                dy = y - pressY;
            }
            if (dragged) {
                panX -= dx * span / side();
                panZ -= dy * span / side();
                clampPan();
            }
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (pressed && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            pressed = false;
            if (!dragged && inView(x, y)) orderAt(x, y);
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double steps) {
        if (!inView(x, y)) return super.mouseScrolled(x, y, steps);
        double next = Mth.clamp(span * Math.pow(.82, steps), MIN_SPAN, SIZE);
        panX += (x - left() - side() / 2.0) * (span - next) / side();
        panZ += (y - top() - side() / 2.0) * (span - next) / side();
        span = next;
        clampPan();
        return true;
    }

    private BlockPos ground(double x, double y) {
        if (frame == null || minecraft.level == null || minecraft.player == null) return null;
        Vec3 end = frame.unproject(x, y, .9998f);
        BlockHitResult hit = minecraft.level.clip(new ClipContext(frame.eye(), end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
        if (hit.getType() != HitResult.Type.BLOCK || !inArea(hit.getBlockPos())) return null;
        BlockPos point = hit.getBlockPos().above();
        return minecraft.level.hasChunkAt(point) ? point : null;
    }

    private void orderAt(double x, double y) {
        Entity target = entityAt(x, y);
        if (target != null && allies.contains(target.getUUID())) {
            selected = target.getUUID();
            aimBreath = false;
            refreshButtons();
        } else if (selected != null) {
            BlockPos point = target == null ? ground(x, y) : target.blockPosition();
            if (point == null) return;
            int action = aimBreath ? BREATH : target == null ? MOVE : ATTACK;
            send(action, selected, point, target == null ? -1 : target.getId());
            aimBreath = false;
        }
    }

    private void immediate(int action) {
        if (selected != null) send(action, selected, center, -1);
        aimBreath = false;
    }

    private void send(int action, UUID dragon, BlockPos position, int targetEntity) {
        TinkersNewlife.CHANNEL.sendToServer(new DragonWar.Command(token, action, dragon, position, targetEntity));
    }

    private void refreshButtons() {
        if (breath != null) breath.active = follow.active = cancel.active = selected != null;
    }

    private record Marker(Entity entity, Vec3 pixel) {}

    private List<Marker> markers(float partialTick) {
        List<Marker> result = new ArrayList<>();
        if (frame == null || minecraft.level == null) return result;
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof net.minecraft.world.entity.item.ItemEntity
                    || entity instanceof net.minecraftforge.entity.PartEntity<?>
                    || !entity.isAlive() || entity.isSpectator() || !inArea(entity.blockPosition())) continue;
            Vec3 label = entity.getPosition(partialTick).add(0, entity.getBbHeight() + .12, 0);
            Vec3 projected = frame.project(label);
            if (projected != null && inView(projected.x, projected.y)) result.add(new Marker(entity, projected));
        }
        return result;
    }

    private boolean isOrderTarget(Entity entity) {
        return entity instanceof LivingEntity && entity != minecraft.player
                && (entity.isPickable() || allies.contains(entity.getUUID()));
    }

    private Entity entityAt(double x, double y) {
        Entity candidate = null;
        double closest = 81;
        for (Marker marker : markers(minecraft.getFrameTime())) {
            Entity entity = marker.entity;
            if (!isOrderTarget(entity)) continue;
            double dx = x - marker.pixel.x, dy = y - marker.pixel.y;
            double distance = dx * dx + dy * dy;
            if (distance < closest) {
                closest = distance;
                candidate = entity;
            }
        }
        if (candidate != null || frame == null || minecraft.level == null) return candidate;
        Vec3 eye = frame.eye(), end = frame.unproject(x, y, .9998f);
        double nearest = eye.distanceToSqr(end);
        if (minecraft.player != null) {
            var hit = minecraft.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, minecraft.player));
            if (hit.getType() == HitResult.Type.BLOCK) nearest = eye.distanceToSqr(hit.getLocation());
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!isOrderTarget(entity) || !entity.isAlive() || entity.isSpectator() || !inArea(entity.blockPosition())) continue;
            var hit = entity.getBoundingBox().clip(eye, end);
            if (hit.isEmpty()) continue;
            double distance = eye.distanceToSqr(hit.get());
            if (distance < nearest) {
                nearest = distance;
                candidate = entity;
            }
        }
        return candidate;
    }

    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        int l = left(), t = top(), r = l + side(), b = t + side();
        if (frame == null) gui.fill(l, t, r, b, 0xFF161B23);
        gui.fill(0, 0, width, t, 0xFF11151C);
        gui.fill(0, b, width, height, 0xFF11151C);
        gui.fill(0, t, l, b, 0xFF11151C);
        gui.fill(r, t, width, b, 0xFF11151C);
        gui.renderOutline(l - 1, t - 1, side() + 2, side() + 2, 0xFFAF9060);
        gui.enableScissor(l, t, r, b);
        drawBoundary(gui);
        for (Marker marker : markers(partialTick)) {
            UUID id = marker.entity.getUUID();
            int x = (int) Math.round(marker.pixel.x), y = (int) Math.round(marker.pixel.y);
            int color = id.equals(selected) ? PICKED
                    : allies.contains(id) || marker.entity == minecraft.player ? FRIEND : OTHER;
            int radius = id.equals(selected) ? 4 : 3;
            gui.fill(x - radius - 1, y - radius - 1, x + radius + 2, y + radius + 2, 0xCD11151C);
            gui.renderOutline(x - radius, y - radius, 2 * radius + 1, 2 * radius + 1, color);
            var name = marker.entity.getDisplayName();
            int labelWidth = font.width(name);
            int labelX = x - labelWidth / 2, labelY = y - radius - font.lineHeight - 3;
            gui.fill(labelX - 2, labelY - 1, labelX + labelWidth + 2, labelY + font.lineHeight, 0xCD11151C);
            gui.drawString(font, name, labelX, labelY, color, false);
        }
        gui.disableScissor();
        gui.drawCenteredString(font, title, width / 2, 7, PICKED);
        gui.drawCenteredString(font, text(aimBreath ? "aim_breath" : "controls"), width / 2, 21, 0xFFE3E7EE);
        refreshButtons();
        super.render(gui, mouseX, mouseY, partialTick);
    }

    private void drawBoundary(GuiGraphics gui) {
        if (frame == null) return;
        double x = center.getX() - 32, y = center.getY(), z = center.getZ() - 32;
        Vec3 nw = frame.project(new Vec3(x, y, z));
        Vec3 ne = frame.project(new Vec3(x + SIZE, y, z));
        Vec3 se = frame.project(new Vec3(x + SIZE, y, z + SIZE));
        Vec3 sw = frame.project(new Vec3(x, y, z + SIZE));
        line(gui, nw, ne);
        line(gui, ne, se);
        line(gui, se, sw);
        line(gui, sw, nw);
    }

    private static void line(GuiGraphics gui, Vec3 from, Vec3 to) {
        if (from == null || to == null) return;
        double dx = to.x - from.x, dy = to.y - from.y;
        gui.pose().pushPose();
        gui.pose().translate(from.x, from.y, 0);
        gui.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(dy, dx)));
        gui.fill(0, 0, (int) Math.ceil(Math.hypot(dx, dy)), 1, 0xCFFFE09F);
        gui.pose().popPose();
    }
}
