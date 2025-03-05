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
import { type FileSystem } from "../ExportFileStore";

type FoundLinksListingArgs = {|
  // The total number of files found across all of the filestores
  filesCount: number,

  // The filesystems to which linked files belong
  fileSystems: Array<FileSystem>,
|};

/**
 * This component displays a card that shows some summary information
 * about the links to files in filestores that have been found as
 * well as a dialog that the user can open that displays more
 * detailed information.
 */
export default function FoundLinksListing({
  filesCount,
  fileSystems,
}: FoundLinksListingArgs): Node {
  const [open, setOpen] = useState(false);

  return (
    <Card sx={{ p: 1 }}>
      <h2>Filestore links found in exported content</h2>
      <p>
        Exported content contains {filesCount} filestore link from{" "}
        {fileSystems.length} File System.
      </p>

      <Button
        variant="outlined"
        color="primary"
        onClick={() => setOpen(true)}
        fullWidth
      >
        Show found filestore links
      </Button>
      <Dialog onClose={() => setOpen(false)} open={open}>
        <DialogTitle>Filestore links found in exported content</DialogTitle>
        <DialogContent>
          {fileSystems.map((fileStore) => (
            <div key={"foundLinks" + fileStore.id}>
              <h3>{fileStore.name}</h3>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Full path on the File System</TableCell>
                    <TableCell align="right">Link type</TableCell>
                    <TableCell align="right">Notes</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {fileStore.foundNfsLinks.map((file) => (
                    <TableRow key={"foundLinks" + fileStore.id + file.path}>
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
          <Button
            onClick={() => setOpen(false)}
            color="primary"
            data-test-id="button-ok-links"
          >
            OK
          </Button>
        </DialogActions>
      </Dialog>
    </Card>
  );
}
