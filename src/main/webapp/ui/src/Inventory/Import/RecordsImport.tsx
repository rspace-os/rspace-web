import React, { useContext } from "react";
import TitledBox from "../../components/TitledBox";
import { observer } from "mobx-react-lite";
import FileForImport from "./Fields/File";
import ColumnFieldMapping from "./Fields/ColumnFieldMapping";
import TemplateDetails from "./Fields/TemplateDetails";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import SubmitSpinner from "../../components/SubmitSpinnerButton";
import Grid from "@mui/material/Grid";
import useStores from "../../stores/use-stores";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import docLinks from "../../assets/DocLinks";
import Tabs from "@mui/material/Tabs";
import Tab from "@mui/material/Tab";
import CustomTooltip from "../../components/CustomTooltip";
import { type ImportRecordType } from "../../stores/stores/ImportStore";
import NavigateContext from "../../stores/contexts/Navigate";
import { capitaliseJustFirstChar } from "../../util/Util";
import { type URL } from "../../util/types";
import { Link } from "react-router-dom";
import { HeadingContext } from "@/components/DynamicHeadingLevel";

function RecordsImport(): React.ReactNode {
  const { importStore } = useStores();
  const importData = importStore.importData;

  const onTypeSelect = (newValue: ImportRecordType): URL => {
    return `/inventory/import?recordType=${newValue}`;
  };

  const recordType = importData?.recordType;
  const isSamplesImport = importData?.isSamplesImport;

  const loadedFileByRecordType = importData?.byRecordType("file") as
    | File
    | null
    | undefined;
  const submitting = importStore.isCurrentImportState("submitting");

  const importSubmittable = importData?.importSubmittable;

  const showFooterAlert =
    Boolean(importData?.containersFile && !importData.containersSubmittable) ||
    Boolean(importData?.samplesFile && !importData.samplesSubmittable) ||
    Boolean(importData?.subSamplesFile && !importData.subSamplesSubmittable);

  const notImportable = () => {
    const types: string[] = [];
    if (importData?.containersFile && !importData.containersSubmittable)
      types.push("Containers");
    if (importData?.samplesFile && !importData.samplesSubmittable)
      types.push("Samples");
    if (importData?.subSamplesFile && !importData.subSamplesSubmittable)
      types.push("Subsamples");
    return types;
  };

  const importButtonLabel = `Import ${[
    ...(importData?.containersSubmittable ? ["Containers"] : []),
    ...(importData?.samplesSubmittable ? ["Samples"] : []),
    ...(importData?.subSamplesSubmittable ? ["Subsamples"] : []),
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
        <Box sx={{ mr: 1, fontWeight: "bold" }}>IMPORT</Box>
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
          <CustomTooltip title="Import Documentation" enterDelay={200}>
            <HelpLinkIcon link={docLinks.import} title="Info on importing." />
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
          <TitledBox title="Template Details" border>
            <TemplateDetails />
          </TitledBox>
        )}
        <TitledBox title="CSV Column Conversion Settings" border>
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
                Some csv documents cannot be imported: check Settings in the{" "}
                {notImportable().map((t, i) => (
                  <span key={i}>
                    {i > 0 && <>, </>}
                    {t.toUpperCase() !== recordType ? (
                      <Link
                        to={onTypeSelect(t.toUpperCase() as ImportRecordType)}
                      >
                        {t}
                      </Link>
                    ) : (
                      t
                    )}
                  </span>
                ))}
                {notImportable().length === 1 ? <> tab.</> : <> tabs.</>}
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
