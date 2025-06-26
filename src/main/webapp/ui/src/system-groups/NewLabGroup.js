"use strict";
import React, { useState, useRef, useEffect } from "react";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../theme";
import TextField from "@mui/material/TextField";
import Grid from "@mui/material/Grid";
import UserSelect from "./UserBox";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import axios from "@/common/axios";
import { createRoot } from "react-dom/client";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";

const scrollToRef = (ref) => window.scrollTo(0, ref.current.offsetTop);
const PROJECT_GROUP = "PROJECT_GROUP";
const LAB_GROUP = "LAB_GROUP";

export default function NewLabGroup() {
  const [values, setValues] = useState({
    name: "",
    nameError: false,
    members: [],
    membersError: false,
    pis: [],
    groupOwners: [],
    pisError: false,
    groupOwnerError: false,
    groupType: LAB_GROUP,
  });
  const [users, setUsers] = useState([]);
  const groupName = useRef(null);
  const executeScroll = () => scrollToRef(groupName);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const response = await axios.get("/groups/admin/ajax/allUsers");
        setUsers(response.data);
      } catch (e) {
        console.log(e);
      }
    };

    fetchData();
  }, []);

  const handleChange = (field) => (event) => {
    setValues({ ...values, [field]: event.target.value });
  };

  const updateSelected = (field) => (users) => {
    setValues({ ...values, [field]: users });
  };

  const updateSelectedDropdown =
    (field) =>
    ({ target: { value } }) => {
      setValues({ ...values, [field]: value });
    };

  function validateForm() {
    if (values.name.length == 0) {
      executeScroll();
      setValues({ ...values, nameError: true });
      return;
    } else {
      setValues({ ...values, nameError: false });
    }
    if (values.groupType === LAB_GROUP && values.pis.length != 1) {
      setValues({ ...values, pisError: "Please, select a PI" });
    } else if (
      values.groupType === PROJECT_GROUP &&
      values.groupOwners.length < 1
    ) {
      setValues({
        ...values,
        groupOwnerError: "Please select at least one group owner",
      });
    } else {
      submit();
    }
  }

  function submit() {
    const form = document.createElement("form");
    form.method = "post";
    form.action = "/groups/admin/?new";

    let params = {};
    params._memberString = 1;
    params.cancel = "Add group";
    params.pis = values.pis.length ? values.pis[0].username : values.pis;
    params.groupOwners = values.groupOwners.length
      ? values.groupOwners.map((m) => m.username)
      : values.groupOwners;
    params.groupType = values.groupType;
    params.memberString = params.displayName = values.name;
    params.memberString = values.members.map((m) => m.username);
    if (values.groupType === LAB_GROUP) {
      params.memberString = params.memberString.concat(values.pis[0].username);
    } else if (values.groupType === PROJECT_GROUP) {
      params.memberString = params.memberString.concat(
        ...params.groupOwners.filter(
          (owner) => !params.memberString.includes(owner)
        )
      );
    }

    for (const key in params) {
      if (Array.isArray(params[key])) {
        for (let i = 0; i < params[key].length; i++) {
          const hiddenField = document.createElement("input");
          hiddenField.type = "hidden";
          hiddenField.name = key;
          hiddenField.value = params[key][i];

          form.appendChild(hiddenField);
        }
      } else {
        const hiddenField = document.createElement("input");
        hiddenField.type = "hidden";
        hiddenField.name = key;
        hiddenField.value = params[key];

        form.appendChild(hiddenField);
      }
    }

    document.body.appendChild(form);
    form.submit();
  }

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <form noValidate autoComplete="off">
          <Grid container>
            <Grid item xs={12} md={6}>
              <Grid container justifyContent="center">
                <TextField
                  variant="standard"
                  required
                  ref={groupName}
                  fullWidth
                  label="Group's identifying name"
                  value={values.name}
                  onChange={handleChange("name")}
                  margin="normal"
                  error={values.nameError}
                  data-test-id="groupName"
                />
              </Grid>
            </Grid>
            <Grid item xs={12}>
              <Typography
                style={{ margin: "20px 0px 10px 0px" }}
                variant="subtitle1"
                gutterBottom
                color={"inherit"}
              >
                Select a group type*
              </Typography>
              <Select
                value={values.groupType}
                onChange={updateSelectedDropdown("groupType")}
              >
                <MenuItem value={LAB_GROUP} data-test-id="groupType-labGroup">
                  Lab Group
                </MenuItem>
                <MenuItem
                  value={PROJECT_GROUP}
                  data-test-id="groupType-projectGroup"
                >
                  Project Group
                </MenuItem>
              </Select>
            </Grid>
            <Grid item xs={12}>
              {values.groupType === LAB_GROUP && (
                <Grid container>
                  <Typography
                    style={{ margin: "20px 0px 10px 0px" }}
                    variant="subtitle1"
                    gutterBottom
                    color={values.pisError ? "error" : "inherit"}
                  >
                    Select one LabGroup PI *
                  </Typography>
                  <UserSelect
                    maxSelected={1}
                    users={users.filter((u) => u.roles.includes("ROLE_PI"))}
                    labelLeft="Available PIs"
                    labelRight="Group PIs"
                    updateSelected={updateSelected("pis")}
                  />
                </Grid>
              )}
              {values.groupType === PROJECT_GROUP && (
                <Grid container>
                  <Typography
                    style={{ margin: "20px 0px 10px 0px" }}
                    variant="subtitle1"
                    gutterBottom
                    color={values.groupOwnerError ? "error" : "inherit"}
                  >
                    Select group owners *
                  </Typography>
                  <UserSelect
                    users={users}
                    labelLeft="Available users"
                    labelRight="Group owners"
                    updateSelected={updateSelected("groupOwners")}
                  />
                </Grid>
              )}
            </Grid>
            <Grid item xs={12}>
              {values.groupType && (
                <Grid container>
                  <Typography
                    style={{ margin: "20px 0px 10px 0px" }}
                    variant="subtitle1"
                    gutterBottom
                    color="inherit"
                  >
                    Select group members{" "}
                    <span style={{ color: "grey" }}>(optional)</span>
                  </Typography>
                  <UserSelect
                    users={users}
                    labelLeft="Available users"
                    labelRight="Group members"
                    updateSelected={updateSelected("members")}
                  />
                </Grid>
              )}
            </Grid>
            <Grid item xs={12} style={{ margin: "20px 0px 40px 0px" }}>
              <Grid container justifyContent="flex-end">
                <Button
                  variant="contained"
                  color="primary"
                  onClick={validateForm}
                  data-test-id="submitGroup"
                >
                  Submit
                </Button>
              </Grid>
            </Grid>
          </Grid>
        </form>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

const domContainer = document.getElementById("newLabGroup");
const root = createRoot(domContainer);
root.render(<NewLabGroup />);
