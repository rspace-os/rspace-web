# Reproduce the ELN/Workspace tree look in the Inventory link "Browse ELN" picker

Status: accepted

## Context

The Inventory structured-Link field's "Browse ELN" dialog (`ElnRecordPicker` →
`ElnFolderBrowser`) lets a user pick an ELN document, notebook, folder or Gallery
file as a link target. It renders a MUI `SimpleTreeView` (via the generic
`src/components/Tree.tsx`) and looked nothing like the rest of RSpace.

The requirement was that the picker's tree should look like the **ELN/Workspace
tree**, and that the same look be applied to Gallery items shown in it; explicitly
*not* like the Inventory picker.

The ELN/Workspace tree is rendered by **legacy jQuery Fancytree**
(`workspace.jsp` loads `jquery.fancytree/dist/skin-bootstrap/...`), styled by
Fancytree's skin-bootstrap CSS plus RSpace's `styles/tags/fileTreeBrowser.css`.
That CSS targets Fancytree's DOM (`.fancytree-node`, `.fancytree-title`,
`.fancytree-expander`, `.fancytree-icon`), which is entirely different from the
MUI tree's DOM (`.MuiTreeItem-content`, `.MuiTreeItem-label`). So the ELN CSS
**cannot be reused** (its selectors will not bind to the MUI markup).

## Decision

Reproduce the ELN/Workspace tree look in the picker's MUI tree, scoped to the
Link-picker files only:

- **Reuse the ELN icon assets by URL** (no copies) for ELN node types: folder →
  `/images/icons/folder.png`, notebook → `/images/icons/notebook.png`, document →
  `/images/icons/unknownDocument.png`, and folder expanders → `/images/icons/RightArrow25.png`
  (collapsed) / `DownArrow25.png` (expanded).
- **Gallery (MEDIA) files use the Gallery's own per-type icons**, not the ELN set, so they look
  like the Gallery (images, PDFs, documents, spreadsheets, etc.). The picker **reuses** the
  Gallery's extension groupings (`src/eln/gallery/fileExtensionsByType.json`) and the shared
  `justFilenameExtension` parser (`src/util/files.ts`), and **copies only** the small
  category→SVG map (`/images/icons/{image,document,pdf,sheet,presentation,audio,video,chemistry,dna,html,csv,xml,zip}.svg`),
  falling back to `/images/icons/unknown.svg`. `useGalleryListing` is not modified. DMP gallery
  files fall back to `unknown.svg` (the picker's `folders/tree` node carries no gallery-section
  info, which the Gallery's `dmp.svg` special-case needs).
- **Copy the few visual values** that cannot be reused (DOM mismatch): selected-row
  background `rgba(193, 193, 193, 0.5)`, ~25px rows, ~20px per-level indent.
- Node labels show the **name only** (the picked target's global ID still surfaces in
  the chip after selection), matching the ELN tree.
- Styling is **picker-local** (applied at `ElnFolderBrowser` via `sx`/slots on the
  forwarded MUI tree); the generic `Tree` is not modified.

**Hard constraint honoured:** zero changes to existing ELN/Gallery styling or code. The Gallery's
`fileExtensionsByType.json` and the shared `justFilenameExtension` are *imported* (read-only reuse),
not modified. No edits to `fileTreeBrowser.css`, Fancytree assets, the MUI theme, `useGalleryListing`
or any other `src/eln/gallery/*` file, or `src/components/Tree.tsx` (also used by `FolderTree`).
Verified by the `git diff` scope.

## Considered options

- **A — reproduce the ELN/Fancytree look in MUI (chosen).** Only way to echo the
  *workspace* tree the user asked for, given the CSS cannot be reused.
- **B — reuse the React Gallery tree styling** (`eln/gallery/TreeView` `StyledTreeItem`:
  `callToAction` selection). Rejected: it matches the Gallery surface, which is a
  distinct look from the Workspace ELN tree the requirement named.
- **C — reuse the Inventory picker styling** (`LinkTargetBrowser`/`InventoryPicker`).
  Rejected: that is the look the requirement explicitly excluded.

## Consequences

- A small number of visual values (selection colour, row height, indent) are duplicated
  from `fileTreeBrowser.css` because the legacy CSS cannot bind to MUI's DOM. The icon
  assets, by contrast, are genuinely reused by URL and stay in sync if those assets change.
- The picker deliberately mimics a legacy jQuery component's look. If RSpace later ships a
  React Workspace tree, the picker should adopt it rather than keep mimicking Fancytree.

## Terminology

- **ELN / Workspace tree** — the legacy Fancytree document/notebook/folder tree in the
  Workspace, styled by `fileTreeBrowser.css` + Fancytree skin-bootstrap. The look this
  picker reproduces.
- **Gallery tree** — the React `eln/gallery/TreeView` (MUI, `callToAction` selection). A
  distinct look; not the target here.
- **Inventory picker** — `LinkTargetBrowser` wrapping `InventoryPicker`; the "Browse
  Inventory" target chooser. Explicitly *not* the look to copy.
- **ELN link picker** — this feature's "Browse ELN" dialog: `ElnRecordPicker` +
  `ElnFolderBrowser`.
