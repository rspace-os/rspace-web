import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import { ThemeProvider } from "@mui/material/styles";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React from "react";
import { createRoot } from "react-dom/client";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import TimeAgoCustom from "@/components/TimeAgoCustom";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import EnhancedTableHead from "../../components/EnhancedTableHead";
import materialTheme from "../../theme";
import { getSorting } from "../../util/table";
import type { Order } from "../../util/types";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
export default function GroupActivity(props: any) {
  const { t } = useTranslation("common");
  const [fetched, setFetched] = React.useState(false);
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const [activities, setActivities] = React.useState<any[] | null>([]);
  const [order, setOrder] = React.useState<Order>("desc");
  const [orderBy, setOrderBy] = React.useState("timestamp");

  const headCells = [
    { id: "groupName", numeric: false, label: t("profile.groups.table.group") },
    { id: "eventType", numeric: false, label: t("profile.groups.activity.action") },
    { id: "timestamp", numeric: true, label: t("profile.groups.activity.time") },
  ];

  const loadUserActivity = () => {
    const url = `/groups/ajax/membershipEventsByUser/${props.userId}`;
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

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const handleRequestSort = (_event: any, property: any) => {
    const isDesc = orderBy === property && order === "desc";
    setOrder(isDesc ? "asc" : "desc");
    setOrderBy(property);
  };

  return (
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={materialTheme}>
        <Box sx={{ width: "690px", padding: "0px 15px" }}>
          {!fetched && (
            <Button color="primary" onClick={loadUserActivity}>
              {t("profile.groups.activity.show")}
            </Button>
          )}
          {fetched && (
            <>
              <div className="api-menu__header">{t("profile.groups.activity.title")}</div>
              <br />
              <Table>
                <EnhancedTableHead
                  headCells={headCells}
                  order={order}
                  orderBy={orderBy}
                  onRequestSort={handleRequestSort}
                  rowCount={0}
                />
                <TableBody>
                  {/** biome-ignore lint/suspicious/noExplicitAny: initial biome migration */}
                  {(activities ?? []).toSorted(getSorting(order, orderBy)).map((row: any, index) => {
                    return (
                      <TableRow data-test-id="row" hover tabIndex={-1} key={`${row.timestamp}-${index}`}>
                        <TableCell scope="row">
                          <a href={`/groups/view/${row.groupId}`}>{row.groupName}</a>
                        </TableCell>
                        <TableCell>
                          {row.eventType.toLowerCase().replace(/./, (x: string) => x.toUpperCase())}
                        </TableCell>
                        <TableCell align="right">
                          <TimeAgoCustom time={row.timestamp} />
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </>
          )}
        </Box>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

const domContainer = document.getElementById("group-activity");

if (domContainer) {
  const root = createRoot(domContainer);
  root.render(
    <I18nRoot namespaces={["common"]}>
      <GroupActivity userId={domContainer.dataset.userid} />
    </I18nRoot>,
  );
}
