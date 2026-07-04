import AddIcon from "@mui/icons-material/Add";
import MakePiIcon from "@mui/icons-material/Badge";
import ChecklistIcon from "@mui/icons-material/Checklist";
import CrossIcon from "@mui/icons-material/Clear";
import CloseIcon from "@mui/icons-material/Close";
import UserAliasIcon from "@mui/icons-material/ContactEmergency";
import TickIcon from "@mui/icons-material/Done";
import ExpandCircleDownIcon from "@mui/icons-material/ExpandCircleDown";
import FilterListIcon from "@mui/icons-material/FilterList";
import ExportIcon from "@mui/icons-material/GetApp";
import TagIcon from "@mui/icons-material/LocalOfferOutlined";
import LockIcon from "@mui/icons-material/Lock";
import DisableIcon from "@mui/icons-material/NoAccounts";
import RemoveUserIcon from "@mui/icons-material/PersonRemove";
import SearchIcon from "@mui/icons-material/Search";
import ViewColumnIcon from "@mui/icons-material/ViewColumn";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardHeader from "@mui/material/CardHeader";
import Chip, { chipClasses } from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import FormControlLabel from "@mui/material/FormControlLabel";
import { inputBaseClasses } from "@mui/material/InputBase";
import Link from "@mui/material/Link";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import Popover from "@mui/material/Popover";
import Portal from "@mui/material/Portal";
import Stack from "@mui/material/Stack";
import Switch from "@mui/material/Switch";
import { ThemeProvider } from "@mui/material/styles";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell, { tableCellClasses } from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import { tablePaginationClasses } from "@mui/material/TablePagination";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import {
  ColumnsPanelTrigger,
  DataGrid,
  Toolbar as DataGridToolbar,
  type GridColumnVisibilityModel,
  type GridRowSelectionModel,
  type GridSlotProps,
  type GridSortModel,
  GridToolbarExportContainer,
  useGridApiContext,
} from "@mui/x-data-grid";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { delay } from "es-toolkit";
import React from "react";
import { createRoot } from "react-dom/client";
import { useTranslation } from "react-i18next";
import { formatList } from "@/modules/common/i18n/listFormat";
import TransRichText from "@/modules/common/i18n/TransRichText";
import createAccentedTheme from "../../../accentedTheme";
import { ACCENT_COLOR } from "../../../assets/branding/rspace/sysadmin";
import Alerts from "../../../components/Alerts/Alerts";
import Analytics from "../../../components/Analytics";
import CustomTooltip from "../../../components/CustomTooltip";
import ErrorBoundary from "../../../components/ErrorBoundary";
import ExportMenuItem from "../../../components/ExportMenuItem";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import SubmitSpinnerButton from "../../../components/SubmitSpinnerButton";
import ExportDialog from "../../../Export/ExportDialog";
import useCheckVerificationPasswordNeeded from "../../../hooks/api/useCheckVerificationPasswordNeeded";
import { useDeploymentProperty } from "../../../hooks/api/useDeploymentProperty";
import useUiPreference, { PREFERENCES, UiPreferences } from "../../../hooks/api/useUiPreference";
import { type User, type UserId, type UserListing, useUserListing } from "../../../hooks/api/useUserListing";
import I18nRoot from "../../../modules/common/i18n/I18nRoot";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import * as FetchingData from "../../../util/fetchingData";
import { formatFileSize } from "../../../util/files";
import type { Optional } from "../../../util/optional";
import * as Parsers from "../../../util/parsers";
import Result from "../../../util/result";
import RsSet, { flattenWithIntersection } from "../../../util/set";
import { DataGridColumn, paginationOptions } from "../../../util/table";
import TagsCombobox from "./TagsCombobox";

/*
 * `userListing` and `selectedCount` are passed to the `UsersToolbar` slot via
 * `slotProps.toolbar`, so they are added to the DataGrid's toolbar prop types
 * here. Passing them through `slotProps` (rather than wrapping the slot in a
 * `useCallback` closure that changes identity when `userListing` updates) keeps
 * the slot component referentially stable. That way the DataGrid re-renders the
 * toolbar with fresh props instead of remounting it, which would otherwise
 * reset the toolbar's filter state and hide the active-filters chip.
 */
declare module "@mui/x-data-grid" {
  interface ToolbarPropsOverrides {
    userListing: FetchingData.Fetched<UserListing>;
    selectedCount: number;
  }
}

/*
 * All of the DOM events that happen inside of components that are themselves
 * inside menu items, such as events within a dialog, shouln't propagate
 * outside as the menu will take them to be events that it should
 * respond to by providing keyboard navigation. See
 * ../../../../QuirksOfMaterialUi.md, secion "Dialogs inside Menus", for more
 * information.
 */
const EventBoundary = ({ children }: { children: React.ReactNode }) => (
  // biome-ignore lint/a11y/noStaticElementInteractions: initial biome migration
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
const Panel = ({
  anchorEl,
  children,
  onClose,
  ariaLabel,
}: {
  anchorEl: HTMLElement | null;
  children: React.ReactNode;
  onClose: () => void;
  ariaLabel: string;
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
    slotProps={{
      paper: {
        elevation: 2,
        style: {
          maxWidth: 400,
        },
        role: "dialog",
        "aria-label": ariaLabel,
      },
    }}
  >
    {Boolean(anchorEl) && children}
  </Popover>
);
const Abbr = (props: React.ComponentPropsWithoutRef<"abbr">) => (
  <Box
    component="abbr"
    {...props}
    sx={(theme) => ({
      textDecorationStyle: "dashed",
      textDecorationColor: theme.palette.standardIcon,
      textDecorationThickness: "2px",
      textDecorationLine: "underline",
    })}
  />
);
const TagDialog = ({
  selectedUsers,
  open,
  onClose,
  setTags,
}: {
  selectedUsers: ReadonlyArray<User>;
  open: boolean;
  onClose: () => void;
  setTags: (addedTags: Array<string>, deletedTags: Array<string>) => Promise<void>;
}) => {
  const { t } = useTranslation(["system", "common"]);
  const { addAlert } = React.useContext(AlertContext);
  const [commonTags, setCommonTags] = React.useState<RsSet<string>>(new RsSet([]));
  const [addedTags, setAddedTags] = React.useState<Array<string>>([]);
  const [deletedTags, setDeletedTags] = React.useState<Array<string>>([]);
  const [submitting, setSubmitting] = React.useState(false);
  const visibleTags = React.useMemo(() => {
    const tags = new Set(commonTags);
    addedTags.forEach((tag) => {
      tags.add(tag);
    });
    deletedTags.forEach((tag) => {
      tags.delete(tag);
    });
    return Array.from(tags);
  }, [commonTags, addedTags, deletedTags]);
  const [anchorEl, setAnchorEl] = React.useState<HTMLElement | null>(null);
  React.useEffect(() => {
    setCommonTags(flattenWithIntersection(new RsSet(selectedUsers.map((user) => new RsSet(user.tags)))));
  }, [selectedUsers]);
  return (
    <Portal>
      <Dialog open={open} onClose={onClose}>
        <DialogTitle component="h3">{t("usersPage.tagDialog.title", { count: selectedUsers.length })}</DialogTitle>
        <DialogContent>
          <Stack spacing={2}>
            <Typography variant="body2">
              <TransRichText i18nKey="system:usersPage.tagDialog.description" />
            </Typography>
            <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap" }}>
              {visibleTags.map((tag) => (
                <Chip
                  key={tag}
                  label={tag}
                  variant="outlined"
                  onDelete={() => {
                    setDeletedTags([...deletedTags, tag]);
                    setAddedTags(addedTags.filter((aTag) => aTag !== tag));
                  }}
                />
              ))}
              <Box>
                <Chip
                  icon={<AddIcon />}
                  label={t("common:tags.addTag")}
                  color="primary"
                  skipFocusWhenDisabled
                  onClick={(e) => {
                    setAnchorEl(e.currentTarget);
                  }}
                />
                <TagsCombobox
                  value={new RsSet(visibleTags)}
                  anchorEl={anchorEl}
                  onSelection={(newTag) => {
                    if (!addedTags.includes(newTag)) setAddedTags([...addedTags, newTag]);
                    setDeletedTags(deletedTags.filter((dTag) => dTag !== newTag));
                  }}
                  onClose={() => {
                    setAnchorEl(null);
                  }}
                />
              </Box>
            </Stack>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>{t("common:actions.cancel")}</Button>
          <SubmitSpinnerButton
            disabled={addedTags.length === 0 && deletedTags.length === 0}
            loading={submitting}
            onClick={() => {
              void (async () => {
                setSubmitting(true);
                try {
                  await setTags(addedTags, deletedTags);
                  addAlert(
                    mkAlert({
                      message: t("usersPage.alerts.tagsSaved"),
                      variant: "success",
                    }),
                  );
                  onClose();
                } catch (error) {
                  console.error(error);
                  if (error instanceof Error) {
                    addAlert(
                      mkAlert({
                        title: t("usersPage.alerts.tagsSaveFailed"),
                        message: error.message,
                        variant: "error",
                      }),
                    );
                  }
                } finally {
                  setSubmitting(false);
                }
              })();
            }}
            label={t("common:actions.save")}
          />
        </DialogActions>
      </Dialog>
    </Portal>
  );
};
const SearchBox = ({ userListing }: { userListing: FetchingData.Fetched<UserListing> }) => {
  const { t } = useTranslation(["system", "common"]);
  const [searchTerm, setSearchTerm] = React.useState("");
  return (
    // biome-ignore lint/a11y/useSemanticElements: initial biome migration
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
      <TextField
        variant="outlined"
        onChange={({ target: { value } }) => {
          setSearchTerm(value);
        }}
        value={searchTerm}
        sx={{
          [`& .${inputBaseClasses.input}`]: {
            p: "5px 0",
          },
        }}
        slotProps={{
          htmlInput: {
            /*
             * Whilst semantically the correct type would be a "search", that
             * means browsers add in their own clear buttons which don't fire the
             * onChange handler
             */
            type: "input",
            role: "searchbox",
            "aria-label": t("usersPage.search.users"),
          },
          input: {
            startAdornment: (
              <SearchIcon
                sx={{
                  color: "standardIcon.main",
                  height: 20,
                  width: 20,
                  mx: 0.5,
                }}
              />
            ),
            endAdornment:
              searchTerm !== "" ? (
                <IconButtonWithTooltip
                  title={t("common:actions.clear")}
                  icon={
                    <CloseIcon
                      sx={{
                        color: "standardIcon.main",
                        height: 20,
                        width: 20,
                      }}
                    />
                  }
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
          },
        }}
        size="small"
        placeholder={t("common:actions.search")}
      />
    </form>
  );
};

/**
 * This is the action that can be performed on a single selected user to either
 * grant them the PI role, if they are not currently a PI, or to revoke the
 * role if they currently are.
 */
const PiAction = ({
  selectedUser,
  setActionsAnchorEl,
  autoFocus,
}: {
  selectedUser: Result<User>;
  setActionsAnchorEl: (_: null) => void;
  autoFocus: boolean;
}) => {
  const { t } = useTranslation(["system", "common"]);
  const [open, setOpen] = React.useState(false);
  const [password, setPassword] = React.useState("");
  const { addAlert } = React.useContext(AlertContext);
  const verificationPasswordNeeded = useCheckVerificationPasswordNeeded();
  const allowedPiAction: Result<{
    user: User;
    action: "revoke" | "grant";
  }> = selectedUser
    .mapError(() => new Error(t("usersPage.piRoleDialog.onlyOneUser")))
    .flatMap((user) => {
      if (user.isPi)
        return Result.Ok({
          user,
          action: "revoke",
        });
      if (user.isRegularUser)
        return Result.Ok({
          user,
          action: "grant",
        });
      return Result.Error([new Error(t("usersPage.piRoleDialog.adminSelected"))]);
    });
  return (
    <>
      <MenuItem
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
              action === "grant"
                ? t("usersPage.piRoleDialog.grantMenuItem")
                : t("usersPage.piRoleDialog.revokeMenuItem"),
            )
            .orElse(t("usersPage.piRoleDialog.grantMenuItem"))}
          secondary={allowedPiAction.map(() => null).orElseGet(([error]) => error.message)}
          slotProps={{
            secondary: {
              style: {
                whiteSpace: "break-spaces",
              },
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
                            message: t("usersPage.alerts.piRoleGranted"),
                          }),
                        );
                        setOpen(false);
                        setActionsAnchorEl(null);
                      } catch (error) {
                        if (error instanceof Error) {
                          addAlert(
                            mkAlert({
                              title: t("usersPage.alerts.piRoleGrantFailed"),
                              message: error.message,
                              variant: "error",
                            }),
                          );
                        }
                      }
                    }
                    if (action === "revoke") {
                      try {
                        await user.revokePiRole(password);
                        addAlert(
                          mkAlert({
                            variant: "success",
                            message: t("usersPage.alerts.piRoleRevoked"),
                          }),
                        );
                        setOpen(false);
                        setActionsAnchorEl(null);
                      } catch (error) {
                        if (error instanceof Error) {
                          addAlert(
                            mkAlert({
                              title: t("usersPage.alerts.piRoleRevokeFailed"),
                              message: error.message,
                              variant: "error",
                            }),
                          );
                        }
                      }
                    }
                  })();
                }}
              >
                <DialogTitle>
                  {action === "grant"
                    ? t("usersPage.piRoleDialog.grantTitle")
                    : t("usersPage.piRoleDialog.revokeTitle")}
                </DialogTitle>
                <DialogContent>
                  {FetchingData.match(verificationPasswordNeeded, {
                    loading: () => (
                      <DialogContentText
                        variant="body2"
                        sx={{
                          mb: 2,
                        }}
                      >
                        {t("usersPage.piRoleDialog.loading")}
                      </DialogContentText>
                    ),
                    error: (errorMsg) => (
                      <DialogContentText
                        variant="body2"
                        sx={{
                          mb: 2,
                        }}
                      >
                        {t("usersPage.piRoleDialog.error", { error: errorMsg })}
                      </DialogContentText>
                    ),
                    success: (veriPwdNeeded) =>
                      veriPwdNeeded ? (
                        <DialogContentText
                          variant="body2"
                          sx={{
                            mb: 2,
                          }}
                        >
                          {t("usersPage.piRoleDialog.setVerificationPassword")}
                        </DialogContentText>
                      ) : (
                        <>
                          <DialogContentText
                            variant="body2"
                            sx={{
                              mb: 2,
                            }}
                          >
                            <TransRichText
                              i18nKey={
                                action === "grant"
                                  ? "system:usersPage.piRoleDialog.grantPrompt"
                                  : "system:usersPage.piRoleDialog.revokePrompt"
                              }
                              values={{ fullName: selectedUser.map((u) => u.fullName).orElse("") }}
                            />
                          </DialogContentText>
                          <TextField
                            type="password"
                            autoComplete="current-password"
                            size="small"
                            label={t("usersPage.piRoleDialog.password")}
                            value={password}
                            onChange={(e) => {
                              setPassword(e.target.value);
                            }}
                            fullWidth
                          />
                        </>
                      ),
                  })}
                </DialogContent>
                <DialogActions>
                  <Button
                    onClick={() => {
                      setPassword("");
                      setOpen(false);
                      setActionsAnchorEl(null);
                    }}
                  >
                    {t("common:actions.cancel")}
                  </Button>
                  <SubmitSpinnerButton
                    type="submit"
                    loading={false}
                    disabled={false}
                    label={
                      action === "grant"
                        ? t("usersPage.piRoleDialog.grantAction")
                        : t("usersPage.piRoleDialog.revokeAction")
                    }
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
}: {
  selectedUser: Result<User>;
  setActionsAnchorEl: (_: null) => void;
}) => {
  const { t } = useTranslation(["system", "common"]);
  const { addAlert } = React.useContext(AlertContext);
  const [open, setOpen] = React.useState(false);
  const [alias, setAlias] = React.useState("");
  const allowedToSetAlias: Result<User> = selectedUser.mapError(
    () => new Error(t("usersPage.aliasDialog.onlyOneUser")),
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
          primary={t("usersPage.aliasDialog.menuItem")}
          secondary={allowedToSetAlias.map(() => null).orElseGet(([error]) => error.message)}
          slotProps={{
            secondary: {
              style: {
                whiteSpace: "break-spaces",
              },
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
                        message: t("usersPage.aliasDialog.success"),
                      }),
                    );
                    setOpen(false);
                    setActionsAnchorEl(null);
                  } catch (error) {
                    if (error instanceof Error) {
                      addAlert(
                        mkAlert({
                          title: t("usersPage.aliasDialog.errorTitle"),
                          message: error.message,
                          variant: "error",
                        }),
                      );
                    }
                  }
                })();
              }}
            >
              <DialogTitle>{t("usersPage.aliasDialog.title")}</DialogTitle>
              <DialogContent>
                <DialogContentText
                  component="div"
                  variant="body2"
                  sx={{
                    mb: 2,
                  }}
                >
                  <Typography variant="body2">{t("usersPage.aliasDialog.description")}</Typography>
                </DialogContentText>
                <TextField
                  size="small"
                  label={t("usersPage.aliasDialog.field")}
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
                  {t("common:actions.cancel")}
                </Button>
                <SubmitSpinnerButton
                  type="submit"
                  loading={false}
                  disabled={false}
                  label={t("usersPage.aliasDialog.submit")}
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
}: {
  selectedUser: Result<User>;
  setActionsAnchorEl: (_: null) => void;
}) => {
  const { t } = useTranslation(["system", "common"]);
  const { addAlert } = React.useContext(AlertContext);
  const [open, setOpen] = React.useState(false);
  const [username, setUsername] = React.useState("");
  const canDelete = useDeploymentProperty("sysadmin.delete.user");
  const allowedToDelete: Result<User> = FetchingData.getSuccessValue(canDelete)
    .flatMap(Parsers.isBoolean)
    .flatMap(Parsers.isTrue)
    .mapError(() => new Error(t("usersPage.deleteDialog.disabledByDeployment")))
    .flatMap(() => selectedUser.mapError(() => new Error(t("usersPage.deleteDialog.onlyOneUser"))));
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
          primary={t("common:actions.delete")}
          secondary={allowedToDelete.map(() => null).orElseGet(([error]) => error.message)}
          slotProps={{
            secondary: {
              style: {
                whiteSpace: "break-spaces",
              },
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
                    if (username !== user.username) throw new Error(t("usersPage.deleteDialog.usernameMismatch"));
                    await user.delete();
                    addAlert(
                      mkAlert({
                        variant: "success",
                        message: t("usersPage.alerts.accountDeleted"),
                      }),
                    );
                    setOpen(false);
                    setActionsAnchorEl(null);
                  } catch (error) {
                    if (error instanceof Error) {
                      addAlert(
                        mkAlert({
                          title: t("usersPage.alerts.accountDeleteFailed"),
                          message: error.message,
                          variant: "error",
                        }),
                      );
                    }
                  }
                })();
              }}
            >
              <DialogTitle>{t("usersPage.deleteDialog.title")}</DialogTitle>
              <DialogContent>
                <DialogContentText
                  component="div"
                  variant="body2"
                  sx={{
                    mb: 2,
                  }}
                >
                  {(user.hasFormsUsedByOtherUsers || user.hasTemplatesUsedByOtherUsers) && (
                    <Alert severity="info" sx={{ mb: 1 }}>
                      <Typography variant="body2">
                        <TransRichText i18nKey="system:usersPage.deleteDialog.formsAndTemplatesNotice" />
                      </Typography>
                    </Alert>
                  )}
                  <Typography
                    variant="body2"
                    sx={{
                      mb: 1,
                    }}
                  >
                    {t("usersPage.deleteDialog.irreversible")}
                  </Typography>
                  {user.hasFormsUsedByOtherUsers && (
                    <Typography
                      variant="body2"
                      sx={{
                        mb: 1,
                      }}
                    >
                      <TransRichText i18nKey="system:usersPage.deleteDialog.formsNotice" />
                    </Typography>
                  )}
                  <Typography
                    variant="body2"
                    sx={{
                      mb: 1,
                    }}
                  >
                    {t("usersPage.deleteDialog.xmlArchive")}
                  </Typography>
                  <Typography
                    variant="body2"
                    sx={{
                      mb: 1,
                    }}
                  >
                    <TransRichText
                      i18nKey="system:usersPage.deleteDialog.confirmUsername"
                      values={{ fullName: selectedUser.map((u) => u.fullName).orElse("") }}
                    />
                  </Typography>
                </DialogContentText>
                <TextField
                  size="small"
                  label={t("usersPage.columns.username")}
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
                  {t("common:actions.cancel")}
                </Button>
                <SubmitSpinnerButton
                  type="submit"
                  loading={false}
                  disabled={false}
                  label={
                    user.hasFormsUsedByOtherUsers || user.hasTemplatesUsedByOtherUsers
                      ? t("usersPage.deleteDialog.transferAndDelete")
                      : t("common:actions.delete")
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
}: {
  selectedIds: ReadonlyArray<UserId>;
  fetchedListing: FetchingData.Fetched<UserListing>;
}) => {
  const { t } = useTranslation("system");
  const { addAlert } = React.useContext(AlertContext);
  const [exportDialogOpen, setExportDialogOpen] = React.useState(false);
  const [actionsAnchorEl, setActionsAnchorEl] = React.useState<HTMLElement | null>(null);
  const [tagDialogOpen, setTagDialogOpen] = React.useState(false);
  const exportAllowed: Result<React.ReactNode> =
    selectedIds.length === 1 ? Result.Ok(null) : Result.Error([new Error(t("usersPage.export.onlyOneUser"))]);
  const selectedUsers: Result<ReadonlyArray<User>> =
    selectedIds.length === 0
      ? Result.Error([new Error(t("usersPage.selection.noneSelected"))])
      : Result.all(
          ...selectedIds.map((id) =>
            FetchingData.getSuccessValue(fetchedListing).flatMap((listing) => listing.getById(id)),
          ),
        );
  const selectedUser: Result<User> = Result.fromNullable(selectedIds.at(0), new Error("selectedIds is empty"))
    .flatMap((id) =>
      selectedIds.length > 1
        ? Result.Error<UserId>([new Error(t("usersPage.selection.moreThanOneSelected"))])
        : Result.Ok(id),
    )
    .flatMap((id) => FetchingData.getSuccessValue(fetchedListing).flatMap((listing) => listing.getById(id)));
  const enableDisableAction: Result<{
    user: User;
    action: "enable" | "disable";
  }> = selectedUser
    .mapError(() => new Error(t("usersPage.enableDisable.onlyOneUser")))
    .map((user) =>
      user.enabled
        ? {
            user,
            action: "disable",
          }
        : {
            user,
            action: "enable",
          },
    );
  const unlockAction: Result<User> = selectedUser
    .mapError(() => new Error(t("usersPage.unlock.onlyOneUser")))
    .flatMap((user) =>
      user.locked ? Result.Ok(user) : Result.Error([new Error(t("usersPage.unlock.onlyLockedAccounts"))]),
    );
  return (
    <>
      {FetchingData.match(fetchedListing, {
        loading: () => <></>,
        error: () => <></>,
        success: (listing) => (
          <Stack
            direction="row"
            spacing={1}
            sx={{
              width: 598,
              alignItems: "center",
              mb: 0.5,
            }}
          >
            <Box>
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
                  setActionsAnchorEl(e.currentTarget);
                }}
                aria-label={t("usersPage.actionsLabel")}
                aria-haspopup="menu"
                aria-expanded={Boolean(actionsAnchorEl)}
              >
                {t("usersPage.actionsButton")}
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
                  <ListItemText primary={t("usersPage.tagDialog.menuItem")} />
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
                        setTags={(addedTags, deletedTags) => listing.setTags(users, addedTags, deletedTags)}
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
                    primary={t("usersPage.export.work")}
                    secondary={exportAllowed.orElseGet(([error]) => error.message)}
                    slotProps={{
                      secondary: {
                        style: {
                          whiteSpace: "break-spaces",
                        },
                      },
                    }}
                  />
                </MenuItem>
                <MenuItem
                  disabled={unlockAction.isError}
                  onClick={() => {
                    unlockAction.do((user) => {
                      void (async () => {
                        try {
                          await user.unlock();
                          addAlert(
                            mkAlert({
                              variant: "success",
                              message: t("usersPage.alerts.accountUnlocked"),
                            }),
                          );
                          setActionsAnchorEl(null);
                        } catch (error) {
                          if (error instanceof Error) {
                            addAlert(
                              mkAlert({
                                title: t("usersPage.alerts.accountUnlockFailed"),
                                message: error.message,
                                variant: "error",
                              }),
                            );
                          }
                        }
                      })();
                    });
                  }}
                >
                  <ListItemIcon>
                    <LockIcon />
                  </ListItemIcon>
                  <ListItemText
                    primary={t("usersPage.unlock.menuItem")}
                    secondary={unlockAction.map(() => null).orElseGet(([error]) => error.message)}
                    slotProps={{
                      secondary: {
                        style: {
                          whiteSpace: "break-spaces",
                        },
                      },
                    }}
                  />
                </MenuItem>
                <EventBoundary>
                  <SetUsernamAliasAction selectedUser={selectedUser} setActionsAnchorEl={setActionsAnchorEl} />
                </EventBoundary>
                <MenuItem
                  disabled={enableDisableAction.isError}
                  onClick={() =>
                    enableDisableAction.do(({ action, user }) => {
                      void (async () => {
                        if (action === "enable") {
                          try {
                            await user.enable();
                            addAlert(
                              mkAlert({
                                variant: "success",
                                message: t("usersPage.alerts.accountEnabled"),
                              }),
                            );
                            setActionsAnchorEl(null);
                          } catch (error) {
                            if (error instanceof Error) {
                              addAlert(
                                mkAlert({
                                  title: t("usersPage.alerts.accountEnableFailed"),
                                  message: error.message,
                                  variant: "error",
                                }),
                              );
                            }
                          }
                        }
                        if (action === "disable") {
                          try {
                            await user.disable();
                            addAlert(
                              mkAlert({
                                variant: "success",
                                message: t("usersPage.alerts.accountDisabled"),
                              }),
                            );
                            setActionsAnchorEl(null);
                          } catch (error) {
                            if (error instanceof Error) {
                              addAlert(
                                mkAlert({
                                  title: t("usersPage.alerts.accountDisableFailed"),
                                  message: error.message,
                                  variant: "error",
                                }),
                              );
                            }
                          }
                        }
                      })();
                    })
                  }
                >
                  <ListItemIcon>
                    <DisableIcon />
                  </ListItemIcon>
                  <ListItemText
                    primary={enableDisableAction
                      .map(({ action }) =>
                        action === "disable"
                          ? t("usersPage.enableDisable.disable")
                          : t("usersPage.enableDisable.enable"),
                      )
                      .orElse(t("usersPage.enableDisable.menuItem"))}
                    secondary={enableDisableAction.map(() => null).orElseGet(([error]) => error.message)}
                    slotProps={{
                      secondary: {
                        style: {
                          whiteSpace: "break-spaces",
                        },
                      },
                    }}
                  />
                </MenuItem>
                <EventBoundary>
                  <DeleteAction selectedUser={selectedUser} setActionsAnchorEl={setActionsAnchorEl} />
                </EventBoundary>
              </Menu>
            </Box>
            <Typography
              variant="body2"
              sx={{
                ml: 0.5,
                p: 0,
                color: selectedIds.length > 0 ? "initial" : "grey",
              }}
            >
              {selectedIds.length > 0
                ? t("usersPage.selection.usersSelected", { count: selectedIds.length })
                : t("usersPage.selection.noSelection")}
            </Typography>
          </Stack>
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
        // @ts-expect-error RS is a global available on the sysadmin page
        //eslint-disable-next-line
        allowFileStores={RS.newFileStoresExportEnabled}
      />
    </>
  );
};
const UsersToolbar = ({ userListing, selectedCount }: GridSlotProps["toolbar"]) => {
  const { t } = useTranslation(["system", "common"]);
  const [filterAnchorEl, setFilterAnchorEl] = React.useState<HTMLElement | null>(null);
  const [tagsComboboxAnchorEl, setTagsComboboxAnchorEl] = React.useState<HTMLElement | null>(null);
  const [tagsChecked, setTagsChecked] = React.useState(false);
  const [tags, setTags] = React.useState<Array<string>>([]);
  const apiRef = useGridApiContext();

  /*
   * This is a bit of hack. The MUI DataGrid component under the MIT license is
   * restricted to displaying 100 rows. This export menu item downloads the
   * full listing of users, exports it as a CSV file, and then returns the
   * table to a valid state.
   */
  const exportAllRows = async () => {
    // biome-ignore lint/suspicious/noImplicitAnyLet: initial biome migration
    let priorSearchParameters;
    const newListing: UserListing = await FetchingData.match(userListing, {
      loading: () => Promise.reject(new Error("loading")),
      error: () => Promise.reject(new Error("error")),
      success: (listing) => {
        priorSearchParameters = listing.getSearchParameters();
        return listing.allUsers();
      },
    });
    await delay(2000); // wait for the table to be re-rendered
    apiRef.current?.exportDataAsCsv({
      getRowsToExport: () => newListing.users.map((u) => u.id),
      allColumns: true,
    });
    await delay(2000); // wait for download to be done
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
    <DataGridToolbar
      aria-label={t("usersPage.tableActionsLabel")}
      style={{
        width: "100%",
        display: "flex",
        alignItems: "center",
        gap: "8px",
        flexWrap: "wrap",
      }}
    >
      <SearchBox userListing={userListing} />
      <Button
        variant="outlined"
        color={tagsChecked && tags.length > 0 ? "primary" : "standardIcon"}
        startIcon={<FilterListIcon />}
        onClick={(e) => {
          setFilterAnchorEl(e.currentTarget);
        }}
        aria-label={t("usersPage.filterLabel")}
        aria-haspopup="dialog"
        aria-expanded={Boolean(filterAnchorEl)}
      >
        {t("usersPage.filtersButton")}
        {tagsChecked && tags.length > 0 && (
          <Chip
            label={tags.length}
            size="small"
            color="primary"
            aria-label={t("usersPage.filtersActive", { count: tags.length })}
            sx={{
              ml: 1,
              height: 20,
              pointerEvents: "none",
              [`& .${chipClasses.label}`]: { px: 1 },
            }}
          />
        )}
      </Button>
      <Panel
        anchorEl={filterAnchorEl}
        onClose={() => {
          setFilterAnchorEl(null);
        }}
        ariaLabel={t("usersPage.filtersButton")}
      >
        <Card>
          <CardContent
            sx={{
              pt: 1,
            }}
          >
            <Stack spacing={1}>
              <FormControlLabel
                control={
                  <Switch
                    size="small"
                    checked={tagsChecked}
                    onChange={(event) => {
                      setTagsChecked(event.target.checked);
                      // When turning the Tags filter off, always refetch
                      // so any previously-applied tag filter is cleared
                      // from the listing. When turning it on, only
                      // refetch if there are tags selected — otherwise
                      // toggling on with no tags is a no-op for the
                      // listing and would just cause an unnecessary
                      // re-render of the whole page.
                      if (event.target.checked && tags.length === 0) return;
                      FetchingData.getSuccessValue(userListing).do((listing) => {
                        void listing.applyTagsFilter(event.target.checked ? tags : []);
                      });
                    }}
                  />
                }
                label={t("common:tags.label")}
              />
              <Stack direction="row" spacing={1} sx={{ flexWrap: "nowrap" }}>
                <Box sx={{ width: 30, flexShrink: 0 }} />
                <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: "wrap" }}>
                  {tags.map((tag) => (
                    <Chip
                      key={tag}
                      label={tag}
                      variant="outlined"
                      onDelete={() => {
                        const newTags = tags.filter((t) => t !== tag);
                        setTags(newTags);
                        FetchingData.getSuccessValue(userListing).do((listing) => {
                          void listing.applyTagsFilter(newTags);
                        });
                      }}
                      disabled={!tagsChecked}
                    />
                  ))}
                  <Box>
                    <Chip
                      icon={<AddIcon />}
                      label={t("common:tags.addTag")}
                      color="primary"
                      skipFocusWhenDisabled
                      onClick={(e) => {
                        setTagsComboboxAnchorEl(e.currentTarget);
                      }}
                      disabled={!tagsChecked}
                    />
                    <TagsCombobox
                      value={new RsSet(tags)}
                      anchorEl={tagsComboboxAnchorEl}
                      allowNewTags={false}
                      onSelection={(newTag) => {
                        if (!tags.includes(newTag)) {
                          const newTags = tags.concat(newTag);
                          setTags(newTags);
                          FetchingData.getSuccessValue(userListing).do((listing) => {
                            void listing.applyTagsFilter(newTags);
                          });
                        }
                      }}
                      onClose={() => {
                        setTagsComboboxAnchorEl(null);
                      }}
                    />
                  </Box>
                </Stack>
              </Stack>
            </Stack>
          </CardContent>
        </Card>
      </Panel>
      <ColumnsPanelTrigger style={{ marginLeft: "auto" }}>
        <ViewColumnIcon sx={{ fontSize: 18, mr: 1 }} />
        {t("usersPage.columns.button")}
      </ColumnsPanelTrigger>
      <GridToolbarExportContainer>
        <ExportMenuItem
          onClick={async () => {
            await exportAllRows();
          }}
        >
          {t("usersPage.export.allRows")}
        </ExportMenuItem>
        <ExportMenuItem
          onClick={() => {
            exportVisibleRows();
          }}
        >
          {t("usersPage.export.visibleRows", {
            scope: selectedCount > 0 ? t("usersPage.export.selected") : t("usersPage.export.thisPageOf"),
          })}
        </ExportMenuItem>
      </GridToolbarExportContainer>
    </DataGridToolbar>
  );
};
export const UsersPage = (): React.ReactNode => {
  const { t, i18n } = useTranslation("system");
  const language = i18n.resolvedLanguage ?? i18n.language;
  const formatDisplayList = (items: Iterable<string>) => formatList(items, language);
  const { userListing } = useUserListing();
  const [rowSelectionModel, setRowSelectionModel] = React.useState<GridRowSelectionModel>({
    type: "include",
    ids: new Set(),
  });
  const [sortModel, setSortModel] = React.useState<GridSortModel>([]);
  const [groupsAnchorEl, setGroupsAnchorEl] = React.useState<HTMLElement | null>(null);
  const [groupsList, setGroupsList] = React.useState<Array<string>>([]);
  const [tagsAnchorEl, setTagsAnchorEl] = React.useState<HTMLElement | null>(null);
  const [tagsList, setTagsList] = React.useState<Array<string>>([]);
  const [columnVisibility, setColumnVisibility] = useUiPreference<GridColumnVisibilityModel>(
    PREFERENCES.SYSADMIN_USERS_TABLE_COLUMNS,
    {
      defaultValue: {
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
  );
  const selectedIds = React.useMemo(
    () => (rowSelectionModel.type === "include" ? [...rowSelectionModel.ids] : []),
    [rowSelectionModel],
  );
  const columns = [
    DataGridColumn.newColumnWithValueMapper("fullNameSurnameFirst", (v) => v, {
      headerName: t("usersPage.columns.fullName"),
      flex: 1,
      renderCell: (params: { value?: string; row: User; tabIndex: number }): React.ReactNode => {
        if (!params.value) return null;
        return (
          <Link
            tabIndex={params.tabIndex}
            href={params.row.url}
            onClick={(e) => {
              e.stopPropagation();
            }}
          >
            {params.value}
          </Link>
        );
      },
      disableExport: true,
    }),
    DataGridColumn.newColumnWithFieldName<"firstName", User>("firstName", {
      headerName: t("usersPage.columns.firstName"),
      flex: 1,
    }),
    DataGridColumn.newColumnWithFieldName<"lastName", User>("lastName", {
      headerName: t("usersPage.columns.lastName"),
      flex: 1,
    }),
    DataGridColumn.newColumnWithFieldName<"email", User>("email", {
      headerName: t("usersPage.columns.email"),
      flex: 1,
    }),
    DataGridColumn.newColumnWithValueMapper<"role", User>(
      "role",
      (role) => {
        const roles = role.split(",");
        const labels = [];
        if (roles.includes("ROLE_ADMIN")) labels.push(t("usersPage.roleLabels.admin"));
        if (roles.includes("ROLE_PI")) labels.push(t("usersPage.roleLabels.pi"));
        if (roles.includes("ROLE_SYSADMIN")) labels.push(t("usersPage.roleLabels.sysadmin"));
        if (roles.includes("ROLE_USER")) labels.push(t("usersPage.roleLabels.user"));
        return formatDisplayList(labels);
      },
      {
        headerName: t("usersPage.columns.role"),
        sortable: false,
        flex: 1,
      },
    ),
    DataGridColumn.newColumnWithFieldName<"username", User>("username", {
      headerName: t("usersPage.columns.username"),
      flex: 1,
    }),
    DataGridColumn.newColumnWithValueMapper<"recordCount", User>("recordCount", (recordCount) => `${recordCount}`, {
      headerName: t("usersPage.columns.documents"),
      flex: 1,
    }),
    DataGridColumn.newColumnWithFieldName<"fileUsage", User>("fileUsage", {
      headerName: t("usersPage.columns.usage"),
      flex: 1,
      renderCell: (params: { value?: number }) => formatFileSize(params.value),
    }),
    DataGridColumn.newColumnWithFieldName<"lastLogin", User>("lastLogin", {
      headerName: t("usersPage.columns.lastLogin"),
      flex: 1,
      valueFormatter: (value: Optional<Date>) => value.map((l) => l.toLocaleString()).orElse("—"),
    }),
    DataGridColumn.newColumnWithFieldName<"created", User>("created", {
      headerName: t("usersPage.columns.creationDate"),
      flex: 1,
      valueFormatter: (value: Optional<Date>) => value.map((l) => l.toLocaleString()).orElse("—"),
    }),
    DataGridColumn.newColumnWithFieldName<"enabled", User>("enabled", {
      headerName: t("usersPage.columns.enabled"),
      flex: 1,
      sortable: false,
      valueFormatter: (value: boolean) => (value ? "true" : "false"),
      renderCell: (params: { value?: boolean }) =>
        params.value ? (
          <TickIcon color="success" aria-label={t("usersPage.columns.enabled")} aria-hidden="false" />
        ) : (
          <CrossIcon color="error" aria-label={t("usersPage.columns.disabled")} aria-hidden="false" />
        ),
    }),
    DataGridColumn.newColumnWithFieldName<"locked", User>("locked", {
      headerName: t("usersPage.columns.locked"),
      flex: 1,
      sortable: false,
      valueFormatter: (value: boolean) => (value ? "true" : "false"),
      renderCell: (params: { value?: boolean }) =>
        params.value ? <LockIcon color="error" aria-label={t("usersPage.columns.locked")} aria-hidden="false" /> : "—",
    }),
    DataGridColumn.newColumnWithFieldName<"groups", User>("groups", {
      headerName: t("usersPage.columns.groupMembership"),
      flex: 1,
      sortable: false,
      valueFormatter: (value: Array<string>) => formatDisplayList(value),
      renderCell: (params: { value?: Array<string>; tabIndex: number }): React.ReactNode => {
        if (!params.value) return <>{"—"}</>;
        const value = params.value;
        if (value.length === 0) return <>{"—"}</>;
        return (
          <Chip
            role="none"
            tabIndex={-1}
            variant="filled"
            label={t("usersPage.groupMembership.count", { count: value.length })}
            onDelete={(e: React.MouseEvent<HTMLButtonElement>) => {
              setGroupsAnchorEl(e.currentTarget);
              setGroupsList(value);
            }}
            deleteIcon={
              <IconButtonWithTooltip
                sx={{
                  mr: 0,
                }}
                ariaLabel={t("usersPage.groupMembership.showListLabel", { count: value.length })}
                title={t("usersPage.groupMembership.showList")}
                tabIndex={params.tabIndex}
                size="small"
                icon={<ExpandCircleDownIcon />}
              />
            }
            sx={{
              backgroundColor: "transparent",
              border: "2px solid #94a7b2",
              color: "standardIcon.main",
              textTransform: "capitalize",
              letterSpacing: "0.04em",
              [`& .${chipClasses.deleteIcon}`]: {
                color: "#94a7b2",
                marginRight: 0,
              },
            }}
          />
        );
      },
    }),
    DataGridColumn.newColumnWithFieldName<"tags", User>("tags", {
      headerName: t("usersPage.columns.tags"),
      flex: 1,
      sortable: false,
      valueFormatter: (value: Array<string>) => formatDisplayList(value),
      renderCell: (params: { value?: Array<string>; tabIndex: number }) => {
        if (!params.value) return <>{"—"}</>;
        const value = params.value;
        if (value.length === 0) return <>{"—"}</>;
        return (
          <Chip
            role="none"
            tabIndex={-1}
            variant="filled"
            label={t("usersPage.tags.count", { count: value.length })}
            onDelete={(e: React.MouseEvent<HTMLButtonElement>) => {
              setTagsAnchorEl(e.currentTarget);
              setTagsList(value);
            }}
            deleteIcon={
              <IconButtonWithTooltip
                sx={{
                  mr: 0,
                }}
                ariaLabel={t("usersPage.tags.showListLabel", { count: value.length })}
                title={t("usersPage.tags.showList")}
                tabIndex={params.tabIndex}
                size="small"
                icon={<ExpandCircleDownIcon />}
              />
            }
            sx={{
              backgroundColor: "transparent",
              border: "2px solid #94a7b2",
              color: "standardIcon.main",
              textTransform: "capitalize",
              letterSpacing: "0.04em",
              [`& .${chipClasses.deleteIcon}`]: {
                color: "#94a7b2",
                marginRight: 0,
              },
            }}
          />
        );
      },
    }),
    DataGridColumn.newColumnWithFieldName<"usernameAlias", User>("usernameAlias", {
      headerName: t("usersPage.columns.usernameAlias"),
      flex: 1,
      sortable: false,
    }),
  ];
  return (
    <Analytics>
      <ErrorBoundary topOfViewport>
        <Alerts>
          <Box
            sx={{
              width: "calc(100% - 16px)",
              backgroundColor: "#f5f5f5",
              padding: "8px",
            }}
          >
            <Card
              sx={{
                border: (theme) => theme.borders.themedDialog?.(200, 90, 20),
                m: 3,
                position: "relative",
              }}
              variant="outlined"
            >
              <CardHeader
                title={t("usersPage.title")}
                sx={(theme) => ({
                  borderBottom: theme.borders.themedDialogTitle?.(200, 90, 20),
                  paddingTop: theme.spacing(1.5),
                  paddingBottom: theme.spacing(1.5),
                })}
              />
              <CardContent
                sx={{
                  "&:last-child": {
                    paddingBottom: 0,
                  },
                }}
              >
                <Box
                  sx={{
                    position: "absolute",
                    top: 0,
                    right: 0,
                    margin: 2,
                    zIndex: 1000,
                  }}
                >
                  <TableContainer
                    component={Card}
                    sx={(theme) => ({
                      border: theme.borders.themedDialogTitle?.(200, 90, 20),
                      boxShadow: "none",
                    })}
                  >
                    <Table
                      size="small"
                      sx={(theme) => ({
                        [`& .${tableCellClasses.root}`]: {
                          backgroundColor: "#edf7fc",
                          padding: "4px 12px",
                          borderBottom: theme.borders.themedDialogTitle?.(200, 90, 20),
                        },
                        [`& tr:last-of-type .${tableCellClasses.root}`]: {
                          borderBottom: "unset",
                        },
                      })}
                    >
                      <TableBody>
                        <TableRow>
                          <TableCell>{t("usersPage.summary.availableSeats")}</TableCell>
                          <TableCell>
                            {FetchingData.match<UserListing, React.ReactNode>(userListing, {
                              loading: () => <>{"—"}</>,
                              error: () => <>{"—"}</>,
                              success: (listing) => listing.availableSeats,
                            })}
                          </TableCell>
                        </TableRow>
                        <TableRow>
                          <TableCell>
                            <CustomTooltip title={t("usersPage.billableUsersTooltip")}>
                              <Abbr>{t("usersPage.billableUsers")}</Abbr>
                            </CustomTooltip>
                          </TableCell>
                          <TableCell>
                            {FetchingData.match<UserListing, React.ReactNode>(userListing, {
                              loading: () => <>{"—"}</>,
                              error: () => <>{"—"}</>,
                              success: (listing) => listing.billableUsersCount,
                            })}
                          </TableCell>
                        </TableRow>
                        <TableRow>
                          <TableCell>{t("usersPage.summary.systemAdmins")}</TableCell>
                          <TableCell>
                            {FetchingData.match<UserListing, React.ReactNode>(userListing, {
                              loading: () => <>{"—"}</>,
                              error: () => <>{"—"}</>,
                              success: (listing) => listing.systemAdminCount,
                            })}
                          </TableCell>
                        </TableRow>
                        <TableRow>
                          <TableCell>{t("usersPage.summary.communityAdmins")}</TableCell>
                          <TableCell>
                            {FetchingData.match<UserListing, React.ReactNode>(userListing, {
                              loading: () => <>{"—"}</>,
                              error: () => <>{"—"}</>,
                              success: (listing) => listing.communityAdminCount,
                            })}
                          </TableCell>
                        </TableRow>
                        <TableRow>
                          <TableCell>
                            <CustomTooltip title={t("usersPage.totalUsersTooltip")}>
                              <Abbr>{t("usersPage.totalUsers")}</Abbr>
                            </CustomTooltip>
                          </TableCell>
                          <TableCell>
                            {FetchingData.match<UserListing, React.ReactNode>(userListing, {
                              loading: () => <>{"—"}</>,
                              error: () => <>{"—"}</>,
                              success: (listing) => listing.totalUsersCount,
                            })}
                          </TableCell>
                        </TableRow>
                      </TableBody>
                    </Table>
                  </TableContainer>
                </Box>
                <Stack spacing={1.25}>
                  <Typography
                    variant="body2"
                    sx={{
                      maxWidth: 575,
                    }}
                  >
                    <TransRichText i18nKey="system:usersPage.intro" />
                  </Typography>
                  <Box sx={{ height: 4 }} />
                  <Box sx={{ width: "100%" }}>
                    {FetchingData.match(userListing, {
                      loading: () => (
                        <Typography
                          variant="body2"
                          sx={{
                            height: "36px",
                          }}
                        >
                          {t("usersPage.loadingListing")}
                        </Typography>
                      ),
                      error: (error) => (
                        <>
                          <Typography variant="body2">{t("usersPage.loadingError")}</Typography>
                          <samp>{error}</samp>
                        </>
                      ),
                      success: () => <></>,
                    })}
                    <SelectionActions selectedIds={selectedIds as ReadonlyArray<UserId>} fetchedListing={userListing} />
                    <Box sx={{ width: "100%" }}>
                      <DataGrid
                        sx={{
                          [`& .${tablePaginationClasses.selectLabel}`]: {
                            m: 0,
                          },
                        }}
                        aria-label={t("usersPage.tableLabel")}
                        autoHeight
                        columns={columns}
                        rows={FetchingData.match(userListing, {
                          loading: () => [] as Array<User>,
                          error: () => [] as Array<User>,
                          success: (listing) => listing.users,
                        })}
                        columnVisibilityModel={columnVisibility}
                        onColumnVisibilityModelChange={(model) => {
                          setColumnVisibility(model);
                        }}
                        density="standard"
                        getRowId={(row: User) => row.id}
                        hideFooterSelectedRowCount
                        checkboxSelection
                        disableRowSelectionExcludeModel
                        rowSelectionModel={rowSelectionModel}
                        onRowSelectionModelChange={(newRowSelectionModel: GridRowSelectionModel, _details) => {
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
                          loading: () => ({
                            page: 0,
                            pageSize: 0,
                          }),
                          error: () => ({
                            page: 0,
                            pageSize: 0,
                          }),
                          success: (listing) => ({
                            page: listing.page,
                            pageSize: listing.pageSize,
                          }),
                        })}
                        pageSizeOptions={FetchingData.match(userListing, {
                          loading: () => [0],
                          error: () => [0],
                          success: (listing) => paginationOptions(listing.totalListingCount),
                        })}
                        onPaginationModelChange={({ pageSize: newPageSize, page: newPage }) => {
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
                        onSortModelChange={(newSortModel: GridSortModel) => {
                          FetchingData.match(userListing, {
                            loading: () => {},
                            error: () => {},
                            success: (listing) => {
                              if (newSortModel.length === 0) {
                                setSortModel([]);
                                void listing.clearOrdering();
                                return;
                              }
                              const [{ field: newOrderBy, sort: newSortOrder }] = newSortModel;
                              setSortModel([
                                {
                                  field: newOrderBy,
                                  sort: newSortOrder,
                                },
                              ]);
                              const apiOrderBy = {
                                username: "username",
                                fileUsage: "fileUsage()",
                                recordCount: "recordCount()",
                                lastLogin: "lastLogin",
                                created: "creationDate",
                                fullNameSurnameFirst: "lastName",
                                firstName: "firstName",
                                lastName: "lastName",
                                email: "email",
                              }[newOrderBy];
                              if (!apiOrderBy) throw new Error(`Invalid order by: ${newOrderBy}`);
                              if (typeof newSortOrder !== "string")
                                throw new Error(`Invalid sort order: ${newSortOrder}`);
                              void listing.setOrdering(apiOrderBy, newSortOrder);
                            },
                          });
                        }}
                        slots={{
                          toolbar: UsersToolbar,
                        }}
                        slotProps={{
                          toolbar: {
                            userListing,
                            selectedCount: selectedIds.length,
                          },
                        }}
                        loading={FetchingData.match(userListing, {
                          loading: () => true,
                          error: () => false,
                          success: () => false,
                        })}
                        {...FetchingData.match(userListing, {
                          loading: () => ({
                            "aria-hidden": true,
                          }),
                          error: () => ({
                            "aria-hidden": true,
                          }),
                          success: () => ({}),
                        })}
                        showToolbar
                      />
                    </Box>
                    <Panel
                      anchorEl={groupsAnchorEl}
                      onClose={() => setGroupsAnchorEl(null)}
                      ariaLabel={t("usersPage.groupMembership.panelLabel")}
                    >
                      <List
                        sx={{
                          p: 0,
                        }}
                      >
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
                      ariaLabel={t("usersPage.tags.panelLabel")}
                    >
                      <List
                        sx={{
                          p: 0,
                        }}
                      >
                        {tagsList.map((tag) => (
                          <ListItem key={tag}>
                            <ListItemText primary={tag} />
                          </ListItem>
                        ))}
                      </List>
                    </Panel>
                  </Box>
                </Stack>
              </CardContent>
            </Card>
          </Box>
        </Alerts>
      </ErrorBoundary>
    </Analytics>
  );
};
const queryClient = new QueryClient();
const wrapperDiv = document.getElementById("sysadminUsers");
if (wrapperDiv) {
  const root = createRoot(wrapperDiv);
  root.render(
    <I18nRoot namespaces={["system", "common"]}>
      <QueryClientProvider client={queryClient}>
        <StyledEngineProvider injectFirst enableCssLayer>
          <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
            <UiPreferences>
              <UsersPage />
            </UiPreferences>
          </ThemeProvider>
        </StyledEngineProvider>
      </QueryClientProvider>
    </I18nRoot>,
  );
}
