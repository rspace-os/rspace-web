import Box from "@mui/material/Box";
import InputAdornment from "@mui/material/InputAdornment";
import MenuItem from "@mui/material/MenuItem";
import Select, { type SelectChangeEvent } from "@mui/material/Select";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import StringField from "../../../components/Inputs/StringField";
import type { HasEditableFields } from "../../../stores/definitions/Editable";
import type { BatchName } from "../../../stores/models/InventoryBaseRecordCollection";
import BatchFormField from "../Inputs/BatchFormField";

const MIN = 2;
const MAX = 255;

function Name<Fields extends { name: BatchName }, FieldOwner extends HasEditableFields<Fields>>({
  fieldOwner,
  allowAlphabeticalSuffix,
  onErrorStateChange,
}: {
  fieldOwner: FieldOwner;
  allowAlphabeticalSuffix: boolean; // has to be disabled if there are more than 26 items selected
  onErrorStateChange: (isError: boolean) => void;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const [initial, setInitial] = useState(true);

  const lengthOfSuffix = {
    NONE: 0,
    INDEX_NUMBER: 2,
    INDEX_LETTER: 2,
    CREATED: 19,
  }[fieldOwner.fieldValues.name.suffix];

  const handleChange = ({ target: { value } }: { target: { value: string } }) => {
    const suffix = fieldOwner.fieldValues.name.suffix;
    fieldOwner.setFieldsDirty({ name: { common: value, suffix } });
    setInitial(false);
    onErrorStateChange(
      value.length + lengthOfSuffix > MAX ||
        value.length + lengthOfSuffix < MIN ||
        (suffix === "NONE" && value.trim().length === 0),
    );
  };

  const handleChangeSuffix = (event: SelectChangeEvent<"NONE" | "INDEX_NUMBER" | "INDEX_LETTER" | "CREATED">) => {
    const value = event.target.value;
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
      return t("fields.name.nonWhitespace");
    if (fieldOwner.fieldValues.name.common.length + lengthOfSuffix < MIN && !initial)
      return t("fields.name.minLength", { min: MIN });
    if (fieldOwner.fieldValues.name.common.length + lengthOfSuffix > MAX)
      return t("fields.name.maxLength", { max: MAX - lengthOfSuffix });
    return null;
  };

  return (
    <BatchFormField
      label={t("fields.name.label")}
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
          slotProps={{
            input: {
              endAdornment: !disabled ? (
                <InputAdornment position="end">
                  <Box sx={{ mx: 1 }}>
                    {t("fields.name.suffix.label")}&nbsp;
                    <Select variant="standard" value={fieldOwner.fieldValues.name.suffix} onChange={handleChangeSuffix}>
                      <MenuItem value="NONE">{t("fields.name.suffix.none")}</MenuItem>
                      <MenuItem value="INDEX_NUMBER">{t("fields.name.suffix.numericalIndex")}</MenuItem>
                      <MenuItem disabled={!allowAlphabeticalSuffix} value="INDEX_LETTER">
                        {t("fields.name.suffix.alphabeticalIndex")}
                      </MenuItem>
                      <MenuItem value="CREATED">{t("fields.name.suffix.dateOfCreation")}</MenuItem>
                    </Select>
                  </Box>
                </InputAdornment>
              ) : null,
            },
          }}
        />
      )}
    />
  );
}

export default observer(Name);
