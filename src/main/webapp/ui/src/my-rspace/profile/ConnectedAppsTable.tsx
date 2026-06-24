import Box from "@mui/material/Box";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableContainer from "@mui/material/TableContainer";
import TablePagination from "@mui/material/TablePagination";
import { type ChangeEvent, useContext, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import ConnectedAppsTableRow from "@/my-rspace/profile/ConnectedAppsTableRow";
import type { ConnectedOAuthApp } from "@/my-rspace/profile/types";
import AlertContext, { mkAlert } from "@/stores/contexts/Alert";
import { getSorting, paginationOptions } from "@/util/table";
import EnhancedTableHead from "../../components/EnhancedTableHead";

export default function ConnectedAppsTable() {
  const { t } = useTranslation("common");
  const [order, setOrder] = useState<"asc" | "desc">("desc");
  const [orderBy, setOrderBy] = useState<string>("clientName");
  const [page, setPage] = useState<number>(0);
  const [rowsPerPage, setRowsPerPage] = useState<number>(5);
  const { addAlert } = useContext(AlertContext);

  const [apps, setApps] = useState<ConnectedOAuthApp[]>([]);
  const [fetchSuccess, setFetchSuccess] = useState<boolean>(false);

  const headCells = [
    { id: "clientName", numeric: false, label: t("profile.oauth.table.appName") },
    { id: "clientId", numeric: false, label: t("profile.oauth.table.clientId"), disablePadding: true },
    { id: "scope", numeric: false, label: t("profile.oauth.table.scope") },
    { id: "", numeric: true, label: t("profile.oauth.table.actions"), align: "right" },
  ];

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
          title: t("profile.oauth.connectedApps.disconnectSuccessTitle"),
          message: t("profile.oauth.connectedApps.disconnectSuccessMessage", { clientId: id }),
          variant: "success",
        }),
      );
      setApps(apps.filter((app) => app.clientId !== id));
    } catch {
      addAlert(
        mkAlert({
          title: t("profile.oauth.connectedApps.disconnectErrorTitle"),
          message: t("profile.oauth.connectedApps.disconnectErrorMessage", { clientId: id }),
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
        <Box sx={{ flexGrow: "1", lineHeight: "42px" }}>{t("profile.oauth.connectedApps.title")}</Box>
      </Box>
      <br />
      {fetchSuccess && (
        <>
          <TableContainer>
            <Table size="small" aria-label={t("profile.oauth.table.ariaLabel")}>
              <EnhancedTableHead
                headCells={headCells}
                order={order}
                orderBy={orderBy}
                onRequestSort={handleRequestSort}
                rowCount={apps.length}
              />
              <TableBody>
                {apps
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
      {!fetchSuccess && <>{t("profile.oauth.fetchError")}</>}
    </Box>
  );
}
