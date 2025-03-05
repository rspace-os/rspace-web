"use strict";
import React, { useEffect } from "react";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import TableContainer from "@mui/material/TableContainer";
import { createRoot } from "react-dom/client";

import { stableSort, getSorting } from "../../util/table";
import EnhancedTableHead from "../../components/EnhancedTableHead";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";

import materialTheme from "../../theme";
import axios from "@/common/axios";
import AutoshareStatus from "./Autoshare/AutoshareStatus";

export default function GroupsTable(props) {
  const [groups, setGroups] = React.useState([]);
  const [isCurrentlySharing, setIsCurrentlySharing] = React.useState(false);
  const [order, setOrder] = React.useState("desc");
  const [orderBy, setOrderBy] = React.useState("appName");
  const [fetchSuccess, setFetchSuccess] = React.useState(true);

  useEffect(() => {
    fetchGroups();
  }, []);

  const fetchGroups = () => {
    let url = `/userform/ajax/userGroupInfo/${props.userId}`;

    axios.get(url).then((response) => {
      if (response.status != 200 || response.data.exceptionMessage) {
        setGroups([]);
        setFetchSuccess(false);
      } else {
        setGroups(response.data.data);
        fetchShareStatus();
      }
    });
  };

  const fetchShareStatus = () => {
    let url = `/userform/ajax/autoshareInProgress`;

    axios.get(url).then((response) => {
      setIsCurrentlySharing(response.data.data);

      if (response.data.data) {
        setTimeout(() => {
          fetchShareStatus();
        }, 3000);
      }
    });
  };

  const handleRequestSort = (event, property) => {
    const isDesc = orderBy === property && order === "desc";
    setOrder(isDesc ? "asc" : "desc");
    setOrderBy(property);
    setPage(0);
  };

  const tableHeaders = () => {
    return [
      { id: "groupName", numeric: false, label: "Group" },
      ...(props.privateGroup
        ? []
        : [{ id: "role", numeric: false, label: "Role" }]),
      ...(props.privateGroup
        ? []
        : [
            {
              id: "status",
              numeric: false,
              label: "Autosharing",
            },
          ]),
      ...(props.canEdit
        ? [{ id: "actions", numeric: false, label: "Actions" }]
        : []),
    ];
  };

  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <div style={{ width: "690px", padding: "0px 15px" }}>
          <div
            className="api-menu__header"
            style={{ marginTop: "15px", display: "flex" }}
          >
            <div style={{ flexGrow: "1", lineHeight: "42px" }}>Groups</div>
          </div>
          <br />
          {groups.length > 0 && fetchSuccess && (
            <TableContainer>
              <Table size="small" aria-label="enhanced table">
                <EnhancedTableHead
                  headCells={tableHeaders()}
                  order={order}
                  orderBy={orderBy}
                  onRequestSort={handleRequestSort}
                />
                <TableBody>
                  {stableSort(groups, getSorting(order, orderBy)).map(
                    (group) => (
                      <TableRow
                        hover
                        tabIndex={-1}
                        key={group.groupDisplayName}
                      >
                        <TableCell align="left">
                          <a href={`/groups/view/${group.groupId}`}>
                            {group.groupDisplayName}
                          </a>
                        </TableCell>
                        {!group.privateGroup && (
                          <>
                            <TableCell align="left">
                              {group.roleInGroup}
                            </TableCell>
                            <TableCell align="left">
                              {group.autoshareEnabled
                                ? "Enabled"
                                : group.labGroup
                                ? "Disabled"
                                : "n/a"}
                            </TableCell>
                          </>
                        )}
                        {props.canEdit && (
                          <TableCell>
                            <AutoshareStatus
                              group={group}
                              username={props.username}
                              userId={props.userId}
                              isCurrentlySharing={isCurrentlySharing}
                              callback={() => fetchGroups()}
                              isSwitch={false}
                              isSwitchDisabled={false}
                            />
                          </TableCell>
                        )}
                      </TableRow>
                    )
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </div>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

const domContainer = document.getElementById("labgroups-table");

if (domContainer) {
  const root = createRoot(domContainer);
  root.render(
    <GroupsTable
      username={domContainer.dataset.username}
      userId={domContainer.dataset.userid}
      canEdit={domContainer.dataset.canedit === "true"}
    />
  );
}
