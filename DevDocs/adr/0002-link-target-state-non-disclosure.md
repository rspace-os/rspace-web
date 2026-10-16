# Link target summaries conflate "unreadable" with "nonexistent"

Status: accepted

The link-target summary endpoint (`GET /api/inventory/v1/linkTargets/{globalId}/summary`,
RSDEV-1182) backs the link card's state pills. It is callable with any
well-formed Global ID, so its responses must not let a caller probe which
records exist. We decided that the summary's `readable: false` deliberately
covers every degraded case identically: target unshared from the viewer,
never shared with the viewer, nonexistent, or hard-deleted by another owner
all produce field-for-field identical payloads (globalId only, `name`/`type`
null, `deleted` false). A unit test pins this invariant.

The user-facing consequence is that the card's pill says **"No access"**
rather than "Item unshared": unshared-after-linking is indistinguishable from
never-shared without consulting share history, and the pill must never imply
knowledge the viewer is not entitled to.

## Considered options

* **Per-viewer share-history detection** (to label genuinely unshared targets
  differently): rejected. It needs a new query chain per link per viewer,
  and only buys a more specific word on the pill.
* **"Item unshared" wording on the generic no-access state**: rejected as
  inaccurate for never-shared viewers, who legitimately see links on records
  shared with them whose targets never were.

## Consequences

* `deleted: true` can only ever arrive alongside `readable: true` (redaction
  zeroes it), so the "Target deleted" and "No access" pills can never co-occur
  and need no precedence logic.
* The "No access" pill also appears to viewers who never had access and for
  hard-deleted targets they do not own. This is accepted: in every such case
  Open could only produce an error page.
* The pill applies to every target kind, inventory included. An earlier
  revision showed no pill for an unreadable inventory target, on the grounds
  that every logged-in user keeps a limited-read view of it; that is not so.
  `canUserLimitedReadInventoryRecord` grants that view only through container
  containment, a list of materials, or a template, and an owner can set an
  item to "Explicit access list" or "Only the Owner", so a genuinely
  unreadable inventory target is reachable and should say so. For inventory,
  "readable" in the summary means READ **or** limited read, since either opens
  the item; link creation and `findReferencingItems` still require full READ.
* ELN read permission in the summary comes from the live row (a plain DAO
  read), never from the audit snapshot. Sharing and publishing a document write
  no audit revision, so a snapshot's ACL can grant what the live row has
  revoked.
* RSDEV-1354 (CSV link import) keeps this rule rather than carving an
  exception into it. An imported link whose target cannot be resolved is
  stored anyway and reported as `readable: false`, exactly like an unreadable
  one, so neither the import result nor the card says whether the record
  exists. An earlier revision of this branch reported a missing inventory
  target as `deleted: true, readable: true` so the card could say "Target
  deleted"; that was withdrawn because it made the two states
  distinguishable, and because absence of an audit snapshot is not proof of
  deletion.
* Resolution must be **type-exact**, and this needs re-checking whenever a new
  kind of Inventory record is added. Numeric ids are not unique across kinds,
  so a target's prefix is part of its identity, not decoration. Samples and
  sample templates are one table split by `DTYPE` and are resolved through a
  single lookup: `InventoryRecordRetriever.getInvRecordByGlobalId` maps both
  `SA` and `IT` to `getSampleIfExists(dbId)`, which tries `sampleDao` then
  `sampleTemplateDao`, so the prefix is discarded and a readable `SA90` used to
  vouch for an `IT90` that does not exist. That discloses a readable record at
  that id, which is precisely what this ADR forbids, so
  `targetIsLiveAndReadable` now requires the resolved record's own
  `getOid().getPrefix()` to equal the requested one, and the ELN path has always
  done the same (`GL150` would otherwise load folder `FL150`).

  Verified on a live instance: `SS`/`IC` hold their own tables, and `IN`/`NT`
  share `InstrumentEntity` under a `DTYPE` but are resolved through separate
  DAOs bound to concrete classes, so Hibernate filters by discriminator and
  crossed ids (`IN152` for a template, `NT153` for an instrument) already
  resolve to nothing. `SA`/`IT` is today the only pair sharing a lookup. That
  is a property of the current retriever, not a guarantee: **anyone adding an
  Inventory record kind, a subtype sharing an existing table, or a new import
  path must re-check whether its prefix can collide with an existing one**, and
  must not assume the shared-lookup problem is confined to `SA`/`IT`. The check
  in `targetIsLiveAndReadable` is uniform across prefixes and so covers a new
  kind automatically, but only for callers that go through it;
  `getInvRecordByGlobalId` itself still resolves `SA`/`IT` by number for every
  other caller in Inventory, which has not been audited.

* The gallery "Related inventory items" attachments endpoint (RSDEV-173,
  `GET /workspace/getAttachingInventoryItems/{globalId}`) applies the same
  non-disclosure gate: an unreadable, nonexistent, or malformed target Global ID
  all return one identical not-found, so it never reveals a gallery file exists
  or which Inventory items attached it. This mirrors the links referencing-items
  endpoint.
* A pinned version is checked only after the READ check, never before it
  (RSDEV-1530). The link write path refuses a pin to a version the target does
  not have (422, `errors.inventory.field.linkTargetVersionNotFound`), and the
  summary answers `pinnedVersionFound` when asked with a versioned id, but both
  only once the actor may read the target (for the summary in its own wider
  sense, which also counts limited read of an Inventory item and READ of a
  published ELN record, as for its other pills): an unreadable target fails as
  "not found" exactly as before, and its summary stays the redacted payload
  above, so a caller still cannot tell a missing item from an unshared one, nor
  probe which versions an item they cannot read has. The CSV import keeps the
  dangling-link rule of the bullet above and adds the same check at parse time,
  where the row can be named: a pinned cell whose target the importer can read
  must name a version it has, while a cell whose target they cannot read is
  still stored unchecked. The PIDINST import reports such an entry with its one
  `NOT_AVAILABLE` reason (ADR 0009 decision 9). Links saved before this check,
  dangling links seen by someone who can read their target, and links whose
  audit rows are purged later keep their pin and are flagged "Version not found"
  on the card, to readers only; their records still save, because an unchanged
  link is never re-validated. Editing such a link re-validates it; on a template
  link field even a relation-only change does, while a custom link field applies
  one without re-validating (a pre-existing difference, kept on purpose).
  Exporting such a link to CSV writes its pin, so re-importing that file fails
  the row, and with it the whole file, until the cell is corrected.
