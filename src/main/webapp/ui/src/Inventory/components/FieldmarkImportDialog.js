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
import { DataGrid } from "@mui/x-data-grid";
import Radio from "@mui/material/Radio";
import useViewportDimensions from "../../util/useViewportDimensions";

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
  const [notebooks, setNotebooks] =
    React.useState<null | $ReadOnlyArray<Notebook>>(null);

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
        <Box sx={{ display: "flex", minHeight: 0 }}>
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
                          value={false}
                          checked={false}
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
                  getRowId={(row) => row.metadata.project_id}
                />
              </Grid>
            </Grid>
          </DialogContent>
        </Box>
      </Dialog>
    </ThemeProvider>
  );
}
