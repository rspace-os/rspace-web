import React, { useId } from "react";
import { observer } from "mobx-react-lite";
import FormControl from "@mui/material/FormControl";
import FormGroup from "@mui/material/FormGroup";
import FormLabel from "@mui/material/FormLabel";
import FormHelperText from "@mui/material/FormHelperText";
import Stack from "@mui/material/Stack";
import { useTheme, type SxProps, type Theme } from "@mui/material/styles";
import { Heading } from "../DynamicHeadingLevel";


function FullLabel({
  label,
  explanation,
  explanationId,
  disabled: _disabled,
}: {
  explanation?: React.ReactNode;
  explanationId: string;
  label?: string;
  disabled?: boolean;
}): React.ReactNode {
  const theme = useTheme();
  return (
    <>
      <Heading>{label}</Heading>
      {explanation && (
        <div
          style={{
            fontWeight: 400,
            lineHeight: 1.5,
            marginTop: theme.spacing(0.5),
          }}
          id={explanationId}
        >
          {explanation}
        </div>
      )}
    </>
  );
}

export type FormControlArgs = {
  label?: string;
  children: React.ReactNode;
  error?: boolean;
  helperText?: string;
  inline?: boolean;
  actions?: React.ReactNode;
  classes?: { formLabel?: string };
  slotProps?: { label?: { sx?: SxProps<Theme> } };
  dataTestId?: string;
  required?: boolean;
  explanation?: React.ReactNode;
  "aria-label"?: string;
  flexWrap?: "nowrap" | "wrap" | "wrap-reverse" | "initial" | "inherit";
  disabled?: boolean;
};

function CustomFormControl({
  label,
  children,
  error = false,
  helperText,
  inline = false,
  actions = <div></div>,
  classes = {},
  slotProps,
  dataTestId,
  required,
  explanation,
  ["aria-label"]: ariaLabel,
  flexWrap = "initial",
  disabled,
}: FormControlArgs): React.ReactNode {
  const explanationId = useId();

  return (
    <FormControl
      component="fieldset"
      error={error}
      data-test-id={dataTestId}
      required={required}
      aria-label={ariaLabel ?? label}
      {...(explanation ? { "aria-describedby": explanationId } : {})}
      fullWidth
      sx={{
        "& .MuiInputBase-root.Mui-disabled, & .MuiFormControlLabel-label.Mui-disabled":
          {
            color: "black",
            "& input": { color: "unset" },
            "& .MuiSvgIcon-root.MuiSelect-icon": { display: "none" },
          },
        "& .MuiSelect-root.MuiSelect-select.MuiSelect-outlined": {
          padding: "11px 10px 10px 10px",
        },
        "& .Mui-disabled::before": { borderBottom: "0px !important" },
      }}
    >
      <Stack
        direction="row"
        sx={{
          justifyContent: "space-between",
          alignItems: "center",
          flexWrap: "wrap",
        }}
      >
        {typeof label !== "undefined" && (
          <FormLabel
            component="legend"
            classes={{ root: classes.formLabel ?? "" }}
            sx={slotProps?.label?.sx}
            required={required}
          >
            <FullLabel
              label={label}
              explanation={explanation}
              explanationId={explanationId}
              disabled={disabled}
            />
          </FormLabel>
        )}
        {actions}
      </Stack>
      <FormGroup style={{ display: inline ? "inline" : "inherit", flexWrap }}>
        {children}
      </FormGroup>
      {error && <FormHelperText>{helperText}</FormHelperText>}
    </FormControl>
  );
}

export default observer(CustomFormControl);
