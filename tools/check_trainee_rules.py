#!/usr/bin/env python3
"""Pure-logic checks for TraineeRules (GDD §6.5)."""

from __future__ import annotations


def on_trainee(village_delivered: int, trainee_free: int) -> bool:
    return trainee_free > 0 and village_delivered < trainee_free


def distance_allowed(
    distance: float,
    village_delivered: int,
    trainee_free: int,
    trainee_max: int,
) -> bool:
    if not on_trainee(village_delivered, trainee_free):
        return True
    if trainee_max <= 0:
        return True
    return distance <= trainee_max


def main() -> None:
    assert on_trainee(0, 3)
    assert on_trainee(2, 3)
    assert not on_trainee(3, 3)
    assert distance_allowed(500, 0, 3, 800)
    assert distance_allowed(801, 0, 3, 800) is False
    assert distance_allowed(2000, 3, 3, 800)
    assert distance_allowed(2000, 0, 3, 0)
    print("check_trainee_rules: OK")


if __name__ == "__main__":
    main()
