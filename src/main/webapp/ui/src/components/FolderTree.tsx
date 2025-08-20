import React from "react";
import Button from "@mui/material/Button";
import Box from "@mui/material/Box";
import CircularProgress from "@mui/material/CircularProgress";
import Alert from "@mui/material/Alert";
import useFolders, {
  folderDetailsAsTreeNode,
  type FolderTreeNode,
} from "../hooks/api/useFolders";
import { doNotAwait } from "../util/Util";
import { Tree, TreeItem } from "./Tree";

const TreeItemContent = ({
  folder,
}: {
  folder: FolderTreeNode;
}): React.ReactNode => {
  const { getFolderTree } = useFolders();
  const [folders, setFolders] = React.useState<ReadonlyArray<FolderTreeNode>>(
    [],
  );
  const [currentPage, setCurrentPage] = React.useState(0);
  const [totalHits, setTotalHits] = React.useState(0);
  const [isLoading, setIsLoading] = React.useState(false);
  const [error, setError] = React.useState<boolean>(false);

  const loadFolders = React.useCallback(
    async (pageNumber: number, append: boolean = false) => {
      setIsLoading(true);
      setError(false);
      try {
        const response = await getFolderTree({
          id: folder.id,
          typesToInclude: "folder",
          pageNumber,
        });

        if (append) {
          setFolders((prev) => [...prev, ...response.records]);
        } else {
          setFolders(response.records);
        }

        setTotalHits(response.totalHits);
        setCurrentPage(pageNumber);
      } catch (err) {
        setError(true);
      } finally {
        setIsLoading(false);
      }
    },
    [folder.id, getFolderTree],
  );

  React.useEffect(() => {
    void loadFolders(0);
  }, [loadFolders]);

  const hasMorePages = folders.length < totalHits;

  return (
    <TreeItem item={folder} label={folder.name} role="treeitem">
      {folders.map((folder) => (
        <TreeItemContent key={folder.id} folder={folder} />
      ))}
      {error && (
        <Box sx={{ p: 1 }}>
          <Alert
            severity="error"
            action={
              <Button
                size="small"
                onClick={doNotAwait(() => loadFolders(currentPage))}
                disabled={isLoading}
              >
                Retry
              </Button>
            }
          >
            Failed to load subfolders
          </Alert>
        </Box>
      )}
      {hasMorePages && (
        <Box sx={{ p: 1 }}>
          <Button
            size="small"
            onClick={doNotAwait(() => loadFolders(currentPage + 1, true))}
            disabled={isLoading}
            startIcon={isLoading ? <CircularProgress size={16} /> : null}
          >
            {isLoading ? "Loading..." : "Load More"}
          </Button>
        </Box>
      )}
    </TreeItem>
  );
};

export default function FolderTree({
  rootFolderId,
  onFolderSelect,
}: {
  rootFolderId?: number;
  onFolderSelect?: (folder: FolderTreeNode | null) => void;
}): React.ReactNode {
  const { getFolderTree, getFolder } = useFolders();
  const [rootFolders, setRootFolders] = React.useState<
    ReadonlyArray<FolderTreeNode>
  >([]);
  const [expandedFolders, setExpandedFolders] = React.useState<
    Set<FolderTreeNode>
  >(new Set());
  const [selectedFolder, setSelectedFolder] =
    React.useState<FolderTreeNode | null>(null);
  const [currentPage, setCurrentPage] = React.useState(0);
  const [totalHits, setTotalHits] = React.useState(0);
  const [isLoading, setIsLoading] = React.useState(false);
  const [error, setError] = React.useState<boolean>(false);

  const loadRootFolders = React.useCallback(
    async (pageNumber: number, append: boolean = false) => {
      setIsLoading(true);
      setError(false);
      try {
        const response = await getFolderTree({
          typesToInclude: "folder",
          pageNumber,
        });

        if (append) {
          setRootFolders((prev) => [...prev, ...response.records]);
        } else {
          setRootFolders(response.records);
        }

        setTotalHits(response.totalHits);
        setCurrentPage(pageNumber);
      } catch (err) {
        setError(true);
      } finally {
        setIsLoading(false);
      }
    },
    [getFolderTree],
  );

  React.useEffect(() => {
    if (rootFolderId) {
      setError(false);
      getFolder(rootFolderId)
        .then((response) => {
          const newFolder = folderDetailsAsTreeNode(response);
          setRootFolders([newFolder]);
        })
        .catch((err) => {
          setError(true);
        });
    } else {
      void loadRootFolders(0);
    }
  }, [rootFolderId, loadRootFolders, getFolder]);

  const hasMorePages = !rootFolderId && rootFolders.length < totalHits;

  return (
    <Box>
      {error && (
        <Box sx={{ mb: 2 }}>
          <Alert
            severity="error"
            action={
              <Button
                size="small"
                onClick={() => {
                  if (rootFolderId) {
                    setError(false);
                    getFolder(rootFolderId)
                      .then((response) => {
                        const newFolder = folderDetailsAsTreeNode(response);
                        setRootFolders([newFolder]);
                      })
                      .catch((err) => {
                        setError(true);
                      });
                  } else {
                    loadRootFolders(0);
                  }
                }}
                disabled={isLoading}
              >
                Retry
              </Button>
            }
          >
            Failed to load folders
          </Alert>
        </Box>
      )}
      <Tree<FolderTreeNode, string>
        aria-label="tree view of shared folder"
        getId={(item) => item.id.toString()}
        expandedItems={[...expandedFolders]}
        onExpandedItemsChange={(_event, newlyExpandedFolders) => {
          setExpandedFolders(new Set(newlyExpandedFolders));
        }}
        selectedItems={selectedFolder}
        onItemSelectionToggle={(_event, newlySelectedFolder) => {
          setSelectedFolder(newlySelectedFolder);
          onFolderSelect?.(newlySelectedFolder);
        }}
      >
        {rootFolders.map((folder) => (
          <TreeItemContent key={folder.id} folder={folder} />
        ))}
      </Tree>
      {hasMorePages && (
        <Box sx={{ p: 1, textAlign: "center" }}>
          <Button
            onClick={doNotAwait(() => loadRootFolders(currentPage + 1, true))}
            disabled={isLoading}
            startIcon={isLoading ? <CircularProgress size={16} /> : null}
          >
            {isLoading ? "Loading..." : "Load More"}
          </Button>
        </Box>
      )}
    </Box>
  );
}
