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
import useFolders, {
  folderDetailsAsTreeNode,
  type FolderTreeNode,
} from "../hooks/api/useFolders";
import { doNotAwait } from "../util/Util";
import * as MapUtils from "../util/MapUtils";
import * as ArrayUtils from "../util/ArrayUtils";
import * as Parsers from "../util/parsers";

// TODO: pagination
// TODO: error handling
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

  React.useEffect(() => {
    getFolderTree({ id: folder.id, typesToInclude: "folder" }).then(
      (response) => {
        setFolders(response.records);
        response.records.forEach((folder) => {
          onNewFolder(folder);
        });
      },
    );
  }, [folder]);

  return (
    <TreeItem itemId={folder.id.toString()} label={folder.name} role="treeitem">
      {folders.map((folder) => (
        <TreeItemContent
          key={folder.id}
          folder={folder}
          onNewFolder={onNewFolder}
        />
      ))}
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

  React.useEffect(() => {
    if (rootFolderId) {
      getFolder(rootFolderId).then((response) => {
        const newFolder = folderDetailsAsTreeNode(response);
        setRootFolders([newFolder]);
        allFoldersInTree.set(newFolder.id, newFolder);
      });
    } else {
      getFolderTree({ typesToInclude: "folder" }).then((response) => {
        setRootFolders(response.records);
        response.records.forEach((folder) => {
          allFoldersInTree.set(folder.id, folder);
        });
      });
    }
  }, [rootFolderId]);

  return (
    <SimpleTreeView
      aria-label="tree view of shared folder"
      expandedItems={[...expandedFolders].map((folder) => folder.id.toString())}
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
  );
}
