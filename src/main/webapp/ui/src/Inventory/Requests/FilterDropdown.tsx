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
  chipLabelKey,
  value,
  options,
  onChange,
}: {
  /** An i18n key taking one `{value}` interpolation - the selected option's own translated label. */
  chipLabelKey: ParseKeys<"inventory">;
  value: T;
  options: ReadonlyArray<FilterOption<T>>;
  onChange: (value: T) => void;
}): React.ReactNode {
  const { t: tRaw } = useTranslation("inventory");
  // i18next's own overloads infer the options shape from a literal `Key`; a key typed as the
  // wider ParseKeys<"inventory"> union (needed so the two call sites below can still pass any
  // real key) defeats that inference. This narrower view of the same function keeps the calls
  // below trivial while FilterOption's own key fields still validate against the real catalog.
  const t = tRaw as (key: string, options?: Record<string, unknown>) => string;
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const selectedOption = options.find((option) => option.value === value);

  return (
    <DropdownButton
      name={t(chipLabelKey, { value: selectedOption ? t(selectedOption.labelKey) : "" })}
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
