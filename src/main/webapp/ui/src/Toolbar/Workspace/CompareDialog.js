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
import {
  DataGrid,
  GridToolbarContainer,
  GridToolbarExportContainer,
  GridToolbarColumnsButton,
  useGridApiContext,
} from "@mui/x-data-grid";
import { DataGridColumn } from "../../util/table";
import createAccentedTheme from "../../accentedTheme";
import { ThemeProvider } from "@mui/material/styles";
import MenuItem from "@mui/material/MenuItem";
import { doNotAwait } from "../../util/Util";
import { getByKey } from "../../util/optional";
import Box from "@mui/material/Box";
import UserDetails from "../../Inventory/components/UserDetails";
import { styled } from "@mui/material/styles";
import CircularProgress from "@mui/material/CircularProgress";
import * as Parsers from "../../util/parsers";

type Document = {
  id: number,
  name: string,
  form: {
    id: number,
    ...
  },
  fields: $ReadOnlyArray<{
    name: string,
    content: string,
    ...
  }>,
  owner: {
    firstName: string,
    lastName: string,
    id: number,
    username: string,
    ...
  },
  created: string,
  lastModified: string,
  ...
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

function CircularProgressWithLabel(props: { value: number }) {
  return (
    <Box sx={{ position: "relative", display: "inline-flex" }}>
      <CircularProgress variant="determinate" value={props.value} />
      <Box
        sx={{
          position: "absolute",
          inset: 0,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <Typography
          variant="caption"
          component="div"
          color="text.primary"
        >{`${Math.round(props.value)}%`}</Typography>
      </Box>
    </Box>
  );
}

function CustomLoadingOverlay({
  loadedCount,
  documentCount,
}: {|
  loadedCount: number,
  documentCount: number,
|}) {
  return (
    <StyledGridOverlay>
      <CircularProgressWithLabel value={(loadedCount / documentCount) * 100} />
      <Box sx={{ mt: 2 }}>Reading documents…</Box>
    </StyledGridOverlay>
  );
}

const ExportMenuItem = ({
  onClick,
  children,
  ...rest
}: {|
  onClick: () => Promise<void>,
  children: Node,
|}) => (
  <MenuItem
    onClick={doNotAwait(async () => {
      await onClick();
      /*
       * `hideMenu` is injected by MUI into the children of
       * `GridToolbarExportContainer`. See
       * https://github.com/mui/mui-x/blob/2414dcfe87b8bd4507361a80ab43c8d284ddc4de/packages/x-data-grid/src/components/toolbar/GridToolbarExportContainer.tsx#L99
       * However, if we add `hideMenu` to the type of the `ExportMenuItem`
       * props then Flow will complain we're not passing it in at the call site
       */
      getByKey<"hideMenu", () => void>("hideMenu", rest).do((hideMenu) => {
        hideMenu();
      });
    })}
  >
    {children}
  </MenuItem>
);

const Toolbar = ({
  rowSelectionModel,
}: {|
  rowSelectionModel: $ReadOnlyArray<number>,
|}) => {
  const apiRef = useGridApiContext();

  const exportVisibleRows = () => {
    apiRef.current?.exportDataAsCsv({
      allColumns: true,
    });
  };

  return (
    <GridToolbarContainer sx={{ width: "100%" }}>
      <Box flexGrow={1}></Box>
      <GridToolbarColumnsButton variant="outlined" />
      <GridToolbarExportContainer variant="outlined">
        <ExportMenuItem
          onClick={() => {
            exportVisibleRows();
            return Promise.resolve();
          }}
        >
          Export {rowSelectionModel.length > 0 ? "selected" : "all"} rows to CSV
        </ExportMenuItem>
      </GridToolbarExportContainer>
    </GridToolbarContainer>
  );
};

function CompareDialog(): Node {
  const { getToken } = useOauthToken();
  const [documents, setDocuments] = React.useState<$ReadOnlyArray<Document>>(
    []
  );
  const [paginationModel, setPaginationModel] = React.useState({
    page: 0,
    pageSize: 100,
  });
  const [rowSelectionModel, setRowSelectionModel] = React.useState<
    $ReadOnlyArray<number>
  >([]);
  const [documentCount, setDocumentCount] = React.useState(0);
  const [loadedCount, setLoadedCount] = React.useState(0);

  const fieldColumns: $ReadOnlyArray<[number, string]> = React.useMemo(() => {
    const cols = [];
    for (const doc of documents) {
      for (const field of doc.fields) {
        if (
          cols.findIndex(
            ([formId, fieldName]: [number, string]) =>
              formId === doc.form.id && fieldName === field.name
          ) === -1
        ) {
          cols.push([doc.form.id, field.name]);
        }
      }
    }
    return cols;
  }, [documents]);

  React.useEffect(() => {
    async function handler(
      event: Event & { detail: { ids: $ReadOnlyArray<string> } }
    ) {
      setDocumentCount(event.detail.ids.length);
      setLoadedCount(0);
      const token = await getToken();
      const docs = await Promise.all(
        event.detail.ids.map(async (id) => {
          const { data } = await axios.get<Document>(
            `/api/v1/documents/${id}`,
            {
              headers: {
                Authorization: "Bearer " + token,
              },
            }
          );
          setLoadedCount((x) => x + 1);
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

  const columns = [
    DataGridColumn.newColumnWithFieldName<Document, _>("name", {
      headerName: "Name",
      flex: 1,
      sortable: false,
    }),
    DataGridColumn.newColumnWithFieldName<Document, _>("globalId", {
      headerName: "Global ID",
      flex: 1,
      sortable: false,
    }),
    DataGridColumn.newColumnWithFieldName<Document, _>("owner", {
      headerName: "Owner",
      flex: 1,
      sortable: false,
      // for CSVs
      valueFormatter: (owner) => owner.username,
      renderCell: ({ row }: { row: Document }) => (
        <UserDetails
          userId={row.owner.id}
          fullName={`${row.owner.firstName} ${row.owner.lastName}`}
          position={["bottom", "right"]}
        />
      ),
    }),
    DataGridColumn.newColumnWithFieldName<Document, _>("created", {
      headerName: "Created Date",
      flex: 1,
      valueFormatter: (value: string) =>
        Parsers.parseDate(value)
          .map((l) => l.toLocaleString())
          .orElse("—"),
    }),
    DataGridColumn.newColumnWithFieldName<Document, _>("lastModified", {
      headerName: "Modified Date",
      flex: 1,
      valueFormatter: (value: string) =>
        Parsers.parseDate(value)
          .map((l) => l.toLocaleString())
          .orElse("—"),
    }),
  ];
  for (const [formId, fieldName] of fieldColumns) {
    columns.push(
      DataGridColumn.newColumnWithValueGetter<Document, _>(
        `${formId}:${fieldName}`,
        (row: mixed, doc: Document) => {
          if (doc.form.id !== formId) return "";
          return doc.fields.find((f) => f.name === fieldName)?.content ?? "";
        },
        {
          headerName: fieldName,
          flex: 1,
          sortable: false,
        }
      )
    );
  }

  return (
    <Dialog
      fullWidth
      maxWidth="xl"
      open={documentCount > 0}
      onClose={() => {
        setDocumentCount(0);
        setDocuments([]);
      }}
    >
      <DialogTitle>Export Documents to CSV</DialogTitle>
      <DialogContent>
        <Grid container direction="column" spacing={2}>
          <Grid item>
            <Typography variant="body2">
              Select the documents you want to combine into a single CSV file.
              Documents with identical structures will be automatically aligned,
              including form data and content. If documents have different
              structures, additional columns will be created to accommodate all
              information.
            </Typography>
          </Grid>
          <Grid item>
            <DataGrid
              aria-label="documents"
              autoHeight
              columns={columns}
              rows={documents}
              initialState={{
                columns: {
                  columnVisibilityModel: {
                    created: false,
                    lastModified: false,
                  },
                },
              }}
              density="standard"
              getRowId={(row: Document) => row.id}
              hideFooterSelectedRowCount
              checkboxSelection
              disableColumnFilter
              paginationMode="client"
              paginationModel={paginationModel}
              onPaginationModelChange={(newPaginationModel) => {
                setPaginationModel(newPaginationModel);
              }}
              rowSelectionModel={rowSelectionModel}
              onRowSelectionModelChange={(
                newRowSelectionModel: $ReadOnlyArray<number>,
                _details
              ) => {
                setRowSelectionModel(newRowSelectionModel);
              }}
              loading={loadedCount < documentCount}
              slots={{
                toolbar: Toolbar,
                loadingOverlay: CustomLoadingOverlay,
              }}
              slotProps={{
                toolbar: {
                  rowSelectionModel,
                },
                loadingOverlay: {
                  loadedCount,
                  documentCount,
                },
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
