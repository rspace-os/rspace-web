"use strict";
import React from "react";
import TextField from "@mui/material/TextField";
import Paper from "@mui/material/Paper";
import Grid from "@mui/material/Grid";
import Chip from "@mui/material/Chip";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../theme";
import axios from "axios";
import Select from "react-select";
import EmailValidator from "email-validator";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";

class createGroupStep3 extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      existingUsers: [],
      newUsers: [],
      returnedUserList: [],
      newUserEmailCheck: true,
    };

    this.handleBlur = this.handleBlur.bind(this);
    this.handleDelete = this.handleDelete.bind(this);
  }

  handleBlur = (event) => {
    if (this.state.newUserEmailCheck === false || event.target.value === "") {
      return;
    }

    const email = event.target.value;
    const arrayName = event.target.name;

    this.setState((prevState) => ({
      [arrayName]: [...prevState[arrayName], email],
    }));

    event.target.value = "";
  };

  handleEnter = (event) => {
    if (event.key === "Enter") {
      this.handleBlur(event);
    }
  };

  handleSelect = (user) => {
    const email = user.value;
    this.setState((prevState) => ({
      existingUsers: [...prevState.existingUsers, email],
    }));
  };

  handleDelete = (name, type) => {
    const selectedUsers = [...this.state[type]];
    const result = selectedUsers.filter((word) => word !== name);

    this.setState({
      [type]: [...result],
    });
  };

  buildUserDisplay = (type) => {
    const emailList =
      type === "existingUsers" ? this.state.existingUsers : this.state.newUsers;
    let display = [];

    for (let i = 0; i < emailList.length; i++) {
      display.push(
        <Grid item xs={12} style={this.styles.chipSpacing}>
          <Chip
            label={emailList[i].slice(0, 20)}
            onDelete={() => this.handleDelete(emailList[i], type)}
            name={emailList[i]}
            value={emailList[i]}
            color="primary"
          />
        </Grid>
      );
    }

    return display;
  };

  getCurrentUsers = (value) => {
    if (value.length < 3) {
      return;
    }

    const selfService = $("#selfServiceLabGroup").length != 0;
    const projectGroup = $("#projectGroup").length != 0;
    const url =
      (selfService && !projectGroup
        ? `/selfServiceLabGroup/`
        : projectGroup
        ? /projectGroup/
        : `/cloud/ajax/`) + `searchPublicUserInfoList?term=${value}`;

    axios
      .get(url)
      .then((data) => {
        this.displayExistingUsers(data.data.data);
      })
      .catch((error) => {
        console.log(error);
        if (error.message === "Network Error") {
          console.log("No network connection");
        } else {
          console.log("Error retrieving information");
        }
      });
  };

  displayExistingUsers = (userList) => {
    const display = [];
    for (let i = 0; i < userList.length; i++) {
      display.push({
        value: userList[i].email,
        label: userList[i].email,
      });
    }
    this.setState({
      returnedUserList: [...display],
    });
  };

  emailValidation = (event) => {
    const emailTest = EmailValidator.validate(event.target.value);
    this.setState({
      newUserEmailCheck: emailTest,
    });
  };

  styles = {
    container: {
      padding: "0 25px 10px 25px",
    },
    paperContainer: {
      padding: "10px",
    },
    chipSpacing: {
      marginBottom: "5px",
    },
    selectSpacing: {
      marginTop: "36.5px",
      paddingBottom: "6px",
    },
    userDisplay: {
      overflow: "auto",
      maxHeight: "250px",
    },
  };

  componentDidMount() {
    this.setState({
      existingUsers: [...this.props.existingUsers],
      newUsers: [...this.props.newUsers],
    });
  }

  componentDidUpdate(prevProps, prevState) {
    //If the user list has changed update main state on CreateGroup.js
    if (
      this.state.existingUsers !== prevState.existingUsers ||
      this.state.newUsers !== prevState.newUsers
    ) {
      this.props.updateInvitedMembers(
        this.state.existingUsers,
        this.state.newUsers
      );
    }
  }

  render() {
    const existingUsersDisplay = this.buildUserDisplay("existingUsers");
    const selfService = $("#selfServiceLabGroup").length != 0;
    const newUsersDisplay = selfService
      ? false
      : this.buildUserDisplay("newUsers");

    return (
      <div style={this.styles.container}>
        <StyledEngineProvider injectFirst>
          <ThemeProvider theme={materialTheme}>
            <Grid container spacing={8}>
              <Grid item xs={6}>
                <Paper style={this.styles.paperContainer}>
                  <h2>Invite RSpace users</h2>
                  <div
                    style={this.styles.selectSpacing}
                    data-test-id="createGroupInviteMembers"
                  >
                    {" "}
                    <p className="bootstrap-custom-flat">
                      {" "}
                      type at least 3 characters
                    </p>
                    <Select
                      style={this.styles.selectSpacing}
                      name="existingUsers"
                      options={this.state.returnedUserList}
                      onInputChange={this.getCurrentUsers}
                      onChange={this.handleSelect}
                      placeholder="Enter email address"
                      classNamePrefix="seleniumTest"
                    />
                  </div>
                  <Grid style={this.styles.userDisplay}>
                    {existingUsersDisplay}
                  </Grid>
                </Paper>
              </Grid>
              {newUsersDisplay && (
                <Grid item xs={6}>
                  <Paper style={this.styles.paperContainer}>
                    <h2>Invite new users</h2>
                    <FormControl error aria-describedby="email-error-text">
                      <TextField
                        variant="standard"
                        data-test-id="createGroupInviteNewUsers"
                        error={!this.state.newUserEmailCheck}
                        placeholder="Enter email address"
                        name="newUsers"
                        margin="normal"
                        onChange={this.emailValidation}
                        onBlur={this.handleBlur}
                        onKeyPress={this.handleEnter}
                      />
                      {!this.state.newUserEmailCheck && (
                        <FormHelperText id="email-error-text">
                          Please enter a valid email address
                        </FormHelperText>
                      )}
                    </FormControl>
                    <Grid style={this.styles.userDisplay}>
                      {newUsersDisplay}
                    </Grid>
                  </Paper>
                </Grid>
              )}
            </Grid>
          </ThemeProvider>
        </StyledEngineProvider>
      </div>
    );
  }
}

export default createGroupStep3;
