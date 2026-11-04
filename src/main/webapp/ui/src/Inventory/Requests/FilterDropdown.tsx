import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import type { ParseKeys } from "i18next";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import DropdownButton from "../../components/DropdownButton";
import StyledMenu from "../../components/StyledMenu";

export type FilterOption<T extends string> = { value: T; labelKey: ParseKeys<"inventory"> };

/**
 * A DropdownButton + StyledMenu filter chip, generic over the filter's own value type - the
 * pattern RequestsList's two filter menus (requests/status) shared, written out twice. Owns its
 * own anchor state and closes itself on selection.
 */
export default function FilterDropdown<T extends string>({
  chipLabel,
  value,
  options,
  onChange,
}: {
  /** Builds the chip's own text from the selected option's translated label (blank if none). */
  chipLabel: (selectedLabel: string) => string;
  value: T;
  options: ReadonlyArray<FilterOption<T>>;
  onChange: (value: T) => void;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const selectedOption = options.find((option) => option.value === value);

  return (
    <DropdownButton
      name={chipLabel(selectedOption ? t(selectedOption.labelKey) : "")}
      onClick={({ target }) => setAnchorEl(target as HTMLElement)}
    >
      <StyledMenu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={() => setAnchorEl(null)}>
        {options.map((option) => (
          <MenuItem
            key={option.value}
            selected={option.value === value}
            onClick={() => {
              onChange(option.value);
              setAnchorEl(null);
            }}
          >
            <ListItemText primary={t(option.labelKey)} />
          </MenuItem>
        ))}
      </StyledMenu>
    </DropdownButton>
  );
}
