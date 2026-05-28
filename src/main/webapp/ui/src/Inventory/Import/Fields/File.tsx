import FileField from "../../../components/Inputs/FileField";
import React from "react";
import useStores from "../../../stores/use-stores";
import { observer } from "mobx-react-lite";
import { useTheme } from "@mui/material/styles";
import TitledBox from "@/components/TitledBox";
import RemoveButton from "@/components/RemoveButton";
import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";

function ErrorDetails({
  errorMessage,
}: {
  errorMessage: string | null | undefined;
}): React.ReactNode {
  const theme = useTheme();
  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        padding: theme.spacing(0.5, 2),
      }}
    >
      <Box component="dt" sx={{ color: theme.palette.text.secondary }}>Error details:</Box>
      <Box component="dd" sx={{ marginLeft: theme.spacing(1), color: theme.palette.error.main }}>
        {errorMessage}
      </Box>
    </Box>
  );
}

type FileArgs = {
  loadedFile?: File | null;
};

function FileForImport({ loadedFile }: FileArgs): React.ReactNode {
  const { importStore } = useStores();

  const labelByRecordType =
    (importStore.importData?.byRecordType("label") as string) || "records";
  const fileByRecordTypeLoaded =
    importStore.importData?.byRecordType("fileLoaded");
  const fileLoaded = importStore.importData?.byRecordType("fileLoaded");
  const submitting = importStore.isCurrentImportState("submitting");

  return (
    <>
      <TitledBox title="Upload CSV File" border={true}>
        <Grid container spacing={2} sx={{ flexDirection: "row" }}>
          <Grid sx={{ flexGrow: 1 }}>
            <FileField
              accept=".csv"
              buttonLabel={`${
                fileByRecordTypeLoaded ? "Replace" : "Select"
              } ${labelByRecordType} CSV File`}
              name="file"
              datatestid="csvFile"
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
              title={`Clear File and Mappings`}
              disabled={!fileLoaded || submitting}
            />
          </Grid>
        </Grid>
      </TitledBox>
      {importStore.isCurrentImportState("parsingFailed") && (
        <ErrorDetails errorMessage={importStore.importData?.fileErrorMessage} />
      )}
    </>
  );
}

export default observer(FileForImport);
