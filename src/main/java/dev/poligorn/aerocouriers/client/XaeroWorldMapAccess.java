package dev.poligorn.aerocouriers.client;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import xaero.map.WorldMapSession;
import xaero.map.gui.GuiMap;

/**
 * All World Map 1.47 types live here. Loaded only after {@code ModList.isLoaded("xaeroworldmap")}; must not reference
 * minimap types — World Map can be installed alone.
 * Opening the map is the same path as Xaero's own M-key: {@code new GuiMap(null, null, processor, camera)}.
 */
final class XaeroWorldMapAccess {
    private static final Field CAMERA_X;
    private static final Field CAMERA_Z;
    private static final Field SHOULD_RESET;

    static {
        Field cameraX = null;
        Field cameraZ = null;
        Field shouldReset = null;
        try {
            cameraX = GuiMap.class.getDeclaredField("cameraX");
            cameraZ = GuiMap.class.getDeclaredField("cameraZ");
            shouldReset = GuiMap.class.getDeclaredField("shouldResetCameraPos");
            cameraX.setAccessible(true);
            cameraZ.setAccessible(true);
            shouldReset.setAccessible(true);
        } catch (Throwable ignored) {
            cameraX = null;
            cameraZ = null;
            shouldReset = null;
        }
        CAMERA_X = cameraX;
        CAMERA_Z = cameraZ;
        SHOULD_RESET = shouldReset;
    }

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

    /** @return whether the map opened and its camera was centred on {@code pos} */
    static boolean openWorldMapAt(BlockPos pos) {
        WorldMapSession session = WorldMapSession.getCurrentSession();
        if (session == null || !session.isUsable()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCameraEntity() == null) {
            return false;
        }
        GuiMap map = new GuiMap(null, null, session.getMapProcessor(), minecraft.getCameraEntity());
        boolean centered = centerGuiMap(map, pos.getX() + 0.5, pos.getZ() + 0.5);
        minecraft.setScreen(map);
        return minecraft.screen instanceof GuiMap && centered;
    }

    private static boolean centerGuiMap(GuiMap map, double blockX, double blockZ) {
        if (CAMERA_X == null || CAMERA_Z == null || SHOULD_RESET == null) {
            return false;
        }
        try {
            CAMERA_X.setDouble(map, blockX);
            CAMERA_Z.setDouble(map, blockZ);
            SHOULD_RESET.setBoolean(map, false);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
