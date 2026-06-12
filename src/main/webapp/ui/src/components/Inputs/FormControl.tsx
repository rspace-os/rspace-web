import Box from "@mui/material/Box";
import FormControl from "@mui/material/FormControl";
import { formControlLabelClasses } from "@mui/material/FormControlLabel";
import FormGroup from "@mui/material/FormGroup";
import FormHelperText from "@mui/material/FormHelperText";
import { inputBaseClasses } from "@mui/material/InputBase";
import { selectClasses } from "@mui/material/Select";
import Stack from "@mui/material/Stack";
import { svgIconClasses } from "@mui/material/SvgIcon";
import { type SxProps, type Theme, useTheme } from "@mui/material/styles";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useId } from "react";
import { Heading } from "../DynamicHeadingLevel";
import FieldLabel from "./FieldLabel";

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
        <Box
          sx={{
            fontWeight: 400,
            lineHeight: 1.5,
            marginTop: theme.spacing(0.5),
          }}
          id={explanationId}
        >
          {explanation}
        </Box>
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
  "data-test-id"?: string;
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
  "data-test-id": dataTestId,
  required,
  explanation,
  // biome-ignore lint/complexity/useLiteralKeys: initial biome migration
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
        [`& .${inputBaseClasses.root}.${inputBaseClasses.disabled}, & .${formControlLabelClasses.label}.${formControlLabelClasses.disabled}`]:
          {
            color: "black",
            "& input": { color: "unset" },
            [`& .${svgIconClasses.root}.${selectClasses.icon}`]: {
              display: "none",
            },
          },
        [`& .${selectClasses.root}.${selectClasses.select}.${selectClasses.outlined}`]: {
          padding: "11px 10px 10px 10px",
        },
        [`& .${inputBaseClasses.disabled}::before`]: { borderBottom: "0px !important" },
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
          <FieldLabel
            asFieldset
            classes={{ root: classes.formLabel ?? "" }}
            sx={slotProps?.label?.sx}
            required={required}
          >
            <FullLabel label={label} explanation={explanation} explanationId={explanationId} disabled={disabled} />
          </FieldLabel>
        )}
        {actions}
      </Stack>
      <FormGroup sx={{ display: inline ? "inline" : "inherit", flexWrap }}>{children}</FormGroup>
      {error && <FormHelperText>{helperText}</FormHelperText>}
    </FormControl>
  );
}

export default observer(CustomFormControl);
