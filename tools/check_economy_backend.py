#!/usr/bin/env python3
"""Pure-logic checks for economy backend selection (EconomyBackendResolver)."""

from __future__ import annotations

import sys
from enum import Enum


class Choice(Enum):
    INTERNAL = 1
    NUMISMATICS = 2


def resolve(config_backend: str | None, numismatics_loaded: bool) -> Choice:
    key = (config_backend or "auto").strip().lower()
    if key == "internal":
        return Choice.INTERNAL
    if key == "numismatics":
        return Choice.NUMISMATICS if numismatics_loaded else Choice.INTERNAL
    return Choice.NUMISMATICS if numismatics_loaded else Choice.INTERNAL


def forced_numismatics_missing(config_backend: str | None, numismatics_loaded: bool) -> bool:
    if numismatics_loaded:
        return False
    return (config_backend or "auto").strip().lower() == "numismatics"


def main() -> int:
    assert resolve("auto", False) == Choice.INTERNAL
    assert resolve("auto", True) == Choice.NUMISMATICS
    assert resolve("internal", True) == Choice.INTERNAL
    assert resolve("numismatics", True) == Choice.NUMISMATICS
    assert resolve("numismatics", False) == Choice.INTERNAL
    assert forced_numismatics_missing("numismatics", False)
    assert not forced_numismatics_missing("auto", False)
    assert not forced_numismatics_missing("numismatics", True)
    print("check_economy_backend: ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
