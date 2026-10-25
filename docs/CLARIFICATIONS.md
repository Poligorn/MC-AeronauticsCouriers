# Aeronautics Couriers — уточнения к ГДД v3 (25.10.2026)

Источник истины — [`GDD.md`](GDD.md). Здесь собраны решения и замечания, принятые после его написания.

## Название и идентификаторы
- Название мода: **Aeronautics Couriers** (раньше Pilot's Freight).
- Mod ID: `aerocouriers`. Пакет: `dev.poligorn.aerocouriers`. Главный класс: `AeronauticsCouriers`.
- Конфиги: `aerocouriers-server.toml`, `aerocouriers-client.toml`. Команды: `/couriers ...`.
- Целевая сборка: MC-Ikaria «Aeronautics Ikaria» (https://github.com/Poligorn/MC-Ikaria, packwiz, ветка `dev`), MC 1.21.1, NeoForge 21.1.250 на клиенте, **21.1.235 на сервере — минимальная поддерживаемая версия NeoForge = 21.1.235**.

## Погоны (рендер лицензии)
- Погоны — перенос из клиентского мода автора SkyPact Pilot Wings (`pilotwings`, исходники в `pilotwings_src.zip`, MIT, автор тот же).
- Сейчас он вешает `ICurioRenderer` на пять предметов KubeJS `kubejs:pilot_license_cadet/second/first/captain/admin`, ранг берётся из ID; две плоские пластины над плечами + glow-слой (`RenderType.eyes`).
- В новом моде: один предмет `aerocouriers:pilot_license`, ранг — Data Component `aerocouriers:license_rank` на предмете (сервер обновляет при повышении), рендер выбирает текстуру по компоненту. Текстуры: cadet → I, second → II, first → III, captain → IV, новая для V (Ас, пока заглушка), admin — отдельная (выдаёт оператор).
- Миграция: старая `kubejs:pilot_license_*` в слоте Curios при входе заменяется новой лицензией с владельцем и стартовым XP по рангу (таблица в конфиге).

## Особенности сборки MC-Ikaria (важно для дизайна)
1. **Мир — небесные острова, обычных деревень почти нет.** Деревенские заказы (атлас по колоколам) на Ikaria не заработают. Нужен абстрактный источник NPC-заказов: интерфейс `ContractSource`, реализации — деревни (для обычных миров) и «почтовые станции» (блок/точка, которую ставит админ или структура). Решение по станциям ещё за командой — заложить расширяемость, деревни сделать первой реализацией.
2. **FFItemWeight** уже в сборке со своими весами предметов. Если мод установлен — брать вес груза из него (soft-dep), иначе свой `item_weights.json`.
3. **Дыра:** при провале деревенского/NPC-заказа груз НЕ отдаётся курьеру (иначе это покупка груза по оценке, выгодно при заниженной оценке редкого груза). Груз NPC-заказа при провале уничтожается.
4. В сборке есть мод Envelope со своими «курьерами» — избегать путаницы терминов в UI.
5. Waystones в сборке телепортируют только в пределах одного измерения — MVP «обе точки в одном измерении» согласуется.
6. ⚠️ Уточнить у Simulated Project, можно ли использовать слово «Aeronautics» в названии аддона.

## Кнопка открытия окна (фишка из Create: Marketplace)
- Клиентский конфиг `aerocouriers-client.toml` `[button]`: `position = CORNER | INVENTORY_SIDE | CUSTOM | HIDDEN` (по умолчанию INVENTORY_SIDE), `custom_x`, `custom_y` (0..4000).
- Marketplace (GPL-3.0, MakotoPD/CreateMarketplace, `ClientGameEvents.onScreenInit`, `ScreenEvent.Init.Post`): CORNER → `(2, height-22)`, INVENTORY_SIDE → `(guiLeft-22, guiTop+10)`. Мы — только на экране инвентаря игрока. С EMI/JEI — встраиваться в их ряд при значении по умолчанию. Код Marketplace не копировать (лицензия мода ещё не выбрана), только идея.

## Xaero (временные точки) — проверено по байткоду Xaero's Minimap 26.6.0
- Подробно: [`research/xaero.md`](research/xaero.md).
- Основной путь: сторонние точки `xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints` (Xaero ≥ 26.2.0), origin `aerocouriers:orders`, id `order-<uuid>`, `temp = true`, снимать `remove(id)` при завершении заказа, `clearOrigin` при выходе.
- Откат для Xaero < 26.2: `Waypoint(..., WaypointPurpose.DESTINATION, temp=true)`.
- `compileOnly` из `https://chocolateminecraft.com/maven` (`xaero.minimap:xaerominimap-neoforge-1.21.1:26.6.0`), jar Xaero НЕ класть в репозиторий (ARR). Мост грузится только при `ModList.isLoaded("xaerominimap")`, все вызовы в try/catch(Throwable), базовая навигация — луч + HUD.

## Мнения по «Вопросам к команде» (не окончательные решения)
- Numismatics — опционально (через интерфейс `Economy`).
- Контейнер L (многоблочный) — вне MVP, сначала S/M.
- Лицензия мода и PvP по умолчанию — решает команда; пока в репозитории не указывать лицензию, кроме явной пометки TODO.
