---
status: superseded
---

# Measured-variable narratives for PIDINST/B2INST registration

> **Superseded (2026-07-27), during RSDEV-1220, before any of this shipped.**
> The narratives were dropped. `MeasuredVariable` now carries only the *Measured
> quantity* field's content verbatim, which is what the property was designed
> for. Nothing narrative reaches `MeasuredVariable` from the other fields.
> *Measurement technique* and *Calibration* were documentation-only from then
> until RSDEV-1253 mapped them to `RelatedIdentifier` entries, resolving the
> vocabulary clash by fixing the sent relation to `IsDescribedBy` (see ADR
> 0007). Only *Last calibrated* remains unmapped, PIDINST's Date vocabulary
> still having no home for it. The reasoning below is kept because it explains
> why the narrative route stays closed; anyone proposing to map these fields
> another way should start here and read the Considered options.

## Context

RSDEV-1220 maps the default PIDINST instrument template's fields into the
B2INST draft-record metadata. Four template facts have no exact PIDINST home:
the document a *Measurement technique* is documented by (link), what a
*Calibration* is calibrated/described by (link), the *Measured quantity*
(text) and the *Last calibrated* date. PIDINST's `MeasuredVariable` expects
plain variable names ("Air temperature"); `RelatedIdentifier` is the
semantically correct property for the two links, but its `relationType`
controlled vocabulary (`IsDescribedBy, IsNewVersionOf, IsPreviousVersionOf,
HasComponent, IsComponentOf, References, HasMetadata, WasUsedIn,
IsIdenticalTo, IsAttachedTo`) contains neither `IsDocumentedBy` nor
`IsCalibratedBy` — the relation types the template's link fields actually
store. (`Last calibrated` also cannot become a `Date` entry: that property's
`dateType` vocabulary is strictly `Commissioned`/`DeCommissioned`.)

## Decision

Emit all four facts as human-readable **measured-variable narratives** —
strings appended to `MeasuredVariable`, opening with the canonical field name
and, for link-backed facts, the relation type stored on the link:

- `Measurement technique IsDocumentedBy <serverUrl>/globalId/<target>`
- `Measured quantity is <content>`
- `Calibration <IsCalibratedBy|IsDescribedBy> <serverUrl>/globalId/<target>`
- `Last calibrated on <yyyy-MM-dd>`

## Considered options

Mapping the link-backed facts to `RelatedIdentifier` was explored mid-design
and deliberately rolled back: because the stored relation types are
off-vocabulary, conformant output would demand lossy translation
(`IsDocumentedBy` → `IsDescribedBy`, `IsCalibratedBy` → `References`),
silently rewriting the relation the user chose in RSpace. Production B2INST
does accept off-vocabulary values (records exist with non-vocabulary
`relatedIdentifierType`), but writing off-list values into a controlled field
merely relocates the non-conformance. Narratives keep the user's relation
verbs verbatim, at the acknowledged cost of bending `MeasuredVariable`
semantics.

## Consequences

- Published B2INST records carry narrative strings, not machine-readable
  relations; harvesters cannot follow the calibration/technique links
  structurally.
- Revisit if PIDINST/B2INST gains calibration-capable relation types or a
  community extension; migrating means re-mapping narratives to
  `RelatedIdentifier` for future registrations (already-published records keep
  their narratives).
- The glossary term *measured-variable narrative* in `CONTEXT.md` records the
  vocabulary; the mapping itself lives in
  `RspaceToExternalProviderAdapterImpl`.

## Outcome

Reversed before release. The narratives never reached a production record, so
there is no migration to do: no published B2INST record carries them. What
replaced this decision is the plain mapping described in the note at the top,
and the *documentation-only field* glossary term that took the place of
*measured-variable narrative* in `CONTEXT.md`.
