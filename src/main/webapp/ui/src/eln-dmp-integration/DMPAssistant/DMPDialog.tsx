import React from "react";
import { Dialog, DialogBoundary } from "../../components/DialogBoundary";
import Portal from "@mui/material/Portal";
import useViewportDimensions from "../../hooks/browser/useViewportDimensions";
import Stack from "@mui/material/Stack";
import Box from "@mui/material/Box";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import DialogTitle from "@mui/material/DialogTitle";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import {
  DataGrid,
  GridToolbarContainer,
  GridToolbarColumnsButton,
  GridRenderCellParams,
} from "@mui/x-data-grid";
import { ThemeProvider } from "@mui/material/styles";
import Link from "@mui/material/Link";
import Checkbox from "@mui/material/Checkbox";
import AlertContext from "../../stores/contexts/Alert";
import * as FetchingData from "../../util/fetchingData";
import {
  useDmpAssistantEndpoint,
  importDmpsIntoGallery,
  type DmpListing,
  type DmpSummary,
} from "./useDmpAssistantEndpoint";
import docLinks from "../../assets/DocLinks";
import NoValue from "../../components/NoValue";
import { doNotAwait } from "../../util/Util";
import createAccentedTheme from "../../accentedTheme";
import AppBar from "../../components/AppBar";
import ValidatingSubmitButton, {
  IsValid,
  IsInvalid,
} from "../../components/ValidatingSubmitButton";
import { DataGridColumn } from "../../util/table";
import { ACCENT_COLOR } from "../../assets/branding/dmpassistant";

function CustomDialog({
  fullScreen,
  ...props
}: React.ComponentProps<typeof Dialog>): React.ReactNode {
  return (
    <Dialog
      {...props}
      fullScreen={fullScreen}
      slotProps={{
        paper: {
          sx: {
            overflow: "hidden",
            margin: fullScreen ? 0 : 2.625,
            maxHeight: "unset",
            minHeight: "unset",
            height: fullScreen ? "100%" : "calc(100% - 48px)",
          },
        },
      }}
    />
  );
}

const DMPDialogContent = ({
  setOpen,
}: {
  setOpen: (open: boolean) => void;
}) => {
  const [selectedDmpIds, setSelectedDmpIds] = React.useState<Set<string>>(
    new Set(),
  );
  const [importing, setImporting] = React.useState(false);
  const { addAlert } = React.useContext(AlertContext);

  const { firstPage } = useDmpAssistantEndpoint();
  const [listing, setListing] =
    React.useState<FetchingData.Fetched<DmpListing>>(firstPage);

  React.useEffect(() => {
    setListing(firstPage);
  }, [firstPage]);

  // If the listing fetch fails (e.g. upstream 403 from DMP Assistant) the user
  // has already been told via the alert toast raised by useDmpAssistantEndpoint;
  // close the dialog so the upstream response — which may include an HTML
  // challenge page from Cloudflare — never reaches the screen.
  React.useEffect(() => {
    if (listing.tag === "error") setOpen(false);
  }, [listing, setOpen]);

  const toggleDmpSelection = (id: string) => {
    setSelectedDmpIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const selectedDmps: ReadonlyArray<DmpSummary> = FetchingData.match(listing, {
    loading: () => [],
    error: () => [],
    success: (l) => {
      const dmps: Array<DmpSummary> = [];
      selectedDmpIds.forEach((id) => {
        const dmp = l.getById(id);
        if (dmp) dmps.push(dmp);
      });
      return dmps;
    },
  });

  const onSubmit = async (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    if (selectedDmps.length === 0) return;
    setImporting(true);
    try {
      await importDmpsIntoGallery(selectedDmps, addAlert);
    } finally {
      setImporting(false);
    }
  };

  const currentPageDmps: ReadonlyArray<DmpSummary> = FetchingData.match(
    listing,
    {
      loading: () => [],
      error: () => [],
      success: (l) => l.dmps,
    },
  );
  const pageIds = currentPageDmps.map((d) => d.id);
  const selectedOnPageCount = pageIds.filter((id) =>
    selectedDmpIds.has(id),
  ).length;
  const allOnPageSelected =
    pageIds.length > 0 && selectedOnPageCount === pageIds.length;
  const someOnPageSelected =
    selectedOnPageCount > 0 && selectedOnPageCount < pageIds.length;

  const toggleSelectAllOnPage = () => {
    setSelectedDmpIds((prev) => {
      const next = new Set(prev);
      if (allOnPageSelected) {
        pageIds.forEach((id) => next.delete(id));
      } else {
        pageIds.forEach((id) => next.add(id));
      }
      return next;
    });
  };

  const columns = [
    {
      field: "__select__",
      headerName: "Select",
      width: 70,
      flex: 0,
      sortable: false,
      hideable: false,
      renderHeader: () => (
        <Checkbox
          color="primary"
          checked={allOnPageSelected}
          indeterminate={someOnPageSelected}
          onChange={toggleSelectAllOnPage}
          disabled={pageIds.length === 0}
          slotProps={{
            input: { "aria-label": "Select all DMPs on this page" },
          }}
        />
      ),
      renderCell: (params: GridRenderCellParams<DmpSummary>) => (
        <Checkbox
          color="primary"
          checked={selectedDmpIds.has(String(params.id))}
          onChange={() => toggleDmpSelection(String(params.id))}
          onClick={(e) => e.stopPropagation()}
          slotProps={{ input: { "aria-label": `Select ${params.row.title}` } }}
        />
      ),
    },
    DataGridColumn.newColumnWithFieldName<"title", DmpSummary>("title", {
      headerName: "Title",
      hideable: false,
    }),
    {
      field: "contact name",
      headerName: "Contact Name",
      renderCell: (params: { row: DmpSummary }) =>
        params.row.contactName.orElse(<NoValue label="Not Specified" />),
    },
    {
      field: "contact affiliation",
      headerName: "Contact Affiliation",
      renderCell: (params: { row: DmpSummary }) =>
        params.row.contactAffiliationName.orElse(
          <NoValue label="Not Specified" />,
        ),
    },
    DataGridColumn.newColumnWithFieldName<"created", DmpSummary>("created", {
      headerName: "Created",
    }),
    DataGridColumn.newColumnWithFieldName<"modified", DmpSummary>("modified", {
      headerName: "Modified",
    }),
  ].map((colDefinition) => ({
    sortable: false,
    flex: 1,
    ...colDefinition,
  }));

  return (
    <>
      <AppBar
        variant="dialog"
        currentPage="DMP Assistant"
        accessibilityTips={{
          supportsHighContrastMode: true,
        }}
        helpPage={{
          docLink: docLinks.dmpassistant,
          title: "DMP Assistant help",
        }}
      />
      <DialogTitle variant="h3">Import DMPs into the Gallery</DialogTitle>
      <DialogContent>
        <Stack
          spacing={2}
          sx={{
            flexWrap: "nowrap",
            height: "calc(100% + 16px)",
          }}
        >
          <Box>
            <Typography variant="body2">
              Importing DMPs from <strong>dmp-pgd.ca</strong> will make them
              available to view and reference within RSpace. Select one or more
              and click Import.
            </Typography>
            <Typography variant="body2">
              See <Link href="https://dmp-pgd.ca">dmp-pgd.ca</Link> and our{" "}
              <Link href={docLinks.dmpassistant}>
                DMP Assistant integration docs
              </Link>{" "}
              for more.
            </Typography>
          </Box>
          <Box sx={{ flexGrow: 1, overflowY: "auto" }}>
            {FetchingData.match(listing, {
              loading: () => (
                <Typography variant="body2">
                  Loading listing of DMPs.
                </Typography>
              ),
              // The dialog is closed by the effect above when listing fails, so
              // we render nothing here — the upstream message (which may contain
              // HTML) is never displayed.
              error: () => <></>,
              success: () => <></>,
            })}
            <DataGrid
              rows={FetchingData.match(listing, {
                loading: () => [] as Array<DmpSummary>,
                error: () => [] as Array<DmpSummary>,
                success: (l) => l.dmps,
              })}
              columns={columns}
              initialState={{
                columns: {
                  columnVisibilityModel: {
                    created: false,
                    modified: false,
                  },
                },
              }}
              disableColumnFilter
              density="compact"
              getRowId={(row) => row.id}
              hideFooterSelectedRowCount
              paginationMode="server"
              rowCount={FetchingData.match(listing, {
                loading: () => 0,
                error: () => 0,
                success: (l) => l.totalCount,
              })}
              paginationModel={FetchingData.match(listing, {
                loading: () => ({ page: 0, pageSize: 0 }),
                error: () => ({ page: 0, pageSize: 0 }),
                success: (l) => ({ page: l.page, pageSize: l.pageSize }),
              })}
              pageSizeOptions={[10, 25, 100]}
              onPaginationModelChange={({
                pageSize: newPageSize,
                page: newPage,
              }) => {
                FetchingData.match(listing, {
                  loading: () => {},
                  error: () => {},
                  success: doNotAwait(async (l) => {
                    const pageSizeChanged = newPageSize !== l.pageSize;
                    const pageChanged = newPage !== l.page;
                    if (!pageSizeChanged && !pageChanged) return;
                    try {
                      setListing({ tag: "loading" });
                      const next = pageSizeChanged
                        ? await l.setPageSize(newPageSize)
                        : await l.setPage(newPage);
                      setListing({ tag: "success", value: next });
                    } catch (error) {
                      if (error instanceof Error)
                        setListing({ tag: "error", error: error.message });
                    }
                  }),
                });
              }}
              slots={{
                toolbar: () => (
                  <GridToolbarContainer>
                    <GridToolbarColumnsButton />
                  </GridToolbarContainer>
                ),
              }}
              sx={{
                display: FetchingData.match(listing, {
                  loading: () => "none",
                  error: () => "none",
                  success: () => "flex",
                }),
              }}
              getRowHeight={() => "auto"}
              {...FetchingData.match(listing, {
                loading: () => ({ "aria-hidden": true }),
                error: () => ({ "aria-hidden": true }),
                success: () => ({}),
              })}
            />
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => setOpen(false)}>Close</Button>
        <ValidatingSubmitButton
          validationResult={
            selectedDmps.length > 0
              ? IsValid()
              : IsInvalid("No DMP is selected.")
          }
          loading={importing}
          onClick={(e) => void onSubmit(e)}
        >
          {selectedDmps.length > 1
            ? `Import (${selectedDmps.length})`
            : "Import"}
        </ValidatingSubmitButton>
      </DialogActions>
    </>
  );
};

type DMPDialogArgs = {
  open: boolean;
  setOpen: (open: boolean) => void;
};

export default function DMPDialog({
  open,
  setOpen,
}: DMPDialogArgs): React.ReactNode {
  const { isViewportSmall } = useViewportDimensions();

  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <Portal>
        <DialogBoundary>
          <CustomDialog
            onClose={() => {
              setOpen(false);
            }}
            open={open}
            maxWidth="lg"
            fullWidth
            fullScreen={isViewportSmall}
          >
            <DMPDialogContent setOpen={setOpen} />
          </CustomDialog>
        </DialogBoundary>
      </Portal>
    </ThemeProvider>
  );
}
