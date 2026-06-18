import MuiTableCell, { type TableCellProps } from "@mui/material/TableCell";
import type React from "react";

function StyledTableCell({ sx, ...props }: TableCellProps): React.ReactNode {
  return (
    <MuiTableCell {...props} sx={[{ wordBreak: "break-word" }, ...(Array.isArray(sx) ? sx : [sx]).filter(Boolean)]} />
  );
}

export default StyledTableCell;
