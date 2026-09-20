import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useTranslation } from "react-i18next";
import { type InventoryOperation, operationAvailability } from "./operations";
import { resolveLabelFrom } from "./types";

export default function OperationPicker({
  operations,
  onSelect,
  selectionCount,
  allSameCategory,
}: {
  operations: ReadonlyArray<InventoryOperation>;
  onSelect: (operation: InventoryOperation) => void;
  selectionCount: number;
  allSameCategory: boolean;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const label = resolveLabelFrom(t);
  return (
    <List sx={{ display: "flex", flexDirection: "column", gap: "2px" }}>
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
            <ListItemIcon sx={{ minWidth: 36 }}>
              <FontAwesomeIcon icon={operation.icon} />
            </ListItemIcon>
            <ListItemText
              primary={
                <Typography component="span" sx={{ fontWeight: 700 }}>
                  {label(operation.labelKey)}
                </Typography>
              }
              secondary={secondary}
            />
          </ListItemButton>
        );
      })}
    </List>
  );
}
