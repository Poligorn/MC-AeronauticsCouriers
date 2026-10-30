package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.client.route.RouteCanvas;
import dev.poligorn.aerocouriers.client.route.RouteProbes;
import dev.poligorn.aerocouriers.client.route.RouteScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import xaero.map.WorldMap;
import xaero.map.element.MapElementReader;
import xaero.map.element.MapElementRenderHandler;
import xaero.map.element.MapElementRenderProvider;
import xaero.map.element.MapElementRenderer;
import xaero.map.element.render.ElementRenderInfo;
import xaero.map.element.render.ElementRenderLocation;
import xaero.map.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;

/**
 * Route line on Xaero's World Map through its map element pipeline ({@code WorldMap.mapElementRenderHandler}, the
 * same list that holds Xaero's waypoint, tracked-player and claim renderers). Verified against World Map
 * 1.40.11–1.47.0; only loaded after {@code ModList.isLoaded("xaeroworldmap")}.
 */
final class XaeroRouteWorldMapLayer {
    private static final RouteProbes PROBES = new RouteProbes();
    private static final int CALIBRATION_GIVE_UP_FRAMES = 600;

    private static @Nullable Renderer renderer;
    private static @Nullable MapElementRenderHandler registeredIn;
    private static @Nullable Vec3 lastCamera;
    private static long lastRenderMillis;

    private XaeroRouteWorldMapLayer() {
    }

    /** @return {@code true} once our renderer sits in the current handler */
    static boolean ensureRegistered() {
        MapElementRenderHandler handler = WorldMap.mapElementRenderHandler;
        if (handler == null) {
            return false;
        }
        if (handler == registeredIn) {
            return true;
        }
        if (renderer == null) {
            renderer = new Renderer();
        }
        handler.add(renderer);
        registeredIn = handler;
        return true;
    }

    /** Wall-clock time of the last frame our renderer was invoked by the World Map. */
    static long lastRenderMillis() {
        return lastRenderMillis;
    }

    private static int worldMapIndex() {
        return ElementRenderLocation.WORLD_MAP.getIndex();
    }

    private static void render(Probe probe, double partialX, double partialY, ElementRenderInfo info,
                               GuiGraphics graphics) {
        lastRenderMillis = System.currentTimeMillis();
        if (info.renderPos != null) {
            lastCamera = info.renderPos;
        }
        if (!PROBES.record(probe.kind, graphics.pose().last().pose(), partialX, partialY, false)) {
            return;
        }
        RouteProbes.Projection projection = PROBES.projection();
        if (projection == null) {
            if (PROBES.failedFrames() > CALIBRATION_GIVE_UP_FRAMES) {
                XaeroRouteHooks.reportFailure(true, "World Map element pose is not affine");
            }
            return;
        }
        Vec3 player = info.renderEntityPos != null ? info.renderEntityPos : playerPos();
        if (player == null || !RouteScene.hasContent(info.mapDimension, true)) {
            return;
        }
        RouteScene.draw(new RouteCanvas(graphics, projection, PROBES.unit()), Minecraft.getInstance().font, true,
                player.x, player.z);
    }

    private static @Nullable Vec3 playerPos() {
        Entity entity = Minecraft.getInstance().getCameraEntity();
        return entity == null ? null : entity.position();
    }

    private static @Nullable ResourceKey<Level> currentDimension() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? null : minecraft.level.dimension();
    }

    /** Invisible probe element; its only job is to be positioned by Xaero. */
    static final class Probe {
        final int kind;

        Probe(int kind) {
            this.kind = kind;
        }
    }

    static final class Context {
    }

    static final class Provider extends MapElementRenderProvider<Probe, Context> {
        private final Probe[] probes = new Probe[RouteProbes.COUNT];
        private int next = RouteProbes.COUNT;

        Provider() {
            for (int i = 0; i < probes.length; i++) {
                probes[i] = new Probe(i);
            }
        }

        @Override
        public void begin(int location, Context context) {
            next = RouteProbes.COUNT;
            if (location != worldMapIndex() || !XaeroRouteHooks.worldMapActive()
                    || !RouteScene.hasContent(currentDimension(), true)) {
                return;
            }
            Vec3 anchor = lastCamera != null ? lastCamera : playerPos();
            if (anchor == null) {
                return;
            }
            PROBES.begin(Math.floor(anchor.x), Math.floor(anchor.z));
            next = 0;
        }

        @Override
        public boolean hasNext(int location, Context context) {
            return next < RouteProbes.COUNT;
        }

        @Override
        public Probe getNext(int location, Context context) {
            return probes[next++];
        }

        @Override
        public void end(int location, Context context) {
            next = RouteProbes.COUNT;
        }
    }

    static final class Reader extends MapElementReader<Probe, Context, Renderer> {
        @Override
        public boolean isHidden(Probe element, Context context) {
            return false;
        }

        @Override
        public double getRenderX(Probe element, Context context, float partialTicks) {
            return PROBES.probeX(element.kind);
        }

        @Override
        public double getRenderZ(Probe element, Context context, float partialTicks) {
            return PROBES.probeZ(element.kind);
        }

        @Override
        public int getInteractionBoxLeft(Probe element, Context context, float partialTicks) {
            return 0;
        }

        @Override
        public int getInteractionBoxRight(Probe element, Context context, float partialTicks) {
            return 0;
        }

        @Override
        public int getInteractionBoxTop(Probe element, Context context, float partialTicks) {
            return 0;
        }

        @Override
        public int getInteractionBoxBottom(Probe element, Context context, float partialTicks) {
            return 0;
        }

        @Override
        public int getRenderBoxLeft(Probe element, Context context, float partialTicks) {
            return -2;
        }

        @Override
        public int getRenderBoxRight(Probe element, Context context, float partialTicks) {
            return 2;
        }

        @Override
        public int getRenderBoxTop(Probe element, Context context, float partialTicks) {
            return -2;
        }

        @Override
        public int getRenderBoxBottom(Probe element, Context context, float partialTicks) {
            return 2;
        }

        @Override
        public int getLeftSideLength(Probe element, Minecraft minecraft) {
            return 0;
        }

        @Override
        public String getMenuName(Probe element) {
            return "";
        }

        @Override
        public String getFilterName(Probe element) {
            return "";
        }

        @Override
        public int getMenuTextFillLeftPadding(Probe element) {
            return 0;
        }

        @Override
        public int getRightClickTitleBackgroundColor(Probe element) {
            return 0;
        }

        @Override
        public boolean shouldScaleBoxWithOptionalScale() {
            return false;
        }

        @Override
        public boolean isInteractable(int location, Probe element) {
            return false;
        }

        @Override
        public boolean isInteractable(ElementRenderLocation location, Probe element) {
            return false;
        }

        @Override
        public boolean isHoveredOnMap(int location, Probe element, double mouseX, double mouseZ, double scale,
                                      double screenSizeBasedScale, double rendererDimDiv, Context context,
                                      float partialTicks) {
            return false;
        }

        @Override
        public boolean isHoveredOnMap(ElementRenderLocation location, Probe element, double mouseX, double mouseZ,
                                      double scale, double screenSizeBasedScale, double rendererDimDiv,
                                      Context context, float partialTicks) {
            return false;
        }

        @Override
        public boolean isOnScreen(Probe element, double cameraX, double cameraZ, int width, int height, double scale,
                                  double screenSizeBasedScale, double rendererDimDiv, Context context,
                                  float partialTicks) {
            return true;
        }

        @Override
        public boolean isRightClickValid(Probe element) {
            return false;
        }
    }

    static final class Renderer extends MapElementRenderer<Probe, Context, Renderer> {
        Renderer() {
            super(new Context(), new Provider(), new Reader());
        }

        @Override
        public boolean shouldRender(ElementRenderLocation location, boolean shadow) {
            return location.getIndex() == worldMapIndex();
        }

        @Override
        public boolean shouldRender(int location, boolean shadow) {
            return location == worldMapIndex();
        }

        @Override
        public void preRender(ElementRenderInfo info, MultiBufferSource.BufferSource buffers,
                              MultiTextureRenderTypeRendererProvider rendererProvider, boolean shadow) {
            if (info.renderPos != null) {
                lastCamera = info.renderPos;
            }
        }

        @Override
        public void postRender(ElementRenderInfo info, MultiBufferSource.BufferSource buffers,
                               MultiTextureRenderTypeRendererProvider rendererProvider, boolean shadow) {
        }

        @Override
        public void renderElementShadow(Probe element, boolean hovered, float optionalScale, double partialX,
                                        double partialY, ElementRenderInfo info, GuiGraphics graphics,
                                        MultiBufferSource.BufferSource buffers,
                                        MultiTextureRenderTypeRendererProvider rendererProvider) {
        }

        @Override
        public boolean renderElement(Probe element, boolean hovered, double optionalDepth, float optionalScale,
                                     double partialX, double partialY, ElementRenderInfo info,
                                     GuiGraphics graphics, MultiBufferSource.BufferSource buffers,
                                     MultiTextureRenderTypeRendererProvider rendererProvider) {
            try {
                render(element, partialX, partialY, info, graphics);
            } catch (Throwable t) {
                XaeroRouteHooks.reportFailure(true, t.toString());
            }
            return false;
        }

        @Override
        public void beforeRender(int location, Minecraft minecraft, GuiGraphics graphics, double cameraX,
                                 double cameraZ, double mouseX, double mouseZ, float brightness, double scale,
                                 double screenSizeBasedScale, TextureManager textureManager, Font font,
                                 MultiBufferSource.BufferSource buffers,
                                 MultiTextureRenderTypeRendererProvider rendererProvider, boolean shadow) {
        }

        @Override
        public void afterRender(int location, Minecraft minecraft, GuiGraphics graphics, double cameraX,
                                double cameraZ, double mouseX, double mouseZ, float brightness, double scale,
                                double screenSizeBasedScale, TextureManager textureManager, Font font,
                                MultiBufferSource.BufferSource buffers,
                                MultiTextureRenderTypeRendererProvider rendererProvider, boolean shadow) {
        }

        @Override
        public void renderElementPre(int location, Probe element, boolean hovered, Minecraft minecraft,
                                     GuiGraphics graphics, double cameraX, double cameraZ, double mouseX,
                                     double mouseZ, float brightness, double scale, double screenSizeBasedScale,
                                     TextureManager textureManager, Font font,
                                     MultiBufferSource.BufferSource buffers,
                                     MultiTextureRenderTypeRendererProvider rendererProvider, float optionalScale,
                                     double partialX, double partialY, boolean cave, float partialTicks) {
        }

        @Override
        public boolean renderElement(int location, Probe element, boolean hovered, Minecraft minecraft,
                                     GuiGraphics graphics, double cameraX, double cameraZ, double mouseX,
                                     double mouseZ, float brightness, double scale, double screenSizeBasedScale,
                                     TextureManager textureManager, Font font,
                                     MultiBufferSource.BufferSource buffers,
                                     MultiTextureRenderTypeRendererProvider rendererProvider, int elementIndex,
                                     double optionalDepth, float optionalScale, double partialX, double partialY,
                                     boolean cave, float partialTicks) {
            return false;
        }
    }
}
