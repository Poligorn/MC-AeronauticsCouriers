package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import dev.poligorn.aerocouriers.net.ZoneSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/**
 * Outlines the courier's own pickup/delivery zones with dust particles (GDD §10.3): the horizontal circle through
 * the zone point and two vertical circles, i.e. the sphere the server checks with {@code Destination.contains}.
 */
final class ZoneParticles {
    private static final int INTERVAL_TICKS = 10;
    private static final int MAX_PER_CIRCLE = 720;
    private static final DustParticleOptions PICKUP = new DustParticleOptions(new Vector3f(1.0f, 0.75f, 0.1f), 1.5f);
    private static final DustParticleOptions DELIVERY = new DustParticleOptions(new Vector3f(0.2f, 0.9f, 0.3f), 1.5f);

    private static int ticks;

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused() || ++ticks % INTERVAL_TICKS != 0
                || !CouriersClientConfig.ZONES_ENABLED.get()) {
            return;
        }
        double density = CouriersClientConfig.ZONES_PARTICLE_DENSITY.get();
        int renderDistance = CouriersClientConfig.ZONES_RENDER_DISTANCE.get();
        Vec3 eye = mc.player.getEyePosition();
        for (ZoneSyncPayload.Zone zone : ClientZones.get()) {
            if (!zone.dimension().equals(level.dimension())) {
                continue;
            }
            Vec3 center = Vec3.atCenterOf(zone.pos());
            if (eye.distanceTo(center) - zone.radius() > renderDistance) {
                continue;
            }
            DustParticleOptions dust = zone.delivery() ? DELIVERY : PICKUP;
            int points = (int) Math.min(MAX_PER_CIRCLE, Math.max(8, Math.ceil(2 * Math.PI * zone.radius() * density)));
            double phase = (ticks / INTERVAL_TICKS) * 0.5 / points;
            for (int i = 0; i < points; i++) {
                double angle = 2 * Math.PI * ((double) i / points + phase);
                double cos = Math.cos(angle) * zone.radius();
                double sin = Math.sin(angle) * zone.radius();
                level.addAlwaysVisibleParticle(dust, center.x + cos, center.y, center.z + sin, 0, 0, 0);
                level.addAlwaysVisibleParticle(dust, center.x + cos, center.y + sin, center.z, 0, 0, 0);
                level.addAlwaysVisibleParticle(dust, center.x, center.y + sin, center.z + cos, 0, 0, 0);
            }
        }
    }

    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientZones.clear();
    }

    private ZoneParticles() {
    }
}
