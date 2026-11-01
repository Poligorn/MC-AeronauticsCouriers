package dev.poligorn.aerocouriers.client.station;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.net.OpenStationNamePayload;
import dev.poligorn.aerocouriers.registry.ModCreativeTabs;
import dev.poligorn.aerocouriers.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Dev/QA: drop {@code tabqa-ui.txt} in the game dir to open the creative tab, naming dialog
 * states, or grab a screenshot without GLFW clicks.
 */
public final class TabQaClient {
    private static String pendingShot;
    private static int hoverSlot = -1;

    private TabQaClient() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Path path = minecraft.gameDirectory.toPath().resolve("tabqa-ui.txt");
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
            AeronauticsCouriers.LOGGER.warn("[tabqa] ui file: {}", e.toString());
        }
    }

    private static void apply(Minecraft minecraft, String cmd) {
        switch (cmd) {
            case "scale3" -> {
                minecraft.options.guiScale().set(3);
                minecraft.resizeDisplay();
            }
            case "close" -> minecraft.setScreen(null);
            case "creative" -> openCreative(minecraft);
            case "hover-xl" -> hoverXl(minecraft);
            case "name-free" -> openName(minecraft, "Ясная Поляна", List.of());
            case "name-taken" -> openName(minecraft, "Лоная Поляна", List.of("Лоная Поляна"));
            case "name-empty" -> openName(minecraft, "", List.of());
            default -> {
                if (cmd.startsWith("shot")) {
                    pendingShot = shotName(cmd);
                }
            }
        }
    }

    private static void openCreative(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        minecraft.player.getAbilities().instabuild = true;
        minecraft.setScreen(new CreativeModeInventoryScreen(minecraft.player, minecraft.player.connection.enabledFeatures(),
                true));
        if (minecraft.screen instanceof CreativeModeInventoryScreen creative) {
            try {
                var select = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
                select.setAccessible(true);
                select.invoke(creative, ModCreativeTabs.MAIN.get());
            } catch (ReflectiveOperationException e) {
                AeronauticsCouriers.LOGGER.warn("[tabqa] selectTab: {}", e.toString());
            }
        }
    }

    private static void hoverXl(Minecraft minecraft) {
        if (!(minecraft.screen instanceof CreativeModeInventoryScreen creative)) {
            openCreative(minecraft);
        }
        if (!(minecraft.screen instanceof CreativeModeInventoryScreen)) {
            return;
        }
        var menu = ((CreativeModeInventoryScreen) minecraft.screen).getMenu();
        hoverSlot = -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.is(ModItems.DECOR_CARGO_CONTAINER_XL.get())) {
                hoverSlot = i;
                break;
            }
        }
    }

    private static void openName(Minecraft minecraft, String value, List<String> taken) {
        StationNameScreen screen = new StationNameScreen(new OpenStationNamePayload(BlockPos.ZERO, taken));
        minecraft.setScreen(screen);
        screen.qaSetValue(value);
    }

    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (hoverSlot >= 0 && event.getScreen() instanceof CreativeModeInventoryScreen creative) {
            var menu = creative.getMenu();
            if (hoverSlot < menu.slots.size()) {
                var slot = menu.slots.get(hoverSlot);
                try {
                    var left = CreativeModeInventoryScreen.class.getSuperclass().getDeclaredField("leftPos");
                    var top = CreativeModeInventoryScreen.class.getSuperclass().getDeclaredField("topPos");
                    left.setAccessible(true);
                    top.setAccessible(true);
                    int x = (int) left.get(creative) + slot.x + 8;
                    int y = (int) top.get(creative) + slot.y + 8;
                    event.getGuiGraphics().renderTooltip(Minecraft.getInstance().font, slot.getItem(), x, y);
                } catch (ReflectiveOperationException ignored) {
                    event.getGuiGraphics().renderTooltip(Minecraft.getInstance().font, slot.getItem(),
                            event.getScreen().width / 2, event.getScreen().height / 2);
                }
            }
        }
        grabPending();
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen == null) {
            grabPending();
        }
    }

    private static void grabPending() {
        if (pendingShot == null) {
            return;
        }
        String name = pendingShot;
        pendingShot = null;
        Minecraft minecraft = Minecraft.getInstance();
        Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(),
                message -> AeronauticsCouriers.LOGGER.info("[tabqa] {}", message.getString()));
    }

    private static String shotName(String cmd) {
        String rest = cmd.length() > 4 ? cmd.substring(4).strip() : "";
        if (rest.isEmpty()) {
            return "tabqa.png";
        }
        rest = rest.replaceAll("[^a-z0-9_\\-]", "_");
        return rest.endsWith(".png") ? rest : rest + ".png";
    }
}
