import { withStyles } from "Styles";
import TableCell from "@mui/material/TableCell";
import type React from "react";

const StyledTableCell = withStyles<React.ComponentProps<typeof TableCell>, { root: string }>(() => ({
    root: {
        wordBreak: "break-word",
    },
}))(TableCell);

export default StyledTableCell;
