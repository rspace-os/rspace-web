"use strict";
import React, { useEffect } from "react";
import Button from "@mui/material/Button";
import TextField from "@mui/material/TextField";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import SubmitSpinnerButton from "../../../components/SubmitSpinnerButton";

export default function NewNotebook() {
  const [open, setOpen] = React.useState(false);
  const [name, setName] = React.useState("");
  const [error, setError] = React.useState(null);
  const [loading, setLoading] = React.useState(false);

  useEffect(() => {
    $(document).on("click", "#createNotebook", () => {
      setOpen(true);
    });
  }, []);

  function validateForm(e) {
    e.preventDefault();
    if (name.trim().length === 0 || name.match(/\//)) {
      setError("Please enter a name - excluding '/' characters.");
    } else {
      setError(null);
      handleSubmit();
    }
  }

  function handleSubmit() {
    setLoading(true);
    let form = $("<form></form>");
    form.attr("method", "POST");
    form.attr(
      "action",
      "/workspace/create_notebook/" + workspaceSettings.parentFolderId,
    );
    form.append(
      $(`<input name="notebookNameField" value="${name}"/>`).attr(
        "type",
        "hidden",
      ),
    );
    $("body").append(form);
    form.submit();
    RS.trackEvent("user:create:notebook:workspace");
  }

  const focusUsernameInputField = (input) => {
    if (input) {
      setTimeout(() => input.focus(), 100);
    }
  };

  return (
    <Dialog
      open={open}
      onClose={() => setOpen(false)}
      aria-labelledby="form-dialog-title"
      fullWidth
    >
      <DialogTitle id="form-dialog-title">Create a notebook</DialogTitle>
      <DialogContent>
        <form onSubmit={validateForm}>
          <FormControl error={error != null} fullWidth>
            <TextField
              variant="standard"
              inputRef={focusUsernameInputField}
              margin="dense"
              placeholder="New notebook name"
              fullWidth
              value={name}
              onChange={(e) => setName(e.target.value)}
              aria-describedby="component-error-text"
              data-test-id="new-notebook-name"
              inputProps={{ "aria-label": "New notebook name" }}
            />
            <FormHelperText id="component-error-text">{error}</FormHelperText>
          </FormControl>
        </form>
      </DialogContent>
      <DialogActions>
        <Button
          onClick={() => setOpen(false)}
          style={{ color: "grey" }}
          data-test-id="new-notebook-cancel"
        >
          Cancel
        </Button>
        <SubmitSpinnerButton
          label="Create"
          loading={loading}
          disabled={loading}
          onClick={validateForm}
        />
      </DialogActions>
    </Dialog>
  );
}
