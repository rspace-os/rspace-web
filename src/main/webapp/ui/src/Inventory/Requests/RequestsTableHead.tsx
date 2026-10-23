import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import SettingsOutlinedIcon from "@mui/icons-material/SettingsOutlined";
import Grid from "@mui/material/Grid";
import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TableSortLabel from "@mui/material/TableSortLabel";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import IconButtonWithTooltip from "@/components/IconButtonWithTooltip";
import StyledMenu from "@/components/StyledMenu";

export type ColumnKey = "id" | "sample" | "requester" | "status" | "submitted";
export type SortDirection = "asc" | "desc";

export const ADJUSTABLE_COLUMN_OPTIONS: ReadonlyArray<ColumnKey> = ["status", "id", "submitted"];

const COLUMN_LABEL_KEYS = {
  id: "requestsManagement.columns.id",
  sample: "requestsManagement.columns.sample",
  requester: "requestsManagement.columns.requester",
  status: "requestsManagement.columns.status",
  submitted: "requestsManagement.columns.submitted",
} as const;

function columnLabelKey(column: ColumnKey): (typeof COLUMN_LABEL_KEYS)[ColumnKey] {
  return COLUMN_LABEL_KEYS[column];
}

function SortableHeaderLabel({
  column,
  sortBy,
  sortDirection,
  onSort,
}: {
  column: ColumnKey;
  sortBy: ColumnKey;
  sortDirection: SortDirection;
  onSort: (column: ColumnKey) => void;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  return (
    <TableSortLabel
      active={sortBy === column}
      direction={sortBy === column ? sortDirection : "asc"}
      onClick={() => onSort(column)}
      IconComponent={ArrowDropDownIcon}
    >
      {t(columnLabelKey(column))}
    </TableSortLabel>
  );
}

function AdjustableHeaderCell({
  current,
  sortBy,
  sortDirection,
  onSort,
  onChange,
}: {
  current: ColumnKey;
  sortBy: ColumnKey;
  sortDirection: SortDirection;
  onSort: (column: ColumnKey) => void;
  onChange: (newColumn: ColumnKey) => void;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null);

  return (
    <TableCell variant="head" padding="normal">
      <Grid container sx={{ alignItems: "center", flexWrap: "nowrap" }}>
        <Grid>
          <SortableHeaderLabel column={current} sortBy={sortBy} sortDirection={sortDirection} onSort={onSort} />
        </Grid>
        <Grid>
          <IconButtonWithTooltip
            title={t("tables.adjustableHeadCell.columnOptions")}
            onClick={({ currentTarget }) => setMenuAnchor(currentTarget)}
            icon={<SettingsOutlinedIcon sx={{ fontSize: "1.1em" }} />}
            color="standardIcon"
            aria-haspopup="menu"
            size="small"
          />
        </Grid>
      </Grid>
      <StyledMenu anchorEl={menuAnchor} open={Boolean(menuAnchor)} onClose={() => setMenuAnchor(null)}>
        {ADJUSTABLE_COLUMN_OPTIONS.map((option) => (
          <MenuItem
            key={option}
            selected={option === current}
            onClick={() => {
              setMenuAnchor(null);
              onChange(option);
            }}
          >
            <ListItemText primary={t(columnLabelKey(option))} />
          </MenuItem>
        ))}
      </StyledMenu>
    </TableCell>
  );
}

/**
 * "Requested Sample" and "Requester" are fixed, sortable columns with no cog.
 * The rightmost column is adjustable: it defaults to "Status" but its cog
 * lets the user swap in "Request ID" or "Submitted" instead.
 */
export default function RequestsTableHead({
  sortBy,
  sortDirection,
  onSort,
  adjustableColumn,
  onAdjustableColumnChange,
}: {
  sortBy: ColumnKey;
  sortDirection: SortDirection;
  onSort: (column: ColumnKey) => void;
  adjustableColumn: ColumnKey;
  onAdjustableColumnChange: (newColumn: ColumnKey) => void;
}): React.ReactNode {
  return (
    <TableHead>
      <TableRow>
        <TableCell variant="head" padding="normal">
          <SortableHeaderLabel column="sample" sortBy={sortBy} sortDirection={sortDirection} onSort={onSort} />
        </TableCell>
        <TableCell variant="head" padding="normal">
          <SortableHeaderLabel column="requester" sortBy={sortBy} sortDirection={sortDirection} onSort={onSort} />
        </TableCell>
        <AdjustableHeaderCell
          current={adjustableColumn}
          sortBy={sortBy}
          sortDirection={sortDirection}
          onSort={onSort}
          onChange={onAdjustableColumnChange}
        />
      </TableRow>
    </TableHead>
  );
}
