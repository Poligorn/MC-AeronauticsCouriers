# Проверка внешних API (25.10.2026)

Все вызовы ниже сверены с **реальными артефактами** нужных версий (sources.jar / байткод), а не с памятью или README. Пометки: ✅ проверено по коду артефакта, ⚠️ проверено только по коду — нужно подтвердить в игре, ❌ ошибка в GDD.

## Версии (сверены с паком MC-Ikaria, ветка `dev`, `mods/*.pw.toml`)
| Мод | Версия в паке | Артефакт для сборки | Maven |
|---|---|---|---|
| NeoForge | 21.1.250 (клиент), 21.1.235 (сервер) | компилируем против **21.1.235** | maven.neoforged.net |
| Create | 6.0.10 | GUI: UV сверены с исходниками тега `mc1.21.1-6.0.10`; blit по `create:textures/gui/...` (не compileOnly — `AllGuiTextures` тянет Catnip) | maven.createmod.net (`com.simibubi.create:create-1.21.1:6.0.10-280`) |
| Create Aeronautics | bundled 1.3.0 | не нужен для компиляции; mod ID `aeronautics` (+ `simulated`) | maven.ryanhcode.dev (`dev.eriksonn.aeronautics:aeronautics-neoforge-1.21.1`, `dev.simulated_team.simulated:simulated-neoforge-1.21.1`) |
| Sable | 2.0.6 | `dev.ryanhcode.sable:sable-neoforge-1.21.1:2.0.6` (`compileOnly`, нетранзитивно) | maven.ryanhcode.dev/releases |
| Sable Companion | 1.6.0 (вшит в Sable) | `dev.ryanhcode.sable-companion:sable-companion-common-1.21.1:1.6.0` (`jarJar`) | maven.ryanhcode.dev/releases |
| Curios | 9.5.1+1.21.1 | `top.theillusivec4.curios:curios-neoforge:9.5.1+1.21.1` | maven.theillusivec4.top |
| Numismatics | 1.1.0 (в GDD — 1.0.20) | `compileOnly maven.modrinth:numismatics:1.1.0+neoforge-mc1.21.1` | ✅ javap 1.1.0 jar |
| Xaero's Minimap | 26.6.0 | `compileOnly xaero.minimap:xaerominimap-neoforge-1.21.1:26.6.0` | см. [xaero.md](xaero.md) |
| Xaero's World Map | 1.47.0 | `compileOnly xaero.map:xaeroworldmap-neoforge-1.21.1:1.47.0` | см. [xaero.md](xaero.md) |
| XaeroLib | 1.7.3 | `compileOnly xaero.lib:xaerolib-neoforge-1.21.1:1.7.3` (compileOnly не транзитивен) | ✅ по POM 26.6.0 / 1.47.0 |

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
| `PlayerModel.createMesh` (1.21.1) | рука `addBox(..., y=-2)`; оверлей рукава `cubeDeformation.extend(0.25F)` → верхняя грань на `y=-2.25`. Модель — `RenderType.entityTranslucent`. Slim/wide: 3×12×4 vs 4×12×4 | ✅ sources NeoForge 21.1.235 |
| Слоты/сущности datapack | `data/<ns>/curios/slots/<id>.json`, `data/<ns>/curios/entities/<name>.json` (`CuriosSlotManager`, `CuriosEntityManager`) | ✅ |
| Валидатор по умолчанию | `curios:tag` → тег `curios:<slot>` (`data/curios/tags/item/license.json`) | ✅ |
| Синхронизация изменений стека | `CuriosEventHandler.tick` (сервер): `!ItemStack.matches(stack, prev)` → `SPacketSyncStack` всем отслеживающим → смена компонента ранга видна другим игрокам без своих пакетов | ✅ по коду, ⚠️ в игре |
| `curioTick` | вызывается каждый тик на обеих сторонах, если слот активен | ✅ |
| Кэш поиска | `findFirstCurio(Item)` кэширует результат по ключу предмета **на текущий игровой тик** → замена стека в том же тике не видна (найдено GameTest'ом). `findFirstCurio(Predicate)` кэш не читает — используем его | ✅ |

## NeoForge 21.1.235
Data Components (`DeferredRegister.createDataComponents`), Data Attachments (`AttachmentType.builder(...).serialize(codec).copyOnDeath()`), `ModConfigSpec`, `RegisterCommandsEvent`, `PlayerEvent.PlayerLoggedInEvent` — проверены компиляцией против 21.1.235.

## Xaero 26.6.0 / World Map 1.47.0 (выбор точки на штампе)
| Что | Факт | Статус |
|---|---|---|
| Открытие World Map | `new xaero.map.gui.GuiMap(null, null, WorldMapSession.getCurrentSession().getMapProcessor(), camera)` — так же делает `ControlsHandler` на клавишу M | ✅ javap |
| `WorldMapSession.getCurrentSession()` / `isUsable()` / `getMapProcessor()` | есть | ✅ |
| Экран вейпоинтов миникарты | `new xaero.common.gui.GuiWaypoints(HudMod.INSTANCE, MinimapSession, parent, escape)` | ✅ |
| Список точек текущего мира | `MinimapWorld.getIterableWaypointSets()` → `WaypointSet.getWaypoints()`; `getX/Y/Z`, `getName`, `getCreatedAt`, `isYIncluded`, `isThirdParty`, `getPurpose().isDeath()` | ✅ |
| Событие «вейпоинт создан» | нет | ✅ нет колбэка |
| Y неизвестен | `isYIncluded()==false` или Y=`32767` (`SupportXaeroMinimap.createTempWaypoint`) | ✅ |
| Maven | `chocolateminecraft.com/maven`, плюс `xaero.lib:xaerolib-neoforge-1.21.1:1.7.3` | ✅ |

## Xaero: линия маршрута на картах (30.10.2026, только javap-сигнатуры)
Подробно и с таблицей версий — [xaero.md](xaero.md#route-line-on-the-maps-2026-10-30-javap-signatures-only-no-decompilation).

| Что | Факт | Статус |
|---|---|---|
| Свои элементы на World Map | `WorldMap.mapElementRenderHandler` (public static) `.add(ElementRenderer)`; базовые `MapElementRenderer/Reader/RenderProvider` (ими же рисуются вейпоинты и игроки) | ✅ javap 1.40.11–1.47.0, сигнатуры одинаковые |
| Свои элементы внутри миникарты | `HudMod.INSTANCE.getMinimap().getMinimapFBORenderer()`, приватное поле типа `MinimapElementMapRendererHandler` (ищем по типу) `.add(MinimapElementRenderer)` | ✅ javap 25.3.13–26.6.0; ⚠️ единственное место рефлексии |
| Публичное преобразование «мир → экран» | нет | ✅ нет — обход «пробами» (4 невидимых элемента, матрица позы) |
| Стандартный тип «линия» | нет; Drawings — функция игрока без API | ✅ нет |
| Как рисует XaeroPlus | миксины `@WrapOperation` + `@Local(name)` в `GuiMap.render` и `MinimapFBORenderer.renderChunksToFBO` | ✅ исходник; у нас миксинов нет |
| Смысл `partialX/partialY` и позы при `renderElement` | выводится пробами в рантайме | ⚠️ визуально не проверено |
| Порядок/состояние рендера внутри батча Xaero (blend, depth, шейдер) | ставим своё и восстанавливаем | ⚠️ в игре не проверено |
| Запасной путь (цепочка точек) | `ThirdPartyWaypoints`, origin `aerocouriers:route` | ✅ javap, только Minimap ≥ 26.2.0 |

## Create 6.0.10 GUI (стиль окон, 25.10.2026)
| Что | Факт | Статус |
|---|---|---|
| `AllGuiTextures` | enum, `create:textures/gui/<file>.png`, `render` = blit фиксированного куска; `FONT_COLOR = 0x575F7A` | ✅ исходник тега `mc1.21.1-6.0.10` |
| Адаптивное окно | `StockKeeperRequestScreen` — фикс. ширина 226, HEADER/BODY/FOOTER без горизонтального растяжения. У нас большие панели: `BRASS_FRAME_*` + плитка `stock_keeper` (64,4, 8×8); `VALUE_SETTINGS_OUTER_BG` (80,9) в PNG прозрачен — не используем как заливку | ✅ |
| Кнопки | `BUTTON/HOVER/DOWN/GREEN/DISABLED` 18×18 в `widgets.png`; `IconButton` не растягивает фон | ✅ |
| Поля | `STATION_TEXTBOX_TOP/MIDDLE/BOTTOM` в `assemble.png` (150×18 / 1 / 4) | ✅ |
| Иконки | `AllIcons`: `I_CONFIRM` (0,1), `I_TRASH` (1,0), `I_MTD_CLOSE` (1,9) в `icons.png` 16×16 | ✅ |
| Слот | `STOCK_KEEPER_REQUEST_SLOT` 18×18 | ✅ |
| Рамка | `BRASS_FRAME_*` + `VALUE_SETTINGS_OUTER_BG` в `value_settings.png` | ✅ |
| Тултип | `SPEECH_TOOLTIP_BACKGROUND/COLOR` 8×8 в `widgets.png` | ✅ |
| Письмо в `AllIcons` | отдельной иконки конверта нет | ✅ нет |
| Catnip на compile | `catnip-neoforge` в maven.createmod.net для 1.21.1 не опубликован (есть только `+mc26.1.2`); `IconButton`/`Label`/`ScrollInput` без него не скомпилировать | ✅ maven |

## Create: Numismatics 1.1.0+neoforge-mc1.21.1 (экономика, 28.10.2026)
| Что | Факт | Статус |
|---|---|---|
| Mod ID | `numismatics` (`Numismatics.MOD_ID`) | ✅ javap |
| Maven (сборка) | `maven.modrinth:numismatics:1.1.0+neoforge-mc1.21.1`, `compileOnly`, non-transitive | ✅ Gradle resolve |
| Менеджер счетов | `Numismatics.BANK` → `GlobalBankManager` | ✅ javap |
| Счёт игрока | `GlobalBankManager.getAccount(UUID)` → `BankAccount` или null; `getOrCreateAccount(UUID, BankAccount.Type.PLAYER)` | ✅ javap |
| Баланс | `BankAccount.getBalance()` → `int` (спуры) | ✅ javap |
| Пополнение | `BankAccount.deposit(int spurs)` | ✅ javap |
| Списание | `BankAccount.deduct(int spurs, boolean allowOverdraft)` → `boolean`; `false` = без ухода в минус | ✅ javap |
| Дробление монет | `Coin.getCoinsFromSpurAmount(int)` (SPUR/BEVEL/…/SUN, `Coin.value` = номинал в спурах) | ✅ javap |
| GUI иконки монет | `BuiltInRegistries.ITEM` → `numismatics:{spur,bevel,sprocket,cog,crown,sun}` + `GuiGraphics.renderItem` | ✅ ids в jar 1.1.0 |
| GDD §14.5 | Старые имена совпадают по смыслу; пакет `dev.ithundxr.createnumismatics.*`, не Create addon namespace | ✅ |

## NeoForge 21.1.235 / MC 1.21.1 — UI уведомлений 0.2.0 (сверено по `neoforge-21.1.235-sources.jar`, 30.10.2026)
| Что | Сигнатура | Статус |
|---|---|---|
| Бейдж на предмете | `IItemDecorator.render(GuiGraphics, Font, ItemStack, int xOffset, int yOffset)` → `boolean` | ✅ исходники |
| Регистрация | `RegisterItemDecorationsEvent.register(ItemLike, IItemDecorator)`, шина мода, клиент | ✅ исходники |
| Звук UI | `SimpleSoundInstance.forUI(SoundEvent, float pitch, float volume)` — **pitch раньше volume**; у нас были перепутаны | ✅ исходники |
| F3 открыт | `DebugScreenOverlay.showDebugScreen()` (`Minecraft.getDebugOverlay()`) | ✅ исходники |
| Чанк загружен | `Level.isLoaded(BlockPos)` вместо устаревшего `LevelReader.hasChunkAt` | ✅ исходники |
| Лицензия при смерти | Curios `ICurioItem.getDropRule(...)` → `ICurio.DropRule.ALWAYS_KEEP` | ✅ (уже было в `PilotLicenseItem`) |

## Jade 15.10.6 (аудит 30.10.2026, плагина в моде ещё нет)

Сверено **только с публичной документацией** `https://jademc.readthedocs.io/en/latest/plugins22/getting-started/` и страницей файла CurseForge Jade NeoForge 1.21.1 15.10.6 (changelog: «improve compatibility with Create Aeronautics»). Jar / sources.jar **не** разбирались. Перед кодом моста повторить по артефакту `15.10.6+neoforge`.

| Что | Откуда | Статус |
|---|---|---|
| `@WailaPlugin` + `IWailaPlugin.register` / `registerClient` | docs plugins22 | ⚠️ не jar |
| `IBlockComponentProvider.appendTooltip(ITooltip, BlockAccessor, IPluginConfig)`, `getUid()` | docs | ⚠️ |
| `StreamServerDataProvider<BlockAccessor, T>` + `registerBlockDataProvider(..., BlockEntity.class)` | docs; sync ~250 мс | ⚠️ |
| Maven `maven.modrinth:jade:15.10.6+neoforge` | docs (пример `15.10.0+neoforge`) | ⚠️ точный classifier подтвердить по Modrinth |
| Луч Jade попадает в блок контейнера на плоте Sable / объекте Aeronautics | changelog 15.10.6 про Aeronautics | ⚠️ **в игре не проверено** (см. аудит §B.5) |

## Что ещё не проверено
- Xaero: fair-play mod ID; выбор точки на карте **в игре** (код есть); линия маршрута на World Map и миникарте **в игре** (код есть).
- FFItemWeight: API для веса предметов.
- Aeronautics: можно ли зацепить верёвку/лебёдку за наш блок (только в игре).
- Jade: jar 15.10.6 (сигнатуры), подсказка на контейнере в над-мире и на плоте Sable.
