import CustomTableCell from "../../Search/components/TableCell";
import React from "react";

type TableCellArgs = React.ComponentProps<typeof CustomTableCell> & {
  nopadding?: boolean;
  borderless?: boolean;
};

function TableCell({
  nopadding,
  borderless,
  sx,
  ...rest
}: TableCellArgs): React.ReactNode {
  const combinedSx = (
    sx
      ? [
          {
            padding: nopadding ? 0 : undefined,
            borderBottom: borderless ? "unset" : undefined,
          },
          sx,
        ]
      : [
          {
            padding: nopadding ? 0 : undefined,
            borderBottom: borderless ? "unset" : undefined,
          },
        ]
  ) as React.ComponentProps<typeof CustomTableCell>["sx"];

  return (
    <CustomTableCell
      {...rest}
      sx={combinedSx}
    />
  );
}

TableCell.displayName = "TableCell";
export default TableCell;
