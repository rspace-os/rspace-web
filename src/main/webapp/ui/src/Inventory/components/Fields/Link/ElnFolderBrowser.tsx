import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Typography from "@mui/material/Typography";
import { treeItemClasses } from "@mui/x-tree-view/TreeItem";
import React from "react";
// Reuse the Gallery's own extension groupings so MEDIA files get the Gallery's per-type icons.
import EXT_BY_TYPE from "@/eln/gallery/fileExtensionsByType.json";
import { justFilenameExtension } from "@/util/files";
import { Tree, TreeItem } from "../../../../components/Tree";
import useFolders, { type FolderTreeNode } from "../../../../hooks/api/useFolders";

// The /api/v1/folders/tree endpoint tags each row with an ApiRecordType: FOLDER,
// NOTEBOOK, DOCUMENT, MEDIA, SNIPPET. We request documents, notebooks and folders;
// the endpoint bundles Gallery/MEDIA files in with documents (it only excludes
// MEDIA_FILE when "document" is absent) and lists the Gallery root, so media files
// are reachable in this tree too.
const TYPES_TO_INCLUDE: Set<"document" | "notebook" | "folder"> = new Set(["document", "notebook", "folder"]);
// Documents, notebooks and Gallery files are valid link targets; folders are navigate-only.
const PICKABLE_TYPES = new Set(["DOCUMENT", "NOTEBOOK", "MEDIA"]);
// Folders and notebooks have children that can be revealed; documents are leaves.
const EXPANDABLE_TYPES = new Set(["FOLDER", "NOTEBOOK"]);

export type ElnTreeSelection = {
  globalId: string;
  name: string;
  type: string;
};

function isPickable(node: FolderTreeNode): boolean {
  return PICKABLE_TYPES.has(node.type);
}

function isExpandable(node: FolderTreeNode): boolean {
  return EXPANDABLE_TYPES.has(node.type);
}

/*
 * The picker reproduces the ELN/Workspace (Fancytree) tree look. The ELN's CSS
 * (styles/tags/fileTreeBrowser.css) targets Fancytree's DOM and cannot bind to this MUI tree,
 * so we reuse its served icon assets by URL and copy the few visual values it can't share.
 * See DevDocs/adr/0001-eln-link-picker-tree-styling.md. Nothing outside this picker is changed.
 */
const ELN_ICON_BASE = "/images/icons";

/*
 * Gallery (MEDIA) files reuse the Gallery's own per-type icons: the same extension groupings
 * (fileExtensionsByType.json) and extension parser (justFilenameExtension) the Gallery uses,
 * mapped to its category SVG icons (image/document/pdf/...). Only this small category->icon map
 * is copied; the extension data and parser are reused, and useGalleryListing is not modified.
 */
const mapToSvgImageIcon = (extensions: ReadonlyArray<string>, filename: string): ReadonlyArray<[string, string]> =>
  extensions.map((ext) => [ext, `${ELN_ICON_BASE}/${filename}.svg`]);

const galleryFileIconMap = new Map<string, string>([
  ...mapToSvgImageIcon(EXT_BY_TYPE.CHEMISTRY, "chemistry"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.DNA, "dna"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.AUDIO, "audio"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.VIDEO, "video"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.SPREADSHEET, "sheet"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.IMAGES, "image"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.DOCUMENTS, "document"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.PRESENTATION, "presentation"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.HTML, "html"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.CSV, "csv"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.PDF, "pdf"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.XML, "xml"),
  ...mapToSvgImageIcon(EXT_BY_TYPE.ZIP, "zip"),
]);

/** Gallery file icon, matching the Gallery: extension -> category SVG, else the unknown fallback. */
function galleryIconUrl(name: string): string {
  return galleryFileIconMap.get(justFilenameExtension(name)) ?? `${ELN_ICON_BASE}/unknown.svg`;
}

function iconUrlForNode(node: FolderTreeNode): string {
  switch (node.type) {
    case "FOLDER":
      return `${ELN_ICON_BASE}/folder.png`;
    case "NOTEBOOK":
      return `${ELN_ICON_BASE}/notebook.png`;
    case "MEDIA":
      // Gallery files use the Gallery's own per-type icons (image/pdf/document/...)
      return galleryIconUrl(node.name);
    default:
      // DOCUMENT and any other leaf type use the ELN's generic document icon
      return `${ELN_ICON_BASE}/unknownDocument.png`;
  }
}

/** A node label matching the ELN tree: the node's type icon followed by its name only. */
function NodeLabel({ node }: { node: FolderTreeNode }): React.ReactElement {
  return (
    <Box sx={{ display: "flex", alignItems: "center", gap: 0.75, minHeight: "25px" }}>
      <Box
        component="img"
        src={iconUrlForNode(node)}
        alt=""
        aria-hidden
        sx={{ width: 22, height: 22, objectFit: "contain", flexShrink: 0 }}
      />
      <Typography variant="body2" noWrap>
        {node.name}
      </Typography>
    </Box>
  );
}

/** Folder/notebook expander arrows, reusing the ELN's served arrow assets. */
function ExpandArrow(): React.ReactElement {
  return (
    <Box
      component="img"
      src={`${ELN_ICON_BASE}/RightArrow25.png`}
      alt=""
      aria-hidden
      sx={{ width: 16, height: 16, objectFit: "contain" }}
    />
  );
}
function CollapseArrow(): React.ReactElement {
  return (
    <Box
      component="img"
      src={`${ELN_ICON_BASE}/DownArrow25.png`}
      alt=""
      aria-hidden
      sx={{ width: 16, height: 16, objectFit: "contain" }}
    />
  );
}

/**
 * A single tree node. Folders and notebooks are expandable and lazily load their
 * own contents (mirroring FolderTree's load strategy); documents render as leaves.
 */
function TreeNodeContent({ node }: { node: FolderTreeNode }): React.ReactNode {
  const { getFolderTree } = useFolders();
  const [children, setChildren] = React.useState<ReadonlyArray<FolderTreeNode>>([]);
  const [totalHits, setTotalHits] = React.useState(0);
  const [currentPage, setCurrentPage] = React.useState(0);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState(false);

  const loadChildren = React.useCallback(
    async (pageNumber: number, append: boolean) => {
      setLoading(true);
      setError(false);
      try {
        const response = await getFolderTree({
          id: node.id,
          typesToInclude: TYPES_TO_INCLUDE,
          pageNumber,
        });
        setChildren((prev) => (append ? [...prev, ...response.records] : response.records));
        setTotalHits(response.totalHits);
        setCurrentPage(pageNumber);
      } catch {
        setError(true);
      } finally {
        setLoading(false);
      }
    },
    [node.id, getFolderTree],
  );

  React.useEffect(() => {
    if (isExpandable(node)) void loadChildren(0, false);
  }, [loadChildren, node]);

  if (!isExpandable(node)) {
    return <TreeItem item={node} label={<NodeLabel node={node} />} role="treeitem" />;
  }

  const hasMorePages = children.length < totalHits;

  return (
    <TreeItem item={node} label={<NodeLabel node={node} />} role="treeitem">
      {children.map((child) => (
        <TreeNodeContent key={child.id} node={child} />
      ))}
      {loading && (
        <Box sx={{ p: 1 }}>
          <CircularProgress size={16} />
        </Box>
      )}
      {error && (
        <Box sx={{ p: 1 }}>
          <Alert severity="error">Failed to load contents</Alert>
        </Box>
      )}
      {hasMorePages && !loading && (
        <Box sx={{ p: 1 }}>
          <Button size="small" onClick={() => void loadChildren(currentPage + 1, true)}>
            Load more
          </Button>
        </Box>
      )}
    </TreeItem>
  );
}

/**
 * A folder-tree browser for picking an ELN link target. Folders and notebooks can
 * be expanded to navigate; documents, notebooks and Gallery files can be selected as
 * the target. Clicking a node both selects it and (for folders and notebooks)
 * expands it, so a notebook can be opened to reach its entries and still be chosen
 * itself. Selection alone does not confirm the pick: the browser only reports the
 * current selection ({@code null} for navigate-only folders) and the host dialog
 * confirms it with an explicit Choose action. Reuses the workspace folder-tree data
 * layer ({@link useFolders}), which also surfaces the Gallery, so gallery files can
 * be picked here as well as via search.
 */
export default function ElnFolderBrowser({
  onSelectionChange,
}: {
  onSelectionChange: (selection: ElnTreeSelection | null) => void;
}): React.ReactElement {
  const { getFolderTree } = useFolders();
  const [roots, setRoots] = React.useState<ReadonlyArray<FolderTreeNode>>([]);
  const [expanded, setExpanded] = React.useState<Array<FolderTreeNode>>([]);
  const [selected, setSelected] = React.useState<FolderTreeNode | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState(false);

  const loadRoots = React.useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const response = await getFolderTree({ typesToInclude: TYPES_TO_INCLUDE });
      setRoots(response.records);
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [getFolderTree]);

  React.useEffect(() => {
    void loadRoots();
  }, [loadRoots]);

  return (
    <Box>
      {error && (
        <Alert
          severity="error"
          sx={{ mb: 1 }}
          action={
            <Button size="small" onClick={() => void loadRoots()}>
              Retry
            </Button>
          }
        >
          Failed to load your workspace
        </Alert>
      )}
      {loading && (
        <Box sx={{ display: "flex", justifyContent: "center", my: 2 }}>
          <CircularProgress size={24} />
        </Box>
      )}
      <Tree<FolderTreeNode, string>
        aria-label="Browse the ELN workspace for a link target"
        getId={(node) => node.id.toString()}
        itemChildrenIndentation={20}
        slots={{ expandIcon: ExpandArrow, collapseIcon: CollapseArrow }}
        sx={{
          // Compact rows like the ELN tree (fileTreeBrowser.css cannot bind to this
          // MUI DOM; see ADR 0001). The selected row uses the solid theme primary
          // colour so the current selection is unmistakable before clicking Choose.
          // x-tree-view 9 marks state with data attributes on the content row (the
          // Mui-selected class no longer exists), so the selectors target those.
          [`& .${treeItemClasses.content}`]: {
            minHeight: "25px",
            borderRadius: 0,
            py: 0,
          },
          [`& .${treeItemClasses.content}[data-selected], & .${treeItemClasses.content}[data-selected][data-focused], & .${treeItemClasses.content}[data-selected]:hover`]:
            {
              backgroundColor: "primary.main",
              color: "primary.contrastText",
            },
        }}
        expandedItems={expanded}
        onExpandedItemsChange={(_event, items) => setExpanded(items)}
        selectedItems={selected}
        onSelectedItemsChange={(_event, node) => {
          setSelected(node);
          onSelectionChange(
            node && isPickable(node) ? { globalId: node.globalId, name: node.name, type: node.type } : null,
          );
        }}
      >
        {roots.map((node) => (
          <TreeNodeContent key={node.id} node={node} />
        ))}
      </Tree>
      {!loading && !error && roots.length === 0 && (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          Nothing to browse here.
        </Typography>
      )}
    </Box>
  );
}
