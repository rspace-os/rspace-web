import React, { useId } from "react";
import { observer } from "mobx-react-lite";
import FormControl from "@mui/material/FormControl";
import FormGroup from "@mui/material/FormGroup";
import FormLabel from "@mui/material/FormLabel";
import FormHelperText from "@mui/material/FormHelperText";
import { makeStyles } from "tss-react/mui";
import { withStyles } from "Styles";
import clsx from "clsx";

const useStyles = makeStyles()(() => ({
  formGroup: {
    "& .MuiInputBase-root.Mui-disabled, & .MuiFormControlLabel-label.Mui-disabled":
      {
        color: "black",
        "& input": {
          WebkitTextFillColor: "unset",
        },
        "& .MuiSvgIcon-root.MuiSelect-icon": {
          display: "none",
        },
      },
    "& .MuiSelect-root.MuiSelect-select.MuiSelect-outlined": {
      padding: "11px 10px 10px 10px",
    },
    "& .Mui-disabled::before": {
      borderBottom: "0px !important",
    },
  },
  label: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    flexWrap: "wrap",
  },
}));

const FullLabel = withStyles<
  { explanation?: React.ReactNode; explanationId: string; label?: string },
  { explanationText: string }
>((theme) => ({
  explanationText: {
    fontWeight: 400,
    lineHeight: 1.5,
    marginTop: theme.spacing(0.5),
  },
}))(({ classes, label, explanation, explanationId }) => {
  return (
    <>
      {label}
      <div className={classes.explanationText} id={explanationId}>
        {explanation}
      </div>
    </>
  );
});

export type FormControlArgs = {
  label?: string;
  children: React.ReactNode;
  error?: boolean;
  helperText?: string;
  inline?: boolean;
  actions?: React.ReactNode;
  classes?: { formLabel?: string; formControl?: string };
  dataTestId?: string;
  required?: boolean;
  explanation?: React.ReactNode;
  "aria-label"?: string;
};

function CustomFormControl({
  label,
  children,
  error = false,
  helperText,
  inline = false,
  actions = <div></div>,
  classes = {},
  dataTestId,
  required,
  explanation,
  ["aria-label"]: ariaLabel,
}: FormControlArgs): React.ReactNode {
  const { classes: additionalClasses } = useStyles();
  const explanationId = useId();

  const fullLabel = explanation ? (
    <FullLabel
      label={label}
      explanation={explanation}
      explanationId={explanationId}
    />
  ) : (
    label
  );

  return (
    <FormControl
      component="fieldset"
      error={error}
      data-test-id={dataTestId}
      required={required}
      aria-label={ariaLabel ?? label}
      {...(explanation ? { "aria-describedby": explanationId } : {})}
      fullWidth
      className={clsx(additionalClasses.formGroup, classes.formControl)}
    >
      <div className={additionalClasses.label}>
        {typeof label !== "undefined" && (
          <FormLabel
            component="legend"
            classes={{ root: classes.formLabel ?? "" }}
            required={required}
          >
            {fullLabel}
          </FormLabel>
        )}
        {actions}
      </div>
      <FormGroup style={{ display: inline ? "inline" : "inherit" }}>
        {children}
      </FormGroup>
      {error && <FormHelperText>{helperText}</FormHelperText>}
    </FormControl>
  );
}

export default observer(CustomFormControl);
