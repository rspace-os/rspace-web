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
import useViewportDimensions from "../../hooks/browser/useViewportDimensions";
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
import { ACCENT_COLOR } from "../../assets/branding/dsw";
import { DataGridWithRadioSelection } from "../../components/DataGridWithRadioSelection";
import {DswConfig} from "@/eln-dmp-integration/DSW/DSWAccentMenuItem";

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

function DMPDialogContent({
  setOpen,
    connection
}: {
  setOpen: (open: boolean) => void;
  connection: DswConfig;
}): React.ReactNode {
  const { addAlert } = useContext(AlertContext);
  const { isViewportSmall } = useViewportDimensions();

  const [DMPHost, setDMPHost] = React.useState<string | null>();
  const [DMPs, setDMPs] = React.useState<Array<DswProjectWithOrigin>>([]);
  const [selectedPlan, setSelectedPlan] = useState<DswProjectWithOrigin | null>();

  const [fetching, setFetching] = useState(false);
  const fetchingId = useRef(0);

  const [importing, setImporting] = useState(false);

  React.useEffect(() => {
    // console.log("DDCuE fetching dmpHost I guess");
    // axios
    //   .get<string>("/apps/dmptool/baseUrlHost")
    //   .then((r) => setDMPHost(r.data))
    //   .catch((e) => console.error("Cannot establish DMP host", e));
    // console.log("DDCuE dmpHost: ", DMPHost);
    //const;
  }, []);

  const getDMPs = async () => {
    setFetching(true);
    const thisId = fetchingId.current;

    let allPlans : Array<DswProjectWithOrigin> = [];
    // let remainingToGet = connections?.length;
    // console.log("DDDDCgD Getting projects for this many connections: ", connections?.length);
    console.log("DDDDCgD Getting projects for this connection: ", connection);
    try {
      console.log("DDDDCgD Getting plans for connection alias: ", connection.DSW_ALIAS);

      const r = await axios.get<{
        success: true;
        data: Array<DswProject>;
        error?: { errorMessages: Array<string> };
      }>(`/apps/dsw/plans?serverAlias=${connection.DSW_ALIAS}`);
      console.log("DDDDCgD plans: ", (r.data.data ? r.data.data : null)); //.data.data.projects);

      console.log("DDDDCgD r: ", r);
      console.log("DDDDCgD success? ", r.data.success);
      if (r.data.success) {

        Object.entries(r.data.data).map(([id, project]) => {
          console.log("DDDDCgD x: ", id, " , y: ", project);
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
            visibility: project.visibility
          }; //y as DswProjectWithOrigin;
          console.log("DDDDCgD projectWithAlias: ", projectWithAlias);
          allPlans.push(projectWithAlias);
        })

        // console.log("DDDDCgD allPlans: ", allPlans);
        // console.log("DDDDCgD remaining to get was: ", remainingToGet, " , and is it? ", (remainingToGet? true : false));
        // remainingToGet = remainingToGet? remainingToGet - 1 : remainingToGet;
        // console.log("DDDDCgD remaining to get now: ", remainingToGet);
        // if (0 == remainingToGet) {
        console.log("DDDDCgD allPlans FINALLY: ", allPlans);
        setDMPs(allPlans);

      } else {
        setFetching(false);
        let errorMsg = r.data && r.data.error && r.data.error.errorMessages ?
            //Object.entries(r.data.error.errorMessages)[0] : null;
            r.data.error.errorMessages[0] : null;
        console.log("DDDDCgD Error message: ", errorMsg);
        addAlert(
          mkAlert({
            title: "Unable to load projects.",
            message: (
              <>
                {errorMsg}
                <br/>
                For more information{" "}
                <a href={docLinks.dmptoolImportingDmps} rel="noreferrer">
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
      console.log("DDDDCgD Fetching false");
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
      setImporting(true);
      //const selectedPlanId = Number(selectedPlan?.id);
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
                      message:
                        r.data.error?.errorMessages[0] ||
                        "Could not import DMP",
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

  console.log("DDC DMPHost: ", DMPHost);
  console.log("DDC DMPs: ", DMPs);

  return (
    <>
      <AppBar
        variant="dialog"
        currentPage="DSW / FAIR Wizard"
        accessibilityTips={{
          supportsHighContrastMode: true,
        }}
        helpPage={{
          docLink: docLinks.dmptool,
          title: "DSW / FAIR Wizard help",
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
              Importing a project from <strong>{connection.DSW_ALIAS}</strong> will make
              it available to view and reference as a DMP within RSpace.
            </Typography>
            <Typography variant="body2">
              See <Link href="https://researchers.dsw.elixir-europe.org">researchers.dsw.elixir-europe.org</Link> and our{" "}
              <Link href={docLinks.dmptool}>DSW / FAIR Wizard integration docs</Link> for
              more.
            </Typography>
          </Grid>
          <Grid item sx={{ overflowY: "auto" }} flexGrow={1}>
            <DataGridWithRadioSelection
              columns={[
                DataGridColumn.newColumnWithFieldName<"name", DswProjectWithOrigin>("name", {
                  headerName: "Name",
                  flex: 1,
                  sortable: false,
                }),
                // DataGridColumn.newColumnWithFieldName<"serverAlias", DswProjectWithOrigin>("serverAlias", {
                //   headerName: "Source Server",
                //   flex: 1,
                //   sortable: false,
                // }),
                DataGridColumn.newColumnWithFieldName<"description", DswProjectWithOrigin>(
                  "description",
                  {
                    renderCell: (params: { row: DswProjectWithOrigin }) => {
                      const sanitized = DOMPurify.sanitize(
                        params.row.description,
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
                  },
                ),
                DataGridColumn.newColumnWithValueMapper<"createdAt", DswProjectWithOrigin>(
                  "createdAt",
                  (createdAt) => new Date(createdAt).toLocaleString(),
                  {
                    headerName: "Created At",
                    flex: 1,
                    sortable: false,
                  },
                ),
                DataGridColumn.newColumnWithValueMapper<"updatedAt", DswProjectWithOrigin>(
                  "updatedAt",
                  (updatedAt) => new Date(updatedAt).toLocaleString(),
                  {
                    headerName: "Updated At",
                    flex: 1,
                    sortable: false,
                  },
                ),
              ]}
              rows={fetching ? [] : DMPs}
              selectedRowId={selectedPlan?.id}
              onSelectionChange={(newSelectionId: GridRowId) => {
                console.log("DDC newSelectionId: ", newSelectionId);
                console.log("DDC Which gives plan: ", DMPs.find((d) => d.id === newSelectionId));
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
                  console.log("DDC keydown id: ", id);
                  console.log("DDC Which gives plan: ", DMPs.find((d) => d.id === id));
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
function DMPDialog({ open, setOpen, connection }: DMPDialogArgs): React.ReactNode {
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
            <DMPDialogContent setOpen={setOpen} connection={connection} />
          </CustomDialog>
        </DialogBoundary>
      </Portal>
    </ThemeProvider>
  );
}

export default observer(DMPDialog);
