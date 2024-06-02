"use strict";
import React, { useState, type Node } from "react";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../theme";
import TextField from "@mui/material/TextField";
import Grid from "@mui/material/Grid";
import Paper from "@mui/material/Paper";
import Tabs from "@mui/material/Tabs";
import Tab from "@mui/material/Tab";
import Container from "@mui/material/Container";
import InputLabel from "@mui/material/InputLabel";
import MenuItem from "@mui/material/MenuItem";
import FormControl from "@mui/material/FormControl";
import Select from "@mui/material/Select";
import { createRoot } from "react-dom/client";

const Password = {
  _pattern: /[a-zA-Z0-9_\-\+\.]/,
  _getRandomByte: function () {
    if (window.crypto && window.crypto.getRandomValues) {
      var result = new Uint8Array(1);
      window.crypto.getRandomValues(result);
      return result[0];
    } else if (window.msCrypto && window.msCrypto.getRandomValues) {
      var result = new Uint8Array(1);
      window.msCrypto.getRandomValues(result);
      return result[0];
    } else {
      return Math.floor(Math.random() * 256);
    }
  },
  generate: function (length) {
    return Array.apply(null, { length: length })
      .map(function () {
        var result;
        while (true) {
          result = String.fromCharCode(this._getRandomByte());
          if (this._pattern.test(result)) {
            return result;
          }
        }
      }, this)
      .join("");
  },
};

export default function NewUser(): Node {
  const [option, setOption] = React.useState(0);
  const [values, setValues] = React.useState({
    firstName: "",
    lastName: "",
    username: "",
    email: "",
    password: "",
    affiliation: "",
    role: "",
  });
  const conditions = {
    roleSysadmin: domContainer.getAttribute("data-role-sysadmin") == "true",
    affiliationRequired:
      domContainer.getAttribute("data-affiliation-required") == "true",
    passwordRequired:
      domContainer.getAttribute("data-password-required") == "true",
  };

  const handleChange = (name) => (event) => {
    setValues({ ...values, [name]: event.target.value });
  };

  function handleMenuChange(e, newOption) {
    setOption(newOption);
  }

  function generatePassword() {
    let password = Password.generate(8);
    setValues({ ...values, password: password });
  }

  const navigation = () => {
    return (
      <Paper square>
        <Tabs
          value={option}
          indicatorColor="primary"
          textColor="primary"
          onChange={handleMenuChange}
        >
          <Tab label="Individual user registration" />
          <Tab label="Batch user registration" />
        </Tabs>
      </Paper>
    );
  };

  const formWrapper = (content) => {
    return (
      <Container style={{ padding: "0px 0px" }}>
        <Grid
          container
          spacing={2}
          style={{
            backgroundColor: "rgb(248,248,248)",
            marginTop: "30px",
            width: "100%",
            marginLeft: "0px",
          }}
        >
          {content}
        </Grid>
      </Container>
    );
  };

  const commonForm = () => {
    return formWrapper(
      <>
        <Grid item xs={6}>
          <TextField
            variant="standard"
            fullWidth
            label="First Name"
            value={values.firstName}
            onChange={handleChange("firstName")}
            margin="normal"
          />
        </Grid>
        <Grid item xs={6}>
          <TextField
            variant="standard"
            fullWidth
            label="Last Name"
            value={values.lastName}
            onChange={handleChange("lastName")}
            margin="normal"
          />
        </Grid>
        <Grid item xs={6}>
          <TextField
            variant="standard"
            fullWidth
            label="Username"
            value={values.username}
            onChange={handleChange("username")}
            margin="normal"
          />
        </Grid>
        <Grid item xs={6}>
          <TextField
            variant="standard"
            fullWidth
            label="Email"
            value={values.email}
            onChange={handleChange("email")}
            margin="normal"
          />
        </Grid>
        {conditions.passwordRequired && (
          <Grid item xs={6}>
            <TextField
              variant="standard"
              fullWidth
              label="Password"
              value={values.password}
              onChange={handleChange("password")}
              margin="normal"
              helperText={
                <a onClick={generatePassword}>Generate random password</a>
              }
            />
          </Grid>
        )}
        {conditions.affiliationRequired && (
          <Grid item xs={6}>
            <TextField
              variant="standard"
              fullWidth
              label="Affiliation"
              value={values.affiliation}
              onChange={handleChange("affiliation")}
              margin="normal"
            />
          </Grid>
        )}
        <Grid item xs={6}>
          <FormControl fullWidth>
            <InputLabel htmlFor="user-role">Role</InputLabel>
            <Select
              variant="standard"
              fullWidth
              value={values.role}
              onChange={handleChange("role")}
              inputProps={{
                name: "role",
                id: "user-role",
              }}
            >
              <MenuItem value={"user"}>User</MenuItem>
              <MenuItem value={"pi"}>PI</MenuItem>
              <MenuItem value={"community-admin"}>Community Admin</MenuItem>
              {conditions.roleSysadmin && (
                <MenuItem value={"system-admin"}>System Admin</MenuItem>
              )}
            </Select>
          </FormControl>
        </Grid>
      </>
    );
  };

  const roleSpecificForm = () => {
    if (values.role == "user") {
      return formWrapper(<></>);
    } else if (values.role == "pi") {
      return formWrapper(<></>);
    } else if (values.role == "community-admin") {
      return formWrapper(<></>);
    } else if (values.role == "system-admin") {
      return formWrapper(<></>);
    } else {
      return <></>;
    }
  };

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        {navigation()}
        {commonForm()}
        {roleSpecificForm()}
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

const domContainer = document.getElementById("contentCreateAccount2");
const root = createRoot(domContainer);
root.render(<NewUser />);
