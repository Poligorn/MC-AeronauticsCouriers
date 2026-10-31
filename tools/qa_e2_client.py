#!/usr/bin/env python3
"""Xvfb client QA for E2 cargo textures and the rare-cargo tablet filter.

Does not change game code. Uses chat + datapack functions + jadeqa look files.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import time
from pathlib import Path

from PIL import Image

ROOT = Path("/workspace")
RUN = ROOT / "run"
LOG = RUN / "logs" / "latest.log"
ART_CARGO = Path("/opt/cursor/artifacts/cargo-e2")
ART_RARE = Path("/opt/cursor/artifacts/rare-tablet")
E2_REF = Path("/tmp/e2_textures/E2_final.png")
if not E2_REF.is_file():
    E2_REF = ROOT / "couriers/cargo_concept_2026-11-01/final_E2/E2_final.png"
DISPLAY = os.environ.get("DISPLAY", ":99")
SAVE = RUN / "saves" / "jadeqa"
PACK = SAVE / "datapacks" / "e2qa"


def run(cmd: list[str], **kw) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, check=False, **kw)


def shot(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    env["DISPLAY"] = DISPLAY
    run(["scrot", "-o", str(path)], env=env)
    print("shot", path, path.stat().st_size if path.exists() else 0)


def xdo(*args: str) -> None:
    env = os.environ.copy()
    env["DISPLAY"] = DISPLAY
    run(["xdotool", *args], env=env)


def wait_log(needle: str, timeout: float = 180) -> bool:
    deadline = time.time() + timeout
    last = 0
    while time.time() < deadline:
        if LOG.exists():
            text = LOG.read_text(errors="replace")
            last = len(text)
            if needle in text:
                return True
        time.sleep(1)
    print("timeout waiting for", needle, "log_bytes", last)
    return False


def chat(command: str, pause: float = 0.9) -> None:
    xdo("key", "Escape")
    time.sleep(0.25)
    # `/` opens the command bar with the slash already typed.
    xdo("key", "slash")
    time.sleep(0.35)
    text = command[1:] if command.startswith("/") else command
    tmp = Path("/tmp/e2_chat.txt")
    tmp.write_text(text, encoding="utf-8")
    xdo("type", "--clearmodifiers", "--file", str(tmp))
    time.sleep(0.25)
    xdo("key", "Return")
    time.sleep(pause)


def write_options() -> None:
    RUN.mkdir(parents=True, exist_ok=True)
    (RUN / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    options = """
lang:ru_ru
guiScale:3
fullscreen:false
overrideWidth:1920
overrideHeight:1080
tutorialStep:none
pauseOnLostFocus:false
autoJump:false
narrator:0
soundDevice:
maxFps:30
gamma:1.0
fov:0.5
renderDistance:8
simulationDistance:8
entityShadows:false
fancyGraphics:false
ao:false
particles:2
mipmapLevels:0
""".lstrip()
    (RUN / "options.txt").write_text(options, encoding="utf-8")


def write_server_properties() -> None:
    (RUN / "server.properties").write_text(
        "\n".join(
            [
                "level-type=minecraft:flat",
                "level-name=world",
                "online-mode=false",
                "spawn-protection=0",
                "allow-flight=true",
                "gamemode=creative",
                "difficulty=peaceful",
                "generate-structures=false",
                "spawn-npcs=false",
                "spawn-animals=false",
                "spawn-monsters=false",
                "max-players=8",
                "motd=e2qa",
                "pvp=false",
                "",
            ]
        ),
        encoding="utf-8",
    )


def write_datapack() -> None:
    func = PACK / "data" / "e2qa" / "function"
    func.mkdir(parents=True, exist_ok=True)
    (PACK / "pack.mcmeta").write_text(
        '{"pack":{"pack_format":48,"description":"e2 cargo qa"}}\n',
        encoding="utf-8",
    )
    lines = [
        # 1.21 default superflat: grass at y=-61. Pad sits on it.
        "fill 0 -61 30 44 -61 52 minecraft:smooth_stone",
        "fill 0 -60 30 44 -52 52 minecraft:air",
        "time set noon",
        "weather clear",
        "gamemode creative @s",
        "effect give @s minecraft:night_vision 9999 0 true",
    ]

    def s(x: int, z: int, waybill: bool) -> None:
        wb = "true" if waybill else "false"
        lines.append(
            f"setblock {x} -60 {z} aerocouriers:cargo_container_s["
            f"facing=east,fill=4,waybill={wb},locked=true]"
        )

    def m(x: int, z: int, waybill: bool) -> None:
        wb = "true" if waybill else "false"
        for half, y in (("lower", -60), ("upper", -59)):
            lines.append(
                f"setblock {x} {y} {z} aerocouriers:cargo_container_m["
                f"facing=east,half={half},fill=4,waybill={wb},locked=true]"
            )

    def box(size: str, x0: int, z0: int, height: int, waybill: bool) -> None:
        wb = "true" if waybill else "false"
        for y in range(height):
            for z in range(2):
                for x in range(2):
                    if height == 2:
                        seg = x | (y << 1) | (z << 2)
                    else:
                        seg = x | (y << 1) | (z << 2) if y < 2 else 8 | x | (z << 1)
                    lines.append(
                        f"setblock {x0 + x} {-60 + y} {z0 + z} aerocouriers:cargo_container_{size}["
                        f"facing=east,segment={seg},fill=4,waybill={wb},locked=true]"
                    )

    # z=34 no waybill; z=44 with waybill. facing=east → paper on south.
    # No give: cargo blocks have no BlockItem (ModBlocks comment / GDD §1).
    s(2, 34, False)
    m(6, 34, False)
    box("l", 10, 34, 2, False)
    box("xl", 16, 34, 3, False)
    s(2, 44, True)
    m(6, 44, True)
    box("l", 10, 44, 2, True)
    box("xl", 16, 44, 3, True)
    lines += [
        "give @s aerocouriers:order_tablet 1",
        "give @s aerocouriers:transport_waybill 1",
    ]
    (func / "lineup.mcfunction").write_text("\n".join(lines) + "\n", encoding="utf-8")


def magenta_count(path: Path) -> int:
    if not path.is_file():
        return -1
    im = Image.open(path).convert("RGB")
    px = im.getdata()
    return sum(1 for r, g, b in px if r > 240 and g < 20 and b > 240)


def sample_means(path: Path, box: tuple[int, int, int, int]) -> tuple[float, float, float]:
    im = Image.open(path).convert("RGB")
    crop = im.crop(box)
    pixels = list(crop.getdata())
    n = max(1, len(pixels))
    r = sum(p[0] for p in pixels) / n
    g = sum(p[1] for p in pixels) / n
    b = sum(p[2] for p in pixels) / n
    return r, g, b


def analyze_cargo(paths: list[Path]) -> None:
    report = ART_CARGO / "compare.txt"
    lines = ["E2 in-game vs raw textures / E2_final.png", ""]
    for p in paths:
        if not p.is_file():
            lines.append(f"MISSING {p.name}")
            continue
        im = Image.open(p)
        mag = magenta_count(p)
        lines.append(f"{p.name}: {im.size} magenta={mag}")
    if E2_REF.is_file():
        ref = Image.open(E2_REF)
        lines.append(f"E2_final.png: {ref.size}")
    lines.append("")
    lines.append("Hue check (expect S blue, M green, L ochre, XL burgundy).")
    report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(report.read_text())


def prepare_look(x: float, y: float, z: float, yaw: float, pitch: float) -> None:
    chat(f"tp @s {x:.2f} {y:.2f} {z:.2f} {yaw:.1f} {pitch:.1f}", pause=1.3)


def cargo_shots() -> list[Path]:
    out: list[Path] = []
    chat("function e2qa:lineup", pause=2.0)
    chat("gamerule doDaylightCycle false")
    chat("gamerule doWeatherCycle false")

    # hide HUD
    xdo("key", "F1")
    time.sleep(0.4)

    # front, both rows (look north at south faces; waybill on south when facing=east)
    prepare_look(12.0, -58.0, 50.5, 180.0, 18.0)
    p = ART_CARGO / "01_lineup_front.png"
    shot(p)
    out.append(p)

    prepare_look(12.0, -56.5, 54.0, 180.0, 22.0)
    p = ART_CARGO / "02_lineup_front_high.png"
    shot(p)
    out.append(p)

    # isometric from south-east
    prepare_look(24.0, -56.0, 52.0, 140.0, 24.0)
    p = ART_CARGO / "03_lineup_iso.png"
    shot(p)
    out.append(p)

    # close-ups of waybill row (z=44)
    for name, x, y, z in (
        ("04_s_waybill", 2.5, -58.8, 47.4),
        ("05_m_waybill", 6.5, -58.4, 47.6),
        ("06_l_waybill", 11.5, -58.0, 48.2),
        ("07_xl_waybill", 17.5, -57.4, 49.0),
    ):
        prepare_look(x, y, z, 180.0, 12.0)
        p = ART_CARGO / f"{name}.png"
        shot(p)
        out.append(p)

    # close-ups of plain row (z=34)
    for name, x, y, z in (
        ("08_s_plain", 2.5, -58.8, 37.4),
        ("09_m_plain", 6.5, -58.4, 37.6),
        ("10_l_plain", 11.5, -58.0, 38.2),
        ("11_xl_plain", 17.5, -57.4, 39.0),
    ):
        prepare_look(x, y, z, 180.0, 12.0)
        p = ART_CARGO / f"{name}.png"
        shot(p)
        out.append(p)

    # top-down-ish
    prepare_look(12.0, -52.0, 42.0, 180.0, 70.0)
    p = ART_CARGO / "12_lineup_top.png"
    shot(p)
    out.append(p)

    # jadeqa pad extras (S/L/XL from CargoSpawner)
    Path("/workspace/run/jadeqa-scene.txt").write_text("xl\n", encoding="utf-8")
    time.sleep(1.8)
    p = ART_CARGO / "15_jadeqa_xl.png"
    shot(p)
    out.append(p)
    Path("/workspace/run/jadeqa-scene.txt").write_text("large\n", encoding="utf-8")
    time.sleep(1.8)
    p = ART_CARGO / "16_jadeqa_l.png"
    shot(p)
    out.append(p)
    Path("/workspace/run/jadeqa-scene.txt").write_text("published\n", encoding="utf-8")
    time.sleep(1.8)
    p = ART_CARGO / "17_jadeqa_s_waybill.png"
    shot(p)
    out.append(p)
    Path("/workspace/run/jadeqa-scene.txt").write_text("empty\n", encoding="utf-8")
    time.sleep(1.8)
    p = ART_CARGO / "18_jadeqa_s_empty.png"
    shot(p)
    out.append(p)

    # hotbar (no cargo BlockItem — GDD) + tablet
    xdo("key", "F1")
    time.sleep(0.3)
    prepare_look(12.0, -58.0, 50.5, 180.0, 18.0)
    p = ART_CARGO / "13_hotbar.png"
    shot(p)
    out.append(p)
    xdo("key", "e")
    time.sleep(1.2)
    p = ART_CARGO / "14_inventory.png"
    shot(p)
    out.append(p)
    xdo("key", "Escape")
    time.sleep(0.5)
    return out


def rare_shots() -> list[Path]:
    out: list[Path] = []
    chat("couriers license give @s rank5", pause=1.0)
    chat("couriers dev seed", pause=3.5)
    time.sleep(2.0)
    p = ART_RARE / "01_list_mixed.png"
    shot(p)
    out.append(p)

    # Tab to filter widgets: tabs first, then S M L XL, rank, TYPE, sort.
    # Cycle type ALL → VILLAGE → PLAYER → RARE (3 Enter on type chip).
    for _ in range(12):
        xdo("key", "Tab")
        time.sleep(0.12)
    for i in range(3):
        xdo("key", "Return")
        time.sleep(0.7)
        p = ART_RARE / f"02_filter_cycle_{i + 1}.png"
        shot(p)
        out.append(p)

    p = ART_RARE / "03_filter_rare.png"
    shot(p)
    out.append(p)

    # try click the first list row (after rare filter only rare remains)
    # GUI 3, 1920x1080 → 640x360. Tablet cavity roughly centered.
    # Row cards start ~ y=140-180 GUI → 420-540 window.
    for x, y in ((960, 480), (960, 520), (960, 430), (900, 500)):
        xdo("mousemove", "--sync", str(x), str(y))
        time.sleep(0.15)
        xdo("click", "1")
        time.sleep(0.6)
    p = ART_RARE / "04_details_attempt.png"
    shot(p)
    out.append(p)
    return out


def main() -> None:
    ART_CARGO.mkdir(parents=True, exist_ok=True)
    ART_RARE.mkdir(parents=True, exist_ok=True)
    write_options()
    write_datapack()
    if not wait_log("joined the game", timeout=15):
        print("client not in world yet; shots skipped (call after join)")
        return
    time.sleep(2)
    cargo = cargo_shots()
    rare = rare_shots()
    analyze_cargo(cargo)
    print("cargo", len(cargo), "rare", len(rare))


if __name__ == "__main__":
    main()
