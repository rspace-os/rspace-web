//@flow

import React, { type Node } from "react";
import { ThemeProvider } from "@mui/material/styles";
import Box from "@mui/material/Box";
import Dialog from "@mui/material/Dialog";
import createAccentedTheme from "../../accentedTheme";
import Toolbar from "@mui/material/Toolbar";
import AppBar from "@mui/material/AppBar";
import AccessibilityTips from "../../components/AccessibilityTips";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import DialogContent from "@mui/material/DialogContent";
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
import Stack from "@mui/material/Stack";
import Button from "@mui/material/Button";
import ValidatingSubmitButton, {
  IsInvalid,
  IsValid,
} from "../../components/ValidatingSubmitButton";
import DialogActions from "@mui/material/DialogActions";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";

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

  React.useEffect(
    doNotAwait(async () => {
      try {
        const { data } = await InvApiService.get<
          mixed,
          $ReadOnlyArray<Notebook>
        >("/fieldmark/notebooks");
        setNotebooks(data);
      } catch (e) {
        console.error(e);
      }
    }),
    []
  );

  async function importNotebook(notebook: Notebook) {
    try {
      await InvApiService.get<mixed, mixed>(
        "/fieldmark/import/notebook/" + notebook.metadata.project_id
      );
      addAlert(
        mkAlert({
          variant: "success",
          message: "Successfully imported notebook.",
        })
      );
      // reload bench, if that's the current search
    } catch (e) {
      console.error(e);
      addAlert(
        mkAlert({
          variant: "error",
          title: "Could not import notebook.",
          message: e.message,
        })
      );
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
              <HelpLinkIcon title="Fieldmark help" link="#" />
            </Box>
          </Toolbar>
        </AppBar>
        <Box sx={{ display: "flex", minHeight: 0, flexDirection: "column" }}>
          <DialogContent>
            <Grid
              container
              direction="column"
              spacing={2}
              sx={{ height: "100%", flexWrap: "nowrap" }}
            >
              <Grid item>
                <Typography variant="h3">Import from Fieldmark</Typography>
              </Grid>
              <Grid item>
                <Typography variant="body2">
                  Choose a Fieldmark notebook to import into Inventory. The new
                  list container will be placed on your bench.
                </Typography>
                <Typography variant="body2">
                  See <Link href="#">docs.fieldmark.au</Link> and our{" "}
                  <Link href={"#"}>Fieldmark integration docs</Link> for more.
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
                    DataGridColumn.newColumnWithFieldName<Notebook, _>(
                      "status",
                      {
                        headerName: "Status",
                        flex: 1,
                        sortable: false,
                      }
                    ),
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
                  localeText={{
                    noRowsLabel: "No Notebooks",
                  }}
                  loading={notebooks === null}
                  slots={{
                    toolbar: GridToolbar,
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
                  <Button onClick={() => onClose()} disabled={false}>
                    {selectedNotebook ? "Cancel" : "Close"}
                  </Button>
                  <ValidatingSubmitButton
                    onClick={() => {
                      if (selectedNotebook)
                        void importNotebook(selectedNotebook);
                    }}
                    validationResult={
                      !selectedNotebook
                        ? IsInvalid("No Notebook selected.")
                        : IsValid()
                    }
                    loading={false}
                  >
                    Import
                  </ValidatingSubmitButton>
                </Stack>
              </Grid>
            </Grid>
          </DialogActions>
        </Box>
      </Dialog>
    </ThemeProvider>
  );
}
