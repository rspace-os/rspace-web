---
status: accepted
---

# External PIDINST metadata updates ride the instrument save

## Context

Once an instrument's identifier is registered, edits to the instrument in RSpace
were never sent onward, so the provider's copy (a B2INST draft or submitted
record, a DataCite draft DOI) quietly drifted from the instrument it describes
(RSDEV-1251). The ticket text offered two triggers — an explicit "update
external PIDINST" action, or sending updates as part of saving the instrument —
and reviewers on the ticket argued against a separate button.

Provider facts that shaped the decision: B2INST speaks the InvenioRDM v3 API,
where a draft is updated by a full-replace `PUT /api/records/{id}/draft`. A
draft stays writable until the record is accepted, which publishes it and
removes the draft, so an accepted record is the one B2INST state an update
cannot reach. DataCite allows metadata updates on a draft DOI via `updateDoi`,
which the pinned `datacite-java-client` already exposes; a findable DOI already
gets full current metadata resent by the existing Republish (retract then
publish).

Both providers were probed directly in August 2026, and DataCite behaved as
assumed: `PUT /dois/{id}` with no `event` rewrote a draft DOI's metadata and
left it `draft`, bumping DataCite's own `metadataVersion` and leaving `created`,
`source`, `prefix`, `suffix` and `doi` untouched. It also ignored the
server-owned attributes RSpace puts on the wire (`isActive`, `state`,
`metadataVersion`, the counters, null `created`/`registered`), which it has no
choice but to send because `DataCiteDoiAttributes` carries no `@JsonInclude`.
`InstrumentExternalMetadataUpdateDataCiteMVCIT` pins this against the real test
API, nightly.

The B2INST probe corrected the plan on one point. A full-replace PUT carrying only the
rebuilt `access`, `files` and `metadata` blocks preserved every server-side
field (`pids`, `parent`, `access`, `files`, `expires_at`, `created`, the record
links) and bumped `revision_id`, so no GET-and-merge is needed. But the draft
also accepted the PUT while its review status was `created` (PUT but never
submitted) and `cancelled`, not only `draft` and `submitted` as the plan
assumed; cancelling a review drops the record's own status back to `draft`. That
matters because `refreshIdentifier` stores the review's status verbatim for
anything that is not accepted, so an identifier really can be sitting in
`created`, `cancelled`, `declined` or `expired`.

## Decision

1. **The save is the trigger.** The ordinary instrument update
   (`PUT /api/inventory/v1/instruments/{id}`) pushes freshly remapped metadata
   to the provider for every attached identifier in an updatable state. No new
   endpoint, no new button. The two providers state that rule from opposite
   ends, deliberately: B2INST pushes in every state **except** `accepted`,
   because every other state still has a writable draft behind it and a state
   wrongly skipped is silent drift, whereas an accepted record simply has no
   draft to write; DataCite pushes **only** `draft`, because Republish is the
   declared path for a findable DOI and a retracted (`registered`) DOI is left
   alone by choice rather than by provider refusal. Accepted B2INST records stay
   out of scope (updating one means a new draft-and-review round).
2. **The push never blocks the save.** The instrument edit commits first; the
   provider call runs after the write transaction, synchronously in the same
   request, and its outcome (success or user-readable failure reason) is
   reported in the response and audited. A failed push changes nothing locally:
   no rollback, no compensation, identifier state untouched. This deliberately
   avoids extending the existing pattern of provider HTTP calls made inside a
   write transaction, which was already flagged as a concern in the RSDEV-1260
   review.
3. **Every qualifying save pushes; no diffing, no persisted sync state.** Both
   provider updates are idempotent full-metadata writes, so a redundant push is
   harmless. Always-push self-heals: after a failed push the next save re-sends
   regardless of what changed, so retry is "save again". The alternatives were
   a diff-gated push (rejected: if a push fails and the next save changes no
   mapped field, the diff says skip and the provider stays stale forever) and a
   persisted needs-sync flag or payload hash (rejected: schema change and
   bookkeeping to solve a problem always-push does not have).
4. **The outcome is transient response data, not state.** A read-only object on
   the returned identifier DTO carries succeeded/reason for the frontend to
   surface; nothing is persisted. The push seam is the instruments controller,
   after the manager returns, so identifier operations that re-enter the
   instrument update internally (publish, retract, refresh bookkeeping,
   template sync, bulk owner change) can never trigger a recursive push.

## Consequences

- Saving an instrument with an updatable identifier gains the latency of one
  provider HTTP call, bounded by the existing connector timeouts; the save
  itself can no longer be lost to a provider outage.
- Drift windows exist only between a failed push and the next save, and the
  response says so each time.
- Sample, subsample and container draft DOIs still drift (out of scope), as do
  instrument changes made by template sync until the next ordinary save.
- Payload rebuild must run inside a (read-only) transaction because the mapping
  adapter demands one, while the HTTP call runs outside; the orchestrating
  service therefore cannot be a `*Manager` bean (whose every method is
  transactional via AOP) and declares its own boundaries.
- The full-replace semantics of the B2INST draft PUT are verified (see Context),
  so the update sends a rebuilt register-shaped payload with no GET-and-merge.
  Should a future InvenioRDM version start clobbering server-side fields on that
  PUT, the fallback is GET-draft, merge metadata, PUT.
- A retracted DataCite DOI (`registered`) still drifts. DataCite would accept a
  metadata update on it; it is excluded by this decision rather than by the
  provider, and is worth revisiting if retract-then-edit turns out to be a real
  workflow.
- DataCite's `PUT /dois/{id}` is an upsert: pushing to a DOI that was deleted at
  the provider silently recreates it as a draft rather than failing (verified,
  August 2026). So the "record no longer exists" outcome cannot arise on the
  DataCite side, and a save quietly restores a DOI someone removed there. That
  is consistent with always-push self-healing and is left as is, but it means
  RSpace's record of a DOI, not DataCite's, is what keeps it alive.
- Verifying the above turned up a **pre-existing defect on the publish/retract
  path**, unrelated to this decision but found by it: an explicit `null` clears a
  DataCite property just as `[]` does, and only an absent key preserves it. The
  environment guard in `RspaceToExternalProviderAdapterImpl.buildDataCiteDoi`
  returned leaving `relatedIdentifiers` null believing that preserved them, so on
  a deployment with no usable server URL publish and retract stripped exactly the
  entries the guard exists to protect. The on-save update was never affected (it
  touches only drafts, and registration sends an empty payload, so a draft has no
  registered related identifiers to lose).

  Fixed at the root: `DataCiteDoiAttributes` in `datacite-java-client` is now
  serialized `@JsonInclude(NON_NULL)`, so the three cases separate the way the
  calling code always assumed - `null` means "leave it alone", `[]` means "clear
  it", a populated list means "replace it" - and the guard works as written. This
  is safe for every other property because a user clearing one stores an empty
  list rather than removing the key (`applyChangesToDatabaseDOI`), so clearing
  still reaches DataCite as `[]`; only "RSpace never had a value" changes meaning,
  from "clear something that was never there" to "say nothing". Pinned by
  `DataCiteDoiTest.nullAttributesAreOmittedSoTheyDoNotClearRegisteredValues`.

  Live in rspace-web, whose pin now points at the client carrying it. The wire
  half is pinned locally too, by
  `RspaceToExternalProviderAdapterImplTest.dataCiteWireFormatOmitsRelatedIdentifiersEntirelyWhenNoUsableServerUrlExists`:
  the assertion that the environment guard omits the key rather than sending an
  explicit null is what was missing while the bug lived, since a getter assertion
  cannot tell the two apart. **Before merge** the pin must move off the branch
  SNAPSHOT to a released client version.
