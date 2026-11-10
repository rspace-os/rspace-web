---
status: accepted
---

# Filestore links are indexed in their own table

Filestore links live only in document HTML (an anchor whose `rel` holds a filestore id and a path);
nothing in the schema says which documents link a Filestore item, unlike Gallery items, which have
`FieldAttachment`. The metadata sidecar and the Gallery filestores API need that answer on demand.

## Decision

1. A link index table (`NfsFieldLink`, one row per link per text field) is kept in step with document
   content at the same points that maintain `FieldAttachment` (autosave, cancel, copy, archive import,
   revision restore) and backfilled once by a Liquibase Java update. The alternative, scanning every
   text field's content with a `LIKE` at request time and parsing the hits, needs no schema, but its
   cost grows with the instance and would be paid on every sidecar preview and every API call;
   predictable lookups won.
2. Each row stores the filesystem and the normalised absolute path (filestore base path plus the
   link's relative path, with no leading, trailing or doubled slashes) next to the filestore and the
   relative path. Filestores are per user and point at arbitrary base paths of one filesystem, and a
   filestore's path cannot change after creation, so the absolute path is safe to denormalise and
   makes "who links this S3 key" one indexed equality query across every user's filestore.
3. Rows follow autosaved content, as `FieldAttachment` does: a link counts as soon as it is in the
   editor, and cancelling an edit re-reads the saved content.
4. A link whose filestore id does not exist in this instance is skipped, not failed: archive imports
   keep the exporting instance's filestore ids inside links.
5. The sidecar names only the Linked documents the generating user can read, by Global ID page URL;
   the API additionally reports the unreadable ones as owner-only placeholders, like the Gallery's
   linked-documents list. A sidecar is written into a bucket, so a private document's Global ID must
   not leave RSpace through it.

## Consequences

Content written to fields without passing through the maintained points (direct DAO writes, future
importers) leaves the index stale until the field is next autosaved or the backfill is re-run.
