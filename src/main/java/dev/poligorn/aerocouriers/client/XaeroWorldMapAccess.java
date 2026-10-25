package dev.poligorn.aerocouriers.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import xaero.map.WorldMapSession;
import xaero.map.gui.GuiMap;

/**
 * All World Map 1.47 types live here. Loaded only after {@code ModList.isLoaded("xaeroworldmap")}.
 * Opening the map is the same path as Xaero's own M-key: {@code new GuiMap(null, null, processor, camera)}.
 */
final class XaeroWorldMapAccess {
    private XaeroWorldMapAccess() {
    }

    static boolean isUsable() {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        return session != null && session.isUsable() && session.getMapProcessor() != null;
    }

    static boolean openWorldMap() {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null || !session.isUsable()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Entity camera = minecraft.getCameraEntity();
        if (camera == null) {
            return false;
        }
        // Parent stays null (Xaero's own keybind does the same) so the map does not try to render
        // our stamp underneath. XaeroMapPicker restores the stamp instance when the map closes.
        minecraft.setScreen(new GuiMap(null, null, session.getMapProcessor(), camera));
        return minecraft.screen instanceof GuiMap;
    }
}
