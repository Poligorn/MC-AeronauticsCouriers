package dev.poligorn.aerocouriers.client.station;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import dev.poligorn.aerocouriers.net.VillageAdminPayload;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Dev/QA: drop {@code corkqa-ui.txt} in the game dir to open waybills and admin dialogs without GLFW clicks.
 */
public final class CorkQaClient {
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
            case "compact" -> {
                minecraft.options.guiScale().set(4);
                minecraft.resizeDisplay();
            }
            case "large" -> {
                minecraft.options.guiScale().set(3);
                minecraft.resizeDisplay();
            }
            case "close" -> minecraft.setScreen(null);
            default -> {
                if (minecraft.screen instanceof CorkBoardScreen board) {
                    applyBoard(board, cmd);
                } else if (minecraft.screen instanceof VillageAdminScreen admin) {
                    applyAdmin(admin, cmd);
                }
            }
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
        if ("waybill".equals(cmd)) {
            openFirst(board, style -> !style.locked() && !style.inProgress());
            return;
        }
        if ("waybill-locked".equals(cmd)) {
            openFirst(board, CorkNoteStyle::locked);
            return;
        }
        if ("waybill-progress".equals(cmd)) {
            openFirst(board, CorkNoteStyle::inProgress);
            return;
        }
        if ("waybill-expensive".equals(cmd)) {
            openFirst(board, style -> style.expensive() && !style.locked());
            return;
        }
        if ("waybill-rare".equals(cmd)) {
            openFirst(board, style -> style.rare() && !style.locked());
            return;
        }
        if ("waybill-close".equals(cmd)) {
            board.closeWaybill();
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
                board.openWaybill(entry);
                return;
            }
        }
        if (!board.visibleOrders().isEmpty()) {
            board.openWaybill(board.visibleOrders().getFirst());
        }
    }
}
