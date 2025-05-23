import SettingsOverscanIcon from "@mui/icons-material/SettingsOverscan";
import React, { useState } from "react";
import Grow from "@mui/material/Grow";
import IconButtonWithTooltip from "./IconButtonWithTooltip";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import TextField from "@mui/material/TextField";
import SubmitSpinnerButton from "./SubmitSpinnerButton";
import docLinks from "../assets/DocLinks";
import { withStyles } from "Styles";

type TextAreaDialogArgs = {
  query: string;
  onSubmit: () => void;
  setQuery: (event: { target: { value: string } }) => void;
  visible: boolean;
};

const Samp = withStyles<{ children: React.ReactNode }, { root: string }>(
  (theme) => ({
    root: {
      backgroundColor: "#eee",
      borderRadius: 3,
      padding: theme.spacing(0.125, 0.25),
    },
  })
)(({ classes, children }) => <samp className={classes.root}>{children}</samp>);

export default function TextAreaDialog({
  onSubmit,
  setQuery,
  query,
  visible,
}: TextAreaDialogArgs): React.ReactNode {
  const [dialogOpen, setDialogOpen] = useState(false);
  const onClose = () => setDialogOpen(false);
  return visible ? (
    <>
      <Grow in={visible}>
        <IconButtonWithTooltip
          title="Expand field"
          icon={<SettingsOverscanIcon />}
          onClick={() => {
            setDialogOpen(true);
          }}
        />
      </Grow>
      <Dialog open={dialogOpen} onClose={onClose}>
        <DialogTitle>Search query</DialogTitle>
        <DialogContent>
          <Grid container direction="column" spacing={2}>
            <Grid item>
              <TextField
                onChange={setQuery}
                onKeyPress={(e) => {
                  /*
                   * Prevent the enter key because whilst we are giving more
                   * vertical space for the text to wrap, the search query must
                   * still be a single line of text.
                   */
                  if (e.key === "Enter") e.preventDefault();
                }}
                value={query}
                multiline
                fullWidth
                rows={6}
              />
            </Grid>
            <Grid item>
              <DialogContentText>
                Tip: Create powerful Lucene queries by prefixing your query with{" "}
                <Samp>l:</Samp>
              </DialogContentText>
              <DialogContentText>
                For more information, see{" "}
                <a
                  href={docLinks.luceneSyntax}
                  rel="noreferrer"
                  target="_blank"
                >
                  advanced search
                </a>{" "}
                and the related{" "}
                <a
                  href="https://lucene.apache.org/core/2_9_4/queryparsersyntax.html"
                  rel="noreferrer"
                  target="_blank"
                >
                  Apache page
                </a>
                .
              </DialogContentText>
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setQuery({ target: { value: "" } })}>
            Clear
          </Button>
          <Button onClick={onClose}>Close</Button>
          <SubmitSpinnerButton
            loading={false}
            disabled={false}
            onClick={() => {
              onSubmit();
              onClose();
            }}
            label="Search"
          />
        </DialogActions>
      </Dialog>
    </>
  ) : null;
}
