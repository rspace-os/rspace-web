import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import React from "react";
import useStores from "../../../stores/use-stores";
import { Fields } from "../../../stores/models/ImportModel";
import { toTitleCase, match } from "../../../util/Util";

type FieldSelectMenuItemArgs = {
  field: (typeof Fields)[keyof typeof Fields];
  currentField: (typeof Fields)[keyof typeof Fields];
  typeIsCompatibleWithField: boolean;
  onClick?: () => void; // this isn't passed in from anywhere, but if it is removed everything breaks
  value?: string | null; // unused here, but required by the parent MUI Select component
};

const label = (field: (typeof Fields)[keyof typeof Fields]): string =>
  match<(typeof Fields)[keyof typeof Fields], string>([
    [(f) => f === Fields.custom, "Custom Field (Various Types)"],
    [() => true, toTitleCase(Symbol.keyFor(field) ?? "")],
  ])(field);

const FieldSelectMenuItem = React.forwardRef<
  HTMLLIElement,
  FieldSelectMenuItemArgs
>(
  (
    {
      field,
      currentField,
      typeIsCompatibleWithField,
      onClick,
    }: FieldSelectMenuItemArgs,
    ref
  ) => {
    const { importStore } = useStores();
    const fieldIsChosen =
      Boolean(importStore.importData?.fieldIsChosen(field)) &&
      field !== Fields.none;
    const fieldIsChosenHere = field === currentField;
    const customIsChosen = field === Fields.custom;
    const compatibleType = customIsChosen || typeIsCompatibleWithField;
    const disabled =
      !customIsChosen &&
      ((fieldIsChosen && !fieldIsChosenHere) || !compatibleType);
    const helpText = match<void, string>([
      [() => fieldIsChosenHere, ""],
      [() => customIsChosen, ""],
      [() => fieldIsChosen, "This field is already mapped to another column."],
      [() => !compatibleType, "Incompatible data in this column."],
      [() => true, ""],
    ])();

    return (
      <MenuItem disabled={disabled} ref={ref} onClick={onClick}>
        <ListItemText
          primary={label(field)}
          secondary={helpText}
          style={{ marginTop: 2, marginBottom: 2 }}
        />
      </MenuItem>
    );
  }
);

FieldSelectMenuItem.displayName = "FieldSelectMenuItem";
export default FieldSelectMenuItem;
