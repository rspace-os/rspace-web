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
import { useStore } from "@tanstack/react-form";
import React, { Suspense, startTransition, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import axios, { isAxiosError } from "@/common/axios";
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
import type { ExportSelection, PageSize } from "./common";
import ExportFileStore, { useFileStoreExportConfirmation } from "./ExportFileStore";
import ExportRepo, { repositoryFormValidator } from "./ExportRepo";
import { type ExportConfig, type ExportFormValues, useExportForm, validateWith } from "./exportForm";
import FormatChoice from "./FormatChoice";
import FormatSpecificOptions, { exportDetailsSchema } from "./FormatSpecificOptions";
import { PLACEHOLDER_PEOPLE_AND_SUBJECT, REPOS_WITHOUT_PEOPLE_OR_SUBJECT } from "./repositories/common";
import type { Tag } from "./repositories/Tags";
import { appendPane, getIndexOfPane, makePane, numberOfPanes } from "./WizardPanes";

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
 *
 * Every value the user sets is kept in one TanStack form (see ./exportForm).
 * Each pane is wrapped in a FormGroup over its slice of the values, so that
 * tapping Next validates only the current pane. The export request is built
 * from the form's values when the user taps Export on the last pane.
 */

type ExportDialogArgs = {
  open: boolean;
  onClose?: () => void;
  exportSelection: ExportSelection;
  allowFileStores: boolean;
};

const isArchiveExport = (archiveType: ExportConfig["archiveType"]) => {
  return archiveType === "html" || archiveType === "xml" || archiveType === "eln";
};

/**
 * The deposit described by the repository pane, as the export request expects
 * it, or an empty object when the export is not deposited to a repository.
 */
const repositoryConfig = ({ exportConfig, repositoryForm }: ExportFormValues) => {
  if (!exportConfig.repository) return {};
  const repo = exportConfig.repoData[Number(repositoryForm.repoChoice)];
  const license = repo.license.licenses[Number(repositoryForm.license)].licenseDefinition;
  const config = {
    repoChoice: repositoryForm.repoChoice,
    appName: repo.repoName,
    depositToRepository: true,
    repoCfg: repo.repoCfg,
    selectedDMPs: repositoryForm.linkDMP ? repositoryForm.selectedPlans : [],
    meta: {
      title: repositoryForm.title,
      description: repositoryForm.description,
      subject: repositoryForm.subject,
      licenseName: license.name,
      licenseUrl: license.url,
      authors: repositoryForm.authors,
      contacts: repositoryForm.contacts,
      publish: repositoryForm.publish,
      otherProperties: repositoryForm.otherProperties,
      tags: repositoryForm.tags,
    },
    exportToRaid: repositoryForm.exportToRaid,
  };
  return REPOS_WITHOUT_PEOPLE_OR_SUBJECT.has(repo.repoName)
    ? { ...config, meta: { ...config.meta, ...PLACEHOLDER_PEOPLE_AND_SUBJECT } }
    : config;
};

function ExportWizard({ open, onClose, exportSelection, allowFileStores }: ExportDialogArgs): React.ReactNode {
  const { t } = useTranslation(["workspace", "common"]);
  const { data: token } = useOauthTokenQuery();
  const { addAlert } = React.useContext(AlertContext);
  const { isViewportSmall } = useViewportDimensions();
  const { trackEvent } = React.useContext(AnalyticsContext);
  const form = useExportForm();
  const confirmFileStoreExport = useFileStoreExportConfirmation();
  const [isOpen, setIsOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [defaultPageSize, setDefaultPageSize] = useState<PageSize>("A4");
  const [resetPending, setResetPending] = useState(false);

  const { data: raidData } = useRaidIntegrationInfoAjaxQuery();
  const raidEnabled = raidData.success ? raidData.data.enabled : false;

  const exportIds = exportSelection.exportIds ?? [];
  const shouldFetchCommonGroups = exportSelection.type === "selection" && exportIds.length > 0 && Boolean(token);
  const { data: commonGroups = new Map<number, GroupInfo | null>() } = useCommonGroupsShareListingQuery({
    token,
    params: { sharedItemIds: exportIds as string[] },
    enabled: shouldFetchCommonGroups,
  });
  const raidExportStatus = shouldFetchCommonGroups ? getRaidExportEligibility(commonGroups) : null;
  const projectGroupId = raidExportStatus?.isEligible ? raidExportStatus.projectGroup.id : null;

  const [firstPane, setFirstPane] = useState(() => makePane("FormatChoice", t("export.dialog.panes.export")));
  const [activePane, setActivePane] = useState(firstPane);

  const { archiveType, repository, fileStores, repoData } = useStore(form.store, (state) => state.values.exportConfig);

  /*
   * The format and destination switches change which panes are reachable, so
   * the list of panes is rebuilt whenever they change.
   */
  useEffect(() => {
    const newListOfPanes = makePane("FormatChoice", t("export.dialog.panes.export"));
    appendPane(newListOfPanes, makePane("FormatSpecificOptions", t("export.dialog.panes.export")));

    if (repository) {
      if (raidEnabled) {
        appendPane(newListOfPanes, makePane("ExportDialogRaid", t("export.dialog.panes.raid")));
      }
      appendPane(newListOfPanes, makePane("ExportRepo", t("export.dialog.panes.setupRepository")));
    }

    if (fileStores) {
      appendPane(newListOfPanes, makePane("ExportFileStore", t("export.dialog.panes.filestoreExportConfiguration")));
    }

    setFirstPane(newListOfPanes);
    setActivePane(newListOfPanes);
  }, [archiveType, repository, fileStores]);

  useEffect(() => {
    setIsOpen(open);
    form.setFieldValue("exportSelection", exportSelection);
  }, [open, exportSelection]);

  useEffect(() => {
    axios
      .get<{ data: { pageSize: PageSize; defaultPDFConfig: string } }>("/export/ajax/defaultPDFConfig")
      .then((response) => {
        setDefaultPageSize(response.data.data.pageSize);
      })
      .catch((error) => {
        console.log(error);
      });
  }, []);

  /*
   * Resetting the form re-renders the mounted panes straight away, even within
   * a transition, and a later pane cannot render the default values. The reset
   * therefore waits until the first pane has replaced it.
   */
  useEffect(() => {
    if (!resetPending) return;
    form.reset();
    setResetPending(false);
  }, [resetPending]);

  const closeAndReset = () => {
    setIsOpen(false);
    setLoading(false);
    setActivePane(firstPane);
    setResetPending(true);
  };

  const handleClose = () => {
    closeAndReset();
    onClose?.();
  };

  // Called once the current pane's values are valid.
  const goToNextPane = () => {
    const nextPane = activePane.next;
    if (!nextPane) {
      submitExport(form.state.values);
      startTransition(() => {
        handleClose();
      });
    } else {
      startTransition(() => {
        setActivePane(nextPane);
      });
    }
  };

  const handleBack = () => {
    if (activePane.prev) setActivePane(activePane.prev);
  };

  const exportRequest = (values: ExportFormValues, repositoryConfigOfRequest: object) => ({
    exportSelection: { ...values.exportSelection },
    exportConfig: { ...values.exportDetails },
    repositoryConfig: repositoryConfigOfRequest,
    nfsConfig: { ...values.nfsConfig },
    ...(raidEnabled ? { projectGroupId } : {}),
  });

  const submitExport = (values: ExportFormValues) => {
    setLoading(true);

    const url = isArchiveExport(values.exportConfig.archiveType) ? "/export/ajax/exportArchive" : "/export/ajax/export";
    let data: ReturnType<typeof exportRequest>;
    try {
      data = exportRequest(values, repositoryConfig(values));
    } catch (error) {
      console.error(error);
      setLoading(false);
      addAlert(mkAlert({ variant: "error", message: t("common:apiErrors.unknown") }));
      return;
    }
    axios
      .post<string>(url, data)
      .then((response) => {
        closeAndReset();
        addAlert(
          mkAlert({
            variant: "success",
            message: response.data,
          }),
        );
        trackEvent("user:export:submitted");
      })
      .catch((error) => {
        console.log(error);
        addAlert(
          mkAlert({
            variant: "error",
            message:
              isAxiosError(error) && typeof error.response?.data === "string"
                ? error.response.data
                : t("common:apiErrors.unknown"),
          }),
        );
      });
  };

  const fetchTags = async (): Promise<Array<Tag>> => {
    setLoading(true);

    const values = form.state.values;
    const url =
      values.exportConfig.archiveType === "html" || values.exportConfig.archiveType === "xml"
        ? "/export/ajax/exportRecordTagsArchive"
        : "/export/ajax/exportRecordTagsPdfsAndDocs";
    const data = exportRequest(values, {});

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
        // an unreadable response is a failure too, so that the repository pane tries again on its next visit
        .elseThrow();

      return result;
    } finally {
      // a failed request propagates, so that the repository pane can try again on its next visit
      setLoading(false);
    }
  };

  const renderPane = (pane: React.ReactNode, validateAndContinue: () => Promise<void>) => (
    <>
      <DialogContent>
        <Suspense fallback={<FontAwesomeIcon icon={faSpinner} spin size="3x" />}>{pane}</Suspense>
      </DialogContent>
      <LoadingFade loading={loading} />
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
            <Button size="small" data-test-id="createGroupBackButton" onClick={handleBack} disabled={!activePane.prev}>
              {t("common:actions.back")}
            </Button>
          }
          nextButton={
            <Button
              size="small"
              data-test-id="createGroupNextButton"
              onClick={() => {
                void validateAndContinue();
              }}
              disabled={archiveType === ""}
            >
              {activePane.next ? t("common:actions.next") : t("common:actions.export")}
            </Button>
          }
        />
      </DialogActions>
    </>
  );

  /*
   * Each pane's FormGroup is returned from this one place, so that React keeps
   * the dialog's buttons when the pane changes and only the pane is replaced.
   */
  const renderActivePane = () => {
    switch (activePane.key) {
      case "FormatChoice":
        return (
          <form.FormGroup name="exportConfig" onGroupSubmit={goToNextPane}>
            {(group) =>
              renderPane(
                <FormatChoice form={form} allowFileStores={allowFileStores} defaultPageSize={defaultPageSize} />,
                () => group.handleSubmit(),
              )
            }
          </form.FormGroup>
        );
      case "FormatSpecificOptions":
        return (
          <form.FormGroup
            name="exportDetails"
            validators={{ onSubmit: validateWith(exportDetailsSchema) }}
            onGroupSubmit={goToNextPane}
          >
            {(group) =>
              renderPane(archiveType !== "" && <FormatSpecificOptions form={form} />, () => group.handleSubmit())
            }
          </form.FormGroup>
        );
      case "ExportDialogRaid":
        return (
          <form.FormGroup name="repositoryForm.exportToRaid" onGroupSubmit={goToNextPane}>
            {(group) => renderPane(<ExportDialogRaid form={form} />, () => group.handleSubmit())}
          </form.FormGroup>
        );
      case "ExportRepo":
        return (
          <form.FormGroup
            name="repositoryForm"
            validators={{ onSubmit: repositoryFormValidator(repoData) }}
            onGroupSubmit={goToNextPane}
          >
            {(group) =>
              renderPane(
                <ExportRepo
                  form={form}
                  // the group's errors outlive it, so only those from a Next tapped on this visit are shown
                  errors={group.state.meta.submissionAttempts > 0 ? group.state.meta.errorMap.onSubmit : undefined}
                  fetchTags={fetchTags}
                />,
                () => group.handleSubmit(),
              )
            }
          </form.FormGroup>
        );
      case "ExportFileStore":
        return (
          <form.FormGroup
            name="fileStoreScan"
            validators={{ onSubmitAsync: ({ value }) => confirmFileStoreExport(value) }}
            onGroupSubmit={goToNextPane}
          >
            {(group) => renderPane(<ExportFileStore form={form} />, () => group.handleSubmit())}
          </form.FormGroup>
        );
      default:
        return null;
    }
  };

  return (
    <Dialog open={isOpen} fullWidth={true} maxWidth="sm" onClose={handleClose} fullScreen={isViewportSmall}>
      <DialogTitle data-test-id="modalTitle">{activePane.title}</DialogTitle>
      {renderActivePane()}
    </Dialog>
  );
}

function ExportDialog(props: ExportDialogArgs): React.ReactNode {
  return (
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={materialTheme}>
        <Confirm>
          <ExportWizard {...props} />
        </Confirm>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

export default ExportDialog;
