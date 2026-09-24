---
status: accepted
---

# Linked identifiers for instruments imported from a PID registry (RSDEV-1326, UI RSDEV-1325)

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
   in the deployment is refused with 409 naming the instrument. That check matches the
   provider's record id as well as the PID, because a B2INST identifier this deployment
   registered stores the record id: its Handle is minted on publish and never written back.
   *Amended 2026-09-21 (RSDEV-1505):* the 409 names the instrument only when the caller may
   read it, by the read-or-limited-read rule that decides everywhere else whether a caller gets
   a record or only its no-access view; otherwise it says only that an instrument they cannot
   access already holds the PID. The search response marks every such hit `alreadyLinked` and
   carries `linkedInstrumentGlobalId` under the same rule, so neither the search nor the refusal
   volunteers the Global ID of an Instrument the caller may not read (the principle of ADR
   0002). The registry record itself is public, so nothing about it is withheld, and the refusal
   keeps a stated reason either way. The disclosure this decision originally accepted was
   pre-existing from RSDEV-1326 and became visible once the RSDEV-1325 dialog rendered the
   Global ID as a chip. This is a rule about what RSpace offers, not a secrecy guarantee:
   `GET /instruments/{id}` deliberately answers 200 with a name-only public view rather than
   404, so a guessed id still yields the instrument's name (verified 2026-09-22). Note the rule
   is deliberately looser than the one behind the *No access* link-target pill, which uses plain
   read: a viewer with only limited read, through a container they can read or a document whose
   List of Materials lists the Instrument, is named it here because both of those contexts
   already show its Global ID.
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

6. **A search needs at least 4 characters**, counted after trimming, for either provider
   (RSDEV-1325). Below that a free-text query matches a large part of the registry, which is a slow
   provider call for a page nobody wanted, and it is the shortest DOI suffix fragment worth
   matching. The rule lives in `PidinstLookupManagerImpl.search`, not the controller, so a direct
   caller gets it too; it subsumes the blank check the controller used to make, and answers 422
   with `errors.inventory.identifier.pidinstQueryTooShort` rather than a bare
   IllegalArgumentException. The import dialog mirrors the number and keeps Search disabled below
   it, naming the minimum under the field, so the ordinary case never makes the round trip; the
   server stays the authority, and a client that ignores it still gets the 422.
7. **The DataCite free-text query is escaped, and a search that finds nothing is retried as
   `doi:*<query>*`** (RSDEV-1325). DataCite's `query` is Elasticsearch query-string syntax whatever
   the caller intends, so user text reaches it escaped: unbalanced syntax answers 400 rather than an
   empty page, and a dialog can only show a 400 as an error. Verified 2026-09-18 against
   api.datacite.org: `foo"bar`, `foo[bar`, `foo{bar`, `(foo`, `foo!`, `foo^`, `zeiss &&` and a
   dangling `abc OR` all answer 400, and every escaped form answers 200. The escape covers the
   reserved characters and the bare `AND`/`OR`/`NOT`, so the search box is literal text rather than
   a query console. `/` is left alone on the same evidence that keeps it in the allow-list below:
   `10.5281\/zenodo` answers 400 where `10.5281/zenodo` answers 200, so escaping it would break the
   pasted fragments this search exists to match. Escaping costs nothing elsewhere: `\Zeiss` and
   `Zeiss` both answer 71, `spectrometer\*` and `spectrometer` both 146. `<`, `>` and `=` are
   deliberately *not* escaped, and cannot be: a backslash before one is ignored. They need no
   handling, because a range exists only against a field and the `:` that builds one is escaped.
   `publicationYear:>2020` answers 95,411,191, but `publicationYear\:>2020`, which is what RSpace
   sends, answers 15, the same as the plain `publicationYear 2020`. Loose, `Zeiss>4` answers 6,539,
   exactly what `Zeiss 4` and every other separator the analyser splits on answer, so the character
   is inert rather than parsed; comparing it against `Zeiss` alone (42,170) only measures the second
   term. Pinned by a test, because this has already been raised once in review.

   The retry is the second half. DataCite indexes the DOI as a keyword, so free text never matches a suffix or part of one: a
   search for `qvtb-aw74` answers nothing though `10.82316/qvtb-aw74` is findable, which is what a
   user who pasted half a DOI sees. Verified against api.test.datacite.org on 2026-09-16: the bare
   suffix returns 0, `doi:*qvtb-aw74*` returns 1, `doi:*qvtb*` returns 1, and the wildcard matches
   across the slash and whatever the case. The retry runs only when the first page is empty, so a
   search that already found something still costs one call, and only when the query is a bare
   `[A-Za-z0-9._/-]+`, so what the user typed cannot close the `doi:` clause or open another. That
   allow-list is evidence-led rather than derived from the syntax: `/` and `-` *are* reserved in
   query-string syntax but DataCite accepts them inside a wildcard term, and keeping `/` is what
   lets a pasted prefix/suffix pair match. Verified 2026-09-17 against api.datacite.org:
   `doi:*qvtb/aw74*` answers 200, `doi:*5281/zenodo*` (12.89M) narrows `doi:*5281*` (12.91M) so the
   wildcard really does span the slash, and `doi:*"broken*` answers 400. Widening the class further
   needs the same kind of evidence, and both halves are pinned by tests. The B2INST side needs no
   retry: InvenioRDM tokenises its Handle field, so a bare suffix fragment already matches
   (verified 2026-09-22, `twwkx` finds `21.T11975/twwkx-1zd85`). Its *escaping* is a different
   matter and is a known gap, tracked as RSDEV-1524.

8. **B2INST gets a substring search; DataCite gets the query as typed** (RSDEV-1522).

   The defect: both registries match whole *analysed tokens*, and the standard tokenizer follows
   Unicode UAX #29, where `_` is a word character but `-` is not
   (https://www.elastic.co/docs/reference/text-analysis/analysis-standard-tokenizer). So
   `Nico-PIDINST_VAIDA_TILO` indexes as `nico` and `pidinst_vaida_tilo`, and a search for `Vaida`
   found nothing although the record was plainly there. Verified 2026-09-22: `Vaida` answers 0 on
   both registries while `*Vaida*` answers 3 on b2inst-test.gwdg.de and 1 on
   api.test.datacite.org.

   **B2INST: the whole query, spaces escaped, wrapped in `*...*`.** The escape keeps it a single
   wildcard term, so it is a real "contains" including spaces. Verified against b2inst-test.gwdg.de:
   `*Instr1\ prova_COPY*` answers 1 where the unescaped `*Instr1 prova_COPY*` answers 2, and
   `*nstr1\ prova_CO*` still finds `Instr1 prova_COPY` with both words cut at both ends. It is also
   the cheapest shape, one leading wildcard however many words: 0.2-0.4s. Leaving the spaces raw
   does not work, because Elasticsearch parses the query *before* the wildcards apply and splits it
   on whitespace itself, leaving `*Instr1` (ends-with) and `prova_COPY*` (starts-with), which
   B2INST joins with OR.

   **DataCite: the query as typed, escaped but not wildcarded**, so a search returns what the same
   words return in DataCite's own portal. This is a deliberate reversal, decided by Nico on
   2026-09-24 after seeing what wildcarding did there, and it gives up the defect above on the
   DataCite side: `Vaida` answers 0 again, as it does in the portal.

   The reversal was driven by over-matching, not by cost. On api.test.datacite.org, `PIDINST Test`
   answers **1** both in the portal and as plain free text, but **145** once wildcarded. The 145 are
   e2e fixtures matching on fields the results list never shows - a title tokenising to a `pidinst`
   word, and `TestDescriptionAbstract` / `TestSubject` in `descriptions` and `subjects` - so a
   common word like "test" matches almost everything in a test registry. A wildcard per word
   (`*PIDINST* AND *Test*`) answers the same 145 at 3.8s against 0.45s, so it does not help either.
   Scope is not the cause: plain `PIDINST Test` across all of DataCite, instruments only, also
   answers 1.

   The consequence is asymmetry between the providers, which is accepted: B2INST finds a substring
   of a name, DataCite does not. Two earlier shapes were tried and reverted along the way, a
   wildcard per word (`*a* AND *b*`: one word 24s, two 23s, three **31s**, four **66s**, against
   the DataCite client's then-30s read timeout) and splitting the query into analyser words before
   wildcarding (`*a AND b*`), both superseded by this decision. Filtering the returned page inside
   RSpace was also built and rejected: it made `total` disagree with the registry and silently
   dropped hits matched on fields the response does not carry.

   The rest is unchanged. The escape of decision 7 still applies to the DataCite query, because
   unbalanced syntax answers 400 rather than an empty page; it is transparent otherwise, so it does
   not change which records match. The `doi:*<query>*` retry of decision 7 is load-bearing again,
   since plain free text cannot match the DOI keyword: verified 2026-09-24, `82316/qvtb\-aw74` and
   `qvtb\-aw74` both answer 0 as free text while `doi:*82316/qvtb-aw74*` and `doi:*qvtb-aw74*`
   answer 1. The 4-character minimum of decision 6 is unchanged. B2INST receives its query with no
   escaping at all, which is its own defect (RSDEV-1524) and unchanged here; the wrap neither
   causes nor worsens it.

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
- **For the DOI miss: always search `(<query>) OR doi:*<query>*`.** One call instead of two on a
  miss, but it would make RSpace *build a clause* out of user text on every search, so a stray
  quote errors the query rather than simply not matching. (The plain query is already passed to the
  same Elasticsearch-backed `query` parameter, so the difference is who composes the syntax, not
  whether the parameter interprets it.) Rejected in favour of the guarded retry, which composes a
  clause only on an empty page and only from characters DataCite is known to accept.
- **For the DOI miss: reconstruct the full DOI from the deployment's repository prefix.** Exact,
  but it only finds a DOI minted under that prefix, and the lookup is for the whole registry.
- **Allow duplicate links, flagged**: rejected in favour of one RSpace record per PID;
  the search response still flags an already-linked PID. The UI (RSDEV-1325) keeps such a hit
  selectable and refuses Import with the reason, rather than disabling the button, so the user
  can still read the record and follow the chip to the instrument that holds the link. Since
  RSDEV-1505 the chip is shown only to a user who may read that instrument; anyone else sees
  the flag without a name (decision 4, amendment).

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
- The UI offers no way to unlink, and that is intended for now (Nico, 2026-09-09): the row shows
  Retract, disabled, and never Delete, so removing a link needs the API (`DELETE
  /identifiers/{id}`, which the server allows in every state). RSDEV-1325 shipped the import UI
  without an unlink action, so this stands; revisit if users ask to unlink from the page.
  Half-superseded by ADR 0010: there is still no unlink action on the page, but trashing the
  Instrument now unlinks whatever it carried (RSDEV-1504).
  - The disabling is now explicit rather than incidental. It used to hold only for B2INST, where
    the review-state rule happened to disable the button; a linked DataCite PID is `findable`, so
    nothing caught it and the row offered an enabled Retract that the server answers with 422.
    The panel keys on `linked` directly, so the rule holds for both registries.
  - For the same reason the row offers no Preview and explains the identifier with its own
    sentence: a linked PID resolves at its registry, and `findPublishedItemVersionByPublicLink`
    serves no RSpace page for it. No `LOCAL_URL` is stored for a linked identifier either, since
    that address surfaced over the API as its `url` and resolved to a permanent 404.
- Registration credentials are used for read-only searches; verified on
  b2inst-test.gwdg.de and api.test.datacite.org (September 2026) that an authenticated
  search still returns the global published registry, not the account's own drafts.
- `identifier` has no unique key (soft-deleted rows keep their value and MariaDB has no
  partial indexes), so two concurrent imports of one PID can both succeed; accepted. There is a
  non-unique index on `(type, identifier(190))` for the lookup itself, which is about speed, not
  uniqueness: without it every hit on a page of search results scanned the whole table.
- The lookup shares the provider's availability: a provider outage disables lookup as
  well as registration, and the 10-minute result cache is evicted whenever the
  provider settings are reloaded.
- DataCite's `searchDois` and typed `contributors`/`identifiers` live in
  datacite-java-client, so the change rides a client release and a pin bump.
