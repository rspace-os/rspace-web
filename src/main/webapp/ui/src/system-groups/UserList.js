"use strict";
import React, { useEffect, useMemo } from "react";
import PropTypes from "prop-types";
import TextField from "@mui/material/TextField";
import FormControl from "@mui/material/FormControl";
import InputLabel from "@mui/material/InputLabel";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import Checkbox from "@mui/material/Checkbox";
import UserDetails from "@/components/UserDetails";
import { stripDiacritics } from "@/util/StringUtils";

/**
 * @param {{
 *   listTitle: string,
 *   onSelect: (users: string | Array<string>) => void,
 *   selected: Array<string>,
 *   users: Array<{
 *     id: number,
 *     username: string,
 *     displayName?: string,
 *     fullName?: string,
 *     firstName?: string,
 *     lastName?: string,
 *   }>,
 * }} props
 */
export default function UserList({ listTitle, onSelect, selected, users }) {
  const [searchTerm, setSearchTerm] = React.useState("");

  const handleSearch = (e) => {
    setSearchTerm(e.target.value);
  };

  const handleSelect = React.useCallback(
    (u) => {
      if (Array.isArray(u)) {
        onSelect(
          u.map((user) => (typeof user === "string" ? user : user.username)),
        );
      } else {
        onSelect(typeof u === "string" ? u : u.username);
      }
    },
    [onSelect],
  );

  const isSelected = (u) => {
    return selected.findIndex((s) => s === u.username) !== -1;
  };

  function userLabel(u) {
    if (u.displayName) {
      return `${u.username} - ${u.displayName}`;
    }
    if (u.fullName) {
      return `${u.username} - ${u.fullName}`;
    }
    if (u.firstName && u.lastName) {
      return `${u.username} - ${u.firstName} ${u.lastName}`;
    }
    return `${u.username}`;
  }

  function userRow(u) {
    const name = userLabel(u);

    return (
      <UserDetails
        position={["bottom", "right"]}
        userId={u.id}
        fullName={name}
        label={name}
        variant="outlined"
      />
    );
  }

  const visibleUsers = useMemo(
    () =>
      users.filter((u) =>
        stripDiacritics(userLabel(u))
          .toUpperCase()
          .includes(stripDiacritics(searchTerm.toUpperCase())),
      ),
    [searchTerm, users],
  );

  useEffect(() => {
    const visibleUsernames = visibleUsers.map((u) => u.username);
    const toUnselect = selected.filter((s) => !visibleUsernames.includes(s));

    if (toUnselect.length > 0) {
      handleSelect(toUnselect);
    }
  }, [handleSelect, selected, visibleUsers]);

  const listTitleId = listTitle.split(" ").join("-");

  return (
    <FormControl
      fullWidth
      className="grow"
      data-test-id={`${listTitleId}-column`}
    >
      <InputLabel shrink htmlFor="select-multiple-native">
        {listTitle}
      </InputLabel>
      <TextField
        variant="standard"
        fullWidth
        label="Search..."
        margin="dense"
        value={searchTerm}
        onChange={handleSearch}
        data-test-id={`user-search-${listTitleId}`}
        inputProps={{ spellcheck: "false" }}
      />
      {visibleUsers.length === 0 && (
        <div style={{ color: "grey" }} data-test-id={`${listTitleId}-empty`}>
          No users found
        </div>
      )}
      {visibleUsers.length > 0 && (
        <List
          dense
          component="div"
          role="list"
          style={{ height: "350px", overflowY: "auto" }}
          data-test-id={`${listTitleId}-list`}
        >
          {visibleUsers.map((u) => (
            <ListItem
              key={u.username}
              role="listitem"
              disablePadding
              data-test-id="row"
            >
              <ListItemButton onClick={() => handleSelect(u)}>
                <ListItemIcon>
                  <Checkbox
                    color="primary"
                    checked={isSelected(u)}
                    data-test-id={`select-option-${u.username}`}
                  />
                </ListItemIcon>
                <ListItemText primary={userRow(u)} />
              </ListItemButton>
            </ListItem>
          ))}
        </List>
      )}
    </FormControl>
  );
}

UserList.propTypes = {
  listTitle: PropTypes.string.isRequired,
  onSelect: PropTypes.func.isRequired,
  selected: PropTypes.arrayOf(PropTypes.string).isRequired,
  users: PropTypes.arrayOf(
    PropTypes.shape({
      id: PropTypes.number.isRequired,
      username: PropTypes.string.isRequired,
      displayName: PropTypes.string,
      fullName: PropTypes.string,
      firstName: PropTypes.string,
      lastName: PropTypes.string,
    }),
  ).isRequired,
};
