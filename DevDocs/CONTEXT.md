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

**Open (link card action)**:
Navigates to the link target. Offered only when following it lands somewhere
useful: removed for deleted or inaccessible ELN targets (their routes produce
error pages), kept for deleted Inventory targets (the trash viewer works) and
for all readable targets.
_Avoid_: view, go to
