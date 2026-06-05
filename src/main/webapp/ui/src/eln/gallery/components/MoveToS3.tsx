import React from "react";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "../../../accentedTheme";
import Dialog from "@mui/material/Dialog";
import Typography from "@mui/material/Typography";
import AppBar from "../../../components/AppBar";
import DialogContent from "@mui/material/DialogContent";
import Grid from "@mui/material/Grid";
import DialogActions from "@mui/material/DialogActions";
import DialogTitle from "@mui/material/DialogTitle";
import Button from "@mui/material/Button";
import Box from "@mui/material/Box";
import FormField from "../../../components/Inputs/FormField";
import MenuItem from "@mui/material/MenuItem";
import Menu from "@mui/material/Menu";
import ListItemText from "@mui/material/ListItemText";
import ListItemButton from "@mui/material/ListItemButton";
import ListItem from "@mui/material/ListItem";
import List from "@mui/material/List";
import KeyboardArrowDownIcon from "@mui/icons-material/KeyboardArrowDown";
import ChoiceField from "../../../components/Inputs/ChoiceField";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import * as FetchingData from "../../../util/fetchingData";
import ValidatingSubmitButton from "../../../components/ValidatingSubmitButton";
import Result from "../../../util/result";
import AnalyticsContext from "../../../stores/contexts/Analytics";
import { ACCENT_COLOR } from "../../../assets/branding/s3";
import useS3Filestores, {
  type S3Filestore,
  type S3TransferSource,
} from "./useS3Filestores";


const NoFilestoreAlert = () => (
  <Alert severity="error">
    <AlertTitle>No S3 filestore has been configured.</AlertTitle>
    Add a new one in the filestore section of the Gallery or speak to your
    system administrator.
  </Alert>
);

const NoWritableFilestoreAlert = () => (
  <Alert severity="error">
    <AlertTitle>You do not have write access to any S3 filestore.</AlertTitle>
    Your account is not on the write allowlist for any S3 filestore. Ask your
    system administrator if you need write access.
  </Alert>
);

type MoveCopyDialogArgs = {
  dialogOpen: boolean;
  setDialogOpen: (open: boolean) => void;
} & (
  | { selectedIds: ReadonlyArray<string>; transferSources?: never }
  | { selectedIds?: never; transferSources: ReadonlyArray<S3TransferSource> }
);

function MoveCopyDialog({
  dialogOpen,
  setDialogOpen,
  selectedIds,
  transferSources,
}: MoveCopyDialogArgs) {
  const isTransferMode = transferSources !== undefined;
  const sourceFilestoreIds = React.useMemo(
    () => new Set(transferSources?.map((s) => s.sourceFilestoreId) ?? []),
    [transferSources],
  );
  const { trackEvent } = React.useContext(AnalyticsContext);
  const s3Filestores = useS3Filestores();
  const [destinationAnchorEl, setDestinationAnchorEl] =
    React.useState<HTMLElement | null>(null);
  const [selectedFilestore, setSelectedFilestore] =
    React.useState<S3Filestore | null>(null);
  const [retainSourceCopy, setRetainSourceCopy] = React.useState(false);
  const [operationInProgress, setOperationInProgress] = React.useState(false);

  function validateState(): Result<null> {
    return Result.all(
      s3Filestores.tag === "loading"
        ? Result.Error<null>([new Error("Loading available S3 filestores")])
        : Result.Ok(null),
      s3Filestores.tag === "error"
        ? Result.Error([new Error(s3Filestores.error)])
        : Result.Ok(null),
    )
      .flatMap(() =>
        selectedFilestore
          ? Result.Ok(null)
          : Result.Error<null>([
              new Error("A destination filestore is required."),
            ]),
      )
      .map(() => null);
  }

  React.useEffect(() => {
    setSelectedFilestore(null);
  }, [selectedIds, transferSources]);

  const onSubmit = () => {
    if (!selectedFilestore) throw new Error("No filestore selected");
    setOperationInProgress(true);
    if (isTransferMode) {
      void selectedFilestore
        .transfer(transferSources, !retainSourceCopy)
        .then(() => {
          setDialogOpen(false);
          trackEvent("user:transfer:file:s3");
        })
        .finally(() => {
          setOperationInProgress(false);
        });
    } else {
      const recordIds: ReadonlyArray<number> = (selectedIds ?? []).flatMap(
        (id) => {
          const n = parseInt(id, 10);
          return isNaN(n) ? [] : [n];
        },
      );
      const op = retainSourceCopy ? "copy" : "move";
      void (retainSourceCopy
        ? selectedFilestore.copy(recordIds)
        : selectedFilestore.move(recordIds)
      )
        .then(() => {
          setDialogOpen(false);
          trackEvent(`user:${op}:file:s3`);
        })
        .finally(() => {
          setOperationInProgress(false);
        });
    }
  };

  const itemCount = isTransferMode
    ? transferSources.length
    : (selectedIds ?? []).length;

  return (
    <Dialog
      open={dialogOpen}
      onClose={() => setDialogOpen(false)}
      onKeyDown={(e) => {
        e.stopPropagation();
      }}
      PaperProps={{ sx: { width: "530px", maxWidth: "530px" } }}
    >
      <AppBar
        variant="dialog"
        currentPage="S3"
        accessibilityTips={{
          supportsHighContrastMode: true,
        }}
      />
      <Box sx={{ display: "flex" }}>
        <Box
          component="form"
          sx={{
            display: "flex",
            flexDirection: "column",
            flexGrow: 1,
          }}
        >
          <DialogTitle variant="h3">
            {isTransferMode ? "Transfer to S3" : "Move to S3"}
          </DialogTitle>
          <DialogContent>
            <Grid container direction="column" spacing={2}>
              {FetchingData.match(s3Filestores, {
                loading: () => <></>,
                error: (errorMsg) => (
                  <Grid item>
                    <Alert severity="error">
                      <AlertTitle>{errorMsg}</AlertTitle>
                      Please check with your System Admin to ensure the S3
                      filestore is correctly configured.
                    </Alert>
                  </Grid>
                ),
                success: (filestores) =>
                  filestores.length === 0 ? (
                    <Grid item>
                      <NoFilestoreAlert />
                    </Grid>
                  ) : filestores.every((fs) => !fs.canWrite) ? (
                    <Grid item>
                      <NoWritableFilestoreAlert />
                    </Grid>
                  ) : (
                    <>
                      <Grid item>
                        <Typography variant="body2">
                          {isTransferMode ? (
                            <>
                              You have selected {itemCount} item
                              {itemCount > 1 && "s"} to transfer to another S3
                              bucket. By default, the items will be copied to
                              the destination and deleted from the source.
                            </>
                          ) : (
                            <>
                              You have selected {itemCount} item
                              {itemCount > 1 && "s"} to move to S3. By default,
                              the items will be added to S3 and removed from
                              RSpace. You will be able to link to the S3 items
                              inside of RSpace documents.
                            </>
                          )}
                        </Typography>
                      </Grid>
                      <Grid item>
                        <ChoiceField
                          name="keep"
                          value={retainSourceCopy ? ["keep"] : []}
                          onChange={({ target: { value } }) => {
                            setRetainSourceCopy(value.includes("keep"));
                          }}
                          options={[
                            {
                              value: "keep",
                              label: isTransferMode
                                ? "Retain a copy on source bucket"
                                : "Retain a copy in RSpace",
                            },
                          ]}
                        />
                      </Grid>
                      <Grid item>
                        <FormField
                          label="Destination S3 filestore"
                          explanation="The available filestores are configured in the Gallery's filestore section."
                          value={void 0}
                          renderInput={() => (
                            <Box>
                              <List>
                                <ListItem disablePadding>
                                  <ListItemButton
                                    sx={{ maxWidth: "400px" }}
                                    onClick={(e) =>
                                      setDestinationAnchorEl(e.currentTarget)
                                    }
                                  >
                                    <ListItemText
                                      primary={
                                        selectedFilestore?.name ??
                                        "Select a filestore"
                                      }
                                    />
                                    <KeyboardArrowDownIcon />
                                  </ListItemButton>
                                </ListItem>
                              </List>
                              <Menu
                                open={Boolean(destinationAnchorEl)}
                                anchorEl={destinationAnchorEl}
                                onClose={() => {
                                  setDestinationAnchorEl(null);
                                }}
                              >
                                {filestores
                                .filter(
                                  (fs) =>
                                    !isTransferMode ||
                                    !sourceFilestoreIds.has(fs.id),
                                )
                                .map((fs) => (
                                  <MenuItem
                                    key={fs.id}
                                    selected={fs === selectedFilestore}
                                    disabled={!fs.canWrite}
                                    onClick={() => {
                                      setSelectedFilestore(fs);
                                      setDestinationAnchorEl(null);
                                    }}
                                    sx={{ width: "400px" }}
                                  >
                                    <ListItemText
                                      primary={fs.name}
                                      secondary={
                                        fs.canWrite
                                          ? undefined
                                          : "No write access"
                                      }
                                    />
                                  </MenuItem>
                                ))}
                              </Menu>
                            </Box>
                          )}
                        />
                      </Grid>
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
              {isTransferMode
                ? "Transfer"
                : retainSourceCopy
                  ? "Copy"
                  : "Move"}
            </ValidatingSubmitButton>
          </DialogActions>
        </Box>
      </Box>
    </Dialog>
  );
}

export type { S3TransferSource };

type WrapperArgs = {
  dialogOpen: boolean;
  setDialogOpen: (open: boolean) => void;
} & (
  | { selectedIds: ReadonlyArray<string>; transferSources?: never }
  | { selectedIds?: never; transferSources: ReadonlyArray<S3TransferSource> }
);

const accentTheme = Object.freeze(createAccentedTheme(ACCENT_COLOR));

/**
 * A dialog for copying, moving, or transferring files to an S3 filestore.
 */
export default function Wrapper(props: WrapperArgs): React.ReactNode {
  return (
    <ThemeProvider theme={accentTheme}>
      <MoveCopyDialog {...props} />
    </ThemeProvider>
  );
}
