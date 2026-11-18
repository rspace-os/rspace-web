---
status: accepted
---

# An Inventory link row carries one of two target kinds

A Link field could only point at an RSpace record by Global ID. A Filestore item has no Global ID
that resolves, and Inventory items now refer to one through the same Link field.

## Decision

1. A Filestore item target lives on the same `InventoryLink` row as a Global ID target, in columns
   of its own, and a database CHECK constraint keeps every row to exactly one complete kind. The
   alternative was a sibling table for the second kind. It would keep the Global ID columns NOT
   NULL, but both field types would need a second association and every reader of a link (API,
   mandatory check, CSV export, registration, referencing items) would have to look in two places.
   One row keeps "a field holds one link" true and costs a branch only where a reader cares about
   the kind. ADR 0006 kept those columns NOT NULL to rule out half-built links; each kind here is
   still whole, guaranteed by the constraint instead of by column nullability.
2. The row also stores the filesystem and the normalised absolute path, by the rule of ADR 0015, so
   "which Inventory items link this file" is the same indexed equality lookup as for documents,
   across every user's filestore.
3. Only the owner of a filestore can link through it, and only the owner is told its name by the
   link target summary; anyone else, and an unknown filestore, get the identical redacted answer
   (ADR 0002). Read access to the filesystem was rejected as the rule: filestores are personal
   mounts, and it would hand one user's filestore name to another. A reader of the item who does
   not own the filestore therefore sees the stored path with "No access". The rule governs
   choosing or repointing a target, not holding one: duplicating, splitting or transferring an
   item keeps its links as they are, as it does for a Global ID link the new holder cannot read,
   since a copy reveals nothing its original did not.
4. RSpace never contacts the remote store for a link: neither saving nor summarising checks that
   the file is there. That would need the owner's stored credentials and a network call per card.
5. A template's default link cannot be a Filestore item: it would stamp one user's personal
   filestore onto every user's items. PIDINST registration leaves such a link out, because a
   registry entry needs an address that resolves. CSV import does not create one.

## Consequences

- A link through a filestore that is later removed from the Gallery keeps its row and reads
  "Target deleted" to the owner; a file moved or renamed on the remote store leaves the link on the
  old path with no warning.
- CSV export writes such a link as `<relation> <filestore id>:<path>`, which the import refuses, so
  a round trip fails on that row instead of dropping the link silently.
