//@flow

import React, { type Node, useState } from "react";
import { observer } from "mobx-react-lite";
import StringField from "../../../components/Inputs/StringField";
import { type HasEditableFields } from "../../../stores/definitions/Editable";
import InputAdornment from "@mui/material/InputAdornment";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import { type BatchName } from "../../../stores/models/InventoryBaseRecordCollection";
import BatchFormField from "../Inputs/BatchFormField";

const MIN = 2;
const MAX = 255;

function Name<
  Fields: { name: BatchName, ... },
  FieldOwner: HasEditableFields<Fields>
>({
  fieldOwner,
  allowAlphabeticalSuffix,
  onErrorStateChange,
}: {|
  fieldOwner: FieldOwner,
  allowAlphabeticalSuffix: boolean, // has to be disabled if there are more than 26 items selected
  onErrorStateChange: (boolean) => void,
|}): Node {
  const [initial, setInitial] = useState(true);

  const lengthOfSuffix = {
    NONE: 0,
    INDEX_NUMBER: 2,
    INDEX_LETTER: 2,
    CREATED: 19,
  }[fieldOwner.fieldValues.name.suffix];

  const handleChange = ({
    target: { value },
  }: {
    target: { value: string, ... },
    ...
  }) => {
    const suffix = fieldOwner.fieldValues.name.suffix;
    fieldOwner.setFieldsDirty({ name: { common: value, suffix } });
    setInitial(false);
    onErrorStateChange(
      value.length + lengthOfSuffix > MAX ||
        value.length + lengthOfSuffix < MIN ||
        (suffix === "NONE" && value.trim().length === 0)
    );
  };

  const handleChangeSuffix = ({
    target: { value },
  }: {
    target: {
      value: "NONE" | "INDEX_NUMBER" | "INDEX_LETTER" | "CREATED",
      ...
    },
    ...
  }) => {
    const common = fieldOwner.fieldValues.name.common;
    fieldOwner.setFieldsDirty({ name: { common, suffix: value } });
    setInitial(false);
  };

  const errorMessage = () => {
    if (
      fieldOwner.fieldValues.name.suffix === "NONE" &&
      fieldOwner.fieldValues.name.common.trim().length === 0 &&
      !initial
    )
      return "Name must include at least one non-whitespace character.";
    if (
      fieldOwner.fieldValues.name.common.length + lengthOfSuffix < MIN &&
      !initial
    )
      return `Name must be at least ${MIN} characters.`;
    if (fieldOwner.fieldValues.name.common.length + lengthOfSuffix > MAX)
      return `Name must be no longer than ${MAX - lengthOfSuffix} characters.`;
    return null;
  };

  return (
    <BatchFormField
      label="Name"
      maxLength={MAX - lengthOfSuffix}
      error={Boolean(errorMessage())}
      disabled={!fieldOwner.isFieldEditable("name")}
      value={fieldOwner.fieldValues.name.common}
      helperText={errorMessage()}
      noValueLabel={fieldOwner.noValueLabel.name}
      canChooseWhichToEdit={fieldOwner.canChooseWhichToEdit}
      setDisabled={(checked) => {
        fieldOwner.setFieldEditable("name", checked);
      }}
      renderInput={({ disabled, ...props }) => (
        <StringField
          onBlur={() => {
            setInitial(false);
          }}
          {...props}
          disabled={disabled}
          onChange={handleChange}
          data-testid="NameField"
          InputProps={{
            endAdornment: !disabled ? (
              <InputAdornment position="end">
                Suffix:&nbsp;
                <Select
                  variant="standard"
                  value={fieldOwner.fieldValues.name.suffix}
                  onChange={handleChangeSuffix}
                >
                  <MenuItem value="NONE">None</MenuItem>
                  <MenuItem value="INDEX_NUMBER">
                    Numerical Index: 1, 2, 3...
                  </MenuItem>
                  <MenuItem
                    disabled={!allowAlphabeticalSuffix}
                    value="INDEX_LETTER"
                  >
                    Alphabetical Index: A, B, C...
                  </MenuItem>
                  <MenuItem value="CREATED">Date of Creation</MenuItem>
                </Select>
              </InputAdornment>
            ) : null,
          }}
        />
      )}
    />
  );
}

export default (observer(Name): typeof Name);
