import { faPencilAlt } from "@fortawesome/free-solid-svg-icons/faPencilAlt";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import CardHeader from "@mui/material/CardHeader";
import Checkbox from "@mui/material/Checkbox";
import FormControlLabel from "@mui/material/FormControlLabel";
import IconButton from "@mui/material/IconButton";
import Snackbar from "@mui/material/Snackbar";
import SnackbarContent from "@mui/material/SnackbarContent";
import { ThemeProvider } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import Tooltip from "@mui/material/Tooltip";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React from "react";
import { createRoot } from "react-dom/client";
import axios from "@/common/axios";
import i18n from "../../../modules/common/i18n";
import I18nRoot from "../../../modules/common/i18n/I18nRoot";
import { CardWrapper } from "../../../styles/CommonStyles";
import theme from "../../../theme";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
class GroupEditBar extends React.Component<any, any> {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  constructor(props: any) {
    super(props);
    this.state = {
      toast: false,
      editing: false,
      group_id: domContainer.getAttribute("data-group-id"),
      can_edit: domContainer.getAttribute("data-can-edit") === "true",
      profile_text: domContainer.getAttribute("data-profile-text"),
      pi_can_edit_permission: domContainer.getAttribute("data-can-editpieditallchioce") === "true",
      pi_can_edit_value: domContainer.getAttribute("data-pieditallchoicevalue") === "true",
      profile_hiding_enabled: domContainer.getAttribute("data-profile-hiding-enabled") === "true",
      can_hide_profile: domContainer.getAttribute("data-can-hide-group-profile") === "true",
      profile_is_hidden: domContainer.getAttribute("data-group-private-profile") === "true",
      default_text: i18n.t("profile.groups.editBar.noProfile"),
      errorMessage: "",
    };

    this.handleChange = this.handleChange.bind(this);
  }

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleChange(event: any) {
    this.setState({ profile_text: event.target.value });
  }

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleSwitch = (name: any) => (event: any) => {
    this.setState({ [name]: event.target.checked });
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  submitForm = (event: any) => {
    event.preventDefault();
    let data = new FormData(),
      promises = [];

    if (this.state.can_edit) {
      data.set("newProfile", this.state.profile_text);
      promises.push(
        axios
          .post(`/groups/editProfile/${this.state.group_id}`, data)
          .then((response) => {
            if (response.data.data === "Edit profile ok") {
              this.setState({ editing: false });
            } else {
              this.handleError(response.data.exceptionMessage);
            }
          })
          .catch((error) => {
            this.handleError(error);
          }),
      );
    }

    if (
      this.state.pi_can_edit_permission &&
      this.state.pi_can_edit_value !== (domContainer.getAttribute("data-pieditallchoicevalue") === "true")
    ) {
      data = new FormData();
      data.set("canPIEditAll", this.state.pi_can_edit_value);
      promises.push(
        axios
          .post(`/groups/ajax/admin/changePiCanEditAll/${this.state.group_id}`, data)
          .then((_response) => {})
          .catch((error) => {
            this.handleError(error);
          }),
      );
    }

    if (
      this.state.can_hide_profile &&
      this.state.profile_hiding_enabled &&
      this.state.profile_is_hidden !== (domContainer.getAttribute("data-group-private-profile") === "true")
    ) {
      data = new FormData();
      data.set("hideProfile", this.state.profile_is_hidden);
      promises.push(
        axios
          .post(`/groups/ajax/admin/changeHideProfileSetting/${this.state.group_id}`, data)
          .then((_response) => {})
          .catch((error) => {
            this.handleError(error);
          }),
      );
    }

    axios.all(promises).then(() => {
      this.setState({ editing: false });
    });
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleError = (error: any) => {
    this.setState({
      toast: true,
      errorMessage: i18n.t("profile.groups.editBar.errorMessage", { error }),
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

  render() {
    return (
      <StyledEngineProvider injectFirst enableCssLayer>
        <ThemeProvider theme={theme}>
          <CardWrapper>
            <Card>
              <CardHeader
                subheader={i18n.t("profile.groups.editBar.subheader")}
                action={
                  !this.state.editing &&
                  this.state.can_edit && (
                    <Tooltip title={i18n.t("profile.groups.editBar.editTooltip")}>
                      <IconButton
                        onClick={this.handleClickEdit}
                        sx={{ fontSize: "18px", width: "auto" }}
                        data-test-id="button-edit-profile"
                      >
                        <FontAwesomeIcon icon={faPencilAlt} />
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
                  placeholder={i18n.t("profile.groups.editBar.placeholder")}
                  multiline={true}
                  maxRows={4}
                  fullWidth
                  label={i18n.t("profile.groups.editBar.profileDescription")}
                  margin="dense"
                  data-test-id="profile-description"
                  slotProps={{
                    htmlInput: { "aria-label": i18n.t("profile.groups.editBar.profileDescription") },
                  }}
                />
                {(this.state.pi_can_edit_permission || this.state.pi_can_edit_value) && (
                  <Box sx={{ marginTop: "10px" }}>
                    <FormControlLabel
                      sx={{ width: "100%" }}
                      control={
                        <Checkbox
                          {...({
                            disabled: !this.state.editing || !this.state.pi_can_edit_permission,
                            onChange: this.handleSwitch("pi_can_edit_value"),
                            color: "primary",
                            checked: this.state.pi_can_edit_value,
                            margin: "dense",
                            "data-test-id": "pi-can-edit",
                            // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
                          } as any)}
                        />
                      }
                      label={i18n.t("profile.groups.editBar.piCanEdit")}
                    />
                  </Box>
                )}
                {this.state.profile_hiding_enabled && (this.state.can_hide_profile || this.state.profile_is_hidden) && (
                  <Box sx={{ marginTop: "10px" }}>
                    <FormControlLabel
                      sx={{ width: "100%" }}
                      control={
                        <Checkbox
                          {...({
                            disabled: !this.state.can_hide_profile || !this.state.editing,
                            onChange: this.handleSwitch("profile_is_hidden"),
                            color: "primary",
                            checked: this.state.profile_is_hidden,
                            margin: "dense",
                            "data-test-id": "profile-is-hidden",
                            // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
                          } as any)}
                        />
                      }
                      label={i18n.t("profile.groups.editBar.hideGroup")}
                    />
                  </Box>
                )}
              </CardContent>
              {this.state.editing && (
                <CardActions sx={{ display: "flex", justifyContent: "flex-end" }}>
                  <Button onClick={this.handleClickClose} data-test-id="button-cancel-edit">
                    {i18n.t("actions.cancel")}
                  </Button>
                  <Button onClick={this.submitForm} variant="contained" color="primary" data-test-id="button-save-edit">
                    {i18n.t("actions.save")}
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
              {...({
                onClose: this.closeToast,
                message: this.state.errorMessage,
                // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
              } as any)}
            />
          </Snackbar>
        </ThemeProvider>
      </StyledEngineProvider>
    );
  }
}

const domContainer = document.getElementById("groupEditBar") as HTMLElement;
const root = createRoot(domContainer);
root.render(
  <I18nRoot namespaces={["common"]}>
    <GroupEditBar />
  </I18nRoot>,
);
