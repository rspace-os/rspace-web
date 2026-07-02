import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemText from "@mui/material/ListItemText";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import Stack from "@mui/material/Stack";
import { ThemeProvider } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import React from "react";
import createAccentedTheme from "../../../accentedTheme";
import { ACCENT_COLOR } from "../../../assets/branding/irods";
import docLinks from "../../../assets/DocLinks";
import AppBar from "../../../components/AppBar";
import ChoiceField from "../../../components/Inputs/ChoiceField";
import FormField from "../../../components/Inputs/FormField";
import ValidatingSubmitButton from "../../../components/ValidatingSubmitButton";
import AnalyticsContext from "../../../stores/contexts/Analytics";
import * as FetchingData from "../../../util/fetchingData";
import Result from "../../../util/result";
import useIrods, { type IrodsCredentials, type IrodsLocation } from "./useIrods";

type MoveCopyDialogArgs = {
  selectedIds: ReadonlyArray<string>;
  dialogOpen: boolean;
  setDialogOpen: (open: boolean) => void;
};

function MoveCopyDialog({ selectedIds, dialogOpen, setDialogOpen }: MoveCopyDialogArgs) {
  const { trackEvent } = React.useContext(AnalyticsContext);
  const irods = useIrods();
  const [locationsAnchorEl, setLocationsAnchorEl] = React.useState<HTMLElement | null>(null);
  const [selectedDestination, setSelectedDestination] = React.useState<IrodsLocation | null>(null);
  const [keepCopyInRspace, setKeepCopyInRspace] = React.useState(false);
  const [username, setUsername] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [operationInProgress, setOperationInProgress] = React.useState(false);

  /*
   * iRODS authentication is per-filesystem, and in one dialog session the user
   * may pick destinations spanning several iRODS servers. We therefore track
   * credentials keyed by filesystem id rather than globally. An entry is added
   * once a move/copy to that filesystem succeeds (mirroring the server-side
   * credential cache), so subsequent operations to the same server don't
   * re-prompt; selecting a destination on a different server does.
   */
  const [credentialsByFilesystem, setCredentialsByFilesystem] = React.useState<Map<number, IrodsCredentials>>(
    new Map(),
  );

  /*
   * Show the login form for the selected destination unless its filesystem
   * uses no authentication, or we already hold credentials for it. This is the
   * same rule that makes the S3 flow (authType === "NONE") prompt-free, applied
   * per destination.
   */
  const needsCredentials = selectedDestination !== null && selectedDestination.authType !== "NONE";
  const hasCachedCredentials =
    selectedDestination !== null && credentialsByFilesystem.has(selectedDestination.filesystemId);
  const showUsernamePasswordForm = needsCredentials && !hasCachedCredentials;

  function validateState(): Result<null> {
    return Result.all(
      irods.tag === "loading" ? Result.Error<null>([new Error("Loading available locations")]) : Result.Ok(null),
      irods.tag === "error" ? Result.Error([new Error(irods.error)]) : Result.Ok(null),
    )
      .flatMap(() =>
        Result.all(
          selectedDestination ? Result.Ok(null) : Result.Error<null>([new Error("A destination is required.")]),
          showUsernamePasswordForm && username === ""
            ? Result.Error([new Error("Username for iRODS server is required.")])
            : Result.Ok(null),
          showUsernamePasswordForm && password === ""
            ? Result.Error([new Error("Password for iRODS server is required.")])
            : Result.Ok(null),
        ),
      )
      .map(() => null);
  }

  React.useEffect(() => {
    // Clear the chosen destination when the set of selected files changes, so a
    // reused dialog doesn't carry a stale selection across to a new operation.
    setSelectedDestination(null);
  }, [selectedIds]);

  /*
   * Clear the working username/password whenever the selected destination's
   * filesystem changes, so credentials typed for one iRODS server are never
   * carried over to another. Switching between folders on the same server
   * keeps them.
   */
  const selectedFilesystemId = selectedDestination?.filesystemId ?? null;
  React.useEffect(() => {
    setUsername("");
    setPassword("");
  }, [selectedFilesystemId]);

  const onSubmit = () => {
    if (!selectedDestination) throw new Error("A destination has not been selected");
    const recordIds: ReadonlyArray<number> = selectedIds.flatMap((id) => {
      const n = parseInt(id, 10);
      return Number.isNaN(n) ? [] : [n];
    });
    const { filesystemId, authType } = selectedDestination;
    const credentials = credentialsByFilesystem.get(filesystemId) ?? { username, password };
    const operation = keepCopyInRspace ? selectedDestination.copy : selectedDestination.move;
    setOperationInProgress(true);
    void operation(recordIds, credentials)
      .then(() => {
        setDialogOpen(false);
        // Remember credentials for this filesystem so further operations to the
        // same iRODS server within this session don't re-prompt (NONE-auth
        // filesystems never collect or cache credentials).
        if (authType !== "NONE") {
          setCredentialsByFilesystem((prev) => new Map(prev).set(filesystemId, credentials));
        }
        trackEvent(keepCopyInRspace ? "user:copy:file:irods" : "user:move:file:irods");
      })
      .finally(() => {
        setOperationInProgress(false);
      });
  };

  return (
    <Dialog
      open={dialogOpen}
      onClose={() => setDialogOpen(false)}
      onKeyDown={(e) => {
        e.stopPropagation();
      }}
      slotProps={{
        paper: {
          sx: {
            width: "530px",
            maxWidth: "530px",
            height: "calc(90% - 32px)",
          },
        },
      }}
    >
      <AppBar
        variant="dialog"
        currentPage="iRODS"
        accessibilityTips={{
          supportsHighContrastMode: true,
        }}
        helpPage={{
          docLink: docLinks.irods,
          title: "iRODS help",
        }}
      />
      <Box sx={{ display: "flex", height: "calc(100% - 48px)" }}>
        <Box
          component="form"
          sx={{
            height: "100%",
            display: "flex",
            flexDirection: "column",
            flexGrow: 1,
          }}
        >
          <DialogTitle variant="h3">Move to iRODS</DialogTitle>
          <DialogContent
            sx={{
              height: "calc(100% - 48px)",
              overflowY: "auto",
              paddingBottom: 0,
            }}
          >
            <Stack
              spacing={2}
              sx={{
                height: "100%",
                flexWrap: "nowrap",
              }}
            >
              {FetchingData.match(irods, {
                loading: () => <></>,
                error: (errorMsg) => (
                  <Alert severity="error">
                    <AlertTitle>{errorMsg}</AlertTitle>
                    Please check with your System Admin to ensure iRODS is correctly configured.
                  </Alert>
                ),
                success: (configuredLocations) =>
                  configuredLocations.length === 0 ? (
                    <Alert severity="error">
                      <AlertTitle>No iRODS filestore has been configured.</AlertTitle>
                      Add a new one in the filestore section of the Gallery or speak to your system administrator.
                    </Alert>
                  ) : (
                    <>
                      <Typography variant="body2">
                        You have selected {selectedIds.length} item
                        {selectedIds.length > 1 && "s"} to move to iRODS. By default, the items will be added to iRODS
                        and removed from RSpace. You will be able to link to the iRODS items inside of RSpace documents
                        and include them into any exports through our iRODS integration.
                      </Typography>
                      <ChoiceField
                        name="keep"
                        value={keepCopyInRspace ? ["keep"] : []}
                        onChange={({ target: { value } }) => {
                          setKeepCopyInRspace(value.includes("keep"));
                        }}
                        options={[
                          {
                            value: "keep",
                            label: "Retain a copy in RSpace",
                          },
                        ]}
                      />
                      <FormField
                        label="Destination in iRODS"
                        explanation="The available folders are configured in the Gallery's filestore section."
                        value={void 0}
                        renderInput={() => (
                          <Box>
                            <List>
                              <ListItemButton
                                sx={{ maxWidth: "400px" }}
                                onClick={(e) => setLocationsAnchorEl(e.currentTarget)}
                              >
                                <ListItemText
                                  primary={selectedDestination?.name ?? "Select a destination"}
                                  secondary={
                                    selectedDestination
                                      ? `${selectedDestination.path} · ${selectedDestination.filesystemName}`
                                      : ""
                                  }
                                />
                                <KeyboardArrowDownIcon />
                              </ListItemButton>
                            </List>
                            <Menu
                              open={Boolean(locationsAnchorEl)}
                              anchorEl={locationsAnchorEl}
                              onClose={() => {
                                setLocationsAnchorEl(null);
                              }}
                            >
                              {configuredLocations.map((location) => (
                                <MenuItem
                                  key={location.id}
                                  selected={location === selectedDestination}
                                  onClick={() => {
                                    setSelectedDestination(location);
                                    setLocationsAnchorEl(null);
                                  }}
                                  sx={{ width: "400px" }}
                                >
                                  <ListItemText
                                    primary={location.name}
                                    secondary={`${location.path} · ${location.filesystemName}`}
                                  />
                                </MenuItem>
                              ))}
                            </Menu>
                          </Box>
                        )}
                      />
                      {showUsernamePasswordForm && (
                        <Box
                          component="fieldset"
                          sx={(theme) => ({
                            border: theme.borders.card,
                            margin: 0,
                            borderRadius: theme.spacing(0.5),
                            padding: theme.spacing(2),
                            paddingTop: theme.spacing(0.5),
                            "& label": {
                              fontSize: "0.9rem",
                              letterSpacing: "0.03em",
                            },
                            "& input": {
                              padding: theme.spacing(1),
                            },
                          })}
                        >
                          <Box
                            component="legend"
                            sx={(theme) => ({
                              padding: theme.spacing(0.25, 1),
                              fontWeight: 700,
                              fontSize: "1rem",
                              letterSpacing: "0.02em",
                              color: "#3b5958",
                            })}
                          >
                            iRODS login
                          </Box>
                          <Stack spacing={1}>
                            <Typography variant="body2">
                              Please provide your login credentials for {selectedDestination?.filesystemName}
                              {selectedDestination?.filesystemUrl ? ` (${selectedDestination.filesystemUrl})` : ""}
                            </Typography>
                            <FormField
                              label="Username"
                              value={username}
                              renderInput={({ id }) => (
                                <TextField
                                  id={id}
                                  value={username}
                                  autoComplete="username"
                                  onChange={({ target: { value } }) => {
                                    setUsername(value);
                                  }}
                                />
                              )}
                            />
                            <FormField
                              label="Password"
                              value={password}
                              renderInput={({ id }) => (
                                <TextField
                                  id={id}
                                  type="password"
                                  value={password}
                                  autoComplete="current-password"
                                  onChange={({ target: { value } }) => {
                                    setPassword(value);
                                  }}
                                />
                              )}
                            />
                          </Stack>
                        </Box>
                      )}
                    </>
                  ),
              })}
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDialogOpen(false)} disabled={operationInProgress}>
              Cancel
            </Button>
            <ValidatingSubmitButton
              validationResult={validateState()}
              loading={operationInProgress}
              onClick={() => {
                onSubmit();
              }}
            >
              Move
            </ValidatingSubmitButton>
          </DialogActions>
        </Box>
      </Box>
    </Dialog>
  );
}

type WrapperArgs = {
  selectedIds: ReadonlyArray<string>;
  dialogOpen: boolean;
  setDialogOpen: (open: boolean) => void;
};

const accentTheme = Object.freeze(createAccentedTheme(ACCENT_COLOR));

/**
 * A dialog for copying or moving files to an iRODS server.
 */
export default function Wrapper({ selectedIds, dialogOpen, setDialogOpen }: WrapperArgs): React.ReactNode {
  return (
    <ThemeProvider theme={accentTheme}>
      <MoveCopyDialog selectedIds={selectedIds} dialogOpen={dialogOpen} setDialogOpen={setDialogOpen} />
    </ThemeProvider>
  );
}
