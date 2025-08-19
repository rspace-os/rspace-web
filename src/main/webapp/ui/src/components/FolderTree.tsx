import React from "react";
import { SimpleTreeView } from "@mui/x-tree-view/SimpleTreeView";
import { TreeItem } from "@mui/x-tree-view/TreeItem";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import FolderIcon from "@mui/icons-material/Folder";
import Button from "@mui/material/Button";
import Box from "@mui/material/Box";
import CircularProgress from "@mui/material/CircularProgress";
import Typography from "@mui/material/Typography";
import Alert from "@mui/material/Alert";
import useFolders, {
  folderDetailsAsTreeNode,
  type FolderTreeNode,
} from "../hooks/api/useFolders";
import { doNotAwait } from "../util/Util";
import * as MapUtils from "../util/MapUtils";
import * as ArrayUtils from "../util/ArrayUtils";
import * as Parsers from "../util/parsers";

// TODO: create abstraction?

const TreeItemContent = ({
  folder,
  onNewFolder,
}: {
  folder: FolderTreeNode;
  onNewFolder: (folder: FolderTreeNode) => void;
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

        response.records.forEach((folder) => {
          onNewFolder(folder);
        });
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
    <TreeItem itemId={folder.id.toString()} label={folder.name} role="treeitem">
      {folders.map((folder) => (
        <TreeItemContent
          key={folder.id}
          folder={folder}
          onNewFolder={onNewFolder}
        />
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
  const [allFoldersInTree] = React.useState<
    Map<FolderTreeNode["id"], FolderTreeNode>
  >(new Map());
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

        response.records.forEach((folder) => {
          allFoldersInTree.set(folder.id, folder);
        });
      } catch (err) {
        setError(true);
      } finally {
        setIsLoading(false);
      }
    },
    [getFolderTree, allFoldersInTree],
  );

  React.useEffect(() => {
    if (rootFolderId) {
      setError(false);
      getFolder(rootFolderId)
        .then((response) => {
          const newFolder = folderDetailsAsTreeNode(response);
          setRootFolders([newFolder]);
          allFoldersInTree.set(newFolder.id, newFolder);
        })
        .catch((err) => {
          setError(true);
        });
    } else {
      void loadRootFolders(0);
    }
  }, [rootFolderId, loadRootFolders, getFolder, allFoldersInTree]);

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
                        allFoldersInTree.set(newFolder.id, newFolder);
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
      <SimpleTreeView
        aria-label="tree view of shared folder"
        expandedItems={[...expandedFolders].map((folder) =>
          folder.id.toString(),
        )}
        onExpandedItemsChange={(_event, nodeIds) => {
          setExpandedFolders(
            new Set(
              ArrayUtils.mapOptional(
                (idString) =>
                  Parsers.parseInteger(idString)
                    .toOptional()
                    .flatMap((id) => MapUtils.get(allFoldersInTree, id)),
                nodeIds,
              ),
            ),
          );
        }}
        selectedItems={selectedFolder?.id.toString()}
        onItemSelectionToggle={(_event, idAsString) => {
          const folder = Parsers.parseInteger(idAsString)
            .toOptional()
            .flatMap((id) => MapUtils.get(allFoldersInTree, id))
            .orElse(null);
          setSelectedFolder(folder);
          onFolderSelect?.(folder);
        }}
      >
        {rootFolders.map((folder) => (
          <TreeItemContent
            key={folder.id}
            folder={folder}
            onNewFolder={(folder) => {
              allFoldersInTree.set(folder.id, folder);
            }}
          />
        ))}
      </SimpleTreeView>
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
