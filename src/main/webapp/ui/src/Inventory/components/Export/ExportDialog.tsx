import React, { useState } from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import ContextDialog from "../ContextMenu/ContextDialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContentText from "@mui/material/DialogContentText";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import RadioField, {
  type RadioOption,
} from "../../../components/Inputs/RadioField";
import SubmitSpinner from "../../../components/SubmitSpinnerButton";
import Typography from "@mui/material/Typography";
import { makeStyles } from "tss-react/mui";
import {
  type InventoryRecord,
  type ApiRecordType,
} from "../../../stores/definitions/InventoryRecord";
import {
  type ExportOptions,
  type ExportMode,
  type OptionalContent,
  type ExportFileType,
} from "../../../stores/definitions/Search";
import { toTitleCase } from "../../../util/Util";
import Alert from "@mui/material/Alert";

export type ExportType = "userData" | "contextMenu" | "listOfMaterials";

export function defaultExportOptions(
  selectedResults: Array<InventoryRecord> | undefined,
  exportType: ExportType
): ExportOptions {
  const exportedRecordTypes = new Set(selectedResults?.map(({ type }) => type));
  const showSamplesModule: boolean = exportedRecordTypes.has("SAMPLE");
  const showContainersModule: boolean =
    exportedRecordTypes.has("CONTAINER") || exportType === "userData";

  return {
    exportMode: "FULL",
    includeSubsamplesInSample: showSamplesModule ? "INCLUDE" : null,
    includeContainerContent: showContainersModule ? "EXCLUDE" : null,
    resultFileType: "ZIP",
  };
}

const useStyles = makeStyles()((theme) => ({
  optionModuleWrapper: {
    border: `1px solid ${theme.palette.divider}`,
    borderRadius: theme.spacing(0.5),
    padding: theme.spacing(1),
    marginBottom: theme.spacing(2),
  },
}));

type ExportDialogArgs = {
  openExportDialog: boolean;
  setOpenExportDialog: (value: boolean) => void;
  onExport: (options: ExportOptions) => Promise<void> | void;
  exportType: ExportType;
  /* n/a for user's data export case */
  closeMenu?: () => void;
  selectedResults?: Array<InventoryRecord>;
};

type OptionsWrapperArgs = {
  exportOptions: ExportOptions;
  setExportOptions: (options: ExportOptions) => void;
  selectedResults?: Array<InventoryRecord>;
  exportType: ExportType;
};

export const ExportOptionsWrapper = ({
  exportOptions,
  setExportOptions,
  selectedResults,
  exportType,
}: OptionsWrapperArgs): React.ReactNode => {
  const { classes } = useStyles();

  const exportModeOptions: Array<RadioOption<ExportMode>> = [
    { value: "FULL", label: "Full" },
    { value: "COMPACT", label: "Compact" },
  ];

  const samplesContentOptions: Array<RadioOption<OptionalContent>> = [
    { value: "INCLUDE", label: "Include Subsamples" },
    { value: "EXCLUDE", label: "Exclude Subsamples" },
  ];

  const containersContentOptions: Array<RadioOption<OptionalContent>> = [
    { value: "INCLUDE", label: "Include Content" },
    { value: "EXCLUDE", label: "Exclude Content" },
  ];

  const resultFileTypeOptions: Array<RadioOption<ExportFileType>> = [
    { value: "ZIP", label: "ZIP Bundle" },
    { value: "SINGLE_CSV", label: "Single CSV" },
  ];

  const exportedRecordTypes: Array<ApiRecordType> = [
    ...new Set(selectedResults?.map(({ type }) => type)),
  ];

  const showSamplesModule: boolean = exportedRecordTypes.includes("SAMPLE");
  const showContainersModule: boolean =
    exportedRecordTypes.includes("CONTAINER") || exportType === "userData";

  const HelperText = () => (
    <>
      <Typography variant="body1" paragraph>
        {exportType === "userData"
          ? "Exporting all items owned by the user."
          : `Exporting ${
              exportType === "contextMenu" ? "selected" : "all list items"
            }: ${exportedRecordTypes
              .map((t) => toTitleCase(t) + "s")
              .join(", ")}.`}
      </Typography>
    </>
  );

  return (
    <>
      <DialogContentText component="span">
        <HelperText />
      </DialogContentText>
      <FormControl component="fieldset" fullWidth>
        <Box className={classes.optionModuleWrapper}>
          <FormLabel id="export-mode-radiogroup-label">Export Mode</FormLabel>
          <RadioField
            name={"Export Mode Options"}
            value={exportOptions.exportMode}
            onChange={({ target }) => {
              if (target.value)
                setExportOptions({
                  ...exportOptions,
                  exportMode: target.value,
                });
            }}
            options={exportModeOptions}
            disabled={false}
            labelPlacement="bottom"
            row
            smallText={true}
          />
          <Alert severity="info">
            {`All data, ${
              exportOptions.exportMode === "FULL" ? "including" : "excluding"
            } custom and template fields.`}
          </Alert>
        </Box>
        {showSamplesModule && (
          <Box className={classes.optionModuleWrapper}>
            <FormLabel id="samples-options-label">Samples</FormLabel>
            <RadioField
              name={"Export Samples Options"}
              value={exportOptions.includeSubsamplesInSample}
              onChange={({ target }) => {
                if (target.value)
                  setExportOptions({
                    ...exportOptions,
                    includeSubsamplesInSample: target.value,
                  });
              }}
              options={samplesContentOptions}
              disabled={false}
              labelPlacement="bottom"
              row
              smallText={true}
            />
          </Box>
        )}
        {showContainersModule && (
          <Box className={classes.optionModuleWrapper}>
            <FormLabel id="containers-options-label">Containers</FormLabel>
            <RadioField
              name={"Export Containers Options"}
              value={exportOptions.includeContainerContent}
              onChange={({ target }) => {
                if (target.value)
                  setExportOptions({
                    ...exportOptions,
                    includeContainerContent: target.value,
                  });
              }}
              options={containersContentOptions}
              disabled={false}
              labelPlacement="bottom"
              row
              smallText={true}
            />
            <Alert severity="info">
              {exportOptions.includeContainerContent === "INCLUDE"
                ? `All content visible to the user, including items belonging to
                  other users.`
                : `Containers only, without their content.`}
            </Alert>
          </Box>
        )}
        <Box className={classes.optionModuleWrapper}>
          <FormLabel id="export-mode-radiogroup-label">File Type</FormLabel>
          <RadioField
            name={"Export File Type Options"}
            value={exportOptions.resultFileType}
            onChange={({ target }) => {
              if (target.value)
                setExportOptions({
                  ...exportOptions,
                  resultFileType: target.value,
                });
            }}
            options={resultFileTypeOptions}
            disabled={false}
            labelPlacement="bottom"
            row
            smallText={true}
          />
          <Alert severity="info">
            {exportOptions.resultFileType === "ZIP"
              ? "A .ZIP bundle containing .CSV files for each item type."
              : "A single .CSV file combining all exported items."}
          </Alert>
        </Box>
      </FormControl>
    </>
  );
};

export default function ExportDialog({
  openExportDialog,
  setOpenExportDialog,
  onExport,
  exportType,
  closeMenu,
  selectedResults,
}: ExportDialogArgs): React.ReactNode {
  const [exportOptions, setExportOptions] = useState<ExportOptions>(
    defaultExportOptions(selectedResults, exportType)
  );

  const handleClose = () => {
    setOpenExportDialog(false);
    if (typeof closeMenu === "function") closeMenu();
  };

  const onSubmitHandler = () => {
    void onExport(exportOptions);
    handleClose();
  };

  return (
    <ContextDialog
      open={openExportDialog}
      onClose={handleClose}
      fullWidth
      maxWidth="sm"
    >
      <DialogTitle>Export Options</DialogTitle>
      <DialogContent>
        <ExportOptionsWrapper
          exportOptions={exportOptions}
          setExportOptions={setExportOptions}
          selectedResults={selectedResults}
          exportType={exportType}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={false}>
          Cancel
        </Button>
        <SubmitSpinner
          onClick={onSubmitHandler}
          disabled={false}
          loading={false}
          label="Export"
        />
      </DialogActions>
    </ContextDialog>
  );
}
