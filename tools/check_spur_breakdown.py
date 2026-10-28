#!/usr/bin/env python3
"""Pure-logic checks for greedy spur breakdown (Numismatics denominations)."""

from __future__ import annotations

import sys

DENOMS = [4096, 512, 64, 16, 8, 1]


def greedy(spurs: int) -> list[tuple[int, int]]:
    remaining = max(0, spurs)
    lines: list[tuple[int, int]] = []
    for value in DENOMS:
        if remaining < value:
            continue
        count = remaining // value
        if count <= 0:
            continue
        remaining -= count * value
        lines.append((value, count))
    return lines


def total(lines: list[tuple[int, int]]) -> int:
    return sum(v * c for v, c in lines)


def main() -> int:
    assert greedy(0) == []
    assert greedy(989) == [(512, 1), (64, 7), (16, 1), (8, 1), (1, 5)]
    assert total(greedy(989)) == 989
    assert greedy(4096) == [(4096, 1)]
    assert greedy(4097) == [(4096, 1), (1, 1)]
    for n in (1, 7, 8, 15, 16, 63, 64, 511, 512, 5000, 999_999):
        lines = greedy(n)
        assert total(lines) == n
        for i in range(len(lines) - 1):
            assert lines[i][0] > lines[i + 1][0]
    print("check_spur_breakdown: ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
