import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Link from "@mui/material/Link";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { DataGrid } from "@mui/x-data-grid";
import React from "react";
import { useTranslation } from "react-i18next";
import { DataGridColumn } from "@/util/table";
import type { GalaxyDataSummary } from "./GalaxyData";

export type ExternalWorkflowDialogArgs = {
  open: boolean;
  setOpen: (val: boolean) => void;
  galaxySummaryReport: Array<GalaxyDataSummary>;
};

function makeGalleryLinks(row: GalaxyDataSummary) {
  return row.galaxyDataNames.map((dataName) => (
    <React.Fragment key={dataName.id ?? dataName.fileName}>
      <Link href={`/gallery/item/${dataName.id}`} target="_blank" rel="noreferrer">
        {dataName.fileName}
      </Link>{" "}
    </React.Fragment>
  ));
}

function ExternalWorkflowDialog({ open, setOpen, galaxySummaryReport }: ExternalWorkflowDialogArgs) {
  const { t } = useTranslation(["apps", "common"]);
  return (
    <Dialog open={open} fullWidth maxWidth="xl">
      <DialogTitle>{t("externalWorkflows.dialogTitle")}</DialogTitle>
      <DialogContent>
        <Stack spacing={3}>
          <Typography>{t("externalWorkflows.dataUploadedDesc")}</Typography>
          <DataGrid
            getRowHeight={() => "auto"} //for text wrapping
            rows={galaxySummaryReport}
            disableColumnSelector={true}
            initialState={{
              sorting: {
                sortModel: [
                  { field: "Created", sort: "desc" },
                  { field: "History", sort: "asc" },
                ],
              },
            }}
            columns={[
              DataGridColumn.newColumnWithValueGetter(
                "Data File Names",
                (wf: GalaxyDataSummary) => wf.galaxyDataNames,
                {
                  headerName: t("externalWorkflows.columns.dataUploaded"),
                  flex: 1,
                  sortable: false,
                  resizable: true,
                  renderCell: ({ row }) => makeGalleryLinks(row),
                },
              ),
              DataGridColumn.newColumnWithValueGetter("Container", (wf: GalaxyDataSummary) => wf.galaxyHistoryName, {
                headerName: t("externalWorkflows.columns.container"),
                flex: 1,
                resizable: true,
                renderCell: ({ row }) => (
                  <Link
                    href={`${row.galaxyBaseUrl}/histories/view?id=${row.galaxyHistoryId}`}
                    target="_blank"
                    rel="noreferrer"
                  >
                    {`${row.galaxyBaseUrl}: ${row.galaxyHistoryName}`}
                  </Link>
                ),
              }),
              DataGridColumn.newColumnWithValueGetter(
                "Invocation",
                (wf: GalaxyDataSummary) => wf.galaxyInvocationName,
                {
                  headerName: t("externalWorkflows.columns.invocation"),
                  flex: 1,
                  resizable: true,
                  renderCell: ({ row }) => (
                    <Link
                      href={`${row.galaxyBaseUrl}/workflows/invocations/${row.galaxyInvocationId}`}
                      target="_blank"
                      rel="noreferrer"
                    >
                      {row.galaxyInvocationName}
                    </Link>
                  ),
                },
              ),
              DataGridColumn.newColumnWithValueGetter("Status", (wf: GalaxyDataSummary) => wf.galaxyInvocationStatus, {
                headerName: t("externalWorkflows.columns.status"),
                flex: 1,
                resizable: true,
                renderCell: ({ row }) => row.galaxyInvocationStatus,
              }),
              DataGridColumn.newColumnWithValueGetter("Created", (wf: GalaxyDataSummary) => new Date(wf.createdOn), {
                headerName: t("externalWorkflows.columns.created"),
                flex: 1,
                resizable: true,
                renderCell: ({ row }) => (row.createdOn !== null ? new Date(row.createdOn).toLocaleString() : ""),
              }),
            ]}
            loading={false}
            getRowId={(row: GalaxyDataSummary) => row.galaxyHistoryName + row.createdOn}
            density="compact"
            disableColumnFilter
            hideFooter
            autoHeight
            localeText={{
              noRowsLabel: t("externalWorkflows.noRows"),
            }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button
          color="primary"
          variant="contained"
          disableElevation
          onClick={() => {
            setOpen(false);
          }}
        >
          {t("common:actions.close")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

export default ExternalWorkflowDialog;
