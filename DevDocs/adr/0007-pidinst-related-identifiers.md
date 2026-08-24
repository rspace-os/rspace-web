---
status: accepted
---

# PIDINST related identifiers for Measurement technique and Calibration

## Context

ADR 0005 left *Measurement technique*, *Calibration* and *Last calibrated* unmapped
("documentation-only"): PIDINST's `RelatedIdentifier.relationType` vocabulary contains
neither `IsDocumentedBy` nor `IsCalibratedBy`, the relation types the template's link
fields store, and inventing narrative workarounds was rejected. RSDEV-1253 now requires
the two link fields to reach both providers as related identifiers.

## Decision

At registration the two link fields become related-identifier entries, in both the
B2INST draft metadata (`RelatedIdentifier`) and the DataCite attributes
(`relatedIdentifiers`):

- `relationType` is always `IsDescribedBy`, whatever relation the link stores. It is
  the one semantically usable type present in PIDINST's vocabulary; the stored relation
  survives unchanged inside RSpace.
- The value is the link target's internal globalId page, `<serverUrl>/globalId/<id>`,
  built by `InventoryUrls.globalIdPageUrl`. The target generally has no public page, so
  the sign-in-walled address is accepted (unlike a LandingPage, ADR 0006, a
  RelatedIdentifier makes no promise of anonymous resolvability).
- The human label ("Measurement Technique", "Calibration", the ticket's capitalisation)
  goes to B2INST's `relatedIdentifierName` and DataCite's `relationTypeInformation`.
- `relatedIdentifierType` is `URL`.
- Guard rails mirror the landing page: an address that would not be absolute http(s) is
  omitted with a WARN; an absent field, empty, deleted or target-less link is omitted
  silently.
- B2INST receives the entries at draft-register time (its only metadata write). DataCite
  receives them on publish and again on retract, because both resend full metadata and
  the entries are computed from the instrument, not persisted on the DOI.

*Last calibrated* stays documentation-only; PIDINST's `Date.dateType` vocabulary is
still strictly Commissioned/DeCommissioned.

## Consequences

- `datacite-java-client` >= 1.1.0 is required (`relatedIdentifiers` on
  `DataCiteDoiAttributes`).
- The registered entries reflect the link fields at register/publish time; editing a
  link afterwards updates nothing at the provider (B2INST has no metadata-update call;
  DataCite would refresh on the next publish/retract).
