package dev.poligorn.aerocouriers.client.station;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.net.OpenStationNamePayload;
import dev.poligorn.aerocouriers.registry.ModCreativeTabs;
import dev.poligorn.aerocouriers.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.CreativeTabsScreenPage;

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
            case "close" -> {
                hoverSlot = -1;
                minecraft.setScreen(null);
            }
            case "creative" -> {
                hoverSlot = -1;
                openCreative(minecraft);
            }
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
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        // displayItems uses ItemDisplayParameters.hasPermissions == canUseGameMasterBlocks
        // (instabuild && permission ≥ 2). A world copied from a dedicated server has no cheats.
        minecraft.player.getAbilities().instabuild = true;
        minecraft.player.setPermissionLevel(4);
        CreativeModeTabs.tryRebuildTabContents(
                minecraft.player.connection.enabledFeatures(),
                true,
                minecraft.level.registryAccess());
        minecraft.setScreen(new CreativeModeInventoryScreen(minecraft.player,
                minecraft.player.connection.enabledFeatures(), true));
        if (!(minecraft.screen instanceof CreativeModeInventoryScreen creative)) {
            return;
        }
        CreativeModeTab tab = ModCreativeTabs.MAIN.get();
        showTabPage(creative, tab);
        try {
            var select = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
            select.setAccessible(true);
            select.invoke(creative, tab);
        } catch (ReflectiveOperationException e) {
            AeronauticsCouriers.LOGGER.warn("[tabqa] selectTab: {}", e.toString());
        }
        AeronauticsCouriers.LOGGER.info("[tabqa] tab items={}", tab.getDisplayItems().size());
    }

    /** NeoForge splits tabs across pages; selectTab alone leaves the waybill icon off-screen. */
    private static void showTabPage(CreativeModeInventoryScreen creative, CreativeModeTab tab) {
        try {
            var pagesField = CreativeModeInventoryScreen.class.getDeclaredField("pages");
            pagesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<CreativeTabsScreenPage> pages = (List<CreativeTabsScreenPage>) pagesField.get(creative);
            for (CreativeTabsScreenPage page : pages) {
                if (page.getVisibleTabs().contains(tab)) {
                    creative.setCurrentPage(page);
                    return;
                }
            }
        } catch (ReflectiveOperationException e) {
            AeronauticsCouriers.LOGGER.warn("[tabqa] page: {}", e.toString());
        }
    }

    private static void hoverXl(Minecraft minecraft) {
        if (!(minecraft.screen instanceof CreativeModeInventoryScreen)) {
            openCreative(minecraft);
        }
        if (!(minecraft.screen instanceof CreativeModeInventoryScreen creative)) {
            return;
        }
        hoverSlot = -1;
        for (int i = 0; i < creative.getMenu().slots.size(); i++) {
            if (creative.getMenu().slots.get(i).getItem().is(ModItems.DECOR_CARGO_CONTAINER_XL.get())) {
                hoverSlot = i;
                break;
            }
        }
        AeronauticsCouriers.LOGGER.info("[tabqa] hover-xl slot={} items={}", hoverSlot,
                ModCreativeTabs.MAIN.get().getDisplayItems().size());
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
                Slot slot = menu.slots.get(hoverSlot);
                int[] pos = slotGui(creative, slot);
                renderItemTooltip(event.getGuiGraphics(), slot.getItem(), pos[0], pos[1]);
            }
        }
        grabPending();
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen == null) {
            grabPending();
        }
    }

    /**
     * Vanilla {@code renderTooltip} after the screen has flushed paints fill and text at z=400;
     * the fill wins the depth test (empty purple box). Draw the real item lines ourselves
     * at z=410 so the title, grey hint and blue mod name stay visible.
     */
    private static void renderItemTooltip(GuiGraphics graphics, ItemStack stack, int x, int y) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        List<Component> lines = stack.getTooltipLines(
                Item.TooltipContext.of(minecraft.level),
                minecraft.player,
                minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL);
        if (lines.isEmpty()) {
            AeronauticsCouriers.LOGGER.warn("[tabqa] empty tooltip lines for {}", stack.getItem());
            return;
        }
        // NeoForge injects the blue italic mod name in the render tooltip event, not getTooltipLines.
        String modName = ModList.get().getModContainerById(AeronauticsCouriers.MOD_ID)
                .map(c -> c.getModInfo().getDisplayName())
                .orElse("Aeronautics Couriers");
        boolean hasMod = lines.stream().anyMatch(c -> c.getString().equals(modName));
        if (!hasMod) {
            lines.add(Component.literal(modName).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
        }
        int textW = 0;
        for (Component line : lines) {
            textW = Math.max(textW, font.width(line));
        }
        int boxW = textW + 8;
        int boxH = 6 + lines.size() * 10;
        int sw = minecraft.getWindow().getGuiScaledWidth();
        int sh = minecraft.getWindow().getGuiScaledHeight();
        int tx = Math.min(x + 12, sw - boxW - 4);
        int ty = Math.max(4, y - 12);
        if (ty + boxH > sh - 4) {
            ty = sh - boxH - 4;
        }
        RenderSystem.disableDepthTest();
        graphics.flush();
        graphics.fill(tx - 1, ty - 1, tx + boxW + 1, ty + boxH + 1, 0x505000FF);
        graphics.fill(tx, ty, tx + boxW, ty + boxH, 0xF0100010);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 410.0F);
        RenderSystem.disableDepthTest();
        int lineY = ty + 3;
        for (Component line : lines) {
            graphics.drawString(font, line, tx + 4, lineY, 0xFFFFFF);
            lineY += 10;
        }
        graphics.flush();
        graphics.pose().popPose();
    }

    private static int[] slotGui(CreativeModeInventoryScreen creative, Slot slot) {
        try {
            var left = AbstractContainerScreen.class.getDeclaredField("leftPos");
            var top = AbstractContainerScreen.class.getDeclaredField("topPos");
            left.setAccessible(true);
            top.setAccessible(true);
            return new int[] {(int) left.get(creative) + slot.x + 8, (int) top.get(creative) + slot.y + 8};
        } catch (ReflectiveOperationException e) {
            return new int[] {creative.width / 2, creative.height / 2};
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
        boolean png = rest.endsWith(".png");
        if (png) {
            rest = rest.substring(0, rest.length() - 4);
        }
        rest = rest.replaceAll("[^a-z0-9_\\-]", "_");
        return rest + ".png";
    }
}
