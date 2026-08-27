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
useful: removed for deleted or inaccessible ELN targets (their routes produce
error pages), kept for deleted Inventory targets (the trash viewer works) and
for all readable targets.
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
