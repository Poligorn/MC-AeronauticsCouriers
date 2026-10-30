# Аудит деревенской системы (0.3.0)

Аудит по коду и ассетам на `main` `b92e921` (0.2.1-beta). Фичи деревень **не реализовывались**. Макеты UI станции для 0.3.0 ещё не рисовались — ниже только факты и предложения.

**Проверено:** исходники, datapack, модели, GameTest `StationGameTests` / `NpcContractGameTests`. **Не проверено в runClient:** живое окно станции, генерация в новом мире, редкий груз глазами (см. §8).

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
| Предмет | креатив «Инструменты оператора» + `/give`; рецепта нет | GDD §6.0 |

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

Это **не** текущий стиль вкладки «Заказы» планшета (карточки, фильтры, чипы размера, модалка принятия). Ближе к старому списку строк. Отдельного макета 0.3.0 нет.

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

- Jade **15.10.6** в паке, опционально, мост в `compat/` (сейчас там только `NumismaticsEconomy` и `package-info`).
- Цель: не путать одинаковые ящики — показать `shortId` заказа и вес (из контракта/Escrow, не из блока: у BE **нет инвентаря**).
- Реализация (когда разрешат): `IJade`/`IBlockComponentProvider` на `CargoBlock` / `CargoContainerBlockEntity`, `ModList.isLoaded("jade")`, все вызовы в try/catch по духу клиентских мостов. Данные: `contractId` из BE → `ContractManager` на сервере, на клиент — через Jade server data (вес + `#abcd1234`). Без Jade ничего не меняется.
- Сложность: **средняя**. Риск: угадать API Jade 15.10.6 — сверить с jar, записать в `api-verification.md`. Не копировать чужие провайдеры.

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

Каталог: `/opt/cursor/artifacts/villages-audit/`.

- `station-window-layout-mock.png` — раскладка окна по спрайтам планшета (`tools/render_station_window_preview.py`). **Это не кадр runClient**, а схема из кода/спрайтов.
- Живой `runClient` (ПКМ по станции, список заказов, репутация) в этом прогоне не снят: отдельная docs-ветка, GLFW на этой машине не принимает синтетические клики (тот же лимит, что у PR накладной). Нужен ручной заход в мир с двумя станциями.

Модель блока в игре = стол картографа; отдельный кадр блока без клиента не добавляет информации сверх `post_station.json`.
