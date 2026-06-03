import React from "react";
import Box from "@mui/material/Box";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Typography from "@mui/material/Typography";
import useFolders, {
  type FolderTreeNode,
} from "../../../../hooks/api/useFolders";
import { Tree, TreeItem } from "../../../../components/Tree";

// The /api/v1/folders/tree endpoint tags each row with an ApiRecordType: FOLDER,
// NOTEBOOK, DOCUMENT, MEDIA, SNIPPET. We browse documents, notebooks and folders.
const TYPES_TO_INCLUDE: Set<"document" | "notebook" | "folder"> = new Set([
  "document",
  "notebook",
  "folder",
]);
// Documents and notebooks are valid link targets; folders are navigate-only.
const PICKABLE_TYPES = new Set(["DOCUMENT", "NOTEBOOK"]);
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

/**
 * A single tree node. Folders and notebooks are expandable and lazily load their
 * own contents (mirroring FolderTree's load strategy); documents render as leaves.
 */
function TreeNodeContent({
  node,
}: {
  node: FolderTreeNode;
}): React.ReactNode {
  const { getFolderTree } = useFolders();
  const [children, setChildren] = React.useState<ReadonlyArray<FolderTreeNode>>(
    [],
  );
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
        setChildren((prev) =>
          append ? [...prev, ...response.records] : response.records,
        );
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

  const label = isPickable(node) ? `${node.name} (${node.globalId})` : node.name;

  if (!isExpandable(node)) {
    return <TreeItem item={node} label={label} role="treeitem" />;
  }

  const hasMorePages = children.length < totalHits;

  return (
    <TreeItem item={node} label={label} role="treeitem">
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
 * be expanded to navigate; documents and notebooks can be selected as the target.
 * Clicking a node's expand chevron only navigates, while clicking its label selects
 * it, so a notebook can be both opened and chosen. Reuses the workspace folder-tree
 * data layer ({@link useFolders}); gallery files are not in this tree and are reached
 * through search instead.
 */
export default function ElnFolderBrowser({
  onPick,
}: {
  onPick: (selection: ElnTreeSelection) => void;
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
        expandedItems={expanded}
        onExpandedItemsChange={(_event, items) => setExpanded(items)}
        selectedItems={selected}
        onSelectedItemsChange={(_event, node) => {
          setSelected(node);
          if (node && isPickable(node)) {
            onPick({
              globalId: node.globalId,
              name: node.name,
              type: node.type,
            });
          }
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
