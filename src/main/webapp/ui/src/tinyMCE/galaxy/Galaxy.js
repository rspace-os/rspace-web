//@flow
import axios from "@/common/axios";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import {ThemeProvider} from "@mui/material/styles";
import materialTheme from "@/theme";
import React, {useEffect, useState} from "react";
import TitledBox from "@/Inventory/components/TitledBox";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import Button from "@mui/material/Button";
import {DataGridColumn} from "@/util/table";
import {DataGrid} from "@mui/x-data-grid";
import CssBaseline from "@mui/material/CssBaseline";
import CircularProgress from "@mui/material/CircularProgress";
import Grid from "@mui/material/Grid";
import {Modal} from "@mui/material";

function Galaxy({
    fieldId,
  recordId,
    attachedFileInfo,
  galaxy_web_url
}) {
  const sendDataToGalaxy = async () => {
    setHistoryId(null);
    setUploading(true);
    window.parent.postMessage(
        {
          mceAction:  "uploading"
        },
        "*"
    );
    const createdGalaxyHistoryHistory = ((await setUpGalaxyData()).data);
    setUploading(false);
    window.parent.postMessage(
        {
          mceAction:  "uploading_complete"
        },
        "*"
    );
    const historyId = createdGalaxyHistoryHistory.id;
    setHistoryId(historyId);
    setHistoryName(createdGalaxyHistoryHistory.name);
    window.parent.postMessage(
        {
          mceAction:  "enableClose"
        },
        "*"
    );
  }

  const setUpGalaxyData = async () => {
    return axios.post("/apps/galaxy/setUpDataInGalaxyFor",null,{ params: {
        recordId,fieldId,
        selectedAttachmentIds : selectedAttachmentIds.join(",")
      }});
  }
  const [attachedFiles, setAttachedFiles] = useState([]);
  const [selectedAttachmentIds, setSelectedAttachmentIds] = useState([]);
  const [historyId, setHistoryId] = useState(null);
  const [historyName, setHistoryName] = useState(null);
  const [uploading, setUploading] = useState(false);
  useEffect(() => {
    window.parent.postMessage(
        {
          mceAction:  "disableClose"
        },
        "*"
    );
    void  setAttachedFiles(attachedFileInfo);
  }, []);
  return (
      <StyledEngineProvider injectFirst>
        <link rel="stylesheet" href='/styles/simplicity/typo.css' />
        <link rel="stylesheet" href='/styles/simplicity/typoEdit.css' />
        <CssBaseline />
        <ThemeProvider theme={materialTheme}>
            {historyId && (
                <>
                <TitledBox title="View Workflow in Galaxy" border>
                  <Stack spacing={2} alignItems="flex-start">
                    <Typography variant="body1">
                    Your new history can be viewed here:{" "}
                    <a
                        href={`${galaxy_web_url}/histories/view?id=${historyId}`}
                        target="galaxyWithHistory"
                        rel="noreferrer noopener"
                    >
                      {historyName}
                    </a>{" "}(opens in new tab){" "}</Typography><Typography variant="body1">
                      <b>Click on 'switch to this  history', </b></Typography>
                  </Stack>
                </TitledBox>
                </>
            )}
            {!historyId && ( <>
          <TitledBox title="Choose Data" border>
            <Stack spacing={2} alignItems="flex-start">
              <Typography>
               Choose attached files to be uploaded to Galaxy.
              </Typography>
              <Typography>
                All selected files will be combined into a 'list dataset', which will be available for immediate use. The list dataset will be named after this RSpace document.
              </Typography>
              <Typography>
                When you click 'Upload to Galaxy', a new history will be created in Galaxy named  after your RSpace document.
                Your chosen data will be uploaded to this new history. You can make this history active in Galaxy by switching to it.
              </Typography>
              <Button
                  variant="contained"
                  color="primary"
                  disableElevation
                  onClick={() => sendDataToGalaxy()}
                  disabled={selectedAttachmentIds.length == 0 || uploading}
              >
                Upload To Galaxy
              </Button>
              <Modal
                  open={uploading}
                  aria-labelledby="modal-modal-title"
                  aria-describedby="modal-modal-description"
              >
                <Grid
                    container
                    spacing={0}
                    direction="column"
                    alignItems="center"
                    justifyContent="center"
                    sx={{ minHeight: '100vh' }}
                >
                  <Grid item xs={3}>
                    {uploading && <CircularProgress variant="indeterminate" value={1} size="8rem" />}
                  </Grid>
                </Grid>
              </Modal>
            </Stack>
          </TitledBox>
          <DataGrid
              onRowSelectionModelChange={(
                  newRowSelectionModel,
                  _details
              ) => {
                setSelectedAttachmentIds(newRowSelectionModel);
              }}
              getRowHeight={()=>"auto"}
              rows={attachedFiles}
              disableColumnSelector={true}
              columns={[
                DataGridColumn.newColumnWithFieldName("File Name",
                    // (wf) =>  wf.name,
                    {
                      maxWidth: 370,
                      headerName: "File Name",
                      flex: 1,
                      sortable: false,
                      resizable: true,
                      renderCell: ({row}) => <div
                          dangerouslySetInnerHTML={{
                            __html: row.html
                          }}
                      />,
                    }),
                DataGridColumn.newColumnWithValueGetter("Description",
                    (af) => af.description,
                    {
                      headerName: "Description",
                      flex: 1,
                      resizable: true,
                      sortable: false,
                      // maxWidth: 90
                      // renderCell: ({ row }) => row.description,
                    }),
              ]}
              loading={false}
              checkboxSelection
              getRowId={(row) => row.id}
              initialState={{
                columns: {},
              }}
              density="compact"
              disableColumnFilter
              hideFooter
              autoHeight
              localeText={{
                noRowsLabel: "No Data is attached to this document",
              }}
          /></>)}
        </ThemeProvider>
      </StyledEngineProvider>

  );

}

export default Galaxy;