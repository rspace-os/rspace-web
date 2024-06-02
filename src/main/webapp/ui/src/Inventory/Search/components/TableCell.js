// @flow

import { type ElementProps } from "react";
import TableCell from "@mui/material/TableCell";
import { withStyles } from "Styles";

const StyledTableCell: typeof TableCell = withStyles<
  ElementProps<typeof TableCell>,
  { root: string }
>(() => ({
  root: {
    wordBreak: "break-word",
  },
}))(TableCell);

export default StyledTableCell;
