# Relocated comments — RSDEV-1354-export-import-inventory-links

Rationale that was written into source comments but belongs with the decision
record rather than beside the code. Kept here so it is not lost, and so the
source says only what a maintainer of that code needs.

## src/main/java/com/researchspace/service/inventory/csvimport/CsvLinkValueParser.java

`CsvLinkValueParser` (class javadoc) — the ELN round-trip asymmetry, now stated
in `0002-link-target-state-non-disclosure.md`: an exported link whose inventory
target has since gone re-imports as a dangling link, while one whose ELN target
(SD/NB/GL) has gone fails its row. Storing the ELN one would need a
permission-free existence probe, which is the disclosure ADR-0002 prevents, and
a rejected row is recoverable where a disclosure is not. Both kinds still
export. The parser itself does not implement this split — `LinkTargetResolver`
`.targetIsKnownMissing` does — so the contract is documented there and in the ADR.

## src/main/java/com/researchspace/service/inventory/impl/LinkTargetSnapshotResolverImpl.java

`resolveSummary` (missing-target branch) — the disclosure trade-off, now stated
in `0002-link-target-state-non-disclosure.md`: this is a deliberate carve-out
from ADR-0002, not an application of it. An existing-but-unreadable inventory
record still answers `readable=false`, so the two payloads differ and a caller
walking ids learns which inventory ids are occupied. Name, type and owner stay
unset either way, so the leak is the id high-water mark rather than any content,
judged a fair price for not offering a dead Open link.
