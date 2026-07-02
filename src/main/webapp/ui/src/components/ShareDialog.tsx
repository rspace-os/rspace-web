import Alert from "@mui/material/Alert";
import Autocomplete, { type AutocompleteRenderInputParams } from "@mui/material/Autocomplete";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Portal from "@mui/material/Portal";
import Select from "@mui/material/Select";
import Stack from "@mui/material/Stack";
import { ThemeProvider } from "@mui/material/styles";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import React from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import AlertsContext, { mkAlert } from "@/stores/contexts/Alert";
import createAccentedTheme from "../accentedTheme";
import { ACCENT_COLOR } from "../assets/branding/rspace/workspace";
import useDocuments from "../hooks/api/useDocuments";
import useFolders, { type FolderTreeNode } from "../hooks/api/useFolders";
import useGroups from "../hooks/api/useGroups";
import useShare, { getParentFolderName, type NewShare, type ShareInfo, type ShareOption } from "../hooks/api/useShare";
import useUserDetails from "../hooks/api/useUserDetails";
import useWhoAmI from "../hooks/api/useWhoAmI";
import AnalyticsContext from "../stores/contexts/Analytics";
import Result from "../util/result";
import RsSet, { flattenWithIntersectionWithEq } from "../util/set";
import Alerts from "./Alerts/Alerts";
import Analytics from "./Analytics";
import { Dialog, DialogBoundary } from "./DialogBoundary";
import ErrorBoundary from "./ErrorBoundary";
import FolderSelectionDialog from "./FolderSelectionDialog";
import GroupDetails from "./GroupDetails";
import UserDetails from "./UserDetails";
import ValidatingSubmitButton from "./ValidatingSubmitButton";
import VisuallyHiddenHeading from "./VisuallyHiddenHeading";
import WarningBar from "./WarningBar";

type DocumentGlobalId = string;
type DocumentName = string;
type ShareId = string;
type Permission = "READ" | "EDIT" | "UNSHARE";

type ShareOptionWithState = ShareOption & {
  isDisabled: boolean;
};

function getGroupFolderId(
  group: {
    sharedFolderId: number;
    sharedSnippetFolderId: number;
  },
  isSnippet: boolean,
): number {
  return isSnippet ? group.sharedSnippetFolderId : group.sharedFolderId;
}

export default function Wrapper(): React.ReactNode {
  return (
    <Analytics>
      <ErrorBoundary topOfViewport>
        <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
          <Portal>
            <Alerts>
              <DialogBoundary>
                <ShareDialogFromGlobalEvent />
              </DialogBoundary>
            </Alerts>
          </Portal>
        </ThemeProvider>
      </ErrorBoundary>
    </Analytics>
  );
}

export type ShareDialogProps = {
  globalIds: ReadonlyArray<DocumentGlobalId>;
  open: boolean;
  onClose: () => void;
  names: ReadonlyArray<DocumentName>;
  isSnippet?: boolean;
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
function useSetup(): ShareDialogProps {
  const [open, setOpen] = React.useState(false);
  const [globalIds, setGlobalIds] = React.useState<ReadonlyArray<DocumentGlobalId>>([]);
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

function ShareDialogFromGlobalEvent(): React.ReactNode {
  const { open, onClose, globalIds, names } = useSetup();
  return <ShareDialog open={open} onClose={onClose} globalIds={globalIds} names={names} />;
}

export function ShareDialog({ open, onClose, globalIds, names, isSnippet = false }: ShareDialogProps) {
  const { t } = useTranslation("common");
  /*
   * The noun for the shared items ("document" vs "snippet") is selected inside
   * each ICU message via `itemType`, so every locale supplies its own
   * correctly-declined and pluralised noun. It is derived from `isSnippet`
   * rather than passed as a separate prop.
   */
  const itemType = isSnippet ? "snippet" : "document";
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
  const [permissionChanges, setPermissionChanges] = React.useState<Map<ShareId, Permission>>(new Map());
  const [newShares, setNewShares] = React.useState<Map<DocumentGlobalId, NewShare[]>>(new Map());
  // Track folder names for group shares: Map<sharedTargetId, folderName>
  const [groupFolderNames, setGroupFolderNames] = React.useState<Map<number, string>>(new Map());
  // Track folder selection dialog
  const [folderSelectionOpen, setFolderSelectionOpen] = React.useState(false);
  const [selectedShareForFolderChange, setSelectedShareForFolderChange] = React.useState<{
    shareId: ShareId;
    groupId: number;
    globalId: DocumentGlobalId;
    sharedFolderId: number;
  } | null>(null);
  // Track folder path changes for existing shares: Map<shareId, {name, id}>
  const [shareFolderChanges, setShareFolderChanges] = React.useState<Map<ShareId, { name: string; id: number }>>(
    new Map(),
  );
  const { trackEvent } = React.useContext(AnalyticsContext);
  const { getShareInfoForMultiple, createShare, deleteShare, updateShare } = useShare();
  const { getGroups } = useGroups();
  const { getGroupMembers } = useUserDetails();
  const { getFolder } = useFolders();
  const { move } = useDocuments();
  const currentUser = useWhoAmI();
  const [autocompleteInput, setAutocompleteInput] = React.useState("");
  const { addAlert } = React.useContext(AlertsContext);

  const resetDialogState = React.useCallback(() => {
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
    setAutocompleteInput("");
  }, []);

  React.useEffect(() => {
    if (!open) {
      resetDialogState();
    }
  }, [open, resetDialogState]);

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
                const sharedFolderId = group ? getGroupFolderId(group, isSnippet) : null;
                if (sharedFolderId) {
                  try {
                    const folder = await getFolder(sharedFolderId);
                    folderNameMap.set(groupId, folder.name);
                  } catch (error) {
                    console.error(`Failed to fetch folder for group ${groupId}:`, error);
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
  }, [open, globalIds, isSnippet]);

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
                const sharedFolderId = getGroupFolderId(group, isSnippet);
                if (sharedFolderId) {
                  try {
                    const folder = await getFolder(sharedFolderId);
                    folderNameMap.set(group.id, folder.name);
                  } catch (error) {
                    console.error(`Failed to fetch folder for group ${group.id}:`, error);
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
                    const sharedFolderId = getGroupFolderId(group, isSnippet);
                    if (sharedFolderId) {
                      try {
                        const folder = await getFolder(sharedFolderId);
                        folderNameMap.set(group.id, folder.name);
                      } catch (error) {
                        console.error(`Failed to fetch folder for group ${group.id}:`, error);
                        folderNameMap.set(group.id, "Unknown folder");
                      }
                    }
                  }),
                );

                setGroupFolderNames((prev) => new Map([...prev, ...folderNameMap]));
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
  }, [open, currentUser, isSnippet]);

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
          const alreadySharedWith: ReadonlyArray<ShareIdentifier> = shareData.get(gId)?.directShares ?? [];
          const toBeSharedWith: ShareIdentifier[] = newShares.get(gId) ?? [];
          return new RsSet([...alreadySharedWith, ...toBeSharedWith]);
        }),
      ),
      eqFunc,
    );
    return shareOptions.map((option) => ({
      ...option,
      isDisabled: commonShares.hasWithEq({ recipientType: option.optionType, recipientId: option.id }, eqFunc),
    }));
  }, [shareOptions, shareData, newShares, globalIds]);

  // Check if there are any permission changes, new shares, or folder changes
  const hasChanges = permissionChanges.size > 0 || newShares.size > 0 || shareFolderChanges.size > 0;

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
  function handleNewSharePermissionChange(globalId: DocumentGlobalId, newShareId: ShareId, newPermission: Permission) {
    const updatedNewShares = new Map(newShares);
    const docNewShares = updatedNewShares.get(globalId) || [];

    if (newPermission === "UNSHARE") {
      // Remove the share if marked for unshare
      const updatedDocNewShares = docNewShares.filter((share) => share.id !== newShareId);
      if (updatedDocNewShares.length === 0) {
        updatedNewShares.delete(globalId);
      } else {
        updatedNewShares.set(globalId, updatedDocNewShares);
      }
    } else {
      // Update the permission
      const updatedDocNewShares = docNewShares.map((share) =>
        share.id === newShareId ? { ...share, permission: newPermission } : share,
      );
      updatedNewShares.set(globalId, updatedDocNewShares);
    }

    setNewShares(updatedNewShares);
  }

  function handleClose() {
    onClose();
    resetDialogState();
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
      trackEvent("user:close:share_dialog");
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
      const allChangedShareIds = new Set([...permissionChanges.keys(), ...shareFolderChanges.keys()]);

      for (const shareId of allChangedShareIds) {
        const newPermission = permissionChanges.get(shareId);
        const newLocationId = shareFolderChanges.get(shareId)?.id;
        if (newPermission !== "UNSHARE") {
          // Find the original share to get its details
          const originalShare = Array.from(shareData.values())
            .flatMap((s) => s.directShares)
            .find((share) => share.shareId.toString() === shareId);

          if (originalShare) {
            if (newPermission !== originalShare.permission && typeof newPermission !== "undefined") {
              await updateShare({
                ...originalShare,
                permission: newPermission,
              });
              continue;
            }
            if (newLocationId !== originalShare.parentId && newLocationId) {
              await move({
                documentId: originalShare.sharedDocId,
                // biome-ignore lint/style/noNonNullAssertion: initial biome migration
                sourceFolderId: originalShare.parentId!,
                destinationFolderId: newLocationId,
                ...(originalShare.grandparentId != null ? { currentGrandparentId: originalShare.grandparentId } : {}),
              });
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
        .filter(({ id, shares }) => !Number.isNaN(id) && shares.length > 0);

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
            const sharedFolderId = group ? getGroupFolderId(group, isSnippet) : null;
            if (sharedFolderId) {
              try {
                const folder = await getFolder(sharedFolderId);
                folderNameMap.set(groupId, folder.name);
              } catch (error) {
                console.error(`Failed to fetch folder for group ${groupId}:`, error);
                folderNameMap.set(groupId, "Unknown folder");
              }
            }
          }),
        );

        setGroupFolderNames(folderNameMap);
      }

      handleClose();
      try {
        // This only exists in workspace, not Gallery. Since ShareDialog can be used
        // outside of the workspace, we let this fail silently if the function doesn't exist
        // TODO: Remove this when old Workspace is removed
        // @ts-expect-error See above
        // eslint-disable-next-line @typescript-eslint/no-unsafe-call,@typescript-eslint/no-unsafe-member-access
        getAndDisplayWorkspaceResults(workspaceSettings.url, workspaceSettings);
      } catch {}
      addAlert(
        mkAlert({
          message: t("shareDialog.updatedSuccessfully"),
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
          <TransRichText ns="common" i18nKey="shareDialog.titleSingle" values={{ name: names[0] }} />
        ) : (
          t("shareDialog.titleMultiple", { count: names.length, itemType })
        )}
      </DialogTitle>
      <DialogContent>
        <Box sx={{ mb: 3, mt: 0.75 }}>
          <VisuallyHiddenHeading variant="h3">{t("shareDialog.addUsersOrGroups")}</VisuallyHiddenHeading>
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
                  const existingShares = shareData.get(globalId)?.directShares || [];
                  const existingNewShares = updatedNewShares.get(globalId) || [];

                  // Check if this user/group is already shared with this document
                  const alreadyShared = existingShares.find(
                    (share) => share.recipientId === newValue.id && share.recipientType === newValue.optionType,
                  );

                  // Check if this user/group is already in new shares for this document
                  const alreadyInNewShares = existingNewShares.find(
                    (share) => share.recipientId === newValue.id && share.recipientType === newValue.optionType,
                  );

                  if (alreadyShared) {
                    // Update the existing share's permission to READ and set new folder location
                    updatedPermissionChanges.set(alreadyShared.shareId.toString(), "READ");

                    // Set the new folder location if it's a group share
                    const sharedFolderId =
                      newValue.optionType === "GROUP" ? getGroupFolderId(newValue, isSnippet) : null;
                    if (sharedFolderId) {
                      const currentFolderChanges = new Map(shareFolderChanges);
                      currentFolderChanges.set(alreadyShared.shareId.toString(), {
                        id: sharedFolderId,
                        name: groupFolderNames.get(newValue.id) || "",
                      });
                      setShareFolderChanges(currentFolderChanges);
                    }
                  } else {
                    // Remove from new shares if already there
                    if (alreadyInNewShares) {
                      const filteredNewShares = existingNewShares.filter(
                        (share) => !(share.recipientId === newValue.id && share.recipientType === newValue.optionType),
                      );
                      updatedNewShares.set(globalId, filteredNewShares);
                    }

                    // Add as new share only if not already an existing share
                    const newShare: NewShare = {
                      id: `new-${newValue.id}-${Date.now()}`, // temporary ID
                      recipientId: newValue.id,
                      recipientName: newValue.optionType === "GROUP" ? newValue.name : newValue.username,
                      recipientType: newValue.optionType,
                      permission: "READ", // default permission
                      locationName: newValue.optionType === "GROUP" ? groupFolderNames.get(newValue.id) || "" : null,
                      locationId: newValue.optionType === "GROUP" ? getGroupFolderId(newValue, isSnippet) : null,
                    };

                    const currentNewShares = updatedNewShares.get(globalId) || [];
                    updatedNewShares.set(globalId, [...currentNewShares, newShare]);
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
            renderOption={(props, option) => {
              const { key, ...optionProps } = props;

              return (
                <Box
                  component="li"
                  key={key}
                  {...optionProps}
                  sx={{
                    opacity: option.isDisabled ? 0.5 : 1,
                    cursor: option.isDisabled ? "not-allowed" : "pointer",
                  }}
                >
                  <Box sx={{ width: "100%" }}>
                    <Typography variant="body2" sx={{ fontWeight: "medium" }}>
                      {option.optionType === "GROUP" ? option.name : `${option.firstName} ${option.lastName}`}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {option.isDisabled
                        ? t("shareDialog.optionDescriptions.alreadyShared", {
                            itemType,
                            recipientName:
                              option.optionType === "GROUP" ? option.name : `${option.firstName} ${option.lastName}`,
                          })
                        : option.optionType === "GROUP"
                          ? t("shareDialog.optionDescriptions.group", {
                              type: option.type,
                              memberCount: option.members?.length || 0,
                            })
                          : t("shareDialog.optionDescriptions.user", {
                              username: option.username,
                              email: option.email,
                            })}
                    </Typography>
                  </Box>
                </Box>
              );
            }}
            renderInput={(params: AutocompleteRenderInputParams) => {
              const { slotProps, ...textFieldProps } = params;
              const { input: inputSlotProps, ...otherSlotProps } = slotProps;

              return (
                <TextField
                  {...textFieldProps}
                  label={t("shareDialog.autocomplete.label")}
                  placeholder={t("shareDialog.autocomplete.placeholder")}
                  size="small"
                  slotProps={{
                    ...otherSlotProps,
                    input: {
                      ...inputSlotProps,
                      endAdornment: (
                        <>
                          {optionsLoading ? <CircularProgress color="inherit" size={20} /> : null}
                          {inputSlotProps.endAdornment}
                        </>
                      ),
                    },
                  }}
                />
              );
            }}
            noOptionsText={t("shareDialog.autocomplete.noMatches")}
            fullWidth
            clearOnBlur
            handleHomeEndKeys
          />
        </Box>

        {isSnippet && (
          <Alert severity="info" sx={{ mb: 2 }}>
            <TransRichText ns="common" i18nKey="shareDialog.snippetsSharedNote" />
          </Alert>
        )}

        {loading ? (
          <Box
            sx={{
              display: "flex",
              justifyContent: "center",
              alignItems: "center",
              minHeight: "200px",
            }}
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
                    const docName = names[0] || t("shareDialog.thisItem", { itemType });
                    const directShares = shareData.get(globalId)?.directShares ?? [];
                    const notebookShares = shareData.get(globalId)?.notebookShares ?? [];
                    const docNewShares = newShares.get(globalId) ?? [];
                    const allDirectShares = [...directShares, ...docNewShares];
                    return (
                      <Box>
                        <Stack spacing={3}>
                          {allDirectShares.length === 0 ? (
                            <Typography variant="body2" color="text.secondary" sx={{ fontStyle: "italic" }}>
                              {t("shareDialog.noDirectShares", { itemType })}
                            </Typography>
                          ) : (
                            <TableContainer component={Paper} variant="outlined">
                              <Table size="small" sx={{ mb: 0 }}>
                                <TableHead>
                                  <TableRow>
                                    <TableCell>{t("shareDialog.columns.sharedWith")}</TableCell>
                                    <TableCell>{t("shareDialog.columns.type")}</TableCell>
                                    <TableCell>{t("shareDialog.columns.permission")}</TableCell>
                                    <TableCell>{t("shareDialog.columns.location")}</TableCell>
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
                                          color={share.recipientType === "USER" ? "primary" : "secondary"}
                                          variant="outlined"
                                        />
                                      </TableCell>
                                      <TableCell>
                                        <FormControl size="small" sx={{ minWidth: 120 }}>
                                          <Select
                                            inputProps={{
                                              "aria-label": t("shareDialog.permissionSelectLabel", {
                                                recipientName: share.recipientName,
                                              }),
                                            }}
                                            value={getCurrentPermission(share)}
                                            onChange={(e) => {
                                              const newValue = e.target.value;
                                              if (!["EDIT", "READ", "UNSHARE"].includes(newValue))
                                                throw new Error("Impossible");
                                              handlePermissionChange(share.shareId.toString(), newValue);
                                            }}
                                            size="small"
                                          >
                                            <MenuItem value="READ">{t("shareDialog.permissions.read")}</MenuItem>
                                            <MenuItem value="EDIT">{t("shareDialog.permissions.edit")}</MenuItem>
                                            <MenuItem value="UNSHARE" sx={{ color: "error.main" }}>
                                              {t("shareDialog.permissions.unshare")}
                                            </MenuItem>
                                          </Select>
                                        </FormControl>
                                      </TableCell>
                                      <TableCell>
                                        {share.recipientType === "USER" ? (
                                          "—"
                                        ) : (
                                          <>
                                            {shareFolderChanges.get(share.shareId.toString())?.name ||
                                              getParentFolderName(share)}
                                            <Button
                                              size="small"
                                              sx={{ ml: 1 }}
                                              onClick={() => {
                                                const groups = shareOptions.filter((opt) => opt.optionType === "GROUP");
                                                const group = groups.find((g) => g.id === share.recipientId);
                                                const sharedFolderId = group
                                                  ? getGroupFolderId(group, isSnippet)
                                                  : null;
                                                if (sharedFolderId) {
                                                  setSelectedShareForFolderChange({
                                                    shareId: share.shareId.toString(),
                                                    groupId: share.recipientId,
                                                    globalId,
                                                    sharedFolderId,
                                                  });
                                                  setFolderSelectionOpen(true);
                                                }
                                              }}
                                            >
                                              {t("shareDialog.changeLocation")}
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
                                        <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
                                          {newShare.recipientType === "USER" ? (
                                            <Typography variant="body2">{newShare.recipientName}</Typography>
                                          ) : (
                                            <Typography
                                              variant="body2"
                                              color="success.dark"
                                              sx={{ fontWeight: "medium" }}
                                            >
                                              {newShare.recipientName}
                                            </Typography>
                                          )}
                                        </Stack>
                                      </TableCell>
                                      <TableCell>
                                        <Chip
                                          size="small"
                                          label={newShare.recipientType}
                                          color={newShare.recipientType === "USER" ? "primary" : "secondary"}
                                          variant="outlined"
                                        />
                                      </TableCell>
                                      <TableCell>
                                        <FormControl size="small" sx={{ minWidth: 120 }}>
                                          <Select
                                            inputProps={{
                                              "aria-label": t("shareDialog.permissionSelectLabel", {
                                                recipientName: newShare.recipientName,
                                              }),
                                            }}
                                            value={newShare.permission}
                                            onChange={(e) => {
                                              const newValue = e.target.value;
                                              if (!["EDIT", "READ", "UNSHARE"].includes(newValue))
                                                throw new Error("Impossible");
                                              handleNewSharePermissionChange(globalId, newShare.id, newValue);
                                            }}
                                            size="small"
                                          >
                                            <MenuItem value="READ">{t("shareDialog.permissions.read")}</MenuItem>
                                            <MenuItem value="EDIT">{t("shareDialog.permissions.edit")}</MenuItem>
                                            <MenuItem value="UNSHARE" sx={{ color: "error.main" }} disabled>
                                              {t("shareDialog.permissions.unshare")}
                                            </MenuItem>
                                          </Select>
                                        </FormControl>
                                      </TableCell>
                                      <TableCell>
                                        {newShare.recipientType === "USER" ? (
                                          "—"
                                        ) : (
                                          <>
                                            <Typography variant="body2" color="text.secondary">
                                              {newShare.locationName || "/"}
                                            </Typography>
                                            <Button
                                              size="small"
                                              sx={{ ml: 1 }}
                                              onClick={() => {
                                                const groups = shareOptions.filter((opt) => opt.optionType === "GROUP");
                                                const group = groups.find((g) => g.id === newShare.recipientId);
                                                const sharedFolderId = group
                                                  ? getGroupFolderId(group, isSnippet)
                                                  : null;
                                                if (sharedFolderId) {
                                                  setSelectedShareForFolderChange({
                                                    shareId: newShare.id,
                                                    groupId: newShare.recipientId,
                                                    globalId,
                                                    sharedFolderId,
                                                  });
                                                  setFolderSelectionOpen(true);
                                                }
                                              }}
                                            >
                                              {t("shareDialog.changeLocation")}
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
                              <Typography variant="body1" component="h3" gutterBottom>
                                {t("shareDialog.notebookShares.heading", { docName })}
                              </Typography>
                              <TableContainer component={Paper} variant="outlined" sx={{ my: 1 }}>
                                <Table size="small" sx={{ mb: 0 }}>
                                  <TableHead>
                                    <TableRow>
                                      <TableCell>{t("shareDialog.columns.notebook")}</TableCell>
                                      <TableCell>{t("shareDialog.columns.sharedWith")}</TableCell>
                                      <TableCell>{t("shareDialog.columns.type")}</TableCell>
                                      <TableCell>{t("shareDialog.columns.permission")}</TableCell>
                                    </TableRow>
                                  </TableHead>
                                  <TableBody>
                                    {notebookShares.map((share) => (
                                      <TableRow key={share.shareId}>
                                        <TableCell>{getParentFolderName(share)}</TableCell>
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
                                            color={share.recipientType === "USER" ? "primary" : "secondary"}
                                            variant="outlined"
                                          />
                                        </TableCell>
                                        <TableCell>
                                          <FormControl size="small" sx={{ minWidth: 120 }} disabled>
                                            <Select
                                              inputProps={{
                                                "aria-label": t("shareDialog.permissionSelectLabel", {
                                                  recipientName: share.recipientName,
                                                }),
                                              }}
                                              value={share.permission}
                                              onChange={() => {
                                                /* Read-only for notebook shares */
                                              }}
                                              size="small"
                                            >
                                              <MenuItem value="READ">{t("shareDialog.permissions.read")}</MenuItem>
                                              <MenuItem value="EDIT">{t("shareDialog.permissions.edit")}</MenuItem>
                                            </Select>
                                          </FormControl>
                                        </TableCell>
                                      </TableRow>
                                    ))}
                                  </TableBody>
                                </Table>
                              </TableContainer>
                              <Typography variant="body1" color="text.secondary">
                                {t("shareDialog.notebookShares.inheritedPermissions")}
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
                  {t("shareDialog.multipleSelection.heading", {
                    count: globalIds.length,
                    itemType,
                  })}
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                  <TransRichText
                    ns="common"
                    i18nKey="shareDialog.multipleSelection.description"
                    values={{ itemType }}
                  />
                </Typography>
                {newShares.size > 0 && (
                  <Box>
                    <Typography variant="h6" gutterBottom>
                      {t("shareDialog.multipleSelection.newSharesHeading")}
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
                        Array.from(newShares.entries()).forEach(([_globalId, docNewShares]) => {
                          docNewShares.forEach((share) => {
                            const key = `${share.recipientType}-${share.recipientId}`;
                            if (uniqueShares.has(key)) {
                              // biome-ignore lint/style/noNonNullAssertion: initial biome migration
                              uniqueShares.get(key)!.documentCount++;
                            } else {
                              uniqueShares.set(key, {
                                share,
                                documentCount: 1,
                              });
                            }
                          });
                        });

                        return Array.from(uniqueShares.values()).map(({ share, documentCount }) => (
                          <Stack
                            direction="row"
                            spacing={2}
                            key={`${share.recipientType}-${share.recipientId}`}
                            sx={{
                              alignItems: "center",
                              p: 2,
                              borderRadius: 1,
                              backgroundColor: "action.hover",
                            }}
                          >
                            <Box sx={{ flexGrow: 1 }}>
                              <Typography variant="body2" sx={{ fontWeight: "medium" }}>
                                {share.recipientName}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                {documentCount === globalIds.length
                                  ? t("shareDialog.multipleSelection.allItems", {
                                      count: globalIds.length,
                                      itemType,
                                    })
                                  : t("shareDialog.multipleSelection.someItems", {
                                      documentCount,
                                      totalCount: globalIds.length,
                                      itemType,
                                    })}
                              </Typography>
                              {share.recipientType === "GROUP" && (
                                <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
                                  {t("shareDialog.locationLabel", { location: share.locationName || "/" })}
                                </Typography>
                              )}
                            </Box>

                            <Chip
                              size="small"
                              label={share.recipientType}
                              color={share.recipientType === "USER" ? "primary" : "secondary"}
                              variant="outlined"
                            />

                            <FormControl size="small" sx={{ minWidth: 80 }}>
                              <Select
                                inputProps={{
                                  "aria-label": t("shareDialog.permissionSelectLabel", {
                                    recipientName: share.recipientName,
                                  }),
                                }}
                                value={share.permission}
                                onChange={(e) => {
                                  const newPermission = e.target.value as string;
                                  if (newPermission === "UNSHARE") {
                                    // Remove this share from all documents
                                    const updatedNewShares = new Map(newShares);
                                    globalIds.forEach((globalId) => {
                                      const docShares = updatedNewShares.get(globalId) || [];
                                      const filteredShares = docShares.filter(
                                        (s) =>
                                          !(
                                            s.recipientType === share.recipientType &&
                                            s.recipientId === share.recipientId
                                          ),
                                      );
                                      if (filteredShares.length === 0) {
                                        updatedNewShares.delete(globalId);
                                      } else {
                                        updatedNewShares.set(globalId, filteredShares);
                                      }
                                    });
                                    setNewShares(updatedNewShares);
                                  } else {
                                    // Update permission for this share across all documents
                                    const updatedNewShares = new Map(newShares);
                                    globalIds.forEach((globalId) => {
                                      const docShares = updatedNewShares.get(globalId) || [];
                                      const updatedDocShares = docShares.map((s) =>
                                        s.recipientType === share.recipientType && s.recipientId === share.recipientId
                                          ? {
                                              ...s,
                                              permission: newPermission as "READ" | "EDIT",
                                            }
                                          : s,
                                      );
                                      updatedNewShares.set(globalId, updatedDocShares);
                                    });
                                    setNewShares(updatedNewShares);
                                  }
                                }}
                                size="small"
                              >
                                <MenuItem value="READ">{t("shareDialog.permissions.read")}</MenuItem>
                                <MenuItem value="EDIT">{t("shareDialog.permissions.edit")}</MenuItem>
                                <MenuItem value="UNSHARE" sx={{ color: "error.main" }}>
                                  {t("shareDialog.permissions.remove")}
                                </MenuItem>
                              </Select>
                            </FormControl>

                            {share.recipientType === "GROUP" && (
                              <Button
                                size="small"
                                onClick={() => {
                                  const groups = shareOptions.filter((opt) => opt.optionType === "GROUP");
                                  const group = groups.find((g) => g.id === share.recipientId);
                                  const sharedFolderId = group ? getGroupFolderId(group, isSnippet) : null;
                                  if (sharedFolderId) {
                                    // Use the first globalId as a placeholder
                                    setSelectedShareForFolderChange({
                                      shareId: share.id,
                                      groupId: share.recipientId,
                                      globalId: globalIds[0],
                                      sharedFolderId,
                                    });
                                    setFolderSelectionOpen(true);
                                  }
                                }}
                              >
                                {t("shareDialog.changeFolder")}
                              </Button>
                            )}
                          </Stack>
                        ));
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
            const isNewShare = docNewShares.some((share) => share.id === shareId);

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
        title={t("shareDialog.selectSharedFolderLocation")}
      />
      <DialogActions>
        {hasChanges && <Button onClick={handleCancel}>{t("actions.cancel")}</Button>}
        <ValidatingSubmitButton loading={saving} onClick={() => void handleSave()} validationResult={validationResult}>
          {hasChanges ? t("actions.save") : t("actions.done")}
        </ValidatingSubmitButton>
      </DialogActions>
    </Dialog>
  );
}
