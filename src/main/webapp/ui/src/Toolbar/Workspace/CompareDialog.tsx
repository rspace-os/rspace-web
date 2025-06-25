import React from "react";
import Portal from "@mui/material/Portal";
import Alerts from "../../components/Alerts/Alerts";
import { Dialog, DialogBoundary } from "../../components/DialogBoundary";
import ErrorBoundary from "../../components/ErrorBoundary";
import axios from "@/common/axios";
import useOauthToken from "../../common/useOauthToken";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import {
  DataGrid,
  GridToolbarContainer,
  GridToolbarExportContainer,
  GridToolbarColumnsButton,
  type GridRenderCellParams,
  useGridApiContext,
  GridRowSelectionModel,
} from "@mui/x-data-grid";
import { DataGridColumn } from "../../util/table";
import createAccentedTheme from "../../accentedTheme";
import { ThemeProvider, styled } from "@mui/material/styles";
import MenuItem from "@mui/material/MenuItem";
import { doNotAwait } from "../../util/Util";
import { getByKey } from "../../util/optional";
import Box from "@mui/material/Box";
import UserDetails from "../../Inventory/components/UserDetails";
import CircularProgress from "@mui/material/CircularProgress";
import * as Parsers from "../../util/parsers";
import TickIcon from "@mui/icons-material/Done";
import CrossIcon from "@mui/icons-material/Clear";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import Button from "@mui/material/Button";
import Analytics from "../../components/Analytics";
import AnalyticsContext from "../../stores/contexts/Analytics";
import { GlobalId } from "@/stores/definitions/BaseRecord";

/**
 * This module provides a  dialog allows the user to compare the contents of
 * several ELN documents and download a CSV of the same. The table and
 * resulting file also contain various bits of metadata that were easily
 * extracted from the API, rather than necessarily what would be most useful
 * due to constraints on backend developer capacity.
 *
 * It should be added to the DOM on page load and remains hidden until the
 * OPEN_COMPARE_DIALOG event is dispatched on `window`. That event MUST contain
 * a list of document ids in its `detail`. This code then goes and fetches each
 * of these documents and displays a table.
 */

type Document = {
  id: number;
  name: string;
  globalId: GlobalId;
  form: {
    id: number;
  };
  fields: ReadonlyArray<{
    name: string;
    content: string;
  }>;
  owner: {
    firstName: string;
    lastName: string;
    id: number;
    username: string;
  };
  created: string;
  lastModified: string;
  signed: boolean;
  tags: string;
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
}: {
  loadedCount: number;
  documentCount: number;
}) {
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
}: {
  onClick: () => Promise<void>;
  children: React.ReactNode;
  hideMenu?: () => void;
}) => (
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
      getByKey<{ hideMenu?: () => void }, "hideMenu">("hideMenu", rest).do(
        (hideMenu) => {
          hideMenu();
        }
      );
    })}
  >
    {children}
  </MenuItem>
);

const Toolbar = ({
  rowSelectionModel,
  setColumnsMenuAnchorEl,
}: {
  rowSelectionModel: ReadonlyArray<number>;
  setColumnsMenuAnchorEl: (anchorEl: HTMLElement) => void;
}) => {
  const { trackEvent } = React.useContext(AnalyticsContext);
  const apiRef = useGridApiContext();

  /**
   * The columns menu can be opened by either tapping the "Columns" toolbar
   * button or by tapping the "Manage columns" menu item in each column's menu,
   * logic that is handled my MUI. We provide a custom `anchorEl` so that the
   * menu is positioned beneath the "Columns" toolbar button to be consistent
   * with the other toolbar menus, otherwise is appears far to the left. Rather
   * than having to hook into the logic that triggers the opening of the
   * columns menu in both places, we just set the `anchorEl` pre-emptively.
   */
  const columnMenuRef = React.useRef<HTMLElement>();
  React.useEffect(() => {
    if (columnMenuRef.current) setColumnsMenuAnchorEl(columnMenuRef.current);
  }, [setColumnsMenuAnchorEl]);

  const exportVisibleRows = () => {
    apiRef.current?.exportDataAsCsv({
      allColumns: true,
    });
  };

  return (
    <GridToolbarContainer sx={{ width: "100%" }}>
      <Box flexGrow={1}></Box>
      <GridToolbarColumnsButton
        ref={(node) => {
          if (node) columnMenuRef.current = node;
        }}
      />
      <GridToolbarExportContainer>
        <ExportMenuItem
          onClick={() => {
            exportVisibleRows();
            trackEvent("CSV comparing ELN documents downloaded");
            return Promise.resolve();
          }}
        >
          Export {rowSelectionModel.length > 0 ? "selected" : "all"} rows to CSV
        </ExportMenuItem>
      </GridToolbarExportContainer>
    </GridToolbarContainer>
  );
};

function CompareDialog(): React.ReactNode {
  const analytics = React.useContext(AnalyticsContext);
  const { addAlert } = React.useContext(AlertContext);
  const { getToken } = useOauthToken();
  const [documents, setDocuments] = React.useState<ReadonlyArray<Document>>([]);
  const [paginationModel, setPaginationModel] = React.useState({
    page: 0,
    pageSize: 100,
  });
  const [rowSelectionModel, setRowSelectionModel] =
    React.useState<GridRowSelectionModel>([]);
  const [documentCount, setDocumentCount] = React.useState(0);
  const [loadedCount, setLoadedCount] = React.useState(0);
  const [columnsMenuAnchorEl, setColumnsMenuAnchorEl] =
    React.useState<HTMLElement | null>(null);

  const fieldColumns: ReadonlyArray<[number, string]> = React.useMemo(() => {
    const cols: Array<[number, string]> = [];
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
    const handler = doNotAwait(async (event: Event) => {
      // @ts-expect-error the event will have this detail
      const ids = event.detail.ids as Array<string>;
      analytics.trackEvent("Dialog comparing ELN documents opened", {
        numberOfDocuments: ids.length,
      });
      setDocumentCount(ids.length);
      setLoadedCount(0);
      try {
        const token = await getToken();
        /*
         * Making one network call per document may not be the most performant
         * approach but this work was only possible by not requiring any
         * backend developer capacity. If we find that users are downloading
         * the CSV of many documents and this is causing a performance
         * bottleneck then we should look to parallelise this with a custom API
         * endpoint.
         */
        const docs = await Promise.all(
          ids.map(async (id) => {
            const { data } = await axios.get<
              Document | { errors: ReadonlyArray<string> }
            >(`/api/v1/documents/${id}`, {
              headers: {
                Authorization: "Bearer " + token,
              },
            });
            Parsers.getValueWithKey("errors")(data)
              .flatMap(Parsers.isArray)
              .do((errors) => {
                throw errors[0];
              });
            setLoadedCount((x) => x + 1);
            return data as Document;
          })
        );
        setDocuments(docs);
      } catch (e) {
        setDocumentCount(0);
        setLoadedCount(0);
        const message = Parsers.objectPath(["response", "data", "message"], e)
          .orElseTry(() => Parsers.objectPath(["message"], e))
          .flatMap(Parsers.isString)
          .orElse("Unknown reason");
        addAlert(
          mkAlert({
            variant: "error",
            title: "Could not read all of the documents",
            message,
          })
        );
      }
    });
    window.addEventListener("OPEN_COMPARE_DIALOG", handler);
    return () => {
      window.removeEventListener("OPEN_COMPARE_DIALOG", handler);
    };
  }, []);

  const columns = [
    DataGridColumn.newColumnWithFieldName<"name", Document>("name", {
      headerName: "Name",
      flex: 1,
      sortable: false,
    }),
    DataGridColumn.newColumnWithFieldName<"globalId", Document>("globalId", {
      headerName: "Global ID",
      flex: 1,
      sortable: false,
    }),
    DataGridColumn.newColumnWithValueGetter<"owner", Document, string>(
      "owner",
      (doc) => {
        return doc.owner.username;
      },
      {
        headerName: "Owner",
        flex: 1,
        sortable: false,
        renderCell: ({ row }: { row: Document }) => (
          <UserDetails
            userId={row.owner.id}
            fullName={`${row.owner.firstName} ${row.owner.lastName}`}
            position={["bottom", "right"]}
          />
        ),
      }
    ),
    DataGridColumn.newColumnWithFieldName<"created", Document>("created", {
      headerName: "Created Date",
      flex: 1,
      valueFormatter: (value: string) =>
        Parsers.parseDate(value)
          .map((l) => l.toLocaleString())
          .orElse("—"),
    }),
    DataGridColumn.newColumnWithFieldName<"lastModified", Document>(
      "lastModified",
      {
        headerName: "Modified Date",
        flex: 1,
        valueFormatter: (value: string) =>
          Parsers.parseDate(value)
            .map((l) => l.toLocaleString())
            .orElse("—"),
      }
    ),
    DataGridColumn.newColumnWithFieldName<"signed", Document>("signed", {
      headerName: "Signed",
      flex: 1,
      sortable: false,
      // for CSVs
      valueFormatter: (signed) => (signed ? "true" : "false"),
      renderCell: ({ value }: GridRenderCellParams) =>
        value ? (
          <TickIcon color="success" aria-label="Signed" aria-hidden="false" />
        ) : (
          <CrossIcon color="error" aria-label="Unsigned" aria-hidden="false" />
        ),
    }),
    DataGridColumn.newColumnWithFieldName<"tags", Document>("tags", {
      headerName: "Tags",
      flex: 1,
      sortable: false,
    }),
  ];
  for (const [formId, fieldName] of fieldColumns) {
    columns.push(
      DataGridColumn.newColumnWithValueGetter<
        `${typeof formId}:${typeof fieldName}`,
        Document,
        string
      >(
        `${formId}:${fieldName}`,
        (doc: Document) => {
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
          <Grid item sx={{ width: "100%" }}>
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
                    signed: false,
                    tags: false,
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
                newRowSelectionModel: GridRowSelectionModel
              ) => {
                setRowSelectionModel(newRowSelectionModel);
              }}
              loading={loadedCount < documentCount}
              slots={{
                // @ts-expect-error The type of toolbar does not account for the slotProps that also get passed
                toolbar: Toolbar,
                // @ts-expect-error The type of loadingOverlay does not account for the slotProps that also get passed
                loadingOverlay: CustomLoadingOverlay,
              }}
              slotProps={{
                toolbar: {
                  rowSelectionModel,
                  setColumnsMenuAnchorEl,
                },
                loadingOverlay: {
                  // @ts-expect-error The type of loadingOverlay does not account for the slotProps that also get passed
                  loadedCount,
                  documentCount,
                },
                panel: {
                  anchorEl: columnsMenuAnchorEl,
                },
              }}
            />
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button
          onClick={() => {
            setDocumentCount(0);
          }}
        >
          Close
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/*
 * This is just a temporary neutral colour that should be replaced with the
 * workspace's accent colour once that part of the product has been fully
 * migrated to react.
 */
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

export default function Wrapper(): React.ReactNode {
  return (
    <ErrorBoundary topOfViewport>
      <Analytics>
        <Portal>
          <Alerts>
            <DialogBoundary>
              <ThemeProvider theme={createAccentedTheme(COLOR)}>
                <CompareDialog />
              </ThemeProvider>
            </DialogBoundary>
          </Alerts>
        </Portal>
      </Analytics>
    </ErrorBoundary>
  );
}
