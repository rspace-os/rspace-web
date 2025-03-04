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
import { makeStyles } from "tss-react/mui";
import Tooltip from "@mui/material/Tooltip";
import IconButton from "@mui/material/IconButton";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faTrashAlt } from "@fortawesome/free-regular-svg-icons";
import { faUnlink } from "@fortawesome/free-solid-svg-icons";
library.add(faTrashAlt, faUnlink);
import OAuthDialog from "./OAuthDialog";

const headCells = [
  { id: "appName", numeric: false, label: "App Name" },
  { id: "clientId", numeric: false, label: "Client ID", disablePadding: true },
  { id: "", numeric: true, label: "Actions", align: "right" },
];

const useStyles = makeStyles()((theme) => ({
  actions: {
    display: "flex",
    justifyContent: "flex-end",
  },
}));

export default function OAuthTable(props) {
  const { classes } = useStyles();
  const [order, setOrder] = React.useState("desc");
  const [orderBy, setOrderBy] = React.useState("appName");
  const [page, setPage] = React.useState(0);
  const [rowsPerPage, setRowsPerPage] = React.useState(5);

  const [apps, setApps] = React.useState([]);
  const [fetchSuccess, setFetchSuccess] = React.useState(false);

  const fetchApps = () => {
    let urlAll = "/userform/ajax/oAuthApps";

    axios.get(urlAll).then((response) => {
      if (response.status !== 200 || response.data.exceptionMessage) {
        setApps([]);
      } else {
        setApps(response.data.data.oauthApps);
        setFetchSuccess(true);
      }
    });
  };

  useEffect(() => {
    fetchApps();
  }, []);

  const confirmDeleteApp = (clientId) => {
    let app = apps.find((a) => a.clientId == clientId);
    let event = new CustomEvent("confirm-action", {
      detail: {
        title: "Confirm deletion",
        consequences: `Are you sure you want to delete <b>${app.appName}</b>? All access and refresh tokens will be revoked.`,
        confirmTextLabel: "Type OAuth app name to confirm",
        confirmText: app ? app.appName : null,
        variant: "warning",
        callback: () => deleteApp(clientId),
      },
    });
    document.dispatchEvent(event);
  };

  const deleteApp = (clientId) => {
    let url = `/userform/ajax/oAuthApps/${clientId}`;
    axios
      .delete(url)
      .then((response) => {
        if (response.status == 200) {
          RS.confirm("App successfully deleted.", "success", 3000);
          removeApp(clientId);
        } else {
          RS.confirm(
            "There was a problem deleting this app. Please, try again.",
            "warning",
            5000
          );
        }
      })
      .catch((error) => {
        RS.confirm(error.message, "error", "infinite");
      });
  };

  const addApp = (app) => {
    setApps((oldApps) => [app, ...oldApps]);
  };

  const removeApp = (clientId) => {
    setApps((oldApps) => oldApps.filter((oa) => oa.clientId != clientId));
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
        <div style={{ flexGrow: "1", lineHeight: "42px" }}>OAuth Apps</div>
        <OAuthDialog addApp={(app) => addApp(app)} />
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
                      <TableCell align="left">{app.appName}</TableCell>
                      <TableCell align="left" padding="none">
                        {app.clientId}
                      </TableCell>
                      <TableCell align="right">
                        <div className={classes.actions}>
                          <Tooltip title="Delete" enterDelay={100}>
                            <IconButton
                              color="inherit"
                              onClick={() => confirmDeleteApp(app.clientId)}
                              style={{ width: "42px" }}
                            >
                              <FontAwesomeIcon
                                icon={["far", "trash-alt"]}
                                size="xs"
                              />
                            </IconButton>
                          </Tooltip>
                        </div>
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
