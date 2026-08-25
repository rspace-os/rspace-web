---
status: accepted
---

# A template's default link is whole or nothing

## Context

RSDEV-1131 gave Inventory items structured Link fields. A template's Link field
declares a name and a set of **allowed relationship types**; the item created
from it supplies the actual link. RSDEV-1246 asks templates to also supply a
default, so items arrive with the link already set.

An `InventoryLink` row is `(relation_type, target_globalid, target_prefix,
target_db_id, version_pin?)` and the first four columns are all `NOT NULL`
(`changeLog-rsdev-1131.xml`). Nothing half-built is ever persisted today,
because an item's link row is only created at the moment a user picks both
halves at once in the editor.

Two facts shape the options:

- `InventoryEntityField` is the single table behind both template fields and
  item fields, and it already carries `link_id`. On an item field that column
  holds the item's link. On a template field it is always `NULL`, deliberately:
  `ApiFieldToModelFieldFactory` never sets a link when building a template
  field, `ApiInventoryEntityField.applyChangesToDatabaseTemplateField`
  re-applies only the whitelist, and `SampleApiManagerImpl.updateDbSample`
  skips the link write path when the entity is a template.
- Every other field type already puts a template's default value in the same
  column an item's value lives in: a text template field's default sits in
  `data`, and `SampleEntity.copy` clones it onto the new item via
  `shallowCopy()`. `InventoryLinkField.shallowCopy()` already deep-copies the
  link, so a template field holding a link would already be stamped onto items
  with no new copy code.

The tension: putting the default in `link_id` matches every other field type
and needs no schema change, but a link row cannot express "target chosen,
relation type not yet" or the reverse. Supporting each half independently
therefore costs either two new nullable columns on `InventoryEntityField` or a
relaxation of `InventoryLink`'s `NOT NULL` constraints.

## Considered options

1. **Whole or nothing, stored in the existing `link_id`.** The template author
   picks a relation type and a target together or leaves the field with no
   default. No schema change, no new API fields, no new copy code.
2. **Two nullable default columns** (`default_relation_type`,
   `default_target_globalid`) beside `allowed_relation_types`. Each half
   settable alone. Costs a changeset, two API fields, and a second notion of
   "a link" that the rest of the system does not understand.
3. **Relax `InventoryLink`'s `NOT NULL` columns** so one storage slot holds
   partial defaults. Cheapest schema-wise, but every consumer of
   `InventoryLink` (API serialisation, mandatory-field validation, referencing
   items, target summary, archive export, Envers audit) must then tolerate
   half-empty rows, including for real item links, forever.

## Decision

Option 1. A template's Link field either carries a complete `InventoryLink` in
its existing `link_id`, or it carries no default at all. `InventoryLink` keeps
its `NOT NULL` constraints and gains no new states.

Consequences that follow from reusing the item write path rather than inventing
a template one:

- The default is validated exactly like an item's link: relation type in the
  DataCite vocabulary, target syntactically valid, of an allowed target kind,
  existing and readable by the template author, and not the template itself.
  Its relation type must additionally be one the field's own whitelist allows.
- The default may pin a target version, because the editor already offers it
  and `InventoryLink.shallowCopy()` already carries `version_pin` and
  `target_revision_id`. A pinned default stays pinned: an item created a year
  later links to the version captured when the template was edited.
- A default whose target is later deleted, or which the item's creator cannot
  read, is stamped verbatim anyway. Both states already have defined behaviour
  for links made directly on an item (ADR-0002), and the alternative would make
  the same template produce different items for different users.
- A template holding a default link appears in its target's "referenced by"
  list, because `findReferencingItems` already queries every
  `InventoryLinkField` without excluding templates. This is wanted: it shows
  that new items will keep being created pointing at that record.

## Consequences

Good:

- No Liquibase changeset, no new API field, no new model state. The work is
  removing three deliberate suppressions of a path that already exists.
- `InventoryLink` stays a type that is always complete, so no read path
  anywhere gains a null branch.
- Items are stamped by the existing `shallowCopy()` chain, so bulk creation,
  API creation and UI creation behave identically without three code paths.

Bad:

- A template author cannot pre-select a relation type without also choosing a
  target. If that turns out to be wanted, it arrives as option 2 later; the
  migration is additive, but items created in the meantime will have been
  stamped under the older rule.
- Once templates hold links, `InventoryLink` rows are no longer exclusively
  owned by concrete items. Anything that assumes "a link implies an item" must
  be checked; `findReferencingItems` is the known case and is being embraced
  rather than filtered.

Neutral:

- Archive export/import does not carry `InventoryLink` at all today, so a
  default link does not survive export. Pre-existing gap, unchanged by this
  decision.
