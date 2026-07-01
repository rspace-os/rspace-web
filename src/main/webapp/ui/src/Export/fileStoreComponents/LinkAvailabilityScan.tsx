import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import { isNotNil } from "es-toolkit";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import LoadingFade from "../../components/LoadingFade";
import { formatFileSize } from "../../util/files";
import type { FileSystem } from "../common";

type FoundLinksListingArgs = {
  scanResultsPresent: boolean;
  scanResultsAvailableCount: number;
  scanResultsTotalFileSize: number;
  scanResultsOmittedCount: number;
  fileStoreScan: () => void;
  loadingScanResults: boolean;
  checkedFileSystems: Array<FileSystem>;
};

/**
 * This component allows the user to trigger a server-side scan of the export
 * plan that will check that all of the included links are accessible.
 */
export default function LinkAvailabilityScan({
  scanResultsPresent,
  scanResultsAvailableCount,
  scanResultsTotalFileSize,
  scanResultsOmittedCount,
  fileStoreScan,
  loadingScanResults,
  checkedFileSystems,
}: FoundLinksListingArgs): React.ReactNode {
  const { t } = useTranslation(["workspace", "common"]);
  const [open, setOpen] = useState(false);

  return (
    <Card sx={{ p: 1 }}>
      <h2>{t("export.fileStore.scan.heading")}</h2>
      {scanResultsPresent === false ? (
        <p>{t("export.fileStore.scan.runHint")}</p>
      ) : (
        <p>
          {scanResultsAvailableCount === 0
            ? t("export.fileStore.scan.noFilesIncluded")
            : t("export.fileStore.scan.filesIncluded", {
                count: scanResultsAvailableCount,
                size: formatFileSize(scanResultsTotalFileSize),
              })}
          {scanResultsAvailableCount > 0 &&
            scanResultsOmittedCount > 0 &&
            t("export.fileStore.scan.filesSkipped", { count: scanResultsOmittedCount })}
          {scanResultsOmittedCount > 0 && t("export.fileStore.scan.checkResults")}
        </p>
      )}
      <Button
        variant="contained"
        color="primary"
        onClick={() => {
          setOpen(true);
          fileStoreScan();
        }}
        data-test-id="scan-links"
        fullWidth
      >
        {t("export.fileStore.scan.scanButton")}
      </Button>
      {scanResultsPresent && (
        <Button variant="outlined" onClick={() => setOpen(true)} data-test-id="show-last-scan" fullWidth>
          {t("export.fileStore.scan.showLastButton")}
        </Button>
      )}
      <Dialog onClose={() => setOpen(false)} open={open}>
        {loadingScanResults ? (
          <div>
            <LoadingFade loading={loadingScanResults} />
          </div>
        ) : (
          <>
            <DialogTitle>{t("export.fileStore.scan.dialogTitle")}</DialogTitle>
            <DialogContent>
              {checkedFileSystems.map((fileSystem) => {
                const issues = Object.entries(fileSystem.checkedNfsLinkMessages);
                const checkedFiles = fileSystem.checkedNfsLinks.filter(isNotNil);
                return (
                  <div key={`skippedLinks${fileSystem.id}`}>
                    {issues.length > 0 ? (
                      <div>
                        <h3>{t("export.fileStore.scan.skippedHeading", { name: fileSystem.name })}</h3>
                        <Table>
                          <TableHead>
                            <TableRow>
                              <TableCell>{t("export.fileStore.scan.columns.path")}</TableCell>
                              <TableCell align="right">{t("export.fileStore.scan.columns.reason")}</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {issues.map((file) => {
                              return (
                                <TableRow key={`skippedLink${fileSystem.id}${file[0]}`}>
                                  <TableCell component="th" scope="row">
                                    {file[0]}
                                  </TableCell>
                                  <TableCell align="right">{file[1]}</TableCell>
                                </TableRow>
                              );
                            })}
                          </TableBody>
                        </Table>
                      </div>
                    ) : (
                      <div></div>
                    )}
                    {checkedFiles.length > 0 ? (
                      <div>
                        <h3>{t("export.fileStore.scan.includedHeading", { name: fileSystem.name })}</h3>
                        <Table>
                          <TableHead>
                            <TableRow>
                              <TableCell>{t("export.fileStore.scan.columns.path")}</TableCell>
                              <TableCell align="right">{t("export.fileStore.scan.columns.type")}</TableCell>
                              <TableCell align="right">{t("export.fileStore.scan.columns.size")}</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {checkedFiles.map((file) => {
                              if (!fileSystem.checkedNfsLinkMessages[file.fileSystemFullPath]) {
                                return (
                                  <>
                                    <TableRow key={`scannedLink${fileSystem.id}${file.fileSystemFullPath}`}>
                                      <TableCell component="th" scope="row">
                                        {file.fileSystemFullPath}
                                      </TableCell>
                                      <TableCell align="right">{file.type}</TableCell>
                                      <TableCell align="right">{formatFileSize(file.size)}</TableCell>
                                    </TableRow>
                                    {file.type === "folder" &&
                                      file.content.map((childFile) => {
                                        if (!fileSystem.checkedNfsLinkMessages[childFile.fileSystemFullPath]) {
                                          return (
                                            <TableRow
                                              key={`scannedLink${fileSystem.id}${childFile.fileSystemFullPath}`}
                                            >
                                              <TableCell component="th" scope="row">
                                                {childFile.fileSystemFullPath}
                                              </TableCell>
                                              <TableCell align="right">{childFile.type}</TableCell>
                                              <TableCell align="right">{formatFileSize(childFile.size)}</TableCell>
                                            </TableRow>
                                          );
                                        }
                                        return null;
                                      })}
                                  </>
                                );
                              }
                              return null;
                            })}
                          </TableBody>
                        </Table>
                      </div>
                    ) : (
                      <div></div>
                    )}
                  </div>
                );
              })}
            </DialogContent>
            <DialogActions>
              <Button onClick={() => setOpen(false)} color="primary" data-test-id="button-loading-scan-ok">
                {t("common:actions.ok")}
              </Button>
            </DialogActions>
          </>
        )}
      </Dialog>
    </Card>
  );
}
