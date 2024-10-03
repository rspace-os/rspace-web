"use strict";
import React from "react";
import TextField from "@mui/material/TextField";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import Checkbox from "@mui/material/Checkbox";
import Card from "@mui/material/Card";
import CardHeader from "@mui/material/CardHeader";
import CardContent from "@mui/material/CardContent";
import { stripDiacritics } from "../../../util/StringUtils";

class UserList extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      searchTerm: "",
    };
  }

  handleSearch = (event) => {
    this.props.searchUsers(event.target.value);
    this.setState({ searchTerm: event.target.value });
  };

  isSelected = (user) => {
    return (
      this.props.selected.findIndex(
        (selected) => selected.username == user.username
      ) != -1
    );
  };

  visibleUsers = () => {
    return this.props.users.filter((user) =>
      stripDiacritics(
        `${user.username} ${
          user.displayName ? user.displayName : user.fullName
        }`
      )
        .toUpperCase()
        .includes(stripDiacritics(this.state.searchTerm.toUpperCase()))
    );
  };

  render() {
    return (
      <Card variant="outlined">
        <CardHeader
          title={this.props.listTitle}
          titleTypographyProps={{
            component: "h3",
            variant: "h6",
          }}
          sx={{ p: 1, pb: 0 }}
        />
        <CardContent sx={{ p: 1, pt: 0 }}>
          <TextField
            variant="standard"
            fullWidth
            id="userSearch"
            label="Search..."
            type="search"
            margin="dense"
            value={this.state.searchTerm}
            onChange={this.handleSearch}
            data-test-id={`user-search-${this.props.listTitle
              .split(" ")
              .join("-")}`}
          />
          <List
            dense
            component="div"
            role="list"
            style={{ height: "350px", overflowY: "auto" }}
          >
            {this.visibleUsers().map((user) => (
              <ListItem
                key={user.username}
                role="listitem"
                button
                onClick={() => this.props.handleSelect(user)}
              >
                <ListItemIcon>
                  <Checkbox
                    color="primary"
                    checked={this.isSelected(user)}
                    data-test-id={`select-option-${user.username}`}
                  />
                </ListItemIcon>
                <ListItemText
                  primary={`${user.username} - ${
                    user.displayName ? user.displayName : user.fullName
                  }`}
                />
              </ListItem>
            ))}
          </List>
        </CardContent>
      </Card>
    );
  }
}

export default UserList;
