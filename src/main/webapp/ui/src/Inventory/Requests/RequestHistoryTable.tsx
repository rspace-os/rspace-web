import SettingsOutlinedIcon from "@mui/icons-material/SettingsOutlined";
import Grid from "@mui/material/Grid";
import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import IconButtonWithTooltip from "@/components/IconButtonWithTooltip";
import NoValue from "@/components/NoValue";
import StyledMenu from "@/components/StyledMenu";
import UserDetails from "@/components/UserDetails";
import { isoToLocale } from "@/util/Util";
import RequestsStatusChip from "./RequestsStatusChip";

export type ApiSampleRequestStatusChangeItem = {
  id: number;
  status: string;
  created: string;
  createdBy: { id: number; firstName: string; lastName: string };
  reason: string | null;
  transferredSample: { id: number; globalId: string; name: string } | null;
};

type HistoryColumn = "date" | "additionalNotes";

const HISTORY_COLUMN_OPTIONS: ReadonlyArray<HistoryColumn> = ["date", "additionalNotes"];

/**
 * A record of every status change for a SampleRequest, in the same plain-table
 * format as the "Sample Locations" table. Not sortable by the user (always
 * shown newest-first); the rightmost column has a cog to swap between "Date"
 * (the default) and "Additional Notes" (the reason supplied for that change).
 */
export default function RequestHistoryTable({
  statusChanges,
}: {
  statusChanges: Array<ApiSampleRequestStatusChangeItem>;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const [column, setColumn] = useState<HistoryColumn>("date");
  const [columnMenuAnchor, setColumnMenuAnchor] = useState<HTMLElement | null>(null);

  const sortedChanges = [...statusChanges].sort(
    (a, b) => new Date(b.created).getTime() - new Date(a.created).getTime(),
  );

  const columnLabel = (option: HistoryColumn): string =>
    option === "date"
      ? t("requestsManagement.detail.history.columns.date")
      : t("requestsManagement.detail.history.columns.additionalNotes");

  return (
    <TableContainer>
      <Table size="small" stickyHeader>
        <TableHead>
          <TableRow>
            <TableCell>{t("requestsManagement.detail.history.columns.status")}</TableCell>
            <TableCell>{t("requestsManagement.detail.history.columns.user")}</TableCell>
            <TableCell>
              <Grid container sx={{ alignItems: "center", flexWrap: "nowrap" }}>
                <Grid>{columnLabel(column)}</Grid>
                <Grid>
                  <IconButtonWithTooltip
                    title={t("tables.adjustableHeadCell.columnOptions")}
                    onClick={({ currentTarget }) => setColumnMenuAnchor(currentTarget)}
                    icon={<SettingsOutlinedIcon sx={{ fontSize: "1.1em" }} />}
                    color="standardIcon"
                    aria-haspopup="menu"
                    size="small"
                  />
                </Grid>
              </Grid>
              <StyledMenu
                anchorEl={columnMenuAnchor}
                open={Boolean(columnMenuAnchor)}
                onClose={() => setColumnMenuAnchor(null)}
              >
                {HISTORY_COLUMN_OPTIONS.map((option) => (
                  <MenuItem
                    key={option}
                    selected={option === column}
                    onClick={() => {
                      setColumn(option);
                      setColumnMenuAnchor(null);
                    }}
                  >
                    <ListItemText primary={columnLabel(option)} />
                  </MenuItem>
                ))}
              </StyledMenu>
            </TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {sortedChanges.map((change) => (
            <TableRow key={change.id}>
              <TableCell>
                <RequestsStatusChip status={change.status} />
              </TableCell>
              <TableCell>
                <UserDetails
                  userId={change.createdBy.id}
                  fullName={`${change.createdBy.firstName} ${change.createdBy.lastName}`}
                  position={["bottom", "right"]}
                />
              </TableCell>
              <TableCell>
                {column === "date" ? (
                  isoToLocale(change.created)
                ) : change.reason ? (
                  change.reason
                ) : (
                  <NoValue label={t("requestsManagement.detail.fields.noComment")} />
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
