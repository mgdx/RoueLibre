"""The graph generator and the routing engine are the same BRouter (SPEC.md §5).

The engine embedded in the application comes from the `third_party/brouter`
submodule; the map creator that produces the `.rd5` files comes from the
release archive `build_routing.py` downloads. Nothing ties the two: their
version number is written once in each place, and BRouter only refuses a
graph when the major version of its vocabulary changes, which is rare enough
for a mismatch to go unnoticed for a long time. This test is the tie.
"""

from __future__ import annotations

import re
import unittest

from build_routing import BROUTER_VERSION, REPO_ROOT

# Where BRouter declares its own version, in the submodule's build conventions.
SUBMODULE_VERSION_FILE = (
    REPO_ROOT / "third_party" / "brouter" / "buildSrc" / "src" / "main"
    / "groovy" / "brouter.version-conventions.gradle"
)


class BrouterVersionTest(unittest.TestCase):
    def test_generator_matches_the_embedded_engine(self) -> None:
        if not SUBMODULE_VERSION_FILE.exists():
            self.skipTest("the brouter submodule is not checked out")
        declaration = re.search(
            r"^version\s*=\s*'([^']+)'", SUBMODULE_VERSION_FILE.read_text(), re.M
        )
        self.assertIsNotNone(declaration, "no version declared in the submodule")
        self.assertEqual(
            declaration.group(1), BROUTER_VERSION,
            "build_routing.py and the third_party/brouter submodule must move "
            "to a new BRouter version together, lookups.dat in the app's "
            "assets with them",
        )


if __name__ == "__main__":
    unittest.main()
