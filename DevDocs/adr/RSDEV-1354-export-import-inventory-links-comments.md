# Relocated comments — RSDEV-1354-export-import-inventory-links

Rationale that was written into source comments but belongs with the decision
record rather than beside the code. Kept here so it is not lost, and so the
source says only what a maintainer of that code needs.

## src/main/java/com/researchspace/service/inventory/csvimport/CsvLinkValueParser.java

`CsvLinkValueParser` (class javadoc) — an earlier design split CSV import by
target state: an inventory target that provably did not exist imported as a
dangling link, while an ELN one failed its row, because deciding the same for
an ELN record would need the permission-free existence probe ADR-0002 prevents.
That split is withdrawn. Import now stores the link whatever state the target
is in, for every prefix, and the card reports an unresolvable target as
"No access" at read time. See `0002-link-target-state-non-disclosure.md`.

## src/main/java/com/researchspace/service/inventory/impl/LinkTargetSnapshotResolverImpl.java

`resolveSummary` (missing-target branch) — this branch once carved out
inventory targets from ADR-0002, answering `readable=true, deleted=true` for a
target with no live record and no audit snapshot so the card could say "Target
deleted" instead of offering a dead Open. That payload differed from the
existing-but-unreadable one, so a caller walking ids learned which inventory
ids were occupied. The carve-out is withdrawn: every prefix now returns the
same redacted summary, and the id high-water-mark leak is gone.
