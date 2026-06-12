import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardHeader from "@mui/material/CardHeader";
import Checkbox from "@mui/material/Checkbox";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import TextField from "@mui/material/TextField";
import React from "react";
import { stripDiacritics } from "../../../util/StringUtils";

// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
class UserList extends React.Component<any, any> {
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  constructor(props: any) {
    super(props);
    this.state = {
      searchTerm: "",
    };
  }

  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  handleSearch = (event: any) => {
    this.props.searchUsers(event.target.value);
    this.setState({ searchTerm: event.target.value });
  };

  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  isSelected = (user: any) => {
    return (
      this.props.selected.findIndex(
        // biome-ignore lint/suspicious/noDoubleEquals: initial biome migration
        // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
        (selected: any) => selected.username == user.username,
        // biome-ignore lint/suspicious/noDoubleEquals: initial biome migration
      ) != -1
    );
  };

  visibleUsers = () => {
    // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
    return this.props.users.filter((user: any) =>
      stripDiacritics(`${user.username} ${user.displayName ? user.displayName : user.fullName}`)
        .toUpperCase()
        .includes(stripDiacritics(this.state.searchTerm.toUpperCase())),
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
            data-test-id={`user-search-${this.props.listTitle.split(" ").join("-")}`}
            slotProps={{
              htmlInput: { spellcheck: "false" },
            }}
          />
          <List dense component="div" role="list" sx={{ height: "350px", overflowY: "auto" }}>
            {/* biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion */}
            {this.visibleUsers().map((user: any) => (
              <ListItem
                key={user.username}
                role="listitem"
                // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
                {...({ button: true } as any)}
                onClick={() => this.props.handleSelect(user)}
              >
                <ListItemIcon>
                  <Checkbox
                    color="primary"
                    checked={this.isSelected(user)}
                    data-test-id={`select-option-${user.username}`}
                  />
                </ListItemIcon>
                <ListItemText primary={`${user.username} - ${user.displayName ? user.displayName : user.fullName}`} />
              </ListItem>
            ))}
          </List>
        </CardContent>
      </Card>
    );
  }
}

export default UserList;
