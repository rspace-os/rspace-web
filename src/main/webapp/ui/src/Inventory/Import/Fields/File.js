// @flow

import FileField from "../../../components/Inputs/FileField";
import UploadFormControl from "./FormControl";
import React, { type Node, type ComponentType } from "react";
import useStores from "../../../stores/use-stores";
import { observer } from "mobx-react-lite";
import { withStyles } from "Styles";

type FileArgs = {|
  loadedFile?: ?File,
|};

function FileForImport({ loadedFile }: FileArgs): Node {
  const { importStore } = useStores();

  const ErrorDetails = withStyles<
    {| errorMessage: ?string |},
    { details: string, detailsLabel: string, message: string }
  >((theme) => ({
    details: {
      display: "flex",
      alignItems: "center",
      padding: theme.spacing(0.5, 2),
    },
    detailsLabel: {
      color: theme.palette.text.secondary,
    },
    message: {
      marginLeft: theme.spacing(1),
      color: theme.palette.error.main,
    },
  }))(({ classes, errorMessage }) => (
    <div className={classes.details}>
      <dt className={classes.detailsLabel}>Error details:</dt>
      <dd className={classes.message}>{errorMessage}</dd>
    </div>
  ));

  const labelByRecordType =
    importStore.importData?.byRecordType("label") || "records";
  const fileByRecordTypeLoaded =
    importStore.importData?.byRecordType("fileLoaded");

  return (
    <>
      <UploadFormControl
        label="File Upload"
        error={false}
        helperText="Add a CSV file"
      >
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
      </UploadFormControl>
      {importStore.isCurrentImportState("parsingFailed") && (
        <ErrorDetails errorMessage={importStore.importData?.fileErrorMessage} />
      )}
    </>
  );
}

export default (observer(FileForImport): ComponentType<FileArgs>);
