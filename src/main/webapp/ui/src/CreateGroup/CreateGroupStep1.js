"use strict";
import React from "react";
import TextField from "@mui/material/TextField";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../theme";

class CreateGroupStep1 extends React.Component {
  constructor(props) {
    super(props);
    this.state = { value: "" };

    this.handleChange = this.handleChange.bind(this);
  }

  handleChange = (event) => {
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

  styles = {
    container: {
      padding: "0 25px 10px 25px",
    },
  };

  render() {
    return (
      <div style={this.styles.container}>
        <StyledEngineProvider injectFirst>
          <ThemeProvider theme={materialTheme}>
            <h3>Group Name</h3>
            <p>Enter a name to identify the new group</p>
            <TextField
              variant="standard"
              label="Group Name"
              data-test-id="createGroupGroupName"
              value={this.state.value}
              onChange={this.handleChange}
            />
          </ThemeProvider>
        </StyledEngineProvider>
      </div>
    );
  }
}

export default CreateGroupStep1;
