"use strict";
import React, { useEffect } from "react";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import FormControlLabel from "@mui/material/FormControlLabel";
import Checkbox from "@mui/material/Checkbox";
import TextField from "@mui/material/TextField";
import axios from "@/common/axios";

export default function NewNotebook() {
  const [open, setOpen] = React.useState(false);
  const [name, setName] = React.useState("");
  const [navigateAfterCreate, setNavigateAfterCreate] = React.useState(false);
  const [error, setError] = React.useState(null);

  useEffect(() => {
    $(document).on("click", "#createFolder", () => {
      setOpen(true);
    });
  }, []);

  function validateForm(e) {
    e.preventDefault();

    if (name.trim().length === 0) {
      setError("Please enter a name");
    } else {
      setError(null);
      handleSubmit();
    }
  }

  function handleSubmit() {
    setOpen(false);
    RS.blockPage("Creating a new folder...");

    let bodyFormData = new FormData();
    bodyFormData.set("folderNameField", name);
    setName("");

    axios({
      method: "post",
      url: `/workspace/ajax/create_folder/${workspaceSettings.parentFolderId}`,
      data: bodyFormData,
      config: { headers: { "Content-Type": "multipart/form-data" } },
    })
      .then((response) => {
        if (response.data) {
          reloadFileTreeBrowser();
          if (navigateAfterCreate) {
            workspaceSettings.parentFolderId = response.data.data;
          }
          getAndDisplayWorkspaceResults(
            `/workspace/ajax/view/${workspaceSettings.parentFolderId}`,
            workspaceSettings
          );
        } else {
          RS.apprise(
            getValidationErrorString(response.errorMsg, ";", true),
            true,
            e.target
          );
          RS.unblockPage();
        }
      })
      .catch((error) => {
        RS.unblockPage();
        RS.ajaxFailed("Display", false, error);
      });
  }

  const focusUsernameInputField = (input) => {
    if (input) {
      setTimeout(() => input.focus(), 100);
    }
  };

  return (
    <Dialog open={open} onClose={() => setOpen(false)} fullWidth>
      <DialogTitle id="form-dialog-title">Create a folder</DialogTitle>
      <DialogContent>
        <form onSubmit={validateForm}>
          <FormControl error={error != null} fullWidth>
            <TextField
              variant="standard"
              inputRef={focusUsernameInputField}
              margin="dense"
              placeholder="Enter a name, /or/a/path to create multiple folders"
              fullWidth
              value={name}
              onChange={(e) => setName(e.target.value)}
              data-test-id="new-folder-name"
              inputProps={{ "aria-label": "Folder name" }}
            />
            <FormHelperText id="component-error-text">{error}</FormHelperText>
          </FormControl>
          <FormControlLabel
            control={
              <Checkbox
                checked={navigateAfterCreate}
                onChange={(e) =>
                  setNavigateAfterCreate(e.target.value != "true")
                }
                value={navigateAfterCreate}
                color="primary"
                data-test-id="new-folder-navigate"
                inputProps={{ "aria-label": "Navgate to the created folder" }}
              />
            }
            label="Navigate to the created folder"
          />
        </form>
      </DialogContent>
      <DialogActions>
        <Button
          onClick={() => setOpen(false)}
          style={{ color: "grey" }}
          data-test-id="new-folder-cancel"
        >
          Cancel
        </Button>
        <Button
          onClick={validateForm}
          color="primary"
          data-test-id="new-folder-submit"
        >
          Create
        </Button>
      </DialogActions>
    </Dialog>
  );
}
