#!/usr/bin/env python3
"""Generates data/aerocouriers/structure/village/post_station.nbt (GDD §6.0).

A 3×3×3 village "house": a floor, the 1×1×2 post station (concept A «Стойка») facing the street
and two lanterns on fences. Lower half at (1,1,1) holds the BE; upper half at (1,2,1). The antenna
is model-only into the cell above the piece (Minecraft allows one block of model overflow). The jigsaw at
(0, 0, 1) faces west and is named minecraft:building_entrance, which is what village street jigsaws target, so the
piece attaches to streets like vanilla houses. Run from the repository root: python3 tools/gen_post_station_structure.py
"""
import gzip
import io
import struct

OUT = "src/main/resources/data/aerocouriers/structure/village/post_station.nbt"
DATA_VERSION = 3955  # 1.21.1

END, BYTE, INT, STR, LIST, COMP = 0, 1, 3, 8, 9, 10


def tag_type(v):
    if isinstance(v, str):
        return STR
    if isinstance(v, dict):
        return COMP
    if isinstance(v, list):
        return LIST
    if isinstance(v, bool):
        return BYTE
    if isinstance(v, int):
        return INT
    raise TypeError(v)


def write(out, v):
    t = tag_type(v)
    if t == STR:
        b = v.encode()
        out.write(struct.pack(">H", len(b)) + b)
    elif t == BYTE:
        out.write(struct.pack(">b", int(v)))
    elif t == INT:
        out.write(struct.pack(">i", v))
    elif t == COMP:
        for key, value in v.items():
            out.write(struct.pack(">b", tag_type(value)))
            write(out, key)
            write(out, value)
        out.write(b"\x00")
    elif t == LIST:
        out.write(struct.pack(">bi", tag_type(v[0]) if v else END, len(v)))
        for value in v:
            write(out, value)


palette = []
blocks = []


def put(x, y, z, name, properties=None, nbt=None):
    state = {"Name": name}
    if properties:
        state["Properties"] = properties
    if state not in palette:
        palette.append(state)
    block = {"pos": [x, y, z], "state": palette.index(state)}
    if nbt:
        block["nbt"] = nbt
    blocks.append(block)


for x in range(3):
    for z in range(3):
        if (x, z) == (0, 1):
            put(x, 0, z, "minecraft:jigsaw", {"orientation": "west_up"}, {
                "id": "minecraft:jigsaw",
                "name": "minecraft:building_entrance",
                "target": "minecraft:empty",
                "pool": "minecraft:empty",
                "final_state": "minecraft:structure_void",
                "joint": "aligned",
                "selection_priority": 0,
                "placement_priority": 0,
            })
        else:
            put(x, 0, z, "minecraft:spruce_planks")

for y in (1, 2):
    for x in range(3):
        for z in range(3):
            if (x, z) == (1, 1):
                if y == 1:
                    put(x, y, z, "aerocouriers:post_station", {"facing": "west", "half": "lower"},
                        {"id": "aerocouriers:post_station", "village": True})
                else:
                    # Dummy half: no BE nbt. Antenna is model-only into the cell above the piece.
                    put(x, y, z, "aerocouriers:post_station", {"facing": "west", "half": "upper"})
            elif x == 2 and z in (0, 2):
                if y == 1:
                    put(x, y, z, "minecraft:spruce_fence", {"north": "false", "south": "false", "east": "false",
                                                            "west": "false", "waterlogged": "false"})
                else:
                    put(x, y, z, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
            else:
                put(x, y, z, "minecraft:air")

root = {"DataVersion": DATA_VERSION, "size": [3, 3, 3], "palette": palette, "blocks": blocks, "entities": []}
buffer = io.BytesIO()
buffer.write(struct.pack(">b", COMP))
write(buffer, "")
write(buffer, root)
with gzip.open(OUT, "wb") as f:
    f.write(buffer.getvalue())
print(f"{OUT}: {len(blocks)} blocks, {len(palette)} states")
