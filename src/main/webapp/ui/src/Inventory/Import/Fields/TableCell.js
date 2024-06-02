//@flow

import CustomTableCell from "../../Search/components/TableCell";
import React, { type ComponentType, type ElementProps } from "react";
import { withStyles } from "Styles";

type TableCellArgs = {|
  nopadding: boolean,
  borderless?: boolean,
  ...ElementProps<typeof CustomTableCell>,
|};

const TableCell: ComponentType<TableCellArgs> = withStyles<
  TableCellArgs,
  { root: string }
>((theme, { nopadding, borderless }) => ({
  root: {
    padding: nopadding ? 0 : theme.spacing(0.5),
    borderBottom: borderless ? "unset" : theme.borders.table,
  },
}))(({ nopadding, borderless, ...rest }) => <CustomTableCell {...rest} />); // eslint-disable-line no-unused-vars

TableCell.displayName = "TableCell";
export default TableCell;
