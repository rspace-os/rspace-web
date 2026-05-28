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
  return (
    <CustomTableCell
      {...rest}
      sx={
        (sx
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
            ]) as React.ComponentProps<typeof CustomTableCell>["sx"]
      }
    />
  );
}

export default TableCell;
