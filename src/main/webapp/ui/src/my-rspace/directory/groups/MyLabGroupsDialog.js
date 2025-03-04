"use strict";
import React from "react";
import update from "immutability-helper";
import UserList from "./UserList";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import IconButton from "@mui/material/IconButton";
import styled from "@emotion/styled";
import Snackbar from "@mui/material/Snackbar";
import SnackbarContent from "@mui/material/SnackbarContent";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faCaretLeft,
  faCaretRight,
  faAngleDoubleLeft,
} from "@fortawesome/free-solid-svg-icons";
import axios from "@/common/axios";
import Grid from "@mui/material/Grid";
library.add(faCaretLeft, faCaretRight, faAngleDoubleLeft);

const Actions = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0px 20px;

  button {
    width: 48px;
  }
`;

const DEFAULT_STATE = {
  userList: [],
  selectedUsers: { add: [], remove: [] },
  availableUsers: [],
  chosenUsers: [],
  dialogOpen: false,
  usersAddedToast: false,
  usersAddedMsg: "",
  useSearch: false,
};

class MyLabGroupsDialog extends React.Component {
  constructor(props) {
    super(props);
    this.state = DEFAULT_STATE;

    this.searchUsers = this.searchUsers.bind(this);
  }

  openDialog() {
    this.getUsers();
    this.setState({ dialogOpen: true });
  }

  closeDialog = () => {
    this.resetDialog();
  };

  resetDialog = () => {
    this.setState(DEFAULT_STATE);
  };

  submitForm = () => {
    const url = "/groups/admin/addUser";

    let data = new FormData();
    const config = {
      headers: { "Content-Type": "multipart/form-data", responseType: "text" },
    };
    data.append("_memberString", 1);
    data.append("Go", "Go");
    data.append("Admins", "");
    data.append("pis", "");
    data.append("id", this.props.groupId);
    data.append("uniqueName", this.props.groupUniqueId);
    data.append("groupType", this.props.groupType);
    for (let i = 0; i < this.state.chosenUsers.length; i++) {
      data.append("memberString", this.state.chosenUsers[i].username);
    }

    axios.defaults.headers.common["X-Requested-With"] = "XMLHttpRequest";
    axios
      .post(url, data, config)
      .then((msg) => {
        this.resetDialog();
        this.setState({
          usersAddedToast: true,
          usersAddedMsg: msg.data,
          chosenUsers: [],
        });
      })
      .catch((error) => {
        console.error("couldn't add users: " + error);
        RS.confirm(
            "Unable to add/invite the user(s). Please check browser console for error details, and contact support if the problem persists.",
            "error",
            "infinite"
        );
      });
  };

  getUsers() {
    const url = `/groups/ajax/invitableUsers/${this.props.groupId}`;

    axios
      .get(url)
      .then((response) => {
        this.setState({
          userList: response.data.data,
          availableUsers: response.data.data,
        });
      })
      .catch((error) => {
        this.setState({ useSearch: true });
      });
  }

  searchUsers(term) {
    if (!this.state.useSearch) {
    } else if (term.length < 3) {
      this.setState({
        userList: [],
        availableUsers: [],
      });
    } else {
      axios
        .get(`/cloud/ajax/searchPublicUserInfoList?term=${term}`)
        .then((response) => {
          this.setState({
            userList: response.data.data,
            availableUsers: response.data.data,
          });
        });
    }
  }

  findUser = (array, user) => {
    return array.findIndex((selected) => selected.username == user.username);
  };

  handleSelected = (action) => (user) => {
    let idx = this.findUser(this.state.selectedUsers[action], user),
      new_selected;
    if (idx == -1) {
      new_selected = update(this.state.selectedUsers, {
        [action]: { $push: [user] },
      });
    } else {
      new_selected = update(this.state.selectedUsers, {
        [action]: { $splice: [[idx, 1]] },
      });
    }
    this.setState({ selectedUsers: new_selected });
  };

  addUsers = () => {
    this.setState(
      {
        chosenUsers: update(this.state.chosenUsers, {
          $push: this.state.selectedUsers.add,
        }),
      },
      this.updateAvailableUsers
    );
  };

  removeUsers = () => {
    this.setState(
      {
        chosenUsers: this.state.chosenUsers.filter(
          (user) =>
            this.state.selectedUsers.remove.findIndex(
              (selected) => selected.username == user.username
            ) == -1
        ),
      },
      this.updateAvailableUsers
    );
  };

  updateAvailableUsers = () => {
    this.setState(
      {
        availableUsers: this.state.userList.filter(
          (user) => this.findUser(this.state.chosenUsers, user) == -1
        ),
      },
      this.resetSelection()
    );
  };

  resetUsers = () => {
    this.setState(
      {
        chosenUsers: [],
        availableUsers: this.state.userList,
      },
      this.resetSelection
    );
  };

  resetSelection = () => {
    this.setState({
      selectedUsers: update(this.state.selectedUsers, {
        add: { $set: [] },
        remove: { $set: [] },
      }),
    });
  };

  submitButtonTitle = () => {
    // sysadmin/community admin can directly add other people to a group
    if (this.props.role == "admin") return "Add";

    // PIs, as admins of LabGroups, or regular users, as owners of Project
    // Groups, can invite others to their respective groups.
    return "Invite";
  };

  closeUsersAddedToast = () => {
    if (this.state.usersAddedToast) {
      // need to re-check, as Snackbar autohide sometimes triggers without toast being displayed
      this.setState({ usersAddedToast: false });
      window.location.reload();
    }
  };

  styles = {
    toastSuccess: {
      backgroundColor: "#4CAF50",
      color: "white",
    },
  };

  render() {
    return (
      <div>
        <Dialog
          open={this.state.dialogOpen}
          onClose={this.closeDialog}
          maxWidth="md"
          fullWidth={true}
        >
          <DialogTitle id="form-dialog-title">
            {this.submitButtonTitle()} members
          </DialogTitle>
          <DialogContent>
            <Grid container direction="row" alignItems="center">
              <Grid item xs={5}>
                <UserList
                  searchUsers={this.searchUsers}
                  users={this.state.availableUsers}
                  selected={this.state.selectedUsers.add}
                  handleSelect={this.handleSelected("add")}
                  listTitle="Available users"
                />
              </Grid>
              <Grid item xs={2}>
                <Actions>
                  <IconButton
                    disabled={!this.state.selectedUsers.add.length}
                    onClick={this.addUsers}
                    data-test-id="button-add-selected"
                  >
                    <FontAwesomeIcon icon="caret-right" />
                  </IconButton>
                  <IconButton
                    disabled={!this.state.selectedUsers.remove.length}
                    onClick={this.removeUsers}
                    data-test-id="button-remove-selected"
                  >
                    <FontAwesomeIcon icon="caret-left" />
                  </IconButton>
                  <IconButton
                    disabled={!this.state.chosenUsers.length}
                    onClick={this.resetUsers}
                    data-test-id="button-remove-all"
                  >
                    <FontAwesomeIcon icon="angle-double-left" />
                  </IconButton>
                </Actions>
              </Grid>
              <Grid item xs={5}>
                <UserList
                  searchUsers={this.searchUsers}
                  users={this.state.chosenUsers}
                  selected={this.state.selectedUsers.remove}
                  handleSelect={this.handleSelected("remove")}
                  listTitle="Selected users"
                />
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button
              onClick={this.closeDialog}
              data-test-id="button-close-dialog"
            >
              Cancel
            </Button>
            <Button
              disabled={!this.state.chosenUsers.length}
              onClick={this.submitForm}
              variant="contained"
              color="primary"
              data-test-id="button-submit"
            >
              {this.submitButtonTitle()}
            </Button>
          </DialogActions>
        </Dialog>
        <Snackbar
          anchorOrigin={{
            vertical: "top",
            horizontal: "right",
          }}
          open={this.state.usersAddedToast}
          autoHideDuration={5000}
          onClose={this.closeUsersAddedToast}
        >
          <SnackbarContent
            onClose={this.closeUsersAddedToast}
            message={this.state.usersAddedMsg}
            style={this.styles.toastSuccess}
          />
        </Snackbar>
      </div>
    );
  }
}

export default MyLabGroupsDialog;
