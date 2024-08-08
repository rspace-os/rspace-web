//@flow

import React, { type Node, useState, useEffect } from "react";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../theme";
import Button from "@mui/material/Button";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import MobileStepper from "@mui/material/MobileStepper";
import axios from "axios";
import FormatChoice, { type ArchiveType } from "./FormatChoice";
import FormatSpecificOptions from "./FormatSpecificOptions";
import ExportRepo from "./ExportRepo";
import ExportFileStore from "./ExportFileStore";
import LoadingFade from "../components/LoadingFade";
import Confirm from "../components/ConfirmContextDialog";
import { runInAction, action, observable } from "mobx";
import { observer } from "mobx-react-lite";
import {
  makePane,
  appendPane,
  numberOfPanes,
  getIndexOfPane,
  getPaneByKey,
} from "./WizardPanes";
import { type Person, type Repo } from "./repositories/common";
import { lift3 } from "../util/optional";
import { type Tag } from "./repositories/Tags";
import * as ArrayUtils from "../util/ArrayUtils";
import * as Parsers from "../util/parsers";
import Result from "../util/result";
import { parseEncodedTags } from "../components/Tags/ParseEncodedTagStrings";
import Divider from "@mui/material/Divider";
import AlertContext, { mkAlert } from "../stores/contexts/Alert";
import useViewportDimensions from "../util/useViewportDimensions";

const DEFAULT_REPO_CONFIG = {
  repoChoice: 0,
  meta: {
    title: "",
    description: "",
    subject: "",
    licenseName: "",
    authors: ([]: Array<Person>),
    contacts: ([]: Array<Person>),
    publish: "false",
    otherProperties: {},
  },
  depositToRepository: false,
};

export type RepoDetails = typeof DEFAULT_REPO_CONFIG;

type ExportConfig = {|
  archiveType: string,
  repository: boolean,
  fileStores: boolean,
  allVersions: boolean,
  repoData: Array<mixed>,
|};

const DEFAULT_STATE = {
  open: false,
  loading: false,
  exportSubmitResponse: "",
  exportSelection: ({
    type: "selection",
    exportTypes: ([]: Array<"MEDIA_FILE" | "NOTEBOOK" | "NORMAL" | "FOLDER">),
    exportNames: ([]: Array<string>),
    exportIds: ([]: Array<string>),
  }: ExportSelection),
  exportConfig: {
    archiveType: "",
    repository: false,
    fileStores: false,
    allVersions: false,
    repoData: ([]: Array<Repo>),
  },
  repositoryConfig: DEFAULT_REPO_CONFIG,
  nfsConfig: {
    excludedFileExtensions: "",
    includeNfsFiles: false,
    maxFileSizeInMB: 50,
  },
  exportDetails: ({
    archiveType: "",
    repository: false,
    fileStores: false,
    allVersions: false,
    repoData: [],
  }: ExportConfig),
};

/*
 * This react component implements the entire export flow for ELN documents.
 * The dialog is a multi-step wizard that walks the user through the process
 * of exporting their selected documents, dynamically adding steps to request
 * additional metadata as required. In addition to supporting the bundling of
 * the selected documents into one of various different file formats that is
 * then placed in the Gallery, the export dialog also supports exporting to
 * repositories that we integrate with. It is therefore the basis for a lot of
 * the value that RSpace delivers for our users as it is one of the major
 * outflows of their data from our system.
 */

export type ExportSelection =
  | {|
      type: "selection",
      exportTypes: Array<"MEDIA_FILE" | "NOTEBOOK" | "NORMAL" | "FOLDER">,
      exportNames: Array<string>,
      exportIds: Array<string>,
    |}
  | {|
      type: "group",
      groupId: string,
      groupName: string,
      exportIds: Array<mixed>,
    |}
  | {|
      type: "user",
      username: string,
      exportIds: Array<mixed>,
    |};

type ExportDialogArgs = {|
  open: boolean,
  onClose?: () => void,
  exportSelection: ExportSelection,
  allowFileStores: boolean,
|};

function ExportDialog({
  open,
  onClose,
  exportSelection,
  allowFileStores,
}: ExportDialogArgs): Node {
  const { addAlert } = React.useContext(AlertContext);
  const { isViewportSmall } = useViewportDimensions();

  const [state, setState] = useState<typeof DEFAULT_STATE>(
    observable(DEFAULT_STATE)
  );

  const [firstPane, setFirstPane] = useState(
    makePane("FormatChoice", "Export")
  );
  const [activePane, setActivePane] = useState(firstPane);

  const createWizardPanes = () => {
    const newListOfPanes = makePane("FormatChoice", "Export");
    appendPane(newListOfPanes, makePane("FormatSpecificOptions", "Export"));

    if (state.exportConfig.repository)
      appendPane(newListOfPanes, makePane("ExportRepo", "Setup Repository"));

    if (state.exportConfig.fileStores)
      appendPane(
        newListOfPanes,
        makePane("ExportFileStore", "Filestore Links Export Configuration")
      );

    setFirstPane(newListOfPanes);
    setActivePane(newListOfPanes);
  };

  useEffect(() => {
    createWizardPanes();
  }, []);

  useEffect(() => {
    setInitialDocPDF(exportSelection);
    runInAction(() => {
      state.open = open;
      state.exportSelection = exportSelection;
    });
  }, [open, exportSelection]);

  const setInitialDocPDF = (exportSelection: ExportSelection) => {
    // Add initial export name data to the loaded dialog (it is used in PDF and WORD exports)
    let initialExportName;

    if (exportSelection.exportNames && exportSelection.exportNames.length > 0) {
      initialExportName = exportSelection.exportNames[0].trimStart();
    } else if (exportSelection.username) {
      initialExportName = exportSelection.username + " - all work";
    } else if (exportSelection.groupName) {
      initialExportName = exportSelection.groupName + " - all work";
    } else {
      initialExportName = "Export Data";
    }

    pdfConfig.exportName = initialExportName;
    docConfig.exportName = initialExportName;
  };

  useEffect(() => {
    axios
      .get<{| data: {| pageSize: string, defaultPDFConfig: string |} |}>(
        "/export/ajax/defaultPDFConfig"
      )
      .then((response) => {
        const format = response.data.data.pageSize;

        pdfConfig.pageSize = format;
        pdfConfig.defaultPageSize = format;
        docConfig.pageSize = format;
        docConfig.defaultPageSize = format;
      })
      .catch((error) => {
        console.log(error);
      });
  }, []);

  const handleClose = () => {
    setState(observable(DEFAULT_STATE));
    setActivePane(firstPane);
    onClose?.();
  };

  const handleNext = async () => {
    if (!(await activePane.validator.isValid())) return;

    if (!activePane.next) {
      submitExport();
      handleClose();
    } else {
      setActivePane(activePane.next);
    }
  };

  const handleBack = () => {
    if (activePane.prev) setActivePane(activePane.prev);
  };

  const submitExport = () => {
    const archiveType = state.exportConfig.archiveType;
    runInAction(() => {
      state.loading = true;
    });

    const url =
      archiveType === "html" || archiveType === "xml" || archiveType === "eln"
        ? "/export/ajax/exportArchive"
        : "/export/ajax/export";
    const data = {
      exportSelection: { ...state.exportSelection },
      exportConfig: { ...state.exportDetails },
      repositoryConfig: state.repositoryConfig.depositToRepository
        ? { ...state.repositoryConfig }
        : {},
      nfsConfig: { ...state.nfsConfig },
    };
    axios
      .post<typeof data, string>(url, data)
      .then((response) => {
        setState(
          observable({
            ...DEFAULT_STATE,
          })
        );
        addAlert(
          mkAlert({
            variant:
              response.data.indexOf("Please contact") > -1
                ? "error"
                : "success",
            message: response.data,
          })
        );
      })
      .catch((error) => {
        console.log(error);
      });
  };

  const fetchTags = (): Promise<Array<Tag>> => {
    runInAction(() => {
      state.loading = true;
    });

    const archiveType = state.exportConfig.archiveType;
    const url =
      archiveType === "html" || archiveType === "xml"
        ? "/export/ajax/exportRecordTagsArchive"
        : "/export/ajax/exportRecordTagsPdfsAndDocs";
    const data = {
      exportSelection: { ...state.exportSelection },
      exportConfig: { ...state.exportDetails },
      repositoryConfig: {},
      nfsConfig: { ...state.nfsConfig },
    };
    return axios
      .post<typeof data, mixed>(url, data)
      .then((response) =>
        Parsers.isObject(response)
          .flatMap(Parsers.isNotNull)
          .flatMap(Parsers.getValueWithKey("data"))
          .flatMap(Parsers.isArray)
          .flatMap((data) => Result.all(...data.map(Parsers.isString)))
          .map((data) =>
            // parse the tag strings and only keep those with metadata
            ArrayUtils.mapOptional(
              (tag) =>
                lift3(
                  (vocabulary, uri, version) => ({
                    value: tag.value,
                    vocabulary,
                    uri,
                    version,
                  }),
                  tag.vocabulary,
                  tag.uri,
                  tag.version
                ),
              parseEncodedTags(data.flatMap((str) => str.split(",")))
            )
          )
          .orElse<Array<Tag>>([])
      )
      .catch(() => {
        return ([]: Array<Tag>);
      })
      .finally(() => {
        runInAction(() => {
          state.loading = false;
        });
      });
  };

  // this function is just too complex to adequately type
  const exportConfigUpdate = (itemToUpdate: any, value: any) => {
    action<any>((state) => {
      if (itemToUpdate === "archiveType") {
        state.exportDetails = { ...exportConfigSwitch(value) };
      } else if (itemToUpdate === "repository") {
        state.repositoryConfig = value
          ? DEFAULT_REPO_CONFIG
          : {
              depositToRepository: false,
            };
      } else if (itemToUpdate === "allVersions") {
        state.exportDetails.allVersions = value;
      }
      state.exportConfig[itemToUpdate] = value;

      // some of the switches change which panes are reachable so recreate panes
      if (itemToUpdate === "repository" || itemToUpdate === "fileStores")
        createWizardPanes();
    })(state);
  };

  const exportConfigSwitch = (type: ArchiveType) => {
    switch (type) {
      case "html":
        return htmlConfig;
      case "eln":
        return elnConfig;
      case "xml":
        return xmlConfig;
      case "pdf":
        return pdfConfig;
      case "doc":
        return docConfig;
    }
  };

  const updateFileStoreFilters = <Key: $Keys<typeof state.nfsConfig>>(
    name: Key,
    value: (typeof state.nfsConfig)[Key]
  ) => {
    runInAction(() => {
      // $FlowExpectedError[incompatible-type] Flow doesn't always infer type variables that are object keys properly
      state.nfsConfig[name] = value;
    });
  };

  const updateExportDetails = <Key: $Keys<ExportConfig>>(
    name: Key,
    value: (typeof state.exportDetails)[Key]
  ) => {
    runInAction(() => {
      // $FlowExpectedError[incompatible-type] Flow doesn't always infer type variables that are object keys properly
      state.exportDetails[name] = value;
    });
  };

  const updateRepoConfig = (config: typeof state.repositoryConfig) => {
    runInAction(() => {
      state.repositoryConfig = config;
    });
  };

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <Confirm>
          <Dialog
            open={state.open}
            fullWidth={true}
            maxWidth="sm"
            onClose={handleClose}
            fullScreen={isViewportSmall}
          >
            <DialogTitle data-test-id="modalTitle">
              {activePane.title}
            </DialogTitle>
            <DialogContent>
              {activePane.key === "FormatChoice" && (
                <FormatChoice
                  exportSelection={state.exportSelection}
                  exportConfigUpdate={exportConfigUpdate}
                  archiveType={state.exportConfig.archiveType}
                  allowFileStores={allowFileStores}
                  repoSelected={state.exportConfig.repository}
                  fileStoresSelected={state.exportConfig.fileStores}
                  allVersions={state.exportConfig.allVersions}
                  updateFileStores={updateFileStoreFilters}
                  validator={getPaneByKey(firstPane, "FormatChoice").validator}
                />
              )}
              {activePane.key === "FormatSpecificOptions" && (
                <>
                  {/* $FlowExpectedError[incompatible-type] */}
                  <FormatSpecificOptions
                    exportType={state.exportConfig.archiveType}
                    exportDetails={state.exportDetails}
                    updateExportDetails={updateExportDetails}
                    validator={
                      getPaneByKey(firstPane, "FormatSpecificOptions").validator
                    }
                  />
                </>
              )}
              {activePane.key === "ExportRepo" && (
                <ExportRepo
                  repoList={state.exportConfig.repoData}
                  repoDetails={state.repositoryConfig}
                  updateRepoConfig={updateRepoConfig}
                  validator={getPaneByKey(firstPane, "ExportRepo").validator}
                  fetchTags={fetchTags}
                />
              )}
              {activePane.key === "ExportFileStore" && (
                <ExportFileStore
                  exportConfig={state.exportDetails}
                  exportSelection={state.exportSelection}
                  nfsConfig={state.nfsConfig}
                  updateFilters={updateFileStoreFilters}
                  validator={
                    getPaneByKey(firstPane, "ExportFileStore").validator
                  }
                />
              )}
            </DialogContent>
            <LoadingFade loading={state.loading} />
            <DialogActions>
              <Button size="small" onClick={handleClose}>
                Cancel
              </Button>
              <Divider orientation="vertical" sx={{ height: "2em" }} />
              <MobileStepper
                sx={{
                  m: -1,
                  ml: "0 !important",
                  flexGrow: 1,
                  background: "transparent",
                  pl: 0,
                }}
                steps={numberOfPanes(firstPane)}
                position="static"
                activeStep={getIndexOfPane(firstPane, activePane)}
                backButton={
                  <Button
                    size="small"
                    data-test-id="createGroupBackButton"
                    onClick={handleBack}
                    disabled={!activePane.prev}
                  >
                    Back
                  </Button>
                }
                nextButton={
                  <Button
                    size="small"
                    data-test-id="createGroupNextButton"
                    onClick={() => handleNext()}
                    disabled={state.exportConfig.archiveType === ""}
                  >
                    {activePane.next ? "Next" : "Export"}
                  </Button>
                }
              />
            </DialogActions>
          </Dialog>
        </Confirm>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

const htmlConfig = {
  maxLinkLevel: 1,
  archiveType: "html",
  description: "",
  allVersions: false,
};

const elnConfig = {
  maxLinkLevel: 1,
  archiveType: "eln",
  description: "",
  allVersions: false,
};

const xmlConfig = {
  maxLinkLevel: 1,
  archiveType: "xml",
  description: "",
  allVersions: false,
};

const pdfConfig = {
  exportFormat: "PDF",
  exportName: "",
  provenance: true,
  comments: true,
  annotations: true,
  restartPageNumberPerDoc: true,
  pageSize: "A4",
  defaultPageSize: "A4",
  dateType: "EXP",
  includeFooter: true,
  setPageSizeAsDefault: false,
  includeFieldLastModifiedDate: true,
};

const docConfig = {
  exportFormat: "WORD",
  exportName: "",
  pageSize: "A4",
  defaultPageSize: "A4",
  setPageSizeAsDefault: false,
};

export default (observer(ExportDialog): typeof ExportDialog);
