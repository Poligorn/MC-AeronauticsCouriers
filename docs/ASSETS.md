# Ассеты для ручной перерисовки (выгрузка 0.1.12, чистка 0.1.13)

В 0.1.13 из мода и из этой выгрузки убраны спрайты, на которые код не ссылался (`*_alt` контейнера, старые `tab`/`paper_*`/`scroller`/`tablet_scroller`/`tablet_grid`/`tablet_list_row`, иконки `bell`/`envelope`/`envelope_tablet` и их нарезка). `overview.png` снят до чистки и может показывать удалённые клетки. Папки `world/` нет.

Выгрузка лежит вне репозитория: `/opt/cursor/artifacts/assets_0.1.12/`.
Рядом каталог `x4/` — те же PNG, увеличенные в 4 раза (nearest neighbor).
Сводный лист с подписями: `assets_overview.png` (имя файла, размер в px, откуда взято).
Полный перечень с размерами — `catalog.json`.

Натуральный размер — пиксели GUI (GUI scale 3 на 1920×1080: один пиксель листа = один пиксель макета 640×360).

## Куда класть нарисованный PNG

Если файл есть, код рисует его вместо прежней заливки и растягивает на прямоугольник элемента.
Нет файла — остаётся старая отрисовка. Класть в ресурсы мода или в ресурспак, затем F3+T.

| Элемент | Путь в ресурсах | Прямоугольник |
|---|---|---|
| Доска накладной (картон, без тени) | `assets/aerocouriers/textures/gui/override/waybill_board.png` | лицо доски на базовой раскладке, выгрузка `code/waybill_board.png` 564×324 вместе с тенью |
| Бумага с фестоном | `textures/gui/override/waybill_paper.png` | `code/waybill_paper.png` 507×299 |
| Зажим | `textures/gui/override/waybill_clip.png` | 157×58 (`code/waybill_clip.png`) |
| Печать «ОПУБЛИКОВАНО» | `textures/gui/override/waybill_stamp.png` | 100×20, заменяет всю печать, включая слово |
| Иконка шпоры на листе | `textures/gui/override/waybill_spur.png` | 8×7 |
| Фон метки «+N» | `textures/gui/override/hidden_stacks_badge.png` | фон плашки; цифра `+N` по-прежнему шрифтом |
| Дуги антенны, передача | `textures/gui/override/antenna_arc_send.png` | 72×36, центр — лампа |
| Дуги антенны, приём | `textures/gui/override/antenna_arc_receive.png` | 72×36 |
| Вспышка лампы | `textures/gui/override/antenna_lamp_flash.png` | 12×12, и для передачи, и для приёма |

Луч в мире — цветные квады в `NavRenderer`, текстуры нет. Цвет по умолчанию совпадает с точкой Xaero: погрузка `#FFFF55`, доставка `#55FFFF`. Образцы: `code/nav_beam_pickup.png`, `code/nav_beam_delivery.png`.

Глифы `PixelGlyphs` (пин, сетка, сундук и остальные) по-прежнему рисуются кодом. PNG в выгрузке — для срисовки; отдельного подхвата у каждого глифа нет.

Зажим накладной рисуется кодом (`WaybillCreateGui.drawClip`). Рамка планшета и антенна — спрайты `tablet_frame` (9-slice, отступ 14) и `tablet_decor_antenna`. Вкладки планшета — `tablet_tab` / `tablet_tab_highlighted` / `tablet_tab_selected`.

## Контейнеры

| Размер | Текстуры | Модель |
|---|---|---|
| S | `textures/block/cargo_container_{side,top,bottom,side_waybill}.png` 16×16 | куб, все грани `side`; накладная на `east` |
| M | `cargo_container_m_{side_upper,side_lower,side_lower_waybill,top,bottom}` 16×16 | две половины 1×2 |
| L | `cargo_container_l_{side,side_waybill,top,bottom}` 32×32 | сегменты `cargo_container_l_seg*` (2×2×2) |
| XL | `cargo_container_xl_{side,side_waybill}` 32×48, `{top,bottom}` 32×32 | сегменты `cargo_container_xl_seg*` (2×2×3, 12 клеток) |

Макет E2 (31.10.2026): металл, гофра, приглушённые S синий / M зелёный / L охра / XL бордовый. Дверей, табличек и букв нет. Единственная наклейка — накладная-клипборд 10×12 с тенью (`*_waybill`). Спрайты пробковой доски `gui/sprites/cork/crate_*` не трогали.

Контейнер — физический объект Sable. Текстуры блока перечислены выше. Папки `world/` в выгрузке нет.

## 9-slice

Отступы: левый, верхний, правый, нижний. Последняя колонка — номинальный размер спрайта в mcmeta.
Нарезанные углы и рёбра лежат в `slices/` выгрузки (имя `*_tl.png`, `_t`, `_tr`, `_l`, `_c`, `_r`, `_bl`, `_b`, `_br`).

| Файл | PNG | Отступы L,T,R,B | Номинал mcmeta |
|---|---|---|---|
| `button.png` | 200×20 | 3, 4, 3, 3 | 200×20 |
| `button_disabled.png` | 200×20 | 3, 4, 3, 2 | 200×20 |
| `button_highlighted.png` | 200×20 | 3, 4, 3, 3 | 200×20 |
| `tablet_button.png` | 200×20 | 2, 2, 2, 2 | 200×20 |
| `tablet_button_disabled.png` | 200×20 | 2, 2, 2, 2 | 200×20 |
| `tablet_button_highlighted.png` | 200×20 | 2, 2, 2, 2 | 200×20 |
| `tablet_frame.png` | 84×84 | 14, 14, 14, 14 | 84×84 |
| `tablet_tab.png` | 130×24 | 2, 2, 2, 0 | 130×24 |
| `tablet_tab_highlighted.png` | 130×24 | 2, 2, 2, 0 | 130×24 |
| `tablet_tab_selected.png` | 130×24 | 2, 2, 2, 0 | 130×24 |

## Код
| Файл | Размер | Откуда |
|---|---|---|
| `code/waybill_board.png` | 564×324 | code |
| `code/waybill_paper.png` | 507×299 | code |
| `code/waybill_clip.png` | 157×58 | code |
| `code/waybill_spur.png` | 8×7 | code |
| `code/waybill_stamp.png` | 100×20 | code |
| `code/hidden_stacks_badge.png` | 22×10 | code |
| `code/antenna_arc_send.png` | 72×36 | code |
| `code/antenna_arc_receive.png` | 72×36 | code |
| `code/antenna_lamp_send.png` | 12×12 | code |
| `code/antenna_lamp_receive.png` | 12×12 | code |
| `code/nav_beam_pickup.png` | 18×128 | code |
| `code/nav_beam_delivery.png` | 18×128 | code |
| `code/glyph_house.png` | 7×7 | code |
| `code/glyph_face.png` | 7×7 | code |
| `code/glyph_arrow.png` | 7×5 | code |
| `code/glyph_warn.png` | 7×6 | code |
| `code/glyph_clock.png` | 9×8 | code |
| `code/glyph_box.png` | 9×7 | code |
| `code/glyph_lock.png` | 7×7 | code |
| `code/glyph_sheet_grid.png` | 9×5 | code |
| `code/glyph_pin.png` | 7×7 | code |
| `code/glyph_check.png` | 7×5 | code |
| `code/glyph_cross.png` | 5×5 | code |
| `code/glyph_up.png` | 7×5 | code |
| `code/glyph_chest.png` | 9×6 | code |

## Спрайты GUI
| Файл | Размер | Откуда |
|---|---|---|
| `textures/gui/sprites/button.png` | 200×20 | texture:textures/gui/sprites/button.png |
| `textures/gui/sprites/button_disabled.png` | 200×20 | texture:textures/gui/sprites/button_disabled.png |
| `textures/gui/sprites/button_highlighted.png` | 200×20 | texture:textures/gui/sprites/button_highlighted.png |
| `textures/gui/sprites/icon/clock_tablet.png` | 16×16 | texture:textures/gui/sprites/icon/clock_tablet.png |
| `textures/gui/sprites/icon/crate_tablet.png` | 16×16 | texture:textures/gui/sprites/icon/crate_tablet.png |
| `textures/gui/sprites/icon/lock_tablet.png` | 16×16 | texture:textures/gui/sprites/icon/lock_tablet.png |
| `textures/gui/sprites/icon/map_pin_tablet.png` | 16×16 | texture:textures/gui/sprites/icon/map_pin_tablet.png |
| `textures/gui/sprites/icon/rare_cargo_tablet.png` | 16×16 | texture:textures/gui/sprites/icon/rare_cargo_tablet.png |
| `textures/gui/sprites/icon/star_badge_tablet.png` | 16×16 | texture:textures/gui/sprites/icon/star_badge_tablet.png |
| `textures/gui/sprites/icon/village_tablet.png` | 16×16 | texture:textures/gui/sprites/icon/village_tablet.png |
| `textures/gui/sprites/tablet_button.png` | 200×20 | texture:textures/gui/sprites/tablet_button.png |
| `textures/gui/sprites/tablet_button_disabled.png` | 200×20 | texture:textures/gui/sprites/tablet_button_disabled.png |
| `textures/gui/sprites/tablet_button_highlighted.png` | 200×20 | texture:textures/gui/sprites/tablet_button_highlighted.png |
| `textures/gui/sprites/tablet_decor_antenna.png` | 24×78 | texture:textures/gui/sprites/tablet_decor_antenna.png |
| `textures/gui/sprites/tablet_frame.png` | 84×84 | texture:textures/gui/sprites/tablet_frame.png |
| `textures/gui/sprites/tablet_loading_gear.png` | 20×20 | texture:textures/gui/sprites/tablet_loading_gear.png |
| `textures/gui/sprites/tablet_tab.png` | 130×24 | texture:textures/gui/sprites/tablet_tab.png |
| `textures/gui/sprites/tablet_tab_highlighted.png` | 130×24 | texture:textures/gui/sprites/tablet_tab_highlighted.png |
| `textures/gui/sprites/tablet_tab_selected.png` | 130×24 | texture:textures/gui/sprites/tablet_tab_selected.png |

## Текстуры блоков
| Файл | Размер | Откуда |
|---|---|---|
| `textures/block/cargo_container_bottom.png` | 16×16 | texture:textures/block/cargo_container_bottom.png |
| `textures/block/cargo_container_l_bottom.png` | 32×32 | texture:textures/block/cargo_container_l_bottom.png |
| `textures/block/cargo_container_l_side.png` | 32×32 | texture:textures/block/cargo_container_l_side.png |
| `textures/block/cargo_container_l_side_waybill.png` | 32×32 | texture:textures/block/cargo_container_l_side_waybill.png |
| `textures/block/cargo_container_l_top.png` | 32×32 | texture:textures/block/cargo_container_l_top.png |
| `textures/block/cargo_container_m_bottom.png` | 16×16 | texture:textures/block/cargo_container_m_bottom.png |
| `textures/block/cargo_container_m_side_lower.png` | 16×16 | texture:textures/block/cargo_container_m_side_lower.png |
| `textures/block/cargo_container_m_side_lower_waybill.png` | 16×16 | texture:textures/block/cargo_container_m_side_lower_waybill.png |
| `textures/block/cargo_container_m_side_upper.png` | 16×16 | texture:textures/block/cargo_container_m_side_upper.png |
| `textures/block/cargo_container_m_top.png` | 16×16 | texture:textures/block/cargo_container_m_top.png |
| `textures/block/cargo_container_side.png` | 16×16 | texture:textures/block/cargo_container_side.png |
| `textures/block/cargo_container_side_waybill.png` | 16×16 | texture:textures/block/cargo_container_side_waybill.png |
| `textures/block/cargo_container_top.png` | 16×16 | texture:textures/block/cargo_container_top.png |
| `textures/block/cargo_container_xl_bottom.png` | 32×32 | texture:textures/block/cargo_container_xl_bottom.png |
| `textures/block/cargo_container_xl_side.png` | 32×48 | texture:textures/block/cargo_container_xl_side.png |
| `textures/block/cargo_container_xl_side_waybill.png` | 32×48 | texture:textures/block/cargo_container_xl_side_waybill.png |
| `textures/block/cargo_container_xl_top.png` | 32×32 | texture:textures/block/cargo_container_xl_top.png |

## Текстуры предметов
| Файл | Размер | Откуда |
|---|---|---|
| `textures/item/courier_stamp.png` | 16×16 | texture:textures/item/courier_stamp.png |
| `textures/item/order_tablet.png` | 16×16 | texture:textures/item/order_tablet.png |
| `textures/item/pilot_license_admin.png` | 16×16 | texture:textures/item/pilot_license_admin.png |
| `textures/item/pilot_license_rank1.png` | 16×16 | texture:textures/item/pilot_license_rank1.png |
| `textures/item/pilot_license_rank2.png` | 16×16 | texture:textures/item/pilot_license_rank2.png |
| `textures/item/pilot_license_rank3.png` | 16×16 | texture:textures/item/pilot_license_rank3.png |
| `textures/item/pilot_license_rank4.png` | 16×16 | texture:textures/item/pilot_license_rank4.png |
| `textures/item/pilot_license_rank5.png` | 16×16 | texture:textures/item/pilot_license_rank5.png |
| `textures/item/transport_waybill.png` | 16×16 | texture:textures/item/transport_waybill.png |

## Эполеты
| Файл | Размер | Откуда |
|---|---|---|
| `textures/entity/epaulette_admin.png` | 16×16 | texture:textures/entity/epaulette_admin.png |
| `textures/entity/epaulette_admin_glow.png` | 16×16 | texture:textures/entity/epaulette_admin_glow.png |
| `textures/entity/epaulette_rank1.png` | 16×16 | texture:textures/entity/epaulette_rank1.png |
| `textures/entity/epaulette_rank1_glow.png` | 16×16 | texture:textures/entity/epaulette_rank1_glow.png |
| `textures/entity/epaulette_rank2.png` | 16×16 | texture:textures/entity/epaulette_rank2.png |
| `textures/entity/epaulette_rank2_glow.png` | 16×16 | texture:textures/entity/epaulette_rank2_glow.png |
| `textures/entity/epaulette_rank3.png` | 16×16 | texture:textures/entity/epaulette_rank3.png |
| `textures/entity/epaulette_rank3_glow.png` | 16×16 | texture:textures/entity/epaulette_rank3_glow.png |
| `textures/entity/epaulette_rank4.png` | 16×16 | texture:textures/entity/epaulette_rank4.png |
| `textures/entity/epaulette_rank4_glow.png` | 16×16 | texture:textures/entity/epaulette_rank4_glow.png |
| `textures/entity/epaulette_rank5.png` | 16×16 | texture:textures/entity/epaulette_rank5.png |
| `textures/entity/epaulette_rank5_glow.png` | 16×16 | texture:textures/entity/epaulette_rank5_glow.png |

## Прочие текстуры
| Файл | Размер | Откуда |
|---|---|---|
| `textures/slot/order_tablet.png` | 16×16 | texture:textures/slot/order_tablet.png |
