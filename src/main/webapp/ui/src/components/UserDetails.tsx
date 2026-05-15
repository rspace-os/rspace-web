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
import { type PersonId } from "@/stores/definitions/Person";
import Chip, { type ChipProps } from "@mui/material/Chip";
import * as Parsers from "../util/parsers";

type UserDetailsArgs = {
  userId: PersonId;
  fullName: string;
  position: ["top" | "bottom", "right" | "left"];
  label?: string;
  variant?: ChipProps["variant"];
  allowMessaging?: boolean;
  onOpen?: () => void;
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
  username?: string;
  profileImageLink: string;
  email: string;
  accountEnabled: boolean;
};

export default function UserDetails(props: UserDetailsArgs): React.ReactNode {
  const variant = props.variant ?? "filled";
  const [anchorEl, setAnchorEl] = React.useState<null | Element>(null);
  const [user, setUser] = React.useState<Person | null>(null);
  const [fetched, setFetched] = React.useState(false);
  const [messagingAvailable, setMessagingAvailable] = React.useState(false);

  const sendMessage = () => {
    if (!user?.username) {
      return;
    }

    setAnchorEl(null);
    const recipient = `${user.username}<${user.fullname}>,`;
    const dialog = $("#createRequestDlg");
    dialog.data("recipient", recipient);
    (dialog as JQuery<HTMLElement> & { dialog: (action: string) => void }).dialog(
      "open",
    );
  };

  const fetchUser = () => {
    const url = `/userform/ajax/miniprofile/${props.userId}`;
    axios
      .get<{ exceptionMessage: string } | { data: Person }>(url)
      .then((response) => {
        setUser(
          Parsers.getValueWithKey("data")(response.data).orElse(
            null,
          ) as Person | null,
        );
      })
      .catch((error) => {
        console.error(error);
      });
    setFetched(true);
  };

  const listLabgroups = user?.groups.map((group) => (
    <TableRow key={group.groupId}>
      <TableCell component="th" scope="row">
        {group.roleInGroup} at
      </TableCell>
      <TableCell align="right">
        <a href={`/groups/view/${group.groupId}`}>
          {group.groupName}
        </a>
      </TableCell>
    </TableRow>
  )) ?? <></>;

  const handlePopoverOpen = (event: React.MouseEvent) => {
    event.preventDefault();
    event.stopPropagation();
    props.onOpen?.();
    if (!fetched) {
      fetchUser();
      if (
        props.allowMessaging &&
        $("#createRequestDlg").length > 0 &&
        $("body").find("[aria-describedby='messageDlg']").length > 0
      ) {
        setMessagingAvailable(true);
      }
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
        variant={variant}
        label={props.label ?? props.fullName}
        onClick={handlePopoverOpen}
        sx={(theme) => ({
          mt: 0.5,
          mb: 0.5,
          height: theme.spacing(3),
          cursor: "default",
          ...(variant === "filled"
            ? {
                backgroundColor: theme.palette.grey[300],
                color: theme.palette.grey[800],
                fontWeight: theme.typography.fontWeightRegular,
              }
            : {}),
        })}
      />
      <Popover
        open={Boolean(anchorEl) && Boolean(user)}
        anchorEl={anchorEl}
        onClose={handlePopoverClose}
        anchorOrigin={{
          vertical: props.position[0] === "top" ? "top" : "bottom",
          horizontal: props.position[1] === "right" ? "left" : "right",
        }}
        transformOrigin={{
          vertical: props.position[0] === "top" ? "bottom" : "top",
          horizontal: props.position[1] === "right" ? "left" : "right",
        }}
        slotProps={{
          paper: {
            sx: {
              p: 0,
            },
          },
        }}
      >
        {user && (
          <Card>
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
            <CardContent
              sx={{
                p: 0,
                "&:last-child": {
                  pb: 0,
                },
              }}
            >
              <Table sx={{
                // Override typo.css
                margin: '0 !important'
              }}>
                <TableBody>
                  <TableRow>
                    <TableCell component="th" scope="row">
                      Email
                    </TableCell>
                    <TableCell align="right">
                      <a href={`mailto:${user.email}`}>
                        {user.email}
                      </a>
                    </TableCell>
                  </TableRow>
                  <TableRow>
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
              {messagingAvailable && user.username && (
                <Button
                  size="small"
                  color="primary"
                  onClick={(event) => {
                    event.preventDefault();
                    event.stopPropagation();
                    sendMessage();
                  }}
                  href="#"
                >
                  Send a message
                </Button>
              )}
              <Button
                component="a"
                size="small"
                color="primary"
                href={`/userform?userId=${props.userId}`}
                sx={{
                  cursor: "pointer",
                }}
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
