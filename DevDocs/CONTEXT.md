# RSpace inventory Link fields

Ubiquitous language for the inventory "Related Items" Link-field feature
(RSDEV-1131): the link card, its target-state pills, and the Open action.

## Language

**Link target**:
The record an inventory Link field points at, identified by its Global ID.
Either an Inventory item (SA/SS/IC/IN/IT) or an ELN record (SD/NB/GL).
_Avoid_: linked item, destination

**Target deleted (pill)**:
The link target currently exists in a soft-deleted state and is readable by
the viewer. Only readable targets can surface this state at all.
_Avoid_: trashed, removed

**No access (pill)**:
The link target is not readable by the current viewer, for whatever reason:
unshared after the link was made, never shared with this viewer, or
hard-deleted by another owner. The system deliberately does not distinguish
these causes, and the state is indistinguishable from "record does not exist"
so that existence is never disclosed (see ADR-0002).
_Avoid_: Item unshared, unshared, hidden

**Pinned link**:
A link that names one version of its target rather than the target as it is
now: "Pinned to v3" on the card, a `v3` suffix on the Global ID in an exported
CSV cell, in a registered address and in an API payload. Saved only when the
target has that version, and that is checked after the viewer's right to read
the target, so an unreadable target never has its versions looked at
(ADR-0002). A link with no pin follows the latest version.
_Avoid_: versioned link, locked link, frozen link

**Dangling link**:
A CSV-imported link whose target the importer could not read when the file was
imported: stored anyway, so one unreachable target does not fail the whole
file, and shown as No access until the target is shared with the viewer. Its
pin is left unchecked, because checking it would reveal whether the item, or
that version of it, exists.
_Avoid_: broken link, orphan link, lenient link

**Version not found (pill)**:
A pinned link whose target the viewer can read but which names a version the
target does not have. Only readable targets surface it; No access wins
otherwise. Reachable only through links saved before the pin was checked on
save, dangling links (stored unchecked because the importer could not fully
read the target), or targets whose version history was purged later: the link
editor, the API, the CSV import and the instrument import all refuse to save one
for a target the writer can read. Open stays for Inventory items and Gallery
files, whose pinned version views explain the missing version, and is removed
for an ELN document, whose version route is only an error page.
_Avoid_: broken pin, stale pin, invalid version, missing revision

**Allowed relationship types**:
The whitelist a template's Link field declares, naming which DataCite relation
types items created from that template may use. An empty whitelist means all of
them. It constrains choice; it does not supply a value.
_Avoid_: permitted relations, relation options

**Default link**:
The complete relation-type-and-target pair a template's Link field may carry,
stamped onto every item created from that template as that item's own ordinary,
editable link. Its relation type must be one the same field allows. Both halves
are set together or not at all: a relation type without a target, or a target
without a relation type, is not a link and cannot be a default (RSDEV-1246).
Distinct from the **allowed relationship types**, which constrain what an item
may choose rather than choosing for it.
_Avoid_: default target, default relation, template link, pre-set link

**Stamped**:
Copied onto an item at the moment that item is created from a template, after
which the copy belongs to the item alone. A later edit to the template's default
link never reaches items already created, and an item updated to a newer
template version is never re-stamped. The same word already describes how a
custom field's default value reaches a new item.
_Avoid_: inherited, synced, propagated, applied

**Open (link card action)**:
Navigates to the link target. Offered only when following it lands somewhere
useful: removed for deleted or inaccessible ELN targets and for an ELN document
whose pinned version is not found (their routes produce error pages), kept for
deleted Inventory targets (the trash viewer works), for an Inventory item or
Gallery file whose pinned version is not found (its view says so) and for all
readable targets.
_Avoid_: view, go to

## Related inventory items (Gallery info panel)

**Referencing item**:
An Inventory item that connects to a Gallery file the viewer is looking at, and
so appears as a back-reference row in the Gallery info panel's "Related
inventory items" section. The connection is one of two **relation kinds**:
_Link_ or _Attachment_. The section shows both, one row per connection.
_Avoid_: related item, back-link

**Link (relation kind)**:
An `InventoryLink` on the item pointing at the Gallery file by Global ID (RSDEV-1131).
Carries a DataCite _relation type_ (IsPartOf, References, ...) shown in the
Relation column. No file bytes are copied.
_Avoid_: reference, pointer

**Attachment (relation kind)**:
An `InventoryFile` whose `mediaFile` is the Gallery file: an inventory item
attached that Gallery file (its bytes were copied into the item). Reported
against the **owning item** whether the file hangs off the record directly or
off one of the item's attachment fields; the field is never the subject. Has no
DataCite relation type, so its Relation column reads "Attachment". Distinct from
a Link.
_Avoid_: file link, attached link, embedded file, attachment field

**Relation (column)**:
The single grid column that names how each referencing item connects: a Link
row shows its DataCite relation type; an Attachment row shows the literal
"Attachment". One axis, one column.
