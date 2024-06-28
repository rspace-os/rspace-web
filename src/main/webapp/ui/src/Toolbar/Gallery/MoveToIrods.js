//@flow

import React, { type Node } from "react";
import { ThemeProvider, styled } from "@mui/material/styles";
import createAccentedTheme from "../../accentedTheme";
import Dialog from "@mui/material/Dialog";
import Typography from "@mui/material/Typography";
import Toolbar from "@mui/material/Toolbar";
import AppBar from "@mui/material/AppBar";
import DialogContent from "@mui/material/DialogContent";
import Grid from "@mui/material/Grid";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import Link from "@mui/material/Link";
import Box from "@mui/material/Box";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import FormField from "../../components/Inputs/FormField";
import MenuItem from "@mui/material/MenuItem";
import Menu from "@mui/material/Menu";
import ListItemText from "@mui/material/ListItemText";
import ListItemButton from "@mui/material/ListItemButton";
import List from "@mui/material/List";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import ChoiceField from "../../components/Inputs/ChoiceField";
import TextField from "@mui/material/TextField";
import { withStyles } from "Styles";
import Stack from "@mui/material/Stack";
import useIrods, { type IrodsLocation } from "./useIrods";
import Alert from "@mui/lab/Alert";
import AlertTitle from "@mui/lab/AlertTitle";
import * as FetchingData from "../../util/fetchingData";
import ValidatingSubmitButton from "../../components/ValidatingSubmitButton";
import Result from "../../util/result";
import AccessibilityTips from "../../components/AccessibilityTips";
import docLinks from "../../assets/DocLinks";

const COLOR = {
  main: {
    hue: 180,
    saturation: 30,
    lightness: 80,
  },
  darker: {
    hue: 180,
    saturation: 30,
    lightness: 40,
  },
  contrastText: {
    hue: 180,
    saturation: 20,
    lightness: 29,
  },
  background: {
    hue: 180,
    saturation: 30,
    lightness: 80,
  },
  backgroundContrastText: {
    hue: 180,
    saturation: 20,
    lightness: 29,
  },
};

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

const CustomFieldset = withStyles<{| children: Node |}, { root: string }>(
  (theme) => ({
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
  })
)(({ classes, children }) => (
  <fieldset className={classes.root}>{children}</fieldset>
));

const ErrorAlert = ({ message }: {| message: string |}) => {
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

type MoveCopyDialogArgs = {|
  selectedIdsProp?: $ReadOnlyArray<string>,
  openProp?: boolean,
|};

function MoveCopyDialog({ selectedIdsProp, openProp }: MoveCopyDialogArgs) {
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [selectedIds, setSelectedIds] = React.useState<$ReadOnlyArray<string>>(
    []
  );

  React.useEffect(() => {
    if (typeof openProp !== "undefined") setDialogOpen(openProp);
  }, [openProp]);

  React.useEffect(() => {
    if (typeof selectedIdsProp !== "undefined") setSelectedIds(selectedIdsProp);
  }, [selectedIdsProp]);

  const irods = useIrods(selectedIds);
  const [locationsAnchorEl, setLocationsAnchorEl] = React.useState(null);
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
        : Result.Ok(null)
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
            : Result.Ok(null)
        )
      )
      .map(() => null);
  }

  React.useEffect(() => {
    const handler = (
      event: Event & { detail: { ids: Array<string>, ... }, ... }
    ) => {
      setSelectedIds(event.detail.ids);
      /*
       * selectedDestination is reset because the IrodsLocations are
       * paramaterised by the files that are being moved. This allows the
       * RESTful API to adjust the available locations based on the particular
       * files being moved.
       */
      setSelectedDestination(null);
      setDialogOpen(true);
    };
    window.addEventListener("OPEN_IRODS_DIALOG", handler);
    return () => {
      window.removeEventListener("OPEN_IRODS_DIALOG", handler);
    };
  }, []);

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
            // eslint-disable-next-line no-undef
            gallery();
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
            // eslint-disable-next-line no-undef
            gallery();
          })
          .finally(() => {
            setOperationInProgress(false);
          });
      });
    }
  };

  return (
    <CustomDialog open={dialogOpen} onClose={() => setDialogOpen(false)}>
      <AppBar position="relative" open={true}>
        <Toolbar variant="dense">
          <Typography variant="h6" noWrap component="h2">
            iRODS
          </Typography>
          <Box flexGrow={1}></Box>
          <Box ml={1}>
            <AccessibilityTips supportsHighContrastMode elementType="dialog" />
          </Box>
          <Box ml={1} sx={{ transform: "translateY(2px)" }}>
            <HelpLinkIcon title="iRODS help" link={docLinks.irods} />
          </Box>
        </Toolbar>
      </AppBar>
      <Box sx={{ display: "flex", height: "calc(100% - 48px)" }}>
        <Box
          sx={{
            height: "100%",
            display: "flex",
            flexDirection: "column",
            flexGrow: 1,
          }}
        >
          <form>
            <DialogContent>
              <Grid
                container
                direction="column"
                spacing={2}
                sx={{ height: "100%", flexWrap: "nowrap" }}
              >
                <Grid item>
                  <Typography variant="h3">Move to iRODS</Typography>
                </Grid>
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
                          iRODS items inside of RSpace documents and include
                          them into any exports through our iRODS integration.
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
          </form>
        </Box>
      </Box>
    </CustomDialog>
  );
}

type WrapperArgs = {|
  selectedIds?: $ReadOnlyArray<string>,
  open?: boolean,
|};

export default function Wrapper({ selectedIds, open }: WrapperArgs): Node {
  return (
    <ThemeProvider theme={createAccentedTheme(COLOR)}>
      <MoveCopyDialog selectedIdsProp={selectedIds} openProp={open} />
    </ThemeProvider>
  );
}
