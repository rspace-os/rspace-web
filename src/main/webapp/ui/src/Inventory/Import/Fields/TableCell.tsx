// biome-ignore lint/style/useImportType: initial biome migration
import React from "react";
import CustomTableCell from "../../Search/components/TableCell";

type TableCellArgs = React.ComponentProps<typeof CustomTableCell> & {
  nopadding?: boolean;
  borderless?: boolean;
};

function TableCell({ nopadding, borderless, sx, ...rest }: TableCellArgs): React.ReactNode {
  return (
    <CustomTableCell
      {...rest}
      sx={[
        {
          padding: nopadding ? 0 : undefined,
          borderBottom: borderless ? "unset" : undefined,
        },
        ...(Array.isArray(sx) ? sx : [sx]).filter(Boolean),
      ]}
    />
  );
}

export default TableCell;
