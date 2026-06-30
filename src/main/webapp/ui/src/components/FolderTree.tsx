import AddIcon from "@mui/icons-material/Add";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import React from "react";
import { useTranslation } from "react-i18next";
import useFolders, { type FolderTreeNode, folderDetailsAsTreeNode } from "../hooks/api/useFolders";
import IconButtonWithTooltip from "./IconButtonWithTooltip";
import { Tree, TreeItem } from "./Tree";
import ValidatingSubmitButton, { IsInvalid, IsValid } from "./ValidatingSubmitButton";

type CreateFolderDialogProps = {
  open: boolean;
  onClose: () => void;
  onSubmit: (name: string) => void;
  isLoading: boolean;
};

const CreateFolderDialog = ({ open, onClose, onSubmit, isLoading }: CreateFolderDialogProps): React.ReactNode => {
  const { t } = useTranslation("common");
  const [folderName, setFolderName] = React.useState("");

  const isValidName = folderName.length > 0;

  const handleSubmit = (e: React.FormEvent | React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    if (isValidName) {
      onSubmit(folderName);
    }
  };

  const handleClose = () => {
    setFolderName("");
    onClose();
  };

  React.useEffect(() => {
    if (!open) {
      setFolderName("");
    }
  }, [open]);

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t("folderTree.createFolder.title")}</DialogTitle>
      <DialogContent>
        <Box sx={{ mt: 0.75 }}>
          <TextField
            autoFocus
            label={t("folderTree.createFolder.folderName")}
            fullWidth
            variant="outlined"
            value={folderName}
            onChange={(e) => setFolderName(e.target.value)}
            disabled={isLoading}
            onKeyDown={(e) => {
              if (e.key === "Enter" && isValidName) {
                handleSubmit(e);
              }
            }}
          />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={isLoading}>
          {t("actions.cancel")}
        </Button>
        <ValidatingSubmitButton
          loading={isLoading}
          validationResult={isValidName ? IsValid() : IsInvalid(t("folderTree.createFolder.validation"))}
          onClick={handleSubmit}
        >
          {t("actions.create")}
        </ValidatingSubmitButton>
      </DialogActions>
    </Dialog>
  );
};

const TreeItemContent = ({
  folder,
  onFolderCreated,
}: {
  folder: FolderTreeNode;
  onFolderCreated?: (newFolder: FolderTreeNode, parentId: number) => void;
}): React.ReactNode => {
  const { t } = useTranslation("common");
  const { getFolderTree, createFolder } = useFolders();
  const [folders, setFolders] = React.useState<ReadonlyArray<FolderTreeNode>>([]);
  const [currentPage, setCurrentPage] = React.useState(0);
  const [totalHits, setTotalHits] = React.useState(0);
  const [isLoading, setIsLoading] = React.useState(false);
  const [error, setError] = React.useState<boolean>(false);

  const handleChildFolderCreated = React.useCallback(
    (newFolder: FolderTreeNode, parentId: number) => {
      // If this folder is the parent, add the new folder to our children
      if (parentId === folder.id) {
        setFolders((prev) => [newFolder, ...prev]);
      }
      // Propagate up to parent
      onFolderCreated?.(newFolder, parentId);
    },
    [folder.id, onFolderCreated],
  );
  const [isDialogOpen, setIsDialogOpen] = React.useState(false);
  const [isCreatingFolder, setIsCreatingFolder] = React.useState(false);

  const loadFolders = React.useCallback(
    async (pageNumber: number, append: boolean = false) => {
      setIsLoading(true);
      setError(false);
      try {
        const response = await getFolderTree({
          id: folder.id,
          typesToInclude: new Set(["folder", "notebook"]),
          pageNumber,
        });

        if (append) {
          setFolders((prev) => [...prev, ...response.records]);
        } else {
          setFolders(response.records);
        }

        setTotalHits(response.totalHits);
        setCurrentPage(pageNumber);
      } catch (_err) {
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

  const handleCreateFolder = React.useCallback(
    async (name: string) => {
      setIsCreatingFolder(true);
      try {
        const newFolderDetails = await createFolder({
          name,
          parentFolderId: folder.id,
        });
        const newFolder = folderDetailsAsTreeNode(newFolderDetails);
        setFolders((prev) => [newFolder, ...prev]);
        onFolderCreated?.(newFolder, folder.id);
        setIsDialogOpen(false);
      } catch (_err) {
        // Error is handled by the hook
      } finally {
        setIsCreatingFolder(false);
      }
    },
    [folder.id, createFolder, onFolderCreated],
  );

  const hasMorePages = folders.length < totalHits;

  const labelContent = (
    <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
      <span>{folder.name}</span>
      {folder.type !== "NOTEBOOK" && (
        <IconButtonWithTooltip
          title={`Add subfolder to ${folder.name}`}
          icon={<AddIcon fontSize="small" />}
          size="small"
          onClick={(e) => {
            e.stopPropagation();
            setIsDialogOpen(true);
          }}
          sx={{ opacity: 0.6, "&:hover": { opacity: 1 } }}
        />
      )}
    </Stack>
  );

  return (
    <>
      <TreeItem item={folder} label={labelContent} role="treeitem">
        {folders.map((subFolder) => (
          <TreeItemContent key={subFolder.id} folder={subFolder} onFolderCreated={handleChildFolderCreated} />
        ))}
        {error && (
          <Box sx={{ p: 1 }}>
            <Alert
              severity="error"
              action={
                <Button size="small" onClick={() => void loadFolders(currentPage)} disabled={isLoading}>
                  {t("actions.retry")}
                </Button>
              }
            >
              {t("folderTree.failedSubfolders")}
            </Alert>
          </Box>
        )}
        {hasMorePages && (
          <Box sx={{ p: 1 }}>
            <Button
              size="small"
              onClick={() => void loadFolders(currentPage + 1, true)}
              disabled={isLoading}
              startIcon={isLoading ? <CircularProgress size={16} /> : null}
            >
              {isLoading ? t("folderTree.loading") : t("folderTree.loadMore")}
            </Button>
          </Box>
        )}
      </TreeItem>
      <CreateFolderDialog
        open={isDialogOpen}
        onClose={() => setIsDialogOpen(false)}
        onSubmit={handleCreateFolder}
        isLoading={isCreatingFolder}
      />
    </>
  );
};

export default function FolderTree({
  rootFolderId,
  onFolderSelect,
}: {
  rootFolderId?: number;
  onFolderSelect?: (folder: FolderTreeNode | null) => void;
}): React.ReactNode {
  const { t } = useTranslation("common");
  const { getFolderTree, getFolder, createFolder } = useFolders();
  const [rootFolders, setRootFolders] = React.useState<ReadonlyArray<FolderTreeNode>>([]);
  const [expandedFolders, setExpandedFolders] = React.useState<Set<FolderTreeNode>>(new Set());
  const [selectedFolder, setSelectedFolder] = React.useState<FolderTreeNode | null>(null);
  const [currentPage, setCurrentPage] = React.useState(0);
  const [totalHits, setTotalHits] = React.useState(0);
  const [isLoading, setIsLoading] = React.useState(false);
  const [error, setError] = React.useState<boolean>(false);
  const [isDialogOpen, setIsDialogOpen] = React.useState(false);
  const [isCreatingFolder, setIsCreatingFolder] = React.useState(false);

  const loadRootFolders = React.useCallback(
    async (pageNumber: number, append: boolean = false) => {
      setIsLoading(true);
      setError(false);
      try {
        const response = await getFolderTree({
          typesToInclude: new Set(["folder", "notebook"]),
          pageNumber,
        });

        if (append) {
          setRootFolders((prev) => [...prev, ...response.records]);
        } else {
          setRootFolders(response.records);
        }

        setTotalHits(response.totalHits);
        setCurrentPage(pageNumber);
      } catch (_err) {
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
        .catch((_err) => {
          setError(true);
        });
    } else {
      void loadRootFolders(0);
    }
  }, [rootFolderId, loadRootFolders, getFolder]);

  const handleFolderCreated = React.useCallback(
    (newFolder: FolderTreeNode, parentId: number) => {
      // Select the newly created folder
      setSelectedFolder(newFolder);
      onFolderSelect?.(newFolder);
      // Expand the parent folder if it's in expandedFolders
      const parentFolder = rootFolders.find((f) => f.id === parentId);
      if (parentFolder) {
        setExpandedFolders((prev) => new Set([...prev, parentFolder]));
      }
    },
    [onFolderSelect, rootFolders],
  );

  const handleCreateRootFolder = React.useCallback(
    async (name: string) => {
      if (rootFolderId) return; // Only allow root folder creation when no specific root is set

      setIsCreatingFolder(true);
      try {
        const newFolderDetails = await createFolder({
          name,
          parentFolderId: 0, // Root folder
        });
        const newFolder = folderDetailsAsTreeNode(newFolderDetails);
        setRootFolders((prev) => [newFolder, ...prev]);
        setSelectedFolder(newFolder);
        onFolderSelect?.(newFolder);
        setIsDialogOpen(false);
      } catch (_err) {
        // Error is handled by the hook
      } finally {
        setIsCreatingFolder(false);
      }
    },
    [rootFolderId, createFolder, onFolderSelect],
  );

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
                      .catch((_err) => {
                        setError(true);
                      });
                  } else {
                    loadRootFolders(0);
                  }
                }}
                disabled={isLoading}
              >
                {t("actions.retry")}
              </Button>
            }
          >
            {t("folderTree.failedFolders")}
          </Alert>
        </Box>
      )}
      <Tree<FolderTreeNode, string>
        aria-label={t("folderTree.sharedFolderLabel")}
        getId={(item) => item.id.toString()}
        expandedItems={[...expandedFolders]}
        onExpandedItemsChange={(_event, newlyExpandedFolders) => {
          setExpandedFolders(new Set(newlyExpandedFolders));
        }}
        selectedItems={selectedFolder}
        onSelectedItemsChange={(_event, newlySelectedFolder) => {
          setSelectedFolder(newlySelectedFolder);
          onFolderSelect?.(newlySelectedFolder);
        }}
      >
        {rootFolders.map((folder) => (
          <TreeItemContent key={folder.id} folder={folder} onFolderCreated={handleFolderCreated} />
        ))}
      </Tree>
      {hasMorePages && (
        <Box sx={{ p: 1, textAlign: "center" }}>
          <Button
            onClick={() => void loadRootFolders(currentPage + 1, true)}
            disabled={isLoading}
            startIcon={isLoading ? <CircularProgress size={16} /> : null}
          >
            {isLoading ? t("folderTree.loading") : t("folderTree.loadMore")}
          </Button>
        </Box>
      )}
      <CreateFolderDialog
        open={isDialogOpen}
        onClose={() => setIsDialogOpen(false)}
        onSubmit={handleCreateRootFolder}
        isLoading={isCreatingFolder}
      />
    </Box>
  );
}
