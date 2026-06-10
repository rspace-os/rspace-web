import React from "react";
import MuiTableCell, { type TableCellProps } from "@mui/material/TableCell";

function StyledTableCell({ sx, ...props }: TableCellProps): React.ReactNode {
  return (
    <MuiTableCell
      {...props}
      sx={[
        { wordBreak: "break-word" },
        ...(Array.isArray(sx) ? sx : [sx]).filter(Boolean),
      ]}
    />
  );
}

export default StyledTableCell;
