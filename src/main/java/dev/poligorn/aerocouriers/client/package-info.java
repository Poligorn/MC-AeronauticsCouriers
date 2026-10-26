/**
 * Client-only code (GDD §10, §14.1): {@link dev.poligorn.aerocouriers.client.CourierBoardScreen} (Create-styled chrome),
 * {@link dev.poligorn.aerocouriers.client.InventoryBoardButton} (position from {@code aerocouriers-client.toml}),
 * {@link dev.poligorn.aerocouriers.client.BoardKeyMappings} (J), zone particles, epaulette renderer.
 * Xaero map pick: {@link dev.poligorn.aerocouriers.client.XaeroMapPicker} (no Xaero types);
 * Minimap/World Map calls live in {@code XaeroMinimapAccess} / {@code XaeroWorldMapAccess}.
 * Navigation: {@link dev.poligorn.aerocouriers.client.navigation.NavRenderer} (beam + HUD);
 * order waypoints via {@link dev.poligorn.aerocouriers.client.XaeroOrderBridge} (see docs/research/xaero.md).
 */
package dev.poligorn.aerocouriers.client;
