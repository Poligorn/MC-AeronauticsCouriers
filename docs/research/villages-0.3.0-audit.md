# Аудит деревенской системы (0.3.0)

Аудит по коду и ассетам на `main` `b92e921` (0.2.1-beta). Фичи деревень **не реализовывались**. Макеты UI станции для 0.3.0 ещё не рисовались — ниже только факты и предложения.

**Проверено:** исходники, datapack, модели, GameTest `StationGameTests` / `NpcContractGameTests`. **Не проверено в runClient:** живое окно станции, генерация в новом мире, редкий груз глазами, подсказка Jade (см. §5, §A, §B).

Дополнение 30.10.2026: полная инвентаризация блока станции и UI — **[§A](#a-блок-станции-и-ui)**; данные Jade по всем блокам мода — **[§B](#b-jade-данные-по-блокам-мода)**.

---

## 1. Что есть

### 1.1 Блок станции `aerocouriers:post_station`

| Что | Где | Факт |
|---|---|---|
| Блок | `village/PostStationBlock.java` | `BaseEntityBlock`, `FACING`, твёрдость −1, `noLootTable`, в выживании не ломается |
| BE | `village/PostStationBlockEntity.java` | `stationId` + имя; деревенская регистрация откладывается на тик; дубль → стол картографа |
| ПКМ | `PostStationBlock.java:74–85` | `StationPackets.open` → `StationSnapshotPayload` → `ClientStation.apply` → **`PostStationScreen`** |
| Модель | `assets/aerocouriers/models/block/post_station.json` | **Заглушка:** parent `minecraft:block/orientable`, текстуры ванильного стола картографа (`cartography_table_top` / `side3` / `side1`) |
| Item model | `models/item/post_station.json` | parent блока |
| Blockstate | `blockstates/post_station.json` | 4 стороны, один и тот же stub-модель |
| Предмет | креатив «Операторские блоки» (`OP_BLOCKS`, только с правами) + `/give`; rarity EPIC; рецепта нет | GDD §6.0; подробности — [§A](#a-блок-станции-и-ui) |

Комментарий на блоке (`PostStationBlock.java:74`: «opens the shared job board (iteration 4)») **устарел**: ПКМ открывает окно станции, не общую доску. Правило `.cursor/rules/65-post-stations.mdc` строки 25–26 («Отдельного окна станции нет») тоже **устарело**. GDD §6.0 строка 256 говорит «окно отложено», §6.4 строка 280 — «окно станции сделано»: документы противоречат друг другу и коду.

### 1.2 Worldgen

`village/VillageStructures.java`: на `ServerAboutToStartEvent` элемент `aerocouriers:village/post_station` (вес 4) **дописывается** в пулы `minecraft:village/{plains,desert,savanna,snowy,taiga}/houses`. Ванильный JSON не перезаписывается. Выключается `stations.generate_in_villages`.

Структура: `data/aerocouriers/structure/village/post_station.nbt`, править через `tools/gen_post_station_structure.py` (3×3×3, jigsaw `minecraft:building_entrance`). Зомби-деревни не трогаем (`zombie/houses` нет в списке).

Одна станция на деревню: `PostStationBlockEntity` — дубль в том же старте структуры или ближе `stations.village_dedup_radius` (96) становится столом картографа. Админские станции не заменяются. GameTest: `villageStructurePlacesOneNamedStationThatLicensedPlayersDiscover`, `villageStationsEightyBlocksApartKeepOnlyOne`.

На Ikaria деревень почти нет — рабочие точки ставит оператор (CLARIFICATIONS, `60-ikaria-pack.mdc`).

### 1.3 Атлас

`village/StationAtlas.java` (SavedData `aerocouriers_stations`): id, имя, измерение, позиция, `discoveredBy`, репутация. Лицензированный игрок открывает станцию в радиусе `stations.register_radius` (32), раз в секунду (`TICK_INTERVAL = 20`). FakePlayer в тестах зовёт `discoverNearby` напрямую.

На доске NPC-заказ виден, если игрок открыл **обе** станции (`BoardPackets.java:452–460`). Оператор видит все.

Имена: `village/StationNames.java` + `data/aerocouriers/station_names/*.json` (40 русских названий в дефолте). Оператор переименовывает в окне станции или `/couriers station rename`.

### 1.4 Генератор NPC-заказов

`village/StationContractSource.java` implements `ContractSource`:

- Тик: каждые `village_generation_interval_min` (10) минут (`onServerTick`, строки 53–59). Ручной прогон: `/couriers station generate`.
- Пары **любых** станций атласа (деревенские и админские) в одном измерении, ≤ 10 000 бл (`MAX_PAIR_DISTANCE`), не больше `village_board_max` (12) открытых NPC, одна открытая пара на направление.
- Межмир: `generateCrossDimension`, если `allow_cross_dimension_orders`, шанс `cross_dimension_order_chance` (0.15). Принять с ранга IV.
- Зоны A/B: радиус `ZONE_RADIUS = 12` (строка 38). Срок `DEFAULT_DEADLINE_MINUTES = 60`.
- Короткие пары для стажёра сортируются вперёд (`sortPairsForTrainee`, `trainee_max_distance` 800).
- **Контейнер не preplaced.** `publishVillage` (`ContractManager.java:493–514`) ставит `preplaced=false`, груз только в Escrow; физический ящик — ленивый спавн при принятии (GameTest `npcGenerateAcceptDeliver`: «NPC cargo is escrow-only until accept»). GDD §6.2 строка 268 и `65-post-stations.mdc` строка 29 пишут «контейнер ставится сразу (preplaced)» — **это ложь относительно кода**.

Награда минтится в Escrow. При провале залог сгорает, груз уничтожается (не почта курьеру).

### 1.5 Таблицы груза

Datapack `data/aerocouriers/village_cargo/`:

| Файл | produces (кратко) | consumes |
|---|---|---|
| `default.json` | пшеница 8–24, хлеб 4–16, дуб 8–16 | уголь, железо, пшеница |
| `plains.json` | пшеница 12–32, хлеб 8–16, сено 1–4 | уголь, железо, дуб |
| `desert.json` | кактус 8–24, песок 16–32, золотой самородок 8–24 | пшеница, дуб, ведро воды 1–2 |
| `savanna.json` | акация, стейк, кожа | пшеница, уголь, железо |
| `snowy.json` | снежок 16–32, ель, кроличья шкура | уголь, хлеб, белая шерсть |
| `taiga.json` | ель 12–24, ягоды, уголь | пшеница, железо, хлеб |

Логика `VillageCargo.rollManifest` (строки 140–160): пересечение produces отправителя и consumes получателя; если пусто — всё, что производит отправитель; 1–3 стака. Нет таблицы биома → `default`. Неизвестный биом на Ikaria (острова) почти всегда `default`.

Вес по `Weights`: блок = 1.0, самородок ≈ 0.05, предметы ≈ 0.05. Самый тяжёлый реалистичный бросок (песок 32 + ещё стаки) остаётся **далеко ниже** `max_weight_s` (256) и `slots_s` (9). `CargoSize.smallestFor` поэтому почти всегда возвращает **S**. Код M/L/XL у деревень **не запрещён** — таблиц на них просто нет.

### 1.6 Редкие заказы

Код есть. `VillageCargo.tryInjectRare` (162–200): если `StationReputation.tier(originReputation) > 0` и `random < village_rare_roll_chance` (0.35), один стак заменяется записью из `village_cargo/rare/*.json`.

| Файл | min_reputation | weight | Что |
|---|---|---|---|
| `rare/tier1_seeds.json` | 10 | 12 | свёкла 2–8 |
| `rare/tier1_melon.json` | 10 | 8 | семена арбуза 1–4 |
| `rare/tier2_unbreaking.json` | 25 | 6 | книга Unbreaking/Efficiency/Feather Falling 1–2 |
| `rare/tier3_mending.json` | 50 | 4 | книга Mending/Fortune/Silk Touch ур. 1 |
| `rare/tier3_sapling.json` | 50 | 10 | саженец вишни 1–2 |

Пороги репутации: 10 / 25 / 50 → бонус +10 / +20 / +30% к награде при доставке (`StationReputation.java`, конфиг). Новая станция имеет репутацию 0 → `tier == 0` → **редкий груз никогда не выпадает**, пока с неё/на неё не отвезут 10 заказов. На Ikaria с двумя админскими станциями это долго.

«Дорогой» отдельной категории нет. Формула награды (`Pricing.npcReward`, GDD §6.3): `16 + (D/60)×(1+W/400) + W×0.04` × 0.8–1.2. Короткая пара (~7 бл, W≈32) ≈ 16–20 сп (после `MoneyScale` — единицы изумрудов). Пара на 10 000 бл ≈ сотни шпор, не тысячи. Таблица доходности GDD §11.3 (32 / 76 / 233 / 658 сп по рангам) **текущими таблицами не воспроизводится**.

На доске редкий заказ: иконка `icon/rare_cargo_tablet`, подсказка `ui.aerocouriers.row.rare_cargo` (`BoardRowRenderer.java:162–177`).

### 1.7 Стажировка и таймеры

- Первые `trainee_free_contracts` (3) NPC без залога. После 3-й доставки — аванс `trainee_bonus` (100 сп / делитель изумрудов). Пока есть бесплатные — нельзя принять NPC дальше `trainee_max_distance` (800). GameTest: `npcTraineeAdvanceAfterThirdDelivery`, `npcTraineeRejectsLongRoute`.
- Срок заказа 60 мин игровых тиков. Генерация раз в 10 мин. Шапка доски/станции: `VillageRefreshTiming` + `VillageRefreshUi` (осталось до следующего прогона).
- Сроки стоят, пока сервер выключен (инвариант проекта).

### 1.8 Окно станции (UI)

`client/PostStationScreen.java` (555 строк) — не заглушка класса, а рабочий экран:

- Хром планшета (`TabletScreenChrome`), `ScreenFit`, анимация открытия.
- Строка репутации: очки, тир, бонус %, следующий порог (`ui.aerocouriers.station.reputation`).
- Список заказов **этой** станции (`issuer` или `recipient` = id станции, лимит `StationSnapshotPayload.MAX_ORDERS` = 32) через `BoardRowRenderer` (не карточки доски `BoardCardRenderer`).
- Иконки карты и груза в строке, клик по строке → закрыть и `ClientBoard.focusContract`.
- Кнопки «Открыть доску» / «Готово»; у оператора — поле переименования.
- `isPauseScreen() == false`.

Это **не** текущий стиль вкладки «Заказы» планшета (карточки, фильтры, чипы размера, модалка принятия). Ближе к старому списку строк. Отдельного макета 0.3.0 нет. Полный перечень виджетов, пакетов и дублей с планшетом — [§A](#a-блок-станции-и-ui).

### 1.9 Доска и планшет

NPC-заказы на общей доске: тип `VILLAGE`, глиф дома (`TabletFilterGlyphs`), лицо заказчика — иконка деревни (`BoardCustomerFace.drawVillageIcon`), имя станции. Фильтр типа «NPC». Учебный заказ — отдельный тип, не деревенский.

Вкладки «Заказы» / «Мои» показывают NPC, если открыты обе станции. Вкладки «Атлас» нет (STATUS → «В работе»).

---

## 2. Что устарело / заглушка / расхождение документов

| Проблема | Файлы:строки | Суть |
|---|---|---|
| Модель блока — стол картографа | `models/block/post_station.json` целиком | Заглушка ассета; в мире станция неотличима от ванильного стола (дубль дедупа тоже стол) |
| Комментарий ПКМ | `PostStationBlock.java:74` | Пишет «job board», код открывает `PostStationScreen` |
| Правило станций | `.cursor/rules/65-post-stations.mdc:25–26` | «Отдельного окна нет» — окно есть |
| GDD §6.0 vs §6.4 | `docs/GDD.md:256` vs `:280` | «окно отложено» / «окно сделано» |
| Preplaced NPC | `docs/GDD.md:268`, `65-post-stations.mdc:29` vs `ContractManager.java:502` и GameTest | Документы: ящик сразу у станции. Код: ленивый спавн |
| Комментарий `publish` | `StationContractSource.java:204–205` | Согласован с кодом (спавн при accept); GDD нет |
| Доходность §11.3 | `docs/GDD.md:547–555` vs `Pricing.npcReward` + таблицы | Цифры рангов не бьются с текущим грузом S |
| Редкий груз на новых станциях | `VillageCargo.java:164–166` | `tier <= 0` → шанс 0 при репутации 0 |
| «Только S у деревень» | не запрет в коде | Следствие лёгких таблиц + `smallestFor` |
| 65-post-stations «не сделано: окно станции» | `65-post-stations.mdc:26` | Сделано в итерации 5, правило не обновляли |

---

## 3. Предложения на 0.3.0 «Полировка деревень»

Ничего из этого не делать, пока нет макетов (правило проекта). Оценка — компоненты и риск, не календарь.

1. **Новый UI станции в стиле текущего планшета.** Сейчас `PostStationScreen` — список строк `BoardRowRenderer`, без карточек, фильтров и принятия на месте. Цель: те же карточки/детали/модалка риска, что на вкладке «Заказы», плюс шапка станции (имя, репутация, таймер генерации). Сложность: **средняя** (экран и пакет уже есть, нужно выровнять с `CourierBoardScreen` / `BoardCardRenderer`; макет обязателен).
2. **Настоящая модель блока станции.** Свои текстуры и, при необходимости, многоблочная/ориентированная модель вместо стола картографа. Иначе в деревне станцию не найти, а дедуп «лишняя → стол картографа» визуально путает. Сложность: **средняя** (арт + blockstate; JSON-модель без GeckoLib — проще). Нужен рисунок граней.
3. **Дорогие заказы с крупной суммой.** Отдельный пул или множитель награды (не только D и W): редкий флаг «ценный маршрут», потолок под ранг, чтобы §11.3 перестал быть фантазией. Сложность: **низкая–средняя** (формула + datapack + подпись на карточке; экономика через `Economy`, без второй ветки выплат).
4. **Редкие заказы как событие, а не только замена стака.** Сейчас семена свёклы при репутации ≥ 10. Имеет смысл: шанс без тира 1 для админских станций Ikaria; отдельные грузы/награды (не «заменить пшеницу на 4 семечка»); чип на карточке уже есть. Сложность: **низкая** (таблицы + ослабить `tier <= 0`; дизайн дропа — макет/таблица).
5. **Контейнеры M/L/XL у деревень с учётом рангов.** `smallestFor` и `AcceptEligibility` по размеру уже есть (`rank_required_xl` = 4). Нужны таблицы с весом/стаками под M (768) / L (2048) / XL (4096) и генератор, который не предлагает XL стажёру. Сложность: **средняя** (datapack + проверка, что ленивый спавн M/L/XL у станции находит площадку; XL — ранг IV).
6. **Вкладка «Атлас» на планшете.** Список открытых станций, репутация, «на карте». Уже в STATUS «В работе». Сложность: **средняя** (новый таб, пакет можно расширить атласом; макет).
7. **Ikaria: генератор не должен ждать деревень.** Пары админских станций уже работают. Имеет смысл явнее в UI («нет станций в атласе — попросите оператора») и `/couriers station generate` в справке. Сложность: **низкая**.
8. **Согласовать документы с кодом.** GDD §6.2 preplaced, правило 65, комментарий блока. Сложность: **низкая** (только docs). Имеет смысл сделать в том же 0.3.0, что и фичи, чтобы не разъехалось снова.

Прочее, что стоит улучшить, но не в первую тройку: пулы зомби-деревень; вес worldgen 4/70 → часто 0 станций; сообщение «добавлена в атлас» проверить живому игроку; не путать термины с Envelope.

---

## 4. Оценка смежных пунктов 0.3.0 (план, без реализации)

### 4.1 Jade на контейнере (вес + номер заказа)

Кратко: плагина нет, в BE только `contractId`. Полная таблица полей, приватность и вопрос Sable — **[§B](#b-jade-данные-по-блокам-мода)**.

- Jade **15.10.6** в паке, опционально, мост в `compat/` (сейчас там только `NumismaticsEconomy` и `package-info`).
- Цель 0.3.0 (BACKLOG): не путать одинаковые ящики — показать `shortId` заказа и вес (из контракта, не из блока: у BE **нет инвентаря**). GDD §9.2 дополнительно называет получателя и срок.
- Реализация (когда разрешат): `@WailaPlugin` + `IBlockComponentProvider` / `StreamServerDataProvider` на `CargoBlock` / `CargoContainerBlockEntity`, `ModList.isLoaded("jade")`. Без Jade ничего не меняется.
- Сложность: **средняя**. API 15.x сверен с публичной документацией Jade (`jademc.readthedocs.io`, plugins22), **не** с jar — перед кодом записать в `api-verification.md` по артефакту. Не копировать чужие провайдеры.

### 4.2 Мёртвые lang-ключи

Паритет `en_us` / `ru_ru`: 961/961. Точный список на `b92e921`:

**`aerocouriers.configuration.*` без `.translation()` в Spec — 84 ключа** (из 90; живые только 6 лучей: `beam_enabled`, `beam_height`, `beam_render_distance`, `beam_fade_start`, `beam_pickup_color`, `beam_delivery_color`):

`access`, `ace_min_success`, `allow_cross_dimension_orders`, `allow_self_accept`, `backend`, `blast_resistance`, `break_seconds`, `button`, `cargo`, `cargo_mass_scale`, `contracts`, `cross_dimension_distance`, `cross_dimension_order_chance`, `customWaypointSymbol`, `custom_x`, `custom_y`, `debug`, `debug_log_interval_ticks`, `delivery_max_speed`, `delivery_still_checks`, `drop_ratio`, `economy`, `emerald_value_spurs`, `enabled`, `fee_multiplier_l`, `fee_multiplier_m`, `fee_multiplier_s`, `fee_multiplier_xl`, `generate_in_villages`, `gui`, `late_window`, `max_open_per_player`, `max_weight_l`, `max_weight_m`, `max_weight_s`, `max_weight_xl`, `migration`, `navigation`, `notificationSounds`, `p2p_fee`, `p2p_min_collateral_ratio`, `particle_density`, `pause_on_logout_singleplayer`, `piracy`, `position`, `pricing`, `progression`, `rank_required_xl`, `rank_xp`, `register_radius`, `render_distance`, `reputation_tier_bonus_percent`, `reputation_tier_thresholds`, `require_tablet`, `reward_distance_divisor`, `reward_per_weight`, `slots_l`, `slots_m`, `slots_s`, `slots_xl`, `sounds`, `sounds_enabled`, `sounds_volume`, `spawn_search_radius`, `spawn_trigger_radius`, `stations`, `tablet_grid_animation`, `tablet_grid_speed`, `trainee_bonus`, `trainee_free_contracts`, `trainee_max_distance`, `ui_animations`, `useVanillaSprites`, `village_board_max`, `village_dedup_radius`, `village_generation_interval_min`, `village_rare_roll_chance`, `waypointSymbolMode`, `waypoints`, `xp_cadet`, `xp_captain`, `xp_first`, `xp_second`, `zones`.

**`ui.aerocouriers.stamp.*` — 4**, ни одного упоминания в Java: `title`, `origin`, `weight`, `cost`. (Живой `commands.aerocouriers.stamp.given` не трогать.)

**`ui.aerocouriers.test.*` — 8**, ни одного упоминания в Java: `stamp`, `waybill`, `license`, `money`, `selected`, `forcespawn`, `forcedeliver`, `hint`.

Итого **96 ключей** (84+4+8), не «около 77»: конфиг-ключи без `.translation()` плюс мёртвые stamp/test UI. Часть `configuration.*` — заголовки секций NeoForge Config UI; если когда-нибудь повесить `.translation()` на Spec, удалять их нельзя. План 0.3.0: повесить `.translation()` на живые ключи Spec **или** удалить неподключённые; stamp/test UI — удалить, если вкладка «Тест» правда снята. Сложность: **низкая**. Сверять паритет en/ru.

### 4.3 Справка `/couriers` без аргументов

Корень `Commands.literal("couriers")` в `CouriersCommands.java:54` **без** `.executes()`. Brigadier отвечает «неполная команда» (подтверждено QA 0.2.0-alpha в STATUS). План: `.executes` с короткой справкой (player: `profile`, `ui`, `contract`, `mail`, `balance`; оператор: `license`, `station`, `admin`, …) + ключи `commands.aerocouriers.help.*` в en_us/ru_ru. Сложность: **низкая**.

### 4.4 Клик по нику заказчика → профиль

Частично сделано. В деталях заказа клик по имени вызывает `openCustomerProfile` → `ClientBoard.requestPlayerProfile` (`BoardDetailsOverlay.java:554–558`, `CourierBoardScreen.java:1711–1716`). На списке карточек клик открывает **детали**, не профиль (`mouseClicked` → `openDetails`). Подсказка `ui.aerocouriers.row.customer_profile_hint` уже есть. План: на карточке/строке клик по нику (не по всей карточке) открывает профиль; NPC-станция — нет. Сложность: **низкая**.

---

## 5. Скриншоты

Каталог: `/opt/cursor/artifacts/villages-audit/`. Генератор: `tools/render_villages_audit_ab.py`. **Ни один файл не является кадром `runClient`.** GLFW на этой машине не принимает синтетические клики; Jade 15.10.6 в прогоне не ставился.

| Файл | Что это |
|---|---|
| `station-block-in-world-stub.png` | Три грани текущей модели блока: ванильный стол картографа 16×16 (`cartography_table_top` / `side3` / `side1`) |
| `station-village-structure-topdown.png` | Вид сверху 3×3 структуры `village/post_station.nbt` (пол, jigsaw, станция, фонари) |
| `station-screen-operator-layout.png` | Схема окна станции у оператора: EditBox + Rename, репутация, таймер, строки, Job board / Done |
| `station-screen-player-layout.png` | То же без поля переименования |
| `station-window-layout-mock.png` | Копия операторской схемы (старое имя) |
| `jade-container-s-current-mock.png` | Реконструкция **текущего** Jade на контейнере S без плагина Couriers |
| `jade-station-current-mock.png` | Реконструкция Jade на станции: кастомное имя через `Nameable`, без репутации |

Живой клиент (ПКМ по станции, список заказов, настоящий Jade на ящике / на плоте Sable) в этом прогоне не снимался. Нужен ручной заход.

---

## A. Блок станции и UI

Инвентаризация `aerocouriers:post_station` на `b92e921`. Код не менялся.

### A.1 Идентификаторы и свойства блока

| Что | Значение | Где |
|---|---|---|
| Block ID | `aerocouriers:post_station` | `registry/ModBlocks.java:51–59` |
| BlockEntityType | `aerocouriers:post_station` | `registry/ModBlockEntities.java:26–28` |
| Item ID | `aerocouriers:post_station` (`BlockItem`) | `registry/ModItems.java:37–39` |
| Класс блока | `village/PostStationBlock` extends `BaseEntityBlock` | |
| Класс BE | `village/PostStationBlockEntity` implements `Nameable` | |
| Lang | `block.aerocouriers.post_station` = «Post Station» / «Почтовая станция» | `en_us.json:121`, `ru_ru.json:121` |
| Map color | `WOOD` | `ModBlocks.java:55` |
| Sound | `SoundType.WOOD` | |
| Прочность | `strength(-1.0F, 3_600_000.0F)` — в выживании не ломается, кроме креатива | GDD §6.0 |
| Лут | `.noLootTable()` — JSON-лута нет | |
| Push | `PushReaction.BLOCK` | поршень не двигает |
| Теги mineable | нет (контейнеры в `pickaxe` / `needs_diamond_tool` есть, станции нет) | `data/minecraft/tags/block/` |
| POI / профессия жителя | нет | поиск по репозиторию пуст |
| Меню / слоты | нет (`ModMenus` станцию не регистрирует; экран — `Screen`, не `AbstractContainerScreen`) | |
| Capability инвентаря | нет | |

Предмет: `Rarity.EPIC`, `stacksTo` по умолчанию 64. В креативе только вкладка **Operator Utilities** (`CreativeModeTabs.OP_BLOCKS`) и только если `event.hasPermissions()` (`AeronauticsCouriers.java:94–96`). Рецепта в `data/aerocouriers/recipe/` нет (там только `order_tablet.json` и `transport_waybill.json`). Выживание: только `/give` или операторский креатив.

### A.2 Модель, текстуры, blockstates

`assets/aerocouriers/models/block/post_station.json`:

```json
{
  "parent": "minecraft:block/orientable",
  "textures": {
    "top": "minecraft:block/cartography_table_top",
    "front": "minecraft:block/cartography_table_side3",
    "side": "minecraft:block/cartography_table_side1"
  }
}
```

Это **заглушка**. Свои PNG у станции нет. Ванильные карты 16×16 (`cartography_table_top.png` 314 байт, `side3` 206 байт, `side1` 273 байт в client jar 1.21.1). Item model (`models/item/post_station.json`) — parent блока.

`blockstates/post_station.json`: четыре горизонтальных `facing` (north / east+90 / south+180 / west+270), одна и та же модель. Свойство `PostStationBlock.FACING` = `HorizontalDirectionalBlock.FACING`. При установке лицом к игроку: `context.getHorizontalDirection().getOpposite()` (`PostStationBlock.java:50–51`). `rotate` / `mirror` есть. `RenderShape.MODEL`.

В мире станция неотличима от ванильного стола картографа. Дедуп как раз заменяет дубль **на** стол картографа — две одинаковые картинки, разный блок. Кадр граней: `station-block-in-world-stub.png`.

### A.3 Как блок появляется

Три пути, жителя среди них нет.

1. **Генерация деревни (jigsaw pool injection, не свой structure start).** `VillageStructures.onServerAboutToStart`: элемент `aerocouriers:village/post_station` (вес 4, `Projection.RIGID`) дописывается рефлексией в уже загруженные пулы `minecraft:village/{plains,desert,savanna,snowy,taiga}/houses`. Ванильный JSON пулов не перезаписывается. Выключатель: `stations.generate_in_villages` (`CouriersServerConfig.STATIONS_IN_VILLAGES`). Зомби-деревни (`zombie/houses`) не трогаются. Типичный суммарный вес пула домов 68–87 → шанс слота ≈ 5%, в деревне 0–2 куска, затем дедуп оставляет одну станцию.

2. **Структура куска.** `data/aerocouriers/structure/village/post_station.nbt`, править `tools/gen_post_station_structure.py`. 3×3×3, DataVersion 3955:
   - y=0: еловые доски; в `(0,0,1)` — jigsaw `orientation=west_up`, `name=minecraft:building_entrance`, `final_state=minecraft:structure_void` (стык с улицей, как у ванильных домов);
   - `(1,1,1)` — `aerocouriers:post_station` `facing=west`, NBT `{id: aerocouriers:post_station, village: true}`;
   - `(2,1,0)` и `(2,1,2)` — spruce fence; над ними фонари;
   - остальное — воздух.
   Вид сверху: `station-village-structure-topdown.png`.

3. **Игрок / оператор.** `BlockItem` ставит блок. `PostStationBlockEntity.placingVillage` / флаг `village` в NBT **не** ставятся → админская станция, дедуп её не заменяет (`PostStationBlockEntity.java:114–117`, `28`). Житель станцию не ставит и не привязан.

После постановки `onLoad` откладывает регистрацию на следующий тик (`TickTask`). Если `village && isDuplicateVillageStation` (тот же structure start `village_{plains,desert,savanna,snowy,taiga}`, или станция в атласе ближе `stations.village_dedup_radius` (96), или сканирование 48 бл) — блок становится `minecraft:cartography_table`. Иначе `stationId = randomUUID()`, имя из `StationNames.pick`, запись в `StationAtlas` (SavedData `aerocouriers_stations`). GameTest: `villageStructurePlacesOneNamedStationThatLicensedPlayersDiscover`, `villageStationsEightyBlocksApartKeepOnlyOne`.

**Открытие атласа игроком** — не клик, а тик: лицензированный игрок в радиусе `stations.register_radius` (32) раз в секунду (`StationAtlas.TICK_INTERVAL = 20`). ПКМ по станции атлас не пополняет.

На Ikaria деревень почти нет — рабочие точки ставит оператор.

### A.4 Функции блока (не UI)

| Функция | Есть? | Как |
|---|---|---|
| Источник NPC-заказов | да | `StationContractSource` берёт **любые** станции атласа (деревенские и админские) как A/B |
| Зона погрузки / сдачи | да, неявно | `ZONE_RADIUS = 12` вокруг позиции станции; доставка — `ContractManager.checkContainer`, **не** GUI станции |
| Спавн контейнера | нет до accept | `publishVillage` ставит `preplaced=false`; ящик лениво при принятии |
| Хранение груза | нет | предметов в блоке нет |
| Приём / сдача руками | нет | нет слотов, нет кнопки «сдать» |
| Открытие доски | да | ПКМ → окно станции (не общая доска, комментарий на `PostStationBlock.java:74` врёт) |
| Переименование | да, оператор | UI или `/couriers station rename` |
| Репутация | да, не в BE | `StationAtlas.Station.reputation`; +1 за доставку, −3 за провал (`StationReputation.DELIVERY_DELTA` / `FAILURE_DELTA`) |
| Обнаружение | да | proximity, см. выше |
| Слом / лут | нет в выживании | hardness −1, `noLootTable` |
| Снятие из атласа | да | `onRemove` → `StationAtlas.remove` |

### A.5 Сеть

| Пакет | Направление | ID | Когда |
|---|---|---|---|
| `OpenStationPayload(BlockPos)` | C2S | `aerocouriers:open_station` | **Никогда не шлётся с клиента.** Зарегистрирован в `ModPayloads.java:123–128`, хендлер `StationPackets.handleOpen`. ПКМ идёт серверным `useWithoutItem` → `StationPackets.open` напрямую |
| `StationSnapshotPayload` | S2C | `aerocouriers:station_snapshot` | Ответ на открытие и после rename. Хендлер: `ClientStation.apply` → `new PostStationScreen` |
| `RenameStationPayload(pos, name)` | C2S | `aerocouriers:rename_station` | Кнопка Rename. Сервер: права `Commands.LEVEL_GAMEMASTERS`, имя 1…48 после `strip()`, иначе `station.aerocouriers.error.name` |

Поля `StationSnapshotPayload` (`net/StationSnapshotPayload.java`):

| Поле | Тип | Смысл |
|---|---|---|
| `stationPos` | `BlockPos` | какой блок открыли |
| `name` | `String` | имя из BE / атласа |
| `reputation` | `int` | `StationAtlas.reputation(stationId)` |
| `bonusPercent` | `int` | `StationReputation.bonusPercent` (0/10/20/30) |
| `reputationTier` | `int` | 0…3 |
| `reputationNextThreshold` | `int` | 10/25/50 или −1 на макс. тире |
| `canRename` | `boolean` | `player.hasPermissions(2)` |
| `orders` | `List<BoardEntry>` ≤ 32 | NPC `OPEN`, у которых `issuer` **или** `recipient` = `stationId`, сортировка по `createdAt` |
| `nextVillageRefreshGameTime` | `long` | абсолютный overworld game time следующего `StationContractSource` тика; 0 если генерация выключена |
| `snapshotOverworldGameTime` | `long` | базовая точка для клиентского таймера |

`BoardEntry` тот же, что у планшета (`BoardPackets.boardEntry`): `id`, `shortId`, `state`, `role`, подписи A/B, `distance`, `size`, `weight` строкой, `reward`, `collateral`, ранг, дедлайн, манифест ≤ лимита, заказчик, координаты (pickup режется `Contract.revealsPickupTo`), `villageRare`, репутация станции-заказчика, `originRevealed`, межмир, слоты, maxWeight, возраст, `cargoPickedUp`.

Расхождения с доской планшета:

- Фильтр «открыл обе станции» (`BoardPackets.visibleOnBoard`) на окне станции **не** применяется. Кто дошёл до этой станции, видит все её NPC-заказы, включая те, чья вторая точка ещё не в атласе, и координаты B уходят в пакете.
- `OrderTablets.canAccessBoard` / `require_tablet` на ПКМ станции **не** проверяются. Окно открывается без планшета. Кнопка «Job board» и клик по строке уже идут в `ClientBoard.requestOpen` и упираются в планшет.

### A.6 Экран `PostStationScreen`

Класс: `client/PostStationScreen.java` (555 строк). `isPauseScreen() == false`. Хром тот же, что у планшета: `TabletScreenChrome`, `ScreenFit`, `TabletGui` (рамка, полость, шестерёнка загрузки). Отдельного GUI-текстурного файла станции нет.

**Нет:** слотов, `AbstractContainerMenu`, кнопки принять / отказаться / отменить, фильтров, чипов размера, карточек `BoardCardRenderer`, модалки риска, скроллбара (колёсико есть), вкладки «Атлас».

Виджеты и тексты сверху вниз (координаты — логические, в полости планшета; `panelW = min(440, guiW-32)`, `panelH = min(360, max(220, maxFrameHeight))`):

| Элемент | Условие | Ключ / поведение |
|---|---|---|
| Заголовок | всегда | `ui.aerocouriers.station.title` = «Post station «%s»» / имя из снимка |
| `EditBox` имя, max 48, ширина `cw-80`, высота 18 | `canRename` | `ui.aerocouriers.station.rename` |
| `TabletButton` 72×20 «Rename» | `canRename` | `ui.aerocouriers.station.rename.action` → `RenameStationPayload` |
| Строка репутации | всегда | `ui.aerocouriers.station.reputation` + `reputation_next` или `ui.aerocouriers.reputation.max_tier` |
| Шапка списка | всегда | `ui.aerocouriers.station.orders.header` |
| Таймер генерации справа в шапке | `nextVillageRefreshGameTime > 0` | `VillageRefreshUi.renderHeader` → `ui.aerocouriers.village_refresh.in` / `.soon` (порог 20 тиков) |
| Пустой список | `orders.isEmpty()` | `ui.aerocouriers.station.orders.empty` |
| Строки `BoardRowRenderer` | иначе | линия 1: `#shortId`, статус, иконка деревни, имя; линия 2: награда, залог (если не скрыт), минуты, размер, звезда issuer, иконка rare |
| `TabletRowIconButton` MAP | на видимой строке | клик / ховер → `XaeroOrderBridge.onHoverDestination`; подсказка координат A/B и дистанции |
| `TabletRowIconButton` CRATE | на видимой строке | ховер → `BoardCargoTooltip` (стаки + вес); клик без действия (`onActivate == null`) |
| Клик по строке (не по иконке) | ЛКМ | закрыть экран, `ClientBoard.focusContract(id)` → открыть планшет на этом заказе |
| Колёсико | список | `scroll`, пересборка иконок; видимых строк ≈ `(contentH - 36 - listTop) / rowH` |
| `TabletButton` 120×20 «Job board» | всегда, низ слева | `ui.aerocouriers.station.open_board` → `onClose()` + `ClientBoard.requestOpen(0)` |
| `TabletButton` 68×20 Done | всегда, низ справа | `gui.done` → `onClose()` (анимация хрома) |
| Тултип строки | ховер текста | сводка: награда, залог, ранг, дедлайн, время, груз (вес/размер), дистанция (`ui.aerocouriers.row.summary.*`) |
| Тултип rare | ховер иконки | `ui.aerocouriers.row.rare_cargo` |

Схемы: `station-screen-operator-layout.png`, `station-screen-player-layout.png`.

### A.7 Что дублирует планшет / устарело

После UI планшета 0.2.x окно станции — второй, более старый список той же доски.

| На станции | На планшете (`CourierBoardScreen`) | Вывод |
|---|---|---|
| `BoardRowRenderer` (две текстовые строки) | вкладка «Заказы»: карточки `BoardCardRenderer`, фильтры, чипы, модалка риска | список станции **устарел по стилю** |
| Те же `BoardEntry`, иконки карты/груза, rare, таймер `VillageRefreshUi` | то же на доске | **дубль данных** |
| Клик по строке → `focusContract` → планшет | принятие на месте | со станции **нельзя принять**; без планшета (если `require_tablet`) клик заканчивается `need_tablet` |
| Кнопка «Job board» | открытие доски | дубль входа |
| Репутация в шапке станции | репутация во всплывашке деревенского заказчика (`row.customer_tooltip_village`) | шапка станции **уникальна** как обзор точки |
| Rename | нет на планшете (есть `/couriers station rename` и админка) | **уникально** |
| Имя станции в заголовке | `StationHint` только в накладной / админке; вкладки «Атлас» нет | заголовок **уникален**, пока нет Атласа |
| Фильтр discovery слабее, чем у доски | обе станции должны быть открыты | расхождение, не дубль |

Комментарий «opens the shared job board (iteration 4)» и правило `.cursor/rules/65-post-stations.mdc:25–26` («отдельного окна нет») — устарели относительно кода.

### A.8 Предложение 0.3.0: что оставить / убрать / добавить

Ничего не делать, пока нет макета. Это целевой состав, не план работ в этом PR.

**Оставить (столп 3 GDD: блоки — контейнер и почтовая станция):**

- Сам блок в мире: генерация / операторская постановка, атлас, дедуп, неразрушимость, зона A/B радиуса 12.
- Шапка: имя, репутация (очки / тир / бонус / следующий порог), таймер до генерации — это то, чего на карточках доски не видно сразу.
- Rename для оператора.
- Редкие заказы как маркер в списке (`villageRare` + иконка) — логика генерации уже есть.
- ПКМ без планшета как **локальный** вход в заказы этой точки (решить явно относительно `require_tablet`).

**Убрать или заменить:**

- Текущий `BoardRowRenderer`-список как отдельный UI-язык. Либо те же карточки/детали/модалка, что вкладка «Заказы», либо одна кнопка «открыть планшет, фильтр = эта станция» без второго списка.
- Мёртвый клиентский `OpenStationPayload` — либо начать слать его с клиента (единый C2S-intent), либо оставить серверный `useWithoutItem` и не держать пакет «на всякий случай». Решать при коде, не сейчас.
- Комментарий блока и правило 65 про «окна нет».

**Добавить (после макета):**

- Своя модель/текстуры станции, чтобы не путать со столом картографа и с результатом дедупа.
- Репутация: полоса до следующего тира, не только строка.
- Редкие и **дорогие** заказы: шанс на репутации 0 для админских точек Ikaria; отдельный множитель награды, не только замена стака на семена. Чип на карточке уже есть.
- Сдача груза: отдельную GUI-сдачу **не** предлагать — доставка уже автоматическая, когда контейнер в зоне B. Имеет смысл только обратная связь у станции (частицы/звук уже завязаны на контракт, не на блок). Preplaced-ящик у NPC по-прежнему расходится с GDD §6.2.
- Клик по станции = discover в атласе (сейчас только proximity).
- На пакете станции тот же `visibleOnBoard`, что у планшета, чтобы координаты B не утекали раньше открытия второй точки.
- Вкладка «Атлас» на планшете (STATUS «В работе») — тогда окно блока можно сузить до шапки станции + «открыть заказы».

---

## B. Jade: данные по блокам мода

Плагина Couriers нет. `compat/` содержит `NumismaticsEconomy` и `package-info` (упоминает Jade словами). В `build.gradle` / `neoforge.mods.toml` Jade не подключён. В паке Ikaria — Jade **15.10.6**. WTHIT в GDD §9.2 назван рядом, в 0.3.0 его нет.

Публичный API 15.x (документация `jademc.readthedocs.io`, plugins22, **не** jar; ⚠️ записать в `api-verification.md` по артефакту перед кодом):

- `@WailaPlugin` на `IWailaPlugin`; NeoForge подхватывает аннотацию, Fabric-entrypoint не нужен.
- Клиент: `IBlockComponentProvider.appendTooltip(ITooltip, BlockAccessor, IPluginConfig)` + `getUid()`, регистрация `registerBlockComponent(provider, BlockClass.class)`.
- Сервер: `StreamServerDataProvider<BlockAccessor, T>` (~250 мс), регистрация `registerBlockDataProvider(provider, BlockEntityClass.class)`.
- Ключ конфига Jade: `config.jade.plugin_<modid>.<path>`.
- Maven: `maven.modrinth:jade:15.10.6+neoforge` (пример из той же доки).

Мод регистрирует **два** типа BE и **пять** блоков.

### B.1 Какие блоки есть

| Блок | BE | Item | Что лежит в мире |
|---|---|---|---|
| `aerocouriers:cargo_container_s` | общий `CargoContainerBlockEntity` (`aerocouriers:cargo_container`) | нет `BlockItem` (GDD столп 3) | 1×1×1 |
| `aerocouriers:cargo_container_m` | тот же тип | нет | 1×2×1 |
| `aerocouriers:cargo_container_l` | тот же тип | нет | 2×2×2 |
| `aerocouriers:cargo_container_xl` | тот же тип | нет | 2×3×2 |
| `aerocouriers:post_station` | `PostStationBlockEntity` | есть, операторский | 1×1×1 |

Других блоков нет. Предметы (лицензия, накладная, планшет) — не блоки; у лицензии уже есть item tooltip.

### B.2 Что уже есть в данных (без Jade)

**Контейнер — блокстейт** (`CargoContainerBlock`, все размеры):

| Свойство | Поле | Клиенту видно? | Смысл |
|---|---|---|---|
| Размер | класс блока / `CargoSize` (`S`/`M`/`L`/`XL`) | да, по ID блока | S/M/L: lang `block.aerocouriers.cargo_container_*`. **XL: ключа lang нет** — Jade покажет сырой ID |
| Заполненность | `FILL` `IntegerProperty` 0…8 | да, в blockstate | `CargoSize.fillFor(weight)` = round(W / maxWeight × 8), минимум 1 при W>0. На модель **не** влияет (`blockstates/cargo_container_*.json` смотрят только `facing`+`waybill`). На массу Sable — да (`physics_block_properties`, `fill=1…8` и `locked=true` → mass 0) |
| Ориентация | `FACING` | да | дверь |
| Накладная | `WAYBILL` | да | бумага на грани |
| Замок физики | `LOCKED` | да | до accept штампа ящик не собирается в плот |

**Контейнер — BE** (`CargoContainerBlockEntity`, NBT `saveAdditional`):

| Поле NBT / метод | Есть у чужого клиента? | Смысл |
|---|---|---|
| `contract_id` / `contractId()` | в чанк-NBT да; **отдельного `getUpdatePacket`/`getUpdateTag` нет** | UUID заказа. После `bind()` без ресенда чанка клиент может отстать; после сборки Sable переносит полный NBT плота |
| `debug` / `isDebug()` | да, в NBT | тестовый ящик |
| `ticks`, `chunkUnloading`, `selfRemoving`, `assembling` | нет в NBT | служебные |

Инвентаря, веса, заказчика, курьера, статуса, точки B, дедлайна, награды, залога в блоке **нет**. Это `Contract` + `Escrow` + `StationAtlas` на сервере.

**Контракт** (`contract/Contract.java`) — SavedData менеджера, не блока:

| Нужное для Jade | Откуда | Примечание |
|---|---|---|
| Номер заказа | `Contract.shortId()` = первые 8 символов UUID | GDD §9.2, BACKLOG 0.3.0 |
| Вес | `Contract.weight` (`double`) | BACKLOG 0.3.0. Строка «W / max» собирается в `BoardPackets.entry` |
| Заказчик | `issuer` (UUID игрока или UUID станции); имя — `BoardPackets.playerName` / `StationAtlas.name` | |
| Курьер | `Optional<UUID> courier` | пусто, пока OPEN |
| Статус | `ContractState` OPEN/ACCEPTED/IN_TRANSIT/DELIVERED/EXPIRED/FAILED | у стоящего ящика обычно OPEN (штамп) или IN_TRANSIT |
| Пункт назначения | `destination` (`Destination`: измерение + pos + radius) | |
| Расстояние | `Contract.distance()` / `ContractDistances.pricingBlocks` | от A до B, не от игрока до ящика |
| Дедлайн | `timing.deadline`, `timing.dueAt()`, `timing.expiresAt` | OPEN: до истечения публикации; активный: до сдачи |
| Награда | `reward` | |
| Залог | `collateral` | 0 у trainee NPC |
| Свой / чужой | `roleFor(viewer)` → `issuer` / `courier` / `board`; `isCourier`, `issuer.equals` | |
| Кому показывать pickup | `revealsPickupTo(viewer)` | антипират: координаты A только издателю и курьеру после accept |
| Редкий груз | `Escrow.Holding.villageRare` | не в контракте |
| Манифест | `Escrow.Holding.items` | **не светить чужим** |

Позиция ящика в мире, если он на плоте: `CargoTracker.Snapshot.position` (JOML world) vs `plotPos`. Для Jade позиция — луч, не трекер.

**Станция — BE** (`PostStationBlockEntity`):

| Поле NBT | Метод | Клиенту | Смысл |
|---|---|---|---|
| `station_id` | `stationId()` | в чанк-NBT | UUID в атласе; `null` один тик при первой постановке |
| `name` | `name()`, `getCustomName()` | да | кастомное имя `Nameable` — ванильный Jade, скорее всего, покажет его в заголовке |
| `village` | `village()` | да | сгенерирована ли деревней |

**Станция — не в BE, только сервер:**

| Что просили | Откуда |
|---|---|
| «Деревня» / имя точки | `StationAtlas.Station.name` (совпадает с BE `name`). Имени ванильной деревни Minecraft нет |
| Число заказов | `ContractManager.open()` filter `VILLAGE` и `issuer|recipient == stationId` (как `StationPackets.open`, лимит 32) |
| Время до обновления | `VillageRefreshTiming.nextRefreshGameTime` — **глобальный** тик генератора, не per-station |
| Репутация / тир / бонус | `StationAtlas.Station.reputation` + `StationReputation.*` |
| Кто открыл | `Station.discoveredBy` |

### B.3 Что покажет Jade **сейчас** (без плагина)

Реконструкция, не кадр: `jade-container-s-current-mock.png`, `jade-station-current-mock.png`.

- Контейнер S/M/L: локализованное имя блока, harvest (алмазная кирка, hardness 50), имя мода. Расширенный режим Jade может вывести blockstate (`fill`, `locked`, `waybill`, `facing`) — это не наши строки.
- Контейнер XL: то же, но заголовок, скорее всего, `block.aerocouriers.cargo_container_xl` (ключа нет).
- Станция: заголовок = `getCustomName()` («Северный причал»), под ним «Почтовая станция», мод. Репутация, заказы, таймер — нет.
- Номер заказа, вес, заказчик, курьер, статус, B и дистанция, дедлайн, награда, залог — **ни на одном блоке**.

### B.4 Что добавлять (когда разрешат код)

Один `StreamServerDataProvider` на `CargoContainerBlockEntity` и один на `PostStationBlockEntity`. Клиентские `IBlockComponentProvider` вешать на `CargoBlock` (покроет S/M/L/XL) и `PostStationBlock`. Регистрация только если `ModList.get().isLoaded("jade")`. Не читать `ContractManager` на клиенте.

Минимум 0.3.0 (BACKLOG): на контейнере `#shortId` + вес.

GDD §9.2 к минимуму: получатель + срок.

Ниже — полная матрица и кто из зрителей что видит. «Чужой» = не issuer, не courier, не оператор (`hasPermissions(2)`).

**Контейнер**

| Поле | Источник для пакета Jade | Чужой | Свой (заказчик / курьер) / оператор |
|---|---|---|---|
| Размер | ID блока, без сервера | да | да |
| Заполненность 0–8 | blockstate `FILL`, без сервера | да (грубая шкала; точный вес — ниже) | да |
| Locked / waybill | blockstate | да | да |
| `#shortId` | `Contract.shortId()` | да (GDD: номер заказа публичен, иначе ящики неразличимы) | да |
| Вес числом | `Contract.weight` | да (BACKLOG; это не состав груза) | да |
| Статус | `Contract.state` | да, коротко (OPEN / IN_TRANSIT / …) | да |
| Заказчик (имя) | игрок / имя станции | **нет** (P2P-ник — приватно; деревня — спорно, можно имя станции) | да |
| Курьер | `courier` + `playerName` | **нет** | да |
| Пункт назначения (имя) | label станции / «игрок» | GDD хочет «получатель»: имя станции или «заказ игрока», **без координат** | имя + координаты |
| Расстояние A→B | `Contract.distance()` | можно как «длина маршрута», не GPS | да |
| Дедлайн / минуты | `timing` относительно `now` | да (GDD §9.2 «срок») | да |
| Награда | `reward` | **нет** (экономика для пирата) | да |
| Залог | `collateral` | **нет** | да |
| Манифест / стаки | Escrow | **нет** | по желанию, не обязательно в Jade |
| Свой / чужой | `roleFor` | не подписывать «чужой»; просто урезанный набор | подпись роли не обязательна |
| Редкий | `villageRare` | можно иконкой, без состава | да |

Координаты pickup чужому не слать (`revealsPickupTo` уже есть). Стоящий на точке A штамповый ящик и так выдаёт место — это не повод светить B.

**Станция**

| Поле | Источник | Чужой | Свой / оператор |
|---|---|---|---|
| Имя | BE `name` / атлас | да (и так Nameable) | да |
| Репутация, тир, бонус, порог | атлас | да (публичная репутация точки) | да |
| Число открытых NPC | `ContractManager` | да, **число**, не список | да + можно shortId |
| Время до генерации | `VillageRefreshTiming` | да | да |
| village-флаг | BE | не нужно игроку | оператору можно |
| stationId UUID | BE | **нет** | оператору можно |
| discoveredBy | атлас | **нет** | нет смысла в Jade |

Серверный провайдер **обязан** смотреть `accessor.getPlayer()`: один и тот же луч даёт разный payload. Не класть в client NBT блока то, чего нет в нашем `revealsPickupTo` / role.

### B.5 Jade на контейнере на корабле Sable

**Не проверено в этом прогоне** (Jade не ставился, корабль не собирался).

По коду:

- После accept `SableBridge.assembleStructure` уносит блоки в sub-level. `CargoContainerBlockEntity.worldPosition` — координата **плота**, не мира (`CargoContainerBlockEntity.java:23–24`, `api-verification.md`).
- В над-мире на месте ящика физического блока уже нет; клиент рисует собранный объект Aeronautics/Sable.
- Jade 15.10.6 в changelog CurseForge (6.08.2026) явно чинит «improve compatibility with Create Aeronautics». Это намекает, что луч Jade умеет попадать в блоки на объекте Aeronautics, но **не доказано** для нашего контейнера.
- Если луч резолвится в `CargoContainerBlockEntity` плота — достаточно `IBlockComponentProvider` + server data. Если луч попадает только в entity физики — нужен ещё `IEntityComponentProvider`, иначе подсказки не будет.
- `IServerDataProvider` смотрит `accessor.getBlockEntity()`; на плоте это сработает только если Jade отдаёт BE sub-level, а не пустышку.

Перед реализацией: прототип — собрать S/M, встать у летящего ящика, навести Jade. Результат записать в `api-verification.md` (✅/❌). Пока честно: **не проверено**.

### B.6 Скриншот текущего Jade

Живого кадра нет. Реконструкция без плагина: `jade-container-s-current-mock.png` (имя блока + harvest + мод, без `#shortId` и веса) и `jade-station-current-mock.png` (кастомное имя). Настоящий Jade на контейнере, станции и плоте Sable снимать вручную, когда появится мост.
