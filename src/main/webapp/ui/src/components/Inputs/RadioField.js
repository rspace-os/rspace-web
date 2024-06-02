//@flow

import React, { type Node, type ComponentType, type ElementProps } from "react";
import { observer } from "mobx-react-lite";
import { makeStyles } from "tss-react/mui";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import FormControlLabel from "@mui/material/FormControlLabel";
import TextField from "@mui/material/TextField";
import InputAdornment from "@mui/material/InputAdornment";
import RemoveButton from "../../components/RemoveButton";
import clsx from "clsx";
import NoValue from "../../components/NoValue";
import { withStyles } from "Styles";
import Typography from "@mui/material/Typography";

const useStyles = makeStyles()((theme) => ({
  row: { display: "flex", flexDirection: "row", alignItems: "center" },
  spacedAroundRow: {
    justifyContent: "space-around",
  },
  spacedBetweenRow: {
    justifyContent: "space-between",
  },
  radio: { padding: theme.spacing(0.5) },
  formControlLabel: {
    marginRight: theme.spacing(0),
    overflowWrap: "anywhere",
  },
  smallerLabel: { fontSize: "13px" },
  primary: { color: theme.palette.primary.main },
  standard: { color: theme.palette.standardIcon.main },
  extraSpacingBetweenOptions: {
    marginBottom: theme.spacing(2),
  },
}));

export type RadioOption<OptionValue: string> = {
  label: Node,
  value: OptionValue,
  editing?: boolean,
};

export type RadioFieldArgs<OptionValue: string> = {|
  // required
  name: string,
  onChange: ({ target: { value: ?OptionValue, name: string, ... } }) => void,
  options: Array<RadioOption<OptionValue>>,
  value: ?OptionValue,

  // optional
  allowOptionDeletion?: boolean,
  allowRadioUnselection?: boolean,
  disabled?: boolean,
  hideWhenDisabled?: boolean,
  labelPlacement?: "top" | "start" | "bottom" | "end",
  noValueLabel?: ?string,
  onOptionChange?: (
    number,
    {| label: string, value: OptionValue, editing: true |}
  ) => void,
  onOptionRemove?: (number) => void,
  row?: boolean,
  smallText?: boolean,
|};

function RadioField<OptionValue: string>({
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
}: RadioFieldArgs<OptionValue>): Node {
  const { classes } = useStyles();

  const updateSelected = (newValue: ?OptionValue) => {
    onChange({ target: { value: newValue, name } });
  };

  const handleUpdateValue = (e: {
    target: { value: ?OptionValue, ... },
    ...
  }) => {
    updateSelected(e.target.value);
  };

  const handleRemoveOption = () => {
    updateSelected(null);
  };

  const radioOptions = () => {
    const filteredOptions =
      disabled && hideWhenDisabled
        ? options.filter((o) => o.value === value)
        : options;

    return disabled && !value && hideWhenDisabled ? (
      <NoValue label={noValueLabel ?? "No option selected"} />
    ) : (
      filteredOptions.map(
        (option: { value: OptionValue, label: Node, editing?: boolean }, i) => (
          <div
            key={i}
            className={clsx(
              classes.row,
              !option.editing && classes.spacedBetweenRow,
              typeof option.label !== "string" &&
                classes.extraSpacingBetweenOptions
            )}
          >
            <FormControlLabel
              classes={{
                root: classes.formControlLabel,
                label: clsx(smallText && classes.smallerLabel),
              }}
              value={option.value}
              control={
                <Radio
                  className={clsx(row && classes.radio)}
                  color="primary"
                  disabled={disabled || !option.value}
                  onClick={() =>
                    updateSelected(
                      allowRadioUnselection && value === option.value
                        ? null
                        : option.value
                    )
                  }
                />
              }
              labelPlacement={labelPlacement}
              label={option.editing ? "" : option.label}
              data-test-id={
                typeof option.label === "string"
                  ? option.label.replace(/\s/g, "")
                  : null
              }
            />
            {!disabled && allowOptionDeletion && !option.editing && (
              <RemoveButton
                onClick={() => {
                  if (value === option.value) handleRemoveOption();
                  onOptionRemove(i);
                }}
                title={`Delete Option: ${option.value}`}
              />
            )}
            {option.editing && (
              <TextField
                autoFocus={!option.value}
                variant="standard"
                value={option.value}
                onChange={(e) => {
                  if (value === option.value) handleUpdateValue(e);
                  onOptionChange(i, {
                    value: e.target.value,
                    label: e.target.value,
                    editing: true,
                  });
                }}
                placeholder={`Option ${i + 1}`}
                error={option.value === ""}
                helperText={
                  option.value.length === 0
                    ? "Option value cannot be empty"
                    : null
                }
                InputProps={{
                  endAdornment: (
                    <InputAdornment position="end">
                      <RemoveButton
                        onClick={() => {
                          if (value === option.value) handleRemoveOption();
                          onOptionRemove(i);
                        }}
                        title="Delete New Option"
                      />
                    </InputAdornment>
                  ),
                }}
              />
            )}
          </div>
        )
      )
    );
  };
  return (
    <RadioGroup
      className={clsx(row && classes.spacedAroundRow)}
      name={name}
      aria-label={name}
      value={value || null} // null prevents un/controlled component error msg
      disabled={disabled}
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

export default (observer(RadioField): typeof RadioField);

/*
 * These two components are for defining complex labels, where there is a pithy
 * heading and more detailed explanation required.
 */
type OptionHeadingArgs = {|
  children: Node,
|};

export const OptionHeading: ComponentType<OptionHeadingArgs> = withStyles<
  ElementProps<typeof Typography>,
  { root: string }
>(() => ({
  root: {
    letterSpacing: 0,
  },
}))(Typography);

type OptionExplanationArgs = {|
  children: Node,
  "data-testid"?: string,
|};

export const OptionExplanation: ComponentType<OptionExplanationArgs> =
  withStyles<ElementProps<typeof Typography>, { root: string }>(() => ({
    root: {
      fontSize: "0.825em",
    },
  }))((props) => <Typography {...props} variant="body2" />);
