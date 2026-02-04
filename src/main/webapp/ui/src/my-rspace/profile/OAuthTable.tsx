import { ChangeEvent, useContext, useEffect, useState } from "react";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableContainer from "@mui/material/TableContainer";
import TablePagination from "@mui/material/TablePagination";
import { stableSort, getSorting, paginationOptions } from "@/util/table";
import EnhancedTableHead from "../../components/EnhancedTableHead";
import axios from "@/common/axios";
import OAuthDialog from "./OAuthDialog";
import AlertContext, { mkAlert } from "@/stores/contexts/Alert";
import { OAuthApp } from "@/my-rspace/profile/types";
import OAuthTableRow from "@/my-rspace/profile/OAuthTableRow";

const headCells = [
  { id: "appName", numeric: false, label: "App Name" },
  { id: "clientId", numeric: false, label: "Client ID", disablePadding: true },
  { id: "", numeric: true, label: "Actions", align: "right" },
];

export default function OAuthTable() {
  const [order, setOrder] = useState<"asc" | "desc">("desc");
  const [orderBy, setOrderBy] = useState<string>("appName");
  const [page, setPage] = useState<number>(0);
  const [rowsPerPage, setRowsPerPage] = useState<number>(5);
  const { addAlert } = useContext(AlertContext);

  const [apps, setApps] = useState<OAuthApp[]>([]);
  const [fetchSuccess, setFetchSuccess] = useState(false);

  const fetchApps = async () => {
    const urlAll = "/userform/ajax/oAuthApps";

    const response = await axios.get<{
      data: {
        oauthApps: OAuthApp[];
      }
    }>(urlAll)
    if (response.status !== 200 || 'exceptionMessage' in response.data) {
      setApps([]);
    } else {
      setApps(response.data.data.oauthApps);
      setFetchSuccess(true);
    }
  };

  useEffect(() => {
    void fetchApps();
  }, []);

  const deleteApp = async (clientId: string) => {
    try {
      const response = await axios.delete(`/userform/ajax/oAuthApps/${clientId}`);
      if (response.status === 200) {
        addAlert(mkAlert({
          title: "App successfully deleted",
          message: `App with client ID ${clientId} was successfully deleted.`,
          duration: 3000,
          variant: "success",
        }));
        removeApp(clientId);
      } else {
        addAlert(mkAlert({
          title: "Unable to delete app",
          message: `There was a problem deleting app with client ID ${clientId}.`,
          duration: 5000,
          variant: "warning",
        }))
      }
    } catch (e) {
      addAlert(mkAlert({
        title: "Unable to delete app",
        message: `There was a problem deleting app with client ID ${clientId}: ${e instanceof Error ? e.message : String(e)}.`,
        isInfinite: true,
        variant: "error",
      }))
    }
  };

  const addApp = (app: OAuthApp) => {
    setApps((oldApps) => [app, ...oldApps]);
  };

  const removeApp = (clientId: string) => {
    setApps((oldApps) => oldApps.filter((oa) => oa.clientId != clientId));
  };

  const handleRequestSort = (
    _ : unknown,
    property: string,
  ) => {
    const isDesc = orderBy === property && order === "desc";
    setOrder(isDesc ? "asc" : "desc");
    setOrderBy(property);
    setPage(0);
  };

  const handleChangePage = (_: unknown, newPage: number) => {
    setPage(newPage);
  };

  const handleChangeRowsPerPage = (
    event: ChangeEvent<HTMLTextAreaElement | HTMLInputElement>,
  ) => {
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
        <OAuthDialog addApp={(app: OAuthApp) => addApp(app)} />
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
                {/* @ts-expect-error stableSort needs better types */}
                {stableSort(apps, getSorting(order, orderBy))
                  .slice(page * rowsPerPage, page * rowsPerPage + rowsPerPage)
                  .map((app) => (
                    <OAuthTableRow
                      key={app.clientId}
                      // @ts-expect-error stableSort needs better types
                      app={app}
                      onDeleteApp={() => deleteApp(app.clientId)}
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
      {!fetchSuccess && (
        <>There was a problem fetching your apps. Please, try again.</>
      )}
    </div>
  );
}
