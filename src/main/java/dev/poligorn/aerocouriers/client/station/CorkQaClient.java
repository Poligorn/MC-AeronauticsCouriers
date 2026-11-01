package dev.poligorn.aerocouriers.client.station;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.VillageAdminPayload;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Dev/QA: drop {@code corkqa-ui.txt} in the game dir to change GUI scale, open a note on the
 * tablet, or open admin dialogs without GLFW clicks.
 */
public final class CorkQaClient {
    private static String pendingShot;

    private CorkQaClient() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Path path = minecraft.gameDirectory.toPath().resolve("corkqa-ui.txt");
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            String cmd = Files.readString(path).strip().toLowerCase();
            Files.deleteIfExists(path);
            if (!cmd.isEmpty()) {
                apply(minecraft, cmd);
            }
        } catch (Exception e) {
            AeronauticsCouriers.LOGGER.warn("[corkqa] ui file: {}", e.toString());
        }
    }

    private static void apply(Minecraft minecraft, String cmd) {
        switch (cmd) {
            case "scale2", "wide" -> {
                minecraft.options.guiScale().set(2);
                minecraft.resizeDisplay();
            }
            case "scale3", "large" -> {
                minecraft.options.guiScale().set(3);
                minecraft.resizeDisplay();
            }
            case "scale4", "compact" -> {
                minecraft.options.guiScale().set(4);
                minecraft.resizeDisplay();
            }
            case "close" -> minecraft.setScreen(null);
            default -> {
                if (cmd.startsWith("shot")) {
                    pendingShot = shotName(cmd);
                    return;
                }
                if (minecraft.screen instanceof CorkBoardScreen board) {
                    applyBoard(board, cmd);
                } else if (minecraft.screen instanceof VillageAdminScreen admin) {
                    applyAdmin(admin, cmd);
                }
            }
        }
    }

    public static void onScreenRender(ScreenEvent.Render.Post event) {
        grabPending();
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen == null) {
            grabPending();
        }
    }

    private static String shotName(String cmd) {
        String name = cmd.contains("=") ? cmd.substring(cmd.indexOf('=') + 1).strip() : "corkqa.png";
        if (name.isEmpty()) {
            name = "corkqa.png";
        }
        return name.endsWith(".png") ? name : name + ".png";
    }

    private static void grabPending() {
        if (pendingShot == null) {
            return;
        }
        String name = pendingShot;
        pendingShot = null;
        Minecraft minecraft = Minecraft.getInstance();
        writeTooltipRect(minecraft);
        writeFooterRect(minecraft);
        RenderTarget target = minecraft.getMainRenderTarget();
        Screenshot.grab(minecraft.gameDirectory, name, target, message ->
                AeronauticsCouriers.LOGGER.info("[corkqa] {}", message.getString()));
    }

    private static void writeTooltipRect(Minecraft minecraft) {
        try {
            int scale = (int) minecraft.getWindow().getGuiScale();
            int x = CorkNoteTooltip.lastX();
            int y = CorkNoteTooltip.lastY();
            int w = CorkNoteTooltip.lastW();
            int h = CorkNoteTooltip.lastH();
            String line = "gui=" + x + "," + y + "," + w + "," + h
                    + "\nscale=" + scale
                    + "\nwindow=" + (x * scale) + "," + (y * scale) + "," + (w * scale) + "," + (h * scale)
                    + "\n";
            Files.writeString(minecraft.gameDirectory.toPath().resolve("corkqa-tooltip-rect.txt"), line);
            AeronauticsCouriers.LOGGER.info("[corkqa] tooltip rect {}", line.replace('\n', ' '));
        } catch (Exception e) {
            AeronauticsCouriers.LOGGER.warn("[corkqa] tooltip rect: {}", e.toString());
        }
    }

    private static void writeFooterRect(Minecraft minecraft) {
        try {
            if (!(minecraft.screen instanceof CorkBoardScreen board) || board.layout() == null) {
                return;
            }
            int scale = (int) minecraft.getWindow().getGuiScale();
            CorkBoardLayout layout = board.layout();
            CorkBoardLayout.Rect text = board.lastFooterText();
            CorkBoardLayout.Rect plank = layout.footerPlank;
            CorkBoardLayout.Rect hole = layout.innerHole;
            String line = "plank=" + plank.x() + "," + plank.y() + "," + plank.w() + "," + plank.h()
                    + "\ntext=" + text.x() + "," + text.y() + "," + text.w() + "," + text.h()
                    + "\nwin=" + layout.winX + "," + layout.winY + "," + layout.winW + "," + layout.winH
                    + "\nhole=" + hole.x() + "," + hole.y() + "," + hole.w() + "," + hole.h()
                    + "\ninset=" + layout.frameInsetBottom()
                    + "\nscale=" + scale
                    + "\n";
            Files.writeString(minecraft.gameDirectory.toPath().resolve("corkqa-footer-rect.txt"), line);
            AeronauticsCouriers.LOGGER.info("[corkqa] footer {}", line.replace('\n', ' '));
        } catch (Exception e) {
            AeronauticsCouriers.LOGGER.warn("[corkqa] footer rect: {}", e.toString());
        }
    }

    private static void applyBoard(CorkBoardScreen board, String cmd) {
        if (cmd.startsWith("scroll")) {
            int rows = 1;
            if (cmd.length() > 6) {
                try {
                    rows = Integer.parseInt(cmd.substring(6).replace("=", "").strip());
                } catch (NumberFormatException ignored) {
                    rows = 1;
                }
            }
            board.setScrollRows(rows);
            return;
        }
        if (cmd.startsWith("open-note") || cmd.startsWith("waybill") || "open-order".equals(cmd)) {
            openFirst(board, style -> !style.locked() && !style.inProgress());
            return;
        }
        if (cmd.startsWith("hover-note") || "hover".equals(cmd)) {
            board.qaHoverFirstNote();
        }
    }

    private static void applyAdmin(VillageAdminScreen admin, String cmd) {
        switch (cmd) {
            case "dialog-rename" -> admin.openRenameDialog();
            case "dialog-rep" -> admin.openReputationDialog();
            case "dialog-clear" -> admin.openConfirmDialog(VillageAdminPayload.Action.CLEAR);
            case "dialog-regen" -> admin.openConfirmDialog(VillageAdminPayload.Action.REGENERATE);
            default -> {
            }
        }
    }

    private static void openFirst(CorkBoardScreen board, java.util.function.Predicate<CorkNoteStyle> match) {
        for (BoardSnapshotPayload.BoardEntry entry : board.visibleOrders()) {
            if (match.test(board.noteStyle(entry))) {
                board.openOrderOnTablet(entry);
                return;
            }
        }
        if (!board.visibleOrders().isEmpty()) {
            board.openOrderOnTablet(board.visibleOrders().getFirst());
        }
    }
}
