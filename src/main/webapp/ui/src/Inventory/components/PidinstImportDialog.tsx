import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Link from "@mui/material/Link";
import Stack from "@mui/material/Stack";
import { ThemeProvider } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { GridToolbarColumnsButton, GridToolbarContainer } from "@mui/x-data-grid";
import React from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import { useConfirm } from "@/components/ConfirmProvider";
import useOauthToken from "@/hooks/auth/useOauthToken";
import TransRichText, { helpDocsArticleUrl } from "@/modules/common/i18n/TransRichText";
import { getErrorMessage } from "@/util/error";
import createAccentedTheme, { type AccentColor } from "../../accentedTheme";
import AppBar from "../../components/AppBar";
import { DataGridWithRadioSelection } from "../../components/DataGridWithRadioSelection";
import { Dialog } from "../../components/DialogBoundary";
import GlobalId from "../../components/GlobalId";
import ValidatingSubmitButton, { IsInvalid, IsValid } from "../../components/ValidatingSubmitButton";
import useViewportDimensions from "../../hooks/browser/useViewportDimensions";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import LinkableRecordFromGlobalId from "../../stores/models/LinkableRecordFromGlobalId";
import { DataGridColumn } from "../../util/table";

/**
 * Instrument colours for the dialog. `main` is `theme.palette.record.instrument.bg` (#ab4c08)
 * exactly, as HSL; the remaining slots are derived from it and tuned for contrast rather than
 * taken from the palette, so `background` is a flatter, darker tint than `lighter` (#fce8d5).
 */
const INSTRUMENT_ACCENT_COLOR: AccentColor = {
  main: { hue: 25, saturation: 91, lightness: 35 },
  darker: { hue: 25, saturation: 91, lightness: 25 },
  contrastText: { hue: 25, saturation: 80, lightness: 20 },
  background: { hue: 29, saturation: 60, lightness: 80 },
  backgroundContrastText: { hue: 25, saturation: 50, lightness: 25 },
};

/** One hit of GET /api/inventory/v1/pidinst/search. Null scalars are omitted by the server. */
export type PidinstRecord = {
  pid: string;
  provider: string;
  providerRecordUrl?: string;
  publicUrl?: string;
  state?: string;
  name?: string;
  description?: string;
  owners: ReadonlyArray<string>;
  manufacturers: ReadonlyArray<string>;
  model?: string;
  instrumentTypes: ReadonlyArray<string>;
  measuredVariables: ReadonlyArray<string>;
  commissioned?: string;
  decommissioned?: string;
  landingPage?: string;
  alternateIdentifier?: string;
  created?: string;
  updated?: string;
  linkedInstrumentGlobalId?: string;
};

/** Mirrors PidinstLookupManager.MIN_QUERY_LENGTH, which rejects a shorter query with a 422. */
const MIN_QUERY_LENGTH = 4;

type PidinstSearchResult = {
  provider: string;
  total: number;
  hits: ReadonlyArray<PidinstRecord>;
};

type ImportedInstrumentResponse = {
  id: number;
  globalId: string;
  name: string;
};

const joined = (values: ReadonlyArray<string>): string => values.join("; ");

const GridToolbar = ({ setColumnsMenuAnchorEl }: { setColumnsMenuAnchorEl: (anchorEl: HTMLElement) => void }) => {
  // anchored pre-emptively so the columns menu opens beneath its button, not far to the left
  const columnMenuRef = React.useRef<HTMLElement | null>(null);
  React.useEffect(() => {
    if (columnMenuRef.current) setColumnsMenuAnchorEl(columnMenuRef.current);
  }, [setColumnsMenuAnchorEl]);
  return (
    <GridToolbarContainer sx={{ width: "100%" }}>
      <Box sx={{ flexGrow: 1 }}></Box>
      <GridToolbarColumnsButton
        ref={(node) => {
          if (node) columnMenuRef.current = node;
        }}
      />
    </GridToolbarContainer>
  );
};

function SearchingOverlay() {
  const id = React.useId();
  const { t } = useTranslation("inventory");
  return (
    <Box
      sx={(theme) => ({
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: "100%",
        backgroundColor: "rgba(18, 18, 18, 0.9)",
        ...theme.applyStyles("light", {
          backgroundColor: "rgba(255, 255, 255, 0.9)",
        }),
      })}
    >
      <CircularProgress variant="indeterminate" value={1} aria-labelledby={id} />
      <Box sx={{ mt: 2 }} id={id}>
        {t("pidinstImport.search.searching")}
      </Box>
    </Box>
  );
}

function PreviewField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    // the wrapper keeps a label and its value in the same column of the two-column list
    <Box sx={{ breakInside: "avoid" }}>
      <Typography component="dt" variant="body2" sx={{ fontWeight: 500 }}>
        {label}
      </Typography>
      <Typography component="dd" variant="body2" sx={{ m: 0, mb: 1 }}>
        {children}
      </Typography>
    </Box>
  );
}

function ExternalLink({ href }: { href: string }) {
  return (
    <Link href={href} target="_blank" rel="noreferrer">
      {href}
    </Link>
  );
}

function RecordPreview({ record }: { record: PidinstRecord }) {
  const { t } = useTranslation("inventory");
  const headingId = React.useId();
  return (
    <Box component="section" aria-labelledby={headingId} sx={{ mt: 1 }}>
      <Typography id={headingId} variant="h6" component="h3" sx={{ mb: 1 }}>
        {t("pidinstImport.preview.title")}
      </Typography>
      {record.linkedInstrumentGlobalId && (
        <Alert severity="info" sx={{ mb: 1, alignItems: "center" }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
            <span>{t("pidinstImport.preview.alreadyLinked")}</span>
            <GlobalId record={new LinkableRecordFromGlobalId(record.linkedInstrumentGlobalId)} />
          </Stack>
        </Alert>
      )}
      <Box component="dl" sx={{ m: 0, columnCount: { xs: 1, md: 2 }, columnGap: 4 }}>
        <PreviewField label={t("pidinstImport.preview.pid")}>
          {record.publicUrl ? (
            <Link href={record.publicUrl} target="_blank" rel="noreferrer">
              {record.pid}
            </Link>
          ) : (
            record.pid
          )}
        </PreviewField>
        {record.providerRecordUrl && (
          <PreviewField label={t("pidinstImport.preview.providerRecord")}>
            <ExternalLink href={record.providerRecordUrl} />
          </PreviewField>
        )}
        {record.description && (
          <PreviewField label={t("pidinstImport.preview.description")}>{record.description}</PreviewField>
        )}
        {record.owners.length > 0 && (
          <PreviewField label={t("pidinstImport.preview.owners")}>{joined(record.owners)}</PreviewField>
        )}
        {record.manufacturers.length > 0 && (
          <PreviewField label={t("pidinstImport.preview.manufacturers")}>{joined(record.manufacturers)}</PreviewField>
        )}
        {record.model && <PreviewField label={t("pidinstImport.preview.model")}>{record.model}</PreviewField>}
        {record.instrumentTypes.length > 0 && (
          <PreviewField label={t("pidinstImport.preview.instrumentTypes")}>
            {joined(record.instrumentTypes)}
          </PreviewField>
        )}
        {record.measuredVariables.length > 0 && (
          <PreviewField label={t("pidinstImport.preview.measuredVariables")}>
            {joined(record.measuredVariables)}
          </PreviewField>
        )}
        {record.commissioned && (
          <PreviewField label={t("pidinstImport.preview.commissioned")}>{record.commissioned}</PreviewField>
        )}
        {record.decommissioned && (
          <PreviewField label={t("pidinstImport.preview.decommissioned")}>{record.decommissioned}</PreviewField>
        )}
        {record.landingPage && (
          <PreviewField label={t("pidinstImport.preview.landingPage")}>
            <ExternalLink href={record.landingPage} />
          </PreviewField>
        )}
        {record.alternateIdentifier && (
          <PreviewField label={t("pidinstImport.preview.alternateIdentifier")}>
            {record.alternateIdentifier}
          </PreviewField>
        )}
      </Box>
    </Box>
  );
}

type PidinstImportDialogArgs = {
  open: boolean;
  onClose: () => void;
  onImported: (instrument: { id: number; globalId: string }) => void;
};

/**
 * Search the deployment's enabled PIDINST provider for published instrument records and import
 * one as a new Instrument with a linked identifier. The server routes the search and re-fetches
 * the record on import, so this dialog only shows hits and sends back the chosen PID.
 */
export default function PidinstImportDialog({ open, onClose, onImported }: PidinstImportDialogArgs): React.ReactNode {
  const confirm = useConfirm();
  const { t } = useTranslation(["inventory", "common"]);
  const { getToken } = useOauthToken();
  const { isViewportSmall } = useViewportDimensions();
  const { addAlert, removeAlert } = React.useContext(AlertContext);
  const [query, setQuery] = React.useState("");
  const [result, setResult] = React.useState<null | PidinstSearchResult>(null);
  const [searching, setSearching] = React.useState(false);
  const [selectedPid, setSelectedPid] = React.useState<null | string>(null);
  const [importing, setImporting] = React.useState(false);
  const [columnsMenuAnchorEl, setColumnsMenuAnchorEl] = React.useState<HTMLElement | null>(null);
  /**
   * Set when the user closes the dialog while an import is still running. A ref rather than state
   * because the in-flight import reads it after the close, outside the render that started it.
   */
  const closedDuringImport = React.useRef(false);

  const hits = result?.hits ?? [];
  const selected = hits.find((hit) => hit.pid === selectedPid) ?? null;

  const queryTooShort = query.trim().length < MIN_QUERY_LENGTH;

  const providerLabel = (provider: string) =>
    provider === "PIDINST_B2INST" ? t("pidinstImport.providers.b2inst") : t("pidinstImport.providers.datacite");

  async function runSearch() {
    const trimmed = query.trim();
    if (trimmed.length < MIN_QUERY_LENGTH) return;
    setSearching(true);
    setSelectedPid(null);
    try {
      const { data } = await axios.get<PidinstSearchResult>("/api/inventory/v1/pidinst/search", {
        params: { query: trimmed },
        headers: {
          Authorization: `Bearer ${await getToken()}`,
        },
      });
      setResult(data);
    } catch (e) {
      // the whole AxiosError carries config.headers, and those hold the bearer token
      console.error("PIDINST search failed", getErrorMessage(e, t("errors.unknownReason")));
      // cleared so the grid does not keep listing the last successful search under the new query
      setResult(null);
      addAlert(
        mkAlert({
          variant: "error",
          title: t("pidinstImport.searchError"),
          message: getErrorMessage(e, t("errors.unknownReason")),
        }),
      );
    } finally {
      setSearching(false);
    }
  }

  async function importRecord(record: PidinstRecord): Promise<boolean> {
    setImporting(true);
    closedDuringImport.current = false;
    const importingAlert = mkAlert({
      variant: "notice",
      title: t("pidinstImport.importing.title"),
      message: t("pidinstImport.importing.message", { name: record.name ?? record.pid }),
      isInfinite: true,
    });
    addAlert(importingAlert);
    try {
      const { data } = await axios.post<ImportedInstrumentResponse>(
        "/api/inventory/v1/instruments/importPidinst",
        { pid: record.pid },
        {
          headers: {
            Authorization: `Bearer ${await getToken()}`,
          },
        },
      );
      addAlert(
        mkAlert({
          variant: "success",
          message: t("pidinstImport.importSuccess"),
          details: [
            {
              variant: "success",
              title: data.name,
              record: new LinkableRecordFromGlobalId(data.globalId),
            },
          ],
        }),
      );
      // closing mid-import promises only that the result will not be shown here; moving the user
      // to the new instrument anyway would be the opposite of what they chose. The toast above
      // still links to it, so the import is not lost.
      if (!closedDuringImport.current) {
        onImported({ id: data.id, globalId: data.globalId });
      }
      return true;
    } catch (e) {
      console.error("PIDINST import failed", getErrorMessage(e, t("errors.unknownReason")));
      addAlert(
        mkAlert({
          variant: "error",
          title: t("pidinstImport.importError"),
          message: getErrorMessage(e, t("errors.unknownReason")),
        }),
      );
      return false;
    } finally {
      setImporting(false);
      removeAlert(importingAlert);
    }
  }

  function resetState() {
    setQuery("");
    setResult(null);
    setSearching(false);
    setSelectedPid(null);
    setImporting(false);
    setColumnsMenuAnchorEl(null);
  }

  function handleClose() {
    if (!importing) {
      onClose();
      resetState();
      return;
    }
    confirm(
      t("pidinstImport.closeConfirm.title"),
      t("pidinstImport.closeConfirm.message"),
      t("pidinstImport.closeConfirm.confirm"),
      t("pidinstImport.closeConfirm.cancel"),
    ).then((confirmed) => {
      if (confirmed) {
        closedDuringImport.current = true;
        onClose();
        resetState();
      }
    });
  }

  const importValidation = !selected
    ? IsInvalid(t("pidinstImport.validation.noSelection"))
    : selected.linkedInstrumentGlobalId
      ? IsInvalid(
          t("pidinstImport.validation.alreadyLinked", {
            globalId: selected.linkedInstrumentGlobalId,
          }),
        )
      : IsValid();

  return (
    <ThemeProvider theme={createAccentedTheme(INSTRUMENT_ACCENT_COLOR)}>
      <Dialog open={open} onClose={handleClose} maxWidth="lg" fullWidth fullScreen={isViewportSmall}>
        <AppBar
          variant="dialog"
          currentPage={t("pidinstImport.appBarTitle")}
          accessibilityTips={{
            supportsHighContrastMode: true,
          }}
          helpPage={{
            docLink: helpDocsArticleUrl("pidinstIdentifiers"),
            title: t("pidinstImport.helpTitle"),
          }}
        />
        <DialogTitle variant="h3">{t("pidinstImport.title")}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ height: "100%", flexWrap: "nowrap" }}>
            <Box>
              <Typography variant="body2" sx={{ maxWidth: "54em" /* entirely arbitrary */ }}>
                {t("pidinstImport.description")}
              </Typography>
              <Typography variant="body2">
                <TransRichText i18nKey="inventory:pidinstImport.descriptionLinks" />
              </Typography>
            </Box>
            <Box
              component="form"
              onSubmit={(event: React.FormEvent) => {
                event.preventDefault();
                void runSearch();
              }}
            >
              <Stack direction="row" spacing={1} sx={{ alignItems: "flex-start" }}>
                <TextField
                  label={t("pidinstImport.search.label")}
                  placeholder={t("pidinstImport.search.placeholder")}
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  size="small"
                  fullWidth
                  disabled={searching || importing}
                  // the space keeps the helper row reserved, so the results below do not jump as it appears
                  helperText={
                    queryTooShort && query !== ""
                      ? t("pidinstImport.search.validation.tooShort", { min: MIN_QUERY_LENGTH })
                      : " "
                  }
                />
                <Button type="submit" variant="outlined" disabled={searching || importing || queryTooShort}>
                  {t("common:actions.search")}
                </Button>
              </Stack>
            </Box>
            {/* rendered even with nothing to say: a live region inserted together with its text
                is not announced, so the first search's summary would be silent */}
            <Typography variant="body2" aria-live="polite" role="status">
              {result && result.hits.length > 0 && (
                <>
                  <span>
                    {t("pidinstImport.results.summary", {
                      shown: result.hits.length,
                      total: result.total,
                      provider: providerLabel(result.provider),
                    })}
                  </span>
                  {result.total > result.hits.length && (
                    <>
                      {" "}
                      <span>{t("pidinstImport.results.truncated", { shown: result.hits.length })}</span>
                    </>
                  )}
                </>
              )}
            </Typography>
            {/* fixed so a long result set scrolls inside the grid instead of pushing the preview off-screen */}
            <Box sx={{ height: "380px" }}>
              <DataGridWithRadioSelection
                columns={[
                  DataGridColumn.newColumnWithValueGetter<"name", PidinstRecord, string>(
                    "name",
                    (row) => row.name ?? "",
                    {
                      headerName: t("pidinstImport.columns.name"),
                      flex: 1,
                      sortable: false,
                    },
                  ),
                  DataGridColumn.newColumnWithFieldName<"pid", PidinstRecord>("pid", {
                    headerName: t("pidinstImport.columns.pid"),
                    flex: 1,
                    sortable: false,
                    renderCell: ({ row }) =>
                      row.publicUrl ? (
                        <Link href={row.publicUrl} target="_blank" rel="noreferrer">
                          {row.pid}
                        </Link>
                      ) : (
                        row.pid
                      ),
                  }),
                  DataGridColumn.newColumnWithValueGetter<"manufacturers", PidinstRecord, string>(
                    "manufacturers",
                    (row) => joined(row.manufacturers),
                    {
                      headerName: t("pidinstImport.columns.manufacturers"),
                      flex: 1,
                      sortable: false,
                    },
                  ),
                  DataGridColumn.newColumnWithValueGetter<"owners", PidinstRecord, string>(
                    "owners",
                    (row) => joined(row.owners),
                    {
                      headerName: t("pidinstImport.columns.owners"),
                      flex: 1,
                      sortable: false,
                    },
                  ),
                  DataGridColumn.newColumnWithValueGetter<"linkedTo", PidinstRecord, string>(
                    "linkedTo",
                    (row) => row.linkedInstrumentGlobalId ?? "",
                    {
                      headerName: t("pidinstImport.columns.linkedTo"),
                      flex: 0.7,
                      sortable: false,
                      renderCell: ({ row }) =>
                        row.linkedInstrumentGlobalId ? (
                          <GlobalId record={new LinkableRecordFromGlobalId(row.linkedInstrumentGlobalId)} />
                        ) : null,
                    },
                  ),
                  DataGridColumn.newColumnWithValueGetter<"model", PidinstRecord, string>(
                    "model",
                    (row) => row.model ?? "",
                    {
                      headerName: t("pidinstImport.columns.model"),
                      flex: 1,
                      sortable: false,
                    },
                  ),
                  DataGridColumn.newColumnWithValueGetter<"instrumentTypes", PidinstRecord, string>(
                    "instrumentTypes",
                    (row) => joined(row.instrumentTypes),
                    {
                      headerName: t("pidinstImport.columns.instrumentTypes"),
                      flex: 1,
                      sortable: false,
                    },
                  ),
                  DataGridColumn.newColumnWithValueGetter<"commissioned", PidinstRecord, string>(
                    "commissioned",
                    (row) => row.commissioned ?? "",
                    {
                      headerName: t("pidinstImport.columns.commissioned"),
                      flex: 0.7,
                      sortable: false,
                    },
                  ),
                  DataGridColumn.newColumnWithValueGetter<"state", PidinstRecord, string>(
                    "state",
                    (row) => row.state ?? "",
                    {
                      headerName: t("pidinstImport.columns.state"),
                      flex: 0.7,
                      sortable: false,
                    },
                  ),
                ]}
                initialState={{
                  columns: {
                    columnVisibilityModel: {
                      manufacturers: !isViewportSmall,
                      owners: !isViewportSmall,
                      linkedTo: !isViewportSmall,
                      model: false,
                      instrumentTypes: false,
                      commissioned: false,
                      state: false,
                    },
                  },
                }}
                rows={hits}
                selectedRowId={selectedPid}
                onSelectionChange={(newSelectionId) => setSelectedPid(String(newSelectionId))}
                selectRadioAriaLabelFunc={(row) => t("pidinstImport.selectRadioLabel", { name: row.name ?? row.pid })}
                disableColumnFilter
                hideFooter
                // without this the toolbar slot does not render, so the hidden columns have no Columns button
                showToolbar
                localeText={{
                  noRowsLabel: result ? t("pidinstImport.results.none") : t("pidinstImport.results.prompt"),
                }}
                loading={searching}
                slots={{
                  toolbar: GridToolbar,
                  loadingOverlay: SearchingOverlay,
                }}
                slotProps={{
                  toolbar: {
                    setColumnsMenuAnchorEl,
                  },
                  panel: {
                    target: columnsMenuAnchorEl,
                  },
                }}
                getRowId={(row) => row.pid}
              />
            </Box>
            {selected && <RecordPreview record={selected} />}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Stack direction="row" spacing={1} sx={{ ml: "auto" }}>
            <Button onClick={() => handleClose()}>{t("common:actions.close")}</Button>
            <ValidatingSubmitButton
              onClick={() => {
                if (selected) {
                  void importRecord(selected).then((success) => {
                    if (success) handleClose();
                  });
                }
              }}
              validationResult={importValidation}
              loading={importing}
            >
              {t("common:actions.import")}
            </ValidatingSubmitButton>
          </Stack>
        </DialogActions>
      </Dialog>
    </ThemeProvider>
  );
}
