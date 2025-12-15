import React from "react";
import { makeStyles } from "tss-react/mui";
import TableSortLabel from "./TableSortLabel";
import TableHead from "@mui/material/TableHead";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import Checkbox from "@mui/material/Checkbox";
import { type Order } from "../util/types";

const useStyles = makeStyles()(() => ({
  visuallyHidden: {
    border: 0,
    clip: "rect(0 0 0 0)",
    height: 1,
    margin: -1,
    overflow: "hidden",
    padding: 0,
    position: "absolute",
    top: 20,
    width: 1,
  },
}));

export type Cell<COLUMN_ID_TYPE extends React.Key> = {
  id: COLUMN_ID_TYPE;
  numeric: boolean;
  label: string;
  sortable?: boolean;
  disablePadding?: boolean;
};

type EnhancedTableHeadArgs<COLUMN_ID_TYPE extends React.Key> = {
  headCells: Array<Cell<COLUMN_ID_TYPE>>;
  headStyle?: string;
  order: Order;
  orderBy: COLUMN_ID_TYPE;
  onRequestSort: (
    event: React.MouseEvent<HTMLSpanElement>,
    column: COLUMN_ID_TYPE
  ) => void;
  selectAll?: boolean;
  onSelectAllClick?: (event: { target: { checked: boolean } }) => void;
  numSelected?: number;
  rowCount: number;
  emptyCol?: boolean;
};

export default function EnhancedTableHead<COLUMN_ID_TYPE extends React.Key>(
  props: EnhancedTableHeadArgs<COLUMN_ID_TYPE>
): React.ReactNode {
  const { classes } = useStyles();
  const {
    headCells,
    headStyle,
    order,
    orderBy,
    onRequestSort,
    selectAll,
    onSelectAllClick,
    numSelected,
    rowCount,
    emptyCol,
  } = props;
  const createSortHandler =
    (property: COLUMN_ID_TYPE) =>
    (event: React.MouseEvent<HTMLSpanElement>) => {
      onRequestSort(event, property);
    };

  return (
    <TableHead>
      <TableRow>
        {selectAll && typeof numSelected === "number" && (
          <TableCell padding="checkbox" className={headStyle ?? ""}>
            <Checkbox
              color="primary"
              indeterminate={numSelected > 0 && numSelected < rowCount}
              checked={rowCount > 0 && numSelected === rowCount}
              onChange={onSelectAllClick}
              inputProps={{ "aria-label": "select/deselect all" }}
            />
          </TableCell>
        )}
        {emptyCol && (
          <TableCell padding="checkbox" className={headStyle ?? ""} />
        )}
        {headCells.map((headCell) => (
          <TableCell
            key={headCell.id}
            align={headCell.numeric ? "right" : "left"}
            padding={headCell.disablePadding ? "none" : "normal"}
            {...(orderBy === headCell.id && { sortDirection: order })}
            className={headStyle ?? ""}
          >
            {(typeof headCell.sortable === "undefined" ||
              headCell.sortable === true) && (
              <TableSortLabel
                active={orderBy === headCell.id}
                direction={order}
                onClick={createSortHandler(headCell.id)}
              >
                {headCell.label}
                {orderBy === headCell.id ? (
                  <span className={classes.visuallyHidden}>
                    {order === "desc"
                      ? "sorted descending"
                      : "sorted ascending"}
                  </span>
                ) : null}
              </TableSortLabel>
            )}
            {headCell.sortable === false && headCell.label}
          </TableCell>
        ))}
      </TableRow>
    </TableHead>
  );
}
