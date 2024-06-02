"use strict";
import React from "react";
import Button from "@mui/material/Button";
import DialogTitle from "@mui/material/DialogTitle";
import Dialog from "@mui/material/Dialog";
import MobileStepper from "@mui/material/MobileStepper";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../theme";
import Step1 from "./CreateGroupStep1";
import Step2 from "./CreateGroupStep2";
import Step3 from "./CreateGroupStep3";
import Step4 from "./CreateGroupStep4";
import IconButton from "@mui/material/IconButton";
import CloseIcon from "@mui/icons-material/Close";
import LinearProgress from "@mui/material/LinearProgress";
import Fade from "@mui/material/Fade";
import axios from "axios";
import Snackbar from "@mui/material/Snackbar";
import SnackbarContent from "@mui/material/SnackbarContent";
import { createRoot } from "react-dom/client";

class CreateGroup extends React.Component {
  constructor() {
    super();
    this.state = {
      open: false,
      toast: false,
      errorMessage: "",
      activeStep: 0,
      labGroup: null,
      loading: false,
      createGroup: {
        currentUser: domContainer.getAttribute("data-currentUser"),
        selectPI: {
          value: selfService
            ? domContainer.getAttribute("data-currentUser")
            : "",
          selectedUser: selfService
            ? domContainer.getAttribute("data-currentUser")
            : "",
          displayNominate: false,
        },
        groupType: projectGroup ? "Project Group" : "labGroup",
        groupName: "",
        existingUsers: [],
        newUsers: [],
      },
    };
  }
  maxSteps = selfService ? 3 : 4;
  handleClickOpen = () => {
    this.setState({
      ...this.initialState,
      open: true,
    });
  };

  handleNext = () => {
    if (this.state.activeStep === this.maxSteps - 1) {
      this.createGroup();
    } else {
      this.setState((prevState) => ({
        activeStep: prevState.activeStep + 1,
      }));
    }
  };

  handleBack = () => {
    this.setState((prevState) => ({
      activeStep: prevState.activeStep - 1,
    }));
  };

  groupTypeSelect = (groupType) => {
    this.setState({
      activeStep: 1,
      createGroup: {
        ...this.state.createGroup,
        groupType: groupType,
      },
    });
  };

  changeGroupName = (groupName) => {
    this.setState({
      createGroup: {
        ...this.state.createGroup,
        groupName: groupName,
      },
    });
  };

  changePI = (selectPiOptions) => {
    this.setState({
      createGroup: {
        ...this.state.createGroup,
        selectPI: {
          ...selectPiOptions,
        },
      },
    });
  };

  updateInvitedmembers = (existingUsers, newUsers) => {
    this.setState((prevState) => ({
      createGroup: {
        ...prevState.createGroup,
        existingUsers: [...existingUsers],
        newUsers: [...newUsers],
      },
    }));
  };

  createGroup = () => {
    this.displayLoading(true);

    const url = projectGroup
      ? "/projectGroup/createProjectGroup"
      : selfService
      ? "/selfServiceLabGroup/createSelfServiceLabGroup"
      : "/cloud/createCloudGroup2";
    const data = {
      emails: [
        ...this.state.createGroup.existingUsers,
        ...this.state.createGroup.newUsers,
      ],
      piEmail: this.state.createGroup.selectPI.selectedUser,
      groupName: this.state.createGroup.groupName,
      newGroup: this.state.createGroup.groupType,
    };

    axios
      .post(url, data)
      .then((response) => {
        if (response.data.success === true) {
          if (selfService) {
            this.setState({
              open: false,
              toast: true,
              newGroup: response.data.data.newGroup,
            });
          } else {
            this.setState({
              open: false,
              toast: true,
            });
          }
        } else if (response.data.success === false) {
          // handled error message
          this.setState({
            errorMessage: response.data.error.errorMessages[0],
          });
        } else {
          // unhandled error message
          console.warn(
            "Unexpected error returned by group creation request",
            response.data
          );
          this.setState({
            errorMessage: `Your request failed with unexpected error. If this problem persists, please contact RSpace Support.`,
          });
        }
        this.displayLoading(false);
      })
      .catch((error) => {
        this.setState({
          errorMessage: `Your request failed due to: ${error.message}. If this problem persists, please contact RSpace Support.`,
        });
        this.displayLoading(false);
      });
  };

  displayLoading = (showLoading) => {
    this.setState({
      loading: showLoading,
    });
  };

  detailsComplete = () => {
    const details = this.state.createGroup;

    if (
      details.selectPI.value === "" ||
      details.selectPI.selectedUser === "" ||
      details.groupName === "" ||
      details.groupType === ""
    ) {
      return false;
    } else {
      return true;
    }
  };

  //Depending on what modal number the user is on depends on what is displayed via switch
  getStepContent = (step) => {
    if (selfService && step > 0) {
      step++;
    }
    switch (step) {
      case 0:
        return (
          <Step1
            changeGroupName={this.changeGroupName}
            groupName={this.state.createGroup.groupName}
          />
        );
      case 1:
        return (
          <Step2
            currentUser={this.state.createGroup.currentUser}
            changePI={this.changePI}
            radioDetails={this.state.createGroup.selectPI}
          />
        );
      case 2:
        return (
          <Step3
            updateInvitedMembers={this.updateInvitedmembers}
            existingUsers={this.state.createGroup.existingUsers}
            newUsers={this.state.createGroup.newUsers}
          />
        );
      case 3:
        return <Step4 summary={this.state.createGroup} />;
      default:
        return (
          <Step01
            changeGroupName={this.changeGroupName}
            groupName={this.state.createGroup.groupName}
          />
        );
    }
  };

  handleClose = () => {
    this.setState({
      open: false,
    });
  };

  closeToast = () => {
    this.setState({
      toast: false,
    });
    if (selfService) {
      window.location.replace("/groups/view/" + this.state.newGroup);
    }
  };

  //This is to capture how the very initial state looks so if user wants to create another group we do not have to reset state manually.
  initialState = {};

  componentDidMount = () => {
    this.initialState = { ...this.state };
  };

  styles = {
    closeButton: {
      position: "absolute",
      right: 0,
      top: 0,
      width: "auto",
    },
    container: {
      width: "600px",
    },
    toastSuccess: {
      backgroundColor: "#4CAF50",
    },
    errorText: {
      color: "#d32f2f",
      textAlign: "center",
      fontSize: "14px",
      marginBottom: "10px",
    },
  };

  render() {
    const detailsComplete = this.detailsComplete();
    const loading = this.state.loading;

    return (
      <>
        <div>
          <StyledEngineProvider injectFirst>
            <ThemeProvider theme={materialTheme}>
              <Button
                variant="contained"
                color="primary"
                data-test-id="createGroupOpenButton"
                onClick={this.handleClickOpen}
              >
                Create Group
              </Button>
              <Dialog
                aria-labelledby="simple-dialog-title"
                open={this.state.open}
                className="createGroup-Modal"
                data-test-id="createGroupModal"
              >
                <DialogTitle data-test-id="modalTitle">
                  Create group
                  <IconButton
                    aria-label="Close"
                    data-test-id="closeModal"
                    style={this.styles.closeButton}
                    onClick={this.handleClose}
                  >
                    <CloseIcon />
                  </IconButton>
                </DialogTitle>
                <div style={this.styles.container}>
                  {this.getStepContent(this.state.activeStep)}
                </div>
                <Fade in={loading} unmountOnExit>
                  <LinearProgress color="primary" />
                </Fade>
                <span style={this.styles.errorText}>
                  {this.state.errorMessage}
                </span>
                <MobileStepper
                  steps={this.maxSteps}
                  position="static"
                  activeStep={this.state.activeStep}
                  nextButton={
                    <Button
                      size="small"
                      data-test-id="nextButton"
                      onClick={this.handleNext}
                      disabled={
                        this.state.createGroup.groupType === "" ||
                        (this.state.activeStep === this.maxSteps - 1 &&
                          detailsComplete === false)
                      }
                    >
                      {this.state.activeStep !== this.maxSteps - 1
                        ? "Next"
                        : `Create ${this.state.createGroup.groupType}`}
                    </Button>
                  }
                  backButton={
                    <Button
                      size="small"
                      data-test-id="backButton"
                      onClick={this.handleBack}
                      disabled={this.state.activeStep === 0}
                    >
                      Back
                    </Button>
                  }
                />
              </Dialog>
              <Snackbar
                anchorOrigin={{
                  vertical: "top",
                  horizontal: "right",
                }}
                open={this.state.toast}
                autoHideDuration={selfService ? 1000 : 6000}
                onClose={this.closeToast}
                style={
                  selfService
                    ? {
                        position: "absolute",
                        marginBottom: "10px",
                        textAlign: "left",
                      }
                    : {}
                }
              >
                <SnackbarContent
                  onClose={this.closeToast}
                  message={
                    this.state.createGroup.currentUser !==
                    this.state.createGroup.selectPI.selectedUser
                      ? `Group creation request for ${this.state.createGroup.groupName} sent successfully`
                      : `${this.state.createGroup.groupName} group created successfully!`
                  }
                  style={this.styles.toastSuccess}
                />
              </Snackbar>
            </ThemeProvider>
          </StyledEngineProvider>
        </div>
      </>
    );
  }
}

const domContainer = document.getElementById("createGroup");
const selfService = $("#selfServiceLabGroup").length != 0;
const projectGroup = $("#projectGroup").length != 0;
const root = createRoot(domContainer);
root.render(<CreateGroup />);
