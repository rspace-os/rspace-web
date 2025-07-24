import React from "react";
import { ThemeProvider, styled } from "@mui/material/styles";
import Box from "@mui/material/Box";
import { Dialog } from "../../components/DialogBoundary";
import createAccentedTheme from "../../accentedTheme";
import AppBar from "../../components/AppBar";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Grid from "@mui/material/Grid";
import Link from "@mui/material/Link";
import Typography from "@mui/material/Typography";
import InvApiService from "../../common/InvApiService";
import { doNotAwait } from "../../util/Util";
import { DataGridColumn } from "../../util/table";
import {
  GridToolbarContainer,
  GridToolbarColumnsButton,
  GridRowId,
} from "@mui/x-data-grid";
import useViewportDimensions from "../../util/useViewportDimensions";
import * as ArrayUtils from "../../util/ArrayUtils";
import * as Parsers from "../../util/parsers";
import Stack from "@mui/material/Stack";
import Button from "@mui/material/Button";
import ValidatingSubmitButton, {
  IsInvalid,
  IsValid,
} from "../../components/ValidatingSubmitButton";
import DialogActions from "@mui/material/DialogActions";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import CircularProgress from "@mui/material/CircularProgress";
import { type LinkableRecord } from "../../stores/definitions/LinkableRecord";
import docLinks from "../../assets/DocLinks";
import { ACCENT_COLOR } from "../../assets/branding/fieldmark";
import { DataGridWithRadioSelection } from "../../components/DataGridWithRadioSelection";
import Result from "@/util/result";
import FormControl from "@mui/material/FormControl";
import InputLabel from "@mui/material/InputLabel";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import FormHelperText from "@mui/material/FormHelperText";
import { useConfirm } from "@/components/ConfirmProvider";

/**
 * This class allows us to provide a link to the newly created container in the
 * success alert toast.
 */
class ResponseContainer implements LinkableRecord {
  id: number | null;
  globalId: string | null;
  name: string;

  constructor({ globalId, name }: { globalId: string; name: string }) {
    this.globalId = globalId;
    this.name = name;
    this.id = 0;
  }

  get recordTypeLabel(): string {
    return "Container";
  }

  get iconName(): string {
    return "container";
  }

  get permalinkURL(): string {
    if (!this.globalId) throw new Error("Impossible");
    return `/globalId/${this.globalId}`;
  }
}

const GridToolbar = ({
  setColumnsMenuAnchorEl,
}: {
  setColumnsMenuAnchorEl: (anchorEl: HTMLElement) => void;
}) => {
  /**
   * The columns menu can be opened by either tapping the "Columns" toolbar
   * button or by tapping the "Manage columns" menu item in each column's menu,
   * logic that is handled my MUI. We provide a custom `anchorEl` so that the
   * menu is positioned beneath the "Columns" toolbar button to be consistent
   * with the other toolbar menus, otherwise is appears far to the left. Rather
   * than having to hook into the logic that triggers the opening of the
   * columns menu in both places, we just set the `anchorEl` pre-emptively.
   */
  const columnMenuRef = React.useRef<HTMLElement | null>(null);
  React.useEffect(() => {
    if (columnMenuRef.current) setColumnsMenuAnchorEl(columnMenuRef.current);
  }, [setColumnsMenuAnchorEl]);

  return (
    <GridToolbarContainer sx={{ width: "100%" }}>
      <Box flexGrow={1}></Box>
      <GridToolbarColumnsButton
        ref={(node) => {
          if (node) columnMenuRef.current = node;
        }}
      />
    </GridToolbarContainer>
  );
};

const StyledGridOverlay = styled("div")(({ theme }) => ({
  display: "flex",
  flexDirection: "column",
  alignItems: "center",
  justifyContent: "center",
  height: "100%",
  backgroundColor: "rgba(18, 18, 18, 0.9)",
  ...theme.applyStyles("light", {
    backgroundColor: "rgba(255, 255, 255, 0.9)",
  }),
}));

function CustomLoadingOverlay() {
  return (
    <StyledGridOverlay>
      <CircularProgress variant="indeterminate" value={1} />
      <Box sx={{ mt: 2 }}>Fetching notebooks from Fieldmark…</Box>
    </StyledGridOverlay>
  );
}

type FieldmarkImportDialogArgs = {
  open: boolean;
  onClose: () => void;
};

type Notebook = {
  name: string;
  metadata: {
    project_id: string;
    ispublic: boolean;
    pre_description: string;
    project_lead: string;
  };
  status: string;
};

/**
 * Fieldmark is a third-party tool for collecting sample data in the field as
 * "records" of a "notebook". Our integration allows these notebooks to be
 * imported as containers, with each record imported as a subsample.
 *
 * This dialog provides a mechanism for choosing a Fieldmark notebook and
 * triggering an import.
 */
export default function FieldmarkImportDialog({
  open,
  onClose,
}: FieldmarkImportDialogArgs): React.ReactNode {
  const confirm = useConfirm();
  const { isViewportSmall } = useViewportDimensions();
  const { addAlert, removeAlert } = React.useContext(AlertContext);
  const [notebooks, setNotebooks] =
    React.useState<null | ReadonlyArray<Notebook>>(null);
  const [selectedNotebook, setSelectedNotebook] =
    React.useState<null | Notebook>(null);
  const [columnsMenuAnchorEl, setColumnsMenuAnchorEl] =
    React.useState<HTMLElement | null>(null);
  const [fetchingNotebooks, setFetchingNotebooks] = React.useState(false);
  const [importing, setImporting] = React.useState(false);
  const [fetchingIdentifierFields, setFetchingIdentifierFields] =
    React.useState(false);
  const [identifierFields, setIdentifierFields] =
    React.useState<ReadonlyArray<string> | null>(null);

  type IdentifierFieldSelection =
    | { type: "unselected" }
    | { type: "none" }
    | { type: "selected"; field: string };

  const [identifierFieldSelection, setIdentifierFieldSelection] =
    React.useState<IdentifierFieldSelection>({ type: "unselected" });

  React.useEffect(
    doNotAwait(async () => {
      if (!open) return;
      setFetchingNotebooks(true);
      setIdentifierFieldSelection({ type: "unselected" });
      setSelectedNotebook(null);
      setNotebooks(null);
      try {
        const { data } = await InvApiService.get<ReadonlyArray<Notebook>>(
          "/fieldmark/notebooks",
        );
        setNotebooks(data);
      } catch (e) {
        console.error(e);
        if (e instanceof Error) {
          const message = Parsers.objectPath(
            ["response", "data", "data", "validationErrors"],
            e,
          )
            .flatMap(Parsers.isArray)
            .flatMap(ArrayUtils.head)
            .flatMap(Parsers.isObject)
            .flatMap(Parsers.isNotNull)
            .flatMap(Parsers.getValueWithKey("message"))
            .flatMap(Parsers.isString)
            .orElse(e.message);
          addAlert(
            mkAlert({
              variant: "error",
              title: "Could not get notebooks from Fieldmark",
              message,
            }),
          );
        }
      } finally {
        setFetchingNotebooks(false);
      }
    }),
    [open],
  );

  async function importNotebook(notebook: Notebook) {
    setImporting(true);
    const importingAlert = mkAlert({
      variant: "notice",
      title: "Importing notebook",
      message: `Importing notebook "${notebook.name}" from Fieldmark.`,
      isInfinite: true,
    });
    addAlert(importingAlert);
    try {
      const { data } = await InvApiService.post<{
        containerName: string;
        containerGlobalId: string;
      }>("/import/fieldmark/notebook", {
        notebookId: notebook.metadata.project_id,
        ...(identifierFieldSelection.type === "selected"
          ? { identifier: identifierFieldSelection.field }
          : {}),
      });
      addAlert(
        mkAlert({
          variant: "success",
          message: "Successfully imported notebook.",
          details: [
            {
              variant: "success",
              title: data.containerName,
              record: new ResponseContainer({
                globalId: data.containerGlobalId,
                name: data.containerName,
              }),
            },
          ],
        }),
      );
    } catch (e) {
      console.error(e);
      if (e instanceof Error)
        addAlert(
          mkAlert({
            variant: "error",
            title: "Could not import notebook.",
            message: e.message,
          }),
        );
      throw e;
    } finally {
      setImporting(false);
      if (importingAlert) removeAlert(importingAlert);
    }
  }

  async function fetchIdentifierColumn(
    notebookId: Notebook["metadata"]["project_id"],
  ) {
    setFetchingIdentifierFields(true);
    setIdentifierFields(null);
    setIdentifierFieldSelection({ type: "unselected" });
    try {
      const { data } = await InvApiService.get(
        "/fieldmark/notebooks/igsn/" + notebookId,
      );
      setIdentifierFields(
        Parsers.isArray(data)
          .flatMap((fieldNames) =>
            Result.all(...fieldNames.map(Parsers.isString)),
          )
          .elseThrow(),
      );
    } catch (e) {
      console.error(e);
      setIdentifierFields([]);
    } finally {
      setFetchingIdentifierFields(false);
    }
  }

  function handleClose() {
    const resetState = () => {
      setNotebooks(null);
      setSelectedNotebook(null);
      setColumnsMenuAnchorEl(null);
      setFetchingNotebooks(false);
      setImporting(false);
      setFetchingIdentifierFields(false);
      setIdentifierFields(null);
      setIdentifierFieldSelection({ type: "unselected" });
    };

    if (!importing) {
      onClose();
      resetState();
      return;
    }
    confirm(
      "Importing in progress",
      "Are you sure you want to close this dialog? The import will continue in the background and there will be an alert when it completes.",
      "Yes, close",
      "No, keep open",
    ).then((confirmed) => {
      if (confirmed) {
        onClose();
        resetState();
      }
    });
  }
  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <Dialog
        open={open}
        onClose={handleClose}
        maxWidth="lg"
        fullWidth
        fullScreen={isViewportSmall}
      >
        <AppBar
          variant="dialog"
          currentPage="Fieldmark"
          accessibilityTips={{
            supportsHighContrastMode: true,
          }}
          helpPage={{
            docLink: docLinks.fieldmark,
            title: "Fieldmark help",
          }}
        />
        <DialogTitle variant="h3">Import from Fieldmark</DialogTitle>
        <DialogContent>
          <Grid
            container
            direction="column"
            spacing={2}
            sx={{ height: "100%", flexWrap: "nowrap" }}
          >
            <Grid item>
              <Typography
                variant="body2"
                sx={{ maxWidth: "54em" /* entirely arbitrary */ }}
              >
                Choose a Fieldmark notebook to import into Inventory. A Sample
                will be created for each record inside the notebook. A new list
                container will be placed on your bench, containing a singular
                subsample for each sample.
              </Typography>
              <Typography variant="body2">
                See{" "}
                <Link href="https://docs.fieldmark.au">docs.fieldmark.au</Link>{" "}
                and our{" "}
                <Link href={docLinks.fieldmark}>
                  Fieldmark integration docs
                </Link>{" "}
                for more.
              </Typography>
            </Grid>
            <Grid item>
              <DataGridWithRadioSelection
                columns={[
                  DataGridColumn.newColumnWithFieldName<"name", Notebook>(
                    "name",
                    {
                      headerName: "Name",
                      flex: 1,
                      sortable: false,
                    },
                  ),
                  DataGridColumn.newColumnWithFieldName<"status", Notebook>(
                    "status",
                    {
                      headerName: "Status",
                      flex: 1,
                      sortable: false,
                    },
                  ),
                  DataGridColumn.newColumnWithValueGetter<
                    "isPublic",
                    Notebook,
                    boolean
                  >("isPublic", (notebook) => notebook.metadata.ispublic, {
                    headerName: "Is Public",
                    flex: 1,
                    sortable: false,
                  }),
                  DataGridColumn.newColumnWithValueGetter<
                    "description",
                    Notebook,
                    string
                  >(
                    "description",
                    (notebook) => notebook.metadata.pre_description,
                    {
                      headerName: "Description",
                      flex: 1,
                      sortable: false,
                    },
                  ),
                  DataGridColumn.newColumnWithValueGetter<
                    "projectLead",
                    Notebook,
                    string
                  >(
                    "projectLead",
                    (notebook) => notebook.metadata.project_lead,
                    {
                      headerName: "Project Lead",
                      flex: 1,
                      sortable: false,
                    },
                  ),
                  DataGridColumn.newColumnWithValueGetter<
                    "id",
                    Notebook,
                    string
                  >("id", (notebook) => notebook.metadata.project_id, {
                    headerName: "Id",
                    flex: 1,
                    sortable: false,
                  }),
                ]}
                initialState={{
                  columns: {
                    columnVisibilityModel: {
                      status: !isViewportSmall,
                      isPublic: !isViewportSmall,
                      description: false,
                      projectLead: false,
                      id: false,
                    },
                  },
                }}
                rows={notebooks ?? []}
                selectedRowId={selectedNotebook?.metadata.project_id}
                onSelectionChange={(newSelectionId) => {
                  ArrayUtils.find(
                    (n) => n.metadata.project_id === newSelectionId,
                    notebooks ?? [],
                  ).do((newlySelectedNotebook) => {
                    setSelectedNotebook(newlySelectedNotebook);
                    void fetchIdentifierColumn(
                      newlySelectedNotebook.metadata.project_id,
                    );
                  });
                }}
                selectRadioAriaLabelFunc={(row) =>
                  `Select notebook: ${row.name}`
                }
                disableColumnFilter
                hideFooter
                autoHeight
                localeText={{
                  noRowsLabel: "No Notebooks",
                }}
                loading={fetchingNotebooks}
                slots={{
                  toolbar: GridToolbar,
                  loadingOverlay: CustomLoadingOverlay,
                }}
                slotProps={{
                  toolbar: {
                    setColumnsMenuAnchorEl,
                  },
                  panel: {
                    anchorEl: columnsMenuAnchorEl,
                  },
                }}
                getRowId={(row) => row.metadata.project_id}
              />
            </Grid>
            <Grid item>
              {selectedNotebook && (
                <>
                  {fetchingIdentifierFields ? (
                    <Box
                      sx={{
                        display: "flex",
                        alignItems: "center",
                        gap: 2,
                        ml: 2,
                      }}
                    >
                      <CircularProgress size={24} />
                      <Typography variant="body2">
                        Loading available identifier fields...
                      </Typography>
                    </Box>
                  ) : (
                    identifierFields &&
                    identifierFields.length > 0 && (
                      <FormControl sx={{ width: "auto", minWidth: 200 }}>
                        <InputLabel id="identifier-field-select-label">
                          Identifier Field
                        </InputLabel>
                        <Select
                          labelId="identifier-field-select-label"
                          id="identifier-field-select"
                          value={
                            identifierFieldSelection.type === "none"
                              ? "none"
                              : identifierFieldSelection.type === "selected"
                                ? identifierFieldSelection.field
                                : ""
                          }
                          label="Identifier Field"
                          onChange={(e) => {
                            const value = e.target.value;
                            if (value === "none") {
                              setIdentifierFieldSelection({ type: "none" });
                            } else if (value !== "") {
                              setIdentifierFieldSelection({
                                type: "selected",
                                field: value,
                              });
                            } else {
                              setIdentifierFieldSelection({
                                type: "unselected",
                              });
                            }
                          }}
                        >
                          <MenuItem value="none">
                            <em>Do not use an identifier</em>
                          </MenuItem>
                          {identifierFields.map((field) => (
                            <MenuItem key={field} value={field}>
                              {field}
                            </MenuItem>
                          ))}
                        </Select>
                        <FormHelperText>
                          Select a field to use as the identifier for imported
                          samples, or select 'Do not use an identifier' to
                          import without one
                        </FormHelperText>
                      </FormControl>
                    )
                  )}
                </>
              )}
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Grid container direction="row" spacing={1}>
            <Grid item sx={{ ml: "auto" }}>
              <Stack direction="row" spacing={1}>
                <Button onClick={() => handleClose()}>Close</Button>
                <ValidatingSubmitButton
                  onClick={() => {
                    if (selectedNotebook)
                      void importNotebook(selectedNotebook).then(() =>
                        handleClose(),
                      );
                  }}
                  validationResult={
                    !selectedNotebook
                      ? IsInvalid("No Notebook selected.")
                      : IsValid()
                  }
                  loading={importing}
                >
                  Import
                </ValidatingSubmitButton>
              </Stack>
            </Grid>
          </Grid>
        </DialogActions>
      </Dialog>
    </ThemeProvider>
  );
}
