import { faAngleDoubleLeft } from "@fortawesome/free-solid-svg-icons/faAngleDoubleLeft";
import { faCaretLeft } from "@fortawesome/free-solid-svg-icons/faCaretLeft";
import { faCaretRight } from "@fortawesome/free-solid-svg-icons/faCaretRight";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Grid from "@mui/material/Grid";
import IconButton from "@mui/material/IconButton";
import Snackbar from "@mui/material/Snackbar";
import SnackbarContent from "@mui/material/SnackbarContent";
import { produce } from "immer";
import React from "react";
import axios from "@/common/axios";
import AlertContext, { mkAlert } from "@/stores/contexts/Alert";
import UserListComponent from "./UserList";

// UserList is an untyped JS->TSX class component; cast at the call site to
// accept the props it consumes without changing behavior.
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
const UserList = UserListComponent as any;

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

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
class MyLabGroupsDialog extends React.Component<any, any> {
  declare context: React.ContextType<typeof AlertContext>;

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  constructor(props: any) {
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

    const data = new FormData();
    const config = {
      headers: { "Content-Type": "multipart/form-data", responseType: "text" },
    };
    data.append("_memberString", "1");
    data.append("Go", "Go");
    data.append("Admins", "");
    data.append("pis", "");
    data.append("id", this.props.groupId);
    data.append("uniqueName", this.props.groupUniqueId);
    data.append("groupType", this.props.groupType);
    for (let i = 0; i < this.state.chosenUsers.length; i++) {
      data.append("memberString", this.state.chosenUsers[i].username);
    }

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
        console.error(`couldn't add users: ${error}`);
        this.context.addAlert(
          mkAlert({
            message:
              "Unable to add/invite the user(s). Please check browser console for error details, and contact support if the problem persists.",
            variant: "error",
            isInfinite: true,
          }),
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
      .catch((_error) => {
        this.setState({ useSearch: true });
      });
  }

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  searchUsers(term: any) {
    if (!this.state.useSearch) {
    } else if (term.length < 3) {
      this.setState({
        userList: [],
        availableUsers: [],
      });
    } else {
      axios.get(`/cloud/ajax/searchPublicUserInfoList?term=${term}`).then((response) => {
        this.setState({
          userList: response.data.data,
          availableUsers: response.data.data,
        });
      });
    }
  }

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  findUser = (array: any, user: any) => {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    return array.findIndex((selected: any) => selected.username === user.username);
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleSelected = (action: any) => (user: any) => {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    this.setState((prevState: any) => ({
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      selectedUsers: produce(prevState.selectedUsers, (draft: any) => {
        const idx = this.findUser(draft[action], user);
        if (idx === -1) {
          draft[action].push(user);
        } else {
          draft[action].splice(idx, 1);
        }
      }),
    }));
  };

  addUsers = () => {
    this.setState(
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      (prevState: any) => ({
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        chosenUsers: produce(prevState.chosenUsers, (draft: any) => {
          draft.push(...prevState.selectedUsers.add);
        }),
      }),
      this.updateAvailableUsers,
    );
  };

  removeUsers = () => {
    this.setState(
      {
        chosenUsers: this.state.chosenUsers.filter(
          // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
          (user: any) =>
            // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
            this.state.selectedUsers.remove.findIndex((selected: any) => selected.username === user.username) === -1,
        ),
      },
      this.updateAvailableUsers,
    );
  };

  updateAvailableUsers = () => {
    this.setState(
      {
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        availableUsers: this.state.userList.filter((user: any) => this.findUser(this.state.chosenUsers, user) === -1),
      },
      // @ts-expect-error pragmatic jsx->tsx conversion: preserves original immediate-invoke behavior
      this.resetSelection(),
    );
  };

  resetUsers = () => {
    this.setState(
      {
        chosenUsers: [],
        availableUsers: this.state.userList,
      },
      this.resetSelection,
    );
  };

  resetSelection = () => {
    this.setState({
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      selectedUsers: produce(this.state.selectedUsers, (draft: any) => {
        draft.add = [];
        draft.remove = [];
      }),
    });
  };

  submitButtonTitle = () => {
    // sysadmin/community admin can directly add other people to a group
    if (this.props.role === "admin") return "Add";

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

  render() {
    return (
      <div>
        <Dialog open={this.state.dialogOpen} onClose={this.closeDialog} maxWidth="md" fullWidth={true}>
          <DialogTitle id="form-dialog-title">{this.submitButtonTitle()} members</DialogTitle>
          <DialogContent>
            <Grid container direction="row" sx={{ alignItems: "center" }}>
              <Grid size={5}>
                <UserList
                  searchUsers={this.searchUsers}
                  users={this.state.availableUsers}
                  selected={this.state.selectedUsers.add}
                  handleSelect={this.handleSelected("add")}
                  listTitle="Available users"
                />
              </Grid>
              <Grid size={2}>
                <Box
                  sx={{
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "center",
                    justifyContent: "center",
                    padding: "0px 20px",
                  }}
                >
                  <IconButton
                    sx={{ width: 48 }}
                    disabled={!this.state.selectedUsers.add.length}
                    onClick={this.addUsers}
                    data-test-id="button-add-selected"
                  >
                    <FontAwesomeIcon icon={faCaretRight} />
                  </IconButton>
                  <IconButton
                    sx={{ width: 48 }}
                    disabled={!this.state.selectedUsers.remove.length}
                    onClick={this.removeUsers}
                    data-test-id="button-remove-selected"
                  >
                    <FontAwesomeIcon icon={faCaretLeft} />
                  </IconButton>
                  <IconButton
                    sx={{ width: 48 }}
                    disabled={!this.state.chosenUsers.length}
                    onClick={this.resetUsers}
                    data-test-id="button-remove-all"
                  >
                    <FontAwesomeIcon icon={faAngleDoubleLeft} />
                  </IconButton>
                </Box>
              </Grid>
              <Grid size={5}>
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
            <Button onClick={this.closeDialog} data-test-id="button-close-dialog">
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
            {...({
              onClose: this.closeUsersAddedToast,
              message: this.state.usersAddedMsg,
              sx: { backgroundColor: "#4CAF50", color: "white" },
              // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
            } as any)}
          />
        </Snackbar>
      </div>
    );
  }
}

MyLabGroupsDialog.contextType = AlertContext;

export default MyLabGroupsDialog;
