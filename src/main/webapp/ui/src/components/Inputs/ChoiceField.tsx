import Checkbox from "@mui/material/Checkbox";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormGroup from "@mui/material/FormGroup";
import Grid from "@mui/material/Grid";
import InputAdornment from "@mui/material/InputAdornment";
import TextField from "@mui/material/TextField";
import type React from "react";
import { useTranslation } from "react-i18next";
import NoValue from "../../components/NoValue";
import RemoveButton from "../../components/RemoveButton";

export type ChoiceOption<OptionValue extends string> = {
  value: OptionValue;
  label: React.ReactNode;
  disabled?: boolean;
  editing?: boolean;
};

export type ChoiceFieldArgs<OptionValue extends string> = {
  // required
  options: ReadonlyArray<ChoiceOption<OptionValue>>;
  value: ReadonlyArray<OptionValue>;

  // optional
  name: string;
  disabled?: boolean;
  onChange?: (event: { target: { name: string; value: ReadonlyArray<OptionValue> } }) => void;
  allowOptionDeletion?: boolean;
  onOptionChange?: (index: number, option: { label: string; value: OptionValue; editing: true }) => void;
  onOptionRemove?: (index: number) => void;
  hideWhenDisabled?: boolean;
};

export default function ChoiceField<OptionValue extends string>({
  disabled,
  name,
  onChange,
  value = [],
  options = [],
  allowOptionDeletion = false,
  onOptionChange = () => {},
  onOptionRemove = () => {},
  hideWhenDisabled = true,
}: ChoiceFieldArgs<OptionValue>): React.ReactNode {
  const { t } = useTranslation("common");
  const updateSelected = (newValues: ReadonlyArray<OptionValue>) => {
    onChange?.({ target: { name, value: newValues } });
  };

  const handleToggleCheckbox = (e: { target: { value: OptionValue; checked: boolean } }) => {
    let newValues: typeof value = [];
    if (value.includes(e.target.value) && !e.target.checked) {
      newValues = value.filter((v) => v !== e.target.value);
    } else if (!value.includes(e.target.value) && e.target.checked) {
      newValues = [...value, e.target.value];
    }
    updateSelected(newValues);
  };

  const handleUpdateValue = (optionValue: OptionValue, targetValue: OptionValue) => {
    const newValues = value.filter((v) => v !== optionValue).concat(targetValue);
    updateSelected(newValues);
  };

  const handleRemoveOption = (optionValue: string) => {
    const newValues = value.filter((v) => v !== optionValue);
    updateSelected(newValues);
  };

  const filteredOptions = (): ReadonlyArray<ChoiceOption<OptionValue>> => {
    return disabled && hideWhenDisabled ? options.filter((o) => value.includes(o.value)) : options;
  };

  return disabled && value.length === 0 && hideWhenDisabled ? (
    <NoValue label={t("inputs.optionField.noOptionSelected")} />
  ) : (
    <FormGroup>
      {filteredOptions().map((option, i) => (
        <Grid
          container
          direction="row"
          sx={{
            justifyContent: option.editing ? "flex-start" : "space-between",
            alignItems: "center",
          }}
          key={i}
        >
          <FormControlLabel
            control={
              <Checkbox
                color="primary"
                name={`${name} - ${i}`}
                value={option.value}
                checked={value.includes(option.value)}
                onChange={(event) => {
                  handleToggleCheckbox({
                    target: {
                      value: event.target.value as OptionValue,
                      checked: event.target.checked,
                    },
                  });
                }}
                disabled={disabled || option.disabled || !option.value}
              />
            }
            label={option.editing ? "" : option.label}
            sx={{
              marginRight: option.editing ? 0 : "initial",
              overflowWrap: "anywhere",
            }}
          />
          {!disabled && allowOptionDeletion && !option.editing && (
            <RemoveButton
              title={t("inputs.optionField.deleteOption", { option: option.value })}
              onClick={() => {
                if (value.includes(option.value)) handleRemoveOption(option.value);
                onOptionRemove(i);
              }}
            />
          )}

          {option.editing && (
            <TextField
              variant="standard"
              autoFocus={!option.value}
              value={option.value}
              onChange={(e) => {
                if (value.includes(option.value)) {
                  handleUpdateValue(option.value, e.target.value as OptionValue);
                }
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
                        title={t("inputs.optionField.deleteNewOption")}
                        onClick={() => {
                          if (value.includes(option.value)) handleRemoveOption(option.value);
                          onOptionRemove(i);
                        }}
                      />
                    </InputAdornment>
                  ),
                },
              }}
            />
          )}
        </Grid>
      ))}
    </FormGroup>
  );
}
