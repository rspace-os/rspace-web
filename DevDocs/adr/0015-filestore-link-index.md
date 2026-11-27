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
   link's relative path, with no leading or trailing slashes) next to the filestore and the relative
   path. Inner slashes are kept and paths compare exactly, case and trailing spaces included,
   because S3 keys are flat and `data/a//b`, `Data/a` and `data/a ` name different objects; the columns'
   binary collation ignores trailing spaces, so every lookup by path compares the matched path again
   after its indexed query. A filestore's path cannot change after creation, so the absolute path is
   safe to denormalise and makes "who links this file" one indexed query. A lookup by filesystem and
   paths asserts the caller's read access to the filesystem itself, whichever entry point calls it.
   On a filesystem reached with one shared login (S3) a lookup matches across every user's
   filestore. On one with per-user logins (any authentication but none) the same path can name a
   different file for each login, through a chroot or a filestore path relative to the login's home,
   and RSpace cannot tell when two logins see the same file, so a lookup counts only links made
   through the caller's own filestores, removed ones included. There a link and a looked-up path can
   also share their stored form yet name different files by their leading slashes alone: SFTP reads a
   path without the slash under the login's home, iRODS prefixes the account's home to any path that
   does not start with it, and SMBJ glues a folder that the server URL names after the share to a
   path without the slash, and reads a first folder named like the share as the share after none or
   one slash but not after two. So the lookup asks the client which spellings of the stored form (no
   leading slash, one, or more) it opens as the same file as the looked-up path, and matches links
   only in those, reading a link's spelling from its filestore's base and its own path. SMBJ and iRODS
   answer by running their own path resolution on each spelling; jcifs opens all three alike unless
   it hands downloads to SMBJ; any other client keeps a path with a leading slash apart from one
   without. Matching at lookup time leaves the stored form and the clients' path resolution as they
   are: recomputing the stored form would need a data migration, and changing the resolution would
   change which file existing links open. Where the slash names the same file (a login chrooted to
   its home) the lookup fails closed: a user's own links through a relative and an absolute base no
   longer meet, nor do document links that older Gallery versions wrote without the slash through a
   filestore with no base.
3. Rows follow autosaved content, as `FieldAttachment` does: a link counts as soon as it is in the
   editor, and cancelling an edit re-reads the saved content.
4. A link whose filestore id does not exist in this instance is skipped, not failed: archive imports
   keep the exporting instance's filestore ids inside links.
5. The sidecar names only the Linked documents the generating user can read, by Global ID page URL;
   the API additionally reports the unreadable ones as owner-only placeholders, like the Gallery's
   linked-documents list. A sidecar is written into a bucket, so a private document's Global ID must
   not leave RSpace through it.
   *Amended 2026-11-20:* "can read" is the generating user's whole read right, including what a PI
   or admin may read in their group or community, shared or not, so a sidecar they save can name a
   member's unshared document. That is intended: the sidecar carries only the Global ID page URL,
   never the name, and opening it goes through the usual document permission check.

## Consequences

Content written to fields without passing through the maintained points (direct DAO writes, future
importers) leaves the index stale until the field is next autosaved or the backfill is re-run.
