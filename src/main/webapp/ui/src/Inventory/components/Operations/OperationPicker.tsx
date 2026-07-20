import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemText from "@mui/material/ListItemText";
import type React from "react";
import { useTranslation } from "react-i18next";
import { type InventoryOperation, operationAvailability, operations } from "./operationsConfig";

/**
 * Step 1: choose an operation. Every operation from operations_config.json is shown; each is enabled
 * or greyed-out for the current selection (adr/0007): single-origin operations need exactly one
 * subsample, Pool needs two or more of the same measurement category. A disabled operation shows the
 * reason as its secondary line instead of its description.
 */
export default function OperationPicker({
  onSelect,
  selectionCount,
  allSameCategory,
}: {
  onSelect: (operation: InventoryOperation) => void;
  /** How many subsamples are selected (drives which operations are enabled). */
  selectionCount: number;
  /** Whether the selected subsamples all share one measurement category (required by Pool). */
  allSameCategory: boolean;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  // Cast so config-driven (dynamic) keys resolve; the keys are validated to exist in the catalog.
  const label = t as unknown as (key: string) => string;
  return (
    <List>
      {operations.map((operation) => {
        const availability = operationAvailability(operation, selectionCount, allSameCategory);
        const secondary = availability.enabled
          ? operation.descriptionKey
            ? label(operation.descriptionKey)
            : undefined
          : availability.reasonKey
            ? label(availability.reasonKey)
            : undefined;
        return (
          <ListItemButton
            key={operation.key}
            onClick={() => onSelect(operation)}
            disabled={!availability.enabled}
            data-test-id={`operation-${operation.key}`}
          >
            <ListItemText primary={label(operation.labelKey)} secondary={secondary} />
          </ListItemButton>
        );
      })}
    </List>
  );
}
