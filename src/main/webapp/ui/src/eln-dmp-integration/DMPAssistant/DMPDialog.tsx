import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Link from "@mui/material/Link";
import Portal from "@mui/material/Portal";
import Stack from "@mui/material/Stack";
import { ThemeProvider } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import { DataGrid, type GridRenderCellParams, GridToolbarColumnsButton, GridToolbarContainer } from "@mui/x-data-grid";
import React from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/dmpassistant";
import docLinks from "../../assets/DocLinks";
import AppBar from "../../components/AppBar";
import { Dialog, DialogBoundary } from "../../components/DialogBoundary";
import NoValue from "../../components/NoValue";
import ValidatingSubmitButton, { IsInvalid, IsValid } from "../../components/ValidatingSubmitButton";
import useViewportDimensions from "../../hooks/browser/useViewportDimensions";
import AlertContext from "../../stores/contexts/Alert";
import * as FetchingData from "../../util/fetchingData";
import { DataGridColumn } from "../../util/table";
import {
  type DmpListing,
  type DmpSummary,
  importDmpsIntoGallery,
  useDmpAssistantEndpoint,
} from "./useDmpAssistantEndpoint";

function CustomDialog({ fullScreen, ...props }: React.ComponentProps<typeof Dialog>): React.ReactNode {
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

const DMPDialogContent = ({ setOpen }: { setOpen: (open: boolean) => void }) => {
  const { t } = useTranslation(["apps", "common"]);
  const [selectedDmpIds, setSelectedDmpIds] = React.useState<Set<string>>(new Set());
  const [importing, setImporting] = React.useState(false);
  const { addAlert } = React.useContext(AlertContext);

  const { firstPage } = useDmpAssistantEndpoint();
  const [listing, setListing] = React.useState<FetchingData.Fetched<DmpListing>>(firstPage);

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

  const currentPageDmps: ReadonlyArray<DmpSummary> = FetchingData.match(listing, {
    loading: () => [],
    error: () => [],
    success: (l) => l.dmps,
  });
  const pageIds = currentPageDmps.map((d) => d.id);
  const selectedOnPageCount = pageIds.filter((id) => selectedDmpIds.has(id)).length;
  const allOnPageSelected = pageIds.length > 0 && selectedOnPageCount === pageIds.length;
  const someOnPageSelected = selectedOnPageCount > 0 && selectedOnPageCount < pageIds.length;

  const toggleSelectAllOnPage = () => {
    setSelectedDmpIds((prev) => {
      const next = new Set(prev);
      if (allOnPageSelected) {
        // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
        pageIds.forEach((id) => next.delete(id));
      } else {
        // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
        pageIds.forEach((id) => next.add(id));
      }
      return next;
    });
  };

  const columns = [
    {
      field: "__select__",
      headerName: t("dmpIntegrations.dialog.selectColumn"),
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
            input: { "aria-label": t("dmpIntegrations.dialog.selectAllLabel") },
          }}
        />
      ),
      renderCell: (params: GridRenderCellParams<DmpSummary>) => (
        <Checkbox
          color="primary"
          checked={selectedDmpIds.has(String(params.id))}
          onChange={() => toggleDmpSelection(String(params.id))}
          onClick={(e) => e.stopPropagation()}
          slotProps={{
            input: { "aria-label": t("dmpIntegrations.dialog.selectPlanLabel", { label: params.row.title }) },
          }}
        />
      ),
    },
    DataGridColumn.newColumnWithFieldName<"title", DmpSummary>("title", {
      headerName: t("dmpIntegrations.dialog.columns.title"),
      hideable: false,
    }),
    {
      field: "contact name",
      headerName: t("dmpIntegrations.dialog.columns.contactName"),
      renderCell: (params: { row: DmpSummary }) =>
        params.row.contactName.orElse(<NoValue label={t("dmpIntegrations.dialog.notSpecified")} />),
    },
    {
      field: "contact affiliation",
      headerName: t("dmpIntegrations.dialog.columns.contactAffiliation"),
      renderCell: (params: { row: DmpSummary }) =>
        params.row.contactAffiliationName.orElse(<NoValue label={t("dmpIntegrations.dialog.notSpecified")} />),
    },
    DataGridColumn.newColumnWithFieldName<"created", DmpSummary>("created", {
      headerName: t("dmpIntegrations.dialog.columns.created"),
    }),
    DataGridColumn.newColumnWithFieldName<"modified", DmpSummary>("modified", {
      headerName: t("dmpIntegrations.dialog.columns.modified"),
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
        currentPage={t("dmpIntegrations.dmpAssistant")}
        accessibilityTips={{
          supportsHighContrastMode: true,
        }}
        helpPage={{
          docLink: docLinks.dmpassistant,
          title: t("dmpIntegrations.dialog.helpTitle", { name: t("dmpIntegrations.dmpAssistant") }),
        }}
      />
      <DialogTitle variant="h3">{t("dmpIntegrations.dialog.importDmpsIntoGallery")}</DialogTitle>
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
              <TransRichText i18nKey="apps:dmpIntegrations.dialog.dmpassistantImportDesc" />
            </Typography>
            <Typography variant="body2">
              <TransRichText
                i18nKey="apps:dmpIntegrations.dialog.dmpassistantDocsLink"
                components={{
                  helpLink: <Link href={docLinks.dmpassistant} />,
                }}
              />
            </Typography>
          </Box>
          <Box sx={{ flexGrow: 1, overflowY: "auto" }}>
            {FetchingData.match(listing, {
              loading: () => <Typography variant="body2">{t("dmpIntegrations.dialog.loadingDmps")}</Typography>,
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
              onPaginationModelChange={({ pageSize: newPageSize, page: newPage }) => {
                FetchingData.match(listing, {
                  loading: () => {},
                  error: () => {},
                  success: (l) => {
                    void (async () => {
                      const pageSizeChanged = newPageSize !== l.pageSize;
                      const pageChanged = newPage !== l.page;
                      if (!pageSizeChanged && !pageChanged) return;
                      try {
                        setListing({ tag: "loading" });
                        const next = pageSizeChanged ? await l.setPageSize(newPageSize) : await l.setPage(newPage);
                        setListing({ tag: "success", value: next });
                      } catch (error) {
                        if (error instanceof Error) setListing({ tag: "error", error: error.message });
                      }
                    })();
                  },
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
        <Button onClick={() => setOpen(false)}>{t("common:actions.close")}</Button>
        <ValidatingSubmitButton
          validationResult={
            selectedDmps.length > 0 ? IsValid() : IsInvalid(t("dmpIntegrations.dialog.noDmpIsSelected"))
          }
          loading={importing}
          onClick={(e) => void onSubmit(e)}
        >
          {t("dmpIntegrations.dialog.importButton", { count: selectedDmps.length })}
        </ValidatingSubmitButton>
      </DialogActions>
    </>
  );
};

type DMPDialogArgs = {
  open: boolean;
  setOpen: (open: boolean) => void;
};

export default function DMPDialog({ open, setOpen }: DMPDialogArgs): React.ReactNode {
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
