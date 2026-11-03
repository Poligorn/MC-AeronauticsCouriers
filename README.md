# Aeronautics Couriers

Курьерские контракты в духе EVE Online для **Create Aeronautics**: пилот с лицензией берёт контракт, вносит залог и сам решает, на чём и как везти **физический** грузовой контейнер (объект [Sable](https://github.com/ryanhcode/sable)) через полмира. Довёз — награда, залог обратно и опыт. Разбился — залог уходит заказчику.

- Mod ID: `aerocouriers`, пакет `dev.poligorn.aerocouriers`
- Minecraft **1.21.1**, NeoForge **≥ 21.1.235**, Java 21
- Обязательные зависимости: Create 6.x, Create Aeronautics (`aeronautics` + `simulated`), Sable, Curios 9. Sable Companion встроен (jar-in-jar).
- Опционально (в планах, через изолированные мосты): Create: Numismatics, Xaero's Minimap / World Map, Jade, FFItemWeight, моды приватов.
- Целевая сборка: [MC-Ikaria «Aeronautics Ikaria»](https://github.com/Poligorn/MC-Ikaria).

### Команды
| Команда | Кто | Что делает |
|---|---|---|
| `/couriers ui` | все | Открыть доску заказов (нужен планшет заказов, если `[access] require_tablet = true`) |
| `/couriers waybill give <игроки>` (`stamp give` — алиас) | оператор | Выдать транспортные накладные |
| `/couriers profile [игрок]` | все (чужой — оператор) | Профиль курьера: ранг, XP, статистика, надетая лицензия |
| `/couriers license give <игроки> [rank1..rank5\|admin]` | оператор | Выдать оформленную лицензию |
| `/couriers license migrate <игроки>` | оператор | Принудительно перенести старые KubeJS-лицензии |
| `/couriers xp add\|set <игроки> <число>` | оператор | Изменить XP (для проверки рангов и погон) |
| `/couriers spawncargo [pos] [fill 0..8]` | оператор | Создать отладочный контейнер и собрать его в объект Sable |
| `/couriers cargo list` | оператор | Последние известные координаты/скорости загруженных контейнеров |
| `/couriers contract create <сундук> <куда> <награда> <залог> <минуты> [радиус]` | все (для операторов/тестов) | Опубликовать заказ без штампа: груз из сундука в хранилище, контейнер появится при подлёте курьера |
| `/couriers contract list` · `info <id>` | все | Открытые заказы и свои; подробности и манифест |
| `/couriers contract accept\|abandon <id>` | курьер с лицензией | Принять (залог списывается, показывается риск) или отказаться. Контейнер появляется сам, когда курьер подлетает к точке погрузки; доставка засчитывается сама, когда контейнер стоит в зоне доставки, а курьер рядом |
| `/couriers contract forcespawn\|forcedeliver <id>` | оператор | Поставить контейнер принятого заказа сейчас; засчитать доставку там, где контейнер (курьер должен быть в сети) |
| `/couriers contract cancel <id>` | заказчик | Снять открытый заказ: груз — в почту, награда — на счёт, комиссия сгорает |
| `/couriers contract fail <id>` | оператор | Провал «контейнер уничтожен»: залог и награда — заказчику, груз потерян |
| сломать или взорвать контейнер | любой игрок (если `piracy.enabled`) | Провал `DAMAGED`: залог и награда — заказчику, доля груза `piracy.drop_ratio` выпадает в мир (если сломал не сам курьер) |
| `/couriers mail [claim]` | все | Почта: доставленные грузы и возвраты, «забрать всё» |
| `/couriers balance [игрок]` · `/couriers money add\|set <игроки> <сп>` | все / оператор | Баланс внутренней экономики |
| `/couriers station list` | все (оператор видит все станции) | Почтовые станции, которые вы открыли (лицензированный игрок открывает станцию, подойдя ближе 32 блоков) |
| `/couriers station generate` | оператор | Сейчас опубликовать NPC-заказы между станциями в атласе |
| `/couriers station rename <pos> <имя>` | оператор | Переименовать почтовую станцию |

### Команды админ-панели (`/couriers admin`, permission 2)

| Команда | Что делает |
|---|---|
| `contract list` | Список всех активных/открытых заказов |
| `contract spawn\|deliver\|fail\|cancel\|extend\|tp <id>` | Те же действия, что на подвкладке «Заказы» |
| `player rank <игрок> <rank1…rank5\|admin>` | Выдать ранг (лицензия + XP) |
| `player xp add\|set <игрок> <число>` | Изменить XP |
| `player rep <игрок> <станция> <значение>` | Репутация станции (по имени из атласа) |
| `player license <игрок> <ранг>` | Выдать лицензию указанного ранга |
| `player waybill <игрок>` | Выдать накладные |
| `player money <игрок> <сп>` | Добавить спуры |
| `economy reload` | Перезагрузить `item_values`, `item_weights`, `village_cargo` |
| `economy value [spurs]` · `economy weight [вес]` | Показать или задать override для предмета в руке |
| `server regen` | Снять открытые NPC-заказы и сгенерировать новые |
| `server config list\|get <key>\|set <key> <value>` | Просмотр/изменение части `aerocouriers-server.toml` |
| `server debug allow_self_accept [true\|false]` | Переключить или задать `debug.allow_self_accept` |

Те же действия доступны на вкладке **«Админ-панель»** планшета заказов (только для операторов).

## Авторы и лицензия
Автор — Poligorn (Егор). Рендер погон перенесён из собственного мода автора SkyPact Pilot Wings (MIT).

GUI palette inspired by Create Style Interface by ogabasferr (original sprites in this repo; pack assets not redistributed).

Лицензия — **MIT** (см. [`LICENSE`](LICENSE)). Код Create: Marketplace не используется и не копируется — только идея настраиваемой кнопки.
