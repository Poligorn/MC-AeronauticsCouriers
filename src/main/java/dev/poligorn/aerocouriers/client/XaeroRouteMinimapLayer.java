package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.client.route.RouteCanvas;
import dev.poligorn.aerocouriers.client.route.RouteProbes;
import dev.poligorn.aerocouriers.client.route.RouteScene;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import xaero.common.HudMod;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.common.minimap.render.MinimapFBORenderer;
import xaero.hud.minimap.Minimap;
import xaero.hud.minimap.element.render.MinimapElementReader;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.element.render.MinimapElementRenderLocation;
import xaero.hud.minimap.element.render.MinimapElementRenderProvider;
import xaero.hud.minimap.element.render.MinimapElementRenderer;
import xaero.hud.minimap.element.render.map.MinimapElementMapRendererHandler;

/**
 * Route line inside Xaero's minimap. Registers into the handler that draws elements into the minimap framebuffer
 * ({@code IN_MINIMAP}, the radar's handler) so the line is clipped by the minimap shape and rotates with it. That
 * handler has no public getter: it is the only {@link MinimapElementMapRendererHandler} field of
 * {@link MinimapFBORenderer}, found by type, not by name. Verified against Minimap 25.3.13–26.6.0; safe mode
 * (no framebuffer) gets no line.
 */
final class XaeroRouteMinimapLayer {
    private static final RouteProbes PROBES = new RouteProbes();
    private static final int CALIBRATION_GIVE_UP_FRAMES = 600;

    private static @Nullable Renderer renderer;
    private static @Nullable MinimapElementMapRendererHandler registeredIn;
    private static @Nullable Field handlerField;

    private XaeroRouteMinimapLayer() {
    }

    /** @return {@code true} once our renderer sits in the current minimap framebuffer handler */
    static boolean ensureRegistered() throws ReflectiveOperationException {
        if (HudMod.INSTANCE == null) {
            return false;
        }
        Minimap minimap = HudMod.INSTANCE.getMinimap();
        if (minimap == null) {
            return false;
        }
        MinimapFBORenderer fbo = minimap.getMinimapFBORenderer();
        if (fbo == null) {
            return false;
        }
        if (handlerField == null) {
            handlerField = findHandlerField(fbo.getClass());
        }
        MinimapElementMapRendererHandler handler = (MinimapElementMapRendererHandler) handlerField.get(fbo);
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

    private static Field findHandlerField(Class<?> type) throws NoSuchFieldException {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (MinimapElementMapRendererHandler.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        throw new NoSuchFieldException("MinimapElementMapRendererHandler in " + type.getName());
    }

    private static boolean inMinimap(MinimapElementRenderLocation location) {
        return location.getIndex() == MinimapElementRenderLocation.IN_MINIMAP.getIndex();
    }

    private static void render(Probe probe, boolean outOfBounds, double partialX, double partialY,
                               MinimapElementRenderInfo info, GuiGraphics graphics) {
        if (!PROBES.record(probe.kind, graphics.pose().last().pose(), partialX, partialY, outOfBounds)) {
            return;
        }
        RouteProbes.Projection projection = PROBES.projection();
        if (projection == null) {
            if (PROBES.failedFrames() > CALIBRATION_GIVE_UP_FRAMES) {
                XaeroRouteHooks.reportFailure(false, "minimap element pose is not affine");
            }
            return;
        }
        Vec3 player = info.renderEntityPos != null ? info.renderEntityPos : playerPos();
        if (player == null || !RouteScene.hasContent(info.mapDimension, false)) {
            return;
        }
        RouteScene.draw(new RouteCanvas(graphics, projection, PROBES.unit()), null, false, player.x, player.z);
    }

    private static @Nullable Vec3 playerPos() {
        Entity entity = Minecraft.getInstance().getCameraEntity();
        return entity == null ? null : entity.position();
    }

    private static @Nullable ResourceKey<Level> currentDimension() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? null : minecraft.level.dimension();
    }

    static final class Probe {
        final int kind;

        Probe(int kind) {
            this.kind = kind;
        }
    }

    static final class Context {
    }

    static final class Provider extends MinimapElementRenderProvider<Probe, Context> {
        private final Probe[] probes = new Probe[RouteProbes.COUNT];
        private int next = RouteProbes.COUNT;

        Provider() {
            for (int i = 0; i < probes.length; i++) {
                probes[i] = new Probe(i);
            }
        }

        @Override
        public void begin(MinimapElementRenderLocation location, Context context) {
            next = RouteProbes.COUNT;
            if (!inMinimap(location) || !XaeroRouteHooks.minimapActive()
                    || !RouteScene.hasContent(currentDimension(), false)) {
                return;
            }
            Vec3 anchor = playerPos();
            if (anchor == null) {
                return;
            }
            PROBES.begin(Math.floor(anchor.x), Math.floor(anchor.z));
            next = 0;
        }

        @Override
        public boolean hasNext(MinimapElementRenderLocation location, Context context) {
            return next < RouteProbes.COUNT;
        }

        @Override
        public Probe getNext(MinimapElementRenderLocation location, Context context) {
            return probes[next++];
        }

        @Override
        public void end(MinimapElementRenderLocation location, Context context) {
            next = RouteProbes.COUNT;
        }
    }

    static final class Reader extends MinimapElementReader<Probe, Context> {
        @Override
        public boolean isHidden(Probe element, Context context) {
            return false;
        }

        @Override
        public double getRenderX(Probe element, Context context, float partialTicks) {
            return PROBES.probeX(element.kind);
        }

        @Override
        public double getRenderY(Probe element, Context context, float partialTicks) {
            Entity entity = Minecraft.getInstance().getCameraEntity();
            return entity == null ? 64 : entity.getY();
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
        public boolean isInteractable(MinimapElementRenderLocation location, Probe element) {
            return false;
        }
    }

    static final class Renderer extends MinimapElementRenderer<Probe, Context> {
        Renderer() {
            super(new Reader(), new Provider(), new Context());
        }

        @Override
        public boolean shouldRender(MinimapElementRenderLocation location) {
            return inMinimap(location);
        }

        @Override
        public void preRender(MinimapElementRenderInfo info, MultiBufferSource.BufferSource buffers,
                              MultiTextureRenderTypeRendererProvider rendererProvider) {
        }

        @Override
        public void postRender(MinimapElementRenderInfo info, MultiBufferSource.BufferSource buffers,
                               MultiTextureRenderTypeRendererProvider rendererProvider) {
        }

        @Override
        public boolean renderElement(Probe element, boolean highlighted, boolean outOfBounds, double optionalDepth,
                                     float optionalScale, double partialX, double partialY,
                                     MinimapElementRenderInfo info, GuiGraphics graphics,
                                     MultiBufferSource.BufferSource buffers) {
            try {
                render(element, outOfBounds, partialX, partialY, info, graphics);
            } catch (Throwable t) {
                XaeroRouteHooks.reportFailure(false, t.toString());
            }
            return false;
        }
    }
}
