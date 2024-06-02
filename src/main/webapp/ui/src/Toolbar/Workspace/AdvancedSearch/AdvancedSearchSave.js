"use strict";
import React from "react";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../theme";
import Button from "@mui/material/Button";
import TextField from "@mui/material/TextField";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControlLabel from "@mui/material/FormControlLabel";
import axios from "axios";
import update from "immutability-helper";
import styled from "@emotion/styled";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faPlus,
  faTrashAlt,
  faSave,
  faTimes,
} from "@fortawesome/free-solid-svg-icons";
library.add(faPlus, faTrashAlt, faSave, faTimes);

const DEFAULT_STATE = {
  loading: false,
  label: "",
  open: false,
};

const SearchRow = styled.tr`
  td {
    padding: 0px 0px 10px 0px;
  }
  .search-type {
    width: 150px;
  }
  .search-term {
    padding-left: 10px;
    padding-right: 10px;
  }
  .actions {
    width: 100px;
  }
  .MuiInputBase-root {
    width: 100%;
  }
  .MuiInputBase-root {
    input:hover,
    input:active {
      background-color: transparent !important;
    }
  }
  .MuiButtonBase-root {
    width: 39px;
    font-size: 15px;
    margin-left: 10px;
  }
`;

class AdvancedSearchSave extends React.Component {
  constructor(props) {
    super(props);
    this.state = DEFAULT_STATE;
  }

  saveSearch = () => {};

  openDialog = () => {
    this.setState({ open: true });
  };

  closeDialog = () => {
    this.setState({ open: false });
  };

  reset = () => {
    this.setState(DEFAULT_STATE);
  };

  searchQueries = () => {
    return <SearchRow></SearchRow>;
  };

  render() {
    return (
      <StyledEngineProvider injectFirst>
        <ThemeProvider theme={materialTheme}>
          <Button onClick={this.openDialog}>
            <FontAwesomeIcon icon="save" />
            <span>Save search query</span>
          </Button>
          <Dialog
            open={this.state.open}
            onClose={this.closeDialog}
            fullWidth={true}
            maxWidth="sm"
          >
            <DialogTitle id="form-dialog-title">
              Save search for later
            </DialogTitle>
            <DialogContent>
              <TextField
                variant="standard"
                autoFocus
                margin="dense"
                label="Search label"
                fullWidth
              />
            </DialogContent>
            <DialogActions>
              <Button onClick={this.closeDialog}>Cancel</Button>
              <Button
                onClick={this.saveSearch}
                variant="contained"
                color="primary"
              >
                Save
              </Button>
            </DialogActions>
          </Dialog>
        </ThemeProvider>
      </StyledEngineProvider>
    );
  }
}

export default AdvancedSearchSave;
