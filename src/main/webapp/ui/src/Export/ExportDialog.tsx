import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Divider from "@mui/material/Divider";
import MobileStepper from "@mui/material/MobileStepper";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { action, observable, runInAction } from "mobx";
import { observer } from "mobx-react-lite";
import React, { Suspense, startTransition, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import { DEFAULT_STATE, type ExportConfig } from "@/Export/constants";
import ExportDialogRaid from "@/Export/ExportDialogRaid";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import type { GroupInfo } from "@/modules/groups/schema";
import { useRaidIntegrationInfoAjaxQuery } from "@/modules/raid/queries";
import { getRaidExportEligibility } from "@/modules/raid/services/export";
import { useCommonGroupsShareListingQuery } from "@/modules/share/queries";
import Confirm from "../components/ConfirmProvider";
import LoadingFade from "../components/LoadingFade";
import { parseEncodedTags } from "../components/Tags/ParseEncodedTagStrings";
import useViewportDimensions from "../hooks/browser/useViewportDimensions";
import AlertContext, { mkAlert } from "../stores/contexts/Alert";
import AnalyticsContext from "../stores/contexts/Analytics";
import materialTheme from "../theme";
import * as ArrayUtils from "../util/ArrayUtils";
import { lift3 } from "../util/optional";
import * as Parsers from "../util/parsers";
import Result from "../util/result";
import type { ExportSelection } from "./common";
import ExportFileStore from "./ExportFileStore";
import ExportRepo from "./ExportRepo";
import FormatChoice, { type ArchiveType } from "./FormatChoice";
import FormatSpecificOptions from "./FormatSpecificOptions";
import type { HtmlXmlExportDetails } from "./HtmlXmlExport";
import type { PdfExportDetails } from "./PdfExport";
import { DEFAULT_REPO_CONFIG, type Repo } from "./repositories/common";
import type { Tag } from "./repositories/Tags";
import { appendPane, getIndexOfPane, getPaneByKey, makePane, numberOfPanes } from "./WizardPanes";
import type { WordExportDetails } from "./WordExport";

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

const isArchiveExport = (archiveType: (typeof DEFAULT_STATE)["exportConfig"]["archiveType"]) => {
  return archiveType === "html" || archiveType === "xml" || archiveType === "eln";
};

function ExportDialog({ open, onClose, exportSelection, allowFileStores }: ExportDialogArgs): React.ReactNode {
  const { t } = useTranslation(["workspace", "common"]);
  const { data: token } = useOauthTokenQuery();
  const { addAlert } = React.useContext(AlertContext);
  const { isViewportSmall } = useViewportDimensions();
  const { trackEvent } = React.useContext(AnalyticsContext);
  const [state, setState] = useState<typeof DEFAULT_STATE>(observable(DEFAULT_STATE));

  const { data: raidData } = useRaidIntegrationInfoAjaxQuery();
  const raidEnabled = raidData.success ? raidData.data.enabled : false;

  const exportIds = exportSelection.exportIds ?? [];
  const shouldFetchCommonGroups = exportSelection.type === "selection" && exportIds.length > 0 && Boolean(token);
  const { data: commonGroups = new Map<number, GroupInfo | null>() } = useCommonGroupsShareListingQuery({
    token,
    params: { sharedItemIds: exportIds as string[] },
    enabled: shouldFetchCommonGroups,
  });

  useEffect(() => {
    if (!shouldFetchCommonGroups) {
      updateProjectGroupId(null);
      return;
    }
    const raidExportStatus = getRaidExportEligibility(commonGroups);

    const projectGroupId = raidExportStatus.isEligible ? raidExportStatus.projectGroup.id : null;

    updateProjectGroupId(projectGroupId);
  }, [commonGroups, shouldFetchCommonGroups]);

  const [firstPane, setFirstPane] = useState(makePane("FormatChoice", t("export.dialog.panes.export")));
  const [activePane, setActivePane] = useState(firstPane);

  const createWizardPanes = () => {
    const newListOfPanes = makePane("FormatChoice", t("export.dialog.panes.export"));
    appendPane(newListOfPanes, makePane("FormatSpecificOptions", t("export.dialog.panes.export")));

    if (state.exportConfig.repository) {
      if (raidEnabled) {
        appendPane(newListOfPanes, makePane("ExportDialogRaid", t("export.dialog.panes.raid")));
      }
      appendPane(newListOfPanes, makePane("ExportRepo", t("export.dialog.panes.setupRepository")));
    }

    if (state.exportConfig.fileStores) {
      appendPane(newListOfPanes, makePane("ExportFileStore", t("export.dialog.panes.filestoreExportConfiguration")));
    }

    setFirstPane(newListOfPanes);
    setActivePane(newListOfPanes);
  };

  useEffect(() => {
    createWizardPanes();
  }, [state.exportConfig.archiveType]);

  useEffect(() => {
    setInitialDocPDF(exportSelection);
    runInAction(() => {
      state.open = open;
      state.exportSelection = exportSelection;
    });
  }, [open, exportSelection]);

  const setInitialDocPDF = (exportSelection: ExportSelection) => {
    // Add initial export name data to the loaded dialog (it is used in PDF and WORD exports)
    // biome-ignore lint/suspicious/noImplicitAnyLet: initial biome migration
    let initialExportName;

    if (exportSelection.type === "selection" && exportSelection.exportNames.length > 0) {
      initialExportName = exportSelection.exportNames[0].trimStart();
    } else if (exportSelection.type === "user") {
      initialExportName = t("export.dialog.defaultExportName.userAllWork", { username: exportSelection.username });
    } else if (exportSelection.type === "group") {
      initialExportName = t("export.dialog.defaultExportName.groupAllWork", { groupName: exportSelection.groupName });
    } else {
      initialExportName = t("export.dialog.defaultExportName.exportData");
    }

    pdfConfig.exportName = initialExportName;
    docConfig.exportName = initialExportName;
  };

  useEffect(() => {
    axios
      .get<{ data: { pageSize: string; defaultPDFConfig: string } }>("/export/ajax/defaultPDFConfig")
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
      startTransition(() => {
        handleClose();
      });
    } else {
      startTransition(() => {
        // biome-ignore lint/style/noNonNullAssertion: initial biome migration
        setActivePane(activePane.next!);
      });
    }
  };

  const handleBack = () => {
    if (activePane.prev) setActivePane(activePane.prev);
  };

  const prepareExportData = () => {
    return {
      exportSelection: { ...state.exportSelection },
      exportConfig: { ...state.exportDetails },
      repositoryConfig: state.repositoryConfig.depositToRepository ? { ...state.repositoryConfig } : {},
      nfsConfig: { ...state.nfsConfig },
      ...(raidEnabled ? { projectGroupId: state.projectGroupId } : {}),
    };
  };

  const submitExport = () => {
    const archiveType = state.exportConfig.archiveType;
    runInAction(() => {
      state.loading = true;
    });

    const url = isArchiveExport(archiveType) ? "/export/ajax/exportArchive" : "/export/ajax/export";
    const data = prepareExportData();
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
      ...prepareExportData(),
      repositoryConfig: {},
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

  function exportConfigUpdate(itemToUpdate: "archiveType", value: ArchiveType): void;
  function exportConfigUpdate(itemToUpdate: "repository", value: boolean): void;
  function exportConfigUpdate(itemToUpdate: "allVersions", value: boolean): void;
  function exportConfigUpdate(itemToUpdate: "fileStores", value: boolean): void;
  function exportConfigUpdate(itemToUpdate: "repoData", value: ReadonlyArray<Repo>): void;
  function exportConfigUpdate(
    itemToUpdate: "archiveType" | "repository" | "allVersions" | "fileStores" | "repoData",
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
      if (itemToUpdate === "repository" || itemToUpdate === "fileStores") createWizardPanes();
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

  function updateExportDetails<Key extends keyof PdfExportDetails>(name: Key, value: PdfExportDetails[Key]): void;
  function updateExportDetails<Key extends keyof HtmlXmlExportDetails>(
    name: Key,
    value: HtmlXmlExportDetails[Key],
  ): void;
  function updateExportDetails<Key extends keyof WordExportDetails>(name: Key, value: WordExportDetails[Key]): void;
  function updateExportDetails<Key extends keyof ExportConfig>(name: Key, value: (typeof state.exportDetails)[Key]) {
    runInAction(() => {
      state.exportDetails[name] = value;
    });
  }

  const updateRepoConfig = (config: typeof state.repositoryConfig) => {
    runInAction(() => {
      state.repositoryConfig = config;
    });
  };

  const updateProjectGroupId = (groupId: number | null) => {
    runInAction(() => {
      state.projectGroupId = groupId;
    });
  };

  return (
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={materialTheme}>
        <Confirm>
          <Dialog open={state.open} fullWidth={true} maxWidth="sm" onClose={handleClose} fullScreen={isViewportSmall}>
            <DialogTitle data-test-id="modalTitle">{activePane.title}</DialogTitle>
            <DialogContent>
              <Suspense fallback={<FontAwesomeIcon icon={faSpinner} spin size="3x" />}>
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
                {activePane.key === "FormatSpecificOptions" && state.exportConfig.archiveType !== "" && (
                  <FormatSpecificOptions
                    exportType={state.exportConfig.archiveType}
                    // @ts-expect-error Impossible to type check this
                    exportDetails={state.exportDetails}
                    updateExportDetails={updateExportDetails}
                    validator={getPaneByKey(firstPane, "FormatSpecificOptions").validator}
                  />
                )}
                {activePane.key === "ExportRepo" && (
                  <ExportRepo
                    state={state}
                    repoList={state.exportConfig.repoData}
                    repoDetails={state.repositoryConfig}
                    updateRepoConfig={updateRepoConfig}
                    validator={getPaneByKey(firstPane, "ExportRepo").validator}
                    fetchTags={fetchTags}
                  />
                )}
                {activePane.key === "ExportDialogRaid" && (
                  <ExportDialogRaid state={state} updateRepoConfig={updateRepoConfig}></ExportDialogRaid>
                )}
                {activePane.key === "ExportFileStore" && (
                  <ExportFileStore
                    exportConfig={state.exportDetails}
                    exportSelection={state.exportSelection}
                    nfsConfig={state.nfsConfig}
                    updateFilters={updateFileStoreFilters}
                    validator={getPaneByKey(firstPane, "ExportFileStore").validator}
                  />
                )}
              </Suspense>
            </DialogContent>
            <LoadingFade loading={state.loading} />
            <DialogActions>
              <Button size="small" onClick={handleClose}>
                {t("common:actions.cancel")}
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
                    {t("common:actions.back")}
                  </Button>
                }
                nextButton={
                  <Button
                    size="small"
                    data-test-id="createGroupNextButton"
                    onClick={() => {
                      void handleNext();
                    }}
                    disabled={state.exportConfig.archiveType === ""}
                  >
                    {activePane.next ? t("common:actions.next") : t("common:actions.export")}
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
