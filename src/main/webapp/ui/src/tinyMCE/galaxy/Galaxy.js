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

  parent.tinymce.activeEditor.on("galaxy-used",  function () {
   setDoUpload(true);
  });

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
          mceAction:  "uploading-complete"
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
  const [doUpload, setDoUpload] = useState(false);
  useEffect(() => {
    void  setAttachedFiles(attachedFileInfo);
  }, []);
  useEffect(() => {
    async function doTheUpload () {
      if (doUpload) {
        await sendDataToGalaxy();
        window.parent.postMessage(
            {
              mceAction:  "no-data-selected"
            },
            "*"
        );
      }
    }
    doTheUpload();
  }, [doUpload]);
  useEffect(() => {
    window.parent.postMessage(
        {
          mceAction: selectedAttachmentIds.length === 0 ? "no-data-selected"
              : "data-selected"
        },
        "*"
    );
    void  setAttachedFiles(attachedFileInfo);
  }, [selectedAttachmentIds]);
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
                      <p><b> The data you have uploaded to Galaxy has links back to RSpace present in its 'annotation' metadata.</b></p>
                    <p><b> Data uploaded to this history is now viewable by clicking on the 'workflow' icon which will appear in your document. Any invocations in Galaxy which use
                      this data will be tracked in RSpace, the data being updated whenever you click on this 'workflow' icon. The badge count on the workflow icon indicates
                      how many Galaxy Invocations are using the uploaded data.</b></p></Typography>
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
                All selected files will be combined into a 'list dataset', which will be available for immediate use. The list dataset will be named after this RSpace document, using the format:
                <p><b>"RSPACE_" + document name + "_" + global ID of document + "_" + name of field data was attached to + "_" + global ID of that field.</b></p>
              </Typography>
              <Typography>
                When you click 'Upload to Galaxy', a new history will be created in Galaxy named after your RSpace document with the same name as the 'list dataset' described above.
                Your chosen data will be uploaded to this new history. You can make this history active in Galaxy by switching to it.
                <p><b>RSpace will store the details of the files you have uploaded and also any use of these files on Galaxy in Invocations will be tracked.</b></p>
              </Typography>
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