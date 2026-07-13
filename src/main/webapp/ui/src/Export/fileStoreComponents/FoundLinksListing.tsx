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

type FoundLinksListingArgs = {
  // The total number of files found across all of the filestores
  filesCount: number;

  // The filesystems to which linked files belong
  fileSystems: Array<FileSystem>;
};

/**
 * This component displays a card that shows some summary information
 * about the links to files in filestores that have been found as
 * well as a dialog that the user can open that displays more
 * detailed information.
 */
export default function FoundLinksListing({ filesCount, fileSystems }: FoundLinksListingArgs): React.ReactNode {
  const { t } = useTranslation(["workspace", "common"]);
  const [open, setOpen] = useState(false);

  return (
    <Card sx={{ p: 1 }}>
      <h2>{t("export.fileStore.foundLinks.heading")}</h2>
      <p>{t("export.fileStore.foundLinks.summary", { filesCount, fileSystemsCount: fileSystems.length })}</p>

      <Button variant="outlined" color="primary" onClick={() => setOpen(true)} fullWidth>
        {t("export.fileStore.foundLinks.showButton")}
      </Button>
      <Dialog onClose={() => setOpen(false)} open={open}>
        <DialogTitle>{t("export.fileStore.foundLinks.heading")}</DialogTitle>
        <DialogContent>
          {fileSystems.map((fileStore) => (
            <div key={`foundLinks${fileStore.id}`}>
              <h3>{fileStore.name}</h3>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>{t("export.fileStore.foundLinks.columns.path")}</TableCell>
                    <TableCell align="right">{t("export.fileStore.foundLinks.columns.linkType")}</TableCell>
                    <TableCell align="right">{t("export.fileStore.foundLinks.columns.notes")}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {fileStore.foundNfsLinks.map((file) => (
                    <TableRow key={`foundLinks${fileStore.id}${file.path}`}>
                      <TableCell component="th" scope="row">
                        {file.path}
                      </TableCell>
                      <TableCell align="right">{file.linkType}</TableCell>
                      <TableCell align="right"></TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          ))}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)} color="primary" data-test-id="button-ok-links">
            {t("common:actions.ok")}
          </Button>
        </DialogActions>
      </Dialog>
    </Card>
  );
}
