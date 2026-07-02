import Box from "@mui/material/Box";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableContainer from "@mui/material/TableContainer";
import TablePagination from "@mui/material/TablePagination";
import { type ChangeEvent, useContext, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import OAuthTableRow from "@/my-rspace/profile/OAuthTableRow";
import type { OAuthApp } from "@/my-rspace/profile/types";
import AlertContext, { mkAlert } from "@/stores/contexts/Alert";
import { getSorting, paginationOptions } from "@/util/table";
import EnhancedTableHead from "../../components/EnhancedTableHead";
import OAuthDialog from "./OAuthDialog";

export default function OAuthTable() {
  const { t } = useTranslation("common");
  const [order, setOrder] = useState<"asc" | "desc">("desc");
  const [orderBy, setOrderBy] = useState<string>("appName");
  const [page, setPage] = useState<number>(0);
  const [rowsPerPage, setRowsPerPage] = useState<number>(5);
  const { addAlert } = useContext(AlertContext);

  const [apps, setApps] = useState<OAuthApp[]>([]);
  const [fetchSuccess, setFetchSuccess] = useState(false);

  const headCells = [
    { id: "appName", numeric: false, label: t("profile.oauth.table.appName") },
    { id: "clientId", numeric: false, label: t("profile.oauth.table.clientId"), disablePadding: true },
    { id: "", numeric: true, label: t("profile.oauth.table.actions"), align: "right" },
  ];

  const fetchApps = async () => {
    const urlAll = "/userform/ajax/oAuthApps";

    const response = await axios.get<{
      data: {
        oauthApps: OAuthApp[];
      };
    }>(urlAll);
    if (response.status !== 200 || "exceptionMessage" in response.data) {
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
        addAlert(
          mkAlert({
            title: t("profile.oauth.createdApps.deleteSuccessTitle"),
            message: t("profile.oauth.createdApps.deleteSuccessMessage", { clientId }),
            duration: 3000,
            variant: "success",
          }),
        );
        removeApp(clientId);
      } else {
        addAlert(
          mkAlert({
            title: t("profile.oauth.createdApps.deleteErrorTitle"),
            message: t("profile.oauth.createdApps.deleteErrorMessage", { clientId }),
            duration: 5000,
            variant: "warning",
          }),
        );
      }
    } catch (e) {
      addAlert(
        mkAlert({
          title: t("profile.oauth.createdApps.deleteErrorTitle"),
          message: t("profile.oauth.createdApps.deleteExceptionMessage", {
            clientId,
            error: e instanceof Error ? e.message : String(e),
          }),
          isInfinite: true,
          variant: "error",
        }),
      );
    }
  };

  const addApp = (app: OAuthApp) => {
    setApps((oldApps) => [app, ...oldApps]);
  };

  const removeApp = (clientId: string) => {
    setApps((oldApps) => oldApps.filter((oa) => oa.clientId !== clientId));
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
        <Box sx={{ flexGrow: "1", lineHeight: "42px" }}>{t("profile.oauth.createdApps.title")}</Box>
        <OAuthDialog addApp={(app: OAuthApp) => addApp(app)} />
      </Box>
      <br />
      {fetchSuccess && (
        <>
          <TableContainer>
            <Table size="small" aria-label={t("profile.oauth.table.label")}>
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
                    <OAuthTableRow key={app.clientId} app={app} onDeleteApp={() => deleteApp(app.clientId)} />
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
