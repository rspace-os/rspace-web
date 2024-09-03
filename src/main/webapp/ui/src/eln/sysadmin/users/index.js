//@flow

import { ThemeProvider, styled, useTheme } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React, { type Node, type ElementConfig, type Ref } from "react";
import { createRoot } from "react-dom/client";
import ErrorBoundary from "../../../components/ErrorBoundary";
import Portal from "@mui/material/Portal";
import Button from "@mui/material/Button";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import SubmitSpinnerButton from "../../../components/SubmitSpinnerButton";
import Typography from "@mui/material/Typography";
import Grid from "@mui/material/Grid";
import Chip from "@mui/material/Chip";
import AddIcon from "@mui/icons-material/Add";
import SearchIcon from "@mui/icons-material/Search";
import CloseIcon from "@mui/icons-material/Close";
import TagsCombobox from "./TagsCombobox";
import RsSet, { flattenWithIntersection } from "../../../util/set";
import * as ArrayUtils from "../../../util/ArrayUtils";
import { Optional, getByKey } from "../../../util/optional";
import Card from "@mui/material/Card";
import CardHeader from "@mui/material/CardHeader";
import CardContent from "@mui/material/CardContent";
import Box from "@mui/material/Box";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableRow from "@mui/material/TableRow";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import {
  DataGrid,
  GridToolbarContainer,
  GridToolbarColumnsButton,
  GridToolbarDensitySelector,
  GridToolbarExportContainer,
  useGridApiContext,
} from "@mui/x-data-grid";
import TextField from "@mui/material/TextField";
import FilterListIcon from "@mui/icons-material/FilterList";
import ChecklistIcon from "@mui/icons-material/Checklist";
import CustomTooltip from "../../../components/CustomTooltip";
import {
  useUserListing,
  type User,
  type UserListing,
  type UserId,
} from "./useUserListing";
import * as FetchingData from "../../../util/fetchingData";
import { formatFileSize } from "../../../util/files";
import { DataGridColumn, paginationOptions } from "../../../util/table";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import Link from "@mui/material/Link";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemText from "@mui/material/ListItemText";
import Popover from "@mui/material/Popover";
import Switch from "@mui/material/Switch";
import FormControlLabel from "@mui/material/FormControlLabel";
import ExportDialog from "../../../Export/ExportDialog";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import ListItemIcon from "@mui/material/ListItemIcon";
import TagIcon from "@mui/icons-material/LocalOfferOutlined";
import RemoveUserIcon from "@mui/icons-material/PersonRemove";
import DisableIcon from "@mui/icons-material/NoAccounts";
import ExportIcon from "@mui/icons-material/GetApp";
import MakePiIcon from "@mui/icons-material/Badge";
import Result from "../../../util/result";
import TickIcon from "@mui/icons-material/Done";
import CrossIcon from "@mui/icons-material/Clear";
import LockIcon from "@mui/icons-material/Lock";
import ExpandCircleDownIcon from "@mui/icons-material/ExpandCircleDown";
import DialogContentText from "@mui/material/DialogContentText";
import { doNotAwait, sleep } from "../../../util/Util";
import Grow from "@mui/material/Grow";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import Dialog from "@mui/material/Dialog";
import Alerts from "../../../components/Alerts/Alerts";
import Badge from "@mui/material/Badge";
import docLinks from "../../../assets/DocLinks";
import createAccentedTheme from "../../../accentedTheme";
import UserAliasIcon from "@mui/icons-material/ContactEmergency";
import { useDeploymentProperty } from "../../useDeploymentProperty";
import * as Parsers from "../../../util/parsers";

const Panel = ({
  anchorEl,
  children,
  onClose,
  ariaLabel,
}: {
  anchorEl: ?HTMLElement,
  children: Node,
  onClose: () => void,
  ariaLabel: string,
}) => (
  <Popover
    open={Boolean(anchorEl)}
    anchorEl={anchorEl}
    onClose={onClose}
    anchorOrigin={{
      vertical: "bottom",
      horizontal: "left",
    }}
    transformOrigin={{
      vertical: "top",
      horizontal: "left",
    }}
    PaperProps={{
      elevation: 2,
      style: {
        maxWidth: 400,
      },
      role: "dialog",
      "aria-label": ariaLabel,
    }}
  >
    {Boolean(anchorEl) && children}
  </Popover>
);

const CustomGrow = React.forwardRef<ElementConfig<typeof Grow>, {||}>(
  (props: ElementConfig<typeof Grow>, ref: Ref<typeof Grow>) => (
    <Grow
      {...props}
      ref={ref}
      timeout={
        window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 200
      }
      style={{
        transformOrigin: "center 70%",
      }}
    />
  )
);
CustomGrow.displayName = "CustomGrow";

const TagDialog = ({
  selectedUsers,
  open,
  onClose,
  setTags,
}: {|
  selectedUsers: $ReadOnlyArray<User>,
  open: boolean,
  onClose: () => void,
  setTags: (Array<string>, Array<string>) => Promise<void>,
|}) => {
  const { addAlert } = React.useContext(AlertContext);
  const [commonTags, setCommonTags] = React.useState<RsSet<string>>(
    new RsSet([])
  );
  const [addedTags, setAddedTags] = React.useState<Array<string>>([]);
  const [deletedTags, setDeletedTags] = React.useState<Array<string>>([]);
  const [submitting, setSubmitting] = React.useState(false);

  const visibleTags = React.useMemo(() => {
    return [
      ...new RsSet(addedTags)
        .union(commonTags)
        .subtract(new RsSet(deletedTags)),
    ];
  }, [commonTags, addedTags, deletedTags]);

  const [anchorEl, setAnchorEl] = React.useState(null);

  React.useEffect(() => {
    setCommonTags(
      flattenWithIntersection(
        new RsSet(selectedUsers.map((user) => new RsSet(user.tags)))
      )
    );
  }, [selectedUsers]);

  return (
    <Portal>
      <Dialog open={open} onClose={onClose}>
        <DialogTitle component="h3">
          Tagging {selectedUsers.length} user
          {(selectedUsers.length ?? 0) > 1 && "s"}
        </DialogTitle>
        <DialogContent>
          <Grid container direction="column" spacing={2}>
            <Grid item>
              <Typography variant="body2">
                You can tag users to categorise them, and filter users by tag.
                These tags are only visible to System Admins and Community
                Admins. If you&apos;ve selected several users, only shared tags
                will be shown.{" "}
                <Link
                  target="_blank"
                  rel="noreferrer"
                  href={docLinks.taggingUsers}
                >
                  Read more about tagging users here.
                </Link>{" "}
              </Typography>
            </Grid>
            <Grid item>
              <Grid container direction="row" spacing={1}>
                {visibleTags.map((tag) => (
                  <Grid item key={tag}>
                    <Chip
                      label={tag}
                      variant="outlined"
                      onDelete={() => {
                        setDeletedTags([...deletedTags, tag]);
                        setAddedTags(addedTags.filter((aTag) => aTag !== tag));
                      }}
                    />
                  </Grid>
                ))}
                <Grid item>
                  <Chip
                    icon={<AddIcon />}
                    label="Add Tag"
                    color="primary"
                    skipFocusWhenDisabled
                    onClick={(e) => {
                      setAnchorEl(e.target);
                    }}
                  />
                  <TagsCombobox
                    value={new RsSet(visibleTags)}
                    anchorEl={anchorEl}
                    onSelection={(newTag) => {
                      if (!addedTags.includes(newTag))
                        setAddedTags([...addedTags, newTag]);
                      setDeletedTags(
                        deletedTags.filter((dTag) => dTag !== newTag)
                      );
                    }}
                    onClose={() => {
                      setAnchorEl(null);
                    }}
                  />
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <SubmitSpinnerButton
            disabled={addedTags.length === 0 && deletedTags.length === 0}
            loading={submitting}
            onClick={doNotAwait(async () => {
              setSubmitting(true);
              try {
                await setTags(addedTags, deletedTags);
                addAlert(
                  mkAlert({
                    message: "Successfully saved tags.",
                    variant: "success",
                  })
                );
                onClose();
              } catch (error) {
                console.error(error);
                addAlert(
                  mkAlert({
                    title: "Could not save tags.",
                    message: error.message,
                    variant: "error",
                  })
                );
              } finally {
                setSubmitting(false);
              }
            })}
            label="Save"
          />
        </DialogActions>
      </Dialog>
    </Portal>
  );
};

const StyledCard = styled(Card)(({ theme }) => ({
  "&.MuiPaper-root": {
    border: theme.borders.themedDialog(200, 90, 20),
    borderRadius: 6,
    position: "relative",
  },
}));

const StyledCardHeader = styled(CardHeader)(({ theme }) => ({
  borderBottom: theme.borders.themedDialogTitle(200, 90, 20),
  paddingTop: theme.spacing(1.5),
  paddingBottom: theme.spacing(1.5),
}));

const StyledCardContent = styled(CardContent)(() => ({
  "&:last-child": {
    paddingBottom: 0,
  },
}));

const SummaryInfoCard = styled(Card)(({ theme }) => ({
  border: theme.borders.themedDialogTitle(200, 90, 20),
  boxShadow: "none",
}));

const StyledTableCell = styled(TableCell)(({ theme }) => ({
  padding: "4px 12px",
  backgroundColor: "#edf7fc",
  borderBottom: theme.borders.themedDialogTitle(200, 90, 20),
}));

const Abbr = styled("abbr")(({ theme }) => ({
  textDecorationStyle: "dashed",
  textDecorationColor: theme.palette.standardIcon,
  textDecorationThickness: "2px",
  textDecorationLine: "underline",
}));

const StyledSearchIcon = styled(SearchIcon)(({ theme }) => ({
  color: theme.palette.standardIcon.main,
  height: 20,
  width: 20,
}));

const StyledCloseIcon = styled(CloseIcon)(({ theme }) => ({
  color: theme.palette.standardIcon.main,
  height: 20,
  width: 20,
}));

const StyledTextField = styled(TextField)(() => ({
  width: "253px",
  "& .MuiOutlinedInput-root": {
    height: "32px",
  },
}));

const ChipWithMenu = styled(Chip)(({ theme }) => ({
  backgroundColor: "transparent",
  border: "2px solid #94a7b2",
  color: theme.palette.standardIcon.main,
  textTransform: "capitalize",
  letterSpacing: "0.04em",
  "& .MuiChip-deleteIcon": {
    color: "#94a7b2",
    marginRight: 0,
  },
}));

/**
 * This is the action that can be performed on a single selected user to either
 * grant them the PI role, if they are not currently a PI, or to revoke the
 * role if they currently are.
 */
const PiAction = ({
  selectedUser,
  setActionsAnchorEl,
  autoFocus,
}: {|
  selectedUser: Result<User>,
  setActionsAnchorEl: (null) => void,
  autoFocus: boolean,
|}) => {
  const [open, setOpen] = React.useState(false);
  const [password, setPassword] = React.useState("");
  const { addAlert } = React.useContext(AlertContext);

  const allowedPiAction: Result<{| user: User, action: "revoke" | "grant" |}> =
    selectedUser
      .mapError(() => new Error("Only one user can be modified at a time."))
      .flatMap((user) => {
        if (user.isPi) return Result.Ok({ user, action: "revoke" });
        if (user.isRegularUser) return Result.Ok({ user, action: "grant" });
        return Result.Error([new Error("The selected user is an admin.")]);
      });

  return (
    <>
      <MenuItem
        //eslint-disable-next-line
        autoFocus={autoFocus}
        disabled={allowedPiAction.isError}
        onClick={() => {
          setOpen(true);
        }}
      >
        <ListItemIcon>
          <MakePiIcon />
        </ListItemIcon>
        <ListItemText
          primary={allowedPiAction
            .map(({ action }) =>
              action === "grant" ? "Grant PI role" : "Revoke PI role"
            )
            .orElse("Grant PI role")}
          secondary={allowedPiAction
            .map(() => null)
            .orElseGet(([error]) => error.message)}
          secondaryTypographyProps={{
            style: {
              whiteSpace: "break-spaces",
            },
          }}
        />
      </MenuItem>
      <EventBoundary>
        {allowedPiAction
          .map(({ action, user }) => (
            <Dialog
              key={null}
              open={open}
              onClose={() => {
                setOpen(false);
                setActionsAnchorEl(null);
              }}
            >
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  void (async () => {
                    if (action === "grant") {
                      try {
                        await user.grantPiRole(password);
                        addAlert(
                          mkAlert({
                            variant: "success",
                            message: "Successfully granted PI role to user.",
                          })
                        );
                        setOpen(false);
                        setActionsAnchorEl(null);
                      } catch (error) {
                        addAlert(
                          mkAlert({
                            title: "Could not grant PI role to user.",
                            message: error.message,
                            variant: "error",
                          })
                        );
                      }
                    }
                    if (action === "revoke") {
                      try {
                        await user.revokePiRole(password);
                        addAlert(
                          mkAlert({
                            variant: "success",
                            message: "Successfully revoked PI role from user.",
                          })
                        );
                        setOpen(false);
                        setActionsAnchorEl(null);
                      } catch (error) {
                        addAlert(
                          mkAlert({
                            title: "Could not revoke user's PI role.",
                            message: error.message,
                            variant: "error",
                          })
                        );
                      }
                    }
                  })();
                }}
              >
                <DialogTitle>
                  {action === "grant"
                    ? "Grant user PI role"
                    : "Revoke PI role from user"}
                </DialogTitle>
                <DialogContent>
                  <DialogContentText variant="body2" sx={{ mb: 2 }}>
                    To{" "}
                    {action === "grant"
                      ? "grant the PI role to"
                      : "revoke the PI role from"}{" "}
                    <strong>
                      {selectedUser.map((u) => u.fullName).orElse("")}
                    </strong>{" "}
                    please re-enter your password.
                  </DialogContentText>
                  <TextField
                    type="password"
                    autoComplete="current-password"
                    size="small"
                    label="Password"
                    value={password}
                    onChange={(e) => {
                      setPassword(e.target.value);
                    }}
                    fullWidth
                  />
                </DialogContent>
                <DialogActions>
                  <Button
                    onClick={() => {
                      setPassword("");
                      setOpen(false);
                      setActionsAnchorEl(null);
                    }}
                  >
                    Cancel
                  </Button>
                  <SubmitSpinnerButton
                    type="submit"
                    loading={false}
                    disabled={false}
                    label={action === "grant" ? "Grant" : "Revoke"}
                  />
                </DialogActions>
              </form>
            </Dialog>
          ))
          .orElse(null)}
      </EventBoundary>
    </>
  );
};

const SetUsernamAliasAction = ({
  selectedUser,
  setActionsAnchorEl,
}: {|
  selectedUser: Result<User>,
  setActionsAnchorEl: (null) => void,
|}) => {
  const { addAlert } = React.useContext(AlertContext);
  const [open, setOpen] = React.useState(false);
  const [alias, setAlias] = React.useState("");

  const allowedToSetAlias: Result<User> = selectedUser.mapError(
    () => new Error("Only one user can have an alias set at a time.")
  );

  return (
    <>
      <MenuItem
        disabled={allowedToSetAlias.isError}
        onClick={() => {
          setOpen(true);
        }}
      >
        <ListItemIcon>
          <UserAliasIcon />
        </ListItemIcon>
        <ListItemText
          primary="Set Username Alias"
          secondary={allowedToSetAlias
            .map(() => null)
            .orElseGet(([error]) => error.message)}
          secondaryTypographyProps={{
            style: {
              whiteSpace: "break-spaces",
            },
          }}
        />
      </MenuItem>
      {allowedToSetAlias
        .map((user) => (
          <Dialog
            key={null}
            open={open}
            onClose={() => {
              setOpen(false);
              setActionsAnchorEl(null);
            }}
          >
            <form
              onSubmit={(e) => {
                e.preventDefault();
                void (async () => {
                  try {
                    await user.setAlias(alias);
                    addAlert(
                      mkAlert({
                        variant: "success",
                        message: "Successfully set username alias.",
                      })
                    );
                    setOpen(false);
                    setActionsAnchorEl(null);
                  } catch (error) {
                    addAlert(
                      mkAlert({
                        title: "Could not set username alias.",
                        message: error.message,
                        variant: "error",
                      })
                    );
                  }
                })();
              }}
            >
              <DialogTitle>Set Username Alias</DialogTitle>
              <DialogContent>
                <DialogContentText variant="body2" sx={{ mb: 2 }}>
                  <Typography variant="body2">
                    SysAdmins can set a username alias for a user, enabling both
                    the username and its alias to be used during login. The
                    alias is only recognised during the login process, and does
                    not replace the user’s username as their main identifier
                    inside RSpace.
                  </Typography>
                </DialogContentText>
                <TextField
                  size="small"
                  label="Username Alias"
                  value={alias}
                  onChange={(e) => {
                    setAlias(e.target.value);
                  }}
                  fullWidth
                />
              </DialogContent>
              <DialogActions>
                <Button
                  onClick={() => {
                    setAlias("");
                    setOpen(false);
                    setActionsAnchorEl(null);
                  }}
                >
                  Cancel
                </Button>
                <SubmitSpinnerButton
                  type="submit"
                  loading={false}
                  disabled={false}
                  label="Set Alias"
                />
              </DialogActions>
            </form>
          </Dialog>
        ))
        .orElse(null)}
    </>
  );
};

const DeleteAction = ({
  selectedUser,
  setActionsAnchorEl,
}: {|
  selectedUser: Result<User>,
  setActionsAnchorEl: (null) => void,
|}) => {
  const { addAlert } = React.useContext(AlertContext);
  const [open, setOpen] = React.useState(false);
  const [username, setUsername] = React.useState("");
  const canDelete = useDeploymentProperty("sysadmin.delete.user");

  const allowedToDelete: Result<User> = FetchingData.getSuccessValue(canDelete)
    .flatMap(Parsers.isBoolean)
    .flatMap(Parsers.isTrue)
    .mapError(
      () =>
        new Error('The deployment property "sysadmin.delete.user" is false.')
    )
    .flatMap(() =>
      selectedUser.mapError(
        () => new Error("Only one user can be deleted at a time.")
      )
    );

  return (
    <>
      <MenuItem
        disabled={allowedToDelete.isError}
        onClick={() => {
          setOpen(true);
        }}
      >
        <ListItemIcon>
          <RemoveUserIcon />
        </ListItemIcon>
        <ListItemText
          primary="Delete"
          secondary={allowedToDelete
            .map(() => null)
            .orElseGet(([error]) => error.message)}
          secondaryTypographyProps={{
            style: {
              whiteSpace: "break-spaces",
            },
          }}
        />
      </MenuItem>
      {allowedToDelete
        .map((user) => (
          <Dialog
            key={null}
            open={open}
            onClose={() => {
              setOpen(false);
              setActionsAnchorEl(null);
            }}
          >
            <form
              onSubmit={(e) => {
                e.preventDefault();
                void (async () => {
                  try {
                    if (username !== user.username)
                      throw new Error("Usernames do not match.");
                    await user.delete();
                    addAlert(
                      mkAlert({
                        variant: "success",
                        message: "Successfully deleted user's account.",
                      })
                    );
                    setOpen(false);
                    setActionsAnchorEl(null);
                  } catch (error) {
                    addAlert(
                      mkAlert({
                        title: "Could not delete user's account.",
                        message: error.message,
                        variant: "error",
                      })
                    );
                  }
                })();
              }}
            >
              <DialogTitle>Deletion Confirmation</DialogTitle>
              <DialogContent>
                <DialogContentText variant="body2" sx={{ mb: 2 }}>
                  <Typography variant="body2" sx={{ mb: 1 }}>
                    User deletion is irreversible, and all documents will be
                    deleted.
                  </Typography>
                  {user.hasFormsUsedByOtherUsers && (
                    <Typography variant="body2" sx={{ mb: 1 }}>
                      The user you are trying to delete is{" "}
                      <strong>
                        the owner of Forms that are used by other users.
                      </strong>
                      To ensure continued access to these Forms, the system
                      <strong> will transfer ownership</strong> of the Forms to
                      <strong> this System Administrator</strong> account. Forms
                      that are not used by others will be deleted.
                    </Typography>
                  )}
                  <Typography variant="body2" sx={{ mb: 1 }}>
                    An XML archive will be made of the user&apos;s work which
                    will be available for a short time on the server.
                  </Typography>
                  <Typography variant="body2" sx={{ mb: 1 }}>
                    To delete{" "}
                    <strong>
                      {selectedUser.map((u) => u.fullName).orElse("")}
                    </strong>
                    &apos;s account please enter their username.
                  </Typography>
                </DialogContentText>
                <TextField
                  size="small"
                  label="Username"
                  value={username}
                  onChange={(e) => {
                    setUsername(e.target.value);
                  }}
                  fullWidth
                />
              </DialogContent>
              <DialogActions>
                <Button
                  onClick={() => {
                    setUsername("");
                    setOpen(false);
                    setActionsAnchorEl(null);
                  }}
                >
                  Cancel
                </Button>
                <SubmitSpinnerButton
                  type="submit"
                  loading={false}
                  disabled={false}
                  label={
                    user.hasFormsUsedByOtherUsers
                      ? "Transfer Forms And Delete"
                      : "Delete"
                  }
                />
              </DialogActions>
            </form>
          </Dialog>
        ))
        .orElse(null)}
    </>
  );
};

const SelectionActions = ({
  selectedIds,
  fetchedListing,
}: {|
  selectedIds: $ReadOnlyArray<UserId>,
  fetchedListing: FetchingData.Fetched<UserListing>,
|}) => {
  const { addAlert } = React.useContext(AlertContext);
  const [exportDialogOpen, setExportDialogOpen] = React.useState(false);
  const [actionsAnchorEl, setActionsAnchorEl] = React.useState(null);
  const [tagDialogOpen, setTagDialogOpen] = React.useState(false);

  const exportAllowed =
    selectedIds.length === 1
      ? Result.Ok(null)
      : Result.Error([
          new Error("Only one user's work can be exported at a time."),
        ]);

  const selectedUsers: Result<$ReadOnlyArray<User>> =
    selectedIds.length === 0
      ? Result.Error([new Error("No users selected")])
      : Result.all(
          ...selectedIds.map((id) =>
            FetchingData.getSuccessValue(fetchedListing).flatMap((listing) =>
              listing.getById(id)
            )
          )
        );

  const selectedUser: Result<User> = ArrayUtils.getAt(0, selectedIds)
    .toResult(() => new Error("selectedIds is empty"))
    .flatMap((id) =>
      selectedIds.length > 1
        ? Result.Error<UserId>([new Error("More than one user is selected")])
        : Result.Ok(id)
    )
    .flatMap((id) =>
      FetchingData.getSuccessValue(fetchedListing).flatMap((listing) =>
        listing.getById(id)
      )
    );

  const enableDisableAction: Result<{|
    user: User,
    action: "enable" | "disable",
  |}> = selectedUser
    .mapError(
      () => new Error("Only one user can be enabled / disabled at a time.")
    )
    .map((user) =>
      user.enabled ? { user, action: "disable" } : { user, action: "enable" }
    );

  const unlockAction: Result<User> = selectedUser
    .mapError(() => new Error("Only one user can be unlocked at a time."))
    .flatMap((user) =>
      user.locked
        ? Result.Ok(user)
        : Result.Error([new Error("Only locked accounts can be unlocked.")])
    );

  return (
    <>
      {FetchingData.match(fetchedListing, {
        loading: () => <></>,
        error: () => <></>,
        success: (listing) => (
          <Grid
            container
            direction="row"
            sx={{ width: 598, alignItems: "center", mb: 0.5 }}
            spacing={1}
          >
            <Grid item>
              <Button
                variant="outlined"
                color="primary"
                disabled={selectedIds.length === 0}
                sx={{
                  // synchronises the colour change with the adjacent label change
                  transition: "none",
                }}
                startIcon={<ChecklistIcon />}
                onClick={(e) => {
                  setActionsAnchorEl(e.target);
                }}
                aria-label="Actions menu for selected rows"
                aria-haspopup="menu"
                aria-expanded={Boolean(actionsAnchorEl)}
              >
                Actions
              </Button>
              <Menu
                open={Boolean(actionsAnchorEl)}
                anchorEl={actionsAnchorEl}
                onClose={() => {
                  setActionsAnchorEl(null);
                }}
                elevation={2}
              >
                <PiAction
                  /*
                   * MUI Menus are not supposed to contain components that
                   * render MenuItems; the MenuItems are intended to be direct
                   * children in the component tree. As such, we have to
                   * autoFocus the first MenuItem ourselves so that selecting a
                   * MenuItem with the arrow keys remains possible. See
                   * ../../../../../QuirksOfMaterialUi.md, section
                   * "Custom components that wrap `MenuItem`s"
                   */
                  //eslint-disable-next-line
                  autoFocus
                  selectedUser={selectedUser}
                  setActionsAnchorEl={setActionsAnchorEl}
                />
                <MenuItem
                  onClick={() => {
                    setTagDialogOpen(true);
                  }}
                >
                  <ListItemIcon>
                    <TagIcon />
                  </ListItemIcon>
                  <ListItemText primary="Add/Remove Tags" />
                </MenuItem>
                <EventBoundary>
                  {selectedUsers
                    .map((users) => (
                      <TagDialog
                        key={null}
                        selectedUsers={users}
                        open={Boolean(tagDialogOpen)}
                        onClose={() => {
                          setTagDialogOpen(false);
                          setActionsAnchorEl(null);
                        }}
                        setTags={(addedTags, deletedTags) =>
                          listing.setTags(users, addedTags, deletedTags)
                        }
                      />
                    ))
                    .orElse(null)}
                </EventBoundary>
                <MenuItem
                  disabled={exportAllowed.isError}
                  onClick={() => {
                    setExportDialogOpen(true);
                    setActionsAnchorEl(null);
                  }}
                >
                  <ListItemIcon>
                    <ExportIcon />
                  </ListItemIcon>
                  <ListItemText
                    primary="Export Work"
                    secondary={exportAllowed.orElseGet(
                      ([error]) => error.message
                    )}
                    secondaryTypographyProps={{
                      style: {
                        whiteSpace: "break-spaces",
                      },
                    }}
                  />
                </MenuItem>
                <MenuItem
                  disabled={unlockAction.isError}
                  onClick={() => {
                    unlockAction.do(
                      doNotAwait(async (user) => {
                        try {
                          await user.unlock();
                          addAlert(
                            mkAlert({
                              variant: "success",
                              message: "Successfully unlocked account.",
                            })
                          );
                          setActionsAnchorEl(null);
                        } catch (error) {
                          addAlert(
                            mkAlert({
                              title: "Could not unlock account.",
                              message: error.message,
                              variant: "error",
                            })
                          );
                        }
                      })
                    );
                  }}
                >
                  <ListItemIcon>
                    <LockIcon />
                  </ListItemIcon>
                  <ListItemText
                    primary="Unlock"
                    secondary={unlockAction
                      .map(() => null)
                      .orElseGet(([error]) => error.message)}
                    secondaryTypographyProps={{
                      style: {
                        whiteSpace: "break-spaces",
                      },
                    }}
                  />
                </MenuItem>
                <EventBoundary>
                  <SetUsernamAliasAction
                    selectedUser={selectedUser}
                    setActionsAnchorEl={setActionsAnchorEl}
                  />
                </EventBoundary>
                <MenuItem
                  disabled={enableDisableAction.isError}
                  onClick={() =>
                    enableDisableAction.do(
                      doNotAwait(async ({ action, user }) => {
                        if (action === "enable") {
                          try {
                            await user.enable();
                            addAlert(
                              mkAlert({
                                variant: "success",
                                message: "Successfully enabled account.",
                              })
                            );
                            setActionsAnchorEl(null);
                          } catch (error) {
                            addAlert(
                              mkAlert({
                                title: "Could not enable account.",
                                message: error.message,
                                variant: "error",
                              })
                            );
                          }
                        }
                        if (action === "disable") {
                          try {
                            await user.disable();
                            addAlert(
                              mkAlert({
                                variant: "success",
                                message: "Successfully disabled account.",
                              })
                            );
                            setActionsAnchorEl(null);
                          } catch (error) {
                            addAlert(
                              mkAlert({
                                title: "Could not disable account.",
                                message: error.message,
                                variant: "error",
                              })
                            );
                          }
                        }
                      })
                    )
                  }
                >
                  <ListItemIcon>
                    <DisableIcon />
                  </ListItemIcon>
                  <ListItemText
                    primary={enableDisableAction
                      .map(({ action }) =>
                        action === "disable" ? "Disable" : "Enable"
                      )
                      .orElse("Enable / Disable")}
                    secondary={enableDisableAction
                      .map(() => null)
                      .orElseGet(([error]) => error.message)}
                    secondaryTypographyProps={{
                      style: {
                        whiteSpace: "break-spaces",
                      },
                    }}
                  />
                </MenuItem>
                <EventBoundary>
                  <DeleteAction
                    selectedUser={selectedUser}
                    setActionsAnchorEl={setActionsAnchorEl}
                  />
                </EventBoundary>
              </Menu>
            </Grid>
            <Grid item>
              <Typography
                variant="body2"
                sx={{
                  ml: 0.5,
                  p: 0,
                  color: selectedIds.length > 0 ? "initial" : "grey",
                }}
              >
                {selectedIds.length > 0
                  ? `${selectedIds.length} user${
                      selectedIds.length > 1 ? "s" : ""
                    } selected`
                  : "No selection"}
              </Typography>
            </Grid>
          </Grid>
        ),
      })}
      <ExportDialog
        open={exportDialogOpen}
        onClose={() => setExportDialogOpen(false)}
        exportSelection={{
          type: "user",
          username: selectedUser.map((user) => user.username).orElse(""),
          exportIds: [],
        }}
        //eslint-disable-next-line
        allowFileStores={RS.newFileStoresExportEnabled}
      />
    </>
  );
};

/*
 * All of the DOM events that happen inside of components that are themselves
 * inside menu items, such as events within a dialog, shouln't propagate
 * outside as the menu will take them to be events that it should
 * respond to by providing keyboard navigation. See
 * ../../../../QuirksOfMaterialUi.md, secion "Dialogs inside Menus", for more
 * information.
 */
const EventBoundary = ({ children }: { children: Node }) => (
  // eslint-disable-next-line
  <div
    onKeyDown={(e) => {
      e.stopPropagation();
    }}
    onMouseDown={(e) => {
      e.stopPropagation();
    }}
    onClick={(e) => {
      e.stopPropagation();
    }}
  >
    {children}
  </div>
);

const ExportMenuItem = ({
  onClick,
  children,
  ...rest
}: {|
  onClick: () => Promise<void>,
  children: Node,
|}) => (
  <MenuItem
    onClick={doNotAwait(async () => {
      await onClick();
      /*
       * `hideMenu` is injected by MUI into the children of
       * `GridToolbarExportContainer`. See
       * https://github.com/mui/mui-x/blob/2414dcfe87b8bd4507361a80ab43c8d284ddc4de/packages/x-data-grid/src/components/toolbar/GridToolbarExportContainer.tsx#L99
       * However, if we add `hideMenu` to the type of the `ExportMenuItem`
       * props then Flow will complain we're not passing it in at the call site
       */
      getByKey<"hideMenu", () => void>("hideMenu", rest).do((hideMenu) => {
        hideMenu();
      });
    })}
  >
    {children}
  </MenuItem>
);

const Toolbar = ({
  userListing,
  setColumnsMenuAnchorEl,
  rowSelectionModel,
}: {|
  userListing: FetchingData.Fetched<UserListing>,
  setColumnsMenuAnchorEl: (HTMLElement) => void,
  rowSelectionModel: $ReadOnlyArray<UserId>,
|}) => {
  const [filterAnchorEl, setFilterAnchorEl] = React.useState(null);
  const [tagsComboboxAnchorEl, setTagsComboboxAnchorEl] = React.useState(null);
  const [tagsChecked, setTagsChecked] = React.useState(false);
  const [tags, setTags] = React.useState<Array<string>>([]);

  /**
   * The columns menu can be opened by either tapping the "Columns" toolbar
   * button or by tapping the "Manage columns" menu item in each column's menu,
   * logic that is handled my MUI. We provide a custom `anchorEl` so that the
   * menu is positioned beneath the "Columns" toolbar button to be consistent
   * with the other toolbar menus, otherwise is appears far to the left. Rather
   * than having to hook into the logic that triggers the opening of the
   * columns menu in both places, we just set the `anchorEl` pre-emptively.
   */
  const columnMenuRef = React.useRef();
  React.useEffect(() => {
    if (columnMenuRef.current) setColumnsMenuAnchorEl(columnMenuRef.current);
  }, [setColumnsMenuAnchorEl]);

  const apiRef = useGridApiContext();

  /*
   * This is a bit of hack. The MUI DataGrid component under the MIT license is
   * restricted to displaying 100 rows. This export menu item downloads the
   * full listing of users, exports it as a CSV file, and then returns the
   * table to a valid state.
   */
  const exportAllRows = async () => {
    let priorSearchParameters;
    const newListing: UserListing = await FetchingData.match(userListing, {
      loading: () => Promise.reject(new Error("loading")),
      error: () => Promise.reject(new Error("error")),
      success: (listing) => {
        priorSearchParameters = listing.getSearchParameters();
        return listing.allUsers();
      },
    });
    await sleep(2000); // wait for the table to be re-rendered
    apiRef.current?.exportDataAsCsv({
      getRowsToExport: () => newListing.users.map((u) => u.id),
      allColumns: true,
    });
    await sleep(2000); // wait for download to be done
    if (priorSearchParameters) {
      await newListing.setSearchParameters(priorSearchParameters);
    }
  };

  const exportVisibleRows = () => {
    apiRef.current?.exportDataAsCsv({
      allColumns: true,
    });
  };

  return (
    <GridToolbarContainer sx={{ width: "100%" }}>
      <SearchBox userListing={userListing} />
      <Badge badgeContent={tagsChecked ? tags.length : null} color="primary">
        <Button
          variant="outlined"
          color={tagsChecked && tags.length > 0 ? "primary" : "standardIcon"}
          startIcon={<FilterListIcon />}
          onClick={(e) => {
            setFilterAnchorEl(e.target);
          }}
          aria-label="Filter users"
          aria-haspopup="dialog"
          aria-expanded={Boolean(filterAnchorEl)}
        >
          Filters
        </Button>
      </Badge>
      <Panel
        anchorEl={filterAnchorEl}
        onClose={() => {
          setFilterAnchorEl(null);
        }}
        ariaLabel="Filters"
      >
        <Card>
          <CardContent sx={{ pt: 1 }}>
            <Grid container direction="column" spacing={2}>
              <Grid item container direction="column" spacing={1}>
                <Grid item>
                  <FormControlLabel
                    control={
                      <Switch
                        size="small"
                        checked={tagsChecked}
                        onChange={(event) => {
                          setTagsChecked(event.target.checked);
                          FetchingData.getSuccessValue(userListing).do(
                            (listing) => {
                              void listing.applyTagsFilter(
                                event.target.checked ? tags : []
                              );
                            }
                          );
                        }}
                      />
                    }
                    label="Tags"
                  />
                </Grid>
                <Grid
                  item
                  container
                  direction="row"
                  spacing={1}
                  sx={{ flexWrap: "nowrap" }}
                >
                  <Grid item sx={{ width: 30 }}></Grid>
                  <Grid item container direction="row" spacing={1}>
                    {tags.map((tag) => (
                      <Grid item key={tag}>
                        <Chip
                          label={tag}
                          variant="outlined"
                          onDelete={() => {
                            const newTags = tags.filter((t) => t !== tag);
                            setTags(newTags);
                            FetchingData.getSuccessValue(userListing).do(
                              (listing) => {
                                void listing.applyTagsFilter(newTags);
                              }
                            );
                          }}
                          disabled={!tagsChecked}
                        />
                      </Grid>
                    ))}
                    <Grid item>
                      <Chip
                        icon={<AddIcon />}
                        label="Add Tag"
                        color="primary"
                        skipFocusWhenDisabled
                        onClick={(e) => {
                          setTagsComboboxAnchorEl(e.target);
                        }}
                        disabled={!tagsChecked}
                      />
                      <TagsCombobox
                        value={new RsSet(tags)}
                        anchorEl={tagsComboboxAnchorEl}
                        onSelection={(newTag) => {
                          if (!tags.includes(newTag)) {
                            const newTags = [...tags, newTag];
                            setTags(newTags);
                            FetchingData.getSuccessValue(userListing).do(
                              (listing) => {
                                void listing.applyTagsFilter(newTags);
                              }
                            );
                          }
                        }}
                        onClose={() => {
                          setTagsComboboxAnchorEl(null);
                        }}
                      />
                    </Grid>
                  </Grid>
                </Grid>
              </Grid>
            </Grid>
          </CardContent>
        </Card>
      </Panel>
      <Box flexGrow={1}></Box>
      <GridToolbarColumnsButton
        variant="outlined"
        ref={(node) => {
          if (node) columnMenuRef.current = node;
        }}
      />
      <GridToolbarDensitySelector variant="outlined" />
      <GridToolbarExportContainer variant="outlined">
        <ExportMenuItem onClick={() => exportAllRows()}>
          Export all rows to CSV
        </ExportMenuItem>
        <ExportMenuItem
          onClick={() => {
            exportVisibleRows();
            return Promise.resolve();
          }}
        >
          Export {rowSelectionModel.length > 0 ? "selected" : "this page of"}{" "}
          rows to CSV
        </ExportMenuItem>
      </GridToolbarExportContainer>
    </GridToolbarContainer>
  );
};

const SearchBox = ({
  userListing,
}: {|
  userListing: FetchingData.Fetched<UserListing>,
|}) => {
  const [searchTerm, setSearchTerm] = React.useState("");
  return (
    <form
      role="search"
      onSubmit={(e) => {
        e.preventDefault();
        FetchingData.match(userListing, {
          loading: () => {},
          error: () => {},
          success: (listing) => void listing.setSearchTerm(searchTerm),
        });
      }}
    >
      <StyledTextField
        variant="outlined"
        onChange={({ target: { value } }) => {
          setSearchTerm(value);
        }}
        value={searchTerm}
        sx={{ height: 32 }}
        inputProps={{
          /*
           * Whilst semantically the correct type would be a "search", that
           * means browsers add in their own clear buttons which don't fire the
           * onChange handler
           */
          type: "input",
          role: "searchbox",
          "aria-label": "Search users",
        }}
        InputProps={{
          startAdornment: <StyledSearchIcon sx={{ mx: 0.5 }} />,
          endAdornment:
            searchTerm !== "" ? (
              <IconButtonWithTooltip
                title="Clear"
                icon={<StyledCloseIcon />}
                size="small"
                onClick={() => {
                  setSearchTerm("");
                  FetchingData.match(userListing, {
                    loading: () => {},
                    error: () => {},
                    success: (listing) => void listing.setSearchTerm(""),
                  });
                }}
              />
            ) : null,
        }}
        size="small"
        placeholder="Search"
      />
    </form>
  );
};

export const UsersPage = (): Node => {
  const theme = useTheme();

  const { userListing } = useUserListing();

  const [rowSelectionModel, setRowSelectionModel] = React.useState<
    $ReadOnlyArray<UserId>
  >([]);
  const [sortModel, setSortModel] = React.useState<
    $ReadOnlyArray<{|
      field: | "username"
        | "fileUsage"
        | "recordCount"
        | "lastLogin"
        | "created"
        | "name"
        | "firstName"
        | "lastName"
        | "email",
      sort: "asc" | "desc",
    |}>
  >([]);
  const [groupsAnchorEl, setGroupsAnchorEl] = React.useState(null);
  const [groupsList, setGroupsList] = React.useState<Array<string>>([]);
  const [tagsAnchorEl, setTagsAnchorEl] = React.useState(null);
  const [tagsList, setTagsList] = React.useState<Array<string>>([]);
  const [columnsMenuAnchorEl, setColumnsMenuAnchorEl] =
    React.useState<?HTMLElement>(null);

  const columns = [
    DataGridColumn.newColumnWithValueGetter(
      "fullName",
      (fullName) => fullName,
      {
        headerName: "Full Name",
        flex: 1,
        renderCell: (params: {
          value: string,
          row: User,
          tabIndex: number,
          ...
        }): Node => (
          <Link
            tabIndex={params.tabIndex}
            href={params.row.url}
            onClick={(e) => {
              e.stopPropagation();
            }}
          >
            {params.value}
          </Link>
        ),
        disableExport: true,
      }
    ),
    DataGridColumn.newColumnWithFieldName<User, _>("firstName", {
      headerName: "First Name",
      flex: 1,
    }),
    DataGridColumn.newColumnWithFieldName<User, _>("lastName", {
      headerName: "Last Name",
      flex: 1,
    }),
    DataGridColumn.newColumnWithFieldName<User, _>("email", {
      headerName: "Email",
      flex: 1,
    }),
    DataGridColumn.newColumnWithValueGetter<User, _>(
      "role",
      (role) => {
        const roles = role.split(",");
        const labels = [];
        if (roles.includes("ROLE_ADMIN")) labels.push("Admin");
        if (roles.includes("ROLE_PI")) labels.push("PI");
        if (roles.includes("ROLE_SYSADMIN")) labels.push("Sysadmin");
        if (roles.includes("ROLE_USER")) labels.push("User");
        return labels.join(", ");
      },
      {
        headerName: "Role",
        sortable: false,
        flex: 1,
      }
    ),
    DataGridColumn.newColumnWithFieldName<User, _>("username", {
      headerName: "Username",
      flex: 1,
    }),
    DataGridColumn.newColumnWithValueGetter<User, _>(
      "recordCount",
      (recordCount) => `${recordCount}`,
      {
        headerName: "Documents",
        flex: 1,
      }
    ),
    DataGridColumn.newColumnWithValueGetter<User, _>(
      "fileUsage",
      (fileUsage) => formatFileSize(fileUsage),
      {
        headerName: "Usage",
        flex: 1,
      }
    ),
    DataGridColumn.newColumnWithFieldName<User, _>("lastLogin", {
      headerName: "Last Login",
      flex: 1,
      valueFormatter: (value: Optional<Date>) =>
        value.map((l) => l.toLocaleString()).orElse("—"),
    }),
    DataGridColumn.newColumnWithFieldName<User, _>("created", {
      headerName: "Creation Date",
      flex: 1,
      valueFormatter: (value: Optional<Date>) =>
        value.map((l) => l.toLocaleString()).orElse("—"),
    }),
    DataGridColumn.newColumnWithFieldName<User, _>("enabled", {
      headerName: "Enabled",
      flex: 1,
      sortable: false,
      valueFormatter: (value: boolean) => (value ? "true" : "false"),
      renderCell: (params: { value: boolean, ... }) =>
        params.value ? (
          <TickIcon color="success" aria-label="Enabled" aria-hidden="false" />
        ) : (
          <CrossIcon color="error" aria-label="Disabled" aria-hidden="false" />
        ),
    }),
    DataGridColumn.newColumnWithFieldName<User, _>("locked", {
      headerName: "Locked",
      flex: 1,
      sortable: false,
      valueFormatter: (value: boolean) => (value ? "true" : "false"),
      renderCell: (params: { value: boolean, ... }) =>
        params.value ? (
          <LockIcon color="error" aria-label="Locked" aria-hidden="false" />
        ) : (
          <>&mdash;</>
        ),
    }),
    DataGridColumn.newColumnWithFieldName<User, _>("groups", {
      headerName: "Group Membership",
      flex: 1,
      sortable: false,
      valueFormatter: (value: Array<string>) => value.join(", "),
      renderCell: (params: { value: Array<string>, tabIndex: number, ... }) => {
        if (params.value.length === 0) return <>&mdash;</>;
        return (
          <ChipWithMenu
            role="none"
            tabIndex={-1}
            variant="filled"
            label={`${params.value.length} group${
              params.value.length === 1 ? "" : "s"
            }`}
            onDelete={(e) => {
              setGroupsAnchorEl(e.target);
              setGroupsList(params.value);
            }}
            deleteIcon={
              <IconButtonWithTooltip
                sx={{ mr: 0 }}
                ariaLabel={`${params.value.length} group${
                  params.value.length === 1 ? "" : "s"
                }. Show list of groups.`}
                title="Show list of groups"
                tabIndex={params.tabIndex}
                size="small"
                icon={<ExpandCircleDownIcon />}
              />
            }
          />
        );
      },
    }),
    DataGridColumn.newColumnWithFieldName<User, _>("tags", {
      headerName: "Tags",
      flex: 1,
      sortable: false,
      valueFormatter: (value: Array<string>) => value.join(", "),
      renderCell: (params: { value: Array<string>, tabIndex: number, ... }) => {
        if (params.value.length === 0) return <>&mdash;</>;
        return (
          <ChipWithMenu
            role="none"
            tabIndex={-1}
            variant="filled"
            label={`${params.value.length} tag${
              params.value.length === 1 ? "" : "s"
            }`}
            onDelete={(e) => {
              setTagsAnchorEl(e.target);
              setTagsList(params.value);
            }}
            deleteIcon={
              <IconButtonWithTooltip
                sx={{ mr: 0 }}
                ariaLabel={`${params.value.length} tag${
                  params.value.length === 1 ? "" : "s"
                }. Show list of tags.`}
                title="Show list of tags"
                tabIndex={params.tabIndex}
                size="small"
                icon={<ExpandCircleDownIcon />}
              />
            }
          />
        );
      },
    }),
    DataGridColumn.newColumnWithFieldName<User, _>("usernameAlias", {
      headerName: "Username Alias",
      flex: 1,
      sortable: false,
    }),
  ];

  return (
    <ErrorBoundary topOfViewport>
      <Alerts>
        <div
          style={{
            width: "calc(100% - 16px)",
            backgroundColor: "#f5f5f5",
            padding: "8px",
          }}
        >
          <StyledCard sx={{ m: 3 }} variant="outlined">
            <StyledCardHeader title="Users" />
            <StyledCardContent>
              <Box
                sx={{
                  position: "absolute",
                  top: 0,
                  right: 0,
                  margin: 2,
                  zIndex: 1000,
                }}
              >
                <TableContainer component={SummaryInfoCard}>
                  <Table size="small" sx={{ mb: 0 }}>
                    <TableBody>
                      <TableRow>
                        <StyledTableCell sx={{ borderBottomWidth: 2 }}>
                          Available Seats
                        </StyledTableCell>
                        <StyledTableCell sx={{ borderBottomWidth: 2 }}>
                          {FetchingData.match(userListing, {
                            loading: () => <>&mdash;</>,
                            error: () => <>&mdash;</>,
                            success: (listing) => listing.availableSeats,
                          })}
                        </StyledTableCell>
                      </TableRow>
                      <TableRow>
                        <StyledTableCell>
                          <CustomTooltip title="Enabled users and PIs, excluding admins.">
                            <Abbr>Billable Users</Abbr>
                          </CustomTooltip>
                        </StyledTableCell>
                        <StyledTableCell>
                          {FetchingData.match(userListing, {
                            loading: () => <>&mdash;</>,
                            error: () => <>&mdash;</>,
                            success: (listing) => listing.billableUsersCount,
                          })}
                        </StyledTableCell>
                      </TableRow>
                      <TableRow>
                        <StyledTableCell>System Admins</StyledTableCell>
                        <StyledTableCell>
                          {FetchingData.match(userListing, {
                            loading: () => <>&mdash;</>,
                            error: () => <>&mdash;</>,
                            success: (listing) => listing.systemAdminCount,
                          })}
                        </StyledTableCell>
                      </TableRow>
                      <TableRow>
                        <StyledTableCell sx={{ borderBottomWidth: 2 }}>
                          Community Admins
                        </StyledTableCell>
                        <StyledTableCell sx={{ borderBottomWidth: 2 }}>
                          {FetchingData.match(userListing, {
                            loading: () => <>&mdash;</>,
                            error: () => <>&mdash;</>,
                            success: (listing) => listing.communityAdminCount,
                          })}
                        </StyledTableCell>
                      </TableRow>
                      <TableRow>
                        <StyledTableCell sx={{ borderBottom: "unset" }}>
                          <CustomTooltip title="All users including admins and those with disabled accounts.">
                            <Abbr>Total Users</Abbr>
                          </CustomTooltip>
                        </StyledTableCell>
                        <StyledTableCell sx={{ borderBottom: "unset" }}>
                          {FetchingData.match(userListing, {
                            loading: () => <>&mdash;</>,
                            error: () => <>&mdash;</>,
                            success: (listing) => listing.totalUsersCount,
                          })}
                        </StyledTableCell>
                      </TableRow>
                    </TableBody>
                  </Table>
                </TableContainer>
              </Box>
              <Grid container direction="column" spacing={1.25}>
                <Grid item>
                  <Typography variant="body2" sx={{ maxWidth: 575 }}>
                    You can search, filter, and tag user accounts, as well as
                    export summary information about the users on this server.
                    See our{" "}
                    <Link
                      target="_blank"
                      rel="noreferrer"
                      href={docLinks.taggingUsers}
                    >
                      Tagging docs
                    </Link>{" "}
                    for more.
                  </Typography>
                </Grid>
                <Grid item>
                  <Box height={4}></Box>
                </Grid>
                <Grid item>
                  {FetchingData.match(userListing, {
                    loading: () => (
                      <Typography variant="body2" sx={{ height: "36px" }}>
                        Loading listing of users.
                      </Typography>
                    ),
                    error: (error) => (
                      <>
                        <Typography variant="body2">
                          Failed to load listing of users. Please try
                          refreshing.
                        </Typography>
                        <samp>{error}</samp>
                      </>
                    ),
                    success: () => <></>,
                  })}
                  <SelectionActions
                    selectedIds={rowSelectionModel}
                    fetchedListing={userListing}
                  />
                  <div>
                    <DataGrid
                      aria-label="users"
                      autoHeight
                      columns={columns}
                      rows={FetchingData.match(userListing, {
                        loading: () => ([]: Array<User>),
                        error: () => ([]: Array<User>),
                        success: (listing) => listing.users,
                      })}
                      initialState={{
                        columns: {
                          columnVisibilityModel: {
                            email: false,
                            recordCount: false,
                            created: false,
                            firstName: false,
                            lastName: false,
                            locked: false,
                            tags: false,
                            usernameAlias: false,
                          },
                        },
                      }}
                      density="standard"
                      getRowId={(row: User) => row.id}
                      hideFooterSelectedRowCount
                      checkboxSelection
                      rowSelectionModel={rowSelectionModel}
                      onRowSelectionModelChange={(
                        newRowSelectionModel: $ReadOnlyArray<UserId>,
                        _details
                      ) => {
                        /*
                         * This prop is called not only when the user taps
                         * one of the checkboxes but also when new data is
                         * fetched. It would be nice if we could determine
                         * if the new data being fetched has an overlap
                         * with the old data (e.g. because we're refreshing
                         * after enabling or disabling a user) and keep the
                         * selection that is common. However, the `_details`
                         * argument is always `{ cause: undefined }` rather
                         * than a useful value. This is presumably a bug :(
                         */
                        setRowSelectionModel(newRowSelectionModel);
                      }}
                      disableColumnFilter
                      paginationMode="server"
                      rowCount={FetchingData.match(userListing, {
                        loading: () => 0,
                        error: () => 0,
                        success: (listing) => listing.totalListingCount,
                      })}
                      paginationModel={FetchingData.match(userListing, {
                        loading: () => ({ page: 0, pageSize: 0 }),
                        error: () => ({ page: 0, pageSize: 0 }),
                        success: (listing) => ({
                          page: listing.page,
                          pageSize: listing.pageSize,
                        }),
                      })}
                      pageSizeOptions={FetchingData.match(userListing, {
                        loading: () => [],
                        error: () => [],
                        success: (listing) =>
                          paginationOptions(listing.totalListingCount),
                      })}
                      onPaginationModelChange={({
                        pageSize: newPageSize,
                        page: newPage,
                      }) => {
                        FetchingData.match(userListing, {
                          loading: () => {},
                          error: () => {},
                          success: (listing) => {
                            if (newPage !== listing.page) {
                              void listing.setPage(newPage);
                            }
                            if (newPageSize !== listing.pageSize) {
                              void listing.setPageSize(newPageSize);
                            }
                          },
                        });
                      }}
                      sortingMode="server"
                      sortModel={sortModel}
                      onSortModelChange={(newSortModel: typeof sortModel) => {
                        FetchingData.match(userListing, {
                          loading: () => {},
                          error: () => {},
                          success: (listing) => {
                            if (newSortModel.length === 0) {
                              setSortModel([]);
                              void listing.clearOrdering();
                              return;
                            }
                            const [{ field: newOrderBy, sort: newSortOrder }] =
                              newSortModel;
                            setSortModel([
                              { field: newOrderBy, sort: newSortOrder },
                            ]);
                            void listing.setOrdering(
                              {
                                username: "username",
                                fileUsage: "fileUsage()",
                                recordCount: "recordCount()",
                                lastLogin: "lastLogin",
                                created: "creationDate",
                                name: "lastName",
                                firstName: "firstName",
                                lastName: "lastName",
                                email: "email",
                              }[newOrderBy],
                              newSortOrder
                            );
                          },
                        });
                      }}
                      slots={{
                        toolbar: Toolbar,
                      }}
                      slotProps={{
                        toolbar: {
                          userListing,
                          setColumnsMenuAnchorEl,
                          rowSelectionModel,
                        },
                        panel: {
                          // this has to be here because the panel isn't a
                          // descendent of .MuiDataGrid as it is rendered
                          // inside a Portal
                          sx: {
                            "& .MuiPaper-root": {
                              boxShadow:
                                // this is copied from the other menus
                                "0px 2px 1px -1px rgba(0,0,0,0.2),0px 1px 1px 0px rgba(0,0,0,0.14),0px 1px 3px 0px rgba(0,0,0,0.12)",
                            },
                          },
                          anchorEl: columnsMenuAnchorEl,
                        },
                      }}
                      loading={FetchingData.match(userListing, {
                        loading: () => true,
                        error: () => false,
                        success: () => false,
                      })}
                      {...FetchingData.match(userListing, {
                        loading: () => ({ "aria-hidden": true }),
                        error: () => ({ "aria-hidden": true }),
                        success: () => ({}),
                      })}
                    />
                  </div>
                  <Panel
                    anchorEl={groupsAnchorEl}
                    onClose={() => setGroupsAnchorEl(null)}
                    ariaLabel="Groups"
                  >
                    <List sx={{ p: 0 }}>
                      {groupsList.map((group) => (
                        <ListItem key={group}>
                          <ListItemText primary={group} />
                        </ListItem>
                      ))}
                    </List>
                  </Panel>
                  <Panel
                    anchorEl={tagsAnchorEl}
                    onClose={() => setTagsAnchorEl(null)}
                    ariaLabel="Tags"
                  >
                    <List sx={{ p: 0 }}>
                      {tagsList.map((tag) => (
                        <ListItem key={tag}>
                          <ListItemText primary={tag} />
                        </ListItem>
                      ))}
                    </List>
                  </Panel>
                </Grid>
              </Grid>
            </StyledCardContent>
          </StyledCard>
        </div>
      </Alerts>
    </ErrorBoundary>
  );
};

const COLOR = {
  main: {
    hue: 197,
    saturation: 50,
    lightness: 80,
  },
  darker: {
    hue: 197,
    saturation: 100,
    lightness: 30,
  },
  contrastText: {
    hue: 200,
    saturation: 30,
    lightness: 36,
  },
  background: {
    hue: 200,
    saturation: 20,
    lightness: 82,
  },
  backgroundContrastText: {
    hue: 203,
    saturation: 17,
    lightness: 35,
  },
};

const wrapperDiv = document.getElementById("sysadminUsers");
if (wrapperDiv) {
  const root = createRoot(wrapperDiv);
  root.render(
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={createAccentedTheme(COLOR)}>
        <UsersPage />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
