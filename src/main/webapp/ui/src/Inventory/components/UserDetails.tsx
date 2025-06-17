import React from "react";
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
import { makeStyles } from "tss-react/mui";
import { type PersonId } from "../../stores/definitions/Person";
import Chip from "@mui/material/Chip";
import * as Parsers from "../../util/parsers";

const useStyles = makeStyles()((theme) => ({
  text: {
    fontSize: "16px !important",
    lineHeight: "30px",
    textDecoration: "none",
    fontWeight: "bold",
    color: "#1465b7 !important",
  },
  paper: {
    padding: theme.spacing(0),
  },
  cardContent: {
    padding: "0",
  },
  chip: {
    marginTop: theme.spacing(0.5),
    marginBottom: theme.spacing(0.5),
    backgroundColor: theme.palette.grey[300],
    height: theme.spacing(3),
    color: theme.palette.grey[800],
    fontWeight: theme.typography.fontWeightRegular,
    cursor: "default",
  },
  openProfile: {
    cursor: "pointer",
  },
}));

type UserDetailsArgs = {
  userId: PersonId;
  fullName: string;
  position: ["top" | "bottom", "right" | "left"];
};

type Group = {
  groupId: number;
  roleInGroup: "PI" | "User";
  groupName: string;
};
type Person = {
  groups: Array<Group>;
  lastLogin: string;
  fullname: string;
  profileImageLink: string;
  email: string;
  accountEnabled: boolean;
};

export default function UserDetails(props: UserDetailsArgs): React.ReactNode {
  const { classes } = useStyles();
  const [anchorEl, setAnchorEl] = React.useState<null | Element>(null);
  const [user, setUser] = React.useState<Person | null>(null);
  const [fetched, setFetched] = React.useState(false);

  const fetchUser = () => {
    const url = `/userform/ajax/miniprofile/${props.userId}`;
    axios
      .get<{ exceptionMessage: string } | { data: Person }>(url)
      .then((response) => {
        setUser(
          Parsers.getValueWithKey("data")(response.data).orElse(
            null
          ) as Person | null
        );
      })
      .catch((error) => {
        console.error(error);
      });
    setFetched(true);
  };

  const listLabgroups = user?.groups.map((group) => (
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
  )) ?? <></>;

  const handlePopoverOpen = (event: React.MouseEvent) => {
    event.preventDefault();
    event.stopPropagation();
    if (!fetched) {
      fetchUser();
    }
    setAnchorEl(event.currentTarget);
  };

  const handlePopoverClose = (event: Event) => {
    event.stopPropagation();
    setAnchorEl(null);
  };

  return (
    <>
      <Chip
        clickable={true}
        component="div"
        label={props.fullName}
        data-test-id={`profile-activator-${props.userId}`}
        onClick={handlePopoverOpen}
        className={classes.chip}
      />
      <Popover
        open={Boolean(anchorEl) && Boolean(user)}
        anchorEl={anchorEl}
        onClose={handlePopoverClose}
        data-test-id={`profile-popup-${props.userId}`}
        anchorOrigin={{
          vertical: props.position[0] === "top" ? "top" : "bottom",
          horizontal: props.position[1] === "right" ? "left" : "right",
        }}
        transformOrigin={{
          vertical: props.position[0] === "top" ? "bottom" : "top",
          horizontal: props.position[1] === "right" ? "left" : "right",
        }}
        classes={{
          paper: classes.paper,
        }}
      >
        {user && (
          <Card data-test-id={`profile-card-${props.userId}`}>
            <CardHeader
              avatar={<Avatar src={user.profileImageLink ?? ""} />}
              title={`${user.fullname}`}
              subheader={
                <>
                  {user && user.lastLogin && (
                    <span>Last login: {<TimeAgo date={user.lastLogin} />}</span>
                  )}
                </>
              }
            />
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
                  {listLabgroups}
                </TableBody>
              </Table>
            </CardContent>
            <CardActions>
              <Button
                component="a"
                size="small"
                color="primary"
                href={`/userform?userId=${props.userId}`}
                data-test-id="open-profile"
                className={classes.openProfile}
              >
                Open profile
              </Button>
            </CardActions>
          </Card>
        )}
      </Popover>
    </>
  );
}
