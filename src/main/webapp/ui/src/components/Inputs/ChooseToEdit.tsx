import Box from "@mui/material/Box";
import Checkbox from "@mui/material/Checkbox";
import React from "react";
import { useTranslation } from "react-i18next";

type ChooseToEditArgs = {
  checked: boolean;
  onChange: (newCheckedState: boolean) => void;
  ariaControls?: string;
};

/**
 * This component is a simple widget for toggling whether a given field is
 * editable when batch editing, and as such whether the value will be applied
 * to all of the records being edited.
 */
export default function ChooseToEdit({ checked, onChange, ariaControls }: ChooseToEditArgs): React.ReactNode {
  const { t } = useTranslation("common");
  const id = React.useId();

  return (
    <Box
      component="label"
      htmlFor={id}
      sx={(theme) => ({
        p: theme.spacing(0, 0.25, 0, 1.5),
        border: theme.borders.section,
        borderRadius: theme.spacing(0.5),
        letterSpacing: theme.typography.letterSpacing.spaced,
        ml: "auto",
        position: "absolute",
        right: 0,
      })}
    >
      {t("inputs.chooseToEdit.label")}
      <Checkbox
        id={id}
        aria-controls={ariaControls}
        aria-disabled={false}
        checked={checked}
        onChange={({ target }) => onChange(target.checked)}
        size="small"
        color="primary"
      />
    </Box>
  );
}
