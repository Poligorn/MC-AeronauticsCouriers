# Итерация 5 — аудит (финал 26.10.2026)

База: `cursor/iteration5-trainee-stations-45ff` (PR #16) + редкий груз (`cursor/rare-cargo-iteration5-f22f`).

## Статус по GDD §6 и UI итерации 5

| Пункт | Статус | Где |
|-------|--------|-----|
| §6.0–6.3 Станции, атлас, генерация, деньги NPC | **Готово** | `PostStationBlock`, `StationContractSource`, `VillageCargo` |
| §6.4 Репутация (+1/−3, пороги, бонус награды) | **Готово** | `StationReputation`, `CouriersServerConfig`, `ContractManager` |
| §6.4 Редкий груз | **Готово** | `VillageCargo` (`village_cargo/rare/`), `Escrow.villageRare`, `BoardRowRenderer` |
| §6.5 Стажировка | **Готово** | `TraineeRules`, `ContractManager`, подсказка на доске |
| §8.3 `item_values.json` (залог NPC) | **Готово** | `ItemValues`, `data/aerocouriers/item_values/default.json` |
| Окно станции | **Готово** | `PostStationScreen`, `StationPackets` |
| Уведомления (колокол, лог, звук) | **Готово** | `NotificationLog`, `CouriersNotifications` |
| Доска: confirm accept, без «Подробнее» | **Готово** | `CourierBoardScreen`, `BoardAcceptConfirmScreen` |
| Планшет: фон, рамка, вкладки Текущие/История | **Готово** | `TabletGui`, `CourierBoardScreen` |

## Остаётся

1. Клиентская проверка итерации 5 (живой игрок, биомы, Ikaria).
2. Вкладка «Атлас» на планшете (список станций в UI).

## Jar / артефакты

- `build/libs/aerocouriers-0.1.0-alpha.jar`
- Превью: доска, confirm, фон, окно станции, уведомления
