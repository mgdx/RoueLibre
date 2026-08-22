"""Tests of the data generation tools (SPEC.md §16).

Run them from the root of the repository:

    python3 -m unittest discover --start-directory tools/tests \\
                                 --top-level-directory tools

The scripts of ``tools/`` import one another by module name, so ``tools`` is
the top-level directory the tests are discovered from: that is what puts it on
the import path, and it is why this package holds no path juggling of its own.

Nothing here goes out on the network. A test that needed a live GBFS feed would
fail on a train, and would fail for the producer's reasons rather than for ours.
"""
