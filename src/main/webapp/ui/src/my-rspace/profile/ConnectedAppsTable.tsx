import Box from "@mui/material/Box";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableContainer from "@mui/material/TableContainer";
import TablePagination from "@mui/material/TablePagination";
import { type ChangeEvent, useContext, useEffect, useState } from "react";
import axios from "@/common/axios";
import ConnectedAppsTableRow from "@/my-rspace/profile/ConnectedAppsTableRow";
import type { ConnectedOAuthApp } from "@/my-rspace/profile/types";
import AlertContext, { mkAlert } from "@/stores/contexts/Alert";
import { getSorting, paginationOptions } from "@/util/table";
import EnhancedTableHead from "../../components/EnhancedTableHead";

const headCells = [
  { id: "clientName", numeric: false, label: "App Name" },
  { id: "clientId", numeric: false, label: "Client ID", disablePadding: true },
  { id: "scope", numeric: false, label: "Scope" },
  { id: "", numeric: true, label: "Actions", align: "right" },
];

export default function ConnectedAppsTable() {
  const [order, setOrder] = useState<"asc" | "desc">("desc");
  const [orderBy, setOrderBy] = useState<string>("clientName");
  const [page, setPage] = useState<number>(0);
  const [rowsPerPage, setRowsPerPage] = useState<number>(5);
  const { addAlert } = useContext(AlertContext);

  const [apps, setApps] = useState<ConnectedOAuthApp[]>([]);
  const [fetchSuccess, setFetchSuccess] = useState<boolean>(false);

  const fetchApps = async () => {
    const urlConnected = "/userform/ajax/oAuthConnectedApps";
    try {
      const response = await axios.get<{
        data: {
          oauthConnectedApps: ConnectedOAuthApp[];
        };
      }>(urlConnected);
      if (response.status === 200) {
        setApps(response.data.data.oauthConnectedApps);
        setFetchSuccess(true);
      } else {
        setApps([]);
      }
    } catch {
      setApps([]);
    }
  };

  useEffect(() => {
    void fetchApps();
  }, []);

  const handleConfirmDisconnectApp = async (id: string) => {
    try {
      await axios.delete(`/userform/ajax/oAuthConnectedApps/${id}`);
      addAlert(
        mkAlert({
          title: "App disconnected",
          message: `App with client ID ${id} was successfully disconnected.`,
          variant: "success",
        }),
      );
      setApps(apps.filter((app) => app.clientId !== id));
    } catch {
      addAlert(
        mkAlert({
          title: "Unable to disconnect app",
          message: `There was a problem disconnecting app with client ID ${id}.  Please contact support if the problem persists.`,
          variant: "error",
          isInfinite: true,
        }),
      );
    }
  };

  const handleRequestSort = (_: unknown, property: string) => {
    const isDesc = orderBy === property && order === "desc";
    setOrder(isDesc ? "asc" : "desc");
    setOrderBy(property);
    setPage(0);
  };

  const handleChangePage = (_: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (event: ChangeEvent<HTMLTextAreaElement | HTMLInputElement>) => {
    setRowsPerPage(parseInt(event.target.value, 10));
    setPage(0);
  };

  return (
    <Box sx={{ width: "690px", padding: "0px 15px" }}>
      <Box className="api-menu__header" sx={{ marginTop: "15px", display: "flex" }}>
        <Box sx={{ flexGrow: "1", lineHeight: "42px" }}>Connected Apps</Box>
      </Box>
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
                {apps
                  // @ts-expect-error getSorting types its comparator params more loosely than the row type
                  .toSorted(getSorting(order, orderBy))
                  .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                  .map((app) => (
                    <ConnectedAppsTableRow
                      app={app}
                      onConfirmDisconnectApp={() => handleConfirmDisconnectApp(app.clientId)}
                      key={app.clientId}
                    />
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
      {!fetchSuccess && <>There was a problem fetching your apps. Please, try again.</>}
    </Box>
  );
}
