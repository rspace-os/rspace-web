import type { IconDefinition } from "@fortawesome/fontawesome-svg-core";
import { faSun } from "@fortawesome/free-regular-svg-icons/faSun";
import { faArrowsRotate } from "@fortawesome/free-solid-svg-icons/faArrowsRotate";
import { faCodeBranch } from "@fortawesome/free-solid-svg-icons/faCodeBranch";
import { faEyeDropper } from "@fortawesome/free-solid-svg-icons/faEyeDropper";
import { faFlask } from "@fortawesome/free-solid-svg-icons/faFlask";
import { faSnowflake } from "@fortawesome/free-solid-svg-icons/faSnowflake";
import { faTrash } from "@fortawesome/free-solid-svg-icons/faTrash";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useTranslation } from "react-i18next";
import { type InventoryOperation, operationAvailability } from "./operationsConfig";
import { resolveLabelFrom } from "./types";

// Resolves an operation's config `iconKey` to a statically-imported FontAwesome icon. Icons cannot
// live in the JSON (they must be imported by name for tree-shaking), so the config names one and this
// registry supplies it; an operation whose key is absent simply renders without an icon.
const operationIcons: Record<string, IconDefinition> = {
  "eye-dropper": faEyeDropper,
  "arrows-rotate": faArrowsRotate,
  flask: faFlask,
  "code-branch": faCodeBranch,
  snowflake: faSnowflake,
  sun: faSun,
  trash: faTrash,
};

export default function OperationPicker({
  operations,
  onSelect,
  selectionCount,
  allSameCategory,
}: {
  /** The operation definitions, fetched and validated by the wizard. */
  operations: ReadonlyArray<InventoryOperation>;
  onSelect: (operation: InventoryOperation) => void;
  /** How many subsamples are selected (drives which operations are enabled). */
  selectionCount: number;
  /** Whether the selected subsamples all share one measurement category (required by Pool). */
  allSameCategory: boolean;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  // See resolveLabelFrom: the cast is about t's overloads, not the keys, which are InventoryKey
  // and so checked by the compiler.
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
        const icon = operation.iconKey ? operationIcons[operation.iconKey] : undefined;
        return (
          <ListItemButton
            key={operation.key}
            onClick={() => onSelect(operation)}
            disabled={!availability.enabled}
            data-test-id={`operation-${operation.key}`}
          >
            {icon ? (
              <ListItemIcon sx={{ minWidth: 36 }}>
                <FontAwesomeIcon icon={icon} />
              </ListItemIcon>
            ) : null}
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
