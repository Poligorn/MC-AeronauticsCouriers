#!/usr/bin/env python3
"""Weighted rare-cargo pool selection (mirrors VillageCargo.tryInjectRare eligibility)."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class RareRow:
    min_reputation: int
    weight: int
    name: str


DEFAULT_POOL = (
    RareRow(10, 12, "tier1_seeds"),
    RareRow(10, 8, "tier1_melon"),
    RareRow(25, 6, "tier2_unbreaking"),
    RareRow(50, 4, "tier3_mending"),
    RareRow(50, 10, "tier3_sapling"),
)


def eligible(origin_reputation: int, pool: tuple[RareRow, ...] = DEFAULT_POOL) -> list[RareRow]:
    return [row for row in pool if origin_reputation >= row.min_reputation]


def pick_weighted(rows: list[RareRow], roll: int) -> RareRow:
    total = sum(r.weight for r in rows)
    for row in rows:
        roll -= row.weight
        if roll < 0:
            return row
    return rows[-1]


def main() -> None:
    assert eligible(0) == []
    assert eligible(9) == []
    low = eligible(10)
    assert len(low) == 2
    assert all(r.min_reputation <= 10 for r in low)
    mid = eligible(25)
    assert any(r.name == "tier2_unbreaking" for r in mid)
    assert not any(r.name == "tier3_mending" for r in eligible(49))
    high = eligible(50)
    assert any(r.name == "tier3_sapling" for r in high)
    chosen = pick_weighted(high, 0)
    assert chosen in high
    chosen_last = pick_weighted(high, sum(r.weight for r in high) - 1)
    assert chosen_last == high[-1]
    print("check_village_rare_cargo: OK")


if __name__ == "__main__":
    main()
