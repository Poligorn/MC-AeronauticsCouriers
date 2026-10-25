# Aeronautics Couriers

Курьерские контракты в духе EVE Online для **Create Aeronautics**: пилот с лицензией берёт контракт, вносит залог и сам решает, на чём и как везти **физический** грузовой контейнер (объект [Sable](https://github.com/ryanhcode/sable)) через полмира. Довёз — награда, залог обратно и опыт. Разбился — залог уходит заказчику.

- Mod ID: `aerocouriers`, пакет `dev.poligorn.aerocouriers`
- Minecraft **1.21.1**, NeoForge **≥ 21.1.235**, Java 21
- Обязательные зависимости: Create 6.x, Create Aeronautics (`aeronautics` + `simulated`), Sable, Curios 9. Sable Companion встроен (jar-in-jar).
- Опционально (в планах, через изолированные мосты): Create: Numismatics, Xaero's Minimap / World Map, Jade, FFItemWeight, моды приватов.
- Целевая сборка: [MC-Ikaria «Aeronautics Ikaria»](https://github.com/Poligorn/MC-Ikaria).

## Статус

**Ранняя альфа, не для игроков.** Сделаны итерация 0 (перенос лицензий с KubeJS), итерация 1 (прототип Sable) и итерация 2 (контракты и хранилище, пока только через команды). Подробно и актуально — в [`docs/STATUS.md`](docs/STATUS.md).

Что уже есть:
- Предмет `aerocouriers:pilot_license` для слота Curios `license`; владелец и ранг — Data Components; прогресс курьера — Data Attachment игрока (сохраняется после смерти).
- Погоны на плечах (рендер перенесён из SkyPact Pilot Wings), текстура выбирается по рангу на лицензии.
- Автоматический перенос старых `kubejs:pilot_license_*` при входе игрока.
- Отладочный грузовой контейнер, который превращается в физический объект Sable и сообщает свои мировые координаты и скорость.
- Игровые (P2P) контракты через команды: груз из сундука уходит в хранилище сервера, награда и залог удерживаются, доставка платит курьеру и кладёт груз в почту заказчика; провал, отмена и истечение срока возвращают деньги и предметы по правилам GDD. Деньги — внутренний баланс мода (мост к Numismatics позже), каждое движение пишется в `<мир>/aerocouriers/transactions.log`.

### Команды
| Команда | Кто | Что делает |
|---|---|---|
| `/couriers profile [игрок]` | все (чужой — оператор) | Профиль курьера: ранг, XP, статистика, надетая лицензия |
| `/couriers license give <игроки> [rank1..rank5\|admin]` | оператор | Выдать оформленную лицензию |
| `/couriers license migrate <игроки>` | оператор | Принудительно перенести старые KubeJS-лицензии |
| `/couriers xp add\|set <игроки> <число>` | оператор | Изменить XP (для проверки рангов и погон) |
| `/couriers spawncargo [pos] [fill 0..8]` | оператор | Создать отладочный контейнер и собрать его в объект Sable |
| `/couriers cargo list` | оператор | Последние известные координаты/скорости загруженных контейнеров |
| `/couriers contract create <сундук> <куда> <награда> <залог> <минуты> [радиус]` | все | Опубликовать заказ: всё содержимое сундука (в пределах 8 блоков) уходит в хранилище, награда + комиссия 5% списываются |
| `/couriers contract list` · `info <id>` | все | Открытые заказы и свои; подробности и манифест |
| `/couriers contract accept\|pickup\|deliver\|abandon <id>` | курьер с лицензией | Принять (залог), забрать груз у точки погрузки (заменяет появление контейнера до итерации 3), сдать в зоне доставки, отказаться |
| `/couriers contract cancel <id>` | заказчик | Снять открытый заказ: груз — в почту, награда — на счёт, комиссия сгорает |
| `/couriers contract fail <id>` | оператор | Провал «контейнер уничтожен»: залог и награда — заказчику, груз потерян |
| `/couriers mail [claim]` | все | Почта: доставленные грузы и возвраты, «забрать всё» |
| `/couriers balance [игрок]` · `/couriers money add\|set <игроки> <сп>` | все / оператор | Баланс внутренней экономики |

## Сборка

Нужен JDK 21.

```bash
./gradlew build
```

Готовый jar — `build/libs/aerocouriers-<версия>.jar` (Sable Companion уже внутри).

### Запуск в среде разработки
Sable, Create и Aeronautics подключены только для компиляции (`compileOnly`), их рантайм-зависимости (Veil, Flywheel, Ponder и т.д.) приносит сам пак. Для `./gradlew runClient` / `runServer` положите в `run/mods/` jar'ы той же версии, что в паке: `create-1.21.1-6.0.10.jar`, `create-aeronautics-bundled-1.21.1-1.3.0.jar`, `sable-neoforge-1.21.1-2.0.6.jar` (ссылки — в `mods/*.pw.toml` репозитория пака). Каталог `run/` в git не попадает. Сторонние jar'ы в репозиторий **не коммитим**.

### Автотесты (GameTest)
`./gradlew runGameTestServer` запускает headless-сервер в `run/gametest/` и прогоняет тесты из `dev.poligorn.aerocouriers.gametest` (лицензия и ранги, правила рангов, кодек профиля, контейнер в под-уровне Sable, полный цикл контракта с деньгами и почтой, провал и истечение срока, запреты, сохранение и загрузка SavedData). Тем же jar'ам нужно лежать в `run/gametest/mods/` (удобно — симлинком на `run/mods`).

## Документация
- [`docs/GDD.md`](docs/GDD.md) — дизайн-документ v3, источник истины.
- [`docs/CLARIFICATIONS.md`](docs/CLARIFICATIONS.md) — решения после GDD; при конфликте побеждают они.
- [`docs/STATUS.md`](docs/STATUS.md) — текущая итерация, сделано / в работе / дальше, известные проблемы, вопросы команде.
- [`docs/research/api-verification.md`](docs/research/api-verification.md) — какие внешние API сверены с артефактами и где GDD ошибался.
- [`docs/research/xaero.md`](docs/research/xaero.md) — временные точки Xaero и кнопка Create: Marketplace.

### Правила для ИИ-агентов (Cursor)
В [`.cursor/rules/`](.cursor/rules/) лежат правила, выжатые из GDD и CLARIFICATIONS, чтобы агент, подхвативший задачу, не перечитывал весь GDD:
- `00-project-core.mdc` — всегда в контексте: идентификаторы, версии, зависимости, инварианты, языковые соглашения, чек-лист «как возобновить работу».
- Тематические: `10-architecture`, `20-contracts`, `30-cargo-sable`, `40-license-ranks`, `50-economy`, `55-client-ui-xaero`, `57-config-datapack`, `60-ikaria-pack`.
- `90-maintaining-docs.mdc` — обязанность обновлять `docs/STATUS.md`, чек-лист GDD §16 и правила при каждом решении или завершённом шаге.

## Авторы и лицензия
Автор — Poligorn (Егор). Рендер погон перенесён из собственного мода автора SkyPact Pilot Wings (MIT).

Лицензия мода **ещё не выбрана командой** (TODO, см. вопросы в `docs/STATUS.md`); в `neoforge.mods.toml` стоит `TODO`. Файл `LICENSE` (MIT) появился при создании репозитория — до решения команды на него не стоит опираться.
