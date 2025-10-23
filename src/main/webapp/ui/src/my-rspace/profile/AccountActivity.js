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
import TimeAgoCustom from "@/components/TimeAgoCustom";
import { stableSort, getSorting } from "../../util/table";
import EnhancedTableHead from "../../components/EnhancedTableHead";
import axios from "@/common/axios";
import { createRoot } from "react-dom/client";

const headCells = [
  { id: "eventType", numeric: false, label: "Action" },
  { id: "timestamp", numeric: true, label: "Time" },
];

export default function AccountActivity(props) {
  const [fetched, setFetched] = React.useState(false);
  const [activities, setActivities] = React.useState([]);
  const [order, setOrder] = React.useState("desc");
  const [orderBy, setOrderBy] = React.useState("timestamp");

  const loadUserActivity = () => {
    const url = `/userform/ajax/accountEventsByUser/${props.userId}`;
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
              Show account activity
            </Button>
          )}
          {fetched && (
            <>
              <div className="api-menu__header" style={{ marginTop: "15px" }}>
                User's account activity
              </div>
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
                            {row.eventType
                              .toLowerCase()
                              .replace(/./, (x) => x.toUpperCase())
                              .replace("_", " ")}
                          </TableCell>
                          <TableCell align="right">
                            <TimeAgoCustom time={row.timestamp} />
                          </TableCell>
                        </TableRow>
                      );
                    },
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

const domContainer = document.getElementById("account-activity");

if (domContainer) {
  const root = createRoot(domContainer);
  root.render(<AccountActivity userId={domContainer.dataset.userid} />);
}
