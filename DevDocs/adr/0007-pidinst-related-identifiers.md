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
- The link's **version pin is carried** into that address (`<id>v<pin>`) when the target
  type resolves a version-suffixed globalId, i.e. the inventory prefixes
  `GlobalLookupController` routes to the versioned viewer (SA, SS, IC, IT, IN, NT). A pin
  on any other allowed target is dropped: NB has no versioned route at all, and SD's and
  GL's lead to an audit view and a file stream rather than the record's page, so there the
  unpinned address is the safer thing to make permanent. Rationale: a pinned link names one
  version deliberately, and a registered address cannot be corrected, so it must not
  silently follow the record's latest state.
- The human label ("Measurement Technique", "Calibration", the ticket's capitalisation)
  goes to B2INST's `relatedIdentifierName` and DataCite's `relationTypeInformation`.
- `relatedIdentifierType` is `URL`.
- Guard rails mirror the landing page: an address that would not be absolute http(s) is
  omitted with a WARN; an absent field, empty, deleted or target-less link is omitted
  silently.
- **No read-permission re-check on the link target** at registration, publish or retract,
  deliberately. READ is asserted where the link is written (`InventoryLinkManagerImpl`, on
  create and update); from then on the target's globalId is part of the instrument's own
  data, and RSpace already shows that id to every instrument viewer even when the target
  itself is unreadable — the link summary redacts name and type, never the id
  (`LinkTargetSnapshotResolverImpl`). The registered entry discloses exactly that id plus a
  fixed label, never the target's name or content, and the address resolves only for
  signed-in users who pass the target's own checks. Re-checking at publish time would also
  key the permanent payload to whichever editor happens to click publish on a shared
  instrument, so the same instrument could register different metadata depending on the
  actor — a worse property than the disclosure it would prevent.
- B2INST receives the entries at draft-register time (its only metadata write). DataCite
  receives them on publish and again on retract, because both resend full metadata and
  the entries are computed from the instrument, not persisted on the DOI.
- For DataCite the list is sent **unconditionally, the empty list included**. DataCite
  replaces the whole property with what the payload carries and clears it only on an
  explicit empty array; an absent or null property leaves the registered value alone. An
  instrument whose link fields were all cleared after registration therefore has to send
  `[]`, or the entries registered beforehand stay attached to a findable DOI with no way to
  withdraw them. B2INST keeps sending null for empty, having no metadata-update call and so
  nothing to clear.

*Last calibrated* stays documentation-only; PIDINST's `Date.dateType` vocabulary is
still strictly Commissioned/DeCommissioned.

## Consequences

- `datacite-java-client` >= 1.1.0 is required (`relatedIdentifiers` on
  `DataCiteDoiAttributes`).
- The registered entries reflect the link fields at register/publish time; editing a
  link afterwards updates nothing at the provider (B2INST has no metadata-update call;
  DataCite would refresh on the next publish/retract).
