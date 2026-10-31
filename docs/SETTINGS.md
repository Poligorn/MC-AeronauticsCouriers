# Настройки Aeronautics Couriers

Единый список настроек 0.2.1-beta. Имена ключей — как в файле или в коде (английские). Значения по умолчанию и диапазоны — из `CouriersServerConfig` и `CouriersClientConfig`.

Три группы:

1. Файлы `config/aerocouriers-server.toml` и `config/aerocouriers-client.toml`.
2. Админ-панель планшета и экран «Уведомления и задачи» (они пишут в те же файлы или в сохранённые данные мира).
3. Нарочно не вынесено в конфиг.

Клавиши меню управления — в конце группы 1: это не toml.

## 1. Файлы конфигурации

### Сервер — `aerocouriers-server.toml`

| Секция | Ключ | По умолчанию | Диапазон / допустимые значения |
| --- | --- | --- | --- |
| `contracts` | `village_generation_interval_min` | 10 | 1…1440 минут |
| `contracts` | `village_board_max` | 12 | 0…1000 |
| `contracts` | `p2p_min_collateral_ratio` | 0.5 | 0…10. Читается один раз при миграции на `economy.collateral_profile`, дальше не влияет |
| `contracts` | `p2p_fee` | 0.05 | 0…1, доля награды (сжигается) |
| `contracts` | `max_open_per_player` | 10 | 0…1000 |
| `contracts` | `pause_on_logout_singleplayer` | true | true / false. **Ключ зарезервирован: код не читает**, таймер оффлайн-курьера всегда идёт |
| `contracts` | `late_window` | 0.25 | 0…10, доля срока, в которой доставка ещё зачитывается со штрафом |
| `contracts.pricing` | `reward_distance_divisor` | 100 | 1…1 000 000 |
| `contracts.pricing` | `reward_per_weight` | 0.04 | 0…1000 |
| `contracts.pricing` | `fee_multiplier_s` | 1.0 | 0…100 |
| `contracts.pricing` | `fee_multiplier_m` | 1.5 | 0…100 |
| `contracts.pricing` | `fee_multiplier_l` | 2.5 | 0…100 |
| `contracts.pricing` | `fee_multiplier_xl` | 4.0 | 0…100 |
| `contracts` | `allow_cross_dimension_orders` | true | NPC может парить станции из разных измерений |
| `contracts` | `cross_dimension_order_chance` | 0.15 | 0…1 |
| `contracts` | `cross_dimension_distance` | 3000 | 100…500 000 блоков. Условная длина маршрута для награды, XP, срока и ранга |
| `cargo` | `spawn_trigger_radius` | 64 | 8…512 блоков |
| `cargo` | `delivery_max_speed` | 0.5 | 0…100 блоков/с |
| `cargo` | `delivery_still_checks` | 3 | 1…100 проверок подряд |
| `cargo` | `cargo_mass_scale` | 0.25 | 0…10. Документация физики; JSON уже посчитан для 0.25 |
| `cargo` | `debug_log_interval_ticks` | 20 | 0…72000, 0 = выкл |
| `cargo` | `spawn_search_radius` | 8 | 0…32 |
| `cargo` | `slots_s` / `slots_m` / `slots_l` / `slots_xl` | 9 / 18 / 27 / 45 | 1…256 |
| `cargo` | `max_weight_s` / `m` / `l` / `xl` | 256 / 768 / 2048 / 4096 | 1…1 000 000 |
| `cargo` | `rank_required_xl` | 4 | 1…5. Минимальный ранг, с которого принимают XL |
| `progression` | `rank_xp` | 0, 300, 1500, 5000, 15000 | список из 5 неотрицательных чисел, пороги рангов I…V |
| `progression` | `ace_min_success` | 0.90 | 0…1. Доля успеха для ранга V |
| `progression` | `trainee_free_contracts` | 3 | 0…100 |
| `progression` | `trainee_bonus` | 100 | 0…1 000 000 шпор (с изумрудами делится на `emerald_value_spurs`) |
| `progression` | `trainee_max_distance` | 800 | 0…50 000 блоков, 0 = без лимита |
| `economy` | `backend` | `auto` | `auto` / `numismatics` / `internal` |
| `economy` | `emerald_value_spurs` | 8 | 1…4096. Без Numismatics цены в шпорах делятся на это число |
| `economy` | `collateral_profile` | `normal` | `peace`, `easy`, `normal`, `hard`, `custom` |
| `economy` | `npc_custom_ratio` | 0.5 | 0…10. Доля NPC-залога, только когда профиль `custom` |
| `piracy` | `enabled` | true | false = контейнер нельзя сломать и взорвать |
| `piracy` | `break_seconds` | 10.0 | 0.05…3600. Сейчас не используется: прочность как у обсидиана |
| `piracy` | `blast_resistance` | 6.0 | 0…3 600 000 |
| `piracy` | `drop_ratio` | 0.5 | 0…1, доля стака, которая падает при повреждении |
| `stations` | `register_radius` | 32 | 4…256 |
| `stations` | `village_dedup_radius` | 96 | 16…256 |
| `stations` | `generate_in_villages` | true | true / false, только для новых деревень |
| `stations` | `reputation_tier_thresholds` | 10, 25, 50 | положительные целые |
| `stations` | `reputation_tier_bonus_percent` | 10, 20, 30 | неотрицательные целые, тот же порядок, что пороги |
| `stations` | `village_rare_roll_chance` | 0.35 | 0…1, 0 = выкл |
| `stations` | `village_size_weight_s` / `_m` / `_l` / `_xl` | 45 / 30 / 17 / 8 | 0…10 000, относительные веса размера NPC-заказа. Недоступные по тиру репутации или политике станции отбрасываются, остальные нормируются. На верхнем тире S остаётся самым частым |
| `stations` | `village_s_stacks_max` | 4 | 1…64, но не больше слотов S. Заказы S остаются лёгкими |
| `stations` | `village_value_reward_factor` | 0.35 | 0…100. К NPC-награде добавляется оценка груза (шпоры, после `value_multiplier`) × этот коэффициент. Обычный груз ещё ограничен `village_value_reward_cap` |
| `stations` | `village_value_reward_cap` | 500 | 0…1 000 000 шпор, 0 = без потолка. Потолок только для обычного груза; `valuable`-строки не режутся. Порог «дорого» остаётся 1000 |
| `stations` | `village_valuable_multiplier` | 5.0 | 1…100. Множитель оценки для строк `village_cargo` с `valuable=true` без своего `value_multiplier` |
| `stations` | `village_valuable_inject_chance` | 0.22 | 0…1. После набора обычного груза — шанс добавить один стак ценной строки отправителя (только M+) |
| `stations` | `village_size_bonus_s` / `_m` / `_l` / `_xl` | 0 / 40 / 220 / 240 | 0…100 000 шпор, надбавка к NPC-награде за размер контейнера до джиттера 0.8–1.2. Порог «дорого» (`VillageBoardPolicy.EXPENSIVE_REWARD` = 1000) не меняется |
| `migration` | `xp_cadet` / `xp_second` / `xp_first` / `xp_captain` | 0 / 300 / 1500 / 5000 | 0…2³¹−1. Стартовый XP при переносе старой лицензии kubejs, XP не понижается |
| `debug` | `allow_self_accept` | false | разрешить принять свой заказ |
| `access` | `require_tablet` | true | доска (J, кнопка инвентаря, команда, станция, предмет) требует планшет |
| `notifications` | `warn_minutes` | 5, 2, 1 | положительные целые, минуты до срока |
| `notifications` | `far_container_blocks` | 64 | 8…4096 |
| `notifications` | `far_notice_cooldown_s` | 30 | 1…3600 |
| `tutorial` | `tutorial_enabled` | true | то же значение, что кнопка админки и `/couriers admin server tutorial` |
| `tutorial` | `station_search_radius` | 512 | 16…4096. Ближайшая станция как точка A |
| `tutorial` | `xp` | 100 | 0…100 000, один раз за первое прохождение |
| `license` | `restore_price` | 25 | 0…1 000 000 шпор |

Доли профилей залога в конфиге нет: они зашиты (см. группу 3). В файле только имя профиля и NPC-доля для `custom`.

### Клиент — `aerocouriers-client.toml`

| Секция | Ключ | По умолчанию | Диапазон / допустимые значения |
| --- | --- | --- | --- |
| `button` | `position` | `INVENTORY_SIDE` | `CORNER`, `INVENTORY_SIDE`, `CUSTOM`, `HIDDEN` |
| `button` | `custom_x` / `custom_y` | 2 / 2 | 0…4000, только для `CUSTOM` |
| `zones` | `enabled` | true | контур зоны частицами |
| `zones` | `particle_density` | 1.0 | 0.05…8 |
| `zones` | `render_distance` | 128 | 8…1024 блоков |
| `gui` | `useVanillaSprites` | false | ванильные спрайты кнопок вместо планшета |
| `gui` | `tablet_grid_animation` | true | прокрутка сетки только в поле контента |
| `gui` | `tablet_grid_speed` | 1.0 | 0…32 пикселей/с |
| `gui` | `ui_animations` | true | открытие, вкладки, списки, кнопки, полоска XP |
| `gui` | `notificationSounds` | true | звук нового уведомления о заказе |
| `sounds` | `sounds_enabled` | true | звуки планшета и принятия/отказа |
| `sounds` | `sounds_volume` | 1.0 | 0…2 |
| `waypoints` | `waypointSymbolMode` | `ICON` | `ICON`, `FIRST_LETTER`, `CUSTOM`. Принятые точки всегда A и B |
| `waypoints` | `customWaypointSymbol` | `✓` | символ режима `CUSTOM` |
| `navigation` | `beam_enabled` | true | жёлтый луч — погрузка, голубой — доставка |
| `navigation` | `beam_height` | 256 | 16…512 |
| `navigation` | `beam_render_distance` | 512 | 32…2048 |
| `navigation` | `beam_fade_start` | 12 | 2…64 |
| `navigation` | `beam_pickup_color` | `1.0,1.0,0.33333334` | `r,g,b` в 0…1 |
| `navigation` | `beam_delivery_color` | `0.33333334,1.0,1.0` | `r,g,b` в 0…1 |
| `route` | `enabled` | true | линия маршрута на картах Xaero целиком. Без Xaero ничего не рисуется |
| `route` | `world_map` | true | линия на полноэкранной World Map |
| `route` | `minimap` | true | линия в миникарте и метка цели на краю |
| `route` | `label` | true | подпись у цели на World Map: дистанция, время в пути, время до срока |
| `route` | `erase_passed` | true | пройденная часть линии стирается |
| `route` | `waybill_preview` | true | кнопка «Показать на карте» в накладной |
| `route` | `other_orders` | false | остальные активные заказы тусклым пунктиром |
| `route` | `corridor` | 32 | 16…96 блоков. Дальше от линии — перестроение |
| `route` | `reroute_delay` | 2.0 | 0.5…10 с вне коридора до перестроения |
| `route` | `thickness` | `NORMAL` | `THIN`, `NORMAL`, `THICK` |
| `route` | `contrast_colors` | false | оранжевый к A и белый к B вместо жёлтого и голубого |
| `route` | `eta_mode` | `SHIP_SPEED` | `SHIP_SPEED` (средняя скорость за 10 с), `FIXED` |
| `route` | `fixed_speed` | 8.0 | 1…64 бл/с. Для `FIXED` и пока корабль стоит |
| `route` | `breadcrumb_step` | 96 | 32…512 блоков. Шаг точек-вех, если линию в этой версии Xaero нарисовать нельзя |
| `notifications` | `toast_corner` | `TOP_RIGHT` | `TOP_RIGHT`, `TOP_LEFT`, `BOTTOM_RIGHT`, `BOTTOM_LEFT` |
| `notifications` | `toast_max_visible` | 3 | 1…5 |
| `notifications` | `toast_seconds` | 5 | 2…30. Срочный тост держится вдвое дольше |
| `notifications` | `do_not_disturb` | false | тосты и звук только у срочных; почта пишется всегда |
| `notifications.category.<row>` | `toast` / `sound` / `chat` | см. ниже | true / false |
| `tasks` | `panel_mode` | `FULL` | `FULL`, `COMPACT`, `COLLAPSED`, `HIDDEN` |

`<row>` — `critical`, `orders`, `money`, `cargo`, `tutorial`, `board`, `admin`.

| Строка | toast | sound | chat |
| --- | --- | --- | --- |
| `critical` | true | true | true |
| `orders` | true | true | false |
| `money` | true | true | false |
| `cargo` | true | true | false |
| `tutorial` | true | false | false |
| `board` | false | false | false |
| `admin` | true | true | false |

### Клавиши (меню управления, не toml)

| Ключ перевода | По умолчанию | Что делает |
| --- | --- | --- |
| `key.aerocouriers.open_board` | J | открыть или закрыть планшет |
| `key.aerocouriers.task_panel` | не назначена | цикл режима панели задач, когда нет открытого экрана |

## 2. Админ-панель и экран уведомлений

Планшет, вкладка «Админ» (только оператор). Клиент шлёт намерение, сервер проверяет право.

| Вкладка | Элемент | Что меняет |
| --- | --- | --- |
| Заказы | фильтры списка | только клиентский отбор, мир не меняется |
| Заказы | строка заказа и «▸» | выбор заказа для кнопок ниже |
| Заказы | Поставить контейнер / Засчитать доставку / Провалить / Отменить / Продлить срок / Телепорт | действие оператора над выбранным заказом. Провал и отмена спрашивают подтверждение |
| Игроки | список онлайн и «Сбросить лицензию» | сброс лицензии выбранного игрока после «да» |
| Игроки | кнопки рангов I…V и админ | `PLAYER_RANK` — выдать лицензию этого ранга |
| Игроки | XP +100 / поставить 500 | опыт профиля |
| Игроки | накладная, деньги (+500) | предмет и баланс |
| Игроки | репутация станции (+10), «‹» «›» | репутация выбранной станции |
| Экономика | сегменты Мирный / Лёгкий / Нормальный / Сложный / Кастомный | только выбор на экране, пока не нажато «Применить» |
| Экономика | Применить, затем подтверждение | `economy.collateral_profile`. В Мирном залог открытых и принятых заказов становится 0, уже внесённый залог возвращается курьеру. Деревенская доска пересобирается |
| Экономика | Перезагрузить таблицу | перечитать датапаки ценностей |
| Экономика | Ценность (в руке) / Вес (в руке) | показать оценку предмета в руке |
| Экономика | Поставить ценность 100 / вес 1.0 | записать ценность или вес предмета в руке |
| Экономика | список связанных ключей | только просмотр (`p2p_min_collateral_ratio`, `collateral_factor`, `npc_custom_ratio`, потолки рангов, бесплатные стажёрские). Не редактируется с этой вкладки |
| Сервер | Пересобрать деревенские заказы | новая генерация NPC-доски |
| Сервер | строка `ключ = значение`, «‹» «›», увеличить | `CONFIG_SET`: bool переключается, число +1 |
| Сервер | Разрешить принять свой заказ | `debug.allow_self_accept` |
| Сервер | Обучение новых курьеров | `tutorial.tutorial_enabled` |

Экран «Уведомления и задачи» (Профиль, не админ-вкладка) пишет клиентский toml: клетки toast / sound / chat, угол тостов, сколько тостов, секунды (3, 5, 8, 10, 15, 20), режим панели, громкость, «Не беспокоить», автоотслеживание задач. Столбец «Почта» на экране всегда включён и в файл не пишется. Строка `admin` видна только оператору.

Окно «Маршрут на карте» (кнопка в своём профиле) пишет секцию `[route]` клиентского toml. Без Xaero переключатели окна неактивны (серые).

## 3. Нарочно не настраивается

| Что | Значение | Почему так |
| --- | --- | --- |
| Доли профилей залога | `peace` 0, `easy` 0.25, `normal` 0.5, `hard` 0.75 | Это уровни сложности, а не ползунок. Свободный диапазон есть только у `custom` |
| Потолок Кастомного | 200 % стоимости груза (`CUSTOM_MAX_RATIO` = 2) | Верх макета накладной, чтобы поле не было безграничным |
| Порядок сегментов | Мирный, Лёгкий, Нормальный, Сложный, Кастомный | Кастомный — не «между», а отдельный режим в конце. По сети идёт ключ (`peace`…), не номер enum |
| Потолки рангов I…V и админа | залог 200 / 600 / 2000 / 6000 / ∞ / ∞ шпор, размеры S / M / L / L / L / L, дистанция 1500 / 4000 / 10000 / ∞ / ∞ / ∞, активных 1 / 2 / 2 / 3 / 3 / 3 | Таблица GDD §4.2. В изумрудах залог делится на `emerald_value_spurs`. XL включается с `rank_required_xl` |
| Имена рангов | I Стажёр, II Курьер, III Пилот, IV Капитан, V Ас, Администратор | Ключи `rank.aerocouriers.rank1`…`rank5`, `admin` |
| Межмировой заказ и ранг | принять можно с ранга IV (Капитан), админ тоже; ниже — отказ `rank_cross_dimension` | Уже в `AcceptEligibility` с коммита `b51929b`. Новый порог «только капитан и ас» и новый UI не добавлялись: для планшета нет утверждённого макета |
| Срок заказа на накладной | 30 минут … 7 суток | Антиспам и верх календаря, `Pricing` |
| Досягаемость штампа | 8 блоков | Игрок должен стоять у груза |
| Сторона накладной на ящике | ближайшая горизонталь; диагональ 0,25 бл и «вплотную» 1,5 бл — взгляд; дальше 64 бл / другое измерение — восток | `WaybillFacing`; не конфиг. Сторона пишется в заказ один раз |
| Предупреждения короче 10 минут | не шлются | На коротком заказе 5/2/1 не успевают |
| Шаги обучения | ровно 4 | Сценарий FLOW, не список в конфиге |
| Радиус зоны B учебного заказа | 8 блоков | Короткий учебный маршрут |
| Точка B учебного заказа | 60…120 блоков от A | То же |
| Полоска action bar обучения | раз в 20 тиков | Чтобы не спамить |
| Журнал почты | всегда пишется | Каналы toast/sound/chat можно выключить, историю — нет |
| «Завершено сегодня» | 24000 тиков | Игровые сутки, не календарь |
| Строк на панели задач | 4 | Макет панели |
| Пауза планшета | экран не ставит одиночную игру на паузу | Иначе сервер не шлёт шаги обучения и тосты |
