"use strict";
import React, { useEffect } from "react";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import TableContainer from "@mui/material/TableContainer";
import TablePagination from "@mui/material/TablePagination";
import { stableSort, getSorting, paginationOptions } from "../../util/table";
import EnhancedTableHead from "../../components/EnhancedTableHead";
import axios from "@/common/axios";
import Tooltip from "@mui/material/Tooltip";
import IconButton from "@mui/material/IconButton";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faTrashAlt } from "@fortawesome/free-regular-svg-icons";
import { faUnlink } from "@fortawesome/free-solid-svg-icons";
library.add(faTrashAlt, faUnlink);

const headCells = [
  { id: "clientName", numeric: false, label: "App Name" },
  { id: "clientId", numeric: false, label: "Client ID", disablePadding: true },
  { id: "scope", numeric: false, label: "Scope" },
  { id: "", numeric: true, label: "Actions", align: "right" },
];

export default function ConnectedAppsTable(props) {
  const [order, setOrder] = React.useState("desc");
  const [orderBy, setOrderBy] = React.useState("clientName");
  const [page, setPage] = React.useState(0);
  const [rowsPerPage, setRowsPerPage] = React.useState(5);

  const [apps, setApps] = React.useState([]);
  const [fetchSuccess, setFetchSuccess] = React.useState(false);

  const fetchApps = () => {
    let urlConnected = "/userform/ajax/oAuthConnectedApps";

    axios.get(urlConnected).then((response) => {
      if (response.status == 200) {
        setApps(response.data.data.oauthConnectedApps);
        setFetchSuccess(true);
      } else {
        setApps([]);
      }
    });
  };

  useEffect(() => {
    fetchApps();
  }, []);

  const disconnectApp = (id) => {
    axios
      .delete(`/userform/ajax/oAuthConnectedApps/${id}`)
      .then(() => {
        RS.confirm("App disconnected.", "success");
        setApps(apps.filter((app) => app.clientId != id));
      })
      .catch((error) => {
        RS.confirm(
          "Unable to remove connected app. Please, contact support if the problem persists.",
          "error",
          "infinite"
        );
      });
  };

  const confirmDisconnectApp = (clientId) => {
    let app = apps.find((a) => a.clientId == clientId);
    let event = new CustomEvent("confirm-action", {
      detail: {
        title: "Confirm disconnect",
        consequences: `Are you sure you want to revoke access from <b>${app.clientName}</b>?`,
        variant: "warning",
        callback: () => disconnectApp(clientId),
      },
    });
    document.dispatchEvent(event);
  };

  const handleRequestSort = (event, property) => {
    const isDesc = orderBy === property && order === "desc";
    setOrder(isDesc ? "asc" : "desc");
    setOrderBy(property);
    setPage(0);
  };

  const handleChangePage = (event, newPage) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  return (
    <div style={{ width: "690px", padding: "0px 15px" }}>
      <div
        className="api-menu__header"
        style={{ marginTop: "15px", display: "flex" }}
      >
        <div style={{ flexGrow: "1", lineHeight: "42px" }}>Connected Apps</div>
      </div>
      <br />
      {fetchSuccess && (
        <>
          <TableContainer>
            <Table size="small" aria-label="enhanced table">
              <EnhancedTableHead
                headCells={headCells}
                order={order}
                orderBy={orderBy}
                onRequestSort={handleRequestSort}
                rowCount={apps.length}
              />
              <TableBody>
                {stableSort(apps, getSorting(order, orderBy))
                  .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                  .map((app) => (
                    <TableRow hover tabIndex={-1} key={app.clientId}>
                      <TableCell align="left">{app.clientName}</TableCell>
                      <TableCell align="left" padding="none">
                        {app.clientId}
                      </TableCell>
                      <TableCell align="left">{app.scope}</TableCell>
                      <TableCell align="right">
                        <Tooltip title="Disconnect" enterDelay={100}>
                          <IconButton
                            color="inherit"
                            onClick={() => confirmDisconnectApp(app.clientId)}
                            style={{ width: "42px" }}
                          >
                            <FontAwesomeIcon
                              icon={["fa", "unlink"]}
                              size="xs"
                            />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            rowsPerPageOptions={paginationOptions(apps.length)}
            component="div"
            count={apps.length}
            rowsPerPage={rowsPerPage}
            page={page}
            onPageChange={handleChangePage}
            onRowsPerPageChange={handleChangeRowsPerPage}
          />
        </>
      )}
      {!fetchSuccess && (
        <>There was a problem fetching your apps. Please, try again.</>
      )}
    </div>
  );
}
