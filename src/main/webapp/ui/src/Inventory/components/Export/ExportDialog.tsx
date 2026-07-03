import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import type { Theme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { formatList } from "@/modules/common/i18n/listFormat";
import type { ApiRecordType, InventoryRecord } from "@/stores/definitions/InventoryRecord";
import type { ExportFileType, ExportMode, ExportOptions, OptionalContent } from "@/stores/definitions/Search";
import RadioField, { type RadioOption } from "../../../components/Inputs/RadioField";
import SubmitSpinner from "../../../components/SubmitSpinnerButton";
import ContextDialog from "../ContextMenu/ContextDialog";

export type ExportType = "userData" | "contextMenu" | "listOfMaterials";

export function defaultExportOptions(
  selectedResults: Array<InventoryRecord> | null | undefined,
  exportType: ExportType,
): ExportOptions {
  const exportedRecordTypes = new Set(selectedResults?.map(({ type }) => type));
  const showSamplesModule: boolean = exportedRecordTypes.has("SAMPLE");
  const showContainersModule: boolean = exportedRecordTypes.has("CONTAINER") || exportType === "userData";

  return {
    exportMode: "FULL",
    includeSubsamplesInSample: showSamplesModule ? "INCLUDE" : null,
    includeContainerContent: showContainersModule ? "EXCLUDE" : null,
    resultFileType: "ZIP",
  };
}

const optionModuleWrapperSx = (theme: Theme) => ({
  border: `1px solid ${theme.palette.sidebar.selected.bg}`,
  borderRadius: theme.spacing(0.5),
  p: 1,
  mb: 2,
});

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
  const { t, i18n } = useTranslation(["inventory", "common"]);
  const exportModeOptions: Array<RadioOption<ExportMode>> = [
    { value: "FULL", label: t("export.dialog.options.full") },
    { value: "COMPACT", label: t("export.dialog.options.compact") },
  ];

  const samplesContentOptions: Array<RadioOption<OptionalContent>> = [
    { value: "INCLUDE", label: t("export.dialog.options.subsamplesInclude") },
    { value: "EXCLUDE", label: t("export.dialog.options.subsamplesExclude") },
  ];

  const containersContentOptions: Array<RadioOption<OptionalContent>> = [
    { value: "INCLUDE", label: t("export.dialog.options.containerContentInclude") },
    { value: "EXCLUDE", label: t("export.dialog.options.containerContentExclude") },
  ];

  const resultFileTypeOptions: Array<RadioOption<ExportFileType>> = [
    { value: "ZIP", label: t("export.dialog.options.zipFile") },
    { value: "SINGLE_CSV", label: t("export.dialog.options.singleCsv") },
  ];

  const exportedRecordTypes: Array<ApiRecordType> = [...new Set(selectedResults?.map(({ type }) => type))];
  const recordTypePluralLabels: Record<ApiRecordType, string> = {
    CONTAINER: t("recordTypes.container.plural"),
    INSTRUMENT: t("recordTypes.instrument.plural"),
    INSTRUMENT_TEMPLATE: t("recordTypes.instrumentTemplate.plural"),
    SAMPLE: t("recordTypes.sample.plural"),
    SAMPLE_TEMPLATE: t("recordTypes.sampleTemplate.plural"),
    SUBSAMPLE: t("recordTypes.subsample.plural"),
  };
  const exportedTypesLabel = formatList(
    exportedRecordTypes.map((type) => recordTypePluralLabels[type]),
    i18n.resolvedLanguage ?? i18n.language,
  );
  const exportSummary =
    exportType === "userData"
      ? t("export.dialog.exportingAll")
      : exportType === "contextMenu"
        ? t("export.dialog.exportingContextMenu", { types: exportedTypesLabel })
        : t("export.dialog.exportingList", { types: exportedTypesLabel });

  const showSamplesModule: boolean = exportedRecordTypes.includes("SAMPLE");
  const showContainersModule: boolean = exportedRecordTypes.includes("CONTAINER") || exportType === "userData";

  return (
    <>
      <DialogContentText component="span">
        <Typography component="p" variant="body1" sx={{ mb: 2 }}>
          {exportSummary}
        </Typography>
      </DialogContentText>
      <FormControl component="fieldset" fullWidth>
        <Box sx={optionModuleWrapperSx}>
          <FormLabel id="export-mode-radiogroup-label">{t("export.dialog.mode")}</FormLabel>
          <RadioField
            name={t("export.dialog.radioLabels.mode")}
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
            {t("export.dialog.modeAllData", {
              include:
                exportOptions.exportMode === "FULL"
                  ? t("export.dialog.modeIncluding")
                  : t("export.dialog.modeExcluding"),
            })}
          </Alert>
        </Box>
        {showSamplesModule && (
          <Box sx={optionModuleWrapperSx}>
            <FormLabel id="samples-options-label">{t("recordTypes.sample.plural")}</FormLabel>
            <RadioField
              name={t("export.dialog.radioLabels.samples")}
              value={exportOptions.includeSubsamplesInSample ?? null}
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
          <Box sx={optionModuleWrapperSx}>
            <FormLabel id="containers-options-label">{t("recordTypes.container.plural")}</FormLabel>
            <RadioField
              name={t("export.dialog.radioLabels.containers")}
              value={exportOptions.includeContainerContent ?? null}
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
                ? t("export.exporter.containerContent.include")
                : t("export.exporter.containerContent.exclude")}
            </Alert>
          </Box>
        )}
        <Box sx={optionModuleWrapperSx}>
          <FormLabel id="export-mode-radiogroup-label">{t("export.dialog.fileType")}</FormLabel>
          <RadioField
            name={t("export.dialog.radioLabels.fileType")}
            value={exportOptions.resultFileType ?? null}
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
              ? t("export.dialog.options.zipBundle")
              : t("export.dialog.options.csvFile")}
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
  const { t } = useTranslation(["inventory", "common"]);
  const [exportOptions, setExportOptions] = useState<ExportOptions>(
    defaultExportOptions(selectedResults ?? null, exportType),
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
    <ContextDialog open={openExportDialog} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle>{t("export.dialog.title")}</DialogTitle>
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
          {t("common:actions.cancel")}
        </Button>
        <SubmitSpinner onClick={onSubmitHandler} disabled={false} loading={false} label={t("common:actions.export")} />
      </DialogActions>
    </ContextDialog>
  );
}
