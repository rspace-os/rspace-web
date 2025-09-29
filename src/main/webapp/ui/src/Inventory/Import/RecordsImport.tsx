import React, { useContext } from "react";
import TitledBox from "../components/TitledBox";
import { observer } from "mobx-react-lite";
import FileForImport from "./Fields/File";
import ColumnFieldMapping from "./Fields/ColumnFieldMapping";
import TemplateDetails from "./Fields/TemplateDetails";
import HelpTextAlert from "../../components/HelpTextAlert";
import Box from "@mui/material/Box";
import RemoveButton from "../../components/RemoveButton";
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
import { makeStyles } from "tss-react/mui";
import clsx from "clsx";
import { capitaliseJustFirstChar } from "../../util/Util";
import { type URL } from "../../util/types";
import { Link } from "react-router-dom";
import { HeadingContext } from "@/components/DynamicHeadingLevel";

const useStyles = makeStyles()((theme) => ({
  bold: {
    fontWeight: "bold",
  },
  fileButtonWrapper: {
    flexDirection: "row",
    width: "100%",
    flexGrow: 1,
    flexWrap: "nowrap",
  },
  footWrapper: {
    flexDirection: "row",
    width: "100%",
    alignItems: "center",
    justifyContent: "space-between",
    flexWrap: "nowrap",
  },
  grow: { flexGrow: 1 },
  headWrapper: {
    overflowY: "auto",
    height: "100%",
    marginRight: theme.spacing(1),
    marginLeft: theme.spacing(1),
    marginTop: theme.spacing(0.5),
    paddingBottom: theme.spacing(3),
  },
  helpIconWrapper: {
    backgroundColor: "white",
    borderRadius: theme.spacing(1),
    marginLeft: theme.spacing(1),
    paddingTop: theme.spacing(0.25),
    paddingLeft: theme.spacing(0.25),
    paddingRight: theme.spacing(0.25),
  },
  ml: {
    marginLeft: theme.spacing(1),
  },
  mr: {
    marginRight: theme.spacing(1),
  },
  mt: {
    marginTop: theme.spacing(1),
  },
  tabsWrapper: {
    backgroundColor: theme.palette.primary.main,
    display: "flex",
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingRight: theme.spacing(2),
    paddingLeft: theme.spacing(2),
    borderRadius: theme.spacing(0.5),
    color: "white",
  },
}));

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

  const { classes } = useStyles();

  function ImportTabs(_: Record<string, never>) {
    const importRecordTypes = ["CONTAINERS", "SAMPLES", "SUBSAMPLES"];
    const { useNavigate } = useContext(NavigateContext);
    const navigate = useNavigate();

    return (
      <Box className={classes.tabsWrapper}>
        <Box className={clsx(classes.mr, classes.bold)}>IMPORT</Box>
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
              className={classes.bold}
              key={value}
              label={value}
              value={value}
              data-test-id={`${capitaliseJustFirstChar(value)}ImportTab`}
            />
          ))}
        </Tabs>
        <Box className={classes.helpIconWrapper}>
          <CustomTooltip title="Import Documentation" enterDelay={200}>
            <HelpLinkIcon link={docLinks.import} title="Info on importing." />
          </CustomTooltip>
        </Box>
      </Box>
    );
  }

  return (
    <HeadingContext level={3}>
      <Box className={classes.headWrapper}>
        <ImportTabs />
        <Grid container className={classes.fileButtonWrapper}>
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
          className={clsx(classes.mt, classes.footWrapper)}
        >
          <Grid item className={classes.grow}>
            <HelpTextAlert
              severity="warning"
              condition={showFooterAlert}
              text={
                <>
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
                </>
              }
            />
          </Grid>
          <Grid item>
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
