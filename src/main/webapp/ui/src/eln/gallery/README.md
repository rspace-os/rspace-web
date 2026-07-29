# Gallery

## Overview

The Gallery is a sophisticated file management interface for RSpace that provides
a unified view of various file types stored both locally and on external
filesystems. It serves as the primary mechanism for browsing, organizing, and
managing files across different storage backends, with specialized views for
different file categories and comprehensive preview capabilities.

The Gallery powers file selection throughout RSpace, appearing as both a
standalone application and as picker dialogs embedded in Inventory as a means of attachment files to samples as well as in the ELN for referencing files in documents.

### Use Cases

The Gallery appears in several key contexts:

- **Standalone Gallery Application**: Full-featured file management interface
- **Inventory Attachments**: Selecting files to attach to inventory records
- **Stoichiometry**: For adding reagents to stoichiometry tables

Note that the old Gallery is still used (as of October 2025) in the ELN for:

- **Document Editor**: File picker for inserting images, documents, and other media

## Design

The Gallery handles two fundamentally different storage paradigms:

**Local Files**: Stored in RSpace's internal file system
- Have numeric IDs for reliable referencing
- Support full CRUD operations
- Integrated with RSpace's permission system
- Support versioning and audit trails

**Network Files (Filestores)**: External filesystem integration
- May only have path-based identification
- Operations depend on external system capabilities
- Require separate authentication flows
- Limited metadata and preview support

The Gallery organizes files into type-based sections that provide specialised
interfaces for different kinds of content. Local files are organised according
to their type, while NetworkFiles are organised by their filestore.

- **Images/Audio/Video**: Media files with preview capabilities
- **Documents**: Text documents, spreadsheets, presentations with Office integration
- **Chemistry**: Molecular structure files with Ketcher integration
- **DMPs**: Data Management Plans
- **NetworkFiles (Filestores)**: External filesystem integration (iRODS, etc.)
- **Snippets**: Text snippets for re-use across ELN documents
- **Exports**: Generated PDF documents and reports
- **Miscellaneous**: Other file types

## Core Components

### Main Architecture Components

- **`index.tsx`** - Main application shell with routing, theme, and layout
- **`Sidebar.tsx`** - Section navigation
- **`MainPanel.tsx`** - File grid view with drag & drop, responsive layout
- **`InfoPanel.tsx`** - File details, preview, and metadata display

### Hook-Based Data Management

- **`useGalleryListing.tsx`** - File fetching, caching, pagination, and folder navigation
- **`useGallerySelection.tsx`** - Multi-select state with keyboard/mouse interactions
- **`useGalleryActions.ts`** - File operations with error handling and progress tracking

### External Service Integration

- **`useCollabora.ts`** - Collabora Online document editing integration
- **`useOfficeOnline.ts`** - Microsoft Office Online editing capabilities
- **`CallableAsposePreview.tsx`** - Document preview using Aspose services
- **`CallableSnapGenePreview.tsx`** - DNA sequence file preview integration

## Routing

The Gallery uses a routing system that allows users to bookmark specific
folders, files, and sections. They cannot, however, share those links with other
users as each users has their open separate Gallery.

```
/gallery/{section}                    # Section root
/gallery/{section}/folder/{path}      # Folder navigation
/gallery/{section}/file/{id}          # Select file in its folder
/gallery/item/{id}                    # Select item in its folder
/gallery/item/{id}/{version}          # Same, pinned to a past version (read-only)
```

Every route also needs a matching rule in
[urlrewrite.xml](../../../WEB-INF/urlrewrite.xml), which forwards it to the SPA
shell before Spring MVC sees it. The rules are anchored regexes, so a new URL
shape will 404 until one is added for it.

## Dynamic Primary Action

The user interface dynamically provides a primary action based on the type of
the file and the available integration services. For example, a Word document
may show "Edit" if Office Online is available, or "Preview" if not (which will
use Aspose to generate a PDF). Many of these integrations are enabled as React
hooks and contexts allowing any part of the application to trigger the preview.
If this functionality is useful outside of the Gallery, then these hooks can be
extracted to a more global location. This is logic is in [primaryActionHooks.ts](./primaryActionHooks.ts), and in addition to the external services above includes

- **`CallableImagePreview.tsx`** - Image preview in a modal dialog
- **`CallablePdfPreview.tsx`** - PDF preview in a modal dialog

## Version History

The Actions menu's "Version history" opens
[VersionHistoryDialog.tsx](./components/VersionHistoryDialog.tsx), which lists a
Gallery item's versions newest first. The list comes from
`GET /gallery/ajax/versionHistory/{mediaFileId}`, which requires an authenticated
session and read permission on the item.

Three things about it are easy to get wrong:

- **An audit revision is not a version.** Several revisions can share one
  version, because not every recorded change bumps the counter. The dialog shows
  one row per version, using the newest revision of each. That collapsing rule
  is shared with the Inventory version history via
  [groupByVersion](../../util/versionHistory.ts) rather than reimplemented, and
  each row keeps its revision id because some endpoints key on it.
- **Only locally stored items have a history.** Files held on an external
  filestore are only referenced by RSpace, so `canViewVersionHistory` refuses
  them, as it does folders.
- **A row navigates rather than previews.** Each row opens that version's pinned
  view (below), so what the user lands on is also a URL they can copy. The live
  version links to `/gallery/item/{id}` with no version segment, saving a
  redirect.
- **A filename belongs to a version, not to the item.** Uploading a new version
  can replace the file with one of a different name, so the Name column shows
  each version's own name and the live item's name says nothing about what an
  earlier version was called.

The history is read-only; "Upload new version" is the forward-only equivalent of
restoring. See
[ADR 0003](../../../../../../DevDocs/adr/0003-gallery-version-history-endpoint.md)
for why this has its own endpoint rather than extending the ELN document one.

## Pinned version view

`/gallery/item/{id}/{version}` shows one past version of an item, read-only, with
the item selected. The version is resolved in `GalleryFileInFolder`
([index.tsx](./index.tsx)) from the same version-history endpoint the dialog uses,
then applied by wrapping the live file in
[HistoricalGalleryFile](./historicalGalleryFile.ts).

Seven things about it are easy to get wrong:

- **Almost nothing is safe to delegate to the live file.** `name`, `extension`,
  `description`, `size`, `modificationDate`, `thumbnailUrl` and `downloadHref` all
  differ per version, and delegating any of them puts the live item's data beside
  an older version's bytes. The name and description come from the audit row, via
  `GalleryVersionHistory.Item`; both live on the audited `EditInfo` embeddable, so
  Envers records them per revision the same way it records the version counter.
  A version with no recorded description shows an empty one, never the live one.
  In particular no thumbnail endpoint is version-aware:
  `/gallery/getThumbnail` takes a cache-buster, not a version, and the document
  and chemistry thumbnails are keyed on the live record. An image therefore
  thumbnails from `/Streamfile/{id}?version=N`, and anything else falls back to
  its stock type icon, because showing no content beats showing the wrong
  content.
- **The decoration goes in the listing, not the selection.** Everything
  downstream (grid tile, selection, InfoPanel, Actions menu) reads the listing, so
  decorating there is what keeps them consistent. `WholePage` takes a
  `decorateFile` prop for this, and it must be referentially stable because the
  listing is memoised on it.
- **The two inline editors are gated separately from the menu.** The InfoPanel's
  name field is gated on `canRename`; the description field has no predicate, so
  it is read-only whenever the object has no `setDescription`, which is the same
  test `changeDescription` makes before saving. A past version has no setter.
- **The refusals come from the predicates.** The decorator returns `Result.Error`
  from every `can*` except `canViewVersionHistory`, so the Actions menu disables
  Rename, Delete, Move, Duplicate, Export and Upload-new-version without a single
  new conditional. Download survives because `downloadAllowed` checks
  `isFolder`/`isSnippet` directly, and `downloadHref` resolves to
  `/Streamfile/{id}?version=N`. Edit needed `canBeEdited` adding to the interface:
  it was decided solely by which editor applies to the file's type, so a past
  version stayed editable, and Collabora and Office Online would have edited the
  live bytes while the image editor derived a new file from a version being viewed.
- **`globalId` stays unversioned.** The InfoPanel renders `GL42v2` for display,
  but the object keeps `GL42`, because the ELN linked-documents and Inventory
  referencing lookups read it and neither records the version a reference was made
  against. Those lists are the item's, not the version's, and a single notice above
  both of them says so, at body size rather than as small print, because it
  corrects what the lists otherwise imply. It sits above the pair rather than
  inside each so it plainly covers both.
- **Two places say the state, each with one job.** The item's own tile badge
  carries the version and marks it historical with the same clock icon a
  version-pinned Inventory link uses, shown even for version 1, which a live item
  never badges. The icon is decorative; the badge's own label carries the meaning
  for screen readers. The InfoPanel says which version is on screen, that it is
  locked, and links back to the live item. There is deliberately no page-level
  banner. The link has a real `href` and handles its own click, so it can be copied
  or opened in a new tab without a full page load. Following it does not leave a
  stale selection behind: `Selection.append` keys on the item id, which the
  decorator delegates, so the live object replaces the pinned one.
- **A bad version is reported, never worked around.** A non-numeric version, or
  one the item does not have, renders an error. A version equal to the live one
  redirects to `/gallery/item/{id}`, because a locked page would misrepresent an
  editable item.

`/gallery/item/{id}/{version}` needs its own `urlrewrite.xml` rule; the existing
one is anchored to a single numeric segment, so without it a pasted link never
reaches the SPA shell. See
[ADR 0004](../../../../../../DevDocs/adr/0004-gallery-pinned-version-links.md) for
why this is a route rather than a versioned Global ID.
