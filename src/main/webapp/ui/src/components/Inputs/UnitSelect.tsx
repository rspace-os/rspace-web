import React from "react";
import FormControl from "@mui/material/FormControl";
import { observer } from "mobx-react-lite";
import useStores from "../../stores/use-stores";
import InputAdornment from "@mui/material/InputAdornment";
import Select, { SelectChangeEvent, selectClasses } from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";

type UnitSelectArgs = {
  /**
   * A list of categories of units, as defined in the UnitStore.
   * E.g. `[ "volume", "mass" ]` to get "ml", "kg", etc. in the menu
   */
  categories: Array<string>;

  /**
   * The UnitId of the selected unit, as defined in the UnitStore, and
   * ultimately from the API. Whilst it is a numerical value, the specific
   * value is not significant and is purely a consistent identifier to a
   * particular unit.
   */
  value: number;

  handleChange: (event: SelectChangeEvent<number>) => void;
  disabled?: boolean;
};

function UnitSelect({
  disabled,
  handleChange,
  value,
  categories,
}: UnitSelectArgs): React.ReactNode {
  const { unitStore } = useStores();

  return (
    <InputAdornment position="end">
      <FormControl>
        <Select
          disabled={disabled}
          onChange={handleChange}
          inputProps={{
            "aria-label": "Quantity units",
          }}
          value={value}
          size="small"
          sx={{
            [`& .${selectClasses.select}`]: {
              mt: 0.25,
              pt: 0.5,
              borderLeft: "1px solid #c4c4c4",
              borderRadius: 0,
              mb: 0.25,
              pb: 0.5,
              ":focus": {
                borderRadius: 0,
              },
            },
            "& fieldset": {
              border: "none",
            },
          }}
        >
          {unitStore.unitsOfCategory(categories).map((u) => (
            <MenuItem key={u.id} value={u.id}>
              {u.label}
            </MenuItem>
          ))}
        </Select>
      </FormControl>
    </InputAdornment>
  );
}

export default observer(UnitSelect);
