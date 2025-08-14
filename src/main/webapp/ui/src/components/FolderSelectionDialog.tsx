import React from "react";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Button from "@mui/material/Button";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import FolderTree from "./FolderTree";
import useFolders, { type FolderRecord } from "../hooks/api/useFolders";

type FolderSelectionDialogProps = {
  open: boolean;
  onClose: () => void;
  onFolderSelect: (folder: FolderRecord, folderPath: string) => void;
  rootFolderId?: number;
  title?: string;
  selectedFolderId?: number;
};

export default function FolderSelectionDialog({
  open,
  onClose,
  onFolderSelect,
  rootFolderId,
  title = "Select Folder",
  selectedFolderId,
}: FolderSelectionDialogProps): React.ReactNode {
  const [selectedFolder, setSelectedFolder] =
    React.useState<FolderRecord | null>(null);
  const [folderPath, setFolderPath] = React.useState<string>("");
  const { getFolder } = useFolders();

  React.useEffect(() => {
    if (selectedFolder) {
      getFolder(selectedFolder.id)
        .then((folder) => {
          setFolderPath(folder.pathToRootFolder || folder.name);
        })
        .catch((error) => {
          console.error("Failed to get folder path:", error);
          setFolderPath(selectedFolder.name);
        });
    }
  }, [selectedFolder, getFolder]);

  const handleFolderSelect = React.useCallback((folder: FolderRecord) => {
    setSelectedFolder(folder);
  }, []);

  const handleConfirm = React.useCallback(() => {
    if (selectedFolder) {
      onFolderSelect(selectedFolder, folderPath);
      onClose();
    }
  }, [selectedFolder, folderPath, onFolderSelect, onClose]);

  const handleCancel = React.useCallback(() => {
    setSelectedFolder(null);
    setFolderPath("");
    onClose();
  }, [onClose]);

  React.useEffect(() => {
    if (!open) {
      setSelectedFolder(null);
      setFolderPath("");
    }
  }, [open]);

  return (
    <Dialog open={open} onClose={handleCancel} maxWidth="md" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Box sx={{ minHeight: 300, maxHeight: 500 }}>
          <FolderTree
            onFolderSelect={handleFolderSelect}
            selectedFolderId={selectedFolder?.id || selectedFolderId}
            rootFolderId={rootFolderId}
          />
        </Box>
        {selectedFolder && (
          <Box sx={{ mt: 2, p: 2, bgcolor: "action.hover", borderRadius: 1 }}>
            <Typography variant="subtitle2" gutterBottom>
              Selected folder:
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {folderPath || selectedFolder.name}
            </Typography>
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleCancel}>Cancel</Button>
        <Button
          onClick={handleConfirm}
          variant="contained"
          disabled={!selectedFolder}
        >
          Select
        </Button>
      </DialogActions>
    </Dialog>
  );
}
