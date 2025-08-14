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
import useFolders, { type FolderRecord } from "../hooks/api/useFolders";
import { doNotAwait } from "../util/Util";

type FolderTreeNodeData = FolderRecord & {
  children?: FolderTreeNodeData[];
  hasMore?: boolean;
  totalHits?: number;
  currentPage?: number;
  loading?: boolean;
};

type FolderTreeProps = {
  onFolderSelect?: (folder: FolderRecord) => void;
  selectedFolderId?: number;
};

const PAGE_SIZE = 20;

export default function FolderTree({
  onFolderSelect,
  selectedFolderId,
}: FolderTreeProps): React.ReactNode {
  const { getFolderTree } = useFolders();
  const [expandedItems, setExpandedItems] = React.useState<string[]>([]);
  const [treeData, setTreeData] = React.useState<FolderTreeNodeData[]>([]);
  const [loading, setLoading] = React.useState(false);

  const loadFolderContents = React.useCallback(
    async (
      folderId?: number,
      pageNumber: number = 0,
      append: boolean = false,
    ) => {
      setLoading(true);
      try {
        const response = await getFolderTree(
          folderId,
          "folder",
          pageNumber,
          PAGE_SIZE,
        );

        const updateTreeData = (
          nodes: FolderTreeNodeData[],
        ): FolderTreeNodeData[] => {
          if (folderId === undefined) {
            const newNodes: FolderTreeNodeData[] = response.records.map(
              (folder) => ({
                ...folder,
                children: [],
                hasMore: response.totalHits > response.records.length,
                totalHits: response.totalHits,
                currentPage: 0,
              }),
            );

            if (append) {
              return [...nodes, ...newNodes];
            }
            return newNodes;
          }

          return nodes.map((node) => {
            if (node.id === folderId) {
              const newChildren: FolderTreeNodeData[] = response.records.map(
                (folder) => ({
                  ...folder,
                  children: [],
                  hasMore: false,
                  currentPage: 0,
                }),
              );

              const existingChildren = node.children || [];
              const updatedTotalHits = response.totalHits;
              const newTotalChildren = append
                ? existingChildren.length + newChildren.length
                : newChildren.length;

              return {
                ...node,
                children: append
                  ? [...existingChildren, ...newChildren]
                  : newChildren,
                currentPage: pageNumber,
                loading: false,
                hasMore: updatedTotalHits > newTotalChildren,
                totalHits: updatedTotalHits,
              };
            }

            if (node.children) {
              return {
                ...node,
                children: updateTreeData(node.children),
              };
            }

            return node;
          });
        };

        setTreeData((prevData) => updateTreeData(prevData));
      } finally {
        setLoading(false);
      }
    },
    [getFolderTree],
  );

  React.useEffect(() => {
    doNotAwait(loadFolderContents());
  }, [loadFolderContents]);

  const handleToggle = React.useCallback(
    (event: React.SyntheticEvent, itemIds: string[]) => {
      const newlyExpanded = itemIds.filter((id) => !expandedItems.includes(id));

      setExpandedItems(itemIds);

      newlyExpanded.forEach((itemId) => {
        const folderId = parseInt(itemId, 10);
        if (!isNaN(folderId)) {
          doNotAwait(loadFolderContents(folderId));
        }
      });
    },
    [expandedItems, loadFolderContents],
  );

  const handleSelect = React.useCallback(
    (event: React.SyntheticEvent, itemId: string | null) => {
      if (itemId && onFolderSelect) {
        const findFolder = (
          nodes: FolderTreeNodeData[],
          id: string,
        ): FolderRecord | null => {
          for (const node of nodes) {
            if (node.id.toString() === id) {
              return {
                id: node.id,
                globalId: node.globalId,
                name: node.name,
                type: node.type,
              };
            }
            if (node.children) {
              const found = findFolder(node.children, id);
              if (found) return found;
            }
          }
          return null;
        };

        const folder = findFolder(treeData, itemId);
        if (folder) {
          onFolderSelect(folder);
        }
      }
    },
    [treeData, onFolderSelect],
  );

  const handleLoadMore = React.useCallback(
    (folderId?: number) => {
      const findNode = (
        nodes: FolderTreeNodeData[],
        id?: number,
      ): FolderTreeNodeData | null => {
        if (id === undefined) return null;

        for (const node of nodes) {
          if (node.id === id) return node;
          if (node.children) {
            const found = findNode(node.children, id);
            if (found) return found;
          }
        }
        return null;
      };

      const node = folderId ? findNode(treeData, folderId) : null;
      const nextPage = node ? (node.currentPage || 0) + 1 : 1;

      doNotAwait(loadFolderContents(folderId, nextPage, true));
    },
    [treeData, loadFolderContents],
  );

  const renderTreeItems = (nodes: FolderTreeNodeData[]): React.ReactNode => {
    return nodes.map((node) => {
      const hasChildren =
        (node.children && node.children.length > 0) || node.hasMore;
      const currentTotal = node.children ? node.children.length : 0;
      const shouldShowLoadMore =
        node.totalHits && node.totalHits > currentTotal;

      return (
        <TreeItem
          key={node.id}
          itemId={node.id.toString()}
          label={
            <Box display="flex" alignItems="center" gap={1}>
              <FolderIcon fontSize="small" />
              <Typography variant="body2">{node.name}</Typography>
              {node.loading && <CircularProgress size={16} />}
            </Box>
          }
        >
          {node.children && renderTreeItems(node.children)}
          {shouldShowLoadMore && (
            <TreeItem
              key={`load-more-${node.id}`}
              itemId={`load-more-${node.id}`}
              label={
                <Button
                  size="small"
                  onClick={(e) => {
                    e.stopPropagation();
                    handleLoadMore(node.id);
                  }}
                  disabled={loading}
                >
                  Load More...
                </Button>
              }
            />
          )}
        </TreeItem>
      );
    });
  };

  if (loading && treeData.length === 0) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" p={2}>
        <CircularProgress size={24} />
        <Typography variant="body2" ml={1}>
          Loading folders...
        </Typography>
      </Box>
    );
  }

  return (
    <SimpleTreeView
      expandedItems={expandedItems}
      selectedItems={selectedFolderId ? [selectedFolderId.toString()] : []}
      onExpandedItemsChange={handleToggle}
      onSelectedItemsChange={handleSelect}
      slots={{
        expandIcon: ChevronRightIcon,
        collapseIcon: ExpandMoreIcon,
      }}
    >
      {renderTreeItems(treeData)}
    </SimpleTreeView>
  );
}
