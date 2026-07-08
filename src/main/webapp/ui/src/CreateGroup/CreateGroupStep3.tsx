import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import Grid from "@mui/material/Grid";
import Paper from "@mui/material/Paper";
import { ThemeProvider } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import EmailValidator from "email-validator";
import React from "react";
import Select from "react-select";
import axios from "@/common/axios";
import i18n from "@/modules/common/i18n";
import materialTheme from "../theme";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
class createGroupStep3 extends React.Component<any, any> {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  constructor(props: any) {
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

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleBlur = (event: any) => {
    if (this.state.newUserEmailCheck === false || event.target.value === "") {
      return;
    }

    const email = event.target.value;
    const arrayName = event.target.name;

    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    this.setState((prevState: any) => ({
      [arrayName]: [...prevState[arrayName], email],
    }));

    event.target.value = "";
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleEnter = (event: any) => {
    if (event.key === "Enter") {
      this.handleBlur(event);
    }
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleSelect = (user: any) => {
    const email = user.value;
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    this.setState((prevState: any) => ({
      existingUsers: [...prevState.existingUsers, email],
    }));
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleDelete = (name: any, type: any) => {
    const selectedUsers = [...this.state[type]];
    const result = selectedUsers.filter((word) => word !== name);

    this.setState({
      [type]: [...result],
    });
  };

  buildUserDisplay = (type: string) => {
    const emailList = type === "existingUsers" ? this.state.existingUsers : this.state.newUsers;
    const display = [];

    for (let i = 0; i < emailList.length; i++) {
      display.push(
        <Grid sx={{ marginBottom: "5px" }} size={12}>
          <Chip
            label={emailList[i].slice(0, 20)}
            onDelete={() => this.handleDelete(emailList[i], type)}
            // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
            {...({ name: emailList[i], value: emailList[i] } as any)}
            color="primary"
          />
        </Grid>,
      );
    }

    return display;
  };

  getCurrentUsers = (value: string) => {
    if (value.length < 3) {
      return;
    }

    const selfService = $("#selfServiceLabGroup").length !== 0;
    const projectGroup = $("#projectGroup").length !== 0;
    const url =
      (selfService && !projectGroup ? `/selfServiceLabGroup/` : projectGroup ? /projectGroup/ : `/cloud/ajax/`) +
      `searchPublicUserInfoList?term=${value}`;

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

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  displayExistingUsers = (userList: any) => {
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

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  emailValidation = (event: any) => {
    const emailTest = EmailValidator.validate(event.target.value);
    this.setState({
      newUserEmailCheck: emailTest,
    });
  };

  componentDidMount() {
    this.setState({
      existingUsers: [...this.props.existingUsers],
      newUsers: [...this.props.newUsers],
    });
  }

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  componentDidUpdate(_prevProps: any, prevState: any) {
    //If the user list has changed update main state on CreateGroup.js
    if (this.state.existingUsers !== prevState.existingUsers || this.state.newUsers !== prevState.newUsers) {
      this.props.updateInvitedMembers(this.state.existingUsers, this.state.newUsers);
    }
  }

  render() {
    const existingUsersDisplay = this.buildUserDisplay("existingUsers");
    const selfService = $("#selfServiceLabGroup").length !== 0;
    const newUsersDisplay = selfService ? false : this.buildUserDisplay("newUsers");

    return (
      <Box sx={{ padding: "0 25px 10px 25px" }}>
        <StyledEngineProvider injectFirst enableCssLayer>
          <ThemeProvider theme={materialTheme}>
            <Grid container spacing={8}>
              <Grid size={6}>
                <Paper sx={{ padding: "10px" }}>
                  <h2>{i18n.t("groups:createGroup.step3.inviteExistingHeading")}</h2>
                  <Box sx={{ marginTop: "36.5px", paddingBottom: "6px" }} data-test-id="createGroupInviteMembers">
                    {" "}
                    <p className="bootstrap-custom-flat">{i18n.t("groups:createGroup.step3.typeHint")}</p>
                    <Select
                      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
                      {...({ style: { marginTop: "36.5px", paddingBottom: "6px" } } as any)}
                      name="existingUsers"
                      options={this.state.returnedUserList}
                      onInputChange={this.getCurrentUsers}
                      onChange={this.handleSelect}
                      placeholder={i18n.t("groups:createGroup.step3.emailPlaceholder")}
                      classNamePrefix="seleniumTest"
                    />
                  </Box>
                  <Grid sx={{ overflow: "auto", maxHeight: "250px" }}>{existingUsersDisplay}</Grid>
                </Paper>
              </Grid>
              {newUsersDisplay && (
                <Grid size={6}>
                  <Paper sx={{ padding: "10px" }}>
                    <h2>{i18n.t("groups:createGroup.step3.inviteNewHeading")}</h2>
                    <FormControl error aria-describedby="email-error-text">
                      <TextField
                        variant="standard"
                        data-test-id="createGroupInviteNewUsers"
                        error={!this.state.newUserEmailCheck}
                        placeholder={i18n.t("groups:createGroup.step3.emailPlaceholder")}
                        name="newUsers"
                        margin="normal"
                        onChange={this.emailValidation}
                        onBlur={this.handleBlur}
                        onKeyPress={this.handleEnter}
                      />
                      {!this.state.newUserEmailCheck && (
                        <FormHelperText id="email-error-text">
                          {i18n.t("groups:createGroup.validation.invalidEmail")}
                        </FormHelperText>
                      )}
                    </FormControl>
                    <Grid sx={{ overflow: "auto", maxHeight: "250px" }}>{newUsersDisplay}</Grid>
                  </Paper>
                </Grid>
              )}
            </Grid>
          </ThemeProvider>
        </StyledEngineProvider>
      </Box>
    );
  }
}

export default createGroupStep3;
