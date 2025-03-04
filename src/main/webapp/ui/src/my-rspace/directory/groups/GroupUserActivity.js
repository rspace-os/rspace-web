"use strict";
import React, { useEffect } from "react";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import TimeAgoCustom from "../../../components/TimeAgoCustom";
import UserDetails from "./../../../components/UserDetails";
import EnhancedTableHead from "../../../components/EnhancedTableHead";
import { desc, stableSort, getSorting } from "../../../util/table";
import axios from "@/common/axios";
import { createRoot } from "react-dom/client";

const headCells = [
  { id: "userFullName", numeric: false, label: "User" },
  { id: "eventType", numeric: false, label: "Action" },
  { id: "timestamp", numeric: true, label: "Time" },
];

export default function GroupActivity(props) {
  const [activities, setActivities] = React.useState([]);
  const [order, setOrder] = React.useState("desc");
  const [orderBy, setOrderBy] = React.useState("timestamp");

  useEffect(() => {
    let url = `/groups/ajax/membershipEventsByGroup/${props.groupId}`;
    axios
      .get(url)
      .then((response) => {
        if (response.data.exceptionMessage) {
          setActivities(null);
        } else {
          setActivities(response.data.data);
        }
      })
      .catch((error) => {
        console.log(error);
      });
  }, []);

  const handleRequestSort = (event, property) => {
    const isDesc = orderBy === property && order === "desc";
    setOrder(isDesc ? "asc" : "desc");
    setOrderBy(property);
  };

  return (
    <>
      <h3>Group Activity</h3>
      <Table>
        <EnhancedTableHead
          headCells={headCells}
          order={order}
          orderBy={orderBy}
          onRequestSort={handleRequestSort}
        />
        <TableBody>
          {stableSort(activities, getSorting(order, orderBy)).map((row) => {
            return (
              <TableRow hover tabIndex={-1} key={row.eventId}>
                <TableCell scope="row">
                  <UserDetails
                    position={["top", "right"]}
                    userId={row.userId}
                    fullName={row.userFullName}
                    uniqueId={row.timestamp}
                  />
                </TableCell>
                <TableCell>
                  {row.eventType
                    .toLowerCase()
                    .replace(/./, (x) => x.toUpperCase())}
                </TableCell>
                <TableCell align="right">
                  <TimeAgoCustom time={row.timestamp} />
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </>
  );
}

const domContainer = document.getElementById("groupActivity");
const root = createRoot(domContainer);
root.render(<GroupActivity groupId={domContainer.dataset.groupid} />);
