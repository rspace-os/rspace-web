# Gallery pinned-version links use a path segment, not a versioned Global ID

Status: accepted

A Gallery item needs a shareable link that always shows one past version, the
way the ELN's audit view and Inventory's versioned viewer do. We added the route
`/gallery/item/<itemId>/<version>` and deliberately left
`/globalId/GL<id>v<n>` alone.

That is a departure worth explaining, because every other record type reaches
its pinned version view through a version-suffixed Global ID:

| Global ID  | Resolves to                                                |
|------------|------------------------------------------------------------|
| `SD123v2`  | `/workspace/editor/structuredDocument/audit/view?globalId=` |
| `SA42v2`   | `/inventory/sample/42?version=2`                           |
| `GL42v2`   | `/Streamfile/42?version=2` — **raw bytes, not a view**      |

`EcatMediaFile.getOidWithVersion()` already produces `GL42v2`, and
`GlobalLookupController` already resolves it, so the identifier and its
resolution both predate this work. Repointing it at the new view was considered
and rejected: it would change what an existing, published link does, and the
Gallery's `/globalId` behaviour is relied on as a download URL.

## Considered options

* **Repoint `/globalId/GL<id>v<n>` at the new view**, as `SD` and the inventory
  prefixes do: rejected. It silently changes the meaning of links already in the
  wild from "download this version" to "open this version", and it would have
  pulled `supportsVersionPin` and the inventory link-pinning path into scope.
* **Use only the Global ID and add no new route**: rejected. The view still needs
  an internal URL to land on, so a route gets invented either way, and every
  shared link would carry a redirect hop.

## Consequences

* **The Global ID shown on a pinned view does not lead to that view.** The
  InfoPanel displays `GL42v2` while `/globalId/GL42v2` downloads the file. This
  is deliberate, not an oversight: the visible identifier names the version on
  screen, and the shareable link for the view itself is the address-bar URL
  `/gallery/item/42/2`. A future reader may mistake this for a bug; it is the
  accepted cost of not redefining an existing link.
* To keep that display choice from leaking into behaviour, the versioned form is
  rendered **only** in the InfoPanel's Global ID row. The historical-file
  decorator delegates `globalId` unchanged, so the ELN linked-documents and
  inventory referencing/attaching lookups keep receiving `GL42`. Passing `GL42v2`
  would in fact still work, because those lookups build a `GlobalIdentifier` and
  use only `getDbId()`, but that is the version being silently discarded rather
  than handled, and nothing should depend on it.
* `supportsVersionPin("GL…")` stays `false`, so an Inventory Link field still
  cannot pin a Gallery item to a version, and `VersionLockDialog.fetchVersions`
  keeps returning `[]` for `GL` targets. Both remain open, and both are now
  smaller jobs than before, since a Gallery version list and a pinned view exist
  to build on.
* References shown beside a pinned version are the **item's**, not the version's.
  No schema records the version a link or attachment was made against
  (`inventoryFileDao.findByMediaFileId` takes an id alone), so a version-filtered
  list is unrepresentable, not merely unbuilt. The sections are worded to say so.
  Legacy took the other route and hid them entirely on a revision view
  (`recordInfoPanel.js`); we show them because the information was asked for.
* `/gallery/item/<id>/<version>` needs its own `urlrewrite.xml` rule. The
  existing one is anchored as `^/gallery/item/([0-9]+)$`, so without a second
  rule a pasted pinned link never reaches the SPA shell at all.
* A pinned link whose version equals the current one redirects to the live,
  editable view. The content shown is the same either way; only the locked
  framing differs, so the same link presents differently once a newer version
  lands.
