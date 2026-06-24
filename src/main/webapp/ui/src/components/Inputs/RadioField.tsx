import Box from "@mui/material/Box";
import FormControlLabel, { formControlLabelClasses } from "@mui/material/FormControlLabel";
import InputAdornment from "@mui/material/InputAdornment";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import NoValue from "../../components/NoValue";
import RemoveButton from "../../components/RemoveButton";

export type RadioOption<OptionValue extends string> = {
  label: React.ReactNode;
  value: OptionValue;
  editing?: boolean;
};

export type RadioFieldArgs<OptionValue extends string> = {
  name: string;
  onChange: (event: { target: { value: OptionValue | null; name: string } }) => void;
  options: Array<RadioOption<OptionValue>>;
  value: OptionValue | null;
  allowOptionDeletion?: boolean;
  allowRadioUnselection?: boolean;
  disabled?: boolean;
  hideWhenDisabled?: boolean;
  labelPlacement?: "top" | "start" | "bottom" | "end";
  noValueLabel?: string | null;
  onOptionChange?: (index: number, newOption: { label: React.ReactNode; value: OptionValue; editing: true }) => void;
  onOptionRemove?: (index: number) => void;
  row?: boolean;
  smallText?: boolean;
};

function RadioField<OptionValue extends string>({
  disabled,
  name,
  onChange,
  options,
  value,
  allowOptionDeletion = false,
  allowRadioUnselection = false,
  hideWhenDisabled = true,
  labelPlacement = "end",
  noValueLabel,
  onOptionChange = () => {},
  onOptionRemove = () => {},
  row = false,
  smallText = false,
}: RadioFieldArgs<OptionValue>): React.ReactNode {
  const { t } = useTranslation("common");
  const updateSelected = (newValue: OptionValue | null) => {
    onChange({ target: { value: newValue, name } });
  };

  const handleUpdateValue = (e: { target: { value: OptionValue | null } }) => {
    updateSelected(e.target.value);
  };

  const handleRemoveOption = () => {
    updateSelected(null);
  };

  const radioOptions = () => {
    const filteredOptions = disabled && hideWhenDisabled ? options.filter((o) => o.value === value) : options;

    return disabled && !value && hideWhenDisabled ? (
      <NoValue label={noValueLabel ?? t("inputs.optionField.noOptionSelected")} />
    ) : (
      filteredOptions.map(
        (
          option: {
            value: OptionValue;
            label: React.ReactNode;
            editing?: boolean;
          },
          i,
        ) => (
          <Box
            key={i}
            sx={{
              display: "flex",
              flexDirection: "row",
              alignItems: "center",
              justifyContent: !option.editing ? "space-between" : undefined,
              marginBottom: typeof option.label !== "string" ? "16px" : undefined,
            }}
          >
            <FormControlLabel
              sx={{
                mr: 0,
                overflowWrap: "anywhere",
                ...(smallText
                  ? {
                      [`& .${formControlLabelClasses.label}`]: {
                        fontSize: "13px",
                      },
                    }
                  : {}),
              }}
              value={option.value}
              control={
                <Radio
                  sx={row ? { p: 0.5 } : undefined}
                  color="primary"
                  disabled={disabled || !option.value}
                  onClick={() => updateSelected(allowRadioUnselection && value === option.value ? null : option.value)}
                />
              }
              labelPlacement={labelPlacement}
              label={option.editing ? "" : option.label}
              data-test-id={typeof option.label === "string" ? option.label.replace(/\s/g, "") : null}
            />
            {!disabled && allowOptionDeletion && !option.editing && (
              <RemoveButton
                onClick={() => {
                  if (value === option.value) handleRemoveOption();
                  onOptionRemove(i);
                }}
                title={t("inputs.optionField.deleteOption", { option: option.value })}
              />
            )}
            {option.editing && (
              <TextField
                autoFocus={!option.value}
                variant="standard"
                value={option.value}
                onChange={(e) => {
                  if (value === option.value)
                    handleUpdateValue({
                      target: { value: e.target.value as OptionValue },
                    });
                  onOptionChange(i, {
                    value: e.target.value as OptionValue,
                    label: e.target.value,
                    editing: true,
                  });
                }}
                placeholder={`Option ${i + 1}`}
                error={option.value === ""}
                helperText={option.value.length === 0 ? t("inputs.optionField.emptyOption") : null}
                slotProps={{
                  input: {
                    endAdornment: (
                      <InputAdornment position="end">
                        <RemoveButton
                          onClick={() => {
                            if (value === option.value) handleRemoveOption();
                            onOptionRemove(i);
                          }}
                          title={t("inputs.optionField.deleteNewOption")}
                        />
                      </InputAdornment>
                    ),
                  },
                }}
              />
            )}
          </Box>
        ),
      )
    );
  };
  return (
    <RadioGroup
      sx={row ? { justifyContent: "space-around" } : undefined}
      name={name}
      aria-label={name}
      value={value || null} // null prevents un/controlled component error msg
      row={row}
    >
      {/*
        This is a lambda and not a component because when it is a component
        the text field loses focus after each keypress
      */}
      {radioOptions()}
    </RadioGroup>
  );
}

export default observer(RadioField);

/*
 * These two components are for defining complex labels, where there is a pithy
 * heading and more detailed explanation required.
 */
type OptionHeadingArgs = {
  children: React.ReactNode;
};

export function OptionHeading({ children }: OptionHeadingArgs): React.ReactNode {
  return <Typography sx={{ letterSpacing: 0 }}>{children}</Typography>;
}

type OptionExplanationArgs = {
  children: React.ReactNode;
  "data-testid"?: string;
};

export function OptionExplanation({ children, ...rest }: OptionExplanationArgs): React.ReactNode {
  return (
    <Typography variant="body2" sx={{ fontSize: "0.825em" }} {...rest}>
      {children}
    </Typography>
  );
}
