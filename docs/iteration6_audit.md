# Iteration 6 audit — «Ранги и навигация» (GDD §16 row 6, §4, §3, §10.3)

Audit date: 2026-10-26 · branch `cursor/rare-cargo-iteration5-f22f` (pre-iteration-6).

| Requirement | Status | Notes |
|---|---|---|
| `requiredRank` on contracts (§5.3) | **Done** | `RankRules.requiredRank`, set at publish; shown on board rows |
| Accept: rank vs `requiredRank` | **Done** | `ContractManager.accept` → `rank_too_low` |
| Accept: max active hauls (§4.2) | **Done** | `RankRules.limits().maxActive()`, `too_many_active` names blocking id |
| Accept: max deposit / container / distance per courier rank | **Done** | `AcceptEligibility` + localized errors |
| Trainee village distance (iter. 5) | **Done** | `TraineeRules` + `trainee_too_far` |
| Epaulettes from `license_rank` (§3) | **Done** | `EpauletteRenderer`, textures `epaulette_rank1..5` + `admin` (+ `_glow`) |
| License rank on item / profile | **Done** | `Licenses.syncRankOnLicense`, board profile |
| Zone particle outline (§10.3) | **Done** | `ZoneParticles`, `ZoneSyncPayload`, client `[zones]` config |
| Nav beam (courier-only, pickup → delivery) | **Done** | `NavRenderer` + `[navigation].beam_*` |
| Nav HUD (bearing, distance, order id, multi-order) | **Done** | Nearest or `ClientNavigation` focus |
| Client config: beam/HUD toggles + HUD position | **Done** | `[navigation]` in `aerocouriers-client.toml` |
| Xaero temp waypoints for active hauls (§10.3) | **Done** | Pickup + delivery via `ZoneSync` → `XaeroOrderBridge` |
| Xaero fallback DESTINATION (25.x–26.1) | **Done** | `XaeroLegacyWaypoints` |
| `clearOrigin` on logout | **Done** | `ClientZones.clear` → `XaeroOrderWaypoints.clearAll` |
| Board: gray locked rows (§5.3) | **Done** | Dim overlay + lock icon when rank limits block accept |
| Accept modal: localized rank/limit reasons | **Done** | Denial line + disabled confirm |
| Action bar on accept failure | **Done** | `BoardPackets.handleIntent` |
| Unit/check scripts (nav math, rank limits) | **Done** | `check_nav_math.py`, `check_rank_limits.py` |
| Docs STATUS / GDD §16 / CHANGELOG | **Done** | This audit + STATUS §итерация 6 |

## Placeholder art

All epaulette textures exist under `assets/aerocouriers/textures/entity/` (including `rank5`); no new placeholders required for iteration 6 unless final art replaces current `rank5` files.
