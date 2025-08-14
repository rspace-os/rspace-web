import React from "react";
import Portal from "@mui/material/Portal";
import ErrorBoundary from "../../components/ErrorBoundary";
import Alerts from "../../components/Alerts/Alerts";
import { Dialog, DialogBoundary } from "../../components/DialogBoundary";
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
import Analytics from "../../components/Analytics";
import AnalyticsContext from "../../stores/contexts/Analytics";
import ValidatingSubmitButton from "../../components/ValidatingSubmitButton";
import Result from "../../util/result";
import useShare, {
  ShareInfo,
  ShareOption,
  NewShare,
} from "../../hooks/api/useShare";
import { doNotAwait } from "../../util/Util";
import useGroups, { Group } from "../../hooks/api/useGroups";
import useUserDetails, { GroupMember } from "../../hooks/api/useUserDetails";
import useWhoAmI from "../../hooks/api/useWhoAmI";
import useFolders from "../../hooks/api/useFolders";
import UserDetails from "../../Inventory/components/UserDetails";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/rspace/workspace";
import VisuallyHiddenHeading from "@/components/VisuallyHiddenHeading";
import Stack from "@mui/material/Stack";
import RsSet, { flattenWithIntersectionWithEq } from "@/util/set";
import WarningBar from "@/components/WarningBar";

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
                <ShareDialog />
              </DialogBoundary>
            </Alerts>
          </Portal>
        </ThemeProvider>
      </ErrorBoundary>
    </Analytics>
  );
}

const ShareDialog = () => {
  const [open, setOpen] = React.useState(false);
  const [globalIds, setGlobalIds] = React.useState<string[]>([]);
  const [names, setNames] = React.useState<string[]>([]);
  const [shareData, setShareData] = React.useState<Map<string, ShareInfo[]>>(
    new Map(),
  );
  const [loading, setLoading] = React.useState(false);
  const [saving, setSaving] = React.useState(false);
  const [shareOptions, setShareOptions] = React.useState<ShareOption[]>([]);
  const [optionsLoading, setOptionsLoading] = React.useState(false);
  // Track permission changes: Map<shareId, newPermission>
  const [permissionChanges, setPermissionChanges] = React.useState<
    Map<string, string>
  >(new Map());
  // Track new shares to be added: Map<globalId, NewShare[]>
  const [newShares, setNewShares] = React.useState<Map<string, NewShare[]>>(
    new Map(),
  );
  // Track folder names for group shares: Map<sharedTargetId, folderName>
  const [groupFolderNames, setGroupFolderNames] = React.useState<
    Map<number, string>
  >(new Map());
  const { trackEvent } = React.useContext(AnalyticsContext);
  const { getShareInfoForMultiple, createShare, deleteShare } = useShare();
  const { getGroups } = useGroups();
  const { getGroupMembers } = useUserDetails();
  const { getFolder } = useFolders();
  const currentUser = useWhoAmI();

  React.useEffect(() => {
    function handler(event: Event) {
      // @ts-expect-error there will be a detail
      const { globalIds, names } = event.detail;
      setOpen(true);
      setGlobalIds(globalIds || []);
      setNames(names || []);
      setShareData(new Map());
    }
    window.addEventListener("OPEN_SHARE_DIALOG", handler);
    return () => {
      window.removeEventListener("OPEN_SHARE_DIALOG", handler);
    };
  }, []);

  React.useEffect(() => {
    if (open && globalIds.length > 0) {
      setLoading(true);
      getShareInfoForMultiple(globalIds)
        .then(async (data) => {
          setShareData(data);

          // Collect all unique group IDs that need folder names
          const groupIds = new Set<number>();
          for (const shares of data.values()) {
            for (const share of shares) {
              if (share.sharedTargetType === "GROUP") {
                groupIds.add(share.sharedTargetId);
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
    type ShareIdentifier = {
      sharedTargetType: ShareInfo["sharedTargetType"];
      sharedTargetId: ShareInfo["sharedTargetId"];
    };
    const eqFunc = (a: ShareIdentifier, b: ShareIdentifier) =>
      a.sharedTargetType === b.sharedTargetType &&
      a.sharedTargetId === b.sharedTargetId;
    const commonShares = flattenWithIntersectionWithEq(
      new RsSet(
        globalIds.map((gId) => {
          const alreadySharedWith = shareData.get(gId) ?? [];
          const toBeSharedWith = newShares.get(gId) ?? [];
          return new RsSet([
            ...alreadySharedWith.map(
              ({ sharedTargetType, sharedTargetId }) => ({
                sharedTargetType,
                sharedTargetId,
              }),
            ),
            ...toBeSharedWith.map(({ sharedTargetType, sharedTargetId }) => ({
              sharedTargetType,
              sharedTargetId,
            })),
          ]);
        }),
      ),
      eqFunc,
    );
    return shareOptions.map(
      (option) => (
        console.debug(option.optionType, option.id),
        {
          ...option,
          isDisabled: commonShares.hasWithEq(
            { sharedTargetType: option.optionType, sharedTargetId: option.id },
            eqFunc,
          ),
        }
      ),
    );
  }, [shareOptions, shareData, newShares, globalIds]);

  // Check if there are any permission changes or new shares
  const hasChanges = permissionChanges.size > 0 || newShares.size > 0;

  // Handle permission change
  function handlePermissionChange(shareId: string, newPermission: string) {
    const newChanges = new Map(permissionChanges);
    if (newPermission === "UNSHARE") {
      newChanges.set(shareId, "UNSHARE");
    } else {
      newChanges.set(shareId, newPermission);
    }
    setPermissionChanges(newChanges);
  }

  // Get the current permission value (either from changes or original)
  function getCurrentPermission(share: ShareInfo): string {
    return permissionChanges.get(share.id.toString()) || share.permission;
  }

  // Handle permission change for new shares
  function handleNewSharePermissionChange(
    globalId: string,
    newShareId: string,
    newPermission: string,
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
          ? { ...share, permission: newPermission as "READ" | "EDIT" }
          : share,
      );
      updatedNewShares.set(globalId, updatedDocNewShares);
    }

    setNewShares(updatedNewShares);
  }

  function handleClose() {
    setOpen(false);
    setGlobalIds([]);
    setNames([]);
    setShareData(new Map());
    setLoading(false);
    setShareOptions([]);
    setOptionsLoading(false);
    setPermissionChanges(new Map());
    setNewShares(new Map());
    setGroupFolderNames(new Map());
  }

  function handleCancel() {
    setPermissionChanges(new Map());
    setNewShares(new Map());
  }

  const validationResult = React.useMemo((): Result<null> => {
    const hasGroupShares = Array.from(newShares.values()).some((shares) =>
      shares.some((share) => share.sharedTargetType === "GROUP"),
    );
    if (hasGroupShares) {
      return Result.Error([new Error("Group sharing is not supported yet")]);
    }
    return Result.Ok(null);
  }, [newShares]);

  async function handleSave() {
    if (!hasChanges) {
      trackEvent("user:closes:share_dialog:workspace");
      handleClose();
      return;
    }

    setSaving(true);
    try {
      const deletionPromises = Array.from(permissionChanges.entries())
        .filter(([_, permission]) => permission === "UNSHARE")
        .map(([shareId, _]) => deleteShare(parseInt(shareId, 10)));

      const creationPromises = Array.from(newShares.entries())
        .map(([globalId, shares]) => ({
          id: parseInt(globalId.replace(/\D/g, ""), 10),
          shares,
        }))
        .filter(({ id, shares }) => !isNaN(id) && shares.length > 0)
        .map(({ id, shares }) => createShare(id, shares));

      await Promise.all([...deletionPromises, ...creationPromises]);

      trackEvent("user:saves:share_changes:workspace");
      setPermissionChanges(new Map());
      setNewShares(new Map());
      setLoading(true);
      const data = await getShareInfoForMultiple(globalIds);
      setShareData(data);

      // Refresh folder names for any new group shares
      const groupIds = new Set<number>();
      for (const shares of data.values()) {
        for (const share of shares) {
          if (share.sharedTargetType === "GROUP") {
            groupIds.add(share.sharedTargetId);
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
        <Box mb={3}>
          <VisuallyHiddenHeading variant="h3">
            Add users or groups to share with
          </VisuallyHiddenHeading>
          <Autocomplete
            options={shareOptionsWithState}
            loading={optionsLoading}
            value={null}
            onChange={(event, newValue) => {
              if (newValue && !newValue.isDisabled) {
                // Add this option to each document's new shares
                const updatedNewShares = new Map(newShares);

                globalIds.forEach((globalId) => {
                  const existingNewShares =
                    updatedNewShares.get(globalId) || [];

                  // Create a new share entry
                  const newShare: NewShare = {
                    id: `new-${newValue.id}-${Date.now()}`, // temporary ID
                    sharedTargetId: newValue.id,
                    sharedTargetName:
                      newValue.optionType === "GROUP"
                        ? newValue.name
                        : newValue.username,
                    sharedTargetDisplayName:
                      newValue.optionType === "GROUP"
                        ? newValue.name
                        : `${newValue.firstName} ${newValue.lastName}`,
                    sharedTargetType: newValue.optionType,
                    permission: "READ", // default permission
                    sharedFolderPath:
                      newValue.optionType === "GROUP"
                        ? groupFolderNames.get(newValue.id) || "Loading..."
                        : null,
                  };

                  updatedNewShares.set(globalId, [
                    ...existingNewShares,
                    newShare,
                  ]);
                });

                setNewShares(updatedNewShares);
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
            <Typography variant="h3" gutterBottom>
              Shared with:
            </Typography>
            <Box>
              {globalIds.map((globalId, index) => {
                const shares = shareData.get(globalId) || [];
                const docNewShares = newShares.get(globalId) || [];
                const documentName = names[index] || `Document ${globalId}`;
                const allShares = [...shares, ...docNewShares];

                return (
                  <Box key={globalId} mb={3}>
                    <Typography variant="h6" gutterBottom component="h4">
                      {documentName}
                    </Typography>

                    {allShares.length === 0 ? (
                      <Typography
                        variant="body2"
                        color="text.secondary"
                        style={{ fontStyle: "italic" }}
                      >
                        This document is not shared with anyone.
                      </Typography>
                    ) : (
                      <TableContainer component={Paper} variant="outlined">
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
                            {shares.map((share) => (
                              <TableRow key={share.id}>
                                <TableCell>
                                  <Box>
                                    {share.sharedTargetType === "USER" ? (
                                      <UserDetails
                                        userId={share.sharedTargetId}
                                        fullName={share.sharedTargetDisplayName}
                                        position={["bottom", "right"]}
                                      />
                                    ) : (
                                      <Link
                                        component="button"
                                        variant="body2"
                                        onClick={(e) => {
                                          e.preventDefault();
                                          e.stopPropagation();
                                          window.open(
                                            `/groups/view/${share.sharedTargetId}`,
                                            "_blank",
                                          );
                                        }}
                                        sx={{
                                          fontWeight: "medium",
                                          textAlign: "left",
                                          textDecoration: "none",
                                          "&:hover": {
                                            textDecoration: "underline",
                                          },
                                        }}
                                      >
                                        {share.sharedTargetDisplayName}
                                      </Link>
                                    )}
                                  </Box>
                                </TableCell>
                                <TableCell>
                                  <Chip
                                    size="small"
                                    label={share.sharedTargetType}
                                    color={
                                      share.sharedTargetType === "USER"
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
                                      value={getCurrentPermission(share)}
                                      onChange={(e) => {
                                        handlePermissionChange(
                                          share.id.toString(),
                                          e.target.value,
                                        );
                                      }}
                                      size="small"
                                    >
                                      <MenuItem value="READ">Read</MenuItem>
                                      <MenuItem value="EDIT">Edit</MenuItem>
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
                                  {share.sharedTargetType === "USER" ? (
                                    <>&mdash;</>
                                  ) : (
                                    <>
                                      {groupFolderNames.get(
                                        share.sharedTargetId,
                                      ) || "Loading..."}
                                      <Button size="small" sx={{ ml: 1 }}>
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
                                    {newShare.sharedTargetType === "USER" ? (
                                      <Typography variant="body2">
                                        {newShare.sharedTargetDisplayName}
                                      </Typography>
                                    ) : (
                                      <Typography
                                        variant="body2"
                                        color="success.dark"
                                        sx={{ fontWeight: "medium" }}
                                      >
                                        {newShare.sharedTargetDisplayName}
                                      </Typography>
                                    )}
                                  </Box>
                                </TableCell>
                                <TableCell>
                                  <Chip
                                    size="small"
                                    label={newShare.sharedTargetType}
                                    color={
                                      newShare.sharedTargetType === "USER"
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
                                      value={newShare.permission}
                                      onChange={(e) => {
                                        handleNewSharePermissionChange(
                                          globalId,
                                          newShare.id,
                                          e.target.value,
                                        );
                                      }}
                                      size="small"
                                    >
                                      <MenuItem value="READ">Read</MenuItem>
                                      <MenuItem value="EDIT">Edit</MenuItem>
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
                                  {newShare.sharedTargetType === "USER" ? (
                                    <>&mdash;</>
                                  ) : (
                                    <Typography
                                      variant="body2"
                                      color="text.secondary"
                                    >
                                      {newShare.sharedFolderPath || "/"}
                                    </Typography>
                                  )}
                                </TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </TableContainer>
                    )}
                  </Box>
                );
              })}

              {globalIds.length === 0 && (
                <Typography variant="body2" color="text.secondary">
                  No documents selected.
                </Typography>
              )}
            </Box>
          </Stack>
        )}
      </DialogContent>
      {hasChanges && <WarningBar />}
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
};
