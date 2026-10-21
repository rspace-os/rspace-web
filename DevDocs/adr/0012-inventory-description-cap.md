---
status: accepted
---

# Inventory descriptions widen on their own, not through the shared EditInfo (RSDEV-1236)

## Context

Every record that records who edited it embeds `EditInfo`, and `EditInfo` carries the
description with one `@Column(length = 250)` and one `DESCRIPTION_LENGTH` constant. Seven
tables carry that column with an `_AUD` twin each: Sample, SubSample, Container,
InstrumentEntity and ExtraField on the Inventory side, BaseRecord and RSForm on the ELN side.
The constant is also the cap for inventory tags, for the cryomedium of the cryopreserve
operation (which is stored in a 1000-character template field value), for ELN document
descriptions and for gallery captions. RPD-314 has asked since 2021 for inventory descriptions
of 2000 characters, the cap a subsample note already has; the ELN side has its own ask in
RSDEV-818 and its own legacy UI still capped at 250.

## Decision

Decided with Nico on 2026-10-21.

1. **Inventory records get their own cap, 2000 characters counting markup.** `InventoryRecord`
   declares `DESCRIPTION_LENGTH = 2000` and overrides the embedded `description` column length
   on its `editInfo`; `EditInfo.DESCRIPTION_LENGTH` stays 250 and keeps backing tags, cryomedium,
   ELN descriptions and captions. The Inventory API validators, the PIDINST import truncation,
   the Description field's counter and the record form's save validation use the 2000 cap.
2. **Only the four inventory record tables widen**, Sample, SubSample, Container and
   InstrumentEntity with their `_AUD` twins, through one Liquibase changeset with
   `context="run"`. ExtraField keeps 250 until its own ticket; BaseRecord and RSForm belong to
   RSDEV-818.
3. **The column is `varchar(2050)`, the enforced limit is 2000.** The 50 characters are headroom
   so a value that passes validation can never hit the column, whatever the counting of
   markup or surrogate pairs does at the edge. Validation, not the column, is the limit users
   see.
4. **Only the info card scrolls.** View mode already renders the whole description and wraps;
   the editor already grows to 500px and then scrolls. The info card behind a record's badge
   opens in a tooltip, which closes when the page scrolls, so at 2000 characters its end was
   unreachable; the card is capped at 40% of the viewport height and its content scrolls,
   keeping the Open button in view. Half the viewport does not fit beside a badge in the
   middle of the screen.

## Consequences

- Rows written at 2000 characters cannot be shrunk back; this decision is one-way for data.
- Two constants named `DESCRIPTION_LENGTH` coexist, on `EditInfo` (250) and on
  `InventoryRecord` (2000); a reader who sees the override on `InventoryRecord.getEditInfo`
  should come here.
- Raising extra text fields (ExtraField), template field values (`InventoryEntityField.data`,
  1000) and ELN descriptions each need their own changeset and their own validation, which is
  what the follow-up tickets linked to RPD-314 are for.

## Considered options

- **Bump the shared `EditInfo` constant and widen all fourteen tables.** Smallest code diff, but
  it silently raises tags to 2000, lets cryomedium overflow its 1000-character field value,
  and changes ELN behaviour under a ticket that is scoped to Inventory, while the ELN JSP still
  caps captions at 250. Rejected.
- **A separate description column for inventory.** Full decoupling, but a data migration for
  five tables to gain nothing the override does not already give. Rejected.
- **Column 2000 exactly.** One number everywhere, but no margin between what validation
  accepts and what the column stores. Rejected in favour of 2050.
