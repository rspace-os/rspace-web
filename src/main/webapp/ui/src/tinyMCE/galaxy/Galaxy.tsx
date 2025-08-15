import axios from "@/common/axios";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import {ThemeProvider} from "@mui/material/styles";
import materialTheme from "@/theme";
import React, {useEffect, useState} from "react";
import TitledBox from "@/Inventory/components/TitledBox";
import Stack from "@mui/material/Stack";
import {DataGridColumn} from "@/util/table";
import {DataGrid, GridRowSelectionModel} from "@mui/x-data-grid";
import CssBaseline from "@mui/material/CssBaseline";
import CircularProgress from "@mui/material/CircularProgress";
import Grid from "@mui/material/Grid";
import {FormControlLabel, Modal} from "@mui/material";
import DOMPurify from "dompurify";
import {ErrorReason} from "@/eln/eln-external-workflows/Enums";
import ErrorView from "@/eln/eln-external-workflows/ErrorView";
import {WorkFlowIcon} from "@/eln/eln-external-workflows/ExternalWorkflowInvocations";
import RadioGroup from "@mui/material/RadioGroup";
import Radio from "@mui/material/Radio";
import useLocalStorage from "../../hooks/browser/useLocalStorage";

export type AttachedRecords = {
  id: string;
  html: HTMLElement;
};
export type GalaxyArgs = {
  fieldId: string | undefined;
  recordId: string;
  attachedFileInfo: AttachedRecords [];
};
export type RSpaceErrorResponse = {
  data: { exceptionMessage?: string; errorId?: string };
};
export type RSpaceError = { message: string; response: RSpaceErrorResponse };

function Galaxy({ fieldId, recordId, attachedFileInfo }: GalaxyArgs) {
  parent.tinymce.activeEditor?.on("galaxy-used", function () {
    setDoUpload(true);
  });

  const sendDataToGalaxy = async () => {
    setHistoryId(null);
    setUploading(true);
    window.parent.postMessage(
        {
        mceAction: "uploading",
        },
      "*",
    );
    try {
      const createdGalaxyHistoryHistory = (await setUpGalaxyData()).data;
      handleUploadComplete();
      const historyId = createdGalaxyHistoryHistory.id;
      setHistoryId(historyId);
      setHistoryName(createdGalaxyHistoryHistory.name);
    } catch (error) {
      handleRequestError(error as RSpaceError);
      handleUploadComplete();
    }

    function handleUploadComplete() {
      setUploading(false);
      window.parent.postMessage(
          {
          mceAction: "uploading-complete",
          },
        "*",
      );
      window.parent.postMessage(
          {
          mceAction: "enableClose",
          },
        "*",
      );
    }
  };
  const defaultServerAlias = "galaxy eu server";
  /**
   * Set default to EU as this is the domain of most RSpace users?
   **/
  const [targetAlias, setTargetAlias] = useLocalStorage(
      "galaxyServerChoice",
    defaultServerAlias,
  );
  type GalaxyServer = { GALAXY_ALIAS: string; GALAXY_URL: string };
  const [servers, setServers] = React.useState<Array<GalaxyServer>>([]);
  const [attachedFiles, setAttachedFiles] = useState<Array<AttachedRecords>>(
    [],
  );
  const [selectedAttachmentIds, setSelectedAttachmentIds] =
      useState<GridRowSelectionModel>([]);
  const [historyId, setHistoryId] = useState(null);
  const [historyName, setHistoryName] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [doUpload, setDoUpload] = useState(false);
  const [errorReason, setErrorReason] = useState<
    (typeof ErrorReason)[keyof typeof ErrorReason]
  >(ErrorReason.None);
  const [errorMessage, setErrorMessage] = useState("");
  useEffect(() => {
    void setAttachedFiles(attachedFileInfo);
  }, []);
  useEffect(() => {
    async function getGalaxyIntegrationInfo() {
      try {
        const galaxyIntegrationInfo = (
          await axios.get("/integration/integrationInfo", {
          params: new URLSearchParams({name: "GALAXY"}),
          responseType: "json",
          })
        ).data;
        const authenticatedServers = Object.entries(
          galaxyIntegrationInfo.data.options,
        )
          .filter(([k]) => k !== "GALAXY_CONFIGURED_SERVERS")
          .flatMap((auth) => auth[1] as GalaxyServer);
        setServers(authenticatedServers);
        if (!authenticatedServers || authenticatedServers.length === 0) {
          throw new Error("You must configure at least one Galaxy server to use this feature. Please go to the Galaxy integration settings to do this. " +
              " If you have configured a Galaxy server, please check with your Sysadmin that your server is listed in RSpace Properties under the key 'galaxy.configured.servers' as an 'alias' and 'url'.");
        }
        //could happen if properties for galaxy are changed and dont match any value in local storage or the default value given for localstorage of 'eu server'
        if (!getGalaxyUrl()) {
          setTargetAlias(authenticatedServers[0].GALAXY_ALIAS);
        }
      } catch (error) {
        setErrorMessage((error as RSpaceError).message);
        console.error("Failed to fetch servers", error);
      }
    }

    getGalaxyIntegrationInfo();
  }, []);
  const handleDataTypeChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const datatype = e.target.value;
    setTargetAlias(datatype);
  };

  const getGalaxyUrl = (): string => {
    const targetServer = servers.find(
      (server) => targetAlias === server.GALAXY_ALIAS,
    );
    return targetServer ? targetServer.GALAXY_URL : "";
  };

  const setUpGalaxyData = async () => {
    return axios.post("/apps/galaxy/setUpDataInGalaxyFor", null, {
      params: {
        targetAlias,
        recordId,
        fieldId,
        selectedAttachmentIds: selectedAttachmentIds.join(","),
      },
    });
  };
  /**
   * If tinymce event calls upload code directly, blocking modal does not wait for full upload before it closes.
   * Having tinymce event set a react state which then triggers the upload code resolves this issue.
   */
  useEffect(() => {
    async function doTheUpload() {
      if (doUpload) {
        await sendDataToGalaxy();
        window.parent.postMessage(
            {
            mceAction: "no-data-selected",
            },
          "*",
        );
      }
    }

    doTheUpload();
  }, [doUpload]);
  useEffect(() => {
    window.parent.postMessage(
        {
          mceAction: selectedAttachmentIds.length === 0 || servers.length === 0 ? "no-data-selected"
              : "data-selected"
        },
        "*"
    );
    void setAttachedFiles(attachedFileInfo);
  }, [selectedAttachmentIds]);

  function handleRequestError(error: RSpaceError) {
    // error.message is generated by axios and contains user unfriendly response codes, therefore only use as a last resort
    let responseError = error.response.data.exceptionMessage
      ? error.response.data.exceptionMessage
      : error.message;
    responseError += error.response.data.errorId
      ? ` (errorId: ${error.response.data.errorId})`
      : "";
    setErrorMessage(responseError);
    if (error.message.slice(error.message.length - 3) === "408") {
      setErrorReason(ErrorReason.Timeout);
    } else if (error.message.slice(error.message.length - 3) === "404") {
      setErrorReason(ErrorReason.NotFound);
    } else if (error.message.slice(error.message.length - 3) === "403") {
      setErrorReason(ErrorReason.Unauthorized);
    } else if (error.message.slice(error.message.length - 3) === "400") {
      setErrorReason(ErrorReason.BadRequest);
    } else {
      setErrorReason(ErrorReason.UNKNOWN);
    }
  }

  return (
      <StyledEngineProvider injectFirst>
      <link rel="stylesheet" href="/styles/simplicity/typo.css" />
      <link rel="stylesheet" href="/styles/simplicity/typoEdit.css" />
        <CssBaseline/>
        <ThemeProvider theme={materialTheme}>
          {historyId && (
              <>
                <TitledBox title="View Workflow in Galaxy" border>
                  <Stack spacing={2} alignItems="flex-start">
                <p>Your new history can be viewed here: </p>
                <p>
                  <a
                        href={`${getGalaxyUrl()}/histories/view?id=${historyId}`}
                        target="galaxyWithHistory"
                        rel="noreferrer noopener"
                    >
                      {historyName}
                  </a>{" "}
                  (Opens in new tab.) (You must be logged into Galaxy or you
                  will see 'Unnamed History'){" "}
                </p>
                    <p>
                  <b>
                    {" "}
                    The data you have uploaded to Galaxy has links back to
                    RSpace present in its 'annotation' metadata.
                      </b>
                    </p>
                    <p>
                  <b>
                    {" "}
                    Data uploaded to this history is now viewable by clicking on
                    the 'workflow' icon which will appear in your document. Any
                    invocations in Galaxy which use this data will be tracked in
                    RSpace, the data being updated whenever you click on this
                    'workflow' icon. The badge count on the workflow icon
                    indicates how many Galaxy Invocations are using the uploaded
                    data.
                      </b>
                    </p>
                  </Stack>
                </TitledBox>
              </>
          )}
        {!historyId && (
          <>
            <TitledBox title="Choose Data" border>
              <Stack spacing={2} alignItems="flex-start">
                {servers && (
                    <>
                      <label htmlFor="serverChoice">Choose a Galaxy Server</label>
                      <RadioGroup
                          id="serverChoice"
                          row
                          aria-label="Choose Galaxy Server"
                          name="Choose Galaxy Server"
                          defaultValue={targetAlias}
                          onChange={handleDataTypeChange}
                      >
                        {servers.map((server) => (
                            <FormControlLabel
                                key={server.GALAXY_ALIAS}
                                value={server.GALAXY_ALIAS}
                                control={<Radio color="primary"/>}
                                label={server.GALAXY_ALIAS}
                                checked={server.GALAXY_ALIAS === targetAlias}
                            />
                        ))}
                      </RadioGroup>
                    </>
                )}
                <p>Choose attached files to be uploaded to Galaxy.</p>
                All selected files will be combined into a 'list dataset', which will be available
                for immediate use. The list dataset will be named after this RSpace document, using
                the format:
                <p><b>"RSPACE_" + document name + "_" + global ID of document + "_" + name of field
                  data was attached to + "_" + global ID of that field.</b></p>
                When you click 'Upload to Galaxy', a new history will be created in Galaxy named
                after your RSpace document with the same name as the 'list dataset' described above.
                Your chosen data will be uploaded to this new history. You can make this history
                active in Galaxy by switching to it.
                <p><b>RSpace will store the details of the files you have uploaded and also any use
                  of these files on Galaxy in Invocations will be tracked.</b></p>
                {errorMessage && (
                    <ErrorView
                        errorReason={errorReason}
                        errorMessage={errorMessage}
                        WorkFlowIcon={WorkFlowIcon}
                    />
                )}
                <Modal
                    open={uploading}
                    aria-label="Please wait, uploading data to galaxy is in progress"
                  title={"Galaxy Upload In Progress"}
                >
                  <Grid
                      container
                      spacing={0}
                      direction="column"
                      alignItems="center"
                      justifyContent="center"
                    sx={{ minHeight: "100vh" }}
                  >
                    <Grid item xs={3}>
                      {uploading && (
                        <CircularProgress
                          variant="indeterminate"
                          value={1}
                          size="8rem"
                        />
                      )}
                    </Grid>
                  </Grid>
                </Modal>
              </Stack>
            </TitledBox>
            <DataGrid
                onRowSelectionModelChange={(
                    newRowSelectionModel: GridRowSelectionModel,
                _details,
                ) => {
                  setSelectedAttachmentIds(newRowSelectionModel);
                }}
                getRowHeight={() => "auto"}
                rows={attachedFiles}
                disableColumnSelector={true}
                columns={[
                DataGridColumn.newColumnWithFieldName<"html", AttachedRecords>(
                  "html",
                      {
                        maxWidth: 370,
                        headerName: "File",
                        flex: 1,
                        sortable: false,
                    renderCell: ({ row }) => (
                      <div
                            dangerouslySetInnerHTML={{
                          __html: DOMPurify.sanitize(row.html),
                            }}
                      />
                    ),
                  },
                ),
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
            />
          </>
        )}
        </ThemeProvider>
      </StyledEngineProvider>
  );
}

export default Galaxy;
