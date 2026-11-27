import Box from "@mui/material/Box";
import { ThemeProvider } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import React from "react";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
import i18n from "@/modules/common/i18n";
import materialTheme from "../theme";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
class CreateGroupStep1 extends React.Component<any, any> {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  constructor(props: any) {
    super(props);
    this.state = { value: "" };

    this.handleChange = this.handleChange.bind(this);
  }

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  handleChange = (event: any) => {
    //Remove spaces before group name if it has any.
    if (!event.target.value.replace(/\s/g, "").length) {
      this.setState({ value: "" });
    } else {
      this.setState({ value: event.target.value });
    }
  };

  componentWillUnmount() {
    this.props.changeGroupName(this.state.value);
  }

  componentDidMount() {
    this.setState({ value: this.props.groupName });
  }

  render() {
    return (
      <Box sx={{ padding: "0 25px 10px 25px" }}>
        <MuiCssLayerProvider>
          <ThemeProvider theme={materialTheme}>
            <h3>{i18n.t("groups:createGroup.step1.heading")}</h3>
            <p>{i18n.t("groups:createGroup.step1.hint")}</p>
            <TextField
              variant="standard"
              label={i18n.t("groups:createGroup.step1.heading")}
              data-test-id="createGroupGroupName"
              value={this.state.value}
              onChange={this.handleChange}
            />
          </ThemeProvider>
        </MuiCssLayerProvider>
      </Box>
    );
  }
}

export default CreateGroupStep1;
