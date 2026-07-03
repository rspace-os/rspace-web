import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { HeadingContext } from "@/components/DynamicHeadingLevel";
import { makeListFormatter } from "@/modules/common/i18n/listFormat";
import docLinks from "../../assets/DocLinks";
import CustomTooltip from "../../components/CustomTooltip";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import SubmitSpinner from "../../components/SubmitSpinnerButton";
import TitledBox from "../../components/TitledBox";
import NavigateContext from "../../stores/contexts/Navigate";
import type { ImportRecordType } from "../../stores/stores/ImportStore";
import useStores from "../../stores/use-stores";
import type { URL } from "../../util/types";
import ColumnFieldMapping from "./Fields/ColumnFieldMapping";
import FileForImport from "./Fields/File";
import TemplateDetails from "./Fields/TemplateDetails";

const IMPORT_RECORD_TYPES = ["CONTAINERS", "SAMPLES", "SUBSAMPLES"] as const satisfies readonly ImportRecordType[];

function RecordsImport(): React.ReactNode {
  const { t, i18n } = useTranslation("inventory");
  const { importStore } = useStores();
  const importData = importStore.importData;

  const onTypeSelect = (newValue: ImportRecordType): URL => {
    return `/inventory/import?recordType=${newValue}`;
  };

  const recordType = importData?.recordType;
  const isSamplesImport = importData?.isSamplesImport;

  const loadedFileByRecordType = importData?.byRecordType("file") as File | null | undefined;
  const submitting = importStore.isCurrentImportState("submitting");

  const importSubmittable = importData?.importSubmittable;

  const showFooterAlert =
    Boolean(importData?.containersFile && !importData.containersSubmittable) ||
    Boolean(importData?.samplesFile && !importData.samplesSubmittable) ||
    Boolean(importData?.subSamplesFile && !importData.subSamplesSubmittable);

  const recordTypeLabels: Record<ImportRecordType, string> = {
    CONTAINERS: t("recordTypes.container.plural"),
    SAMPLES: t("recordTypes.sample.plural"),
    SUBSAMPLES: t("recordTypes.subsample.plural"),
  };
  const listFormatter = makeListFormatter(i18n.resolvedLanguage ?? i18n.language);

  const notImportableTypes: Array<{ route: ImportRecordType; label: string }> = [
    ...(importData?.containersFile && !importData.containersSubmittable
      ? [{ route: "CONTAINERS" as const, label: recordTypeLabels.CONTAINERS }]
      : []),
    ...(importData?.samplesFile && !importData.samplesSubmittable
      ? [{ route: "SAMPLES" as const, label: recordTypeLabels.SAMPLES }]
      : []),
    ...(importData?.subSamplesFile && !importData.subSamplesSubmittable
      ? [{ route: "SUBSAMPLES" as const, label: recordTypeLabels.SUBSAMPLES }]
      : []),
  ];

  const importButtonLabel = t("import.actions.importSelected", {
    types: listFormatter.format([
      ...(importData?.containersSubmittable ? [recordTypeLabels.CONTAINERS] : []),
      ...(importData?.samplesSubmittable ? [recordTypeLabels.SAMPLES] : []),
      ...(importData?.subSamplesSubmittable ? [recordTypeLabels.SUBSAMPLES] : []),
    ]),
  });

  const formattedNotImportableTypes = listFormatter.formatToParts(notImportableTypes.map(({ label }) => label));
  let formattedNotImportableTypeIndex = 0;

  function ImportTabs(_: Record<string, never>) {
    const { useNavigate } = useContext(NavigateContext);
    const navigate = useNavigate();

    return (
      <Box
        sx={{
          backgroundColor: "primary.main",
          display: "flex",
          flexDirection: "row",
          justifyContent: "space-between",
          alignItems: "center",
          pr: 2,
          pl: 2,
          borderRadius: 0.5,
          color: "white",
        }}
      >
        <Box sx={{ mr: 1, fontWeight: "bold" }}>{t("import.title")}</Box>
        <Tabs
          value={recordType}
          onChange={(_event, newRecordType: ImportRecordType) => {
            navigate(onTypeSelect(newRecordType));
          }}
          textColor="inherit"
          indicatorColor="secondary"
          variant="scrollable"
          scrollButtons="auto"
        >
          {IMPORT_RECORD_TYPES.map((value) => (
            <Tab
              sx={{ fontWeight: "bold" }}
              key={value}
              label={recordTypeLabels[value]}
              value={value}
              data-test-id={`${value}ImportTab`}
            />
          ))}
        </Tabs>
        <Box
          sx={{
            backgroundColor: "white",
            borderRadius: 1,
            ml: 1,
            pt: 0.25,
            px: 0.25,
          }}
        >
          <CustomTooltip title={t("import.importDocumentation")} enterDelay={200}>
            <HelpLinkIcon link={docLinks.import} title={t("import.helpTitle")} />
          </CustomTooltip>
        </Box>
      </Box>
    );
  }

  return (
    <HeadingContext level={3}>
      <Box sx={{ overflowY: "auto", height: "100%", mx: 1, mt: 0.5, pb: 3 }}>
        <ImportTabs />
        <Grid container sx={{ flexDirection: "row", width: "100%", flexGrow: 1, flexWrap: "nowrap" }}>
          <FileForImport loadedFile={loadedFileByRecordType} />
        </Grid>

        {/* Only samples need template handling */}
        {isSamplesImport && (
          <TitledBox title={t("import.sections.templateDetails")} border>
            <TemplateDetails />
          </TitledBox>
        )}
        <TitledBox title={t("import.sections.columnConversion")} border>
          <ColumnFieldMapping onTypeSelect={onTypeSelect} />
        </TitledBox>
        <Grid
          container
          spacing={2}
          sx={{
            mt: 1,
            flexDirection: "row",
            width: "100%",
            alignItems: "center",
            justifyContent: "space-between",
            flexWrap: "nowrap",
          }}
        >
          <Grid sx={{ flexGrow: 1 }}>
            {showFooterAlert ? (
              <Alert severity="warning">
                {t("import.cannotImport.message")}{" "}
                {formattedNotImportableTypes.map((part, i) => {
                  if (part.type === "literal") return <span key={i}>{part.value}</span>;
                  const item = notImportableTypes[formattedNotImportableTypeIndex++];
                  if (!item) return <span key={i}>{part.value}</span>;
                  return (
                    <span key={i}>
                      {item.route !== recordType ? <Link to={onTypeSelect(item.route)}>{item.label}</Link> : item.label}
                    </span>
                  );
                })}{" "}
                {t("import.cannotImport.tabSuffix", { count: notImportableTypes.length })}
              </Alert>
            ) : null}
          </Grid>
          <Grid>
            <SubmitSpinner
              disabled={!importSubmittable || submitting}
              onClick={() => importStore.submitImport()}
              loading={submitting}
              label={importButtonLabel}
            />
          </Grid>
        </Grid>
      </Box>
    </HeadingContext>
  );
}

export default observer(RecordsImport);
