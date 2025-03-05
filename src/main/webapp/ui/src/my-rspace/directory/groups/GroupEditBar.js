"use strict";
import React from "react";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import theme from "../../../theme";
import Card from "@mui/material/Card";
import CardHeader from "@mui/material/CardHeader";
import CardContent from "@mui/material/CardContent";
import CardActions from "@mui/material/CardActions";
import Button from "@mui/material/Button";
import IconButton from "@mui/material/IconButton";
import TextField from "@mui/material/TextField";
import FormControlLabel from "@mui/material/FormControlLabel";
import Checkbox from "@mui/material/Checkbox";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faPencilAlt } from "@fortawesome/free-solid-svg-icons";
import Tooltip from "@mui/material/Tooltip";
import Snackbar from "@mui/material/Snackbar";
import SnackbarContent from "@mui/material/SnackbarContent";
import axios from "@/common/axios";
import styled from "@emotion/styled";
import { CardWrapper } from "../../../styles/CommonStyles.js";
import { createRoot } from "react-dom/client";
library.add(faPencilAlt);

const Section = styled.div`
  margin-top: 10px;

  label {
    width: 100%;
  }
`;

class GroupEditBar extends React.Component {
  constructor() {
    super();
    this.state = {
      toast: false,
      editing: false,
      group_id: domContainer.getAttribute("data-group-id"),
      can_edit: domContainer.getAttribute("data-can-edit") == "true",
      profile_text: domContainer.getAttribute("data-profile-text"),
      pi_can_edit_permission:
        domContainer.getAttribute("data-can-editpieditallchioce") == "true",
      pi_can_edit_value:
        domContainer.getAttribute("data-pieditallchoicevalue") == "true",
      profile_hiding_enabled:
        domContainer.getAttribute("data-profile-hiding-enabled") == "true",
      can_hide_profile:
        domContainer.getAttribute("data-can-hide-group-profile") == "true",
      profile_is_hidden:
        domContainer.getAttribute("data-group-private-profile") == "true",
      default_text: "There is no profile information for this group.",
      errorMessage: "",
    };

    this.handleChange = this.handleChange.bind(this);
  }

  handleChange(event) {
    this.setState({ profile_text: event.target.value });
  }

  handleSwitch = (name) => (event) => {
    this.setState({ [name]: event.target.checked });
  };

  submitForm = (event) => {
    event.preventDefault();
    let data = new FormData(),
      promises = [];

    if (this.state.can_edit) {
      data.set("newProfile", this.state.profile_text);
      promises.push(
        axios
          .post(`/groups/editProfile/${this.state.group_id}`, data)
          .then((response) => {
            if (response.data.data == "Edit profile ok") {
              this.setState({ editing: false });
            } else {
              this.handleError(response.data.exceptionMessage);
            }
          })
          .catch((error) => {
            this.handleError(error);
          })
      );
    }

    if (
      this.state.pi_can_edit_permission &&
      this.state.pi_can_edit_value !=
        (domContainer.getAttribute("data-pieditallchoicevalue") == "true")
    ) {
      data = new FormData();
      data.set("canPIEditAll", this.state.pi_can_edit_value);
      promises.push(
        axios
          .post(
            `/groups/ajax/admin/changePiCanEditAll/${this.state.group_id}`,
            data
          )
          .then((response) => {})
          .catch((error) => {
            this.handleError(error);
          })
      );
    }

    if (
      this.state.can_hide_profile &&
      this.state.profile_hiding_enabled &&
      this.state.profile_is_hidden !=
        (domContainer.getAttribute("data-group-private-profile") == "true")
    ) {
      data = new FormData();
      data.set("hideProfile", this.state.profile_is_hidden);
      promises.push(
        axios
          .post(
            `/groups/ajax/admin/changeHideProfileSetting/${this.state.group_id}`,
            data
          )
          .then((response) => {})
          .catch((error) => {
            this.handleError(error);
          })
      );
    }

    axios.all(promises).then(() => {
      this.setState({ editing: false });
    });
  };

  handleError = (error) => {
    this.setState({
      toast: true,
      errorMessage: `Your request failed: ${error}. Please contact support if this problem persists.`,
    });
  };

  handleClickEdit = () => {
    this.setState({ editing: true });
  };

  handleClickClose = () => {
    this.setState({ editing: false });
  };

  closeToast = () => {
    this.setState({ toast: false });
  };

  styles = {
    icon_button: {
      fontSize: "18px",
      width: "auto",
    },
    card_actions: {
      display: "flex",
      justifyContent: "flex-end",
    },
  };

  render() {
    return (
      <StyledEngineProvider injectFirst>
        <ThemeProvider theme={theme}>
          <CardWrapper>
            <Card>
              <CardHeader
                subheader="Profile"
                action={
                  !this.state.editing &&
                  this.state.can_edit && (
                    <Tooltip title="Edit profile settings">
                      <IconButton
                        onClick={this.handleClickEdit}
                        style={this.styles.icon_button}
                        data-test-id="button-edit-profile"
                      >
                        <FontAwesomeIcon icon="pencil-alt" />
                      </IconButton>
                    </Tooltip>
                  )
                }
              />
              <CardContent>
                <TextField
                  variant="standard"
                  disabled={!this.state.editing}
                  value={this.state.profile_text}
                  onChange={this.handleChange}
                  placeholder="Please enter a short description of this group..."
                  multiline={true}
                  maxRows={4}
                  fullWidth
                  label="Profile description"
                  inputProps={{ "aria-label": "Profile description" }}
                  margin="dense"
                  data-test-id="profile-description"
                />
                {(this.state.pi_can_edit_permission ||
                  this.state.pi_can_edit_value) && (
                  <Section>
                    <FormControlLabel
                      control={
                        <Checkbox
                          disabled={
                            !this.state.editing ||
                            !this.state.pi_can_edit_permission
                          }
                          onChange={this.handleSwitch("pi_can_edit_value")}
                          color="primary"
                          checked={this.state.pi_can_edit_value}
                          margin="dense"
                          data-test-id="pi-can-edit"
                        />
                      }
                      label="PI can edit all work in this lab group."
                    />
                  </Section>
                )}
                {this.state.profile_hiding_enabled &&
                  (this.state.can_hide_profile ||
                    this.state.profile_is_hidden) && (
                    <Section>
                      <FormControlLabel
                        control={
                          <Checkbox
                            disabled={
                              !this.state.can_hide_profile ||
                              !this.state.editing
                            }
                            onChange={this.handleSwitch("profile_is_hidden")}
                            color="primary"
                            checked={this.state.profile_is_hidden}
                            margin="dense"
                            data-test-id="profile-is-hidden"
                          />
                        }
                        label="Hide the group from public listings"
                      />
                    </Section>
                  )}
              </CardContent>
              {this.state.editing && (
                <CardActions style={this.styles.card_actions}>
                  <Button
                    onClick={this.handleClickClose}
                    data-test-id="button-cancel-edit"
                  >
                    Cancel
                  </Button>
                  <Button
                    onClick={this.submitForm}
                    variant="contained"
                    color="primary"
                    data-test-id="button-save-edit"
                  >
                    Save
                  </Button>
                </CardActions>
              )}
            </Card>
          </CardWrapper>
          <Snackbar
            anchorOrigin={{
              vertical: "top",
              horizontal: "right",
            }}
            open={this.state.toast}
            autoHideDuration={6000}
            onClose={this.closeToast}
          >
            <SnackbarContent
              onClose={this.closeToast}
              message={this.state.errorMessage}
            />
          </Snackbar>
        </ThemeProvider>
      </StyledEngineProvider>
    );
  }
}

const domContainer = document.getElementById("groupEditBar");
const root = createRoot(domContainer);
root.render(<GroupEditBar />);
