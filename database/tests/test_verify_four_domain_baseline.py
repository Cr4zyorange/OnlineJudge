#!/usr/bin/env python3
"""Regression checks for the disposable four-domain MySQL baseline script."""

from __future__ import annotations

import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPOSITORY_ROOT / "database/tests/verify-four-domain-baseline.sh"


class FourDomainBaselineScriptTest(unittest.TestCase):
    def test_mysql_readiness_and_root_administration_use_tcp(self) -> None:
        script = SCRIPT.read_text(encoding="utf-8")

        self.assertIn('mysql --protocol=TCP --host=127.0.0.1 --port=3306 -uroot', script)
        self.assertIn('if mysql_root -e \'SELECT 1\'', script)
        self.assertIn('sql() { mysql_root "$@"; }', script)


if __name__ == "__main__":
    unittest.main(verbosity=2)
