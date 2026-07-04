import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Portal from "@mui/material/Portal";
import Stack from "@mui/material/Stack";
import { ThemeProvider } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import type { GridRowId } from "@mui/x-data-grid";
import DOMPurify from "dompurify";
import { observer } from "mobx-react-lite";
import React, { useContext, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import TransRichText, { helpDocsArticleUrl } from "@/modules/common/i18n/TransRichText";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/dmptool";
import AppBar from "../../components/AppBar";
import { DataGridWithRadioSelection } from "../../components/DataGridWithRadioSelection";
import { Dialog, DialogBoundary } from "../../components/DialogBoundary";
import ValidatingSubmitButton, { IsInvalid, IsValid } from "../../components/ValidatingSubmitButton";
import useViewportDimensions from "../../hooks/browser/useViewportDimensions";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import { DataGridColumn } from "../../util/table";
import ScopeField, { type Scope } from "./ScopeField";

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
            // this is so that the heights of the dialog's content are constrained and scrollbars appear
            // 24px margin above and below, 3px border above and below
            height: fullScreen ? "100%" : "calc(100% - 48px)",
          },
        },
      }}
    />
  );
}

export type Plan = {
  id: number;
  title: string;
  description: string;
  modified: string;
  created: string;
};

function DMPDialogContent({ setOpen }: { setOpen: (open: boolean) => void }): React.ReactNode {
  const { addAlert } = useContext(AlertContext);
  const { isViewportSmall } = useViewportDimensions();
  const { t } = useTranslation(["apps", "common"]);

  const [DMPHost, setDMPHost] = React.useState<string | null>();
  const [DMPs, setDMPs] = useState<Array<Plan>>([]);
  const [selectedPlan, setSelectedPlan] = useState<Plan | null>();

  const [fetching, setFetching] = useState(false);
  const fetchingId = useRef(0);

  const [importing, setImporting] = useState(false);

  React.useEffect(() => {
    axios
      .get<string>("/apps/dmptool/baseUrlHost")
      .then((r) => setDMPHost(r.data))
      .catch((e) => console.error("Cannot establish DMP host", e));
  }, []);

  const getDMPs = async (scope: Scope) => {
    setFetching(true);
    const thisId = fetchingId.current;
    try {
      const r = await axios.get<{
        success: true;
        data: {
          items: Array<{ dmp: Plan }>;
        };
        error?: { errorMessages: Array<string> };
      }>(`/apps/dmptool/plans?scope=${scope}`);
      fetchingId.current += 1;

      if (thisId === fetchingId.current - 1) {
        if (r.data.success) {
          setDMPs(r.data.data.items.map((item) => item.dmp));
        } else {
          if (/Unable to load your DMPs. For more information/.test(r.data.error?.errorMessages[0] ?? "")) {
            addAlert(
              mkAlert({
                title: t("dmpIntegrations.dialog.error.unableToLoad"),
                message: <TransRichText i18nKey="apps:dmpIntegrations.dialog.forMoreInfo" />,
                variant: "error",
              }),
            );
            return;
          }
          addAlert(
            mkAlert({
              title: t("dmpIntegrations.dialog.error.fetchFailed"),
              message: r.data?.error?.errorMessages[0] ?? t("dmpIntegrations.dialog.error.couldNotImport"),
              variant: "error",
            }),
          );
        }
      } else {
        console.info(
          "The response from this request is being discarded because a different listing of plans has been requested whilst this network call was in flight.",
        );
      }
    } catch (e) {
      console.error("Could not get DMPs for scope", e);
      if (e instanceof Error) {
        addAlert(
          mkAlert({
            title: t("dmpIntegrations.dialog.error.fetchFailed"),
            message: t("dmpIntegrations.dialog.error.couldNotGet", { message: e.message }),
            variant: "error",
          }),
        );
      }
      if (thisId === fetchingId.current) {
        setFetching(false);
      }
    } finally {
      setFetching(false);
    }
  };

  useEffect(() => {
    void getDMPs("MINE");
  }, []);

  const handleImport = async () => {
    try {
      setImporting(true);
      const selectedPlanId = Number(selectedPlan?.id);
      if (selectedPlan) {
        await axios
          .post<{
            success: true;
            error?: { errorMessages: string[] };
          }>(`/apps/dmptool/jsonById/${selectedPlanId}`, {})
          .then((r) => {
            addAlert(
              mkAlert(
                r.data.success
                  ? {
                      title: t("dmpIntegrations.dialog.importSuccess"),
                      message: t("dmpIntegrations.dialog.importSuccessMessage", { planId: selectedPlanId }),
                      variant: "success",
                    }
                  : {
                      title: t("dmpIntegrations.dialog.error.importFailed"),
                      message: r.data.error?.errorMessages[0] || t("dmpIntegrations.dialog.error.couldNotImport"),
                      variant: "error",
                    },
              ),
            );
            setSelectedPlan(null);
          });
      }
    } catch (e) {
      console.error("Could not import DMP", e);
    } finally {
      setImporting(false);
    }
  };

  return (
    <>
      <AppBar
        variant="dialog"
        currentPage={t("dmpIntegrations.dmptool")}
        accessibilityTips={{
          supportsHighContrastMode: true,
        }}
        helpPage={{
          docLink: helpDocsArticleUrl("o0wlhlgxnr-dmptool-integration"),
          title: t("dmpIntegrations.dialog.helpTitle", { name: t("dmpIntegrations.dmptool") }),
        }}
      />
      <DialogTitle variant="h3">{t("dmpIntegrations.dialog.importDmpIntoGallery")}</DialogTitle>
      <DialogContent>
        <Stack
          sx={{
            flexWrap: "nowrap",
            height: "calc(100% + 16px)",
          }}
          spacing={2}

          /*
           * The height of 100% ensures that the table is scrollable
           * The extra 16px prevents excessive whitespace, more and we get double scrollbars
           */
        >
          <Box>
            <Typography variant="body2">
              <TransRichText
                i18nKey="apps:dmpIntegrations.dialog.dmptoolImportDescAndDocsLink"
                values={{ serverAlias: DMPHost ?? "", hasAlias: DMPHost ? "yes" : "no" }}
              />
            </Typography>
          </Box>
          <ScopeField
            getDMPs={(scope) => {
              void getDMPs(scope);
            }}
          />
          <Box sx={{ flexGrow: 1, overflowY: "auto" }}>
            <DataGridWithRadioSelection
              columns={[
                DataGridColumn.newColumnWithFieldName<"title", Plan>("title", {
                  headerName: t("dmpIntegrations.dialog.columns.title"),
                  flex: 1,
                  sortable: false,
                }),
                DataGridColumn.newColumnWithFieldName<"id", Plan>("id", {
                  headerName: t("dmpIntegrations.dialog.columns.id"),
                  flex: 1,
                  sortable: false,
                }),
                DataGridColumn.newColumnWithFieldName<"description", Plan>("description", {
                  renderCell: (params: { row: Plan }) => {
                    const sanitized = DOMPurify.sanitize(params.row.description);
                    return (
                      <span
                        dangerouslySetInnerHTML={{
                          __html: `${sanitized.substring(0, 200)} ${sanitized.length > 200 ? "..." : ""}`,
                        }}
                      ></span>
                    );
                  },
                  headerName: t("dmpIntegrations.dialog.columns.description"),
                  display: "flex",
                  flex: 1,
                  sortable: false,
                }),
                DataGridColumn.newColumnWithValueMapper<"created", Plan>(
                  "created",
                  (created) => new Date(created).toLocaleString(),
                  {
                    headerName: t("dmpIntegrations.dialog.columns.createdAt"),
                    flex: 1,
                    sortable: false,
                  },
                ),
                DataGridColumn.newColumnWithValueMapper<"modified", Plan>(
                  "modified",
                  (modified) => new Date(modified).toLocaleString(),
                  {
                    headerName: t("dmpIntegrations.dialog.columns.modifiedAt"),
                    flex: 1,
                    sortable: false,
                  },
                ),
              ]}
              rows={fetching ? [] : DMPs}
              selectedRowId={selectedPlan?.id}
              onSelectionChange={(newSelectionId: GridRowId) => {
                setSelectedPlan(DMPs.find((d) => d.id === newSelectionId));
              }}
              selectRadioAriaLabelFunc={(row) => t("dmpIntegrations.dialog.selectPlanLabel", { label: row.title })}
              initialState={{
                columns: {
                  columnVisibilityModel: {
                    id: !isViewportSmall,
                    description: false,
                    created: false,
                    modified: false,
                  },
                },
              }}
              density="compact"
              disableColumnFilter
              hideFooter
              slots={{
                pagination: null,
              }}
              localeText={{
                noRowsLabel: t("dmpIntegrations.dialog.noDmps"),
              }}
              loading={fetching}
              getRowId={(row) => row.id}
              getRowHeight={() => "auto"}
              onCellKeyDown={({ id }, e) => {
                if (e.key === " " || e.key === "Enter") {
                  setSelectedPlan(DMPs.find((d) => d.id === id));
                  e.stopPropagation();
                }
              }}
            />
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Stack direction="row" spacing={1} sx={{ ml: "auto" }}>
          <Button onClick={() => setOpen(false)} disabled={importing}>
            {selectedPlan ? t("common:actions.cancel") : t("common:actions.close")}
          </Button>
          <ValidatingSubmitButton
            onClick={() => {
              void handleImport();
            }}
            validationResult={!selectedPlan?.id ? IsInvalid(t("dmpIntegrations.dialog.noDmpSelected")) : IsValid()}
            loading={importing}
          >
            {t("common:actions.import")}
          </ValidatingSubmitButton>
        </Stack>
      </DialogActions>
    </>
  );
}

type DMPDialogArgs = {
  open: boolean;
  setOpen: (open: boolean) => void;
};

/*
 * This simple function just for the outer-most components is so that the
 * content of the dialog can use the Alerts context
 *
 * A11y: note that tabbing through this dialog is not possible because the
 * custom tabbing behaviour of the Gallery page takes control of the tab key
 * events away from the React+MUI tech stack. See ../../../../scripts/global.js
 */
function DMPDialog({ open, setOpen }: DMPDialogArgs): React.ReactNode {
  const { isViewportSmall } = useViewportDimensions();

  /*
   * We use DialogBoundary to wrap the Dialog so that Alerts can be shown atop
   * the dialog whilst keeping them accessible to screen readers. We then have
   * to manually add Portal back (Dialogs normally include a Portal) so that
   * the Dialog isn't rendered inside the Menu where it will not be seen once
   * the Menu is closed.
   */

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

export default observer(DMPDialog);
