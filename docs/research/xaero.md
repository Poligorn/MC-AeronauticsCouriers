# Xaero waypoints research for Aeronautics Couriers (2026-10-25)

## Versions checked
- Xaero's Minimap neoforge-1.21.1-26.6.0 (in MC-Ikaria pack), World Map 1.47.0. Also 25.3.13, 26.1.0–26.5.0 (javap).
- Create: Marketplace, MakotoPD/CreateMarketplace @ ba29747f3a3697d699dbbdb4943865b77fbb294d (main, 2026-09-29), GPL-3.0.
- Xaero licence: All Rights Reserved (mods.toml + Modrinth). No public API docs; there is an official maven:
  https://chocolateminecraft.com/maven  ->  xaero.minimap:xaerominimap-neoforge-1.21.1:26.6.0, xaero.map:xaeroworldmap-neoforge-1.21.1:1.47.0

## Create: Marketplace (how it does it)
- client/XaeroCompat.java: pure REFLECTION, no compile dep in code. Xaero jars are committed into libs/ (ARR redistribution; don't copy that) and pulled via `implementation fileTree('libs')`.
- Chain: xaero.common.XaeroMinimapSession.getCurrentSession() -> getWaypointsManager() (legacy WaypointsManager extends MinimapSession)
  -> getCurrentContainerID(), isMultiplayer(String), getNewAutoWorldID(ResourceKey,boolean), getWorld(String,String)/addWorld -> WaypointWorld
  -> getCurrentSet()/addSet("gui.xaero_default")/setCurrent -> legacy WaypointSet.getList().add(new Waypoint(x,y,z,name,symbol,1,0,false))
  -> getWorldManagerIO().saveWorld(MinimapWorld).
- temp = FALSE -> waypoints are PERMANENT and saved to disk; never removed by the mod. Dedup by x/y/z+name only.
- Called from GlobalMarketScreen.java:765 (Navigate button). Fallback = chat message with coords.

## Xaero 26.x native mechanisms (verified via javap + Vineflower)
1. Temporary waypoint: Waypoint(int,int,int,String name,String initials,WaypointColor,WaypointPurpose,boolean temp[,boolean yIncluded]).
   WaypointIO skips isTemporary() on save -> gone after relog. Lives in a normal set (user sees it in list).
   Purpose DESTINATION -> auto-removed when player within 4 blocks (after 5 s) (DestinationHandler).
2. Third-party waypoints (since 26.2.0, absent in 26.1.0/25.x): xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypointManager / ThirdPartyWaypoints.
   In-memory only, keyed by origin ResourceLocation + String id; add/remove/clearOrigin; setEnabledStateGetter.
   Xaero's own Waystones compat (xaero.hud.compat.mods.SupportWaystones) uses exactly this.
   Access: BuiltInHudModules.MINIMAP.getCurrentSession() -> getWorldManager().getAutoRootContainer()
     -> addSubContainer(root.getPath().resolve(session.getDimensionHelper().getDimensionDirectoryName(dimKey)))
     -> getThirdPartyWaypointManager().get(originId).add(id, waypoint)
   Shown on minimap/in-world AND on World Map 1.47.0 (SupportXaeroMinimap) — but only if current set is "gui.xaero_default" or "all sets" enabled.
   User deletion stored as isThirdPartyDeleted by id in root config -> use unique id per order.
- Legacy xaero.common.* classes still exist in 26.6.0 (Marketplace path still resolves), most ctors @Deprecated.

## Button placement (Create: Marketplace)
- MarketConfig.java (CLIENT spec, config/create_marketplace-client.toml, section [appearance]):
  buttonPosition = CORNER (default) | INVENTORY_SIDE | CUSTOM ; customButtonX / customButtonY (0..4000, default 2).
- ClientGameEvents.onScreenInit (ScreenEvent.Init.Post, any AbstractContainerScreen):
  EMI present -> x = 2 + MarketEmiCompat.getEmiOffset(), y = height-22 (config ignored);
  CORNER -> (2, height-22); INVENTORY_SIDE -> (guiLeft-22, guiTop+10); CUSTOM -> (customX, customY). event.addListener(button).

## Sketch (all names verified against 26.6.0 bytecode)
```java
// client-only; loaded only if ModList.get().isLoaded("xaerominimap") — fair-play modId "xaerominimapfair" NOT verified
// build.gradle: maven { url "https://chocolateminecraft.com/maven" }
//               compileOnly "xaero.minimap:xaerominimap-neoforge-1.21.1:26.6.0"   (do NOT ship/commit the jar)
public final class XaeroCourierBridge {
  static final ResourceLocation ORIGIN = ResourceLocation.fromNamespaceAndPath("aerocouriers", "orders");
  static ThirdPartyWaypoints bucket(ResourceKey<Level> dim) {
    MinimapSession s = BuiltInHudModules.MINIMAP.getCurrentSession();
    if (s == null) return null;
    MinimapWorldRootContainer root = s.getWorldManager().getAutoRootContainer();
    String node = s.getDimensionHelper().getDimensionDirectoryName(dim);
    MinimapWorldContainer c = root.addSubContainer(root.getPath().resolve(node));
    return c.getThirdPartyWaypointManager().get(ORIGIN);
  }
  public static void show(String orderId, ResourceKey<Level> dim, BlockPos p, String name) {
    var b = bucket(dim); if (b == null) return;
    b.add("order-" + orderId, new Waypoint(p.getX(), p.getY(), p.getZ(), name, "✉",
        WaypointColor.GOLD, WaypointPurpose.NORMAL, true));   // temp=true just in case
  }
  public static void hide(String orderId, ResourceKey<Level> dim) {
    var b = bucket(dim); if (b != null) b.remove("order-" + orderId);
  }
  public static void clearAll() { /* iterate root.getSubContainers() -> getThirdPartyWaypointManager().clearOrigin(ORIGIN) */ }
}
// Fallback for Xaero < 26.2.0: Waypoint(... , WaypointPurpose.DESTINATION, true) into
// s.getWorldManager().getCurrentWorld().getCurrentWaypointSet().add(wp)  (in-list temporary, auto-removed at <4 blocks)
```
