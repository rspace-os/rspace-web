import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import Dialog from "@mui/material/Dialog";
import React from "react";
import {DataGridColumn} from "@/util/table";
import {DataGrid} from "@mui/x-data-grid";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import Link from "@mui/material/Link";


function ExternalWorkflowDialog({open, setOpen, galaxySummaryReport}) {
  return (

      <>
        <Dialog open={open}  fullWidth maxWidth="xl" >
          <DialogTitle>Galaxy Data</DialogTitle>
          <DialogContent>
            <Stack spacing={3}>
              <Typography>
                Galaxy Data uploaded and Workflow Invocations using that data
              </Typography>
              <DataGrid
                  getRowHeight={()=>"auto"} //for text wrapping
                  rows={galaxySummaryReport}
                  disableColumnSelector={true}
                  initialState={{
                    sorting: {
                      sortModel: [{ field: 'Created', sort: 'desc' }, { field: 'History', sort: 'asc' }],
                    },
                  }}
                  columns={[
                    DataGridColumn.newColumnWithValueGetter("RSpaceData",
                        (wf) =>  wf.galaxyDataNames,
                        {
                          headerName: "RSpace Data",
                          flex: 1,
                          sortable: false,
                          resizable: true,
                          renderCell: ({row}) => row.galaxyDataNames
                        }),
                    DataGridColumn.newColumnWithValueGetter("History",
                        (wf) => wf.galaxyHistoryName,
                        {
                          headerName: "Galaxy History",
                          flex: 1,
                          resizable: true,
                          renderCell: ({row}) =>
                              <Link
                                  href={row.galaxyBaseUrl + "/histories/view?id="+row.galaxyHistoryId}
                                  target="_blank">{row.galaxyHistoryName}</Link>,
                        }),
                    DataGridColumn.newColumnWithValueGetter("Invocation",
                        (wf) => wf.galaxyInvocationName,
                        {
                          headerName: "Invocation",
                          flex: 1,
                          resizable: true,
                          renderCell: ({row}) => <Link
                              href={row.galaxyBaseUrl + "/workflows/invocations/"+row.galaxyInvocationId}
                              target="_blank">{row.galaxyInvocationName}</Link>,

                        }),
                    DataGridColumn.newColumnWithValueGetter("Status",
                        (wf) => wf.galaxyInvocationStatus,
                        {
                          headerName: "Status",
                          flex: 1,
                          resizable: true,
                          renderCell: ({row}) => row.galaxyInvocationStatus
                        }),
                    DataGridColumn.newColumnWithValueGetter("Created",
                        (wf) => new Date(wf.createdOn),
                        {
                          headerName: "Invocation Created",
                          flex: 1,
                          resizable: true,
                          renderCell: ({row}) => row.createdOn !== null
                              ? new Date(row.createdOn).toLocaleString() : ""
                        }),
                  ]}
                  loading={false}
                  getRowId={(row) => row.galaxyHistoryName + row.createdOn}
                  density="compact"
                  disableColumnFilter
                  hideFooter
                  autoHeight
                  localeText={{
                    noRowsLabel: "No Data is attached to this document",
                  }}
              />
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button
              color="primary"
              variant="contained"
              disableElevation
              onClick={() => {setOpen(false)}}
          >
            Close
          </Button>

          </DialogActions>
        </Dialog>
      </>
  );
}

export default ExternalWorkflowDialog;