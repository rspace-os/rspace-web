import Box from "@mui/material/Box";
import { ThemeProvider } from "@mui/material/styles";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableRow from "@mui/material/TableRow";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React, { useEffect } from "react";
import { createRoot } from "react-dom/client";
import axios from "@/common/axios";
import Alerts from "@/components/Alerts/Alerts";
import EnhancedTableHead from "../../components/EnhancedTableHead";

import materialTheme from "../../theme";
import { getSorting } from "../../util/table";
import type { Order } from "../../util/types";
import AutoshareStatus from "./Autoshare/AutoshareStatus";

// Pre-existing global reference; declared to satisfy the type checker without
// altering the original runtime behaviour.
declare function setPage(n: number): void;

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
export default function GroupsTable(props: any) {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const [groups, setGroups] = React.useState<any[]>([]);
  const [isCurrentlySharing, setIsCurrentlySharing] = React.useState(false);
  const [order, setOrder] = React.useState<Order>("desc");
  const [orderBy, setOrderBy] = React.useState("appName");
  const [fetchSuccess, setFetchSuccess] = React.useState(true);

  useEffect(() => {
    fetchGroups();
  }, []);

  const fetchGroups = () => {
    const url = `/userform/ajax/userGroupInfo/${props.userId}`;

    axios.get(url).then((response) => {
      if (response.status !== 200 || response.data.exceptionMessage) {
        setGroups([]);
        setFetchSuccess(false);
      } else {
        setGroups(response.data.data);
        fetchShareStatus();
      }
    });
  };

  const fetchShareStatus = () => {
    const url = `/userform/ajax/autoshareInProgress`;

    axios.get(url).then((response) => {
      setIsCurrentlySharing(response.data.data);

      if (response.data.data) {
        setTimeout(() => {
          fetchShareStatus();
        }, 3000);
      }
    });
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const handleRequestSort = (_event: any, property: any) => {
    const isDesc = orderBy === property && order === "desc";
    setOrder(isDesc ? "asc" : "desc");
    setOrderBy(property);
    setPage(0);
  };

  const tableHeaders = () => {
    return [
      { id: "groupName", numeric: false, label: "Group" },
      ...(props.privateGroup ? [] : [{ id: "role", numeric: false, label: "Role" }]),
      ...(props.privateGroup
        ? []
        : [
            {
              id: "status",
              numeric: false,
              label: "Autosharing",
            },
          ]),
      ...(props.canEdit ? [{ id: "actions", numeric: false, label: "Actions" }] : []),
    ];
  };

  return (
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={materialTheme}>
        <Alerts>
          <Box sx={{ width: "690px", padding: "0px 15px" }}>
            <Box className="api-menu__header" sx={{ marginTop: "15px", display: "flex" }}>
              <Box sx={{ flexGrow: "1", lineHeight: "42px" }}>Groups</Box>
            </Box>
            <br />
            {groups.length > 0 && fetchSuccess && (
              <TableContainer>
                <Table size="small" aria-label="enhanced table">
                  <EnhancedTableHead
                    headCells={tableHeaders()}
                    order={order}
                    orderBy={orderBy}
                    onRequestSort={handleRequestSort}
                    rowCount={0}
                  />
                  <TableBody>
                    {/** biome-ignore lint/suspicious/noExplicitAny: initial biome migration */}
                    {groups.toSorted(getSorting(order, orderBy)).map((group: any) => (
                      <TableRow hover tabIndex={-1} key={group.groupDisplayName}>
                        <TableCell align="left">
                          <a href={`/groups/view/${group.groupId}`}>{group.groupDisplayName}</a>
                        </TableCell>
                        {!group.privateGroup && (
                          <>
                            <TableCell align="left">{group.roleInGroup}</TableCell>
                            <TableCell align="left">
                              {group.autoshareEnabled ? "Enabled" : group.labGroup ? "Disabled" : "n/a"}
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
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Box>
        </Alerts>
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
    />,
  );
}
