# Gallery version history gets its own endpoint

Status: accepted

Gallery items needed a browsable version history (RSDEV-1250). Two endpoints
that serve version lists already existed: the ELN workspace's
`GET /workspace/revisionHistory/ajax/{recordId}/versions` and the Inventory
API's `GET /api/inventory/v1/{type}/{id}/revisions`. We added a third, on
`GalleryController`, rather than extending either.

Extending the ELN endpoint was the tempting option, and not obviously wrong:
`RevisionHistoryController` already deals with media records in its
deleted-item restore path, and `AuditManager.getRevisionsForEntity` is generic
over audited entities, so the media branch would have been small. It would also
have unblocked version-pinning for `GL` link targets, which
`VersionLockDialog.fetchVersions` currently leaves as a silent `return []`.

We chose a dedicated Gallery endpoint anyway, because that endpoint's remaining
surface is document-specific in ways the media branch would not share: its
sibling methods are typed to `StructuredDocument`, they page and search by
document field names, and they exist to serve a JSP view. Widening a
document-shaped controller method to cover a second record type would have left
both callers reading conditionals about the other one. Gallery concerns belong
on the Gallery controller.

## Considered options

* **Extend `/workspace/revisionHistory/ajax/{recordId}/versions`** to branch on
  record type: rejected. Cheaper in lines, but couples Gallery behaviour to a
  document-and-JSP-shaped controller, and the shared response DTO then has to
  mean different things per record type.
* **Reuse the Inventory API endpoint shape by generalising it** to non-inventory
  records: rejected. Its path and DTOs are inventory-specific
  (`ApiInventoryRecordInfo`), and Gallery items are ELN records, not inventory
  records.

## Consequences

* A third version-list response shape exists. We limited the divergence by
  giving the Gallery endpoint the Inventory response shape verbatim
  (`{revisions: [{revisionId, revisionType, record: {...}}], revisionsCount}`)
  plus `size`, so the shared `groupByVersion` helper consumes all of them
  unchanged. A future consolidation should start from that helper, not from the
  endpoints.
* `GL` version-pinning in `VersionLockDialog` stays unimplemented. It is now a
  smaller job than before, since a Gallery version list exists to call, but it
  did not come for free as it would have under the rejected option.
* The endpoint requires an authenticated user and an explicit READ permission
  check. `GalleryController` is mapped at both `/gallery` and
  `/public/publicView/gallery`, so a new method is reachable anonymously by
  default; version history is not offered in public view. Note that the
  neighbouring `/ajax/getLinkedDocuments/{mediaId}` performs no permission check
  at all, and is not a pattern to copy.
