import React, { useState, useEffect } from "react";
import { observer } from "mobx-react-lite";
import TextField from "@mui/material/TextField";
import Box from "@mui/material/Box";
import { type Alias } from "../../../stores/definitions/Sample";
import { makeStyles } from "tss-react/mui";
import { toTitleCase } from "../../../util/Util";
import { type UseState } from "../../../util/types";
import { type HasEditableFields } from "../../../stores/definitions/Editable";
import RadioField, {
  type RadioOption,
} from "../../../components/Inputs/RadioField";
import FormField from "../../../components/Inputs/FormField";

type DefaultAliasOption =
  | "aliquot"
  | "component"
  | "individual"
  | "piece"
  | "portion"
  | "section"
  | "subsample"
  | "unit"
  | "volume";

const ALIAS_DEFAULTS: Array<DefaultAliasOption> = [
  "aliquot",
  "component",
  "individual",
  "piece",
  "portion",
  "section",
  "subsample",
  "unit",
  "volume",
];

function pluralise(option: DefaultAliasOption): string {
  // generating plural this way works for all default values
  return `${option}s`;
}

type AliasSelection =
  | { source: "radio"; option: DefaultAliasOption }
  | { source: "custom"; alias: string; plural: string };

function convert(a: Alias): AliasSelection {
  // use of find is to make Flow happy; includes doens't do the trick
  const option = ALIAS_DEFAULTS.find(
    (aliasDefault) => aliasDefault === a.alias
  );
  if (option) {
    return { source: "radio", option };
  }
  return { source: "custom", alias: a.alias, plural: a.plural };
}

const useStyles = makeStyles()((theme) => ({
  customInput: {
    margin: theme.spacing(0.5),
    width: "45%",
  },
  customValue: {
    color: "black",
  },
}));

const aliasOptions = ALIAS_DEFAULTS.map((value) => ({
  value,
  label: toTitleCase(value),
}));

const radioButtons: Array<RadioOption<DefaultAliasOption | "custom">> = [
  ...aliasOptions,
  { value: "custom", label: "Custom" },
];

type Fields = {
  subSampleAlias: Alias;
};

type SubSampleAliasArgs<FieldOwner extends HasEditableFields<Fields>> = {
  fieldOwner: FieldOwner;
  onErrorStateChange: (hasError: boolean) => void;
};

function SubSampleAlias<FieldOwner extends HasEditableFields<Fields>>({
  fieldOwner,
  onErrorStateChange,
}: SubSampleAliasArgs<FieldOwner>): React.ReactNode {
  const emptyAlias: Alias = { alias: "", plural: "" };
  const min = 2;
  const max = 30;
  const helperText = `Please enter at least ${min} characters, and no more than ${max}`;

  const { classes } = useStyles();
  const aliasDisabled = !fieldOwner.isFieldEditable("subSampleAlias");
  const aliasValue = fieldOwner.fieldValues.subSampleAlias;

  const [value, setValue]: UseState<AliasSelection> = useState(
    convert(aliasValue)
  );

  useEffect(() => {
    /*
     * If toggling between edit mode and view mode, then unconditionally reset
     * the displayed value
     */
    setValue(convert(aliasValue));
  }, [aliasDisabled]);

  useEffect(() => {
    /*
     * If the underlying alias value has changed, then reset the displayed
     * value only if the field is currently disabled. This so that we don't
     * overwrite values entered by the user/select one of the provided alias
     * options when they enter a custom one.
     */
    if (aliasDisabled) setValue(convert(aliasValue));
  }, [aliasValue]);

  const handleRadioChoice = (option: DefaultAliasOption | "custom" | null) => {
    if (!option) return;
    if (option === "custom") {
      setValue({ source: "custom", ...emptyAlias });
      fieldOwner.setFieldsDirty({
        subSampleAlias: { ...emptyAlias },
      });
    } else {
      setValue({ source: "radio", option });
      fieldOwner.setFieldsDirty({
        subSampleAlias: {
          alias: option,
          plural: pluralise(option),
        },
      });
    }
  };

  const customSelected = value.source === "custom";

  const error = (str: string) => str.length < min || str.length > max;

  return (
    <FormField
      label="Subsample Alias"
      explanation={
        !aliasDisabled &&
        "The name for subsamples of the samples created from this template."
      }
      value={customSelected ? "custom" : value.option}
      asFieldset
      renderInput={() => (
        <>
          <RadioField
            name="alias"
            value={customSelected ? "custom" : value.option}
            disabled={aliasDisabled}
            onChange={(e) => {
              handleRadioChoice(e.target.value);
            }}
            options={radioButtons}
          />
          {(!aliasDisabled || customSelected) && (
            <Box
              component="div"
              sx={{
                px: 2,
                width: "100%",
                display: "flex",
                flexDirection: "row",
                justifyContent: "space-between",
              }}
            >
              <TextField
                data-testid="aliasField_singleBox"
                className={classes.customInput}
                label="Singular"
                variant={aliasDisabled ? "standard" : "outlined"}
                size="small"
                multiline
                placeholder="Minimum 2 char."
                value={customSelected ? value.alias : ""}
                onChange={(e) => {
                  setValue({
                    source: "custom",
                    alias: e.target.value,
                    plural: aliasValue.plural,
                  });
                  fieldOwner.setFieldsDirty({
                    subSampleAlias: {
                      ...aliasValue,
                      alias: e.target.value,
                    },
                  });
                  onErrorStateChange(
                    error(e.target.value) || error(aliasValue.plural)
                  );
                }}
                error={error(aliasValue.alias)}
                helperText={error(aliasValue.alias) ? helperText : ""}
                disabled={aliasDisabled || !customSelected}
                // eslint-disable-next-line jsx-a11y/no-autofocus
                autoFocus={customSelected}
                InputProps={{
                  classes: {
                    input: classes.customValue,
                  },
                }}
              />
              <TextField
                data-testid="aliasField_pluralBox"
                className={classes.customInput}
                label="Plural"
                variant={aliasDisabled ? "standard" : "outlined"}
                size="small"
                multiline
                placeholder="Minimum 2 char."
                value={customSelected ? value.plural : ""}
                onChange={(e) => {
                  setValue({
                    source: "custom",
                    alias: aliasValue.alias,
                    plural: e.target.value,
                  });
                  fieldOwner.setFieldsDirty({
                    subSampleAlias: {
                      ...aliasValue,
                      plural: e.target.value,
                    },
                  });
                  onErrorStateChange(
                    error(aliasValue.alias) || error(e.target.value)
                  );
                }}
                error={error(aliasValue.plural)}
                helperText={error(aliasValue.plural) ? helperText : ""}
                disabled={aliasDisabled || !customSelected}
                InputProps={{
                  classes: {
                    input: classes.customValue,
                  },
                }}
              />
            </Box>
          )}
        </>
      )}
    />
  );
}

export default observer(SubSampleAlias);
