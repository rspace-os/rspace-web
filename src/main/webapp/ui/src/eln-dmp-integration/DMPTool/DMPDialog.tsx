import React, { useState, useEffect, useContext, useRef } from "react";
import Button from "@mui/material/Button";
import { Dialog, DialogBoundary } from "../../components/DialogBoundary";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Grid from "@mui/material/Grid";
import { withStyles } from "Styles";
import { observer } from "mobx-react-lite";
import Typography from "@mui/material/Typography";
import axios from "@/common/axios";
import ScopeField, { type Scope } from "./ScopeField";
import useViewportDimensions from "../../util/useViewportDimensions";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import Portal from "@mui/material/Portal";
import createAccentedTheme from "../../accentedTheme";
import { ThemeProvider } from "@mui/material/styles";
import ValidatingSubmitButton, {
  IsInvalid,
  IsValid,
} from "../../components/ValidatingSubmitButton";
import Link from "@mui/material/Link";
import AppBar from "../../components/AppBar";
import docLinks from "../../assets/DocLinks";
import Stack from "@mui/material/Stack";
import { GridRowId } from "@mui/x-data-grid";
import { DataGridColumn } from "../../util/table";
import DOMPurify from "dompurify";
import { mapNullable } from "../../util/Util";
import { ACCENT_COLOR } from "../../assets/branding/dmptool";
import { DataGridWithRadioSelection } from "../../components/DataGridWithRadioSelection";

const CustomDialog = withStyles<
  { fullScreen: boolean } & React.ComponentProps<typeof Dialog>,
  { paper?: string }
>((theme, { fullScreen }) => ({
  paper: {
    overflow: "hidden",
    margin: fullScreen ? 0 : theme.spacing(2.625),
    maxHeight: "unset",
    minHeight: "unset",

    // this is so that the heights of the dialog's content are constrained and scrollbars appear
    // 24px margin above and below, 3px border above and below
    height: fullScreen ? "100%" : "calc(100% - 48px)",
  },
}))(Dialog);

export type Plan = {
  id: number;
  title: string;
  description: string;
  modified: string;
  created: string;
};

function DMPDialogContent({
  setOpen,
}: {
  setOpen: (open: boolean) => void;
}): React.ReactNode {
  const { addAlert } = useContext(AlertContext);
  const { isViewportSmall } = useViewportDimensions();

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
          addAlert(
            mkAlert({
              title: "Fetch failed.",
              message: r.data?.error?.errorMessages[0] ?? "Could not get DMPs",
              variant: "error",
            })
          );
        }
      } else {
        console.info(
          "The response from this request is being discarded because a different listing of plans has been requested whilst this network call was in flight."
        );
      }
    } catch (e) {
      console.error("Could not get DMPs for scope", e);
      if (e instanceof Error)
        addAlert(
          mkAlert({
            title: "Fetch failed.",
            message: `Could not get DMPs: ${e.message}`,
            variant: "error",
          })
        );
      if (thisId === fetchingId.current) {
        setFetching(false);
      }
    } finally {
      setFetching(false);
    }
  };

  useEffect(() => {
    void getDMPs("MINE");
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - getDMPs will not meaningfully change
     */
  }, []);

  const handleImport = async () => {
    try {
      setImporting(true);
      const selectedPlanId = Number(selectedPlan?.id);
      if (selectedPlan) {
        await axios
          .post<{ success: true; error?: { errorMessages: string[] } }>(
            `/apps/dmptool/pdfById/${selectedPlanId}`,
            {}
          )
          .then((r) => {
            addAlert(
              mkAlert(
                r.data.success
                  ? {
                      title: "Success.",
                      message: `DMP ${selectedPlanId} was successfully imported.`,
                      variant: "success",
                    }
                  : {
                      title: "Import failed.",
                      message:
                        r.data.error?.errorMessages[0] ||
                        "Could not import DMP",
                      variant: "error",
                    }
              )
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
        currentPage="DMPTool"
        accessibilityTips={{
          supportsHighContrastMode: true,
        }}
        helpPage={{
          docLink: docLinks.dmptool,
          title: "DMPTool help",
        }}
      />
      <DialogTitle variant="h3">Import a DMP into the Gallery</DialogTitle>
      <DialogContent>
        <Grid
          container
          direction="column"
          spacing={2}
          flexWrap="nowrap"
          /*
           * The height of 100% ensures that the table is scrollable
           * The extra 16px prevents excessive whitespace, more and we get double scrollbars
           */
          height="calc(100% + 16px)"
        >
          <Grid item>
            <Typography variant="body2">
              Importing a DMP{" "}
              {mapNullable(
                (host) => (
                  <>
                    from <strong>{host}</strong>{" "}
                  </>
                ),
                DMPHost
              ) ?? ""}
              will make it available to view and reference within RSpace.
            </Typography>
            <Typography variant="body2">
              See <Link href="https://dmptool.org">dmptool.org</Link> and our{" "}
              <Link href={docLinks.dmptool}>DMPTool integration docs</Link> for
              more.
            </Typography>
          </Grid>
          <Grid item>
            <ScopeField getDMPs={getDMPs} />
          </Grid>
          <Grid item sx={{ overflowY: "auto" }} flexGrow={1}>
            <DataGridWithRadioSelection
              columns={[
                DataGridColumn.newColumnWithFieldName<"title", Plan>("title", {
                  headerName: "Title",
                  flex: 1,
                  sortable: false,
                }),
                DataGridColumn.newColumnWithFieldName<"id", Plan>("id", {
                  headerName: "Id",
                  flex: 1,
                  sortable: false,
                }),
                DataGridColumn.newColumnWithFieldName<"description", Plan>(
                  "description",
                  {
                    renderCell: (params: { row: Plan }) => {
                      const sanitized = DOMPurify.sanitize(
                        params.row.description
                      );
                      return (
                        <span
                          dangerouslySetInnerHTML={{
                            __html: `${sanitized.substring(0, 200)} ${
                              sanitized.length > 200 ? "..." : ""
                            }`,
                          }}
                        ></span>
                      );
                    },
                    headerName: "Description",
                    display: "flex",
                    flex: 1,
                    sortable: false,
                  }
                ),
                DataGridColumn.newColumnWithValueMapper<"created", Plan>(
                  "created",
                  (created) => new Date(created).toLocaleString(),
                  {
                    headerName: "Created At",
                    flex: 1,
                    sortable: false,
                  }
                ),
                DataGridColumn.newColumnWithValueMapper<"modified", Plan>(
                  "modified",
                  (modified) => new Date(modified).toLocaleString(),
                  {
                    headerName: "Modified At",
                    flex: 1,
                    sortable: false,
                  }
                ),
              ]}
              rows={fetching ? [] : DMPs}
              selectedRowId={selectedPlan?.id}
              onSelectionChange={(newSelectionId: GridRowId) => {
                setSelectedPlan(DMPs.find((d) => d.id === newSelectionId));
              }}
              selectRadioAriaLabelFunc={(row) => `Select plan: ${row.title}`}
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
                noRowsLabel: "No DMPs",
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
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions>
        <Grid container direction="row" spacing={1}>
          <Grid item sx={{ ml: "auto" }}>
            <Stack direction="row" spacing={1}>
              <Button onClick={() => setOpen(false)} disabled={importing}>
                {selectedPlan ? "Cancel" : "Close"}
              </Button>
              <ValidatingSubmitButton
                onClick={() => {
                  void handleImport();
                }}
                validationResult={
                  !selectedPlan?.id ? IsInvalid("No DMP selected.") : IsValid()
                }
                loading={importing}
              >
                Import
              </ValidatingSubmitButton>
            </Stack>
          </Grid>
        </Grid>
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
