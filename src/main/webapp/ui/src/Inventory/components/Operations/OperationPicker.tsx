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
import { type InventoryOperation, operationAvailability, operations } from "./operationsConfig";

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
    // 2px between the operation buttons (adr/0009 UI request); a flex column with a small gap keeps
    // the spacing even without per-item margins.
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
            {/* The operation name is emphasised (bold); the description reads as the lighter secondary
                line. A Typography element as `primary` sets the weight portably across MUI versions. */}
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
