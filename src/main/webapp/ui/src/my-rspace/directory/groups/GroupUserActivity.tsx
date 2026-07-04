import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import React, { useEffect } from "react";
import { createRoot } from "react-dom/client";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
import TimeAgoCustom from "@/components/TimeAgoCustom";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { getSorting } from "@/util/table";
import EnhancedTableHead from "../../../components/EnhancedTableHead";
import UserDetails from "./../../../components/UserDetails";

type GroupActivityProps = {
  groupId: string;
};

type ActivityRow = {
  eventId: string | number;
  userId: number;
  userFullName: string;
  eventType: string;
  timestamp: number | string | Date;
};

/**
 */
function GroupActivity({ groupId }: GroupActivityProps) {
  const { t } = useTranslation("common");
  const headCells = [
    { id: "userFullName", numeric: false, label: t("profile.groups.groupActivity.user") },
    { id: "eventType", numeric: false, label: t("profile.groups.activity.action") },
    { id: "timestamp", numeric: true, label: t("profile.groups.activity.time") },
  ];
  const [activities, setActivities] = React.useState<Array<ActivityRow>>([]);
  const [order, setOrder] = React.useState<"asc" | "desc">("desc");
  const [orderBy, setOrderBy] = React.useState("timestamp");

  useEffect(() => {
    const url = `/groups/ajax/membershipEventsByGroup/${groupId}`;
    axios
      .get(url)
      .then((response) => {
        if (response.data.exceptionMessage) {
          setActivities([]);
        } else {
          setActivities(response.data.data as Array<ActivityRow>);
        }
      })
      .catch((error) => {
        console.error(error);
      });
  }, [groupId]);

  const handleRequestSort = (_event: React.MouseEvent<unknown>, property: string) => {
    const isDesc = orderBy === property && order === "desc";
    setOrder(isDesc ? "asc" : "desc");
    setOrderBy(property);
  };

  return (
    <>
      <h3>{t("profile.groups.groupActivity.title")}</h3>
      <Table>
        <EnhancedTableHead
          headCells={headCells}
          order={order}
          orderBy={orderBy}
          onRequestSort={handleRequestSort}
          rowCount={activities.length}
        />
        <TableBody>
          {activities.toSorted(getSorting(order, orderBy)).map((row) => {
            return (
              <TableRow hover tabIndex={-1} key={row.eventId}>
                <TableCell scope="row">
                  <UserDetails
                    position={["top", "right"]}
                    userId={row.userId}
                    fullName={row.userFullName}
                    variant="outlined"
                  />
                </TableCell>
                <TableCell>{row.eventType.toLowerCase().replace(/./, (x) => x.toUpperCase())}</TableCell>
                <TableCell align="right">
                  <TimeAgoCustom time={row.timestamp as string} />
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </>
  );
}

export default GroupActivity;

const domContainer = document.getElementById("groupActivity");
const root = createRoot(domContainer as HTMLElement);
root.render(
  <I18nRoot namespaces={["common"]}>
    <MuiCssLayerProvider>
      <GroupActivity groupId={domContainer?.dataset.groupid as string} />
    </MuiCssLayerProvider>
  </I18nRoot>,
);
