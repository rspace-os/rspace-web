import MenuItem from "@mui/material/MenuItem";
import Select, { type SelectChangeEvent } from "@mui/material/Select";
import type React from "react";
import { useTranslation } from "react-i18next";
import { STOICHIOMETRY_ROLES } from "@/modules/stoichiometry/schema";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";

type StoichiometryTableTypeDropdownProps = {
  rowName?: string | null;
  value?: EditableMolecule["role"] | null;
  onChangeValue: (nextValue: EditableMolecule["role"]) => void | Promise<void>;
  onClose?: () => void;
};

export default function StoichiometryTableTypeDropdown({
  rowName,
  value,
  onChangeValue,
  onClose,
}: StoichiometryTableTypeDropdownProps): React.ReactNode {
  const { t } = useTranslation("common");
  const ariaLabel = t("stoichiometry.table.aria.typeSelect", {
    name: rowName ?? t("stoichiometry.inventoryUpdate.unnamedMolecule"),
  });
  const options = [
    {
      value: STOICHIOMETRY_ROLES.REACTANT,
      label: t("stoichiometry.table.roles.reactant"),
    },
    {
      value: STOICHIOMETRY_ROLES.PRODUCT,
      label: t("stoichiometry.table.roles.product"),
    },
    {
      value: STOICHIOMETRY_ROLES.AGENT,
      label: t("stoichiometry.table.roles.reagent"),
    },
  ] as const satisfies ReadonlyArray<{
    value: EditableMolecule["role"];
    label: string;
  }>;

  const handleChange = (event: SelectChangeEvent<EditableMolecule["role"]>) => {
    const nextValue = event.target.value;
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
      {options.map((option) => (
        <MenuItem key={option.value} value={option.value}>
          {option.label}
        </MenuItem>
      ))}
    </Select>
  );
}
