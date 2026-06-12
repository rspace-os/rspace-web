import ArrowDropDownIcon from "@mui/icons-material/ArrowDropDown";
import TableSortLabel from "@mui/material/TableSortLabel";
// biome-ignore lint/style/useImportType: initial biome migration
import React from "react";

type CustomTableSortLabelArgs = Omit<React.ComponentProps<typeof TableSortLabel>, "IconComponent">;

export default function CustomTableSortLabel(props: CustomTableSortLabelArgs): React.ReactNode {
  return <TableSortLabel {...props} IconComponent={ArrowDropDownIcon} />;
}
