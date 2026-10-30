package dev.poligorn.aerocouriers.client.route;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.jetbrains.annotations.Nullable;

/**
 * Immediate-mode quads in the render space measured by {@link RouteProbes}. Uses its own vertex buffer so it never
 * collides with a batch Xaero may still be filling, and draws without depth test or culling because the map
 * framebuffer's depth and winding conventions are Xaero's business.
 */
public final class RouteCanvas {
    private static @Nullable ByteBufferBuilder buffer;

    private final GuiGraphics graphics;
    private final RouteProbes.@Nullable Projection projection;
    private final float unit;
    private final float depth;
    private @Nullable BufferBuilder builder;

    public RouteCanvas(GuiGraphics graphics, RouteProbes.Projection projection, float unit) {
        this(graphics, projection, unit, (float) projection.depth());
    }

    private RouteCanvas(GuiGraphics graphics, RouteProbes.@Nullable Projection projection, float unit, float depth) {
        this.graphics = graphics;
        this.projection = projection;
        this.unit = unit;
        this.depth = depth;
    }

    /** Canvas in plain render space (no world projection), e.g. HUD overlays drawn at a known point. */
    public static RouteCanvas flat(GuiGraphics graphics, float unit, float depth) {
        return new RouteCanvas(graphics, null, unit, depth);
    }

    public RouteProbes.Projection projection() {
        if (projection == null) {
            throw new IllegalStateException("flat canvas has no world projection");
        }
        return projection;
    }

    /** Render-space length of one icon pixel of the map. */
    public float unit() {
        return unit;
    }

    public double sx(double worldX, double worldZ) {
        return projection().screenX(worldX, worldZ);
    }

    public double sy(double worldX, double worldZ) {
        return projection().screenY(worldX, worldZ);
    }

    private BufferBuilder builder() {
        if (builder == null) {
            if (buffer == null) {
                buffer = new ByteBufferBuilder(1 << 16);
            }
            builder = new BufferBuilder(buffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        }
        return builder;
    }

    public void quad(double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4,
                     int argb) {
        BufferBuilder b = builder();
        b.addVertex((float) x1, (float) y1, depth).setColor(argb);
        b.addVertex((float) x2, (float) y2, depth).setColor(argb);
        b.addVertex((float) x3, (float) y3, depth).setColor(argb);
        b.addVertex((float) x4, (float) y4, depth).setColor(argb);
    }

    public void rect(double x0, double y0, double x1, double y1, int argb) {
        quad(x0, y0, x0, y1, x1, y1, x1, y0, argb);
    }

    /** Screen-space segment of the given full width. */
    public void segment(double ax, double ay, double bx, double by, double width, int argb) {
        double dx = bx - ax;
        double dy = by - ay;
        double len = Math.hypot(dx, dy);
        if (len < 1e-3) {
            return;
        }
        double nx = -dy / len * width / 2;
        double ny = dx / len * width / 2;
        quad(ax - nx, ay - ny, ax + nx, ay + ny, bx + nx, by + ny, bx - nx, by - ny, argb);
    }

    /** Dashed screen-space segment; {@code phase} keeps dashes continuous across polyline vertices. */
    public double dashed(double ax, double ay, double bx, double by, double width, double dash, double gap,
                         double phase, int argb) {
        double len = Math.hypot(bx - ax, by - ay);
        if (len < 1e-3) {
            return phase;
        }
        double period = dash + gap;
        double t = -phase;
        while (t < len) {
            double s = Math.max(0, t);
            double e = Math.min(len, t + dash);
            if (e > s) {
                segment(ax + (bx - ax) * s / len, ay + (by - ay) * s / len,
                        ax + (bx - ax) * e / len, ay + (by - ay) * e / len, width, argb);
            }
            t += period;
        }
        return (phase + len) % period;
    }

    /** Chevron pointing from a to b, centred at (cx, cy). */
    public void chevron(double cx, double cy, double dirX, double dirY, double size, double width, int argb) {
        double len = Math.hypot(dirX, dirY);
        if (len < 1e-6) {
            return;
        }
        double ux = dirX / len;
        double uy = dirY / len;
        double tipX = cx + ux * size / 2;
        double tipY = cy + uy * size / 2;
        double backX = cx - ux * size / 2;
        double backY = cy - uy * size / 2;
        segment(backX - uy * size / 2, backY + ux * size / 2, tipX, tipY, width, argb);
        segment(backX + uy * size / 2, backY - ux * size / 2, tipX, tipY, width, argb);
    }

    public void ring(double cx, double cy, double radius, double width, int argb) {
        int sides = Math.max(16, Math.min(64, (int) (radius / 2)));
        double prevX = cx + radius;
        double prevY = cy;
        for (int i = 1; i <= sides; i++) {
            double a = Math.PI * 2 * i / sides;
            double x = cx + Math.cos(a) * radius;
            double y = cy + Math.sin(a) * radius;
            segment(prevX, prevY, x, y, width, argb);
            prevX = x;
            prevY = y;
        }
    }

    public void flush() {
        if (builder == null) {
            return;
        }
        MeshData mesh = builder.build();
        builder = null;
        if (mesh == null) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    /** Text at a render-space point, scaled like Xaero's own waypoint labels. */
    public void text(Font font, String text, double x, double y, int argb) {
        text(font, text, x, y, argb, true);
    }

    public void text(Font font, String text, double x, double y, int argb, boolean shadow) {
        flush();
        graphics.pose().pushPose();
        graphics.pose().setIdentity();
        graphics.pose().translate(x, y, depth + 1);
        graphics.pose().scale(unit, unit, 1);
        graphics.drawString(font, text, 0, 0, argb, shadow);
        graphics.flush();
        graphics.pose().popPose();
    }
}
