#!/usr/bin/env python3
"""Pure-logic checks for StationReputation tier math (GDD §6.4 defaults)."""

from __future__ import annotations

THRESHOLDS = (10, 25, 50)
BONUS_PERCENT = (10, 20, 30)


def tier(reputation: int) -> int:
    result = 0
    for i, th in enumerate(THRESHOLDS):
        if reputation >= th:
            result = i + 1
    return result


def next_threshold(reputation: int) -> int:
    for th in THRESHOLDS:
        if reputation < th:
            return th
    return -1


def reward_multiplier(origin: int, dest: int) -> float:
    t = max(tier(origin), tier(dest))
    if t <= 0:
        return 1.0
    return 1.0 + BONUS_PERCENT[t - 1] / 100.0


def bonus_percent(reputation: int) -> int:
    return int(round((reward_multiplier(reputation, reputation) - 1.0) * 100))


def main() -> None:
    assert tier(0) == 0
    assert tier(9) == 0
    assert tier(10) == 1
    assert tier(24) == 1
    assert tier(25) == 2
    assert tier(49) == 2
    assert tier(50) == 3
    assert next_threshold(0) == 10
    assert next_threshold(10) == 25
    assert next_threshold(49) == 50
    assert next_threshold(50) == -1
    assert bonus_percent(0) == 0
    assert bonus_percent(10) == 10
    assert bonus_percent(25) == 20
    assert bonus_percent(50) == 30
    assert reward_multiplier(10, 0) == 1.1
    assert reward_multiplier(0, 50) == 1.3
    assert reward_multiplier(25, 25) == 1.2
    print("check_station_reputation: OK")


if __name__ == "__main__":
    main()
