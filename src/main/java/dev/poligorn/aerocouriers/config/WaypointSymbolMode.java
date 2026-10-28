package dev.poligorn.aerocouriers.config;

/** How order waypoints appear on Xaero's minimap (GDD §10.3). */
public enum WaypointSymbolMode {
    /** Unicode crate glyph (📦) for all order waypoints — Xaero renders it like a built-in icon. */
    ICON,
    FIRST_LETTER,
    CUSTOM
}
