---
status: accepted
---

# Linked identifiers for instruments imported from a PID registry (RSDEV-1326)

## Context

Workflow 1 of the PIDINST epic (RSDEV-1030, story RSDEV-1033) lets a user find an
instrument that already has a PID, a B2INST Handle or a DataCite instrument DOI, and
create an Inventory Instrument from its metadata. Until now every identifier RSpace
stored was one RSpace had minted itself: the B2INST `identifier` column holds a draft
record id that is put into provider URLs, the on-save external metadata update (ADR
0008) rewrites any non-accepted B2INST record and any DataCite draft, the `accepted`
and `findable` states open RSpace's own public landing page (ADR 0006), and the UI
offers publish, retract, refresh and delete from the state alone. A PID minted by
another party must not be treated as ours, and there is no column that says whose it
is.

TIB's pidinst-search (MIT, https://search.pidinst.org) was evaluated as middleware
for the lookup and not reused: it is a server-rendered Flask app with no API contract,
and the two anonymous registry calls it makes are trivial from Java. Its query routing
and API field names were ported instead.

## Decision

1. An imported PID is stored as a `DigitalObjectIdentifier` of the provider's type
   (PIDINST_B2INST or PIDINST_DATACITE) with the Handle or DOI as `identifier`, the
   provider's own state (`accepted` for a published B2INST record, `findable` for a
   DataCite one - only public PIDs may be linked, see decision 5),
   the provider record page as PROVIDER_URL, the resolver address as PUBLIC_URL, and
   `ORIGIN=EXTERNAL` in the otherData JSON map. This is a **linked identifier**
   (CONTEXT.md). Everything RSpace registered itself is a **registered identifier** and
   carries no marker, so no migration is needed.
2. Every RSpace-owned flow checks `isLinked()` first: the on-save external metadata
   update skips it, publish, retract and refresh refuse it (422), deleting it deletes
   nothing at the provider and is allowed in every state (it is unlinking), the public
   landing page is never served for it, and registering another PID is refused by the
   existing one-identifier-per-record rule. The UI must not OFFER what the server refuses:
   the identifiers panel withdraws the publish action for a linked identifier of either
   provider, and for any B2INST identifier whose community review is over (`accepted`,
   `declined`, `cancelled`, `expired`), rather than rendering it disabled, and it withdraws
   refresh for a linked identifier on that same flag rather than on the absence of a Handle,
   which only ever hid it by coincidence. `linked` is on every
   `ApiInventoryDOI` for exactly this purpose.
3. Lookup and import route to the single PIDINST provider enabled in the inventory
   settings, with its configured server URL and credentials, through the existing
   connectors. No cross-registry search, no separate lookup hosts, no provider filter
   in the UI. A PID of the other registry yields no hit.
4. Import is one shot: `POST /api/inventory/v1/instruments/importPidinst {pid}`
   re-fetches the record server-side, fills the locked default template "Instrument
   (PIDINST 1.0)" by the inverse of the PIDINST mapping (multi-valued properties joined
   with "; ", description cut to 250 characters, a missing DataCite Owner falls back to
   the publisher, a missing mandatory value refuses the import), creates the instrument
   and attaches the linked identifier in one transaction. A PID already linked anywhere
   in the deployment is refused with 409 naming the instrument.
5. **Only public records may be looked up or imported**: B2INST `accepted` (a published
   record) and DataCite `findable`. A PID that exists at the provider but is not public -
   a B2INST draft, a record submitted for community review or declined, a DataCite `draft`
   or `registered` DOI - is reported exactly like one that does not exist (404), because
   for a DOI belonging to another repository DataCite itself answers 404 and RSpace cannot
   tell the two apart. Both providers apply the filter at the source: B2INST's
   `/api/records` index is published-only, and DataCite is asked with its `state=findable`
   request parameter, so each `total` describes the same set as the page it accompanies.
   RSpace re-checks the state on every hit as well, so the rule holds whatever an index
   returns. The consequence accepted here is that a user cannot import their own
   in-progress registration until it is public; the reason is that a non-public PID has no
   resolvable landing page, so linking one would put an address in the Identifiers card
   that answers nothing.

## Considered options

- **Fields only** (Alternate Identifier and Landing page, no identifier row): no link
  concept, and a second PID could be minted for an instrument that already has one.
- **A new `IdentifierType` per provider**: explicit, but the type also selects the
  provider, so two values would be needed and every switch on type grows.
- **Searching both public registries anonymously**, as pidinst-search does: delivers
  the cross-registry search first asked for on RSDEV-1325, but ignores the configured
  provider and the linked identifier's type would not match the deployment's own
  provider. Nico and Tilo agreed on single-provider routing on 2026-09-07.
- **Two-step import** (the server returns a prefilled instrument the client posts
  back): the create endpoint would have to re-fetch the PID to verify client-sent
  metadata, and `identifiers` are ignored on create today.
- **Allow duplicate links, flagged**: rejected in favour of one RSpace record per PID;
  the search response still flags an already-linked PID so the UI can disable Import.

## Consequences

- A reader seeing an identifier whose `identifier` is a Handle rather than a record
  id, or whose state is `accepted` with no review history, must check `isLinked()`;
  the identifier manager, the external update service, the identifiers controller and
  the public-page lookup all do.
- Publisher and Publication Year are DataCite minting metadata and are NOT required for a
  B2INST identifier. The identifiers panel already hides both fields for B2INST, because
  B2INST keeps its own community metadata, so requiring them produced a "some required
  details are missing" warning against fields the user was never shown. An imported
  identifier has neither, which is how the incoherence surfaced.
- B2INST has no *retract*, and a published record cannot be removed with a deployment's
  credentials either. Verified on b2inst-test (InvenioRDM 13.0) on 2026-09-09 by publishing a
  throwaway record and calling `DELETE /api/records/{id}` on it: the route exists (`OPTIONS`
  reports GET, PUT, DELETE) but answers **403 Permission denied**, and the record stayed published
  with its Handle resolving. The token used even holds community-curator rights, since it accepted
  its own review, so deletion is reserved for instance administrators. InvenioRDM 13 does document
  user-initiated deletion (immediate within a grace period, deletion requests after it), but no
  such route is exposed here: `/api/records/{id}/requests` accepts no POST, and every candidate
  `delete-request` path 404s. So "RSpace never deletes a linked identifier at the provider" is a
  choice that the provider also enforces, not merely a policy of ours.
- The UI offers no way to unlink, and that is intended for now (Nico, 2026-09-09): for a
  linked `accepted` B2INST identifier the row shows Retract, disabled, and never Delete, so
  removing a link needs the API (`DELETE /identifiers/{id}`, which the server allows in every
  state). Revisit in RSDEV-1325 if users need to unlink from the page.
- Registration credentials are used for read-only searches; verified on
  b2inst-test.gwdg.de and api.test.datacite.org (September 2026) that an authenticated
  search still returns the global published registry, not the account's own drafts.
- `identifier` has no unique key (soft-deleted rows keep their value and MariaDB has no
  partial indexes), so two concurrent imports of one PID can both succeed; accepted.
- The lookup shares the provider's availability: a provider outage disables lookup as
  well as registration, and the 10-minute result cache is evicted whenever the
  provider settings are reloaded.
- DataCite's `searchDois` and typed `contributors`/`identifiers` live in
  datacite-java-client, so the change rides a client release and a pin bump.
