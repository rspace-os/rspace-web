import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import React from "react";
import TableSortLabel from "@mui/material/TableSortLabel";

type CustomTableSortLabelArgs = Omit<
  React.ComponentProps<typeof TableSortLabel>,
  "IconComponent"
>;

export default function CustomTableSortLabel(
  props: CustomTableSortLabelArgs
): React.ReactNode {
  return <TableSortLabel {...props} IconComponent={ArrowDropDownIcon} />;
}
