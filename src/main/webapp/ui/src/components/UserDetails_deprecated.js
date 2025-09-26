// Written by Kristiyan - 27.12.2019
// Instructions
// If adding directly using React components, the minimum data required is
{
  /*
  <UserDetails
    userId={data.ownerId}
    fullName={data.ownerFullName}
    position={['bottom', 'right']}
  />
*/
}

import React, { useEffect } from "react";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../theme";
import Popover from "@mui/material/Popover";
import Card from "@mui/material/Card";
import Avatar from "@mui/material/Avatar";
import CardHeader from "@mui/material/CardHeader";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import Button from "@mui/material/Button";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import TimeAgo from "react-timeago";
import axios from "@/common/axios";
import { createRoot } from "react-dom/client";
import { makeStyles } from "tss-react/mui";

const useStyles = makeStyles()((theme) => ({
  text: {
    fontSize: "14px !important",
    lineHeight: "30px",
    color: "#1465b7 !important",
  },
  paper: {
    padding: theme.spacing(0),
  },
  cardContent: {
    padding: "0",
  },
}));

function UserDetails(props) {
  const { classes } = useStyles();
  const [anchorEl, setAnchorEl] = React.useState(null);
  const [user, setUser] = React.useState({
    email: "",
    fullname: "",
    profileImageLink: "",
    lastLogin: "",
    groups: [],
  });
  const [fetched, setFetched] = React.useState(false);
  const [messagingAvailable, setMessagingAvailable] = React.useState(false);

  // when clicking on "Send a message" open the messages dialog and populate the name
  // make sure the messages dialog is imported into the current page
  const sendMessage = () => {
    setAnchorEl(null);
    let recipient = `${user.username}<${user.fullname}>,`;
    $("#createRequestDlg").data("recipient", recipient).dialog("open");
  };

  // fecth user details
  const fetchUser = () => {
    let url = `/userform/ajax/miniprofile/${props.userId}`;
    axios
      .get(url)
      .then((response) => {
        if (response.data.exceptionMessage) {
          setUser(null);
        } else {
          setUser(response.data.data);
        }
      })
      .catch((error) => {
        console.error(error);
      });
    setFetched(true);
  };

  // list user groups
  const listLabgroups = () => {
    return user.groups.length ? (
      user.groups.map((group) => (
        <TableRow
          key={group.groupId}
          data-test-id={`group-${group.groupId}-${group.roleInGroup}`}
        >
          <TableCell component="th" scope="row">
            {group.roleInGroup} at
          </TableCell>
          <TableCell align="right">
            <a
              data-test-id={`group-link-${group.groupId}`}
              href={`/groups/view/${group.groupId}`}
            >
              {group.groupName}
            </a>
          </TableCell>
        </TableRow>
      ))
    ) : (
      <></>
    );
  };

  // Handle state of popup
  const open = Boolean(anchorEl) && user.email != "";
  const id = open ? `popover-${props.uniqueId}` : undefined;

  const handlePopoverOpen = (event) => {
    event.preventDefault();
    // check if user is already fetched
    if (!fetched) {
      fetchUser();

      // check if messaging is available
      if ($("body").find("[aria-describedby='messageDlg']").length) {
        setMessagingAvailable(true);
      }
    }
    setAnchorEl(event.currentTarget);
  };

  const handlePopoverClose = () => {
    setAnchorEl(null);
  };

  useEffect(() => {
    if (props.event) {
      handlePopoverOpen(props.event);
    }
  }, []);

  const label = () => {
    if (props.display == "username" && props.username) {
      return props.username;
    } else if (props.firstName) {
      return `${props.firstName} ${props.lastName}`;
    } else {
      return props.fullName;
    }
  };

  return (
    <>
      <a
        href="#"
        onClick={handlePopoverOpen}
        className={classes.text}
        aria-describedby={id}
        data-test-id={`profile-activator-${props.userId}`}
      >
        {label()}
      </a>
      <Popover
        id={id}
        open={open}
        anchorEl={anchorEl}
        onClose={handlePopoverClose}
        data-test-id={`profile-popup-${props.userId}`}
        // position ["bottom", "right"] means the popup will show on the bottom
        // aligned to the left side of the trigger element
        // position ["top", "left"] means the popup will show above the trigger elem
        // aligned to its right side
        anchorOrigin={{
          vertical: props.position[0] == "top" ? "top" : "bottom",
          horizontal: props.position[1] == "right" ? "left" : "right",
        }}
        transformOrigin={{
          vertical: props.position[0] == "top" ? "bottom" : "top",
          horizontal: props.position[1] == "right" ? "left" : "right",
        }}
        className={classes.popover}
        classes={{
          paper: classes.paper,
        }}
      >
        <Card data-test-id={`profile-card-${props.userId}`}>
          <CardHeader
            avatar={<Avatar src={user ? user.profileImageLink : ""} />}
            title={`${user.fullname}`}
            subheader={
              <>
                {user && user.lastLogin && (
                  <span>Last login: {<TimeAgo date={user.lastLogin} />}</span>
                )}
              </>
            }
          />
          {user != null && (
            <CardContent className={classes.cardContent}>
              <Table>
                <TableBody>
                  <TableRow data-test-id="row-email">
                    <TableCell component="th" scope="row">
                      Email
                    </TableCell>
                    <TableCell align="right">
                      <a
                        href={`mailto:${user.email}`}
                        data-test-id="send-email"
                      >
                        {user.email}
                      </a>
                    </TableCell>
                  </TableRow>
                  <TableRow data-test-id="row-status">
                    <TableCell component="th" scope="row">
                      Account Status
                    </TableCell>
                    <TableCell align="right">
                      {user.accountEnabled ? "Enabled" : "Disabled"}
                    </TableCell>
                  </TableRow>
                  {listLabgroups()}
                </TableBody>
              </Table>
            </CardContent>
          )}
          <CardActions>
            {messagingAvailable && (
              <Button
                size="small"
                color="primary"
                onClick={sendMessage}
                href="#"
                data-test-id="send-message"
              >
                Send a message
              </Button>
            )}
            <Button
              size="small"
              color="primary"
              href={`/userform?userId=${props.userId}`}
              data-test-id="open-profile"
            >
              Open profile
            </Button>
          </CardActions>
        </Card>
      </Popover>
    </>
  );
}

/*
 * This is necessary because as of MUI v5 useStyles cannot be used in the same
 * component as the root MuiThemeProvider
 */
export default function WrappedUserDetails(props) {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <UserDetails {...props} />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

function renderElement(domContainer, event) {
  const root = createRoot(domContainer);
  root.render(
    <WrappedUserDetails
      position={domContainer.dataset.position.split("_")}
      userId={domContainer.dataset.userid}
      uniqueId={domContainer.dataset.uniqueid}
      username={domContainer.dataset.username}
      firstName={domContainer.dataset.firstname}
      lastName={domContainer.dataset.lastname}
      display={domContainer.dataset.display}
      fullName={domContainer.dataset.fullname}
      event={event}
    />,
  );
}

$("body").on("click", ".user-details", function (e) {
  e.preventDefault();
  renderElement($(this)[0], e);
  RS.trackEvent("user:open:user_details_popover");
});
