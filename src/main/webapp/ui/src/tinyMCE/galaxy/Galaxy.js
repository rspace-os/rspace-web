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
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import TextField from "@mui/material/TextField";
import DialogActions from "@mui/material/DialogActions";
import SubmitSpinnerButton from "@/components/SubmitSpinnerButton";
import {doNotAwait, toTitleCase} from "@/util/Util";
import {DataGridColumn} from "@/util/table";
import GlobalId from "@/components/GlobalId";
import LinkableRecordFromGlobalId
  from "@/stores/models/LinkableRecordFromGlobalId";
import {DataGrid, GridRowSelectionModel, GridToolbar} from "@mui/x-data-grid";
import Link from "@mui/material/Link";
import {
  getAllBookingDetails, getAllEquipmentDetails,
  getBookings, getRelevantBookings
} from "@/tinyMCE/clustermarket/ClustermarketClient";
import {
  BOOKING_TYPE,
  BookingAndEquipmentDetails,
  BookingDetails, BookingsList,
  EquipmentDetails,
  EquipmentWithBookingDetails,
  makeBookingAndEquipmentData,
  makeEquipmentWithBookingData
} from "@/tinyMCE/clustermarket/ClustermarketData";
import {BookingType, ErrorReason} from "@/tinyMCE/clustermarket/Enums";
import CssBaseline from "@mui/material/CssBaseline";
import ResultsTable from "@/tinyMCE/clustermarket/ResultsTable";
import CircularProgress from "@mui/material/CircularProgress";

function Galaxy({
    fieldId,
  recordId,
    attachedFileInfo,
  galaxy_web_url
}) {
  const sendDataToGalaxy = async () => {
    setHistoryId(null);
    const createdGalaxyHistoryHistory = ((await setUpGalaxyData()).data);
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
  //doesnt work: 1) Browser will not allow any window.open event that is asynchronous. 2) Doing two window.open in a row synchronously results in only the second one taking effect - so that the current history is not set.
  // const runAfterUserClicksToSeeResults = () =>{
  //   alert('whee');
  //   setTimeout(() => {
  //     window.open("https://usegalaxy.eu/history/set_as_current?id="+historyId, '_blank');
  //   }, "100");
  //
  //   //delay required to allow galaxy time to process the request to set current history. The 100 ms value is arbitrary and may not work on a slow network?
  //   setTimeout(() => {
  //     window.open(galaxy_web_url+"/workflows/run?id="+workflows[selectedWorkflowId[0]].galaxyID, '_blank');
  //   }, "100");
  // }

  const setUpGalaxyData = async () => {
    return axios.post("/apps/galaxy/setUpDataInGalaxyFor",null,{ params: {
        recordId,fieldId,
        selectedAttachmentIds : selectedAttachmentIds.join(","),
        selectedWorkflowId : workflows[selectedWorkflowId[0]].galaxyID
      }});
  }
  //TODO - create a json file to hold the workflows data (or fetch it from Galaxy?)
  const workflows = [{
    id: 0,
    name: 'RNA-seq for Paired-end fastqs (release v1.2) ',
    url: '/workflows/run?id=2d08a73dd8ff99e9',
    galaxyID: '2d08a73dd8ff99e9',
    description: 'This workflow takes as input a list of paired-end fastqs. Adapters and bad quality bases are removed with fastp. Reads are mapped with STAR with ENCODE parameters and genes are counted simultaneously as well as normalized coverage (per million mapped reads) on uniquely mapped reads. The counts are reprocessed to be similar to HTSeq-count output. Alternatively, featureCounts can be used to count the reads/fragments per gene. FPKM are computed with cufflinks and/or with StringTie. The unstranded normalized coverage is computed with bedtools.'
  },
    {
      id: 1,
      name: 'RNA-seq for Single-read fastqs (release v1.2)',
      url: '/workflows/run?id=87fea062a9646a31',
      galaxyID: '87fea062a9646a31',
      description: 'This workflow takes as input a list of single-end fastqs. Adapters and bad quality bases are removed with fastp. Reads are mapped with STAR with ENCODE parameters and genes are counted simultaneously as well as normalized coverage (per million mapped reads) on uniquely mapped reads. The counts are reprocessed to be similar to HTSeq-count output. Alternatively, featureCounts can be used to count the reads/fragments per gene. FPKM are computed with cufflinks and/or with StringTie. The unstranded normalized coverage is computed with bedtools.'
    }];
  const [attachedFiles, setAttachedFiles] = useState([]);
  const [selectedWorkflowId, setSelectedWorkflowId] = useState(null);
  const [selectedAttachmentIds, setSelectedAttachmentIds] = useState([]);
  const [historyId, setHistoryId] = useState(null);
  const [historyName, setHistoryName] = useState(null);

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
                      <b>Click on 'switch to this  history', </b>then your chosen workflow can be run: {" "}
                    <a
                        href={`${galaxy_web_url}/workflows/run?id=${workflows[selectedWorkflowId[0]].galaxyID}`}
                        target="galaxyWithHistory"
                        rel="noreferrer noopener"
                    >{" "}
                      {workflows[selectedWorkflowId[0]].name}
                    </a> {" "} (opens in new tab)</Typography>
                  </Stack>
                </TitledBox>
                </>
            )}
            {!historyId && ( <>
            <TitledBox title="Choose Workflow" border>
            <Stack spacing={2} alignItems="flex-start">
              <Typography>
                You can choose a Galaxy workflow and then upload data attached
                to this document to the Galaxy instance at {galaxy_web_url}
              </Typography>
              <Typography>
                When you click 'Upload to Galaxy', a new history will be created in Galaxy named  after your RSpace document
                Your chosen data will be uploaded to this new history and if appropriate, will be combined into a list dataset,
                also named after your RSpace document.
              </Typography>

              <Button
                  variant="contained"
                  color="primary"
                  disableElevation
                  onClick={() => sendDataToGalaxy()}
              >
                Upload To Galaxy
              </Button>

              <Dialog
              >
                <DialogTitle>Choose workflow</DialogTitle>
                <DialogContent>
                  <Stack spacing={3}>
                    <Typography>
                      We currently support two workflows for processing RNA
                      reads
                    </Typography>
                    <TextField
                        label="TEXTFIELD WHY?"
                        type="text"
                        inputProps={{min: 1, max: 100}}
                    />
                  </Stack>
                </DialogContent>
                {/*<DialogActions>*/}
                {/*  <Button onClick={() => alert('Button2')}>*/}
                {/*    Cancel222222*/}
                {/*  </Button>*/}
                {/*  <SubmitSpinnerButton*/}
                {/*      onClick={doNotAwait(async () => {*/}
                {/*        // setRegisteringInProgress(true);*/}
                {/*        // await bulkRegister({ count: numberOfNewIdentifiers });*/}
                {/*        // void refreshListing();*/}
                {/*        // setRegisteringInProgress(false);*/}
                {/*        // setBulkRegisterDialogOpen(false);*/}
                {/*      })}*/}
                {/*      // disabled={registeringInProgress}*/}
                {/*      // loading={registeringInProgress}*/}
                {/*      label="Register"*/}
                {/*  />*/}
                {/*</DialogActions>*/}
              </Dialog>
            </Stack>
            </TitledBox>
          <DataGrid
              onRowSelectionModelChange={(
                  newRowSelectionModel,
                  _details
              ) => {
                setSelectedWorkflowId(newRowSelectionModel);
              }}
              // getRowHeight={()=>"auto"} //for text wrapping the description, if this is preferred
              rows={workflows}
              disableColumnSelector={true}
              columns={[
                DataGridColumn.newColumnWithFieldName("Workflow",
                    // (wf) =>  wf.name,
                    {
                      maxWidth: 370,
                      headerName: "Workflow",
                      flex: 1,
                      sortable: false,
                      resizable: true,
                      renderCell: ({row}) => <Link
                          href={galaxy_web_url + row.url}
                          target="_blank">{row.name}</Link>,
                    }),
                DataGridColumn.newColumnWithValueGetter("Description",
                    (wf) => wf.description,
                    {
                      headerName: "Description",
                      flex: 1,
                      resizable: true,
                      sortable: false,
                    }),
              ]}
              loading={false}
              checkboxSelection
              disableMultipleRowSelection
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
          />
          <TitledBox title="Choose Data" border>
            <Stack spacing={2} alignItems="flex-start">
              <Typography>
               Choose attached files to be uploaded to Galaxy.
              </Typography>
              <Typography>
                For the RNA-seq for Single-read fastqs workflow, all selected files will be combined into a 'list dataset', which will be available for immediate use.
              </Typography>
              <Typography>
                For the RNA-seq for Paired-end fastqs workflow, selected files will be uploaded to Galaxy, where you must then manually create a 'paired list dataset' before they can be used.
              </Typography>
              <Dialog
              >
                <DialogTitle>Choose workflow</DialogTitle>
                <DialogContent>
                  <Stack spacing={3}>
                    <Typography>
                      We currently support two workflows for processing RNA
                      reads
                    </Typography>
                    <TextField
                        label="TEXTFIELD WHY?"
                        type="text"
                        inputProps={{min: 1, max: 100}}
                    />
                  </Stack>
                </DialogContent>
                {/*<DialogActions>*/}
                {/*  <Button onClick={() => alert('Button2')}>*/}
                {/*    Cancel222222*/}
                {/*  </Button>*/}
                {/*  <SubmitSpinnerButton*/}
                {/*      onClick={doNotAwait(async () => {*/}
                {/*      })}*/}
                {/*      label="Register"*/}
                {/*  />*/}
                {/*</DialogActions>*/}
              </Dialog>
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