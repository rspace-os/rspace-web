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
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { FileSystem } from "../common";
import FileStoreLogin from "../FileStoreLogin";

type LoginStatusArgs = {
  // The filesystems to which linked files belong
  fileSystems: Array<FileSystem>;

  /*
   * This ia a function that is passed to FileStoreLogin as `callBack`. When
   * FileStoreLogin determines that the user has successfully logged into the
   * filestore, ExportFileStore can fetch the QuickExportPlan.
   */
  fileStoreCheck: () => void;
};

/**
 * This component displays a status of whether the user is logged into all of
 * the accounts that authorise us to fetch the data from the filestores to
 * which the linked files belong. It also displays a button that opens a
 * dialog that for each filestore it either details the accounts if the user is
 * logged in or otherwise provides them with a mechanism for logging in.
 */
export default function LoginStatus({ fileSystems, fileStoreCheck }: LoginStatusArgs): React.ReactNode {
  const { t } = useTranslation(["workspace", "common"]);
  const [open, setOpen] = useState(false);
  const loggedOutCount = fileSystems.filter((fs) => fs.loggedAs === null).length;

  return (
    <Card sx={{ p: 1 }}>
      <h2>{t("export.fileStore.login.heading")}</h2>
      {loggedOutCount === 0 && <p>{t("export.fileStore.login.allLoggedIn")}</p>}
      {loggedOutCount > 0 && <p>{t("export.fileStore.login.notAllLoggedIn")}</p>}
      {loggedOutCount > 0 ? (
        <Button variant="contained" color="primary" onClick={() => setOpen(true)} data-test-id="button-login" fullWidth>
          {t("export.fileStore.login.loginButton")}
        </Button>
      ) : (
        <Button
          variant="outlined"
          color="primary"
          onClick={() => setOpen(true)}
          data-test-id="button-login-details"
          fullWidth
        >
          {t("export.fileStore.login.checkButton")}
        </Button>
      )}

      <Dialog onClose={() => setOpen(false)} open={open}>
        <DialogTitle>{t("export.fileStore.login.dialogTitle")}</DialogTitle>
        <DialogContent>
          {fileSystems.map((fileStore) => (
            <div key={`filestoreLogin${fileStore.id}`}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>{t("export.fileStore.login.columns.name")}</TableCell>
                    <TableCell align="right">{t("export.fileStore.login.columns.loggedInAs")}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  <TableRow>
                    <TableCell component="th" scope="row">
                      {fileStore.name}
                    </TableCell>
                    <TableCell
                      align="right"
                      sx={
                        fileStore.loggedAs === null
                          ? {
                              background: "#f44336",
                              color: "#ffffff",
                            }
                          : { background: "none" }
                      }
                    >
                      {fileStore.loggedAs === null ? t("export.fileStore.login.notLoggedIn") : fileStore.loggedAs}
                    </TableCell>
                  </TableRow>
                </TableBody>
              </Table>
              {fileStore.loggedAs === null && (
                <FileStoreLogin fileSystemId={fileStore.id} callBack={fileStoreCheck} hideCancelButton={true} />
              )}
            </div>
          ))}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)} color="primary" data-test-id="button-ok-login">
            {t("common:actions.ok")}
          </Button>
        </DialogActions>
      </Dialog>
    </Card>
  );
}
