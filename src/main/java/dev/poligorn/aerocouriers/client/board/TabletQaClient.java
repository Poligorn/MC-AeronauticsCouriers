package dev.poligorn.aerocouriers.client.board;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.client.CourierBoardScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Dev/QA: drop {@code tabletsqa-ui.txt} in the game dir to change GUI scale, hover chips,
 * open details, force a strict-tutorial frame, or grab a screenshot without GLFW clicks.
 */
public final class TabletQaClient {
    private static String pendingShot;

    private TabletQaClient() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Path path = minecraft.gameDirectory.toPath().resolve("tabletsqa-ui.txt");
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
            AeronauticsCouriers.LOGGER.warn("[tabletsqa] ui file: {}", e.toString());
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
                if (minecraft.screen instanceof CourierBoardScreen board) {
                    applyBoard(board, cmd);
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
        String name = cmd.contains("=") ? cmd.substring(cmd.indexOf('=') + 1).strip() : "tabletsqa.png";
        if (name.isEmpty()) {
            name = "tabletsqa.png";
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
        RenderTarget target = minecraft.getMainRenderTarget();
        Screenshot.grab(minecraft.gameDirectory, name, target, message ->
                AeronauticsCouriers.LOGGER.info("[tabletsqa] {}", message.getString()));
    }

    private static void writeTooltipRect(Minecraft minecraft) {
        try {
            int scale = (int) minecraft.getWindow().getGuiScale();
            int x = dev.poligorn.aerocouriers.client.TooltipDraw.lastX();
            int y = dev.poligorn.aerocouriers.client.TooltipDraw.lastY();
            int w = dev.poligorn.aerocouriers.client.TooltipDraw.lastW();
            int h = dev.poligorn.aerocouriers.client.TooltipDraw.lastH();
            String line = "gui=" + x + "," + y + "," + w + "," + h
                    + "\nscale=" + scale
                    + "\nwindow=" + (x * scale) + "," + (y * scale) + "," + (w * scale) + "," + (h * scale)
                    + "\n";
            Files.writeString(minecraft.gameDirectory.toPath().resolve("tabletsqa-tooltip-rect.txt"), line);
        } catch (Exception e) {
            AeronauticsCouriers.LOGGER.warn("[tabletsqa] tooltip rect: {}", e.toString());
        }
    }

    private static void applyBoard(CourierBoardScreen board, String cmd) {
        boolean ok = switch (cmd) {
            case "list", "reset" -> {
                board.qaSetContractKind(BoardContractKind.ALL);
                yield true;
            }
            case "filter-rare", "rare-filter" -> {
                board.qaSetContractKind(BoardContractKind.RARE);
                yield true;
            }
            case "select-rare" -> board.qaSelect(e -> BoardCardRenderer.showRare(e)
                    && !BoardCardRenderer.showExpensive(e));
            case "select-both" -> board.qaSelect(e -> BoardCardRenderer.showRare(e)
                    && BoardCardRenderer.showExpensive(e));
            case "select-expensive" -> board.qaSelect(e -> BoardCardRenderer.showExpensive(e)
                    && !BoardCardRenderer.showRare(e));
            case "select-plain" -> board.qaSelect(e -> e.villageCustomer()
                    && !BoardCardRenderer.showRare(e) && !BoardCardRenderer.showExpensive(e));
            case "details-rare" -> board.qaOpenDetails(e -> BoardCardRenderer.showRare(e)
                    && !BoardCardRenderer.showExpensive(e));
            case "details-both" -> board.qaOpenDetails(e -> BoardCardRenderer.showRare(e)
                    && BoardCardRenderer.showExpensive(e));
            case "hover-rare" -> board.qaHoverFlag(true);
            case "hover-expensive" -> board.qaHoverFlag(false);
            case "hover-item" -> board.qaHoverRareItem();
            case "tutorial-step1", "tutorial-step2", "tutorial-step2-details", "tutorial-step3" ->
                    board.qaTutorialFrame(cmd);
            case "details-player", "customer-details" -> board.qaOpenPlayerCustomerDetails();
            case "hover-customer" -> board.qaHoverCustomerName();
            case "open-customer", "customer-profile" -> board.qaOpenCustomerProfile();
            default -> {
                AeronauticsCouriers.LOGGER.warn("[tabletsqa] unknown ui {}", cmd);
                yield false;
            }
        };
        AeronauticsCouriers.LOGGER.info("[tabletsqa] {} → {}", cmd, ok);
    }
}
