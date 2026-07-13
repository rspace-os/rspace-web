import CloseIcon from "@mui/icons-material/Close";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import Fade from "@mui/material/Fade";
import IconButton from "@mui/material/IconButton";
import LinearProgress from "@mui/material/LinearProgress";
import MobileStepper from "@mui/material/MobileStepper";
import Snackbar from "@mui/material/Snackbar";
import SnackbarContent from "@mui/material/SnackbarContent";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React from "react";
import { createRoot } from "react-dom/client";
import axios from "@/common/axios";
import LoaderCircular from "@/components/LoadingCircular";
import i18n from "@/modules/common/i18n";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import materialTheme from "../theme";
import Step1 from "./CreateGroupStep1";
import Step2 from "./CreateGroupStep2";
import Step3 from "./CreateGroupStep3";
import Step4 from "./CreateGroupStep4";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
class CreateGroup extends React.Component<any, any> {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  constructor(props: any) {
    super(props);
    this.state = {
      open: false,
      toast: false,
      errorMessage: "",
      activeStep: 0,
      labGroup: null,
      loading: false,
      createGroup: {
        currentUser: domContainer?.getAttribute("data-currentUser"),
        selectPI: {
          value: selfService ? domContainer?.getAttribute("data-currentUser") : "",
          selectedUser: selfService ? domContainer?.getAttribute("data-currentUser") : "",
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
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      this.setState((prevState: any) => ({
        activeStep: prevState.activeStep + 1,
      }));
    }
  };

  handleBack = () => {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    this.setState((prevState: any) => ({
      activeStep: prevState.activeStep - 1,
    }));
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  groupTypeSelect = (groupType: any) => {
    this.setState({
      activeStep: 1,
      createGroup: {
        ...this.state.createGroup,
        groupType,
      },
    });
  };

  changeGroupName = (groupName: string) => {
    this.setState({
      createGroup: {
        ...this.state.createGroup,
        groupName,
      },
    });
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  changePI = (selectPiOptions: any) => {
    this.setState({
      createGroup: {
        ...this.state.createGroup,
        selectPI: {
          ...selectPiOptions,
        },
      },
    });
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  updateInvitedmembers = (existingUsers: any, newUsers: any) => {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    this.setState((prevState: any) => ({
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
      emails: [...this.state.createGroup.existingUsers, ...this.state.createGroup.newUsers],
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
          console.warn("Unexpected error returned by group creation request", response.data);
          this.setState({
            errorMessage: `Your request failed with unexpected error. If this problem persists, please contact RSpace Support.`,
          });
        }
        this.displayLoading(false);
      })
      .catch((error) => {
        this.setState({
          errorMessage: i18n.t("groups:createGroup.errors.requestFailed", { message: error.message }),
        });
        this.displayLoading(false);
      });
  };

  displayLoading = (showLoading: boolean) => {
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
    }
    return true;
  };

  //Depending on what modal number the user is on depends on what is displayed via switch
  getStepContent = (step: number) => {
    if (selfService && step > 0) {
      step++;
    }
    switch (step) {
      case 0:
        return <Step1 changeGroupName={this.changeGroupName} groupName={this.state.createGroup.groupName} />;
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
        return <Step1 changeGroupName={this.changeGroupName} groupName={this.state.createGroup.groupName} />;
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
      window.location.replace(`/groups/view/${this.state.newGroup}`);
    }
  };

  //This is to capture how the very initial state looks so if user wants to create another group we do not have to reset state manually.
  initialState = {};

  componentDidMount = () => {
    this.initialState = { ...this.state };
  };

  render() {
    const detailsComplete = this.detailsComplete();
    const loading = this.state.loading;

    return (
      <div>
        <StyledEngineProvider injectFirst enableCssLayer>
          <ThemeProvider theme={materialTheme}>
            <Button
              variant="contained"
              color="primary"
              data-test-id="createGroupOpenButton"
              onClick={this.handleClickOpen}
            >
              {i18n.t("groups:createGroup.openButton")}
            </Button>
            <Dialog
              aria-labelledby="simple-dialog-title"
              open={this.state.open}
              className="createGroup-Modal"
              data-test-id="createGroupModal"
            >
              <DialogTitle data-test-id="modalTitle">
                {i18n.t("groups:createGroup.title")}
                <IconButton
                  aria-label={i18n.t("common:actions.close")}
                  data-test-id="closeModal"
                  sx={{
                    position: "absolute",
                    right: 0,
                    top: 0,
                    width: "auto",
                  }}
                  onClick={this.handleClose}
                >
                  <CloseIcon />
                </IconButton>
              </DialogTitle>
              <Box sx={{ width: "600px" }}>{this.getStepContent(this.state.activeStep)}</Box>
              <Fade in={loading} unmountOnExit>
                <LinearProgress color="primary" />
              </Fade>
              <Box
                component="span"
                sx={{
                  color: "#d32f2f",
                  textAlign: "center",
                  fontSize: "14px",
                  marginBottom: "10px",
                }}
              >
                {this.state.errorMessage}
              </Box>
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
                      (this.state.activeStep === this.maxSteps - 1 && detailsComplete === false)
                    }
                  >
                    {this.state.activeStep !== this.maxSteps - 1
                      ? i18n.t("groups:createGroup.nav.next")
                      : i18n.t("groups:createGroup.nav.create", { groupType: this.state.createGroup.groupType })}
                  </Button>
                }
                backButton={
                  <Button
                    size="small"
                    data-test-id="backButton"
                    onClick={this.handleBack}
                    disabled={this.state.activeStep === 0}
                  >
                    {i18n.t("groups:createGroup.nav.back")}
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
              sx={
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
                // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
                {...({ onClose: this.closeToast } as any)}
                message={
                  this.state.createGroup.currentUser !== this.state.createGroup.selectPI.selectedUser
                    ? i18n.t("groups:createGroup.toast.requestSent", { groupName: this.state.createGroup.groupName })
                    : i18n.t("groups:createGroup.toast.created", { groupName: this.state.createGroup.groupName })
                }
                sx={{ backgroundColor: "#4CAF50" }}
              />
            </Snackbar>
          </ThemeProvider>
        </StyledEngineProvider>
      </div>
    );
  }
}

const domContainer = document.getElementById("createGroup");
const selfService = $("#selfServiceLabGroup").length !== 0;
const projectGroup = $("#projectGroup").length !== 0;
const root = createRoot(domContainer as HTMLElement);
root.render(
  <I18nRoot namespaces={["groups"]} fallback={<LoaderCircular />}>
    <CreateGroup />
  </I18nRoot>,
);
