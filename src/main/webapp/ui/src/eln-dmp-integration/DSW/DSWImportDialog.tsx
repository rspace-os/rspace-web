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
// biome-ignore lint/style/useImportType: initial biome migration
import { GridRowId } from "@mui/x-data-grid";
import DOMPurify from "dompurify";
import { observer } from "mobx-react-lite";
import React, { useContext, useEffect, useState } from "react";
import axios from "@/common/axios";
// biome-ignore lint/style/useImportType: initial biome migration
import { DswConfig } from "@/eln-dmp-integration/DSW/DSWAccentMenuItem";
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

  const [DMPs, setDMPs] = React.useState<Array<DswProjectWithOrigin>>([]);
  const [selectedPlan, setSelectedPlan] = useState<DswProjectWithOrigin | null>();

  const [fetching, setFetching] = useState(false);

  const [importing, setImporting] = useState(false);

  const getDMPs = async () => {
    setFetching(true);

    // biome-ignore lint/style/useConst: initial biome migration
    let allPlans: Array<DswProjectWithOrigin> = [];
    try {
      const r = await axios.get<{
        success: true;
        data: Array<DswProject>;
        error?: { errorMessages: Array<string> };
      }>(`/apps/dsw/plans?serverAlias=${connection.DSW_ALIAS}`);

      if (r.data.success) {
        // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
        Object.entries(r.data.data).map(([, project]) => {
          // biome-ignore lint/style/useConst: initial biome migration
          let projectWithAlias: DswProjectWithOrigin = {
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
        // biome-ignore lint/style/useConst: initial biome migration
        let errorMsg =
          // biome-ignore lint/complexity/useOptionalChain: initial biome migration
          r.data && r.data.error && r.data.error.errorMessages ? r.data.error.errorMessages[0] : null;
        addAlert(
          mkAlert({
            title: "Unable to load projects.",
            message: (
              <>
                {errorMsg}
                <br />
                For more information{" "}
                <a href={docLinks.dsw} rel="noreferrer">
                  visit our docs
                </a>
                .
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
            title: "Unable to load projects.",
            message: `Could not get DMPs: ${e.message}`,
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
                      title: "Success.",
                      message: `DMP ${selectedPlan.name} was successfully imported.`,
                      variant: "success",
                    }
                  : {
                      title: "Import failed.",
                      message: r.data.error?.errorMessages[0] || "Could not import DMP",
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
        currentPage="DSW / FAIR Wizard"
        accessibilityTips={{
          supportsHighContrastMode: true,
        }}
        helpPage={{
          docLink: docLinks.dsw,
          title: "DSW / FAIR Wizard help",
        }}
      />
      <DialogTitle variant="h3">Import a DMP into the Gallery</DialogTitle>
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
              Importing a project from <strong>{connection.DSW_ALIAS}</strong> will make it available to view and
              reference as a DMP within RSpace.
            </Typography>
            <Typography variant="body2">
              See <Link href="https://guide.ds-wizard.org/en/latest/">https://guide.ds-wizard.org/en/latest</Link> and
              our <Link href={docLinks.dsw}>DSW / FAIR Wizard integration docs</Link> for more.
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
                  headerName: "Name",
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
                  headerName: "Description",
                  flex: 1,
                  sortable: true,
                }),
                DataGridColumn.newColumnWithValueMapper<"createdAt", DswProjectWithOrigin>(
                  "createdAt",
                  (createdAt) => new Date(createdAt).toLocaleString(),
                  {
                    headerName: "Created At",
                    display: "flex",
                    flex: 1,
                    sortable: true,
                  },
                ),
                DataGridColumn.newColumnWithValueMapper<"updatedAt", DswProjectWithOrigin>(
                  "updatedAt",
                  (updatedAt) => new Date(updatedAt).toLocaleString(),
                  {
                    headerName: "Updated At",
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
              selectRadioAriaLabelFunc={(row) => `Select plan: ${row.name}`}
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
                noRowsLabel: "No projects found",
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
            {selectedPlan ? "Cancel" : "Close"}
          </Button>
          <ValidatingSubmitButton
            onClick={() => {
              void handleImport();
            }}
            validationResult={!selectedPlan?.id ? IsInvalid("No DMP selected.") : IsValid()}
            loading={importing}
          >
            Import
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
