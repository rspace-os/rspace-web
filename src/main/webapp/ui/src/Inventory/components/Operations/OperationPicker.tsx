import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemText from "@mui/material/ListItemText";
import type React from "react";
import { useTranslation } from "react-i18next";
import { type InventoryOperation, operationsForSelectionSize } from "./operationsConfig";

/**
 * Step 1: choose an operation applicable to the (single-subsample) selection. Operations come from
 * operations_config.json, so this list grows purely by config.
 */
export default function OperationPicker({
  onSelect,
}: {
  onSelect: (operation: InventoryOperation) => void;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  // Cast so config-driven (dynamic) keys resolve; the keys are validated to exist in the catalog.
  const label = t as unknown as (key: string) => string;
  return (
    <List>
      {operationsForSelectionSize(1).map((operation) => (
        <ListItemButton
          key={operation.key}
          onClick={() => onSelect(operation)}
          data-test-id={`operation-${operation.key}`}
        >
          <ListItemText
            primary={label(operation.labelKey)}
            secondary={operation.descriptionKey ? label(operation.descriptionKey) : undefined}
          />
        </ListItemButton>
      ))}
    </List>
  );
}
