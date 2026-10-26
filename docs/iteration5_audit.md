# Итерация 5 — аудит (финал 26.10.2026)

База: `cursor/waybill-crafting-flow-9b25` → ветка `cursor/iteration5-trainee-stations-45ff` (PR #16).

## Статус по GDD §6 и UI итерации 5

| Пункт | Статус | Где |
|-------|--------|-----|
| §6.0–6.3 Станции, атлас, генерация, деньги NPC | **Готово** | `PostStationBlock`, `StationContractSource`, `VillageCargo` |
| §6.4 Репутация (+1/−3, 10/25/50, +10/+20/+30%) | **Готово** | `StationReputation`, `StationAtlas`, `ContractManager` |
| §6.4 Редкий груз | **Не сделано** | — |
| §6.5 Стажировка | **Готово** | `TraineeRules`, `ContractManager`, подсказка на доске |
| §8.3 `item_values.json` (залог NPC) | **Готово** | `ItemValues`, `data/aerocouriers/item_values/default.json` |
| Окно станции (не только доска) | **Готово** | `PostStationScreen`, `StationPackets` |
| Уведомления (колокол, лог, звук) | **Готово** | `NotificationLog`, `CouriersNotifications` |
| Доска: без «Подробнее», confirm accept | **Готово** | `CourierBoardScreen`, `BoardAcceptConfirmScreen` |
| Фон планшета `#140400` + сетка | **Готово** | `TabletGui` |

## Остаётся

1. Редкий груз при высокой репутации (§6.4).
2. Клиентская проверка итерации 5 (живой игрок, биомы, Ikaria).
3. Вкладка «Атлас» на планшете (список станций в UI).

## Jar / артефакты

- `aerocouriers-0.1.0-alpha.jar` в `/opt/cursor/artifacts/`
- Превью: доска, confirm, фон, окно станции, уведомления
