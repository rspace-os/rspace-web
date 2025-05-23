import React from "react";
import Checkbox from "@mui/material/Checkbox";
import { makeStyles } from "tss-react/mui";

const useStyles = makeStyles()((theme) => ({
  label: {
    padding: theme.spacing(0, 0.25, 0, 1.5),
    backgroundColor: theme.palette.primary.background,
    borderRadius: theme.spacing(0.5),
    letterSpacing: theme.typography.letterSpacing.spaced,
    marginLeft: "auto",
    position: "absolute",
    right: 0,
  },
}));

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
export default function ChooseToEdit({
  checked,
  onChange,
  ariaControls,
}: ChooseToEditArgs): React.ReactNode {
  const { classes } = useStyles();
  const id = React.useId();

  return (
    <label className={classes.label} htmlFor={id}>
      Batch edit this field
      <Checkbox
        id={id}
        aria-controls={ariaControls}
        aria-disabled={false}
        checked={checked}
        onChange={({ target }) => onChange(target.checked)}
        size="small"
        color="primary"
      />
    </label>
  );
}
