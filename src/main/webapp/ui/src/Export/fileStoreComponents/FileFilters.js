//@flow

import React, { type Node, useState } from "react";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Grid from "@mui/material/Grid";
import TextField from "@mui/material/TextField";

type FoundLinksListingArgs = {|
  maxFileSizeInMB: number,
  setMaxFileSizeInMB: (number) => void,

  // comma separated string of extensions
  excludedFileExtensions: string,
  setExcludedFileExtensions: (string) => void,
|};

/*
 * This component allows the user to filter the files from the filestore that
 * will be included in the export, either based on their size or their
 * extension.
 */
export default function FileFilters({
  maxFileSizeInMB,
  setMaxFileSizeInMB,
  excludedFileExtensions,
  setExcludedFileExtensions,
}: FoundLinksListingArgs): Node {
  const [open, setOpen] = useState(false);

  return (
    <Card sx={{ p: 1 }}>
      <h2>File filters</h2>
      <p>
        Filestore files larger than <strong>{maxFileSizeInMB} MB</strong> will
        not be included in the export. All types of filestore files will be
        included
        {excludedFileExtensions === "" ? (
          ""
        ) : (
          <span>
            {" "}
            apart from the following <strong>{excludedFileExtensions}</strong>
          </span>
        )}
        .
      </p>
      <Button
        variant="outlined"
        color="primary"
        onClick={() => setOpen(true)}
        data-test-id="button-change-filters"
        fullWidth
      >
        Change file filtering options
      </Button>
      <Dialog onClose={() => setOpen(false)} open={open}>
        <DialogTitle>Filtering options for filestore files</DialogTitle>
        <DialogContent>
          <Grid container>
            <Grid item xs={12}>
              <TextField
                variant="standard"
                label="Individual file size limit (MB)"
                value={maxFileSizeInMB}
                onChange={({ target: { value } }) => setMaxFileSizeInMB(value)}
                type="number"
                InputLabelProps={{
                  shrink: true,
                }}
                margin="normal"
                data-test-id="file-size-limit"
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                variant="standard"
                label="File types to exclude (comma-separated list)"
                value={excludedFileExtensions}
                onChange={({ target: { value } }) =>
                  setExcludedFileExtensions(value)
                }
                InputLabelProps={{
                  shrink: true,
                }}
                margin="normal"
                data-test-id="types-to-exclude"
              />
            </Grid>
          </Grid>
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
