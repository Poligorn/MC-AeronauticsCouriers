#!/usr/bin/env python3
"""Sanity checks for client board/mine filter bucketing (no JVM)."""

from __future__ import annotations


def classify_history(role: str, state: str) -> str:
    if state == "DELIVERED":
        return "DELIVERED"
    if state == "FAILED":
        return "RETURN" if role == "issuer" else "FAILED"
    if state == "EXPIRED":
        return "REVOKED" if role == "issuer" else "EXPIRED"
    return "EXPIRED"


def main() -> None:
    assert classify_history("courier", "FAILED") == "FAILED"
    assert classify_history("issuer", "FAILED") == "RETURN"
    assert classify_history("issuer", "EXPIRED") == "REVOKED"
    assert classify_history("courier", "EXPIRED") == "EXPIRED"
    print("check_board_list_filters: ok")


if __name__ == "__main__":
    main()
