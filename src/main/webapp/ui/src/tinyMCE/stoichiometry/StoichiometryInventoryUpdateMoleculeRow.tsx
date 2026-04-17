import React from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Checkbox from "@mui/material/Checkbox";
import Chip from "@mui/material/Chip";
import Stack from "@mui/material/Stack";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { alpha, type Theme, useTheme } from "@mui/material/styles";
import GlobalId from "@/components/GlobalId";
import LinkableRecordFromGlobalId from "@/stores/models/LinkableRecordFromGlobalId";
import StockMetricCell, { type MetricColors, type MetricState } from "@/tinyMCE/stoichiometry/inventory/StockMetricCell";
import StoichiometryTableRoleChip from "@/tinyMCE/stoichiometry/StoichiometryTableRoleChip";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";
import type { InventoryUpdateStockDisplay } from "@/tinyMCE/stoichiometry/utils";

export type StoichiometryInventoryUpdateMoleculeSelectionCardProps = {
  molecule: EditableMolecule;
  selected: boolean;
  disabled: boolean;
  helperText: string | null;
  stockDisplay: InventoryUpdateStockDisplay;
  onToggle: () => void;
};

function getMetricColors(
  theme: Theme,
  {
    status,
  }: {
    status: MetricState;
  },
): MetricColors {
  return {
    default: {
      valueColor: theme.palette.text.primary,
      warningColor: theme.palette.error.main,
    },
    positive: {
      valueColor: theme.palette.success.main,
      warningColor: theme.palette.error.main,
    },
    zero: {
      valueColor: theme.palette.warning.dark,
      warningColor: theme.palette.error.main,
    },
    negative: {
      valueColor: theme.palette.error.main,
      warningColor: theme.palette.error.main,
    },
  }[status];
}

function getHelperAlertSx(theme: Theme) {
  return {
    alignItems: "center",
    py: 0.25,
    px: 1,
    border: 1,
    borderColor: alpha(theme.palette.warning.main, 0.35),
    backgroundColor: alpha(theme.palette.warning.main, 0.12),
    color: theme.palette.warning.dark,
    "& .MuiAlert-icon": {
      color: theme.palette.warning.main,
    },
  } as const;
}

export default function StoichiometryInventoryUpdateMoleculeRow({
  molecule,
  selected,
  disabled,
  helperText,
  stockDisplay,
  onToggle,
}: StoichiometryInventoryUpdateMoleculeSelectionCardProps): React.ReactNode {
  const theme = useTheme();
  const nameId = React.useId();
  const helperTextId = React.useId();
  const warningTextId = React.useId();
  const describedBy = [
    helperText ? helperTextId : null,
    stockDisplay.warningText ? warningTextId : null,
  ]
    .filter(Boolean)
    .join(" ");
  const defaultMetricColors = getMetricColors(theme, {
    status: "default",
  });
  const remainingMetricColors = getMetricColors(theme, {
    status: stockDisplay.remainingStatus,
  });
  const moleculeName = molecule.name ?? "Unnamed molecule";
  const mainRowHasSubRow = Boolean(helperText);
  const rowBackgroundColor = selected
    ? alpha(theme.palette.primary.main, 0.06)
    : theme.palette.background.paper;
  const sharedRowCellSx = {
    backgroundColor: rowBackgroundColor,
  } as const;

  return (
    <>
      <TableRow
        hover={!disabled}
        data-row-type="molecule"
        data-molecule-name={moleculeName}
        sx={{
          "& > .MuiTableCell-root": {
            ...sharedRowCellSx,
            borderBottom: mainRowHasSubRow ? 0 : undefined,
            verticalAlign: "top",
          },
        }}
      >
        <TableCell
          padding="checkbox"
          data-column="Actions"
          width={52}
          sx={{ px: 0.5 }}
        >
          <Box
            data-dimmed={disabled ? "true" : "false"}
            display="flex"
            alignItems="flex-start"
            justifyContent="center"
            sx={{
              opacity: disabled ? 0.5 : 1,
              transition: theme.transitions.create("opacity", {
                duration: theme.transitions.duration.shorter,
              }),
            }}
          >
            <Checkbox
              checked={selected}
              disabled={disabled}
              inputProps={{
                "aria-labelledby": nameId,
                "aria-describedby": describedBy || undefined,
              }}
              onChange={() => {
                if (!disabled) {
                  onToggle();
                }
              }}
            />
          </Box>
        </TableCell>
        <TableCell data-column="Molecule" sx={{ py: 1.5 }}>
          <Stack spacing={0.75}>
            <Stack
              direction="row"
              spacing={1}
              alignItems="center"
              flexWrap="wrap"
            >
              <Typography
                id={nameId}
                variant="subtitle1"
                component="h3"
                fontWeight={600}
              >
                {moleculeName}
              </Typography>
              {molecule.role && (
                <StoichiometryTableRoleChip role={molecule.role} />
              )}
            </Stack>
            {molecule.inventoryLink && (
              <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                <GlobalId
                  record={
                    new LinkableRecordFromGlobalId(
                      molecule.inventoryLink.inventoryItemGlobalId,
                    )
                  }
                  onClick={() => {}}
                />
                {molecule.inventoryLink.stockDeducted && (
                  <Chip
                    size="small"
                    variant="outlined"
                    color="warning"
                    label="Stock Deducted"
                  />
                )}
              </Stack>
            )}
          </Stack>
        </TableCell>
        <StockMetricCell
          column="In Stock"
          value={stockDisplay.inStock.displayValue}
          unitLabel={stockDisplay.inStock.unitLabel}
          colors={defaultMetricColors}
        />
        <StockMetricCell
          column="Will Use"
          value={stockDisplay.willUse.displayValue}
          unitLabel={stockDisplay.willUse.unitLabel}
          colors={defaultMetricColors}
        />
        <StockMetricCell
          column="Remaining"
          value={stockDisplay.remaining.displayValue}
          unitLabel={stockDisplay.remaining.unitLabel}
          colors={remainingMetricColors}
          warningText={stockDisplay.warningText}
          warningTextId={warningTextId}
          dataStatus={stockDisplay.remainingStatus}
        />
      </TableRow>
      {helperText && (
        <TableRow
          data-row-type="helper"
          data-molecule-name={moleculeName}
          sx={{
            "& > .MuiTableCell-root": {
              ...sharedRowCellSx,
            },
          }}
        >
          <TableCell padding="checkbox" sx={{ px: 0.5 }} />
          <TableCell colSpan={5} sx={{ pt: 0, pb: 1.25 }}>
            <Alert
              id={helperTextId}
              severity="warning"
              variant="standard"
              sx={getHelperAlertSx(theme)}
            >
              {helperText}
            </Alert>
          </TableCell>
        </TableRow>
      )}
    </>
  );
}
