---
status: proposed
---

# Extra fields on concrete Instruments only

## Context

An *extra field* is an ad-hoc field (text, number or link) a user attaches to an
individual inventory record after creation, outside any template definition. A
*custom field* is a typed field that is part of a template's definition and is
stamped onto every record created from that template.

Today `extraFields` is declared on `InstrumentEntity`, the shared base of the
single-table `Instrument`/`InstrumentTemplate` hierarchy, so instrument
templates support extra fields exactly like concrete instruments do:

- The API applies extra fields to templates on create
  (`InventoryApiManagerImpl.setBasicFieldsFromNewIncomingApiInventoryRecord`,
  which calls `extraFieldHelper.addExtraFieldsForNewInventoryRecord` for every
  record type) and on update
  (`InstrumentEntityApiManagerImpl.updateApiInstrumentTemplate` calls
  `extraFieldHelper.createDeleteRequestedExtraFieldsInDatabaseInstrument`).
- The copy machinery (`InventoryRecord.shallowCopyBasicFields`, both overloads)
  propagates extra fields in both directions: `copyToTemplate` carries an
  instrument's extra fields onto the new template, and `copyFromTemplate`
  stamps a template's extra fields onto every instrument created from it.
- The sample side does the same on purpose: `SampleEntity` declares
  `extraFields` at the shared level, so `SampleTemplate` has them too.

Product direction: for instruments, a template's shape should be expressed
exclusively through its custom fields. Extra fields are per-record annotations
and only make sense on a concrete `Instrument`. `Instrument.copyToTemplate`
currently has no caller in rspace-web (only core-model tests exercise it), so
the conversion rule below shapes a path that is dormant today but defines its
semantics for when the feature is exposed.

## Decision

Move `extraFields` from `InstrumentEntity` to the concrete `Instrument`, with
the following locked-in choices:

1. **No data migration.** Single-table inheritance keeps the
   `ExtraField.instrumentEntity_id` column and all tables unchanged, so there is
   no Liquibase work. Any `ExtraField` rows already attached to template rows
   (and their `_AUD` history) simply become unreachable: never loaded, never
   returned, never deleted by `orphanRemoval`. We accept these orphans rather
   than run a destructive migration; a cleanup script can be written later if
   they ever matter.

2. **`copyToTemplate` converts extra fields into custom fields.** When an
   instrument is turned into a template, each active extra field becomes a
   custom field (`InventoryEntityField`) in the template definition instead of
   being copied as an extra field: the extra field's name becomes the field
   name and its data becomes the field's default content. Proposed type
   mapping: `text` to a text/string field, `number` to a number field, `link`
   to a `uri` field (a plain URL, not a relation-typed `link` field).

3. **`copyFromTemplate` no longer copies extra fields.** An instrument created
   from a template starts with none. The old behaviour (template extra fields
   stamped onto new instruments) is retired; template-driven provisioning is
   expressed through custom fields, which decision 2 now produces.

## Required changes

rspace-core-model:

- Move the `extraFields` field, the `@OneToMany(mappedBy = "instrumentEntity")`
  mapping and the `@IndexedEmbedded(prefix = "fields.")` annotation from
  `InstrumentEntity` to `Instrument`.
- `InventoryRecord.getExtraFields()` is `protected abstract`, so
  `InstrumentTemplate` still needs an implementation: return an immutable empty
  list, and make `addExtraField` on a template fail fast so no code path can
  silently re-attach one.
- `shallowCopyBasicFields` (both overloads) must stop copying extra fields for
  the instrument hierarchy; sample, subsample and container behaviour stays
  untouched. The `InstrumentTemplate(Instrument, User)` constructor implements
  the decision-2 conversion; the `Instrument(InstrumentTemplate, User)`
  constructor has nothing to copy.
- Conversion must respect field-name rules: reserved names
  (`InventoryRecord.RESERVED_FIELD_NAMES`, `verifyFieldNameAllowed`) and
  template field-name uniqueness
  (`InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNames`).
  Recommended: deterministic auto-rename on collision (for example a numeric
  suffix) so `copyToTemplate` never starts failing where it used to succeed.
  Finalise the exact rule during implementation.
- Narrow `InventoryRecordConnectedEntity.setInventoryRecord` from
  `instanceof InstrumentEntity` to `instanceof Instrument` so an `ExtraField`
  can never re-attach to a template. The back-reference property may stay typed
  `InstrumentEntity` (the `mappedBy` from `Instrument` still resolves against a
  supertype-typed property).
- Tests: pin the conversion and the no-copy semantics in
  `InstrumentTemplateTest`/`InstrumentTest`; `HibernateSandboxTest` revalidates
  the moved mapping.

rspace-web (after bumping the pinned model version):

- Template create: reject non-empty `extraFields` on `ApiInstrumentTemplatePost`
  with a validation error (silent ignore hides client bugs). Keep accepting an
  empty array for wire compatibility; the seeded PIDINST JSON sends
  `"extraFields": []`.
- Template update: remove the `extraFieldHelper` call from
  `updateApiInstrumentTemplate` and reject extra-field mutations with a clear
  message.
- Template GET responses keep `extraFields: []` (the property lives on
  `ApiInventoryRecordInfo`), so the response shape is unchanged.
- Check CSV import/export and archive export for instrument-template
  extra-field references.
- Document the breaking change in the public API docs/changelog: clients that
  POST/PUT extra fields on instrument templates get an error instead of a
  silent write.
- Tests: MVCIT coverage for the create/update rejection; conversion coverage
  once a copy-to-template endpoint exists.

## Consequences

- Deliberate parity divergence from samples: `SampleTemplate` keeps extra
  fields, `InstrumentTemplate` loses them. The glossary records the
  distinction.
- Breaking API change on instrument-template create/update for clients that
  send non-empty `extraFields`.
- Orphaned `ExtraField` rows attached to template rows remain in the database,
  unreachable but harmless; no Liquibase change ships with this work.
- Instruments created from a template no longer inherit ad-hoc extra fields;
  the template's custom fields are the only provisioning mechanism.
- Templates stop contributing extra-field content to the search index
  (vacuously, since they can no longer have any).
- The locked default template (ADR 0003) is unaffected: it has no extra fields,
  and mutation was already blocked centrally by the `isEditable` guard.
