---
status: accepted
---

# Public registry lookup, independent of the minting provider (RSDEV-1518)

## Context

ADR 0009 shipped Workflow 1 of the PIDINST epic by routing PID lookup and instrument import to
the single PIDINST provider enabled in the Inventory settings, with that provider's server and
credentials (its decision 3), and rejected "searching both public registries anonymously" among
its considered options. Tilo asked for the opposite on RSDEV-1325 (2026-09-08): a lookup like the
PubChem importer, needing no setup, since RSpace takes over nothing at the registry for an
imported record. RSDEV-1518 makes that the requirement: search the full public B2INST registry
and DataCite with no credentials and whether or not a provider is configured, choose one or both
registries, and page the results fifty at a time by most recent update.

Feasibility was probed live on 2026-10-05. `https://api.datacite.org/dois?query=…&resource-type-id=instrument`
answers anonymously (`x-anonymous-consumer: true`), holds 2002 instrument DOIs, pages with
`page[size]`/`page[number]` and sorts with `sort=-updated`; DataCite Commons shows the same count
for the same query (235 for "microscope"), and both Commons and Fabrica are user interfaces, not
APIs. `https://b2inst.gwdg.de/api/records?q=…` answers anonymously, is already cross-community
(3904 records, the sum of the 14 communities' own counts), pages with `size`/`page` and honours
`sort=updated-desc` although the EUDAT docs list that key only for drafts. DataCite allows 500
anonymous requests per 5 minutes per IP, 1000 when the request carries an email in `User-Agent`
or `mailto=`; B2INST answers `x-ratelimit-limit: 500` per minute. B2INST caps deep paging at
10,000 results; DataCite does not (decision 4). Nothing at either registry blocks the ticket; what
blocked it was RSpace's own gates and the credentials baked into both connectors and into
datacite-java-client.

## Decision

Decided with Nico on 2026-10-05.

1. **A PID lookup goes to the public registries, anonymously.** Their addresses are deployment
   properties, `pidinst.lookup.datacite.url` (default `https://api.datacite.org`) and
   `pidinst.lookup.b2inst.url` (default `https://b2inst.gwdg.de`), never the `pidinst.*.server.url`
   system properties and never a provider's token or password. A PIDINST provider pointing at a
   test server does not move the lookup there. A blank value is a misconfiguration: the connector
   warns at startup and a search that includes that registry fails through the existing
   registry-error path. It is not a switch; the Inventory settings' Enabled flags govern minting
   only. Supersedes ADR 0009 decision 3.
2. **Lookup and import need Inventory only.** `/pidinst/search`, `/instruments/importPidinst`
   and the "From PIDINST registry" menu item no longer require a PIDINST provider to be enabled.
   `GET /identifiers/pidinstEnabled` stays, for the Create-new-PIDINST button.
3. **The user chooses the registries; the result is one merged list.** Two checkboxes, DataCite
   and B2INST, both ticked by default; the search endpoint takes `providers` (one or both of
   `PIDINST_DATACITE` and `PIDINST_B2INST`, 422 for none or anything else) and `pageNumber`
   (0-based, as everywhere in the API), with a fixed page size of 50. Hits from the ticked
   registries form one list ordered by the registry's update time, newest first, each hit naming
   its registry; equal update times keep each registry's own order, the registries in the order
   asked. Both registries sort by update time alone and DataCite stamps it to the second (345 of
   its 2002 public instruments shared a second with another, 2026-10-06), so a tie-breaker of
   RSpace's own, as first decided (creation time, then PID), reordered ties that span two registry
   pages and made the next page repeat one hit and drop another (PullFrog on PR 1211). The
   response carries `totalHits` and `totalsByProvider`. The dialog's pager pages the search on
   screen, so it is disabled while the search box or the checkboxes differ from that search: an
   edit takes effect only on Search, never
   as page 2 of a different search under the first one's totals (Nico, 2026-10-05). Supersedes
   the one-page, name-sorted search contract of RSDEV-1326 (decision 6 of its plan, the one the
   former `PidinstLookupManager.MAX_HITS` cited), which ADR 0009 took for granted. Tabs per
   registry were offered and rejected by Nico: one list.
4. **The merge runs server-side, from cached registry pages.** Merged page k can only contain
   items from each registry's first (k+1)×50, so the manager fetches registry pages 0..k of each
   ticked registry, stopping at a short page, each one cached ten minutes by registry, query and
   page as before, merges them and slices the page. Sequential paging costs one new registry call
   per registry per page; a cold jump to page k costs k+1 per registry, then nothing. The search
   refuses any page past 199 (the first 10,000 hits) with a 422 (`pidinstPageOutOfRange`), so one
   request costs at most 200 calls per registry. A guard is needed because DataCite pages without
   limit: `query=climate` (about 614,000 hits) still served 50 records at page 5000 (checked
   2026-10-06), and the endpoint is a public API, not only the dialog's pager. The DOI-fragment
   retry of ADR 0009 decision 7 applies per page, and only while the free text matched nothing at
   all: when a DataCite page is empty with a total of 0 and the query is a bare fragment, the same
   page is asked as `doi:*<query>*`. An exhausted later page of a query that did match is not
   retried, which would fill it from another query.
5. **Import names its registry.** `POST /instruments/importPidinst` takes a required `provider`
   alongside `pid`; the dialog passes the hit's. A missing `provider` (400) and an unknown one (422)
   are both refused with the import's own message, not the search's (Nico, 2026-10-05). A PID of
   the other shape finds nothing there and answers 404 as today. Inferring the registry from the
   PID's shape was offered (every B2INST PID is a `21.*` Handle, checked live on 2026-10-05) and
   rejected in favour of the explicit field.
6. **A registry failure fails the search**, as decided on 2026-09-25 for one registry: the error
   toast names the registry, and the user can untick it.
7. **The anonymous DataCite client identifies itself** with
   `User-Agent: RSpace (mailto:<sysadmin.rspace.support.email>)`, which DataCite documents as the
   identified tier, 1000 requests per 5 minutes per IP instead of 500. Not verifiable from
   outside, since DataCite sends no rate-limit header; the cost is one header.
8. **datacite-java-client grows; rspace-web does not re-implement it.** The client gets an
   anonymous constructor, `DataCiteClientImpl(URI, String contactEmail)`, which sends no
   Authorization header, and a `searchDois` overload with a page number and a sort key. It is
   consumed as `RSDEV-1518-datacite-SNAPSHOT` while the work is open and pinned to the released
   version before merge, as for 1.3.0.

## Considered options

- **Tabs, one per registry, each paged on its own**: no merge, each registry's own order, one
  registry's failure leaves the other usable. Rejected by Nico on 2026-10-05: one list.
- **Client-side merge**: the endpoint stays single-registry and the dialog fetches pages 0..k from
  each registry and merges in TypeScript. Rejected: paging logic, totals and failure handling
  would live in React state, and API clients would get no merged view.
- **Cursor paging**: an opaque per-registry-offset cursor fetches at most two registry pages per
  registry per request, but gives forward-only navigation and an API shape unlike the rest of
  RSpace's.
- **Dummy credentials in the existing client**: DataCite answers 200 to a search carrying wrong
  Basic credentials (checked 2026-10-05), but that is undocumented behaviour and would send
  garbage credentials on every call.
- **An anonymous RestTemplate inside rspace-web** instead of changing the client: no release
  dance, but DataCite's search URL and its `+` encoding care would live in two places.
- **Blank URL hides the registry**: needs an endpoint telling the UI which registries are
  configured; Rob's comment on the ticket asks for changeable addresses, not for hiding one.

## Consequences

- `/pidinst/search` and `/instruments/importPidinst` change shape days after shipping in 2.27.0:
  `providers` is required on the search (`pageNumber` defaults to 0), `provider` on the import
  body, and the search response replaces `{provider, total, hits}` with
  `{providers, pageNumber, totalHits, totalsByProvider, hits}` (no `pageSize`: it is always 50).
  The OpenAPI spec says so.
- Linked identifiers can now exist on a deployment that has no PIDINST provider. Every
  provider-side flow already checks `isLinked()` first (ADR 0009 decision 2), so nothing calls a
  registry for them; the one gap is `DELETE /identifiers/{id}`, which still asserts a PIDINST
  provider, so on such a deployment a linked identifier is released only by trashing the Instrument
  (ADR 0010). Known and left as is (Nico, 2026-10-05).
- `B2instConnector.searchRecords` and `getRecordByHandle`, and `DataCiteConnector.searchInstrumentDois`
  and `findDoi(…, settingType)`, were used only by the lookup and are replaced by anonymous lookup
  variants on the public registry; the configured, authenticated clients serve registration and
  refresh only.
- The dev deployment and the Spring test context read the production registry addresses from
  `defaultDeployment.properties`. An MVCIT swaps the B2INST connector for a dummy and must tick
  B2INST only; ticking DataCite in a test would call `api.datacite.org`.
- The already-linked marker and the 409 keep their RSDEV-1505 rule unchanged; the already-linked
  query runs once per registry per page.
- Rob's request for admin-editable registry addresses is met by the deployment properties, not by
  a settings panel.
- Imported "authoritative" fields stay editable. RSDEV-1518 first asked for them to be read-only,
  which contradicts the import as ADR 0009 decision 4 shipped it and CONTEXT.md's *Instrument
  import* describes it (imported values are ordinary field values, and only the linked identifier
  stays tied to the registry), and would be a new concept, a per-field lock. Nico split it into
  RSDEV-1545 ("Keep authoritative imported metadata non-editable while allowing users to add
  custom metadata") on 2026-10-05, and on 2026-10-07 the ticket itself moved it under its *Out of
  scope*, handled by RSDEV-1545. Nothing in this ADR or in the code it describes locks a field.
