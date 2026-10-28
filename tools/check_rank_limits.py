#!/usr/bin/env python3
"""Pure-logic checks for rank accept limits (GDD §4.2), mirroring AcceptEligibility."""

from __future__ import annotations

LIMITS = {
    1: {"collateral": 200, "size_ord": 0, "distance": 1500, "active": 1},
    2: {"collateral": 600, "size_ord": 1, "distance": 4000, "active": 2},
}


def satisfies(have: int, required: int) -> bool:
    return have >= required


def board_locked(rank: int, required: int, collateral: int, size_ord: int, distance: float) -> bool:
    lim = LIMITS[rank]
    if not satisfies(rank, required):
        return True
    return (
        collateral > lim["collateral"]
        or size_ord > lim["size_ord"]
        or distance > lim["distance"]
    )


def main() -> None:
    assert board_locked(1, 2, 100, 0, 500)
    assert not board_locked(2, 1, 500, 0, 3000)
    assert board_locked(1, 1, 201, 0, 100)
    print("check_rank_limits: OK")


if __name__ == "__main__":
    main()
