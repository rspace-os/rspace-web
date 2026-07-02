import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Link from "@mui/material/Link";
import Portal from "@mui/material/Portal";
import Stack from "@mui/material/Stack";
import { ThemeProvider } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import type { GridRowId } from "@mui/x-data-grid";
import DOMPurify from "dompurify";
import { observer } from "mobx-react-lite";
import React, { useContext, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import type { DswConfig } from "@/eln-dmp-integration/DSW/DSWAccentMenuItem";
import TransRichText from "@/modules/common/i18n/TransRichText";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/dsw";
import docLinks from "../../assets/DocLinks";
import AppBar from "../../components/AppBar";
import { DataGridWithRadioSelection } from "../../components/DataGridWithRadioSelection";
import { Dialog, DialogBoundary } from "../../components/DialogBoundary";
import ValidatingSubmitButton, { IsInvalid, IsValid } from "../../components/ValidatingSubmitButton";
import useViewportDimensions from "../../hooks/browser/useViewportDimensions";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import AnalyticsContext from "../../stores/contexts/Analytics";
import { DataGridColumn } from "../../util/table";

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

export interface DswProject {
  createdAt: string;
  description: string;
  name: string;
  sharing: string;
  state: string;
  template: boolean;
  updatedAt: string;
  uuid: string;
  visibility: string;
}

export interface DswProjectWithOrigin extends DswProject {
  id: string;
  serverAlias: string;
}

function DSWImportDialogContent({
  setOpen,
  connection,
}: {
  setOpen: (open: boolean) => void;
  connection: DswConfig;
}): React.ReactNode {
  const { addAlert } = useContext(AlertContext);
  const { isViewportSmall } = useViewportDimensions();
  const { trackEvent } = React.useContext(AnalyticsContext);
  const { t } = useTranslation(["apps", "common"]);

  const [DMPs, setDMPs] = React.useState<Array<DswProjectWithOrigin>>([]);
  const [selectedPlan, setSelectedPlan] = useState<DswProjectWithOrigin | null>();

  const [fetching, setFetching] = useState(false);

  const [importing, setImporting] = useState(false);

  const getDMPs = async () => {
    setFetching(true);

    const allPlans: Array<DswProjectWithOrigin> = [];
    try {
      const r = await axios.get<{
        success: true;
        data: Array<DswProject>;
        error?: { errorMessages: Array<string> };
      }>(`/apps/dsw/plans?serverAlias=${connection.DSW_ALIAS}`);

      if (r.data.success) {
        // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
        Object.entries(r.data.data).map(([, project]) => {
          const projectWithAlias: DswProjectWithOrigin = {
            createdAt: project.createdAt,
            description: project.description,
            id: project.uuid,
            name: project.name,
            serverAlias: connection.DSW_ALIAS,
            sharing: project.sharing,
            state: project.state,
            template: project.template,
            updatedAt: project.updatedAt,
            uuid: project.uuid,
            visibility: project.visibility,
          };
          allPlans.push(projectWithAlias);
        });

        setDMPs(allPlans);
      } else {
        setFetching(false);
        const errorMsg = r.data?.error?.errorMessages ? r.data.error.errorMessages[0] : null;
        addAlert(
          mkAlert({
            title: t("dmpIntegrations.dialog.error.unableToLoadProjects"),
            message: (
              <>
                {errorMsg}
                <br />
                <TransRichText i18nKey="apps:dmpIntegrations.dialog.forMoreInfo" values={{ link: docLinks.dsw }} />
              </>
            ),
            variant: "error",
          }),
        );
        return;
      }
      setFetching(false);
    } catch (e) {
      console.error("Could not get DSW plans for reason: ", e);
      if (e instanceof Error) {
        addAlert(
          mkAlert({
            title: t("dmpIntegrations.dialog.error.unableToLoadProjects"),
            message: t("dmpIntegrations.dialog.error.couldNotGet", { message: e.message }),
            variant: "error",
          }),
        );
      }
      setFetching(false);
    } finally {
      setFetching(false);
    }
  };

  useEffect(() => {
    void getDMPs();
  }, []);

  const handleImport = async () => {
    try {
      trackEvent("user:import:from_dsw:gallery");
      setImporting(true);
      if (selectedPlan) {
        await axios
          .post<{
            success: true;
            error?: { errorMessages: string[] };
          }>(`/apps/dsw/importPlan?serverAlias=${connection.DSW_ALIAS}&planUuid=${selectedPlan.uuid}`, {})
          .then((r) => {
            addAlert(
              mkAlert(
                r.data.success
                  ? {
                      title: t("dmpIntegrations.dialog.importSuccess"),
                      message: t("dmpIntegrations.dialog.importSuccessMessage", { planId: selectedPlan.name }),
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
        currentPage={t("dmpIntegrations.dsw")}
        accessibilityTips={{
          supportsHighContrastMode: true,
        }}
        helpPage={{
          docLink: docLinks.dsw,
          title: `${t("dmpIntegrations.dsw")} help`,
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
                i18nKey="apps:dmpIntegrations.dialog.dswImportDesc"
                values={{ serverAlias: connection.DSW_ALIAS }}
              />
            </Typography>
            <Typography variant="body2">
              <TransRichText
                i18nKey="apps:dmpIntegrations.dialog.dswDocsLink"
                components={{
                  helpLink: <Link href={docLinks.dsw} />,
                }}
              />
            </Typography>
          </Box>
          <Box sx={{ flexGrow: 1, overflowY: "auto" }}>
            <DataGridWithRadioSelection
              columns={[
                DataGridColumn.newColumnWithFieldName<"name", DswProjectWithOrigin>("name", {
                  renderCell: (params: { row: DswProjectWithOrigin }) => {
                    const sanitized = DOMPurify.sanitize(params.row.name);
                    return (
                      <span
                        dangerouslySetInnerHTML={{
                          __html: `${sanitized.substring(0, 200)} ${sanitized.length > 200 ? "..." : ""}`,
                        }}
                      ></span>
                    );
                  },
                  headerName: t("dmpIntegrations.dialog.columns.name"),
                  flex: 1,
                  sortable: true,
                }),
                DataGridColumn.newColumnWithFieldName<"description", DswProjectWithOrigin>("description", {
                  renderCell: (params: { row: DswProjectWithOrigin }) => {
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
                  flex: 1,
                  sortable: true,
                }),
                DataGridColumn.newColumnWithValueMapper<"createdAt", DswProjectWithOrigin>(
                  "createdAt",
                  (createdAt) => new Date(createdAt).toLocaleString(),
                  {
                    headerName: t("dmpIntegrations.dialog.columns.createdAt"),
                    display: "flex",
                    flex: 1,
                    sortable: true,
                  },
                ),
                DataGridColumn.newColumnWithValueMapper<"updatedAt", DswProjectWithOrigin>(
                  "updatedAt",
                  (updatedAt) => new Date(updatedAt).toLocaleString(),
                  {
                    headerName: t("dmpIntegrations.dialog.columns.updatedAt"),
                    flex: 1,
                    sortable: true,
                  },
                ),
              ]}
              rows={fetching ? [] : DMPs}
              selectedRowId={selectedPlan?.id}
              onSelectionChange={(newSelectionId: GridRowId) => {
                setSelectedPlan(DMPs.find((d) => d.id === newSelectionId));
              }}
              selectRadioAriaLabelFunc={(row) => t("dmpIntegrations.dialog.selectPlanLabel", { label: row.name })}
              initialState={{
                columns: {
                  columnVisibilityModel: {
                    id: !isViewportSmall,
                    createdAt: false,
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
                noRowsLabel: t("dmpIntegrations.dialog.noProjects"),
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

type DSWImportDialogArgs = {
  open: boolean;
  setOpen: (open: boolean) => void;
  connection: DswConfig;
};

/*
 * This simple function just for the outer-most components is so that the
 * content of the dialog can use the Alerts context
 *
 * A11y: note that tabbing through this dialog is not possible because the
 * custom tabbing behaviour of the Gallery page takes control of the tab key
 * events away from the React+MUI tech stack. See ../../../../scripts/global.js
 */
function DSWImportDialog({ open, setOpen, connection }: DSWImportDialogArgs): React.ReactNode {
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
            <DSWImportDialogContent setOpen={setOpen} connection={connection} />
          </CustomDialog>
        </DialogBoundary>
      </Portal>
    </ThemeProvider>
  );
}

export default observer(DSWImportDialog);
