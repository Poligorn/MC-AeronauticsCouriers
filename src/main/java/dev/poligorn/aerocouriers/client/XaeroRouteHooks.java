package dev.poligorn.aerocouriers.client;

import com.mojang.logging.LogUtils;
import dev.poligorn.aerocouriers.client.board.BoardDimensionMarker;
import dev.poligorn.aerocouriers.client.route.RoutePreview;
import dev.poligorn.aerocouriers.client.route.RouteTracker;
import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Route line on Xaero's maps (README "Маршрут на картах Xaero"). No Xaero types here: the layer classes are only
 * touched after {@link ModList#isLoaded} and every call is wrapped, so a missing or changed Xaero never crashes.
 * If a render hook cannot be registered or misbehaves at runtime, it is switched off for the session and the route
 * falls back to a chain of temporary Xaero waypoints.
 */
public final class XaeroRouteHooks {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** In-world ticks to wait for Xaero's handlers before treating the hook as unavailable. */
    private static final int REGISTER_TIMEOUT_TICKS = 20 * 60;
    private static final int PREVIEW_OPEN_TIMEOUT_TICKS = 40;

    public enum Status {
        NO_XAERO,
        WAITING,
        LINE,
        FALLBACK
    }

    private static boolean worldMapReady;
    private static boolean minimapReady;
    private static boolean worldMapFailed;
    private static boolean minimapFailed;
    private static @Nullable String failure;
    private static int inWorldTicks;

    private static @Nullable Screen previewReturn;
    private static boolean previewSawMap;
    private static int previewTicks;

    private XaeroRouteHooks() {
    }

    public static boolean minimapLoaded() {
        return ModList.get().isLoaded(XaeroMapPicker.MINIMAP_ID);
    }

    public static boolean worldMapLoaded() {
        return ModList.get().isLoaded(XaeroMapPicker.WORLDMAP_ID);
    }

    public static Status status() {
        if (!minimapLoaded() && !worldMapLoaded()) {
            return Status.NO_XAERO;
        }
        if (fallbackActive()) {
            return Status.FALLBACK;
        }
        return worldMapReady || minimapReady ? Status.LINE : Status.WAITING;
    }

    public static @Nullable String failure() {
        return failure;
    }

    /** "Minimap 26.6.0, World Map 1.47.0" for the settings status line. */
    public static String versions() {
        StringBuilder out = new StringBuilder();
        appendVersion(out, XaeroMapPicker.MINIMAP_ID, "Minimap");
        appendVersion(out, XaeroMapPicker.WORLDMAP_ID, "World Map");
        return out.toString();
    }

    private static void appendVersion(StringBuilder out, String modId, String name) {
        ModList.get().getModContainerById(modId).ifPresent(container -> {
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append(name).append(' ').append(container.getModInfo().getVersion());
        });
    }

    static boolean worldMapActive() {
        return !worldMapFailed;
    }

    static boolean minimapActive() {
        return !minimapFailed;
    }

    private static boolean fallbackActive() {
        return (minimapLoaded() && minimapFailed) || (worldMapLoaded() && worldMapFailed);
    }

    /** Called by a layer when its hook throws or never yields a consistent projection. */
    static void reportFailure(boolean worldMap, String reason) {
        if (worldMap ? worldMapFailed : minimapFailed) {
            return;
        }
        if (worldMap) {
            worldMapFailed = true;
        } else {
            minimapFailed = true;
        }
        failure = (worldMap ? "World Map: " : "Minimap: ") + reason;
        LOGGER.warn("Aeronautics Couriers: route line on Xaero's {} disabled, using waypoint fallback ({})",
                worldMap ? "World Map" : "minimap", reason);
    }

    static void onClientTick(ClientTickEvent.Post event) {
        RouteTracker.tick();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            inWorldTicks = 0;
            return;
        }
        inWorldTicks++;
        if (worldMapLoaded() && !worldMapFailed) {
            try {
                worldMapReady = XaeroRouteWorldMapLayer.ensureRegistered();
            } catch (Throwable t) {
                reportFailure(true, t.toString());
            }
            if (!worldMapReady && inWorldTicks > REGISTER_TIMEOUT_TICKS) {
                reportFailure(true, "element handler not found");
            }
        }
        if (minimapLoaded() && !minimapFailed) {
            try {
                minimapReady = XaeroRouteMinimapLayer.ensureRegistered();
            } catch (Throwable t) {
                reportFailure(false, t.toString());
            }
            if (!minimapReady && inWorldTicks > REGISTER_TIMEOUT_TICKS) {
                reportFailure(false, "framebuffer element handler not found");
            }
        }
        if (minimapLoaded()) {
            try {
                XaeroRouteBreadcrumbs.sync(fallbackActive()
                        && CouriersClientConfig.get(CouriersClientConfig.ROUTE_ENABLED));
            } catch (Throwable ignored) {
            }
        }
        tickPreview(minecraft);
    }

    public static void onLogout() {
        RouteTracker.clear();
        RoutePreview.clear();
        previewReturn = null;
        inWorldTicks = 0;
        if (minimapLoaded()) {
            try {
                XaeroRouteBreadcrumbs.clear();
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean canPreview() {
        if (!worldMapLoaded() || worldMapFailed
                || !CouriersClientConfig.get(CouriersClientConfig.ROUTE_WAYBILL_PREVIEW)) {
            return false;
        }
        try {
            return XaeroWorldMapAccess.isUsable();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Opens the World Map with a dashed A→B preview; {@code returnTo} is restored when the map closes. */
    public static boolean showPreview(RoutePreview.Preview preview, @Nullable Screen returnTo) {
        if (!canPreview()) {
            return false;
        }
        RoutePreview.show(preview);
        BlockPos mid = new BlockPos((preview.from().getX() + preview.to().getX()) / 2, preview.to().getY(),
                (preview.from().getZ() + preview.to().getZ()) / 2);
        if (!XaeroOrderBridge.openWorldMapAt(mid) && !isWorldMap(Minecraft.getInstance().screen)) {
            RoutePreview.clear();
            return false;
        }
        previewReturn = returnTo;
        previewSawMap = false;
        previewTicks = 0;
        return true;
    }

    /** Opens the World Map on the tracked target of an active haul and makes it the tracked order. */
    public static boolean showActiveHaul(java.util.UUID orderId, BlockPos target) {
        RouteTracker.track(orderId);
        return worldMapLoaded() && XaeroOrderBridge.openWorldMapAt(target);
    }

    private static void tickPreview(Minecraft minecraft) {
        if (RoutePreview.active() == null) {
            return;
        }
        previewTicks++;
        if (isXaeroScreen(minecraft.screen)) {
            previewSawMap = true;
            return;
        }
        if (!previewSawMap && previewTicks < PREVIEW_OPEN_TIMEOUT_TICKS) {
            return;
        }
        RoutePreview.clear();
        Screen back = previewReturn;
        previewReturn = null;
        if (back != null && minecraft.screen == null) {
            minecraft.setScreen(back);
        }
    }

    /** Preview and other-dimension banners on top of the World Map screen (plain NeoForge screen event). */
    static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!isWorldMap(event.getScreen())) {
            return;
        }
        Component banner = banner();
        if (banner == null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int w = font.width(banner) + 12;
        int x = (event.getScreen().width - w) / 2;
        int y = 24;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(x - 1, y - 1, x + w + 1, y + font.lineHeight + 7, 0xFF8899AA);
        graphics.fill(x, y, x + w, y + font.lineHeight + 6, 0xE0101820);
        graphics.drawString(font, banner, x + 6, y + 4, 0xFFFFFFFF, false);
        graphics.pose().popPose();
    }

    private static @Nullable Component banner() {
        if (RoutePreview.active() != null) {
            return Component.translatable("ui.aerocouriers.route.preview_banner");
        }
        RouteTracker.RouteState state = RouteTracker.state();
        Minecraft minecraft = Minecraft.getInstance();
        if (state == null || !state.otherDimension() || minecraft.level == null
                || !CouriersClientConfig.get(CouriersClientConfig.ROUTE_WORLD_MAP)) {
            return null;
        }
        return Component.translatable("ui.aerocouriers.route.other_dimension", state.delivery() ? "B" : "A",
                BoardDimensionMarker.shortDimensionName(state.dimension()),
                BoardDimensionMarker.shortDimensionName(minecraft.level.dimension()));
    }

    private static boolean isWorldMap(@Nullable Screen screen) {
        return screen != null && screen.getClass().getName().equals("xaero.map.gui.GuiMap");
    }

    private static boolean isXaeroScreen(@Nullable Screen screen) {
        return screen != null && screen.getClass().getName().startsWith("xaero.");
    }
}
