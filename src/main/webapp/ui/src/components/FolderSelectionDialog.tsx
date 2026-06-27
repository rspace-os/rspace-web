import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Typography from "@mui/material/Typography";
import React from "react";
import { useTranslation } from "react-i18next";
import type { FolderTreeNode } from "../hooks/api/useFolders";
import FolderTree from "./FolderTree";

type FolderSelectionDialogProps = {
  open: boolean;
  onClose: () => void;
  onFolderSelect: (folder: FolderTreeNode) => void;
  rootFolderId?: number;
  title?: string;
};

export default function FolderSelectionDialog({
  open,
  onClose,
  onFolderSelect,
  rootFolderId,
  title,
}: FolderSelectionDialogProps): React.ReactNode {
  const { t } = useTranslation("common");
  const [selectedFolder, setSelectedFolder] = React.useState<FolderTreeNode | null>(null);

  const handleFolderSelect = React.useCallback((folder: FolderTreeNode | null) => {
    setSelectedFolder(folder);
  }, []);

  const handleConfirm = React.useCallback(() => {
    if (selectedFolder) {
      onFolderSelect(selectedFolder);
      onClose();
    }
  }, [selectedFolder, onFolderSelect, onClose]);

  const handleCancel = React.useCallback(() => {
    setSelectedFolder(null);
    onClose();
  }, [onClose]);

  React.useEffect(() => {
    if (!open) {
      setSelectedFolder(null);
    }
  }, [open]);

  return (
    <Dialog open={open} onClose={handleCancel} maxWidth="md" fullWidth>
      <DialogTitle>{title ?? t("folderSelectionDialog.title")}</DialogTitle>
      <DialogContent>
        <Box sx={{ minHeight: 300, maxHeight: 500 }}>
          <FolderTree onFolderSelect={handleFolderSelect} rootFolderId={rootFolderId} />
        </Box>
        {selectedFolder && (
          <Box
            sx={{ mt: 2, p: 2, bgcolor: "action.hover", borderRadius: 1 }}
            role="status"
            aria-live="polite"
            aria-label={t("folderSelectionDialog.selectedFolder")}
          >
            <Typography variant="subtitle2">{t("folderSelectionDialog.selectedFolderWithColon")}</Typography>
            <Typography variant="body2" color="text.secondary">
              {selectedFolder.name}
            </Typography>
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleCancel}>{t("actions.cancel")}</Button>
        <Button color="callToAction" onClick={handleConfirm} variant="contained" disabled={!selectedFolder}>
          {t("actions.select")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
