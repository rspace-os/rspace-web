//@flow

import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import React, { type Node } from "react";
import TableSortLabel from "@mui/material/TableSortLabel";

type CustomTableSortLabelArgs = {|
  ...$Rest<CustomTableSortLabelArgs, { IconComponent: Node }>,
|};

export default function CustomTableSortLabel(
  props: CustomTableSortLabelArgs
): Node {
  return <TableSortLabel {...props} IconComponent={ArrowDropDownIcon} />;
}
