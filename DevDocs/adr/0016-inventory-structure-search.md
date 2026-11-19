---
status: accepted
---

# Inventory structure search reuses the Workspace chemistry search and resolves hits through Gallery files

Date: 2026-11-18. Ticket: RSDEV-210. Covers the `chemQuery` / `chemSearchType` parameters on
`GET /api/inventory/v1/search`, `InventoryStructureSearchManager`, and the "Chemical" filter in the
main Inventory search. The mechanics live in the code and its tests; this ADR records the WHY and the
traps. Terms follow `CONTEXT.md` ("Structure search", "Structure hit").

## Context

Users want to find the Inventory items that carry a chemical structure, by drawing or pasting it, and
see where those items are stored. The Workspace already has a "Chemical" search that sends a SMILES
string to the chemistry service (Indigo) and gets back matching `RSChemElement` ids. Inventory items
have no chemistry of their own: structures only reach them as attached files.

## Decisions

### D1 Same search mechanism as the Workspace, no Inventory-side chemistry index

The search calls `ChemistryProvider#search(smiles, searchType)` exactly as the Workspace does, then
maps the returned `RSChemElement` ids to Inventory records:
`RSChemElement.ecatChemFileId` (a Gallery chemistry file) to the live `InventoryFile` attachments of
that file (record-level attachments, or files in an `InventoryAttachmentField`) to the owning record.
The ids are resolved in batches of `chemistry.search.chemIdsDbQuery.pageSize`, with projection queries
that load no file content. Stale ids with no matching row simply drop out.

A separate Inventory structure index was rejected: it would need its own schema, backfill and sync,
and would drift from the Workspace's matching rules. Reusing the provider keeps one source of truth.

An attachment only counts while it holds the Gallery file's **current** version
(`InventoryFile.fileProperty` equals `EcatMediaFile.fileProperty`). Replacing a Gallery file updates
its `RSChemElement` to the new structure, but existing attachments keep the old file. Resolving them
through the Gallery link alone would return them for the new structure and miss them for the old one,
so after a replacement they are found by neither. Copies (duplicated records, records created from a
template, copied attachment fields) keep the Gallery link in `InventoryFile#shallowCopy`, so they are
found like the original. The batch queries fetch the owning-record links, so each record arrives with
its attachment instead of in a follow-up query. The records' own eager collections still load per
record.

**Trap:** only files attached **from the Gallery** are searchable. A file uploaded straight into an
Inventory record is stored as a `GENERAL` `InventoryFile` with no media file and no `RSChemElement`,
so the chemistry service never sees it. Making those searchable needs chem-element generation on
Inventory upload, which is out of scope (the ticket lists it as a Won't). Link fields, preview images
and ELN stoichiometry links are not searched either.

### D2 A structure hit is the record carrying the file, with no roll-up or roll-down

A structure hit is the record a matching file is attached to, deduplicated by record. A file on a
sample returns the sample, not its subsamples; a file on a subsample returns that subsample, not its
sample. Rolling up or down would add duplicates and was not asked for by the ticket's Musts. Users
reach locations by opening the sample, or from the subsample row's Location column (D7).

### D3 Structure-only searches bypass Lucene; structure plus text goes through it

Without a text query, hits are filtered in Java (read permission, deleted option, type, owner,
bookable, and the existing template and workbench exclusions) and repaginated with
`sortRepaginateConvertToApiInventorySearchResult`. This avoids Lucene, though D3a applies its own
1000-hit limit.

With a text query, the hit global ids are passed to `searchInventoryWithSimpleQuery` through a new
`limitResultsToGlobalIds` parameter on `SearchManager` (intersected with a basket limit when both are
set). Reimplementing text matching outside Lucene was rejected. The cost is that Lucene still caps
its raw hits at `SearchConfig.maxResults` (1000) **before** the id limit is applied, so matches can
be missed. This is a documented limitation: `InventorySearchConfig.luceneResultLimitReached` is set
by `FullTextSearcherImpl`, surfaced as `textSearchLimitReached` on the response, and shown as a
warning in the results feedback.

### D3a Hit resolution stops at 1000 items

Every hit is loaded and permission-checked before sorting and paging, so the work grows with the
number of matches, and the Workspace repeats it on every page. Resolution is therefore a lazy stream,
one batch of Gallery files at a time, that stops after 1000 hits passing every filter (permission,
template subsample, workbench, deleted option, type, owner, bookable). 1000 matches the text search's
Lucene limit. When more matched, the response carries `structureSearchLimitReached` and the results
feedback shows a warning. Sorting applies only within those 1000. The Workspace listing uses the same
limit silently, because `ChemicalSearchResults` has no notice field. Loading only the requested page
would remove the limit; that is a follow-up, not yet ticketed.

The chemistry service is called by `InventorySearchApiController` before the transactional
`InventoryStructureSearchManager`, which takes the chem ids, as the Workspace path does. A slow
chemistry service then holds no database transaction.

### D4 Inventory read permission decides, not Gallery permission

A hit is returned when the user can read the Inventory record (`canUserReadInventoryRecord`). Read
access to the attached Gallery file is not required, matching how attachments are shown on the
record. Users with only limited read access get no hit, because the limited view hides attachments
and a hit would leak that the record carries the structure.

### D5 Scope conflicts are refused, except the bench, which is cleared

Structure search is instance-wide over what the user can read. It refuses to combine with a container
or basket scope (`parentGlobalId`) or with `requestable`: the backend returns 422
(`errors.inventory.search.chemistry.incompatibleFilter`), and the UI disables the conflicting buttons
both ways, with the reason as a tooltip. Combining with a container scope would need recursive
containment checks for little gain.

A bench scope is the exception: applying a structure clears the bench scope instead of blocking,
because Inventory opens on My Bench and the filter would otherwise be disabled on arrival.

This differs on purpose from Bookable and Requestable, which clear each other automatically. Here
nothing silently clears the structure, which is the more expensive thing for the user to re-enter.
Type, Owner, Status, Bookable and Tags combine normally. The bookable instrument lookup was extracted
from `SearchManagerImpl` into `BookableInstrumentLookup` so both paths share it.

### D6 Offered only when Indigo and the Chemistry app are both on

The feature needs `chemistry.provider=indigo` and the `CHEMISTRY` integration both available and
enabled for the user. The frontend hides the button otherwise, and the backend returns 422
(`errors.inventory.search.chemistry.unavailable`), so a saved search or a hand-made URL fails
visibly: the chip turns to its error state and no results are shown until it is removed. An invalid
`chemSearchType` stays a 400 parameter validation error. The UI is an opt-in `CHEMISTRY` search
module that only the main Inventory search enables. Pickers and other embedded searches are left
unchanged.

`chemQuery` and `chemSearchType` live in the URL, so they are kept in navigation links and Saved
Searches like every other filter. `SUBSTRUCTURE` is the default, as in the Workspace.

### D7 Location is shown by defaulting a List view column, not by a new column

While a structure filter is active, the List view's third adjustable column shows Location unless
the user has picked that column. A dedicated column was rejected to keep the table layout unchanged.
**Known gap:** the third adjustable column only renders on wide layouts, so narrow screens (or a
1280 px window with the right panel open) show no Location. This was accepted for now and may be
revisited.

### D8 One Ketcher validation and match-type control for both searches

The single-molecule validation and the Substructure/Exact radio group moved from the Workspace's
`ChemicalSearcher` to `components/Ketcher/StructureSearchControls.tsx`, and their strings moved to
`common:ketcher.structureSearch.*`. The Workspace and Inventory dialogs now share one implementation,
so the two searches cannot drift apart.

## Consequences

- No schema change was needed. All the new queries read existing tables.
- The Workspace chemical search (`POST /chemical/search`) also lists Inventory structure hits (the
  ticket's Should), after the document and Gallery hits, when Inventory and structure search are
  available to the user. `RSChemService` queries the chemistry service once and passes the chem
  ids to both the ELN filtering and `InventoryStructureSearchManager#findReadableStructureHits`,
  which applies the same rules as Inventory search (readable, not deleted, every type).
  **Paging trap:** the ELN search only collects hits up to one page past the current page, so its
  total is often unknown. Inventory hits are fetched only once the ELN total is known (fewer hits
  than that limit); before then the page, and the next, are all ELN hits and the total stays
  unknown. Once known, the list is the ELN hits followed by the Inventory hits, so pages and the
  total are exact. Inventory rows carry `source=INVENTORY`, their record type and no `chemId`, and
  the UI keys rows by global id plus chem id. Ordering and presentation (type column, Location)
  are deliberately minimal for now.
- The round-trip test `InventoryStructureSearchChemistryMVCIT` is tagged `chemistry` and needs a
  running chemistry service. The other backend and frontend tests mock the provider.
- In the e2e tests, Firefox and WebKit are unreliable when setting a structure on the Ketcher canvas.
  The existing Workspace chemistry e2e tests have the same problem, so it is not specific to this
  feature.
