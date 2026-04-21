import React from "react";
import Stack from "@mui/material/Stack";
import TableCell from "@mui/material/TableCell";
import Typography from "@mui/material/Typography";
import type { InventoryUpdateStockDisplay } from "../utils";

export type MetricState = InventoryUpdateStockDisplay["remainingStatus"];

export type MetricColors = {
  valueColor: string;
  warningColor: string;
};

export type StockMetricCellProps = {
  column: string;
  value: string;
  unitLabel: string | null;
  colors: MetricColors;
  warningText?: string | null;
  warningTextId?: string;
  dataStatus?: string;
};

const StockMetricCell = ({
  column,
  value,
  unitLabel,
  colors,
  warningText,
  warningTextId,
  dataStatus,
}: StockMetricCellProps) => (
  <TableCell
    align="right"
    data-column={column}
    data-status={dataStatus}
    sx={{ verticalAlign: "top", py: 1.5 }}
  >
    <Stack spacing={0.5} alignItems="flex-end">
      <Typography variant="h6" fontWeight={700} color={colors.valueColor}>
        {unitLabel ? `${value} ${unitLabel}` : value}
      </Typography>
      {warningText && (
        <Typography
          id={warningTextId}
          variant="caption"
          color={colors.warningColor}
          display="block"
          fontWeight={600}
        >
          {warningText}
        </Typography>
      )}
    </Stack>
  </TableCell>
);

export default StockMetricCell;