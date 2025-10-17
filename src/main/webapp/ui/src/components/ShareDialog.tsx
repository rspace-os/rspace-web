import React from "react";
import Portal from "@mui/material/Portal";
import ErrorBoundary from "./ErrorBoundary";
import Alerts from "./Alerts/Alerts";
import { Dialog, DialogBoundary } from "./DialogBoundary";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Button from "@mui/material/Button";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import Paper from "@mui/material/Paper";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Box from "@mui/material/Box";
import Link from "@mui/material/Link";
import TextField from "@mui/material/TextField";
import Autocomplete from "@mui/material/Autocomplete";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import FormControl from "@mui/material/FormControl";
import Analytics from "./Analytics";
import AnalyticsContext from "../stores/contexts/Analytics";
import ValidatingSubmitButton from "./ValidatingSubmitButton";
import Result from "../util/result";
import useShare, {
  ShareInfo,
  ShareOption,
  NewShare,
  getParentFolderName,
} from "../hooks/api/useShare";
import { doNotAwait } from "../util/Util";
import useGroups from "../hooks/api/useGroups";
import useUserDetails from "../hooks/api/useUserDetails";
import useWhoAmI from "../hooks/api/useWhoAmI";
import useFolders from "../hooks/api/useFolders";
import useDocuments from "../hooks/api/useDocuments";
import FolderSelectionDialog from "./FolderSelectionDialog";
import { FolderTreeNode } from "../hooks/api/useFolders";
import UserDetails from "./UserDetails";
import GroupDetails from "./GroupDetails";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "../accentedTheme";
import { ACCENT_COLOR } from "../assets/branding/rspace/workspace";
import VisuallyHiddenHeading from "./VisuallyHiddenHeading";
import Stack from "@mui/material/Stack";
import RsSet, { flattenWithIntersectionWithEq } from "../util/set";
import WarningBar from "./WarningBar";
import AlertsContext, { mkAlert } from "@/stores/contexts/Alert";

type DocumentGlobalId = string;
type DocumentName = string;
type ShareId = string;
type Permission = "READ" | "EDIT" | "UNSHARE";

// Extended type with sharing state
type ShareOptionWithState = ShareOption & {
  isDisabled: boolean;
};

export default function Wrapper(): React.ReactNode {
  return (
    <Analytics>
      <ErrorBoundary topOfViewport>
        <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
          <Portal>
            <Alerts>
              <DialogBoundary>
                <ShareDialog shareDialogConfig="FromGlobalEvent" />
              </DialogBoundary>
            </Alerts>
          </Portal>
        </ThemeProvider>
      </ErrorBoundary>
    </Analytics>
  );
}

type ShareDialogConfig = {
  globalIds: ReadonlyArray<DocumentGlobalId>;
  open: boolean;
  onClose: () => void;
  names: ReadonlyArray<DocumentName>;
};

/**
 * Share dialog component is rendered on parts of the product that are not
 * wholly on a React tech stack, so this custom hook configures a global event
 * that any code can call to setup and open this dialog, provided it is
 * rendered somewhere on the page.
 *
 * The rest of the code in this module assumes that the OPEN_SHARE_DIALOG
 * event will not be dispatched again whilst the dialog is open.
 */
function useSetup(): ShareDialogConfig {
  const [open, setOpen] = React.useState(false);
  const [globalIds, setGlobalIds] = React.useState<
    ReadonlyArray<DocumentGlobalId>
  >([]);
  const [names, setNames] = React.useState<ReadonlyArray<DocumentName>>([]);

  React.useEffect(() => {
    function handler(event: Event) {
      // @ts-expect-error there will be a detail
      const { globalIds, names } = event.detail as {
        globalIds: string[];
        names: string[];
      };
      setOpen(true);
      setGlobalIds(globalIds || []);
      setNames(names || []);
    }
    window.addEventListener("OPEN_SHARE_DIALOG", handler);
    return () => {
      window.removeEventListener("OPEN_SHARE_DIALOG", handler);
    };
  }, []);

  return {
    open,
    onClose: () => {
      setOpen(false);
      setGlobalIds([]);
      setNames([]);
    },
    globalIds,
    names,
  };
}

export function ShareDialog({
  shareDialogConfig,
}: {
  shareDialogConfig: ShareDialogConfig | "FromGlobalEvent";
}) {
  const shareDialogConfigFromHook = useSetup();
  const { open, onClose, globalIds, names } =
    shareDialogConfig === "FromGlobalEvent"
      ? shareDialogConfigFromHook
      : shareDialogConfig;
  const [shareData, setShareData] = React.useState<
    Map<
      DocumentGlobalId,
      {
        directShares: ReadonlyArray<ShareInfo>;
        notebookShares: ReadonlyArray<ShareInfo>;
      }
    >
  >(new Map());
  const [loading, setLoading] = React.useState(false);
  const [saving, setSaving] = React.useState(false);
  const [shareOptions, setShareOptions] = React.useState<ShareOption[]>([]);
  const [optionsLoading, setOptionsLoading] = React.useState(false);
  const [permissionChanges, setPermissionChanges] = React.useState<
    Map<ShareId, Permission>
  >(new Map());
  const [newShares, setNewShares] = React.useState<
    Map<DocumentGlobalId, NewShare[]>
  >(new Map());
  // Track folder names for group shares: Map<sharedTargetId, folderName>
  const [groupFolderNames, setGroupFolderNames] = React.useState<
    Map<number, string>
  >(new Map());
  // Track folder selection dialog
  const [folderSelectionOpen, setFolderSelectionOpen] = React.useState(false);
  const [selectedShareForFolderChange, setSelectedShareForFolderChange] =
    React.useState<{
      shareId: ShareId;
      groupId: number;
      globalId: DocumentGlobalId;
      sharedFolderId: number;
    } | null>(null);
  // Track folder path changes for existing shares: Map<shareId, {name, id}>
  const [shareFolderChanges, setShareFolderChanges] = React.useState<
    Map<ShareId, { name: string; id: number }>
  >(new Map());
  const { trackEvent } = React.useContext(AnalyticsContext);
  const { getShareInfoForMultiple, createShare, deleteShare, updateShare } =
    useShare();
  const { getGroups } = useGroups();
  const { getGroupMembers } = useUserDetails();
  const { getFolder } = useFolders();
  const { move } = useDocuments();
  const currentUser = useWhoAmI();
  const [autocompleteInput, setAutocompleteInput] = React.useState("");
  const { addAlert } = React.useContext(AlertsContext);

  React.useEffect(() => {
    if (open && globalIds.length > 0) {
      setLoading(true);
      getShareInfoForMultiple(globalIds)
        .then(async (data) => {
          setShareData(data);

          // Collect all unique group IDs that need folder names
          const groupIds = new Set<number>();
          for (const shares of data.values()) {
            for (const share of shares.directShares) {
              if (share.recipientType === "GROUP") {
                groupIds.add(share.recipientId);
              }
            }
            for (const share of shares.notebookShares) {
              if (share.recipientType === "GROUP") {
                groupIds.add(share.recipientId);
              }
            }
          }

          // Fetch folder names for all groups
          if (groupIds.size > 0) {
            const folderNameMap = new Map<number, string>();
            const groups = await getGroups();

            await Promise.allSettled(
              Array.from(groupIds).map(async (groupId) => {
                const group = groups.find((g) => g.id === groupId);
                if (group?.sharedFolderId) {
                  try {
                    const folder = await getFolder(group.sharedFolderId);
                    folderNameMap.set(groupId, folder.name);
                  } catch (error) {
                    console.error(
                      `Failed to fetch folder for group ${groupId}:`,
                      error,
                    );
                    folderNameMap.set(groupId, "Unknown folder");
                  }
                }
              }),
            );

            setGroupFolderNames(folderNameMap);
          }
        })
        .catch((error) => {
          console.error("Failed to fetch sharing information:", error);
        })
        .finally(() => {
          setLoading(false);
        });
    }
  }, [open, globalIds]);

  // Fetch and combine groups and group members when dialog opens
  React.useEffect(() => {
    if (open) {
      setOptionsLoading(true);

      // Fetch both groups and group members simultaneously
      Promise.all([getGroups(), getGroupMembers()])
        .then(async ([groups, groupMembers]) => {
          // Combine groups and users into a single options array
          const groupOptions: ShareOption[] = groups.map((group) => ({
            ...group,
            optionType: "GROUP" as const,
          }));

          const userOptions: ShareOption[] = groupMembers
            .filter((user) => {
              if (currentUser.tag !== "success") return true;
              return user.id !== currentUser.value.id;
            })
            .map((user) => ({
              ...user,
              optionType: "USER" as const,
            }));

          setShareOptions([...groupOptions, ...userOptions]);

          // Fetch folder names for all groups in options
          if (groups.length > 0) {
            const folderNameMap = new Map<number, ShareId>();

            await Promise.allSettled(
              groups.map(async (group) => {
                if (group.sharedFolderId) {
                  try {
                    const folder = await getFolder(group.sharedFolderId);
                    folderNameMap.set(group.id, folder.name);
                  } catch (error) {
                    console.error(
                      `Failed to fetch folder for group ${group.id}:`,
                      error,
                    );
                    folderNameMap.set(group.id, "Unknown folder");
                  }
                }
              }),
            );

            setGroupFolderNames((prev) => new Map([...prev, ...folderNameMap]));
          }
        })
        .catch((error) => {
          console.error("Failed to fetch share options:", error);
          // Try to fetch just groups as fallback
          getGroups()
            .then(async (groups) => {
              const groupOptions: ShareOption[] = groups.map((group) => ({
                ...group,
                optionType: "GROUP" as const,
              }));
              setShareOptions(groupOptions);

              // Fetch folder names for groups in fallback
              if (groups.length > 0) {
                const folderNameMap = new Map<number, string>();

                await Promise.allSettled(
                  groups.map(async (group) => {
                    if (group.sharedFolderId) {
                      try {
                        const folder = await getFolder(group.sharedFolderId);
                        folderNameMap.set(group.id, folder.name);
                      } catch (error) {
                        console.error(
                          `Failed to fetch folder for group ${group.id}:`,
                          error,
                        );
                        folderNameMap.set(group.id, "Unknown folder");
                      }
                    }
                  }),
                );

                setGroupFolderNames(
                  (prev) => new Map([...prev, ...folderNameMap]),
                );
              }
            })
            .catch((groupError) => {
              console.error("Failed to fetch groups as fallback:", groupError);
              setShareOptions([]);
            });
        })
        .finally(() => {
          setOptionsLoading(false);
        });
    }
  }, [open, currentUser]);

  // Compute share options with disabled state based on current shares and new shares
  const shareOptionsWithState: ShareOptionWithState[] = React.useMemo(() => {
    interface ShareIdentifier {
      recipientType: ShareInfo["recipientType"];
      recipientId: ShareInfo["recipientId"];
    }
    const eqFunc = (a: ShareIdentifier, b: ShareIdentifier) =>
      a.recipientType === b.recipientType && a.recipientId === b.recipientId;
    const commonShares = flattenWithIntersectionWithEq(
      new RsSet(
        globalIds.map((gId) => {
          const alreadySharedWith: ReadonlyArray<ShareIdentifier> =
            shareData.get(gId)?.directShares ?? [];
          const toBeSharedWith: ShareIdentifier[] = newShares.get(gId) ?? [];
          return new RsSet([...alreadySharedWith, ...toBeSharedWith]);
        }),
      ),
      eqFunc,
    );
    return shareOptions.map((option) => ({
      ...option,
      isDisabled: commonShares.hasWithEq(
        { recipientType: option.optionType, recipientId: option.id },
        eqFunc,
      ),
    }));
  }, [shareOptions, shareData, newShares, globalIds]);

  // Check if there are any permission changes, new shares, or folder changes
  const hasChanges =
    permissionChanges.size > 0 ||
    newShares.size > 0 ||
    shareFolderChanges.size > 0;

  // Handle permission change
  function handlePermissionChange(shareId: ShareId, newPermission: Permission) {
    const newChanges = new Map(permissionChanges);
    if (newPermission === "UNSHARE") {
      newChanges.set(shareId, "UNSHARE");
    } else {
      newChanges.set(shareId, newPermission);
    }
    setPermissionChanges(newChanges);
  }

  // Get the current permission value (either from changes or original)
  function getCurrentPermission(share: ShareInfo): Permission {
    return permissionChanges.get(share.shareId.toString()) || share.permission;
  }

  // Handle permission change for new shares
  function handleNewSharePermissionChange(
    globalId: DocumentGlobalId,
    newShareId: ShareId,
    newPermission: Permission,
  ) {
    const updatedNewShares = new Map(newShares);
    const docNewShares = updatedNewShares.get(globalId) || [];

    if (newPermission === "UNSHARE") {
      // Remove the share if marked for unshare
      const updatedDocNewShares = docNewShares.filter(
        (share) => share.id !== newShareId,
      );
      if (updatedDocNewShares.length === 0) {
        updatedNewShares.delete(globalId);
      } else {
        updatedNewShares.set(globalId, updatedDocNewShares);
      }
    } else {
      // Update the permission
      const updatedDocNewShares = docNewShares.map((share) =>
        share.id === newShareId
          ? { ...share, permission: newPermission }
          : share,
      );
      updatedNewShares.set(globalId, updatedDocNewShares);
    }

    setNewShares(updatedNewShares);
  }

  function handleClose() {
    onClose();
    setShareData(new Map());
    setLoading(false);
    setShareOptions([]);
    setOptionsLoading(false);
    setPermissionChanges(new Map());
    setNewShares(new Map());
    setGroupFolderNames(new Map());
    setFolderSelectionOpen(false);
    setSelectedShareForFolderChange(null);
    setShareFolderChanges(new Map());
  }

  function handleCancel() {
    setPermissionChanges(new Map());
    setNewShares(new Map());
    setShareFolderChanges(new Map());
  }

  const validationResult = React.useMemo((): Result<null> => {
    return Result.Ok(null);
  }, [newShares]);

  /*
   * Here we save all of the changes that have been made: new shares, permission
   * changes, folder changes, and unshares. We do this in a sequence to ensure
   * that each operation completes before the next begins, which helps to
   * maintain data integrity and handle a server issue around concurrent updates.
   * If this becomes a performance issue, we can look at batching some of these
   * operations in the future or resolving the server-side issue.
   */
  async function handleSave() {
    if (!hasChanges) {
      trackEvent("user:closes:share_dialog:workspace");
      handleClose();
      return;
    }

    setSaving(true);
    try {
      // Handle deletions (unshare) sequentially
      const deletions = Array.from(permissionChanges.entries())
        .filter(([_, permission]) => permission === "UNSHARE")
        .map(([shareId, _]) => parseInt(shareId, 10));

      for (const shareId of deletions) {
        await deleteShare(shareId);
      }

      // Handle permission and folder modifications sequentially
      const allChangedShareIds = new Set([
        ...permissionChanges.keys(),
        ...shareFolderChanges.keys(),
      ]);

      for (const shareId of allChangedShareIds) {
        const newPermission = permissionChanges.get(shareId);
        const newLocationId = shareFolderChanges.get(shareId)?.id;
        if (newPermission !== "UNSHARE") {
          // Find the original share to get its details
          const originalShare = Array.from(shareData.values())
            .map((s) => s.directShares)
            .flat()
            .find((share) => share.shareId.toString() === shareId);

          if (originalShare) {
            if (
              newPermission !== originalShare.permission &&
              typeof newPermission !== "undefined"
            ) {
              await updateShare({
                ...originalShare,
                permission: newPermission,
              });
              continue;
            }
            if (newLocationId !== originalShare.parentId && newLocationId) {
              await move({
                documentId: originalShare.sharedDocId,
                sourceFolderId: originalShare.parentId!,
                destinationFolderId: newLocationId,
                ...(originalShare.grandparentId != null
                  ? { currentGrandparentId: originalShare.grandparentId }
                  : {}),
              });
              continue;
            }
          }
        }
      }

      // Handle new share creations sequentially
      const creations = Array.from(newShares.entries())
        .map(([globalId, shares]) => ({
          id: parseInt(globalId.replace(/\D/g, ""), 10),
          shares,
        }))
        .filter(({ id, shares }) => !isNaN(id) && shares.length > 0);

      for (const { id, shares } of creations) {
        await createShare(id, shares);
      }

      trackEvent("user:saves:share_changes:workspace");
      setPermissionChanges(new Map());
      setNewShares(new Map());
      setShareFolderChanges(new Map());
      setLoading(true);
      const data = await getShareInfoForMultiple(globalIds);
      setShareData(data);

      // Refresh folder names for any new group shares
      const groupIds = new Set<number>();
      for (const shares of data.values()) {
        for (const share of shares.directShares) {
          if (share.recipientType === "GROUP") {
            groupIds.add(share.recipientId);
          }
        }
      }

      if (groupIds.size > 0) {
        const folderNameMap = new Map<number, string>();
        const groups = await getGroups();

        await Promise.allSettled(
          Array.from(groupIds).map(async (groupId) => {
            const group = groups.find((g) => g.id === groupId);
            if (group?.sharedFolderId) {
              try {
                const folder = await getFolder(group.sharedFolderId);
                folderNameMap.set(groupId, folder.name);
              } catch (error) {
                console.error(
                  `Failed to fetch folder for group ${groupId}:`,
                  error,
                );
                folderNameMap.set(groupId, "Unknown folder");
              }
            }
          }),
        );

        setGroupFolderNames(folderNameMap);
      }

      handleClose();
      // @ts-expect-error global function
      // eslint-disable-next-line @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access
      getAndDisplayWorkspaceResults(workspaceSettings.url, workspaceSettings);
      addAlert(
        mkAlert({
          message: "Shares updated successfully.",
          variant: "success",
        }),
      );
    } catch (error) {
      console.error("Failed to save shares:", error);
    } finally {
      setSaving(false);
      setLoading(false);
    }
  }

  return (
    <Dialog
      open={open}
      onClose={() => {
        handleClose();
      }}
      onKeyDown={(e) => {
        e.stopPropagation();
      }}
      maxWidth="md"
      fullWidth
    >
      <DialogTitle>
        {names.length === 1 ? (
          <>
            Share <strong>{names[0]}</strong>
          </>
        ) : (
          `Share ${names.length} items`
        )}
      </DialogTitle>
      <DialogContent>
        <Box mb={3} mt={0.75}>
          <VisuallyHiddenHeading variant="h3">
            Add users or groups to share with
          </VisuallyHiddenHeading>
          <Autocomplete
            options={shareOptionsWithState}
            loading={optionsLoading}
            value={null}
            inputValue={autocompleteInput}
            onInputChange={(_, value, reason) => {
              if (reason === "input") {
                setAutocompleteInput(value);
              } else if (reason === "clear") {
                setAutocompleteInput("");
              } else if (reason === "reset") {
                setAutocompleteInput("");
              }
            }}
            selectOnFocus
            onChange={(_event, newValue) => {
              if (newValue && !newValue.isDisabled) {
                setAutocompleteInput("");
                const updatedNewShares = new Map(newShares);
                const updatedPermissionChanges = new Map(permissionChanges);

                globalIds.forEach((globalId) => {
                  const existingShares =
                    shareData.get(globalId)?.directShares || [];
                  const existingNewShares =
                    updatedNewShares.get(globalId) || [];

                  // Check if this user/group is already shared with this document
                  const alreadyShared = existingShares.find(
                    (share) =>
                      share.recipientId === newValue.id &&
                      share.recipientType === newValue.optionType,
                  );

                  // Check if this user/group is already in new shares for this document
                  const alreadyInNewShares = existingNewShares.find(
                    (share) =>
                      share.recipientId === newValue.id &&
                      share.recipientType === newValue.optionType,
                  );

                  if (alreadyShared) {
                    // Update the existing share's permission to READ and set new folder location
                    updatedPermissionChanges.set(
                      alreadyShared.shareId.toString(),
                      "READ",
                    );

                    // Set the new folder location if it's a group share
                    if (
                      newValue.optionType === "GROUP" &&
                      "sharedFolderId" in newValue &&
                      newValue.sharedFolderId
                    ) {
                      const currentFolderChanges = new Map(shareFolderChanges);
                      currentFolderChanges.set(
                        alreadyShared.shareId.toString(),
                        {
                          id: newValue.sharedFolderId,
                          name:
                            groupFolderNames.get(newValue.id) || "Loading...",
                        },
                      );
                      setShareFolderChanges(currentFolderChanges);
                    }
                  } else {
                    // Remove from new shares if already there
                    if (alreadyInNewShares) {
                      const filteredNewShares = existingNewShares.filter(
                        (share) =>
                          !(
                            share.recipientId === newValue.id &&
                            share.recipientType === newValue.optionType
                          ),
                      );
                      updatedNewShares.set(globalId, filteredNewShares);
                    }

                    // Add as new share only if not already an existing share
                    const newShare: NewShare = {
                      id: `new-${newValue.id}-${Date.now()}`, // temporary ID
                      recipientId: newValue.id,
                      recipientName:
                        newValue.optionType === "GROUP"
                          ? newValue.name
                          : newValue.username,
                      recipientType: newValue.optionType,
                      permission: "READ", // default permission
                      locationName:
                        newValue.optionType === "GROUP"
                          ? groupFolderNames.get(newValue.id) || "Loading..."
                          : null,
                      locationId:
                        newValue.optionType === "GROUP" &&
                        "sharedFolderId" in newValue
                          ? newValue.sharedFolderId
                          : null,
                    };

                    const currentNewShares =
                      updatedNewShares.get(globalId) || [];
                    updatedNewShares.set(globalId, [
                      ...currentNewShares,
                      newShare,
                    ]);
                  }
                });

                setNewShares(updatedNewShares);
                setPermissionChanges(updatedPermissionChanges);
              }
            }}
            getOptionLabel={(option) => {
              if (option.optionType === "GROUP") {
                return option.name;
              } else {
                return `${option.firstName} ${option.lastName} (${option.username})`;
              }
            }}
            getOptionDisabled={(option) => option.isDisabled}
            renderOption={(props, option) => (
              <Box
                component="li"
                {...props}
                sx={{
                  opacity: option.isDisabled ? 0.5 : 1,
                  cursor: option.isDisabled ? "not-allowed" : "pointer",
                }}
              >
                <Box sx={{ width: "100%" }}>
                  <Typography variant="body2" fontWeight="medium">
                    {option.optionType === "GROUP"
                      ? option.name
                      : `${option.firstName} ${option.lastName}`}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {option.isDisabled
                      ? `All of the documents have already been shared with ${
                          option.optionType === "GROUP"
                            ? option.name
                            : `${option.firstName} ${option.lastName}`
                        }`
                      : option.optionType === "GROUP"
                        ? `${option.type} • ${option.members?.length || 0} members`
                        : `User • ${option.username} • ${option.email}`}
                  </Typography>
                </Box>
              </Box>
            )}
            renderInput={(params) => (
              <TextField
                {...params}
                label="Add RSpace users or groups"
                placeholder="Type to filter groups and users..."
                InputProps={{
                  ...params.InputProps,
                  endAdornment: (
                    <>
                      {optionsLoading ? (
                        <CircularProgress color="inherit" size={20} />
                      ) : null}
                      {params.InputProps.endAdornment}
                    </>
                  ),
                }}
              />
            )}
            noOptionsText="No matches found. You can only share with groups you are in, and users in groups you are in."
            fullWidth
            clearOnBlur
            handleHomeEndKeys
          />
        </Box>

        {loading ? (
          <Box
            display="flex"
            justifyContent="center"
            alignItems="center"
            minHeight="200px"
          >
            <CircularProgress />
          </Box>
        ) : (
          <Stack spacing={2}>
            {globalIds.length === 1 ? (
              <Box>
                <Stack spacing={3}>
                  {(() => {
                    const globalId = globalIds[0];
                    const docName = names[0] || "this document";
                    const directShares =
                      shareData.get(globalId)?.directShares ?? [];
                    const notebookShares =
                      shareData.get(globalId)?.notebookShares ?? [];
                    const docNewShares = newShares.get(globalId) ?? [];
                    const allDirectShares = [...directShares, ...docNewShares];
                    return (
                      <Box>
                        <Stack spacing={3}>
                          {allDirectShares.length === 0 ? (
                            <Typography
                              variant="body2"
                              color="text.secondary"
                              style={{ fontStyle: "italic" }}
                            >
                              This document is not directly shared with anyone.
                            </Typography>
                          ) : (
                            <TableContainer
                              component={Paper}
                              variant="outlined"
                            >
                              <Table size="small" sx={{ mb: 0 }}>
                                <TableHead>
                                  <TableRow>
                                    <TableCell>Shared With</TableCell>
                                    <TableCell>Type</TableCell>
                                    <TableCell>Permission</TableCell>
                                    <TableCell>Location</TableCell>
                                  </TableRow>
                                </TableHead>
                                <TableBody>
                                  {/* Existing shares */}
                                  {directShares.map((share) => (
                                    <TableRow key={share.shareId}>
                                      <TableCell>
                                        <Box>
                                          {share.recipientType === "USER" ? (
                                            <UserDetails
                                              userId={share.recipientId}
                                              fullName={share.recipientName}
                                              position={["bottom", "right"]}
                                            />
                                          ) : (
                                            <GroupDetails
                                              groupId={share.recipientId}
                                              groupName={share.recipientName}
                                              position={["bottom", "right"]}
                                            />
                                          )}
                                        </Box>
                                      </TableCell>
                                      <TableCell>
                                        <Chip
                                          size="small"
                                          label={share.recipientType}
                                          color={
                                            share.recipientType === "USER"
                                              ? "primary"
                                              : "secondary"
                                          }
                                          variant="outlined"
                                        />
                                      </TableCell>
                                      <TableCell>
                                        <FormControl
                                          size="small"
                                          sx={{ minWidth: 120 }}
                                        >
                                          <Select
                                            inputProps={{
                                              "aria-label": `Set permission for sharing with ${share.recipientName}`,
                                            }}
                                            value={getCurrentPermission(share)}
                                            onChange={(e) => {
                                              const newValue = e.target.value;
                                              if (
                                                ![
                                                  "EDIT",
                                                  "READ",
                                                  "UNSHARE",
                                                ].includes(newValue)
                                              )
                                                throw new Error("Impossible");
                                              handlePermissionChange(
                                                share.shareId.toString(),
                                                newValue as Permission,
                                              );
                                            }}
                                            size="small"
                                          >
                                            <MenuItem value="READ">
                                              Read
                                            </MenuItem>
                                            <MenuItem value="EDIT">
                                              Edit
                                            </MenuItem>
                                            <MenuItem
                                              value="UNSHARE"
                                              sx={{ color: "error.main" }}
                                            >
                                              Unshare
                                            </MenuItem>
                                          </Select>
                                        </FormControl>
                                      </TableCell>
                                      <TableCell>
                                        {share.recipientType === "USER" ? (
                                          <>&mdash;</>
                                        ) : (
                                          <>
                                            {shareFolderChanges.get(
                                              share.shareId.toString(),
                                            )?.name ||
                                              getParentFolderName(share)}
                                            <Button
                                              size="small"
                                              sx={{ ml: 1 }}
                                              onClick={() => {
                                                const groups =
                                                  shareOptions.filter(
                                                    (opt) =>
                                                      opt.optionType ===
                                                      "GROUP",
                                                  );
                                                const group = groups.find(
                                                  (g) =>
                                                    g.id === share.recipientId,
                                                );
                                                if (
                                                  group &&
                                                  "sharedFolderId" in group
                                                ) {
                                                  setSelectedShareForFolderChange(
                                                    {
                                                      shareId:
                                                        share.shareId.toString(),
                                                      groupId:
                                                        share.recipientId,
                                                      globalId,
                                                      sharedFolderId:
                                                        group.sharedFolderId,
                                                    },
                                                  );
                                                  setFolderSelectionOpen(true);
                                                }
                                              }}
                                            >
                                              Change
                                            </Button>
                                          </>
                                        )}
                                      </TableCell>
                                    </TableRow>
                                  ))}
                                  {/* New shares */}
                                  {docNewShares.map((newShare) => (
                                    <TableRow
                                      key={newShare.id}
                                      sx={{
                                        backgroundColor: "action.hover",
                                      }}
                                    >
                                      <TableCell>
                                        <Box
                                          sx={{
                                            display: "flex",
                                            alignItems: "center",
                                            gap: 1,
                                          }}
                                        >
                                          {newShare.recipientType === "USER" ? (
                                            <Typography variant="body2">
                                              {newShare.recipientName}
                                            </Typography>
                                          ) : (
                                            <Typography
                                              variant="body2"
                                              color="success.dark"
                                              sx={{ fontWeight: "medium" }}
                                            >
                                              {newShare.recipientName}
                                            </Typography>
                                          )}
                                        </Box>
                                      </TableCell>
                                      <TableCell>
                                        <Chip
                                          size="small"
                                          label={newShare.recipientType}
                                          color={
                                            newShare.recipientType === "USER"
                                              ? "primary"
                                              : "secondary"
                                          }
                                          variant="outlined"
                                        />
                                      </TableCell>
                                      <TableCell>
                                        <FormControl
                                          size="small"
                                          sx={{ minWidth: 120 }}
                                        >
                                          <Select
                                            inputProps={{
                                              "aria-label": `Set permission for sharing with ${newShare.recipientName}`,
                                            }}
                                            value={newShare.permission}
                                            onChange={(e) => {
                                              const newValue = e.target.value;
                                              if (
                                                ![
                                                  "EDIT",
                                                  "READ",
                                                  "UNSHARE",
                                                ].includes(newValue)
                                              )
                                                throw new Error("Impossible");
                                              handleNewSharePermissionChange(
                                                globalId,
                                                newShare.id,
                                                newValue as Permission,
                                              );
                                            }}
                                            size="small"
                                          >
                                            <MenuItem value="READ">
                                              Read
                                            </MenuItem>
                                            <MenuItem value="EDIT">
                                              Edit
                                            </MenuItem>
                                            <MenuItem
                                              value="UNSHARE"
                                              sx={{ color: "error.main" }}
                                              disabled
                                            >
                                              Unshare
                                            </MenuItem>
                                          </Select>
                                        </FormControl>
                                      </TableCell>
                                      <TableCell>
                                        {newShare.recipientType === "USER" ? (
                                          <>&mdash;</>
                                        ) : (
                                          <>
                                            <Typography
                                              variant="body2"
                                              color="text.secondary"
                                            >
                                              {newShare.locationName || "/"}
                                            </Typography>
                                            <Button
                                              size="small"
                                              sx={{ ml: 1 }}
                                              onClick={() => {
                                                const groups =
                                                  shareOptions.filter(
                                                    (opt) =>
                                                      opt.optionType ===
                                                      "GROUP",
                                                  );
                                                const group = groups.find(
                                                  (g) =>
                                                    g.id ===
                                                    newShare.recipientId,
                                                );
                                                if (
                                                  group &&
                                                  "sharedFolderId" in group
                                                ) {
                                                  setSelectedShareForFolderChange(
                                                    {
                                                      shareId: newShare.id,
                                                      groupId:
                                                        newShare.recipientId,
                                                      globalId,
                                                      sharedFolderId:
                                                        group.sharedFolderId,
                                                    },
                                                  );
                                                  setFolderSelectionOpen(true);
                                                }
                                              }}
                                            >
                                              Change
                                            </Button>
                                          </>
                                        )}
                                      </TableCell>
                                    </TableRow>
                                  ))}
                                </TableBody>
                              </Table>
                            </TableContainer>
                          )}
                          {notebookShares.length > 0 && (
                            <Box>
                              <Typography
                                variant="body1"
                                component="h3"
                                gutterBottom
                              >
                                As {docName} is shared into Notebooks, it is
                                also shared with:
                              </Typography>
                              <TableContainer
                                component={Paper}
                                variant="outlined"
                                sx={{ my: 1 }}
                              >
                                <Table size="small" sx={{ mb: 0 }}>
                                  <TableHead>
                                    <TableRow>
                                      <TableCell>Notebook</TableCell>
                                      <TableCell>Shared With</TableCell>
                                      <TableCell>Type</TableCell>
                                      <TableCell>Permission</TableCell>
                                    </TableRow>
                                  </TableHead>
                                  <TableBody>
                                    {notebookShares.map((share) => (
                                      <TableRow key={share.shareId}>
                                        <TableCell>
                                          {getParentFolderName(share)}
                                        </TableCell>
                                        <TableCell>
                                          <Box>
                                            {share.recipientType === "USER" ? (
                                              <UserDetails
                                                userId={share.recipientId}
                                                fullName={share.recipientName}
                                                position={["bottom", "right"]}
                                              />
                                            ) : (
                                              <GroupDetails
                                                groupId={share.recipientId}
                                                groupName={share.recipientName}
                                                position={["bottom", "right"]}
                                              />
                                            )}
                                          </Box>
                                        </TableCell>
                                        <TableCell>
                                          <Chip
                                            size="small"
                                            label={share.recipientType}
                                            color={
                                              share.recipientType === "USER"
                                                ? "primary"
                                                : "secondary"
                                            }
                                            variant="outlined"
                                          />
                                        </TableCell>
                                        <TableCell>
                                          <FormControl
                                            size="small"
                                            sx={{ minWidth: 120 }}
                                            disabled
                                          >
                                            <Select
                                              inputProps={{
                                                "aria-label": `Set permission for sharing with ${share.recipientName}`,
                                              }}
                                              value={share.permission}
                                              onChange={() => {
                                                /* Read-only for notebook shares */
                                              }}
                                              size="small"
                                            >
                                              <MenuItem value="READ">
                                                Read
                                              </MenuItem>
                                              <MenuItem value="EDIT">
                                                Edit
                                              </MenuItem>
                                            </Select>
                                          </FormControl>
                                        </TableCell>
                                      </TableRow>
                                    ))}
                                  </TableBody>
                                </Table>
                              </TableContainer>
                              <Typography
                                variant="body1"
                                color="text.secondary"
                              >
                                Items in Notebooks inherit the Notebook's
                                permissions. Contact the notebook's owner to
                                alter the notebook's permissions.
                              </Typography>
                            </Box>
                          )}
                        </Stack>
                      </Box>
                    );
                  })()}
                </Stack>
              </Box>
            ) : (
              <>
                <Typography variant="h3" gutterBottom>
                  Adding shares to {globalIds.length} documents
                </Typography>
                <Typography
                  variant="body2"
                  color="text.secondary"
                  sx={{ mb: 2 }}
                >
                  Use the field above to add new shares. The share status of
                  multiple documents can be edited on the{" "}
                  <Link href="/record/share/manage">shared documents page</Link>
                  .
                </Typography>
                {newShares.size > 0 && (
                  <Box>
                    <Typography variant="h6" gutterBottom>
                      New shares to be added:
                    </Typography>
                    <Stack spacing={2}>
                      {/* Get unique shares across all documents */}
                      {(() => {
                        const uniqueShares = new Map<
                          DocumentGlobalId,
                          {
                            share: NewShare;
                            documentCount: number;
                          }
                        >();

                        // Collect all unique shares
                        Array.from(newShares.entries()).forEach(
                          ([_globalId, docNewShares]) => {
                            docNewShares.forEach((share) => {
                              const key = `${share.recipientType}-${share.recipientId}`;
                              if (uniqueShares.has(key)) {
                                uniqueShares.get(key)!.documentCount++;
                              } else {
                                uniqueShares.set(key, {
                                  share,
                                  documentCount: 1,
                                });
                              }
                            });
                          },
                        );

                        return Array.from(uniqueShares.values()).map(
                          ({ share, documentCount }) => (
                            <Box
                              key={`${share.recipientType}-${share.recipientId}`}
                              sx={{
                                display: "flex",
                                alignItems: "center",
                                gap: 2,
                                p: 2,
                                borderRadius: 1,
                                backgroundColor: "action.hover",
                              }}
                            >
                              <Box sx={{ flexGrow: 1 }}>
                                <Typography variant="body2" fontWeight="medium">
                                  {share.recipientName}
                                </Typography>
                                <Typography
                                  variant="caption"
                                  color="text.secondary"
                                >
                                  {documentCount === globalIds.length
                                    ? `All ${globalIds.length} documents`
                                    : `${documentCount} of ${globalIds.length} documents`}
                                </Typography>
                                {share.recipientType === "GROUP" && (
                                  <Typography
                                    variant="caption"
                                    color="text.secondary"
                                    sx={{ display: "block" }}
                                  >
                                    Location: {share.locationName || "/"}
                                  </Typography>
                                )}
                              </Box>

                              <Chip
                                size="small"
                                label={share.recipientType}
                                color={
                                  share.recipientType === "USER"
                                    ? "primary"
                                    : "secondary"
                                }
                                variant="outlined"
                              />

                              <FormControl size="small" sx={{ minWidth: 80 }}>
                                <Select
                                  inputProps={{
                                    "aria-label": `Set permission for sharing with ${share.recipientName}`,
                                  }}
                                  value={share.permission}
                                  onChange={(e) => {
                                    const newPermission = e.target.value;
                                    if (newPermission === "UNSHARE") {
                                      // Remove this share from all documents
                                      const updatedNewShares = new Map(
                                        newShares,
                                      );
                                      globalIds.forEach((globalId) => {
                                        const docShares =
                                          updatedNewShares.get(globalId) || [];
                                        const filteredShares = docShares.filter(
                                          (s) =>
                                            !(
                                              s.recipientType ===
                                                share.recipientType &&
                                              s.recipientId ===
                                                share.recipientId
                                            ),
                                        );
                                        if (filteredShares.length === 0) {
                                          updatedNewShares.delete(globalId);
                                        } else {
                                          updatedNewShares.set(
                                            globalId,
                                            filteredShares,
                                          );
                                        }
                                      });
                                      setNewShares(updatedNewShares);
                                    } else {
                                      // Update permission for this share across all documents
                                      const updatedNewShares = new Map(
                                        newShares,
                                      );
                                      globalIds.forEach((globalId) => {
                                        const docShares =
                                          updatedNewShares.get(globalId) || [];
                                        const updatedDocShares = docShares.map(
                                          (s) =>
                                            s.recipientType ===
                                              share.recipientType &&
                                            s.recipientId === share.recipientId
                                              ? {
                                                  ...s,
                                                  permission: newPermission as
                                                    | "READ"
                                                    | "EDIT",
                                                }
                                              : s,
                                        );
                                        updatedNewShares.set(
                                          globalId,
                                          updatedDocShares,
                                        );
                                      });
                                      setNewShares(updatedNewShares);
                                    }
                                  }}
                                  size="small"
                                >
                                  <MenuItem value="READ">Read</MenuItem>
                                  <MenuItem value="EDIT">Edit</MenuItem>
                                  <MenuItem
                                    value="UNSHARE"
                                    sx={{ color: "error.main" }}
                                  >
                                    Remove
                                  </MenuItem>
                                </Select>
                              </FormControl>

                              {share.recipientType === "GROUP" && (
                                <Button
                                  size="small"
                                  onClick={() => {
                                    const groups = shareOptions.filter(
                                      (opt) => opt.optionType === "GROUP",
                                    );
                                    const group = groups.find(
                                      (g) => g.id === share.recipientId,
                                    );
                                    if (group && "sharedFolderId" in group) {
                                      // Use the first globalId as a placeholder
                                      setSelectedShareForFolderChange({
                                        shareId: share.id,
                                        groupId: share.recipientId,
                                        globalId: globalIds[0],
                                        sharedFolderId: group.sharedFolderId,
                                      });
                                      setFolderSelectionOpen(true);
                                    }
                                  }}
                                >
                                  Change Folder
                                </Button>
                              )}
                            </Box>
                          ),
                        );
                      })()}
                    </Stack>
                  </Box>
                )}
              </>
            )}
          </Stack>
        )}
      </DialogContent>
      {hasChanges && <WarningBar />}
      <FolderSelectionDialog
        open={folderSelectionOpen}
        onClose={() => {
          setFolderSelectionOpen(false);
          setSelectedShareForFolderChange(null);
        }}
        onFolderSelect={(folder: FolderTreeNode) => {
          if (!selectedShareForFolderChange) return;

          const { shareId, groupId } = selectedShareForFolderChange;

          // For multiple documents, update all shares for this group across all documents
          if (globalIds.length > 1) {
            const updatedNewShares = new Map(newShares);
            globalIds.forEach((globalId) => {
              const docShares = updatedNewShares.get(globalId) || [];
              const updatedDocShares = docShares.map((share) =>
                share.recipientType === "GROUP" && share.recipientId === groupId
                  ? {
                      ...share,
                      locationName: folder.name,
                      locationId: folder.id,
                    }
                  : share,
              );
              updatedNewShares.set(globalId, updatedDocShares);
            });
            setNewShares(updatedNewShares);
          } else {
            // Single document logic (existing)
            const { globalId } = selectedShareForFolderChange;
            const updatedNewShares = new Map(newShares);
            const docNewShares = updatedNewShares.get(globalId) || [];
            const isNewShare = docNewShares.some(
              (share) => share.id === shareId,
            );

            if (isNewShare) {
              // Handle new shares
              const updatedDocNewShares = docNewShares.map((share) =>
                share.id === shareId
                  ? {
                      ...share,
                      locationName: folder.name,
                      locationId: folder.id,
                    }
                  : share,
              );
              updatedNewShares.set(globalId, updatedDocNewShares);
              setNewShares(updatedNewShares);
            } else {
              // Handle existing shares - track folder changes
              const updatedFolderChanges = new Map(shareFolderChanges);
              updatedFolderChanges.set(shareId, {
                id: folder.id,
                name: folder.name,
              });
              setShareFolderChanges(updatedFolderChanges);
            }
          }

          setFolderSelectionOpen(false);
          setSelectedShareForFolderChange(null);
        }}
        rootFolderId={selectedShareForFolderChange?.sharedFolderId}
        title="Select Shared Folder Location"
      />
      <DialogActions>
        {hasChanges && <Button onClick={handleCancel}>Cancel</Button>}
        <ValidatingSubmitButton
          loading={saving}
          onClick={doNotAwait(handleSave)}
          validationResult={validationResult}
        >
          {hasChanges ? "Save" : "Done"}
        </ValidatingSubmitButton>
      </DialogActions>
    </Dialog>
  );
}
