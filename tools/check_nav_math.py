#!/usr/bin/env python3
"""Pure-logic checks mirroring NavMath (bearing, distance, yaw delta)."""

from __future__ import annotations

import math


def horizontal_distance_sq(from_x: float, from_z: float, to_x: float, to_z: float) -> float:
    dx = to_x - from_x
    dz = to_z - from_z
    return dx * dx + dz * dz


def horizontal_distance(from_x: float, from_z: float, to_x: float, to_z: float) -> float:
    return math.sqrt(horizontal_distance_sq(from_x, from_z, to_x, to_z))


def bearing_degrees(from_x: float, from_z: float, to_x: float, to_z: float) -> float:
    dx = to_x - from_x
    dz = to_z - from_z
    return math.degrees(math.atan2(-dx, dz))


def yaw_delta(current_yaw: float, target_bearing: float) -> float:
    delta = target_bearing - current_yaw
    while delta > 180:
        delta -= 360
    while delta < -180:
        delta += 360
    return delta


def main() -> None:
    assert abs(horizontal_distance(0, 0, 3, 4) - 5) < 1e-6
    assert abs(bearing_degrees(0, 0, 0, 10) - 0) < 1e-3
    assert abs(yaw_delta(170, -170) - 20) < 1e-3
    print("check_nav_math: OK")


if __name__ == "__main__":
    main()
