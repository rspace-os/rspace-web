import React from "react";
import { observer } from "mobx-react-lite";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardActions from "@mui/material/CardActions";
import CardHeader from "@mui/material/CardHeader";
import Box from "@mui/material/Box";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import { type ExportOptions } from "../../../stores/definitions/Search";
import Alert from "@mui/material/Alert";
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
          <>
            <Button
              variant="contained"
              color="callToAction"
              disableElevation
              onClick={() => {
                void onExport();
              }}
              disabled={selectedResults.length === 0}
            >
              DOWNLOAD {exportOptions.resultFileType === "ZIP" ? "ZIP" : "CSV"}
            </Button>
            <Button
              onClick={() => {
                setOpenExporter(false);
              }}
            >
              Cancel
            </Button>
          </>
        </CardActions>
      )}
    </Card>
  );
}

export default observer(Exporter);
