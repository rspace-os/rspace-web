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
import docLinks from "../../assets/DocLinks";
import CustomTooltip from "../../components/CustomTooltip";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import SubmitSpinner from "../../components/SubmitSpinnerButton";
import TitledBox from "../../components/TitledBox";
import NavigateContext from "../../stores/contexts/Navigate";
import type { ImportRecordType } from "../../stores/stores/ImportStore";
import useStores from "../../stores/use-stores";
import type { URL } from "../../util/types";
import { capitaliseJustFirstChar } from "../../util/Util";
import ColumnFieldMapping from "./Fields/ColumnFieldMapping";
import FileForImport from "./Fields/File";
import TemplateDetails from "./Fields/TemplateDetails";

function RecordsImport(): React.ReactNode {
  const { t } = useTranslation("inventory");
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

  const notImportable = () => {
    const types: Array<{ route: ImportRecordType; label: string }> = [];
    if (importData?.containersFile && !importData.containersSubmittable)
      types.push({ route: "CONTAINERS", label: t("import.types.containers") });
    if (importData?.samplesFile && !importData.samplesSubmittable)
      types.push({ route: "SAMPLES", label: t("import.types.samples") });
    if (importData?.subSamplesFile && !importData.subSamplesSubmittable)
      types.push({ route: "SUBSAMPLES", label: t("import.types.subsamples") });
    return types;
  };

  const importButtonLabel = `${t("import.actions.import")} ${[
    ...(importData?.containersSubmittable ? [t("import.types.containers")] : []),
    ...(importData?.samplesSubmittable ? [t("import.types.samples")] : []),
    ...(importData?.subSamplesSubmittable ? [t("import.types.subsamples")] : []),
  ].join(" + ")}`;

  function ImportTabs(_: Record<string, never>) {
    const importRecordTypes = ["CONTAINERS", "SAMPLES", "SUBSAMPLES"];
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
          {importRecordTypes.map((value) => (
            <Tab
              sx={{ fontWeight: "bold" }}
              key={value}
              label={value}
              value={value}
              data-test-id={`${capitaliseJustFirstChar(value)}ImportTab`}
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
                {notImportable().map(({ route, label }, i) => (
                  <span key={i}>
                    {i > 0 && ", "}
                    {route !== recordType ? <Link to={onTypeSelect(route)}>{label}</Link> : label}
                  </span>
                ))}{" "}
                {t("import.cannotImport.tabSuffix", { count: notImportable().length })}
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
