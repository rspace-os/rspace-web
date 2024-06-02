//@flow

import React, { type Node, useState } from "react";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import FileStoreLogin from "../FileStoreLogin";
import { type FileSystem } from "../ExportFileStore";

type LoginStatusArgs = {|
  // The filesystems to which linked files belong
  fileSystems: Array<FileSystem>,

  /*
   * This ia a function that is passed to FileStoreLogin as `callBack`. When
   * FileStoreLogin determines that the user has successfully logged into the
   * filestore, ExportFileStore can fetch the QuickExportPlan.
   */
  fileStoreCheck: () => void,
|};

/*
 * This component displays a status of whether the user is logged into all of
 * the accounts that authorise us to fetch the data from the filestores to
 * which the linked files belong. It also displays a button that opens a
 * dialog that for each filestore it either details the accounts if the user is
 * logged in or otherwise provides them with a mechanism for logging in.
 */
export default function LoginStatus({
  fileSystems,
  fileStoreCheck,
}: LoginStatusArgs): Node {
  const [open, setOpen] = useState(false);
  const loggedOutCount = fileSystems.filter((fs) => fs.loggedAs === null).length;

  return (
    <Card sx={{ p: 1 }}>
      <h2>File Systems that require login</h2>
      {loggedOutCount === 0 && (
        <p>
          You are logged into all File Systems referenced by filestore links.
        </p>
      )}
      {loggedOutCount > 0 && (
        <p>
          You are not logged into all File Systems referenced by filestore
          links. Please login to remaining File Systems or some linked files may
          be omitted during the export.
        </p>
      )}
      {loggedOutCount > 0 ? (
        <Button
          variant="contained"
          color="primary"
          onClick={() => setOpen(true)}
          data-test-id="button-login"
          fullWidth
        >
          Login to remaining File Systems
        </Button>
      ) : (
        <Button
          variant="outlined"
          color="primary"
          onClick={() => setOpen(true)}
          data-test-id="button-login-details"
          fullWidth
        >
          Check File Systems login details
        </Button>
      )}

      <Dialog onClose={() => setOpen(false)} open={open}>
        <DialogTitle>File Systems login status</DialogTitle>
        <DialogContent>
          {fileSystems.map((fileStore) => (
            <div key={"filestoreLogin" + fileStore.id}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>File System Name</TableCell>
                    <TableCell align="right">Logged in as</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  <TableRow>
                    <TableCell component="th" scope="row">
                      {fileStore.name}
                    </TableCell>
                    <TableCell
                      align="right"
                      style={
                        fileStore.loggedAs === null
                          ? {
                              background: "#f44336",
                              color: "#ffffff",
                            }
                          : { background: "none" }
                      }
                    >
                      {fileStore.loggedAs === null
                        ? "Not logged in"
                        : fileStore.loggedAs}
                    </TableCell>
                  </TableRow>
                </TableBody>
              </Table>
              {fileStore.loggedAs === null && (
                <FileStoreLogin
                  fileSystemId={fileStore.id}
                  callBack={fileStoreCheck}
                  hideCancelButton={true}
                />
              )}
            </div>
          ))}
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => setOpen(false)}
            color="primary"
            data-test-id="button-ok-login"
          >
            OK
          </Button>
        </DialogActions>
      </Dialog>
    </Card>
  );
}
