import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import RemoveButton from "@/components/RemoveButton";
import TitledBox from "@/components/TitledBox";
import FileField from "../../../components/Inputs/FileField";
import useStores from "../../../stores/use-stores";

type FileArgs = {
  loadedFile?: File | null;
};

function FileForImport({ loadedFile }: FileArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const { importStore } = useStores();

  const labelByRecordType = (importStore.importData?.byRecordType("label") as string) || "records";
  const fileByRecordTypeLoaded = importStore.importData?.byRecordType("fileLoaded");
  const fileLoaded = importStore.importData?.byRecordType("fileLoaded");
  const submitting = importStore.isCurrentImportState("submitting");

  return (
    <>
      <TitledBox title={t("import.file.title")} border={true}>
        <Grid container spacing={2} sx={{ flexDirection: "row" }}>
          <Grid sx={{ flexGrow: 1 }}>
            <FileField
              accept=".csv"
              buttonLabel={t(fileByRecordTypeLoaded ? "import.file.replaceCsvFile" : "import.file.selectCsvFile", {
                recordType: labelByRecordType,
              })}
              name="file"
              data-test-id="csvFile"
              onChange={({ file }) => importStore.importData?.setFile(file)}
              showSelectedFilename={true}
              loading={importStore.isCurrentImportState("parsing")}
              error={importStore.isCurrentImportState("parsingFailed")}
              key={importStore.fileImportKey}
              loadedFile={loadedFile}
            />
          </Grid>
          <Grid>
            <RemoveButton
              onClick={() => importStore.importData?.clearFile()}
              title={t("import.file.clearFileAndMappings")}
              disabled={!fileLoaded || submitting}
            />
          </Grid>
        </Grid>
      </TitledBox>
      {importStore.isCurrentImportState("parsingFailed") && (
        <Box sx={{ display: "flex", alignItems: "center", py: 0.5, px: 2 }}>
          <Box component="dt" sx={{ color: "text.secondary" }}>
            {t("import.file.errorDetails")}
          </Box>
          <Box component="dd" sx={{ ml: 1, color: "error.main" }}>
            {importStore.importData?.fileErrorMessage}
          </Box>
        </Box>
      )}
    </>
  );
}

export default observer(FileForImport);
