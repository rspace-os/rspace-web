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
import Chip from "@mui/material/Chip";
import GroupIcon from "@mui/icons-material/Group";
import { makeStyles } from "tss-react/mui";
import useGroups, { type GroupDetail } from "../hooks/api/useGroups";

const useStyles = makeStyles()((theme) => ({
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
  openGroup: {
    cursor: "pointer",
  },
}));

type GroupDetailsArgs = {
  groupId: number;
  groupName: string;
  position: ["top" | "bottom", "right" | "left"];
};

export default function GroupDetails(props: GroupDetailsArgs): React.ReactNode {
  const { classes } = useStyles();
  const [anchorEl, setAnchorEl] = React.useState<null | Element>(null);
  const [groupData, setGroupData] = React.useState<GroupDetail | null>(null);
  const [fetched, setFetched] = React.useState(false);
  const { getGroup } = useGroups();

  const fetchGroup = async () => {
    try {
      const data = await getGroup(props.groupId);
      setGroupData(data);
      setFetched(true);
    } catch (error) {
      console.error("Failed to fetch group:", error);
      setFetched(true);
    }
  };

  const handlePopoverOpen = (event: React.MouseEvent) => {
    event.preventDefault();
    event.stopPropagation();
    if (!fetched) {
      void fetchGroup();
    }
    setAnchorEl(event.currentTarget);
  };

  const handlePopoverClose = (event: Event) => {
    event.stopPropagation();
    setAnchorEl(null);
  };

  const displayData = groupData || {
    name: props.groupName,
    members: [],
    type: "LAB_GROUP",
  };

  return (
    <>
      <Chip
        clickable={true}
        component="div"
        label={props.groupName}
        data-test-id={`group-activator-${props.groupId}`}
        onClick={handlePopoverOpen}
        className={classes.chip}
      />
      <Popover
        open={Boolean(anchorEl) && fetched}
        anchorEl={anchorEl}
        onClose={handlePopoverClose}
        data-test-id={`group-popup-${props.groupId}`}
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
        <Card data-test-id={`group-card-${props.groupId}`}>
          <CardHeader
            avatar={
              <Avatar>
                <GroupIcon />
              </Avatar>
            }
            title={displayData.name}
            subheader={displayData.type.replace("_", " ")}
          />
          <CardContent className={classes.cardContent}>
            <Table>
              <TableBody>
                <TableRow data-test-id="row-members">
                  <TableCell component="th" scope="row">
                    Members
                  </TableCell>
                  <TableCell align="right">
                    {displayData.members.length}
                  </TableCell>
                </TableRow>
                <TableRow data-test-id="row-pis">
                  <TableCell component="th" scope="row">
                    Principal Investigators
                  </TableCell>
                  <TableCell align="right">
                    {displayData.members.filter((m) => m.role === "PI").length}
                  </TableCell>
                </TableRow>
                <TableRow data-test-id="row-users">
                  <TableCell component="th" scope="row">
                    Users
                  </TableCell>
                  <TableCell align="right">
                    {
                      displayData.members.filter((m) => m.role === "USER")
                        .length
                    }
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
          <CardActions>
            <Button
              component="a"
              size="small"
              color="primary"
              href={`/groups/view/${props.groupId}`}
              data-test-id="open-group"
              className={classes.openGroup}
            >
              View Group
            </Button>
          </CardActions>
        </Card>
      </Popover>
    </>
  );
}
