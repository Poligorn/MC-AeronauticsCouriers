# Xaero waypoints research for Aeronautics Couriers (2026-10-25)

## Versions checked
- Xaero's Minimap neoforge-1.21.1-26.6.0 (in MC-Ikaria pack), World Map 1.47.0. Also 25.3.13, 26.1.0–26.5.0 (javap).
- Create: Marketplace, MakotoPD/CreateMarketplace @ ba29747f3a3697d699dbbdb4943865b77fbb294d (main, 2026-09-29), GPL-3.0.
- Xaero licence: All Rights Reserved (mods.toml + Modrinth). No public API docs; there is an official maven:
  https://chocolateminecraft.com/maven  ->  xaero.minimap:xaerominimap-neoforge-1.21.1:26.6.0, xaero.map:xaeroworldmap-neoforge-1.21.1:1.47.0

## Waypoint “icons” (ICON mode)
Xaero has no separate third-party texture hook on `Waypoint` — the minimap marker is the **symbol / initials string** (`getSymbol()`, ctor arg). Mods (e.g. Create: Marketplace, GPL — studied for behaviour only) set `waypointSymbolMode = ICON` by passing a **Unicode emoji** (e.g. shopping basket) as that string; Xaero draws it like a built-in icon. Alternatives: `FIRST_LETTER` of the name, or any single character / short string (`CUSTOM`). `XaeroIcon` / atlases exist for Xaero’s own UI, not exposed for arbitrary third-party waypoint textures without unsupported hacks. Aeronautics Couriers: client config `[waypoints] waypointSymbolMode` + `customWaypointSymbol`; ICON mode uses 📦 for every order waypoint.

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

## Opening the map and picking a waypoint (stamp window, verified 26.6.0 / 1.47.0 javap)

No public callback or Forge event fires when the player adds a waypoint. ClientEvents / CommonEvents are lifecycle only. World Map `SupportXaeroMinimap.createWaypoint` just opens `GuiAddWaypoint` with the map as parent.

**Open World Map** (same path as the M-key, `ControlsHandler.keyDown`):
```
WorldMapSession s = WorldMapSession.getCurrentSession();
mc.setScreen(new xaero.map.gui.GuiMap(null, null, s.getMapProcessor(), mc.getCameraEntity()));
```
`WorldMapSession.isUsable()` must be true. Parent/escape stay null — Xaero does the same; passing our stamp as parent would make the map try to treat it as a Xaero `ScreenBase`.

**Fallback without World Map:** `new xaero.common.gui.GuiWaypoints(HudMod.INSTANCE, minimapSession, parent, escape)` — the same screen as the minimap waypoint-menu key (`WaypointMenuFunction`).

**Detect the new point:** snapshot `MinimapWorld.getIterableWaypointSets()` → `WaypointSet.getWaypoints()` (skip `isThirdParty`, `isDeath`, `isEffectivelyDeleted`) before opening; after every Xaero screen is gone (`screen.getClass().getName().startsWith("xaero.")` is false), take the waypoint whose `identityHashCode` was not in the snapshot (newest `getCreatedAt()` if several). Restore the same `StampCreateScreen` instance so typed fields survive.

**Existing waypoints:** no “last selected” API. Dropdown on the stamp form lists the current world’s player waypoints.

**Y missing:** `Waypoint.isYIncluded() == false` or `getY() == 32767` (sentinel in `SupportXaeroMinimap.createTempWaypoint`). Rule: use the local player’s block Y; if there is no player, 64. The form shows a notice and Y stays editable.

**Older Xaero:** `try/catch` `NoSuchMethodError` / `NoClassDefFoundError` / `LinkageError` around every access class; the button is then hidden. compileOnly also needs `xaero.lib:xaerolib-neoforge-1.21.1:1.7.3` (transitive of both jars; Gradle `compileOnly` is not transitive).

**mod IDs:** `xaerominimap`, `xaeroworldmap`. ⚠️ `xaerominimapfair` still unverified.

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

## Route line on the maps (2026-10-30, javap signatures only, no decompilation)
Goal: draw our own A/B route polyline on World Map and inside the minimap (not Xaero "Drawings"), stay alive across Xaero updates, never crash without Xaero.

### What Xaero exposes (public, non-final, used by Xaero itself)
- **World Map:** `xaero.map.WorldMap.mapElementRenderHandler` (public static) → `MapElementRenderHandler.add(ElementRenderer<?,?,?>)`.
  Base classes `xaero.map.element.MapElementRenderer` / `MapElementReader` / `MapElementRenderProvider`; Xaero's own `WaypointRenderer`, `PlayerTrackerMapElementRenderer` and the OPAC claims renderer extend them. `renderElement(...)` gets `GuiGraphics` with the map pose already applied plus `partialX/partialY`; `MapElementGraphics`/`ElementRenderInfo` carry `renderPos` (camera).
- **Minimap:** `HudMod.INSTANCE.getMinimap().getMinimapFBORenderer()`; the in-map element handler is a **private** field of type `xaero.hud.minimap.element.render.map.MinimapElementMapRendererHandler` → `add(MinimapElementRenderer)`. Elements in it are drawn **inside the minimap framebuffer**: clipped and rotated with the minimap. `getOverMapRendererHandler()` is public but draws over the map (no clipping/rotation) — not used.
- No public "world → screen" transform on either map. No line/polyline element type.

### Signature check across versions (javap, `-p -s`, hashes of renderer/reader/provider signatures)
| Artifact | Versions | Result |
|---|---|---|
| Minimap (`xaero.hud.minimap.element.render.*`, FBO handler field) | 25.3.13, 26.1.0, 26.2.0, 26.4.2, 26.5.0, 26.6.0 | identical; handler field of that type present in all |
| World Map (`xaero.map.element.*`, `WorldMap.mapElementRenderHandler`) | 1.40.11, 1.43.0, 1.45.0, 1.46.0, 1.47.0 | identical |
| Third-party waypoints (fallback) | ≥ 26.2.0 only | absent in 26.1.0 / 25.x |

### How others do it
- **XaeroPlus** (rfresh2/XaeroPlus, branch `1.21` @20a89c0, Minimap 26.6.0 / WM 1.47.0, read as public source): lines are drawn from **mixins** — `@WrapOperation` + `@Local(name=...)` into `GuiMap.render` (endBatch slice) and `MinimapFBORenderer.renderChunksToFBO`. Exact, but tied to private method bodies and local variable names → breaks on any Xaero refactor. We do **not** use mixins.
- Xaero "Drawings" — user feature, no API for third parties.

### Our workaround: probe calibration
- One element provider per map, feeding 4 invisible "probe" elements: anchor, anchor+X, anchor+Z, anchor+X+Z (step adapts so the probes are ~48 GUI px apart).
- In `renderElement` we read the pose matrix Xaero applied for each probe → affine world→render-space map (scale, rotation, dimension scale come for free). The 4th probe must close the parallelogram (ratio 0.8–1.25, axes |cos| < 0.2), and votes whether `partialX/partialY` are a sub-pixel remainder to add. Shear, degenerate or clamped (out-of-bounds) probes → nothing is drawn that frame.
- The line itself is our own `BufferBuilder` (QUADS, POSITION_COLOR), no depth test, drawn in the anchor's `renderElement`; text with `GuiGraphics` under identity pose (World Map only).
- Failure policy: exception in a hook, handler not found within 60 s in world, or > 600 frames without a valid projection → that map's hook is off for the session, warning in the log, **fallback** = chain of temporary third-party waypoints (`aerocouriers:route`, `route-<n>` every `breadcrumb_step` blocks, Minimap ≥ 26.2.0).
- Edge marker on the minimap = the existing A/B third-party waypoint of the order (Xaero clamps it to the minimap edge itself).

### What we rely on / where it can break
1. `WorldMap.mapElementRenderHandler` static field and `add(...)` — public; renamed/removed → no line on World Map, fallback.
2. Private field of type `MinimapElementMapRendererHandler` inside `MinimapFBORenderer` (only reflection point, searched by type, not name) → absent → no line in minimap, fallback.
3. Abstract method set of `MapElementRenderer` / `MinimapElementRenderer` / readers / providers — a new abstract method → `AbstractMethodError` at render, caught → fallback. A changed signature → `NoSuchMethodError`/`IncompatibleClassChangeError` on registration, caught → fallback.
4. Semantics we infer from probes (pose contains world translation; partials are a remainder) — verified only as "affine and self-consistent" at runtime, not visually ⚠️.
5. Render state inside Xaero's batch (blend, shader, depth) — we set our own and restore; mismatch = visual glitches, not crash ⚠️.
6. Third-party waypoints for fallback and edge marker — ≥ 26.2.0.
