# Ассеты для ручной перерисовки (0.1.12)

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

Спрайт `textures/gui/sprites/clipboard_clip.png` в игре не блитится: живой зажим — код выше. Рамка планшета и антенна — спрайты `tablet_frame` (9-slice, отступ 14) и `tablet_decor_antenna`.

## Контейнеры

| Размер | Текстуры | Модель |
|---|---|---|
| S | `textures/block/cargo_container_*.png` без суффикса | `cargo_container_s` |
| M | `cargo_container_m_*` | нижняя и верхняя половины |
| L | `cargo_container_l_*` | сегменты `cargo_container_l_seg*` |
| XL | отдельных PNG нет | blockstate собирается из сегментов L |

Снимки блока в мире и предмета в руке — в `world/` этой же выгрузки, если клиент их снял (см. отчёт 0.1.12).

## 9-slice

Отступы: левый, верхний, правый, нижний. Последняя колонка — номинальный размер спрайта в mcmeta.
Нарезанные углы и рёбра лежат в `slices/` выгрузки (имя `*_tl.png`, `_t`, `_tr`, `_l`, `_c`, `_r`, `_bl`, `_b`, `_br`).

| Файл | PNG | Отступы L,T,R,B | Номинал mcmeta |
|---|---|---|---|
| `button.png` | 200×20 | 3, 4, 3, 3 | 200×20 |
| `button_disabled.png` | 200×20 | 3, 4, 3, 2 | 200×20 |
| `button_highlighted.png` | 200×20 | 3, 4, 3, 3 | 200×20 |
| `inset.png` | 32×32 | 3, 3, 3, 3 | 32×32 |
| `panel.png` | 64×64 | 4, 6, 4, 4 | 64×64 |
| `paper_field.png` | 200×20 | 2, 2, 2, 2 | 200×20 |
| `paper_field_highlighted.png` | 200×20 | 2, 2, 2, 2 | 200×20 |
| `paper_sheet.png` | 48×48 | 5, 5, 5, 5 | 48×48 |
| `scroller.png` | 6×9 | 1, 2, 1, 2 | 6×9 |
| `scroller_track.png` | 6×32 | 1, 2, 1, 2 | 6×32 |
| `tab.png` | 130×24 | 2, 2, 2, 0 | 130×24 |
| `tab_highlighted.png` | 130×24 | 2, 2, 2, 0 | 130×24 |
| `tab_selected.png` | 130×24 | 2, 2, 2, 0 | 130×24 |
| `tablet_button.png` | 200×20 | 2, 2, 2, 2 | 200×20 |
| `tablet_button_disabled.png` | 200×20 | 2, 2, 2, 2 | 200×20 |
| `tablet_button_highlighted.png` | 200×20 | 2, 2, 2, 2 | 200×20 |
| `tablet_frame.png` | 84×84 | 14, 14, 14, 14 | 84×84 |
| `tablet_list_row.png` | 32×22 | 2, 2, 2, 2 | 32×22 |
| `tablet_scroller.png` | 6×9 | 1, 2, 1, 2 | 6×9 |
| `tablet_scroller_track.png` | 6×32 | 1, 2, 1, 2 | 6×32 |
| `tablet_tab.png` | 130×24 | 2, 2, 2, 0 | 130×24 |
| `tablet_tab_highlighted.png` | 130×24 | 2, 2, 2, 0 | 130×24 |
| `tablet_tab_selected.png` | 130×24 | 2, 2, 2, 0 | 130×24 |
| `text_field.png` | 200×20 | 2, 2, 2, 2 | 200×20 |
| `text_field_highlighted.png` | 200×20 | 2, 2, 2, 2 | 200×20 |
| `title_label.png` | 128×18 | 6, 4, 6, 4 | 128×18 |

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
| `code/glyph_star.png` | 9×9 | code |
| `code/glyph_person.png` | 9×8 | code |
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
| `textures/gui/sprites/clipboard_clip.png` | 128×38 | texture:textures/gui/sprites/clipboard_clip.png |
| `textures/gui/sprites/container_slot.png` | 18×18 | texture:textures/gui/sprites/container_slot.png |
| `textures/gui/sprites/icon/bell.png` | 16×16 | texture:textures/gui/sprites/icon/bell.png |
| `textures/gui/sprites/icon/bell_tablet.png` | 16×16 | texture:textures/gui/sprites/icon/bell_tablet.png |
| `textures/gui/sprites/icon/clock_tablet.png` | 16×16 | texture:textures/gui/sprites/icon/clock_tablet.png |
| `textures/gui/sprites/icon/coin_tablet.png` | 16×16 | texture:textures/gui/sprites/icon/coin_tablet.png |
| `textures/gui/sprites/icon/crate_tablet.png` | 16×16 | texture:textures/gui/sprites/icon/crate_tablet.png |
| `textures/gui/sprites/icon/envelope.png` | 16×16 | texture:textures/gui/sprites/icon/envelope.png |
| `textures/gui/sprites/icon/envelope_tablet.png` | 16×16 | texture:textures/gui/sprites/icon/envelope_tablet.png |
| `textures/gui/sprites/icon/lock_tablet.png` | 16×16 | texture:textures/gui/sprites/icon/lock_tablet.png |
| `textures/gui/sprites/icon/map_pin_tablet.png` | 16×16 | texture:textures/gui/sprites/icon/map_pin_tablet.png |
| `textures/gui/sprites/icon/rare_cargo_tablet.png` | 16×16 | texture:textures/gui/sprites/icon/rare_cargo_tablet.png |
| `textures/gui/sprites/icon/star_badge_tablet.png` | 16×16 | texture:textures/gui/sprites/icon/star_badge_tablet.png |
| `textures/gui/sprites/icon/village_tablet.png` | 16×16 | texture:textures/gui/sprites/icon/village_tablet.png |
| `textures/gui/sprites/inset.png` | 32×32 | texture:textures/gui/sprites/inset.png |
| `textures/gui/sprites/panel.png` | 64×64 | texture:textures/gui/sprites/panel.png |
| `textures/gui/sprites/paper_field.png` | 200×20 | texture:textures/gui/sprites/paper_field.png |
| `textures/gui/sprites/paper_field_highlighted.png` | 200×20 | texture:textures/gui/sprites/paper_field_highlighted.png |
| `textures/gui/sprites/paper_sheet.png` | 48×48 | texture:textures/gui/sprites/paper_sheet.png |
| `textures/gui/sprites/paper_slot.png` | 18×18 | texture:textures/gui/sprites/paper_slot.png |
| `textures/gui/sprites/scroller.png` | 6×9 | texture:textures/gui/sprites/scroller.png |
| `textures/gui/sprites/scroller_track.png` | 6×32 | texture:textures/gui/sprites/scroller_track.png |
| `textures/gui/sprites/tab.png` | 130×24 | texture:textures/gui/sprites/tab.png |
| `textures/gui/sprites/tab_highlighted.png` | 130×24 | texture:textures/gui/sprites/tab_highlighted.png |
| `textures/gui/sprites/tab_selected.png` | 130×24 | texture:textures/gui/sprites/tab_selected.png |
| `textures/gui/sprites/tablet_button.png` | 200×20 | texture:textures/gui/sprites/tablet_button.png |
| `textures/gui/sprites/tablet_button_disabled.png` | 200×20 | texture:textures/gui/sprites/tablet_button_disabled.png |
| `textures/gui/sprites/tablet_button_highlighted.png` | 200×20 | texture:textures/gui/sprites/tablet_button_highlighted.png |
| `textures/gui/sprites/tablet_decor_antenna.png` | 24×78 | texture:textures/gui/sprites/tablet_decor_antenna.png |
| `textures/gui/sprites/tablet_frame.png` | 84×84 | texture:textures/gui/sprites/tablet_frame.png |
| `textures/gui/sprites/tablet_grid.png` | 9×9 | texture:textures/gui/sprites/tablet_grid.png |
| `textures/gui/sprites/tablet_list_row.png` | 32×22 | texture:textures/gui/sprites/tablet_list_row.png |
| `textures/gui/sprites/tablet_loading_gear.png` | 20×20 | texture:textures/gui/sprites/tablet_loading_gear.png |
| `textures/gui/sprites/tablet_scroller.png` | 6×9 | texture:textures/gui/sprites/tablet_scroller.png |
| `textures/gui/sprites/tablet_scroller_track.png` | 6×32 | texture:textures/gui/sprites/tablet_scroller_track.png |
| `textures/gui/sprites/tablet_tab.png` | 130×24 | texture:textures/gui/sprites/tablet_tab.png |
| `textures/gui/sprites/tablet_tab_highlighted.png` | 130×24 | texture:textures/gui/sprites/tablet_tab_highlighted.png |
| `textures/gui/sprites/tablet_tab_selected.png` | 130×24 | texture:textures/gui/sprites/tablet_tab_selected.png |
| `textures/gui/sprites/text_field.png` | 200×20 | texture:textures/gui/sprites/text_field.png |
| `textures/gui/sprites/text_field_highlighted.png` | 200×20 | texture:textures/gui/sprites/text_field_highlighted.png |
| `textures/gui/sprites/title_label.png` | 128×18 | texture:textures/gui/sprites/title_label.png |

## Текстуры блоков
| Файл | Размер | Откуда |
|---|---|---|
| `textures/block/cargo_container_bottom.png` | 16×16 | texture:textures/block/cargo_container_bottom.png |
| `textures/block/cargo_container_bottom_alt.png` | 16×16 | texture:textures/block/cargo_container_bottom_alt.png |
| `textures/block/cargo_container_door.png` | 16×16 | texture:textures/block/cargo_container_door.png |
| `textures/block/cargo_container_door_alt.png` | 16×16 | texture:textures/block/cargo_container_door_alt.png |
| `textures/block/cargo_container_l_bottom.png` | 32×32 | texture:textures/block/cargo_container_l_bottom.png |
| `textures/block/cargo_container_l_door.png` | 32×32 | texture:textures/block/cargo_container_l_door.png |
| `textures/block/cargo_container_l_side.png` | 32×32 | texture:textures/block/cargo_container_l_side.png |
| `textures/block/cargo_container_l_side_waybill.png` | 32×32 | texture:textures/block/cargo_container_l_side_waybill.png |
| `textures/block/cargo_container_l_top.png` | 32×32 | texture:textures/block/cargo_container_l_top.png |
| `textures/block/cargo_container_m_bottom.png` | 16×16 | texture:textures/block/cargo_container_m_bottom.png |
| `textures/block/cargo_container_m_door_lower.png` | 16×16 | texture:textures/block/cargo_container_m_door_lower.png |
| `textures/block/cargo_container_m_door_upper.png` | 16×16 | texture:textures/block/cargo_container_m_door_upper.png |
| `textures/block/cargo_container_m_side_lower.png` | 16×16 | texture:textures/block/cargo_container_m_side_lower.png |
| `textures/block/cargo_container_m_side_lower_waybill.png` | 16×16 | texture:textures/block/cargo_container_m_side_lower_waybill.png |
| `textures/block/cargo_container_m_side_upper.png` | 16×16 | texture:textures/block/cargo_container_m_side_upper.png |
| `textures/block/cargo_container_m_top.png` | 16×16 | texture:textures/block/cargo_container_m_top.png |
| `textures/block/cargo_container_side.png` | 16×16 | texture:textures/block/cargo_container_side.png |
| `textures/block/cargo_container_side_alt.png` | 16×16 | texture:textures/block/cargo_container_side_alt.png |
| `textures/block/cargo_container_side_waybill.png` | 16×16 | texture:textures/block/cargo_container_side_waybill.png |
| `textures/block/cargo_container_side_waybill_alt.png` | 16×16 | texture:textures/block/cargo_container_side_waybill_alt.png |
| `textures/block/cargo_container_top.png` | 16×16 | texture:textures/block/cargo_container_top.png |
| `textures/block/cargo_container_top_alt.png` | 16×16 | texture:textures/block/cargo_container_top_alt.png |

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

## Монеты (запасные PNG)
| Файл | Размер | Откуда |
|---|---|---|
| `textures/gui/coins/bevel.png` | 9×9 | texture:textures/gui/coins/bevel.png |
| `textures/gui/coins/cog.png` | 9×9 | texture:textures/gui/coins/cog.png |
| `textures/gui/coins/crown.png` | 9×9 | texture:textures/gui/coins/crown.png |
| `textures/gui/coins/sprocket.png` | 9×9 | texture:textures/gui/coins/sprocket.png |
| `textures/gui/coins/spur.png` | 9×9 | texture:textures/gui/coins/spur.png |
| `textures/gui/coins/sun.png` | 9×9 | texture:textures/gui/coins/sun.png |

## Прочие текстуры
| Файл | Размер | Откуда |
|---|---|---|
| `textures/slot/order_tablet.png` | 16×16 | texture:textures/slot/order_tablet.png |
