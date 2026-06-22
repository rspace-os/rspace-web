import { faBook } from "@fortawesome/free-solid-svg-icons/faBook";
import { faFile } from "@fortawesome/free-solid-svg-icons/faFile";
import { faFolder } from "@fortawesome/free-solid-svg-icons/faFolder";
import { faFolderOpen } from "@fortawesome/free-solid-svg-icons/faFolderOpen";
import { faTimes } from "@fortawesome/free-solid-svg-icons/faTimes";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import IconButton from "@mui/material/IconButton";
import List from "@mui/material/List";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import { produce } from "immer";
import React from "react";

import RootFolder from "./RootFolder";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
class FilePicker extends React.Component<any, any> {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  constructor(props: any) {
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
        this.propagateChange,
      );
    }
    this.setState({ dialogOpen: true });
  };

  closeDialog = () => {
    this.setState({ dialogOpen: false });
  };

  confirmChoices = () => {
    this.setState({ dialogOpen: false, selectedFiles: this.state.globalSelect }, this.propagateChange);
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  removeFile = (file_id: any) => {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    this.setState((prevState: any) => {
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      const selected = produce(prevState.selectedFiles, (draft: any) => {
        const idx = draft.indexOf(file_id);
        if (idx !== -1) {
          draft.splice(idx, 1);
        }
      });

      return {
        selectedFiles: selected,
        globalSelect: selected,
      };
    }, this.propagateChange);
  };

  propagateChange = () => {
    this.props.updateSelected(this.state.globalSelect);
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  updateSelectedFiles = (files: any) => {
    this.setState({ globalSelect: files });
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  recordIcon = (file_id: any) => {
    let icon = faFolder;
    if (file_id.startsWith("SD")) icon = faFile;
    else if (file_id.startsWith("NB")) icon = faBook;

    return <FontAwesomeIcon icon={icon} style={{ padding: "10px" }} />;
  };

  render() {
    return (
      <>
        {/** biome-ignore lint/suspicious/noExplicitAny: initial biome migration */}
        {this.state.selectedFiles.map((f: any) => (
          <Chip
            sx={{ marginRight: "10px" }}
            key={f}
            icon={this.recordIcon(f)}
            label={f}
            clickable
            variant="outlined"
            onDelete={() => this.removeFile(f)}
            deleteIcon={
              <FontAwesomeIcon icon={faTimes} style={{ padding: "10px" }} data-test-id={`delete-record-${f}`} />
            }
            data-test-id={`selected-record-${f}`}
          />
        ))}
        <Tooltip title="Pick files">
          <IconButton onClick={this.openDialog} data-test-id={`open-record-select`}>
            <FontAwesomeIcon icon={faFolderOpen} />
          </IconButton>
        </Tooltip>
        {this.props.error && (
          <Typography variant="inherit" component="span" sx={{ fontSize: "1.3em" }}>
            Click on the folder icon to select records.
          </Typography>
        )}
        <Dialog open={this.state.dialogOpen} onClose={this.closeDialog} maxWidth="sm" fullWidth={true}>
          <DialogTitle id="form-dialog-title">
            {this.state.globalSelect.length} record
            {this.state.globalSelect.length !== 1 ? "s" : ""} selected
          </DialogTitle>
          <DialogContent>
            <List component="nav" aria-labelledby="nested-list-subheader">
              <RootFolder globalSelect={this.state.selectedFiles} updateSelectedFiles={this.updateSelectedFiles} />
            </List>
          </DialogContent>
          <DialogActions>
            <Button onClick={this.closeDialog} data-test-id="close-record-select">
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
