//@flow

import React, { type Node } from "react";
import Portal from "@mui/material/Portal";
import Alerts from "../../components/Alerts/Alerts";
import { Dialog, DialogBoundary } from "../../components/DialogBoundary";
import ErrorBoundary from "../../components/ErrorBoundary";
import axios from "axios";
import useOauthToken from "../../common/useOauthToken";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import { DataGrid } from "@mui/x-data-grid";
import { DataGridColumn } from "../../util/table";
import createAccentedTheme from "../../accentedTheme";
import { ThemeProvider } from "@mui/material/styles";
import Alert from "@mui/material/Alert";

function CompareDialog(): Node {
  const { getToken } = useOauthToken();
  const [documents, setDocuments] = React.useState<null | $ReadOnlyArray<{
    name: string,
    id: number,
    form: {
      id: number,
      ...
    },
    ...
  }>>(null);
  const [paginationModel, setPaginationModel] = React.useState({
    page: 0,
    pageSize: 100,
  });

  const allOfTheSameForm =
    new Set((documents ?? []).map(({ form: { id } }) => id)).size === 1;

  const columns = [
    DataGridColumn.newColumnWithFieldName<{ name: string, id: number, ... }, _>(
      "name",
      {
        headerName: "Name",
        flex: 1,
        sortable: false,
      }
    ),
  ];
  if (allOfTheSameForm) {
    for (const field of documents[0].fields) {
      columns.push(
        DataGridColumn.newColumnWithValueGetter(
          field.name,
          (row, doc) => doc.fields.find((f) => f.name === field.name)?.content,
          {
            headerName: field.name,
            flex: 1,
            sortable: false,
          }
        )
      );
    }
  }

  React.useEffect(() => {
    async function handler(
      event: Event & { detail: { ids: $ReadOnlyArray<string> } }
    ) {
      const token = await getToken();
      const docs = await Promise.all(
        event.detail.ids.map(async (id) => {
          const { data } = await axios.get<{
            name: string,
            id: number,
            form: { id: number, ... },
            ...
          }>(`/api/v1/documents/${id}`, {
            headers: {
              Authorization: "Bearer " + token,
            },
          });
          return data;
        })
      );
      setDocuments(docs);
    }
    window.addEventListener("OPEN_COMPARE_DIALOG", handler);
    return () => {
      window.removeEventListener("OPEN_COMPARE_DIALOG", handler);
    };
  }, []);

  if (!documents) return null;
  return (
    <Dialog
      fullWidth
      maxWidth="xl"
      open={true}
      onClose={() => {
        setDocuments(null);
      }}
    >
      <DialogTitle>Compare Documents</DialogTitle>
      <DialogContent>
        <Grid container direction="column" spacing={2}>
          <Grid item>
            <Typography variant="body2">
              Compare the selected documents and export a table with all of the
              fields to CSV.
            </Typography>
          </Grid>
          {!allOfTheSameForm && (
            <Grid item>
              <Alert severity="info">
                The fields of the documents are not shown because the documents
                are not all of the same form.
              </Alert>
            </Grid>
          )}
          <Grid item>
            <DataGrid
              aria-label="documents"
              autoHeight
              columns={columns}
              rows={documents}
              initialState={{
                columns: {
                  columnVisibilityModel: {},
                },
              }}
              density="standard"
              getRowId={(row: { id: number, ... }) => row.id}
              hideFooterSelectedRowCount
              checkboxSelection
              disableColumnFilter
              paginationMode="client"
              paginationModel={paginationModel}
              onPaginationModelChange={(newPaginationModel) => {
                setPaginationModel(newPaginationModel);
              }}
            />
          </Grid>
        </Grid>
      </DialogContent>
    </Dialog>
  );
}

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

export default function Wrapper(): Node {
  return (
    <ErrorBoundary topOfViewport>
      <Portal>
        <Alerts>
          <DialogBoundary>
            <ThemeProvider theme={createAccentedTheme(COLOR)}>
              <CompareDialog />
            </ThemeProvider>
          </DialogBoundary>
        </Alerts>
      </Portal>
    </ErrorBoundary>
  );
}
