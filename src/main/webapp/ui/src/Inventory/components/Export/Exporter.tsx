import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import CardHeader from "@mui/material/CardHeader";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import type { ExportOptions } from "../../../stores/definitions/Search";
import { ExportOptionsWrapper } from "./ExportDialog";

type ExporterArgs = {
  elevation?: number;
  header?: React.ReactNode;
  selectionHelpText?: string | null;
  testId?: string;
  paddingless?: boolean;
  showActions?: boolean;
  setOpenExporter: (open: boolean) => void;
  selectedResults: Array<InventoryRecord>;
  onExport: () => void;
  exportOptions: ExportOptions;
  setExportOptions: (options: ExportOptions) => void;
};

function Exporter({
  elevation = 0,
  header,
  selectionHelpText,
  testId,
  paddingless = false,
  showActions = false,
  selectedResults,
  onExport,
  setOpenExporter,
  exportOptions,
  setExportOptions,
}: ExporterArgs): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);
  return (
    <Card
      elevation={elevation}
      data-test-id={testId}
      sx={{ height: "100%", width: "100%", display: "flex", flexDirection: "column" }}
    >
      {Boolean(header) && <CardHeader title={header} sx={{ flexWrap: "nowrap" }} />}
      <CardContent
        sx={(theme) => ({
          flexGrow: 1,
          overflowY: "auto",
          display: "flex",
          flexDirection: "column",
          padding: `${theme.spacing(paddingless ? 0 : 2)} !important`,
          paddingTop: "0 !important",
          overflowX: "hidden",
        })}
      >
        <Box sx={{ mb: 1 }}>
          <ExportOptionsWrapper
            exportOptions={exportOptions}
            setExportOptions={setExportOptions}
            selectedResults={selectedResults}
            exportType="listOfMaterials"
          />
        </Box>
        {selectionHelpText && (
          <Box sx={{ mb: 1 }}>
            <Alert severity="info">{selectionHelpText}</Alert>
          </Box>
        )}
      </CardContent>
      {showActions && (
        <CardActions>
          <Button
            variant="contained"
            color="callToAction"
            disableElevation
            onClick={() => {
              void onExport();
            }}
            disabled={selectedResults.length === 0}
          >
            {exportOptions.resultFileType === "ZIP" ? t("export.dialog.downloadZip") : t("export.dialog.downloadCsv")}
          </Button>
          <Button
            onClick={() => {
              setOpenExporter(false);
            }}
          >
            {t("common:actions.cancel")}
          </Button>
        </CardActions>
      )}
    </Card>
  );
}

export default observer(Exporter);
