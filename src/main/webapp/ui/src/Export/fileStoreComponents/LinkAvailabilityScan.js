//@flow

import React, { type Node, useState } from "react";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import { formatFileSize } from "../../util/files";
import LoadingFade from "../../components/LoadingFade";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import { type FileSystem } from "../ExportFileStore";
import * as ArrayUtils from "../../util/ArrayUtils";

type FoundLinksListingArgs = {|
  scanResultsPresent: boolean,
  scanResultsAvailableCount: number,
  scanResultsTotalFileSize: number,
  scanResultsOmittedCount: number,
  fileStoreScan: () => void,
  loadingScanResults: boolean,
  checkedFileSystems: Array<FileSystem>,
|};

/*
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
}: FoundLinksListingArgs): Node {
  const [open, setOpen] = useState(false);

  return (
    <Card sx={{ p: 1 }}>
      <h2>Link availability scan</h2>
      {scanResultsPresent === false ? (
        <p>
          Before continuing please run the filestore links availability scan,
          which will report on any unaccessible or filtered links.
        </p>
      ) : (
        <p>
          {scanResultsAvailableCount === 0 ? (
            <span>
              Export <strong>will not include any filestore files</strong>.
            </span>
          ) : (
            <span>
              Export will include{" "}
              <strong>{scanResultsAvailableCount} filestore file(s) </strong>
              of total size{" "}
              <strong>{formatFileSize(scanResultsTotalFileSize)}</strong>.{" "}
            </span>
          )}
          {scanResultsAvailableCount > 0 && scanResultsOmittedCount > 0 && (
            <span>
              <strong>
                {scanResultsOmittedCount} file(s) or folder(s) will be skipped
              </strong>
              .{" "}
            </span>
          )}
          {scanResultsOmittedCount > 0 && (
            <span>Please check the scan results for details.</span>
          )}
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
        Scan filestore links
      </Button>
      {scanResultsPresent && (
        <Button
          variant="outlined"
          onClick={() => setOpen(true)}
          data-test-id="show-last-scan"
          fullWidth
        >
          Show last scan results
        </Button>
      )}
      <Dialog onClose={() => setOpen(false)} open={open}>
        {loadingScanResults ? (
          <div>
            <LoadingFade loading={loadingScanResults} />
          </div>
        ) : (
          <>
            <DialogTitle>Availability scan results</DialogTitle>
            <DialogContent>
              {checkedFileSystems.map((fileSystem) => {
                const issues = Object.entries(
                  fileSystem.checkedNfsLinkMessages
                );
                const checkedFiles = ArrayUtils.filterNull(
                  fileSystem.checkedNfsLinks
                );
                return (
                  <div key={"skippedLinks" + fileSystem.id}>
                    {issues.length > 0 ? (
                      <div>
                        <h3>Skipped files from {fileSystem.name}</h3>
                        <Table>
                          <TableHead>
                            <TableRow>
                              <TableCell>
                                Full path on the File System
                              </TableCell>
                              <TableCell align="right">Reason</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {issues.map((file) => {
                              return (
                                <TableRow
                                  key={"skippedLink" + fileSystem.id + file[0]}
                                >
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
                        <h3>Included files from {fileSystem.name}</h3>
                        <Table>
                          <TableHead>
                            <TableRow>
                              <TableCell>
                                Full path on the File System
                              </TableCell>
                              <TableCell align="right">Type</TableCell>
                              <TableCell align="right">Size</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {checkedFiles.map((file) => {
                              if (
                                !fileSystem.checkedNfsLinkMessages[
                                  file.fileSystemFullPath
                                ]
                              ) {
                                return (
                                  <>
                                    <TableRow
                                      key={
                                        "scannedLink" +
                                        fileSystem.id +
                                        file.fileSystemFullPath
                                      }
                                    >
                                      <TableCell component="th" scope="row">
                                        {file.fileSystemFullPath}
                                      </TableCell>
                                      <TableCell align="right">
                                        {file.type}
                                      </TableCell>
                                      <TableCell align="right">
                                        {formatFileSize(file.size)}
                                      </TableCell>
                                    </TableRow>
                                    {file.type === "folder" &&
                                      file.content.map((childFile) => {
                                        if (
                                          !fileSystem.checkedNfsLinkMessages[
                                            childFile.fileSystemFullPath
                                          ]
                                        ) {
                                          return (
                                            <TableRow
                                              key={
                                                "scannedLink" +
                                                fileSystem.id +
                                                childFile.fileSystemFullPath
                                              }
                                            >
                                              <TableCell
                                                component="th"
                                                scope="row"
                                              >
                                                {childFile.fileSystemFullPath}
                                              </TableCell>
                                              <TableCell align="right">
                                                {childFile.type}
                                              </TableCell>
                                              <TableCell align="right">
                                                {formatFileSize(childFile.size)}
                                              </TableCell>
                                            </TableRow>
                                          );
                                        }
                                      })}
                                  </>
                                );
                              }
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
              <Button
                onClick={() => setOpen(false)}
                color="primary"
                data-test-id="button-loading-scan-ok"
              >
                OK
              </Button>
            </DialogActions>
          </>
        )}
      </Dialog>
    </Card>
  );
}
