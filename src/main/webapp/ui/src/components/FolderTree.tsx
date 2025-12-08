import AddIcon from "@mui/icons-material/Add";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import { styled } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import React from "react";
import useFolders, { type FolderTreeNode, folderDetailsAsTreeNode } from "../hooks/api/useFolders";
import { doNotAwait } from "../util/Util";
import IconButtonWithTooltip from "./IconButtonWithTooltip";
import { Tree, TreeItem } from "./Tree";
import ValidatingSubmitButton, { IsInvalid, IsValid } from "./ValidatingSubmitButton";

const _StyledTreeItemContent = styled(Box)({
    display: "flex",
    alignItems: "center",
    gap: "8px",
    "& .folder-actions": {
        opacity: 0,
        transition: "opacity 0.2s",
    },
    "&:hover .folder-actions": {
        opacity: 1,
    },
});

type CreateFolderDialogProps = {
    open: boolean;
    onClose: () => void;
    onSubmit: (name: string) => void;
    isLoading: boolean;
};

const CreateFolderDialog = ({ open, onClose, onSubmit, isLoading }: CreateFolderDialogProps): React.ReactNode => {
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
            <DialogTitle>Create New Folder</DialogTitle>
            <DialogContent>
                <Box mt={0.75}>
                    <TextField
                        autoFocus
                        label="Folder Name"
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
                    Cancel
                </Button>
                <ValidatingSubmitButton
                    loading={isLoading}
                    validationResult={isValidName ? IsValid() : IsInvalid("Folder name is required")}
                    onClick={handleSubmit}
                >
                    Create
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
        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
            <span>{folder.name}</span>
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
        </Box>
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
                        onClick={doNotAwait(() => loadRootFolders(currentPage + 1, true))}
                        disabled={isLoading}
                        startIcon={isLoading ? <CircularProgress size={16} /> : null}
                    >
                        {isLoading ? "Loading..." : "Load More"}
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
