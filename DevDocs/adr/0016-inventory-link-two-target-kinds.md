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
   across every user's filestore. A path with a `.` or `..` segment is refused, like a blank or
   root path, rather than resolved: that rule keeps segments as written, as S3 keys are, and
   resolving them here would store Inventory paths differently from document ones.
   *Amended 2026-11-23:* the linked-documents endpoint adds a missing leading slash to the request
   and derives one path from it for the login check, the documents and the items alike, so a
   request without the leading slash still finds the link, and a doubled leading slash names
   another S3 key, which no Inventory link stores. The lookup compares paths exactly and asserts
   read access to the filesystem, as ADR 0015 point 2 decides for documents.
3. Only the owner of a filestore can link through it, and only the owner is told its name by the
   link target summary; anyone else, and an unknown filestore, get the identical redacted answer
   (ADR 0002). Read access to the filesystem was rejected as the rule: filestores are personal
   mounts, and it would hand one user's filestore name to another. A reader of the item who does
   not own the filestore therefore sees the stored path with "No access". The rule governs
   choosing or repointing a target, not holding one: duplicating, splitting or transferring an
   item keeps its links as they are, as it does for a Global ID link the new holder could not
   create themselves, since a copy reveals nothing its original did not.
4. RSpace never contacts the remote store for a link: neither saving nor summarising checks that
   the file is there. That would need the owner's stored credentials and a network call per card.
5. A template's default link cannot be a Filestore item: it would stamp one user's personal
   filestore onto every user's items. PIDINST registration leaves such a link out, because a
   registry entry needs an address that resolves. CSV import does not create one.
6. In the metadata sidecar an Inventory item carries the inverse of its link's relation, because
   the record describes the folder and not the item: an instrument that is the source of the data
   appears as what the data is derived from. An inverse that the record's schema version (DataCite
   4.3) does not list, and a relation that has none, are written as `IsReferencedBy`: a record that
   validates was preferred to the exact relation, which stays in RSpace. Only items the generating
   user can read in full are named; the filestores API shows the others as placeholders with only
   their kind and their owner's name. As for documents (ADR 0015 point 5), "can read" is the
   user's whole read right.

## Consequences

- A link through a filestore that is later removed from the Gallery keeps its row and reads
  "Target deleted" to the owner; a file moved or renamed on the remote store leaves the link on the
  old path with no warning.
- CSV export writes such a link as `<relation> <filestore id>:<path>`, which the import refuses, so
  a round trip fails on that row instead of dropping the link silently.
