"use strict";
import React, { useEffect } from "react";
import TextField from "@mui/material/TextField";
import FormControl from "@mui/material/FormControl";
import InputLabel from "@mui/material/InputLabel";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import Checkbox from "@mui/material/Checkbox";
import UserDetails from "../components/UserDetails";
import { stripDiacritics } from "../util/StringUtils";

export default function UserList(props) {
  const [searchTerm, setSearchTerm] = React.useState("");
  const [visibleUsers, setVisibleUsers] = React.useState([]);

  const handleSearch = (e) => {
    setSearchTerm(e.target.value);
    filterVisibleUsers(e.target.value);
  };

  const handleSelect = (u) => {
    if (Array.isArray(u)) {
      props.onSelect(
        u.map((user) => (typeof user === "string" ? user : user.username))
      );
    } else {
      props.onSelect(typeof u === "string" ? u : u.username);
    }
  };

  const isSelected = (u) => {
    return props.selected.findIndex((s) => s == u.username) != -1;
  };

  function userLabel(u) {
    if (u.displayName) {
      return `${u.username} - ${u.displayName}`;
    } else if (u.fullName) {
      return `${u.username} - ${u.fullName}`;
    } else if (u.firstName && u.lastName) {
      return `${u.username} - ${u.firstName} ${u.lastName}`;
    } else {
      return `${u.username}`;
    }
  }

  function userRow(u) {
    let name = userLabel(u);

    return (
      <UserDetails
        position={["bottom", "right"]}
        userId={u.id}
        uniqueId={u.id}
        username={name}
        display="username"
      />
    );
  }

  function filterVisibleUsers(search) {
    const visible = props.users.filter((u) =>
      stripDiacritics(userLabel(u))
        .toUpperCase()
        .includes(stripDiacritics(search.toUpperCase()))
    );
    setVisibleUsers(visible);
    unselectFilteredUsers(visible);
  }

  function unselectFilteredUsers(visible) {
    visible = visible.map((u) => u.username);
    let to_unselect = props.selected.filter((s) => !visible.includes(s));
    handleSelect(to_unselect);
  }

  useEffect(() => {
    setVisibleUsers(props.users);
    filterVisibleUsers(searchTerm);
  }, [props.users]);

  return (
    <FormControl
      fullWidth
      className="grow"
      data-test-id={`${props.listTitle.split(" ").join("-")}-column`}
    >
      <InputLabel shrink htmlFor="select-multiple-native">
        {props.listTitle}
      </InputLabel>
      <TextField
        variant="standard"
        fullWidth
        label="Search..."
        margin="dense"
        value={searchTerm}
        onChange={handleSearch}
        data-test-id={`user-search-${props.listTitle.split(" ").join("-")}`}
        inputProps={{ spellcheck: "false" }}
      />
      {visibleUsers.length == 0 && (
        <div
          style={{ color: "grey" }}
          data-test-id={`${props.listTitle.split(" ").join("-")}-empty`}
        >
          No users found
        </div>
      )}
      {visibleUsers.length > 0 && (
        <List
          dense
          component="div"
          role="list"
          style={{ height: "350px", overflowY: "auto" }}
          data-test-id={`${props.listTitle.split(" ").join("-")}-list`}
        >
          {visibleUsers.map((u) => (
            <ListItem
              key={u.username}
              role="listitem"
              button
              onClick={(e) => handleSelect(u)}
              data-test-id="row"
            >
              <ListItemIcon>
                <Checkbox
                  color="primary"
                  checked={isSelected(u)}
                  data-test-id={`select-option-${u.username}`}
                />
              </ListItemIcon>
              <ListItemText primary={userRow(u)} />
            </ListItem>
          ))}
        </List>
      )}
    </FormControl>
  );
}
