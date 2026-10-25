# Проверка внешних API (25.10.2026)

Все вызовы ниже сверены с **реальными артефактами** нужных версий (sources.jar / байткод), а не с памятью или README. Пометки: ✅ проверено по коду артефакта, ⚠️ проверено только по коду — нужно подтвердить в игре, ❌ ошибка в GDD.

## Версии (сверены с паком MC-Ikaria, ветка `dev`, `mods/*.pw.toml`)
| Мод | Версия в паке | Артефакт для сборки | Maven |
|---|---|---|---|
| NeoForge | 21.1.250 (клиент), 21.1.235 (сервер) | компилируем против **21.1.235** | maven.neoforged.net |
| Create | 6.0.10 | не нужен для компиляции (пока) | maven.createmod.net (`com.simibubi.create:create-1.21.1:6.0.10-280`) |
| Create Aeronautics | bundled 1.3.0 | не нужен для компиляции; mod ID `aeronautics` (+ `simulated`) | maven.ryanhcode.dev (`dev.eriksonn.aeronautics:aeronautics-neoforge-1.21.1`, `dev.simulated_team.simulated:simulated-neoforge-1.21.1`) |
| Sable | 2.0.6 | `dev.ryanhcode.sable:sable-neoforge-1.21.1:2.0.6` (`compileOnly`, нетранзитивно) | maven.ryanhcode.dev/releases |
| Sable Companion | 1.6.0 (вшит в Sable) | `dev.ryanhcode.sable-companion:sable-companion-common-1.21.1:1.6.0` (`jarJar`) | maven.ryanhcode.dev/releases |
| Curios | 9.5.1+1.21.1 | `top.theillusivec4.curios:curios-neoforge:9.5.1+1.21.1` | maven.theillusivec4.top |
| Numismatics | 1.1.0 (в GDD — 1.0.20) | ещё не подключён | ⚠️ API не сверен |
| Xaero's Minimap | 26.6.0 | ещё не подключён (`compileOnly`, chocolateminecraft.com/maven) | см. [xaero.md](xaero.md) |

## Sable 2.0.6 / Sable Companion 1.6.0
| Что | Факт | Статус |
|---|---|---|
| `SubLevelAssemblyHelper.assembleBlocks(ServerLevel, BlockPos, Iterable<BlockPos>, BoundingBox3ic)` → `ServerSubLevel` | есть, `dev.ryanhcode.sable.api` | ✅ |
| Аргумент `bounds` | тип `BoundingBox3ic`, класс `BoundingBox3i` лежит в **`dev.ryanhcode.sable.companion.math`** (GDD не указывал пакет) | ✅ |
| `/sable` shatter одного блока | `SableAssembleCommands.shatterBlockToSubLevel`: bounds = блок ±1 (как в GDD §9.1); перед этим проверяет `VoxelNeighborhoodState.isSolid` | ✅ |
| Проверка результата | команда `/sable assemble` проверяет `subLevel.getMassTracker().isInvalid()` — используем так же | ✅ |
| Перенос block entity | `moveBlocks`: `blockEntity.saveWithFullMetadata(...)`, координаты в теге заменяются на позицию в плоте, затем тег загружается → `contractId` переезжает | ✅ по коду, ⚠️ в игре |
| Блок после сборки | физически стоит в «плоте» под-уровня; `BlockEntity#worldPosition` — координаты плота | ✅ |
| `SableCompanion.INSTANCE` | `ServiceLoader`; в рантайме реализация `ActiveSableCompanion` из Sable, без Sable — `DefaultSableCompanion` (тождественные преобразования) | ✅ |
| `projectOutOfSubLevel(Level, Vec3)` | **@Deprecated, `ScheduledForRemoval(inVersion = "2.0.0")`** — в GDD §9.3 использовать нельзя. Правильно: `projectOutOfSubLevel(Level, Vector3dc, Vector3d dest)` (JOML) | ❌ GDD → исправлено в коде |
| `getVelocity(Level, Vec3)` | тоже @Deprecated; используем `getVelocity(Level, Vector3dc, Vector3d)` | ❌ GDD → исправлено |
| Единицы скорости | блоки/секунду (серверная ветка: линейная + угловая скорость тела Rapier; клиентская: Δпозы × 20) | ✅ |
| `getContaining(Level, Vec3i/BlockPos)` → `SubLevelAccess` (`getUniqueId()`, `logicalPose()`, `boundingBox()`) | есть | ✅ |
| `distanceSquaredWithSubLevels(Level, Position/Vector3dc, ...)` | есть | ✅ |
| `BlockSubLevelAssemblyListener` | `dev.ryanhcode.sable.api.block`; `beforeMove` (default) и `afterMove` (abstract); в `afterMove` старый блок ещё не удалён | ✅ |
| `BlockWithSubLevelCollisionCallback` | `dev.ryanhcode.sable.api.block` | ✅ (не используется пока) |
| `physics_block_properties` | `SimpleJsonResourceReloadListener("physics_block_properties")` — любой namespace. Поля: `selector` (блок или `#тег`), `priority` (по умолчанию 1000), `properties`, `overrides` (ключ — `prop=value[,prop=value]`). Свойства: `sable:mass` (по умолчанию 1.0), `sable:inertia`, `sable:volume`, `sable:restitution`, `sable:friction`, `sable:fragile`, `sable:floating_material`, `sable:floating_scale` | ✅ |
| Пересчёт массы при смене blockstate у собранного объекта | по коду свойства хранятся на каждом `BlockState`; пересчитывает ли `MassTracker` при `setBlock` внутри плота — не проверено | ⚠️ |
| Совместимость Companion | Sable 2.0.6 `neoforge.mods.toml`: `sablecompanion` `(1.6.0,)` — **incompatible**. Значит, встраиваем 1.6.0; если пак получит новый Sable, jar-in-jar выберет его Companion (диапазон у нас `[1.6.0,)`) | ✅ |
| Зависимости Sable в рантайме | Sable сам встраивает Veil 4.3.2, Rapier-натив и Companion; требует NeoForge ≥ 21.1.228 | ✅ |

## Curios 9.5.1+1.21.1
| Что | Факт | Статус |
|---|---|---|
| `CuriosApi.getCuriosInventory(LivingEntity)` → `Optional<ICuriosItemHandler>` | есть | ✅ |
| `ICuriosItemHandler.findFirstCurio(Item)` → `Optional<SlotResult>`; `getCurios()`, `getStacksHandler(id)`, `setEquippedCurio(id, index, stack)` | есть | ✅ |
| `ICurioItem` на классе предмета | регистрируется автоматически (`Curios.java`: `it instanceof ICurioItem`) | ✅ |
| `ICurioRenderer.render(...)`, `CuriosRendererRegistry.register(Item, Supplier<ICurioRenderer>)` | сигнатуры как в pilotwings | ✅ |
| Слоты/сущности datapack | `data/<ns>/curios/slots/<id>.json`, `data/<ns>/curios/entities/<name>.json` (`CuriosSlotManager`, `CuriosEntityManager`) | ✅ |
| Валидатор по умолчанию | `curios:tag` → тег `curios:<slot>` (`data/curios/tags/item/license.json`) | ✅ |
| Синхронизация изменений стека | `CuriosEventHandler.tick` (сервер): `!ItemStack.matches(stack, prev)` → `SPacketSyncStack` всем отслеживающим → смена компонента ранга видна другим игрокам без своих пакетов | ✅ по коду, ⚠️ в игре |
| `curioTick` | вызывается каждый тик на обеих сторонах, если слот активен | ✅ |

## NeoForge 21.1.235
Data Components (`DeferredRegister.createDataComponents`), Data Attachments (`AttachmentType.builder(...).serialize(codec).copyOnDeath()`), `ModConfigSpec`, `RegisterCommandsEvent`, `PlayerEvent.PlayerLoggedInEvent` — проверены компиляцией против 21.1.235.

## Что ещё не проверено
- Numismatics 1.1.0: `Numismatics.BANK`, `BankAccount.deduct/deposit`, `BankAccount.Type` (GDD §14.5 ⚠️).
- Xaero: fair-play mod ID.
- FFItemWeight: API для веса предметов.
- Aeronautics: можно ли зацепить верёвку/лебёдку за наш блок (только в игре).
