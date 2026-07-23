import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import React from "react";
import { useTranslation } from "react-i18next";
import { Fields } from "../../../stores/models/ImportModel";
import useStores from "../../../stores/use-stores";
import { match, toTitleCase } from "../../../util/Util";

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

const FieldSelectMenuItem = React.forwardRef<HTMLLIElement, FieldSelectMenuItemArgs>(
  ({ field, currentField, typeIsCompatibleWithField, onClick }: FieldSelectMenuItemArgs, ref) => {
    const { t } = useTranslation("inventory");
    const { importStore } = useStores();
    const fieldIsChosen = Boolean(importStore.importData?.fieldIsChosen(field)) && field !== Fields.none;
    const fieldIsChosenHere = field === currentField;
    const customIsChosen = field === Fields.custom;
    const compatibleType = customIsChosen || typeIsCompatibleWithField;
    const disabled = !customIsChosen && ((fieldIsChosen && !fieldIsChosenHere) || !compatibleType);
    const helpText = match<void, string>([
      [() => fieldIsChosenHere, ""],
      [() => customIsChosen, ""],
      [() => fieldIsChosen, t("import.fieldSelect.alreadyMapped")],
      [() => !compatibleType, t("import.fieldSelect.incompatibleData")],
      [() => true, ""],
    ])();

    return (
      <MenuItem disabled={disabled} ref={ref} onClick={onClick}>
        <ListItemText primary={label(field)} secondary={helpText} sx={{ marginTop: "2px", marginBottom: "2px" }} />
      </MenuItem>
    );
  },
);

FieldSelectMenuItem.displayName = "FieldSelectMenuItem";
export default FieldSelectMenuItem;
