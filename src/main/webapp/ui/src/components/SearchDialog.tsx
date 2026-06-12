import SettingsOverscanIcon from "@mui/icons-material/SettingsOverscan";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import Grow from "@mui/material/Grow";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
// biome-ignore lint/style/useImportType: initial biome migration
import React, { useState } from "react";
import docLinks from "../assets/DocLinks";
import IconButtonWithTooltip from "./IconButtonWithTooltip";
import SubmitSpinnerButton from "./SubmitSpinnerButton";

type TextAreaDialogArgs = {
  query: string;
  onSubmit: () => void;
  setQuery: (event: { target: { value: string } }) => void;
  visible: boolean;
};

export default function TextAreaDialog({ onSubmit, setQuery, query, visible }: TextAreaDialogArgs): React.ReactNode {
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
          size="small"
        />
      </Grow>
      <Dialog open={dialogOpen} onClose={onClose}>
        <DialogTitle>Search query</DialogTitle>
        <DialogContent>
          <Stack spacing={2}>
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
            <Box>
              <DialogContentText>
                Tip: Create powerful Lucene queries by prefixing your query with{" "}
                <Typography
                  variant="inherit"
                  component="samp"
                  sx={{ bgcolor: "#eee", borderRadius: "3px", p: "1px 2px" }}
                >
                  l:
                </Typography>
              </DialogContentText>
              <DialogContentText>
                For more information, see{" "}
                <a href={docLinks.luceneSyntax} rel="noreferrer" target="_blank">
                  advanced search
                </a>{" "}
                and the related{" "}
                <a href="https://lucene.apache.org/core/2_9_4/queryparsersyntax.html" rel="noreferrer" target="_blank">
                  Apache page
                </a>
                .
              </DialogContentText>
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setQuery({ target: { value: "" } })}>Clear</Button>
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
