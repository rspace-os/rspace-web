import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import type React from "react";
import RemoveButton from "@/components/RemoveButton";
import TitledBox from "@/components/TitledBox";
import FileField from "../../../components/Inputs/FileField";
import useStores from "../../../stores/use-stores";
import { withStyles } from "../../../util/styles";

type FileArgs = {
    loadedFile?: File | null;
};

function FileForImport({ loadedFile }: FileArgs): React.ReactNode {
    const { importStore } = useStores();

    const ErrorDetails = withStyles<
        { errorMessage: string | null | undefined },
        { details: string; detailsLabel: string; message: string }
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

    const labelByRecordType = (importStore.importData?.byRecordType("label") as string) || "records";
    const fileByRecordTypeLoaded = importStore.importData?.byRecordType("fileLoaded");
    const fileLoaded = importStore.importData?.byRecordType("fileLoaded");
    const submitting = importStore.isCurrentImportState("submitting");

    return (
        <>
            <TitledBox title="Upload CSV File" border={true}>
                <Grid container spacing={2} flexDirection="row">
                    <Grid item flexGrow={1}>
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
                    <Grid item>
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
