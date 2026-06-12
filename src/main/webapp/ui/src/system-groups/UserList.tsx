import Box from "@mui/material/Box";
import Checkbox from "@mui/material/Checkbox";
import FormControl from "@mui/material/FormControl";
import InputLabel from "@mui/material/InputLabel";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import TextField from "@mui/material/TextField";
import React, { useEffect, useMemo } from "react";
import UserDetails from "@/components/UserDetails";
import { stripDiacritics } from "@/util/StringUtils";

type User = {
  id: number;
  username: string;
  displayName?: string;
  fullName?: string;
  firstName?: string;
  lastName?: string;
};

type UserListProps = {
  listTitle: string;
  onSelect: (users: string | Array<string>) => void;
  selected: Array<string>;
  users: Array<User>;
};

export default function UserList({ listTitle, onSelect, selected, users }: UserListProps) {
  const [searchTerm, setSearchTerm] = React.useState("");

  const handleSearch = (e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchTerm(e.target.value);
  };

  const handleSelect = React.useCallback(
    (u: User | Array<User> | string) => {
      if (Array.isArray(u)) {
        onSelect(u.map((user) => (typeof user === "string" ? user : user.username)));
      } else {
        onSelect(typeof u === "string" ? u : u.username);
      }
    },
    [onSelect],
  );

  const isSelected = (u: User) => {
    return selected.indexOf(u.username) !== -1;
  };

  function userLabel(u: User) {
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

  function userRow(u: User) {
    const name = userLabel(u);

    return <UserDetails position={["bottom", "right"]} userId={u.id} fullName={name} label={name} variant="outlined" />;
  }

  const visibleUsers = useMemo(
    () =>
      users.filter((u) =>
        stripDiacritics(userLabel(u)).toUpperCase().includes(stripDiacritics(searchTerm.toUpperCase())),
      ),
    [searchTerm, users],
  );

  useEffect(() => {
    const visibleUsernames = visibleUsers.map((u) => u.username);
    const toUnselect = selected.filter((s) => !visibleUsernames.includes(s));

    if (toUnselect.length > 0) {
      handleSelect(toUnselect as unknown as Array<User>);
    }
  }, [handleSelect, selected, visibleUsers]);

  const listTitleId = listTitle.split(" ").join("-");

  return (
    <FormControl fullWidth sx={{ flexGrow: 1 }} data-test-id={`${listTitleId}-column`}>
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
        slotProps={{
          htmlInput: { spellcheck: "false" },
        }}
      />
      {visibleUsers.length === 0 && (
        <Box sx={{ color: "grey" }} data-test-id={`${listTitleId}-empty`}>
          No users found
        </Box>
      )}
      {visibleUsers.length > 0 && (
        <List
          dense
          component="div"
          role="list"
          sx={{ height: "350px", overflowY: "auto" }}
          data-test-id={`${listTitleId}-list`}
        >
          {visibleUsers.map((u) => (
            <ListItem key={u.username} role="listitem" disablePadding data-test-id="row">
              <ListItemButton onClick={() => handleSelect(u)}>
                <ListItemIcon>
                  <Checkbox color="primary" checked={isSelected(u)} data-test-id={`select-option-${u.username}`} />
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
