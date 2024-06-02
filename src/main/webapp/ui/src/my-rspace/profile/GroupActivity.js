"use strict";
import React from "react";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import materialTheme from "../../theme";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import Button from "@mui/material/Button";
import TimeAgoCustom from "../../components/TimeAgoCustom";
import { stableSort, getSorting } from "../../util/table";
import EnhancedTableHead from "../../components/EnhancedTableHead";
import axios from "axios";
import { createRoot } from "react-dom/client";

const headCells = [
  { id: "groupName", numeric: false, label: "Group" },
  { id: "eventType", numeric: false, label: "Action" },
  { id: "timestamp", numeric: true, label: "Time" },
];

export default function GroupActivity(props) {
  const [fetched, setFetched] = React.useState(false);
  const [activities, setActivities] = React.useState([]);
  const [order, setOrder] = React.useState("desc");
  const [orderBy, setOrderBy] = React.useState("timestamp");

  const loadUserActivity = () => {
    let url = `/groups/ajax/membershipEventsByUser/${props.userId}`;
    axios
      .get(url)
      .then((response) => {
        setFetched(true);
        if (response.data.exceptionMessage) {
          setActivities(null);
        } else {
          setActivities(response.data.data);
        }
      })
      .catch((error) => {
        console.log(error);
      });
  };

  const handleRequestSort = (event, property) => {
    const isDesc = orderBy === property && order === "desc";
    setOrder(isDesc ? "asc" : "desc");
    setOrderBy(property);
  };

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <div style={{ width: "690px", padding: "0px 15px" }}>
          {!fetched && (
            <Button color="primary" onClick={loadUserActivity}>
              Show group activity
            </Button>
          )}
          {fetched && (
            <>
              <div className="api-menu__header">User's group activity</div>
              <br />
              <Table>
                <EnhancedTableHead
                  headCells={headCells}
                  order={order}
                  orderBy={orderBy}
                  onRequestSort={handleRequestSort}
                />
                <TableBody>
                  {stableSort(activities, getSorting(order, orderBy)).map(
                    (row, index) => {
                      return (
                        <TableRow
                          data-test-id="row"
                          hover
                          tabIndex={-1}
                          key={`${row.timestamp}-${index}`}
                        >
                          <TableCell scope="row">
                            <a href={`/groups/view/${row.groupId}`}>
                              {row.groupName}
                            </a>
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
                    }
                  )}
                </TableBody>
              </Table>
            </>
          )}
        </div>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

const domContainer = document.getElementById("group-activity");

if (domContainer) {
  const root = createRoot(domContainer);
  root.render(<GroupActivity userId={domContainer.dataset.userid} />);
}
