import React, { useState, useEffect } from "react";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../theme";
import Button from "@mui/material/Button";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import MobileStepper from "@mui/material/MobileStepper";
import axios from "@/common/axios";
import FormatChoice, { type ArchiveType } from "./FormatChoice";
import FormatSpecificOptions from "./FormatSpecificOptions";
import ExportRepo from "./ExportRepo";
import ExportFileStore from "./ExportFileStore";
import LoadingFade from "../components/LoadingFade";
import Confirm from "../components/ConfirmProvider";
import { runInAction, action, observable } from "mobx";
import { observer } from "mobx-react-lite";
import {
  makePane,
  appendPane,
  numberOfPanes,
  getIndexOfPane,
  getPaneByKey,
} from "./WizardPanes";
import { type Repo, DEFAULT_REPO_CONFIG } from "./repositories/common";
import { lift3 } from "../util/optional";
import { type Tag } from "./repositories/Tags";
import * as ArrayUtils from "../util/ArrayUtils";
import * as Parsers from "../util/parsers";
import Result from "../util/result";
import { parseEncodedTags } from "../components/Tags/ParseEncodedTagStrings";
import Divider from "@mui/material/Divider";
import AlertContext, { mkAlert } from "../stores/contexts/Alert";
import useViewportDimensions from "../util/useViewportDimensions";
import { type ExportSelection } from "./common";
import { doNotAwait } from "../util/Util";
import { PdfExportDetails } from "./PdfExport";
import { HtmlXmlExportDetails } from "./HtmlXmlExport";
import { WordExportDetails } from "./WordExport";
import AnalyticsContext from "../stores/contexts/Analytics";

type ExportConfig = {
  archiveType: string;
  repository: boolean;
  fileStores: boolean;
  allVersions: boolean;
  repoData: Array<unknown>;
};

const DEFAULT_STATE = {
  open: false,
  loading: false,
  exportSubmitResponse: "",
  exportSelection: {
    type: "selection",
    exportTypes: [] as Array<"MEDIA_FILE" | "NOTEBOOK" | "NORMAL" | "FOLDER">,
    exportNames: [] as Array<string>,
    exportIds: [] as Array<string>,
  } as ExportSelection,
  exportConfig: {
    archiveType: "" as ArchiveType | "",
    repository: false,
    fileStores: false,
    allVersions: false,
    repoData: [] as Array<Repo>,
  },
  repositoryConfig: DEFAULT_REPO_CONFIG,
  nfsConfig: {
    excludedFileExtensions: "",
    includeNfsFiles: false,
    maxFileSizeInMB: 50 as number | string,
  },
  exportDetails: {
    archiveType: "" as ArchiveType | "",
    repository: false,
    fileStores: false,
    allVersions: false,
    repoData: [],
  } as ExportConfig,
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

type ExportDialogArgs = {
  open: boolean;
  onClose?: () => void;
  exportSelection: ExportSelection;
  allowFileStores: boolean;
};

function ExportDialog({
  open,
  onClose,
  exportSelection,
  allowFileStores,
}: ExportDialogArgs): React.ReactNode {
  const { addAlert } = React.useContext(AlertContext);
  const { isViewportSmall } = useViewportDimensions();
  const { trackEvent } = React.useContext(AnalyticsContext);

  const [state, setState] = useState<typeof DEFAULT_STATE>(
    observable(DEFAULT_STATE),
  );

  const [firstPane, setFirstPane] = useState(
    makePane("FormatChoice", "Export"),
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
        makePane("ExportFileStore", "Filestore Links Export Configuration"),
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

    if (
      exportSelection.type === "selection" &&
      exportSelection.exportNames.length > 0
    ) {
      initialExportName = exportSelection.exportNames[0].trimStart();
    } else if (exportSelection.type === "user") {
      initialExportName = exportSelection.username + " - all work";
    } else if (exportSelection.type === "group") {
      initialExportName = exportSelection.groupName + " - all work";
    } else {
      initialExportName = "Export Data";
    }

    pdfConfig.exportName = initialExportName;
    docConfig.exportName = initialExportName;
  };

  useEffect(() => {
    axios
      .get<{ data: { pageSize: string; defaultPDFConfig: string } }>(
        "/export/ajax/defaultPDFConfig",
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
      .post<string>(url, data)
      .then((response) => {
        setState(
          observable({
            ...DEFAULT_STATE,
          }),
        );
        addAlert(
          mkAlert({
            variant:
              response.data.indexOf("Please contact") > -1 ||
              response.data.indexOf("failed for the following reason") > -1
                ? "error"
                : "success",
            message: response.data,
          }),
        );
        trackEvent("user:export:submitted");
      })
      .catch((error) => {
        console.log(error);
      });
  };

  const fetchTags = async (): Promise<Array<Tag>> => {
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

    try {
      const response = await axios.post<unknown>(url, data);

      const result = Parsers.isObject(response)
        .flatMap(Parsers.isNotNull)
        .flatMap(Parsers.getValueWithKey("data"))
        .flatMap(Parsers.isObject)
        .flatMap(Parsers.isNotNull)
        .flatMap(Parsers.getValueWithKey("data"))
        .flatMap(Parsers.isArray)
        .flatMap((dataArray) => Result.all(...dataArray.map(Parsers.isString)))
        .map((dataStrings) =>
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
                tag.version,
              ),
            parseEncodedTags(dataStrings.flatMap((str) => str.split(","))),
          ),
        )
        .mapError(([e]) => {
          console.error(e);
          return e;
        })
        .orElse<Array<Tag>>([]);

      return result;
    } catch (e) {
      console.error(e);
      return [] as Array<Tag>;
    } finally {
      runInAction(() => {
        state.loading = false;
      });
    }
  };

  function exportConfigUpdate(
    itemToUpdate: "archiveType",
    value: ArchiveType,
  ): void;
  function exportConfigUpdate(itemToUpdate: "repository", value: boolean): void;
  function exportConfigUpdate(
    itemToUpdate: "allVersions",
    value: boolean,
  ): void;
  function exportConfigUpdate(itemToUpdate: "fileStores", value: boolean): void;
  function exportConfigUpdate(
    itemToUpdate: "repoData",
    value: ReadonlyArray<Repo>,
  ): void;
  function exportConfigUpdate(
    itemToUpdate:
      | "archiveType"
      | "repository"
      | "allVersions"
      | "fileStores"
      | "repoData",
    value: ArchiveType | boolean | ReadonlyArray<Repo>,
  ) {
    action((newState: typeof DEFAULT_STATE) => {
      if (itemToUpdate === "archiveType") {
        // @ts-expect-error Impossible to typecheck
        newState.exportDetails = { ...exportConfigSwitch(value) };
      } else if (itemToUpdate === "repository") {
        newState.repositoryConfig = DEFAULT_REPO_CONFIG;
      } else if (itemToUpdate === "allVersions") {
        newState.exportDetails.allVersions = value as boolean;
      }
      // @ts-expect-error Impossible to typecheck
      newState.exportConfig[itemToUpdate] = value;

      // some of the switches change which panes are reachable so recreate panes
      if (itemToUpdate === "repository" || itemToUpdate === "fileStores")
        createWizardPanes();
    })(state);
  }

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

  const updateFileStoreFilters = <Key extends keyof typeof state.nfsConfig>(
    name: Key,
    value: (typeof state.nfsConfig)[Key],
  ) => {
    runInAction(() => {
      state.nfsConfig[name] = value;
    });
  };

  function updateExportDetails<Key extends keyof PdfExportDetails>(
    name: Key,
    value: PdfExportDetails[Key],
  ): void;
  function updateExportDetails<Key extends keyof HtmlXmlExportDetails>(
    name: Key,
    value: HtmlXmlExportDetails[Key],
  ): void;
  function updateExportDetails<Key extends keyof WordExportDetails>(
    name: Key,
    value: WordExportDetails[Key],
  ): void;
  function updateExportDetails<Key extends keyof ExportConfig>(
    name: Key,
    value: (typeof state.exportDetails)[Key],
  ) {
    runInAction(() => {
      state.exportDetails[name] = value;
    });
  }

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
              {activePane.key === "FormatSpecificOptions" &&
                state.exportConfig.archiveType !== "" && (
                  <>
                    <FormatSpecificOptions
                      exportType={state.exportConfig.archiveType}
                      // @ts-expect-error Impossible to type check this
                      exportDetails={state.exportDetails}
                      updateExportDetails={updateExportDetails}
                      validator={
                        getPaneByKey(firstPane, "FormatSpecificOptions")
                          .validator
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
                    onClick={doNotAwait(handleNext)}
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
  includeFooterAtEndOnly: true,
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

export default observer(ExportDialog);
