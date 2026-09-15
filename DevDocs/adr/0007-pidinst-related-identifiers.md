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
- **The target is re-checked at registration time, as the instrument's owner**, and an
  entry is omitted (with a WARN) when the target is deleted or the owner cannot read it.
  Write-time READ (`InventoryLinkManagerImpl`) is not enough on its own: duplicating an
  instrument copies its links with no target check, so a link can exist that nobody ever
  verified; sharing can be revoked after the link was made; and deleting a record does not
  delete links pointing at it, so an unchecked entry could permanently name a dead record.
  The owner is the actor, not whoever triggers the registration, because the owner is
  stable: the payload must not vary with which editor of a shared instrument happens to
  click. The check runs through the same snapshot the link-card UI shows
  (`LinkTargetSnapshotResolver`), so registration and display cannot disagree about a
  target being deleted or redacted. What a qualifying entry still discloses publicly is the
  bare globalId plus a fixed label, never a name or content, and the address resolves only
  for signed-in users who pass the target's own checks.
- B2INST receives the entries at draft-register time. DataCite receives them on publish and
  again on retract, because both resend full metadata and the entries are computed from the
  instrument, not persisted on the DOI.

  **Amended by ADR 0008 (RSDEV-1251).** Register and publish/retract are no longer the only
  metadata writes: an ordinary instrument save now also pushes to a writable provider record,
  through `B2instConnector.updateDraftDoi` and `DataCiteConnector.updateDoi`, so both providers
  receive the entries on that path too.
- For DataCite the list is sent **unconditionally, the empty list included**. DataCite
  replaces the whole property with what the payload carries and clears it only on an
  explicit empty array; an absent property leaves the registered value alone.

  **Corrected by ADR 0008.** An explicit `null` does *not* leave the value alone: it clears it
  exactly as `[]` does, and only a key absent from the payload preserves it (verified against
  api.test.datacite.org, August 2026). That is why `DataCiteDoiAttributes` is now serialized
  `@JsonInclude(NON_NULL)`, so a null property becomes an absent key. An
  instrument whose link fields were all cleared after registration therefore has to send
  `[]`, or the entries registered beforehand stay attached to a findable DOI with no way to
  withdraw them. The one exception is an **environment failure**: when no usable http(s)
  server URL exists, no address can be built for any link, so an empty list would be
  indistinguishable from "the user cleared the fields" and would strip entries that are
  still correct — the property is left untouched instead, until the deployment is fixed.
  Emptiness that reflects the data (fields cleared, targets deleted or no longer
  owner-readable) does clear. B2INST keeps sending null for empty, having no
  metadata-update call and so nothing to clear.

*Last calibrated* stays documentation-only; PIDINST's `Date.dateType` vocabulary is
still strictly Commissioned/DeCommissioned.

Two wire-level caveats, accepted: DataCite's `relationTypeInformation` was introduced in
Metadata Schema 4.7 (March 2026), so a registrar still on 4.6 or earlier silently drops
the label while keeping the rest of the entry. And the same literal serves two subtly
different slots — B2INST's `relatedIdentifierName` names the linked resource, DataCite's
`relationTypeInformation` describes the relation — which is deliberate: one label per
field keeps the two registries recognisably in step.

## Consequences

- `datacite-java-client` >= 1.2.0 is required. 1.1.0 adds `relatedIdentifiers` to
  `DataCiteDoiAttributes`, but serializes a null property as an explicit null, which
  DataCite treats as "clear it". The mapping below leaves `relatedIdentifiers` null when
  it cannot build the addresses, meaning "leave the registered entries alone", so against
  1.1.0 that strips them from a findable DOI. 1.2.0 omits a null property instead.
- The registered entries reflect the link fields at register/publish time; editing a
  link afterwards updates nothing at the provider (B2INST has no metadata-update call;
  DataCite would refresh on the next publish/retract).
