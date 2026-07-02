import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import InputLabel from "@mui/material/InputLabel";
import Link from "@mui/material/Link";
import MenuItem from "@mui/material/MenuItem";
import Select from "@mui/material/Select";
import Stack from "@mui/material/Stack";
import { ThemeProvider } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import { GridToolbarColumnsButton, GridToolbarContainer } from "@mui/x-data-grid";
import React from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import { useConfirm } from "@/components/ConfirmProvider";
import useOauthToken from "@/hooks/auth/useOauthToken";
import i18n from "@/modules/common/i18n";
import TransRichText, { richTextLink } from "@/modules/common/i18n/TransRichText";
import Result from "@/util/result";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/fieldmark";
import docLinks from "../../assets/DocLinks";
import AppBar from "../../components/AppBar";
import { DataGridWithRadioSelection } from "../../components/DataGridWithRadioSelection";
import { Dialog } from "../../components/DialogBoundary";
import ValidatingSubmitButton, { IsInvalid, IsValid } from "../../components/ValidatingSubmitButton";
import useViewportDimensions from "../../hooks/browser/useViewportDimensions";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import type { LinkableRecord } from "../../stores/definitions/LinkableRecord";
import { Optional } from "../../util/optional";
import * as Parsers from "../../util/parsers";
import { DataGridColumn } from "../../util/table";

const firstResult = <T,>(items: ReadonlyArray<T>): Result<T> =>
  Result.fromNullable(items.at(0), new Error("Array is empty"));

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
    return i18n.t("inventory:recordTypes.container.singular");
  }

  get iconName(): string {
    return "container";
  }

  get permalinkURL(): string {
    if (!this.globalId) throw new Error("Impossible");
    return `/globalId/${this.globalId}`;
  }
}

const GridToolbar = ({ setColumnsMenuAnchorEl }: { setColumnsMenuAnchorEl: (anchorEl: HTMLElement) => void }) => {
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
      <Box sx={{ flexGrow: 1 }}></Box>
      <GridToolbarColumnsButton
        ref={(node) => {
          if (node) columnMenuRef.current = node;
        }}
      />
    </GridToolbarContainer>
  );
};

function CustomLoadingOverlay() {
  const id = React.useId();
  const { t } = useTranslation(["inventory", "common"]);
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
        {t("fieldmarkImport.fetchingNotebooks")}
      </Box>
    </Box>
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
export default function FieldmarkImportDialog({ open, onClose }: FieldmarkImportDialogArgs): React.ReactNode {
  const confirm = useConfirm();
  const { t } = useTranslation(["inventory", "common"]);
  const { getToken } = useOauthToken();
  const { isViewportSmall } = useViewportDimensions();
  const { addAlert, removeAlert } = React.useContext(AlertContext);
  const [notebooks, setNotebooks] = React.useState<null | ReadonlyArray<Notebook>>(null);
  const [selectedNotebook, setSelectedNotebook] = React.useState<null | Notebook>(null);
  const [columnsMenuAnchorEl, setColumnsMenuAnchorEl] = React.useState<HTMLElement | null>(null);
  const [fetchingNotebooks, setFetchingNotebooks] = React.useState(false);
  const [importing, setImporting] = React.useState(false);
  const [fetchingIdentifierFields, setFetchingIdentifierFields] = React.useState(false);
  const [identifierFields, setIdentifierFields] = React.useState<ReadonlyArray<string> | null>(null);
  const [showIgsnMessage, setShowIgsnMessage] = React.useState(false);

  type IdentifierFieldSelection = { type: "unselected" } | { type: "none" } | { type: "selected"; field: string };

  const [identifierFieldSelection, setIdentifierFieldSelection] = React.useState<IdentifierFieldSelection>({
    type: "unselected",
  });

  React.useEffect(() => {
    void (async () => {
      if (!open) return;
      setFetchingNotebooks(true);
      setIdentifierFieldSelection({ type: "unselected" });
      setSelectedNotebook(null);
      setNotebooks(null);
      try {
        const { data } = await axios.get<ReadonlyArray<Notebook>>("/api/inventory/v1/fieldmark/notebooks", {
          headers: {
            Authorization: `Bearer ${await getToken()}`,
          },
        });
        setNotebooks(data);
      } catch (e) {
        console.error(e);
        if (e instanceof Error) {
          const message = Parsers.objectPath(["response", "data", "data", "validationErrors"], e)
            .flatMap(Parsers.isArray)
            .flatMap(firstResult)
            .flatMap(Parsers.isObject)
            .flatMap(Parsers.isNotNull)
            .flatMap(Parsers.getValueWithKey("message"))
            .flatMap(Parsers.isString)
            .orElse(e.message);
          addAlert(
            mkAlert({
              variant: "error",
              title: t("fieldmarkImport.fetchError"),
              message,
            }),
          );
        }
      } finally {
        setFetchingNotebooks(false);
      }
    })();
  }, [open]);

  async function importNotebook(notebook: Notebook) {
    setImporting(true);
    const importingAlert = mkAlert({
      variant: "notice",
      title: t("fieldmarkImport.importNotebook.title"),
      message: t("fieldmarkImport.importNotebook.message", { name: notebook.name }),
      isInfinite: true,
    });
    addAlert(importingAlert);
    try {
      const { data } = await axios.post<{
        containerName: string;
        containerGlobalId: string;
      }>(
        "/api/inventory/v1/import/fieldmark/notebook",
        {
          notebookId: notebook.metadata.project_id,
          ...(identifierFieldSelection.type === "selected" ? { identifier: identifierFieldSelection.field } : {}),
        },
        {
          headers: {
            Authorization: `Bearer ${await getToken()}`,
          },
        },
      );
      addAlert(
        mkAlert({
          variant: "success",
          message: t("fieldmarkImport.importSuccess"),
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
            ...Parsers.objectPath(["response", "data", "data", "validationErrors"], e)
              .flatMap(Parsers.isArray)
              .flatMap((array) =>
                Result.all(
                  ...array.map((x) =>
                    Parsers.isObject(x)
                      .flatMap(Parsers.isNotNull)
                      .flatMap(Parsers.getValueWithKey("message"))
                      .flatMap(Parsers.isString),
                  ),
                ),
              )
              .map((errors) => ({
                message: t("fieldmarkImport.importError"),
                details: errors.map((error) => ({
                  variant: "error" as const,
                  title: error,
                })),
              }))
              .orElse({
                title: t("fieldmarkImport.importError"),
                message: e.message,
              }),
          }),
        );
      throw e;
    } finally {
      setImporting(false);
      if (importingAlert) removeAlert(importingAlert);
    }
  }

  async function fetchIdentifierColumn(notebookId: Notebook["metadata"]["project_id"]) {
    setFetchingIdentifierFields(true);
    setIdentifierFields(null);
    setIdentifierFieldSelection({ type: "unselected" });
    setShowIgsnMessage(false);
    try {
      const { data } = await axios.get(
        `/api/inventory/v1/fieldmark/notebooks/igsnCandidateFields?notebookId=${notebookId}`,
        {
          headers: {
            Authorization: `Bearer ${await getToken()}`,
          },
        },
      );
      setIdentifierFields(
        Parsers.isArray(data)
          .flatMap((fieldNames) => Result.all(...fieldNames.map(Parsers.isString)))
          .elseThrow(),
      );
    } catch (error: unknown) {
      console.error(error);
      setIdentifierFields([]);
      setShowIgsnMessage(
        Parsers.objectPath(["response", "data", "data", "validationErrors"], error)
          .flatMap(Parsers.isArray)
          .map((validationErrors) =>
            validationErrors.some((validationError) =>
              Parsers.objectPath(["message"], validationError)
                .flatMap(Parsers.isString)
                .map((message) => /IGSN integration is not enabled/.test(message))
                .orElse(false),
            ),
          )
          .orElse(false),
      );
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
      t("fieldmarkImport.closeConfirm.title"),
      t("fieldmarkImport.closeConfirm.message"),
      t("fieldmarkImport.closeConfirm.confirm"),
      t("fieldmarkImport.closeConfirm.cancel"),
    ).then((confirmed) => {
      if (confirmed) {
        onClose();
        resetState();
      }
    });
  }
  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <Dialog open={open} onClose={handleClose} maxWidth="lg" fullWidth fullScreen={isViewportSmall}>
        <AppBar
          variant="dialog"
          currentPage={t("fieldmarkImport.appBarTitle")}
          accessibilityTips={{
            supportsHighContrastMode: true,
          }}
          helpPage={{
            docLink: docLinks.fieldmark,
            title: t("fieldmarkImport.helpTitle"),
          }}
        />
        <DialogTitle variant="h3">{t("fieldmarkImport.title")}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ height: "100%", flexWrap: "nowrap" }}>
            <Box>
              <Typography variant="body2" sx={{ maxWidth: "54em" /* entirely arbitrary */ }}>
                {t("fieldmarkImport.description")}
              </Typography>
              <Typography variant="body2">
                <TransRichText
                  ns="inventory"
                  i18nKey="fieldmarkImport.descriptionLinks"
                  components={{
                    docLink: <Link href={docLinks.fieldmark} />,
                  }}
                />
              </Typography>
            </Box>
            <Box>
              <DataGridWithRadioSelection
                columns={[
                  DataGridColumn.newColumnWithFieldName<"name", Notebook>("name", {
                    headerName: t("fieldmarkImport.columns.name"),
                    flex: 1,
                    sortable: false,
                  }),
                  DataGridColumn.newColumnWithFieldName<"status", Notebook>("status", {
                    headerName: t("fieldmarkImport.columns.status"),
                    flex: 1,
                    sortable: false,
                  }),
                  DataGridColumn.newColumnWithValueGetter<"isPublic", Notebook, boolean>(
                    "isPublic",
                    (notebook) => notebook.metadata.ispublic,
                    {
                      headerName: t("fieldmarkImport.columns.isPublic"),
                      flex: 1,
                      sortable: false,
                    },
                  ),
                  DataGridColumn.newColumnWithValueGetter<"description", Notebook, string>(
                    "description",
                    (notebook) => notebook.metadata.pre_description,
                    {
                      headerName: t("fieldmarkImport.columns.description"),
                      flex: 1,
                      sortable: false,
                    },
                  ),
                  DataGridColumn.newColumnWithValueGetter<"projectLead", Notebook, string>(
                    "projectLead",
                    (notebook) => notebook.metadata.project_lead,
                    {
                      headerName: t("fieldmarkImport.columns.projectLead"),
                      flex: 1,
                      sortable: false,
                    },
                  ),
                  DataGridColumn.newColumnWithValueGetter<"id", Notebook, string>(
                    "id",
                    (notebook) => notebook.metadata.project_id,
                    {
                      headerName: t("fieldmarkImport.columns.id"),
                      flex: 1,
                      sortable: false,
                    },
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
                selectedRowId={selectedNotebook?.metadata.project_id}
                onSelectionChange={(newSelectionId) => {
                  Optional.fromNullable((notebooks ?? []).find((n) => n.metadata.project_id === newSelectionId)).do(
                    (newlySelectedNotebook) => {
                      setSelectedNotebook(newlySelectedNotebook);
                      void fetchIdentifierColumn(newlySelectedNotebook.metadata.project_id);
                    },
                  );
                }}
                selectRadioAriaLabelFunc={(row) => t("fieldmarkImport.selectRadioLabel", { name: row.name })}
                disableColumnFilter
                hideFooter
                autoHeight
                localeText={{
                  noRowsLabel: t("fieldmarkImport.noNotebooks"),
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
                    target: columnsMenuAnchorEl,
                  },
                }}
                getRowId={(row) => row.metadata.project_id}
              />
            </Box>
            <Box>
              {selectedNotebook && !(identifierFieldSelection.type === "unselected" && importing) && (
                <>
                  {showIgsnMessage && (
                    <Typography variant="body2" sx={{ ml: 0.5 }}>
                      <TransRichText
                        ns="inventory"
                        i18nKey="fieldmarkImport.igsnMessage"
                        components={{
                          a: richTextLink({ href: docLinks.IGSNIdentifiers }),
                        }}
                      />
                    </Typography>
                  )}
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
                      <Typography variant="body2">{t("fieldmarkImport.igsnField.loading")}</Typography>
                    </Box>
                  ) : (
                    identifierFields &&
                    identifierFields.length > 0 && (
                      <FormControl sx={{ width: "auto", minWidth: 200 }}>
                        <InputLabel id="identifier-field-select-label">
                          {t("fieldmarkImport.igsnField.label")}
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
                          label={t("fieldmarkImport.igsnField.label")}
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
                            <em>{t("fieldmarkImport.igsnField.noIgsn")}</em>
                          </MenuItem>
                          {identifierFields.map((field) => (
                            <MenuItem key={field} value={field}>
                              {field}
                            </MenuItem>
                          ))}
                        </Select>
                        <FormHelperText>{t("fieldmarkImport.igsnField.helperText")}</FormHelperText>
                      </FormControl>
                    )
                  )}
                </>
              )}
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Stack direction="row" spacing={1} sx={{ ml: "auto" }}>
            <Button onClick={() => handleClose()}>{t("common:actions.close")}</Button>
            <ValidatingSubmitButton
              onClick={() => {
                if (selectedNotebook) void importNotebook(selectedNotebook).then(() => handleClose());
              }}
              validationResult={!selectedNotebook ? IsInvalid(t("fieldmarkImport.noNotebookSelected")) : IsValid()}
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
