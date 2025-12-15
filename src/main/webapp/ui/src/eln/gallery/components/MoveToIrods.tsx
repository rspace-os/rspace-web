import React from "react";
import { ThemeProvider, styled } from "@mui/material/styles";
import createAccentedTheme from "../../../accentedTheme";
import Dialog from "@mui/material/Dialog";
import Typography from "@mui/material/Typography";
import AppBar from "../../../components/AppBar";
import DialogContent from "@mui/material/DialogContent";
import Grid from "@mui/material/Grid";
import DialogActions from "@mui/material/DialogActions";
import DialogTitle from "@mui/material/DialogTitle";
import Button from "@mui/material/Button";
import Link from "@mui/material/Link";
import Box from "@mui/material/Box";
import FormField from "../../../components/Inputs/FormField";
import MenuItem from "@mui/material/MenuItem";
import Menu from "@mui/material/Menu";
import ListItemText from "@mui/material/ListItemText";
import ListItemButton from "@mui/material/ListItemButton";
import List from "@mui/material/List";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import ChoiceField from "../../../components/Inputs/ChoiceField";
import TextField from "@mui/material/TextField";
import { withStyles } from "Styles";
import Stack from "@mui/material/Stack";
import useIrods, { type IrodsLocation } from "./useIrods";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import * as FetchingData from "../../../util/fetchingData";
import ValidatingSubmitButton from "../../../components/ValidatingSubmitButton";
import Result from "../../../util/result";
import docLinks from "../../../assets/DocLinks";
import AnalyticsContext from "../../../stores/contexts/Analytics";
import { ACCENT_COLOR } from "../../../assets/branding/irods";

const CustomDialog = styled(Dialog)(() => ({
  "& .MuiDialog-container > .MuiPaper-root": {
    width: "530px",
    maxWidth: "530px",
    height: "calc(90% - 32px)", // 16px margin above and below dialog
  },
  "& .MuiDialogContent-root": {
    height: "calc(100% - 48px)", // 32px being the height of DialogActions + its own 16px of padding
    overflowY: "auto",
    paddingBottom: 0,
  },
  "& form": {
    height: "100%",
  },
}));

const CustomFieldset = withStyles<
  { children: React.ReactNode },
  { root: string }
>((theme) => ({
  root: {
    border: theme.borders.card,
    margin: 0,
    borderRadius: theme.spacing(0.5),
    padding: theme.spacing(2),
    paddingTop: theme.spacing(0.5),
    "& > legend": {
      padding: theme.spacing(0.25, 1),
      fontWeight: 700,
      fontSize: "1rem",
      letterSpacing: "0.02em",
      color: "#3b5958",
    },
    "& label": {
      fontSize: "0.9rem",
      letterSpacing: "0.03em",
    },
    "& input": {
      padding: theme.spacing(1),
    },
  },
}))(
  ({
    classes,
    children,
  }: {
    children: React.ReactNode;
    classes: { root: string };
  }) => <fieldset className={classes.root}>{children}</fieldset>,
);

const ErrorAlert = ({ message }: { message: string }) => {
  if (message === "No iRODS filestore configured")
    return (
      <Alert severity="error">
        <AlertTitle>No iRODS filestore has been configured.</AlertTitle>
        Add a new one in the filestore section of the Gallery or speak to your
        system administrator.
      </Alert>
    );
  return (
    <Alert severity="error">
      <AlertTitle>{message}</AlertTitle>
      Please check with your System Admin to ensure iRODS is correctly
      configured.
    </Alert>
  );
};

type MoveCopyDialogArgs = {
  selectedIds: ReadonlyArray<string>;
  dialogOpen: boolean;
  setDialogOpen: (open: boolean) => void;
};

function MoveCopyDialog({
  selectedIds,
  dialogOpen,
  setDialogOpen,
}: MoveCopyDialogArgs) {
  const { trackEvent } = React.useContext(AnalyticsContext);
  const irods = useIrods(selectedIds);
  const [locationsAnchorEl, setLocationsAnchorEl] =
    React.useState<HTMLElement | null>(null);
  const [selectedDestination, setSelectedDestination] =
    React.useState<IrodsLocation | null>(null);
  const [keepCopyInRspace, setKeepCopyInRspace] = React.useState(false);
  const [username, setUsername] = React.useState("");
  const [password, setPassword] = React.useState("");
  const [operationInProgress, setOperationInProgress] = React.useState(false);

  /*
   * After the first authenticated request in a session, the server caches the
   * credentials so that it is not necessary re-type password over and over.
   */
  const [showUsernamePasswordForm, setShowUsernamePasswordForm] =
    React.useState(true);

  function validateState(): Result<null> {
    return Result.all(
      irods.tag === "loading"
        ? Result.Error<null>([new Error("Loading available locations")])
        : Result.Ok(null),
      irods.tag === "error"
        ? Result.Error([new Error(irods.error)])
        : Result.Ok(null),
    )
      .flatMap(() =>
        Result.all(
          selectedDestination
            ? Result.Ok(null)
            : Result.Error<null>([new Error("A destination is required.")]),
          showUsernamePasswordForm && username === ""
            ? Result.Error([
                new Error("Username for iRODS server is required."),
              ])
            : Result.Ok(null),
          showUsernamePasswordForm && password === ""
            ? Result.Error([
                new Error("Password for iRODS server is required."),
              ])
            : Result.Ok(null),
        ),
      )
      .map(() => null);
  }

  React.useEffect(() => {
    /*
     * selectedDestination is reset because the IrodsLocations are
     * paramaterised by the files that are being moved. This allows the RESTful
     * API to adjust the available locations based on the particular files
     * being moved.
     */
    setSelectedDestination(null);
  }, [selectedIds]);

  const onSubmit = () => {
    if (!selectedDestination)
      throw new Error("A destination has not bee selected");
    if (keepCopyInRspace) {
      selectedDestination.copy.do((c) => {
        setOperationInProgress(true);
        void c({ username, password })
          .then(() => {
            setDialogOpen(false);
            setShowUsernamePasswordForm(false);
            trackEvent("user:copy:file:irods");
          })
          .finally(() => {
            setOperationInProgress(false);
          });
      });
    } else {
      selectedDestination.move.do((m) => {
        setOperationInProgress(true);
        void m({ username, password })
          .then(() => {
            setDialogOpen(false);
            setShowUsernamePasswordForm(false);
            trackEvent("user:move:file:irods");
          })
          .finally(() => {
            setOperationInProgress(false);
          });
      });
    }
  };

  return (
    <CustomDialog
      open={dialogOpen}
      onClose={() => setDialogOpen(false)}
      onKeyDown={(e) => {
        e.stopPropagation();
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
          <DialogContent>
            <Grid
              container
              direction="column"
              spacing={2}
              sx={{ height: "100%", flexWrap: "nowrap" }}
            >
              {FetchingData.match(irods, {
                loading: () => <></>,
                error: (errorMsg) => (
                  <Grid item>
                    <ErrorAlert message={errorMsg} />
                  </Grid>
                ),
                success: ({ serverUrl, configuredLocations }) => (
                  <>
                    <Grid item>
                      <Typography variant="body2">
                        You have selected {selectedIds.length} item
                        {selectedIds.length > 1 && "s"} to move to the iRODS
                        server{" "}
                        <Link target="_blank" href={serverUrl}>
                          {serverUrl}
                        </Link>
                        . By default, the items will be added to iRODS and
                        removed from RSpace. You will be able to link to the
                        iRODS items inside of RSpace documents and include them
                        into any exports through our iRODS integration.
                      </Typography>
                    </Grid>
                    <Grid item>
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
                    </Grid>
                    <Grid item>
                      <FormField
                        label="Destination in iRODS"
                        explanation="The available folders are configured in the Gallery's filestore section."
                        value={void 0}
                        renderInput={() => (
                          <Box>
                            <List>
                              <ListItemButton
                                sx={{ maxWidth: "400px" }}
                                onClick={(e) =>
                                  setLocationsAnchorEl(e.currentTarget)
                                }
                              >
                                <ListItemText
                                  primary={
                                    selectedDestination?.name ??
                                    "Select a destination"
                                  }
                                  secondary={selectedDestination?.path ?? ""}
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
                                    secondary={location.path}
                                  />
                                </MenuItem>
                              ))}
                            </Menu>
                          </Box>
                        )}
                      />
                    </Grid>
                    {showUsernamePasswordForm && (
                      <Grid item>
                        <CustomFieldset>
                          <legend>iRODS login</legend>
                          <Stack spacing={1}>
                            <Typography variant="body2">
                              Please provide your login credentials for{" "}
                              {serverUrl}
                            </Typography>
                            <FormField
                              label="Username"
                              value={username}
                              renderInput={() => (
                                <TextField
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
                              renderInput={() => (
                                <TextField
                                  type="password"
                                  autoComplete="current-password"
                                  onChange={({ target: { value } }) => {
                                    setPassword(value);
                                  }}
                                />
                              )}
                            />
                          </Stack>
                        </CustomFieldset>
                      </Grid>
                    )}
                  </>
                ),
              })}
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button
              onClick={() => setDialogOpen(false)}
              disabled={operationInProgress}
            >
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
    </CustomDialog>
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
export default function Wrapper({
  selectedIds,
  dialogOpen,
  setDialogOpen,
}: WrapperArgs): React.ReactNode {
  return (
    <ThemeProvider theme={accentTheme}>
      <MoveCopyDialog
        selectedIds={selectedIds}
        dialogOpen={dialogOpen}
        setDialogOpen={setDialogOpen}
      />
    </ThemeProvider>
  );
}
