//@flow

import React, { type Node } from "react";
import { ThemeProvider, styled } from "@mui/material/styles";
import Box from "@mui/material/Box";
import { Dialog } from "../../components/DialogBoundary";
import createAccentedTheme from "../../accentedTheme";
import Toolbar from "@mui/material/Toolbar";
import AppBar from "@mui/material/AppBar";
import AccessibilityTips from "../../components/AccessibilityTips";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Grid from "@mui/material/Grid";
import Link from "@mui/material/Link";
import Typography from "@mui/material/Typography";
import InvApiService from "../../common/InvApiService";
import { doNotAwait } from "../../util/Util";
import { DataGridColumn } from "../../util/table";
import {
  DataGrid,
  GridToolbarContainer,
  GridToolbarColumnsButton,
} from "@mui/x-data-grid";
import Radio from "@mui/material/Radio";
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

export const FIELDMARK_COLOR = {
  main: {
    hue: 82,
    saturation: 80,
    lightness: 33,
  },
  darker: {
    hue: 82,
    saturation: 80,
    lightness: 22,
  },
  contrastText: {
    hue: 82,
    saturation: 80,
    lightness: 19,
  },
  background: {
    hue: 82,
    saturation: 46,
    lightness: 66,
  },
  backgroundContrastText: {
    hue: 82,
    saturation: 70,
    lightness: 22,
  },
};

/**
 * This class allows us to provide a link to the newly created container in the
 * success alert toast.
 */
class ResponseContainer implements LinkableRecord {
  id: ?number;
  globalId: ?string;
  name: string;

  constructor({ globalId, name }: {| globalId: string, name: string |}) {
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
}: {|
  setColumnsMenuAnchorEl: (HTMLElement) => void,
|}) => {
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

  return (
    <GridToolbarContainer sx={{ width: "100%" }}>
      <Box flexGrow={1}></Box>
      <GridToolbarColumnsButton
        variant="outlined"
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

type FieldmarkImportDialogArgs = {|
  open: boolean,
  onClose: () => void,
|};

type Notebook = {
  name: string,
  metadata: {
    project_id: string,
    ispublic: string,
    pre_description: string,
    project_lead: string,
    ...
  },
  ...
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
}: FieldmarkImportDialogArgs): Node {
  const { isViewportSmall } = useViewportDimensions();
  const { addAlert } = React.useContext(AlertContext);
  const [notebooks, setNotebooks] =
    React.useState<null | $ReadOnlyArray<Notebook>>(null);
  const [selectedNotebook, setSelectedNotebook] =
    React.useState<null | Notebook>(null);
  const [columnsMenuAnchorEl, setColumnsMenuAnchorEl] =
    React.useState<?HTMLElement>(null);
  const [fetchingNotebooks, setFetchingNotebooks] = React.useState(false);
  const [importing, setImporting] = React.useState(false);

  React.useEffect(
    doNotAwait(async () => {
      if (!open) return;
      setFetchingNotebooks(true);
      try {
        const { data } = await InvApiService.get<
          mixed,
          $ReadOnlyArray<Notebook>
        >("/fieldmark/notebooks");
        setNotebooks(data);
      } catch (e) {
        console.error(e);
        const message = Parsers.objectPath(
          ["response", "data", "data", "validationErrors"],
          e
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
          })
        );
      } finally {
        setFetchingNotebooks(false);
      }
    }),
    [open]
  );

  async function importNotebook(notebook: Notebook) {
    setImporting(true);
    try {
      const { data } = await InvApiService.post<
        { id: string },
        { containerName: string, containerGlobalId: string, ... }
      >("/import/fieldmark/notebook", {
        notebookId: notebook.metadata.project_id,
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
        })
      );
    } catch (e) {
      console.error(e);
      addAlert(
        mkAlert({
          variant: "error",
          title: "Could not import notebook.",
          message: e.message,
        })
      );
      throw e;
    } finally {
      setImporting(false);
    }
  }

  return (
    <ThemeProvider theme={createAccentedTheme(FIELDMARK_COLOR)}>
      <Dialog
        open={open}
        onClose={onClose}
        maxWidth="lg"
        fullWidth
        fullScreen={isViewportSmall}
      >
        <AppBar position="relative" open={true}>
          <Toolbar variant="dense">
            <Typography variant="h6" noWrap component="h2">
              Fieldmark
            </Typography>
            <Box flexGrow={1}></Box>
            <Box ml={1}>
              <AccessibilityTips
                supportsHighContrastMode
                elementType="dialog"
              />
            </Box>
            <Box ml={1} sx={{ transform: "translateY(2px)" }}>
              <HelpLinkIcon title="Fieldmark help" link={docLinks.fieldmark} />
            </Box>
          </Toolbar>
        </AppBar>
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
              <DataGrid
                columns={[
                  {
                    field: "radio",
                    headerName: "Select",
                    renderCell: (params: { row: Notebook, ... }) => (
                      <Radio
                        color="primary"
                        value={
                          selectedNotebook?.metadata.project_id ===
                          params.row.metadata.project_id
                        }
                        checked={
                          selectedNotebook?.metadata.project_id ===
                          params.row.metadata.project_id
                        }
                        inputProps={{ "aria-label": "Notebook selection" }}
                      />
                    ),
                    hideable: false,
                    width: 70,
                    flex: 0,
                    disableColumnMenu: true,
                    sortable: false,
                  },
                  DataGridColumn.newColumnWithFieldName<Notebook, _>("name", {
                    headerName: "Name",
                    flex: 1,
                    sortable: false,
                  }),
                  DataGridColumn.newColumnWithFieldName<Notebook, _>("status", {
                    headerName: "Status",
                    flex: 1,
                    sortable: false,
                  }),
                  DataGridColumn.newColumnWithValueGetter<Notebook, _>(
                    "isPublic",
                    (notebook) => notebook.metadata.ispublic,
                    {
                      headerName: "Is Public",
                      flex: 1,
                      sortable: false,
                    }
                  ),
                  DataGridColumn.newColumnWithValueGetter<Notebook, _>(
                    "description",
                    (notebook) => notebook.metadata.pre_description,
                    {
                      headerName: "Description",
                      flex: 1,
                      sortable: false,
                    }
                  ),
                  DataGridColumn.newColumnWithValueGetter<Notebook, _>(
                    "projectLead",
                    (notebook) => notebook.metadata.project_lead,
                    {
                      headerName: "Project Lead",
                      flex: 1,
                      sortable: false,
                    }
                  ),
                  DataGridColumn.newColumnWithValueGetter<Notebook, _>(
                    "id",
                    (notebook) => notebook.metadata.project_id,
                    {
                      headerName: "Id",
                      flex: 1,
                      sortable: false,
                    }
                  ),
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
                onRowSelectionModelChange={(
                  newSelection: $ReadOnlyArray<string>
                ) => {
                  ArrayUtils.head(newSelection)
                    .toOptional()
                    .flatMap((selectedId) =>
                      ArrayUtils.find(
                        (n) => n.metadata.project_id === selectedId,
                        notebooks ?? []
                      )
                    )
                    .do((newlySelectedNotebook) => {
                      setSelectedNotebook(newlySelectedNotebook);
                    });
                }}
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Grid container direction="row" spacing={1}>
            <Grid item sx={{ ml: "auto" }}>
              <Stack direction="row" spacing={1}>
                <Button onClick={() => onClose()} disabled={importing}>
                  {selectedNotebook ? "Cancel" : "Close"}
                </Button>
                <ValidatingSubmitButton
                  onClick={() => {
                    if (selectedNotebook)
                      void importNotebook(selectedNotebook).then(() =>
                        onClose()
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
