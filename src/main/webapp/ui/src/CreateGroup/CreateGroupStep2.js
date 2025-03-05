"use strict";
import React from "react";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import FormControlLabel from "@mui/material/FormControlLabel";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../theme";
import axios from "@/common/axios";
import CreatableSelect from "react-select/creatable";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import EmailValidator from "email-validator";

class createGroupStep2 extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      radioOptions: {
        value: "",
        selectedUser: "",
        displayNominate: false,
      },
      returnedUserList: [],
      emailValidation: true,
    };
  }
  handleChange = (event) => {
    //Check to see if self nominated or not
    const nominatePi =
      event.target.value === this.props.currentUser ? false : true;

    this.setState({
      radioOptions: {
        value: event.target.value,
        displayNominate: nominatePi,
        selectedUser: event.target.value,
      },
    });
  };

  getCurrentUsers = (value) => {
    //Don't perform a search until after 3 characters have been input
    if (value.length < 3) {
      return;
    }

    //Validate email
    const emailTest = EmailValidator.validate(value);
    this.setState({
      emailValidation: emailTest,
    });
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
        if (error.message === "Network Error") {
          console.warn("No network connection");
        } else {
          console.warn("Error retrieving information");
        }
      });
  };

  handleSelect = (user) => {
    if (user === null) {
      this.setState((prevState) => ({
        radioOptions: {
          ...prevState.radioOptions,
          selectedUser: "",
        },
      }));
    } else {
      const emailTest = EmailValidator.validate(user.value);

      this.setState({
        emailValidation: emailTest,
      });

      this.setState((prevState) => ({
        radioOptions: {
          ...prevState.radioOptions,
          selectedUser: user.value,
        },
      }));
    }
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

  componentDidMount() {
    this.setState({
      radioOptions: {
        ...this.props.radioDetails,
      },
    });
  }

  componentWillUnmount() {
    this.props.changePI(this.state.radioOptions);
  }

  styles = {
    container: {
      padding: "0 25px 10px 25px",
    },
    userSearchForm: {
      width: "100%",
    },
    noteText: {
      fontSize: "11px",
    },
  };

  render() {
    return (
      <div style={this.styles.container}>
        <StyledEngineProvider injectFirst>
          <ThemeProvider theme={materialTheme}>
            <h3>Select PI</h3>
            <RadioGroup
              aria-label="Pi Role"
              name="piRole"
              value={this.state.radioOptions.value}
              onChange={this.handleChange}
            >
              <FormControlLabel
                data-test-id="createGroupSelectSelfPI"
                value={this.props.currentUser}
                control={<Radio color="primary" />}
                label="Make me the group PI and invite users"
              />
              <FormControlLabel
                data-test-id="createGroupSelectOtherPI"
                value="selectPI"
                control={<Radio color="primary" />}
                label={
                  <span>
                    Nominate a PI, make me Lab Admin, and supply users for
                    pending invitations.
                    <br />
                    <i style={this.styles.noteText}>
                      Note: group wont be created until PI accepts invitation
                    </i>
                  </span>
                }
              />
            </RadioGroup>
            {this.state.radioOptions.displayNominate === true && (
              <div>
                <p>
                  Enter a single email address of a new or existing user to be
                  asked to be group PI.
                </p>
                <FormControl
                  data-test-id="createGroupChoosePI"
                  error
                  aria-describedby="email-error-text"
                  style={this.styles.userSearchForm}
                >
                  <CreatableSelect
                    isClearable={true}
                    onChange={this.handleSelect}
                    onInputChange={this.getCurrentUsers}
                    options={this.state.returnedUserList}
                    placeholder="Start typing..."
                    formatCreateLabel={(userInput) => `Select ${userInput}`}
                    defaultValue={{
                      value: this.props.radioDetails.selectedUser,
                      label: this.props.radioDetails.selectedUser,
                    }}
                    classNamePrefix="seleniumTest"
                  />
                  {!this.state.emailValidation && (
                    <FormHelperText id="email-error-text">
                      Please enter a valid email address
                    </FormHelperText>
                  )}
                </FormControl>
              </div>
            )}
          </ThemeProvider>
        </StyledEngineProvider>
      </div>
    );
  }
}

export default createGroupStep2;
