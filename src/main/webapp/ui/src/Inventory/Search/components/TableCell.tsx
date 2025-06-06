import React from "react";
import TableCell from "@mui/material/TableCell";
import { withStyles } from "Styles";

const StyledTableCell = withStyles<
  React.ComponentProps<typeof TableCell>,
  { root: string }
>(() => ({
  root: {
    wordBreak: "break-word",
  },
}))(TableCell);

export default StyledTableCell;
