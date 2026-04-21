import React from "react";
import MuiTableCell, { type TableCellProps } from "@mui/material/TableCell";

const StyledTableCell = React.forwardRef<HTMLTableCellElement, TableCellProps>(
  function StyledTableCell({ sx, ...props }, ref) {
    return (
      <MuiTableCell
        ref={ref}
        {...props}
        sx={[
          { wordBreak: "break-word" },
          ...((Array.isArray(sx) ? sx : [sx])).filter(Boolean),
        ]}
      />
    );
  },
);

export default StyledTableCell;
