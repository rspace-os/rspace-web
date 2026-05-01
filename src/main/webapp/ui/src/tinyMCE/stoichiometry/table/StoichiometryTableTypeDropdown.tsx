import React from "react";
import MenuItem from "@mui/material/MenuItem";
import Select, { type SelectChangeEvent } from "@mui/material/Select";
import { STOICHIOMETRY_ROLES } from "@/modules/stoichiometry/schema";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";

const STOICHIOMETRY_TYPE_OPTIONS = [
  {
    value: STOICHIOMETRY_ROLES.REACTANT,
    label: "Reactant",
  },
  {
    value: STOICHIOMETRY_ROLES.PRODUCT,
    label: "Product",
  },
  {
    value: STOICHIOMETRY_ROLES.AGENT,
    label: "Reagent",
  },
] as const satisfies ReadonlyArray<{
  value: EditableMolecule["role"];
  label: string;
}>;

type StoichiometryTableTypeDropdownProps = {
  rowName?: string | null;
  value?: EditableMolecule["role"] | null;
  onChangeValue: (
    nextValue: EditableMolecule["role"],
  ) => void | Promise<void>;
  onClose?: () => void;
};

export default function StoichiometryTableTypeDropdown({
  rowName,
  value,
  onChangeValue,
  onClose,
}: StoichiometryTableTypeDropdownProps): React.ReactNode {
  const ariaLabel = `Select type for ${rowName ?? "molecule"}`;

  const handleChange = (event: SelectChangeEvent<EditableMolecule["role"]>) => {
    const nextValue = event.target.value as EditableMolecule["role"];
    void onChangeValue(nextValue);
    onClose?.();
  };

  return (
    <Select
      fullWidth
      size="small"
      value={value ?? STOICHIOMETRY_ROLES.REACTANT}
      SelectDisplayProps={{
        "aria-label": ariaLabel,
      }}
      onClose={onClose}
      onChange={handleChange}
    >
      {STOICHIOMETRY_TYPE_OPTIONS.map((option) => (
        <MenuItem key={option.value} value={option.value}>
          {option.label}
        </MenuItem>
      ))}
    </Select>
  );
}

