"use strict";
import React from "react";
import update from "immutability-helper";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Tooltip from "@mui/material/Tooltip";
import IconButton from "@mui/material/IconButton";
import Chip from "@mui/material/Chip";
import List from "@mui/material/List";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faCaretLeft,
  faCaretRight,
  faAngleDoubleLeft,
  faFolderOpen,
  faFolder,
  faBook,
  faFile,
  faTimes,
} from "@fortawesome/free-solid-svg-icons";
library.add(
  faCaretLeft,
  faCaretRight,
  faAngleDoubleLeft,
  faFolderOpen,
  faFolder,
  faBook,
  faFile,
  faTimes
);

import RootFolder from "./RootFolder";

class FilePicker extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      dialogOpen: false,
      selectedFiles: props.term,
      globalSelect: props.term,
      fromWorkspace: true,
    };
  }

  openDialog = () => {
    if (this.state.fromWorkspace) {
      this.setState(
        {
          selectedFiles: [],
          globalSelect: [],
          fromWorkspace: false,
        },
        this.propagateChange
      );
    }
    this.setState({ dialogOpen: true });
  };

  closeDialog = () => {
    this.setState({ dialogOpen: false });
  };

  confirmChoices = () => {
    this.setState(
      { dialogOpen: false, selectedFiles: this.state.globalSelect },
      this.propagateChange
    );
  };

  removeFile = (file_id) => {
    let selected = update(this.state.selectedFiles, {
      $splice: [[this.state.selectedFiles.indexOf(file_id), 1]],
    });

    this.setState(
      {
        selectedFiles: selected,
        globalSelect: selected,
      },
      this.propagateChange
    );
  };

  propagateChange = () => {
    this.props.updateSelected(this.state.globalSelect);
  };

  updateSelectedFiles = (files) => {
    this.setState({ globalSelect: files });
  };

  recordIcon = (file_id) => {
    let icon = "folder";
    if (file_id.startsWith("SD")) icon = "file";
    else if (file_id.startsWith("NB")) icon = "book";

    return <FontAwesomeIcon icon={icon} style={{ padding: "10px" }} />;
  };

  render() {
    return (
      <>
        {this.state.selectedFiles.map((f) => (
          <Chip
            style={{ marginRight: "10px" }}
            key={f}
            icon={this.recordIcon(f)}
            label={f}
            clickable
            variant="outlined"
            onDelete={() => this.removeFile(f)}
            deleteIcon={
              <FontAwesomeIcon
                icon="times"
                style={{ padding: "10px" }}
                data-test-id={`delete-record-${f}`}
              />
            }
            data-test-id={`selected-record-${f}`}
          />
        ))}
        <Tooltip title="Pick files">
          <IconButton
            onClick={this.openDialog}
            data-test-id={`open-record-select`}
          >
            <FontAwesomeIcon icon="folder-open" />
          </IconButton>
        </Tooltip>
        {this.props.error && (
          <span style={{ fontSize: "1.3em" }}>
            Click on the folder icon to select records.
          </span>
        )}
        <Dialog
          open={this.state.dialogOpen}
          onClose={this.closeDialog}
          maxWidth="sm"
          fullWidth={true}
        >
          <DialogTitle id="form-dialog-title">
            {this.state.globalSelect.length} record
            {this.state.globalSelect.length != 1 ? "s" : ""} selected
          </DialogTitle>
          <DialogContent>
            <List component="nav" aria-labelledby="nested-list-subheader">
              <RootFolder
                globalSelect={this.state.selectedFiles}
                updateSelectedFiles={this.updateSelectedFiles}
              />
            </List>
          </DialogContent>
          <DialogActions>
            <Button
              onClick={this.closeDialog}
              data-test-id="close-record-select"
            >
              Cancel
            </Button>
            <Button
              onClick={this.confirmChoices}
              variant="contained"
              color="primary"
              data-test-id="confirm-record-select"
            >
              Confirm
            </Button>
          </DialogActions>
        </Dialog>
      </>
    );
  }
}

export default FilePicker;
