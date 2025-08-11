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
import Divider from "@mui/material/Divider";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import FormControl from "@mui/material/FormControl";
import Analytics from "../../components/Analytics";
import AnalyticsContext from "../../stores/contexts/Analytics";
import ValidatingSubmitButton from "../../components/ValidatingSubmitButton";
import Result from "../../util/result";
import useShare, { ShareInfo } from "../../hooks/api/useShare";
import useGroups, { Group } from "../../hooks/api/useGroups";
import useUserDetails, { GroupMember } from "../../hooks/api/useUserDetails";
import UserDetails from "../../Inventory/components/UserDetails";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/rspace/workspace";
import VisuallyHiddenHeading from "@/components/VisuallyHiddenHeading";
import Stack from "@mui/material/Stack";

// Combined type for autocomplete options
type ShareOption =
  | (Group & { optionType: "group" })
  | (GroupMember & { optionType: "user" });

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
  const [selectedOption, setSelectedOption] =
    React.useState<ShareOptionWithState | null>(null);
  const [shareOptions, setShareOptions] = React.useState<ShareOption[]>([]);
  const [optionsLoading, setOptionsLoading] = React.useState(false);
  // Track permission changes: Map<shareId, newPermission>
  const [permissionChanges, setPermissionChanges] = React.useState<
    Map<string, string>
  >(new Map());
  const { trackEvent } = React.useContext(AnalyticsContext);
  const { getShareInfoForMultiple } = useShare();
  const { getGroups } = useGroups();
  const { getGroupMembers } = useUserDetails();

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
        .then((data) => {
          setShareData(data);
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
        .then(([groups, groupMembers]) => {
          // Combine groups and users into a single options array
          const groupOptions: ShareOption[] = groups.map((group) => ({
            ...group,
            optionType: "group" as const,
          }));

          const userOptions: ShareOption[] = groupMembers.map((user) => ({
            ...user,
            optionType: "user" as const,
          }));

          setShareOptions([...groupOptions, ...userOptions]);
        })
        .catch((error) => {
          console.error("Failed to fetch share options:", error);
          // Try to fetch just groups as fallback
          getGroups()
            .then((groups) => {
              const groupOptions: ShareOption[] = groups.map((group) => ({
                ...group,
                optionType: "group" as const,
              }));
              setShareOptions(groupOptions);
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
  }, [open, getGroups, getGroupMembers]);

  // Compute share options with disabled state based on current shares
  const shareOptionsWithState: ShareOptionWithState[] = React.useMemo(() => {
    return shareOptions.map((option) => {
      // Count how many documents are already shared with this user/group
      let sharedDocumentCount = 0;

      globalIds.forEach((globalId) => {
        const shares = shareData.get(globalId) || [];
        const isAlreadyShared = shares.some((share) => {
          if (option.optionType === "group") {
            return (
              share.sharedTargetType === "GROUP" &&
              share.sharedTargetId === option.id
            );
          } else {
            return (
              share.sharedTargetType === "USER" &&
              share.sharedTargetId === option.id
            );
          }
        });

        if (isAlreadyShared) {
          sharedDocumentCount++;
        }
      });

      // Only disable if ALL documents are already shared with this option
      const isDisabled =
        sharedDocumentCount === globalIds.length && globalIds.length > 0;

      return {
        ...option,
        isDisabled,
      };
    });
  }, [shareOptions, shareData, globalIds, names]);

  // Check if there are any permission changes
  const hasChanges = permissionChanges.size > 0;

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
    return permissionChanges.get(share.id) || share.permission;
  }

  function handleClose() {
    setOpen(false);
    setGlobalIds([]);
    setNames([]);
    setShareData(new Map());
    setLoading(false);
    setSelectedOption(null);
    setShareOptions([]);
    setOptionsLoading(false);
    setPermissionChanges(new Map());
  }

  function handleCancel() {
    setPermissionChanges(new Map());
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
            value={selectedOption}
            onChange={(event, newValue) => {
              setSelectedOption(newValue);
            }}
            getOptionLabel={(option) => {
              if (option.optionType === "group") {
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
                  ...props.sx,
                  opacity: option.isDisabled ? 0.5 : 1,
                  cursor: option.isDisabled ? "not-allowed" : "pointer",
                }}
              >
                <Box sx={{ width: "100%" }}>
                  <Typography variant="body2" fontWeight="medium">
                    {option.optionType === "group"
                      ? option.name
                      : `${option.firstName} ${option.lastName}`}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {option.isDisabled
                      ? `All of the documents have already been shared with ${
                          option.optionType === "group"
                            ? option.name
                            : `${option.firstName} ${option.lastName}`
                        }`
                      : option.optionType === "group"
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
                const documentName = names[index] || `Document ${globalId}`;

                return (
                  <Box key={globalId} mb={3}>
                    <Typography variant="h6" gutterBottom component="h4">
                      {documentName}
                    </Typography>

                    {shares.length === 0 ? (
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
                                          share.id,
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
                                      UserGroup_SHARED/foo/bar
                                      <Button size="small" sx={{ ml: 1 }}>
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
      <DialogActions>
        {hasChanges && <Button onClick={handleCancel}>Cancel</Button>}
        <ValidatingSubmitButton
          loading={false}
          onClick={() => {
            if (hasChanges) {
              // TODO: Implement saving logic
              console.log("Saving changes:", permissionChanges);
              trackEvent("user:saves:share_changes:workspace");
            } else {
              trackEvent("user:closes:share_dialog:workspace");
            }
            handleClose();
          }}
          validationResult={Result.Ok(null)}
        >
          {hasChanges ? "Save" : "Done"}
        </ValidatingSubmitButton>
      </DialogActions>
    </Dialog>
  );
};
