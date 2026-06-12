import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import MenuItem from "@mui/material/MenuItem";
import Select from "@mui/material/Select";
import Stack from "@mui/material/Stack";
import { ThemeProvider } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { useEffect, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import axios from "@/common/axios";
import materialTheme from "../theme";
import UserSelect from "./UserBox";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
const scrollToRef = (ref: any) => window.scrollTo(0, ref.current.offsetTop);
const PROJECT_GROUP = "PROJECT_GROUP";
const LAB_GROUP = "LAB_GROUP";

export default function NewLabGroup() {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const [values, setValues] = useState<any>({
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
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const [users, setUsers] = useState<any[]>([]);
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

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const handleChange = (field: any) => (event: any) => {
    setValues({ ...values, [field]: event.target.value });
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const updateSelected = (field: any) => (users: any) => {
    setValues({ ...values, [field]: users });
  };

  const updateSelectedDropdown =
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    (field: any) =>
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    ({ target: { value } }: { target: { value: any } }) => {
      setValues({ ...values, [field]: value });
    };

  function validateForm() {
    if (values.name.length === 0) {
      executeScroll();
      setValues({ ...values, nameError: true });
      return;
    }
    setValues({ ...values, nameError: false });

    if (values.groupType === LAB_GROUP && values.pis.length !== 1) {
      setValues({ ...values, pisError: "Please, select a PI" });
    } else if (values.groupType === PROJECT_GROUP && values.groupOwners.length < 1) {
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

    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    const params: any = {};
    params._memberString = 1;
    params.cancel = "Add group";
    params.pis = values.pis.length ? values.pis[0].username : values.pis;
    params.groupOwners = values.groupOwners.length
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      ? values.groupOwners.map((m: any) => m.username)
      : values.groupOwners;
    params.groupType = values.groupType;
    params.memberString = values.name;
    params.displayName = values.name;
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    params.memberString = values.members.map((m: any) => m.username);
    if (values.groupType === LAB_GROUP) {
      params.memberString = params.memberString.concat(values.pis[0].username);
    } else if (values.groupType === PROJECT_GROUP) {
      params.memberString = params.memberString.concat(
        // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
        ...params.groupOwners.filter((owner: any) => !params.memberString.includes(owner)),
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
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={materialTheme}>
        <form noValidate autoComplete="off">
          <Grid container>
            <Grid size={{ xs: 12, md: 6 }}>
              <Grid container sx={{ justifyContent: "center" }}>
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
            <Grid size={12}>
              <Stack spacing={2}>
                <Typography variant="subtitle1" gutterBottom color={"inherit"}>
                  Select a group type*
                </Typography>
                <Select
                  value={values.groupType}
                  onChange={updateSelectedDropdown("groupType")}
                  variant="standard"
                  size="small"
                >
                  <MenuItem value={LAB_GROUP} data-test-id="groupType-labGroup">
                    Lab Group
                  </MenuItem>
                  <MenuItem value={PROJECT_GROUP} data-test-id="groupType-projectGroup">
                    Project Group
                  </MenuItem>
                </Select>
                {values.groupType === PROJECT_GROUP && (
                  <Typography variant="body2">
                    If RAiD has been set up, you can associate a RAiD identifier with the project group after it has
                    been created.
                  </Typography>
                )}
              </Stack>
            </Grid>
            <Grid size={12}>
              {values.groupType === LAB_GROUP && (
                <Grid container>
                  <Typography
                    sx={{ margin: "20px 0px 10px 0px" }}
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
                    sx={{ margin: "20px 0px 10px 0px" }}
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
            <Grid size={12}>
              {values.groupType && (
                <Grid container>
                  <Typography sx={{ margin: "20px 0px 10px 0px" }} variant="subtitle1" gutterBottom color="inherit">
                    Select group members{" "}
                    <Typography variant="inherit" component="span" sx={{ color: "grey" }}>
                      (optional)
                    </Typography>
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
            <Grid size={12} sx={{ margin: "20px 0px 40px 0px" }}>
              <Grid container sx={{ justifyContent: "flex-end" }}>
                <Button variant="contained" color="primary" onClick={validateForm} data-test-id="submitGroup">
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
const root = createRoot(domContainer as HTMLElement);
root.render(<NewLabGroup />);
