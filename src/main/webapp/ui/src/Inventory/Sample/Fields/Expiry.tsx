import React from "react";
import { observer } from "mobx-react-lite";
import Alert from "@mui/material/Alert";
import { type HasEditableFields } from "../../../stores/definitions/Editable";
import {
  todaysDate,
  truncateIsoTimestamp,
} from "../../../stores/definitions/Units";
import DateField from "../../../components/Inputs/DateField";
import BatchFormField from "../../components/Inputs/BatchFormField";

function ExpiryDate<
  Fields extends {
    expiryDate: string | null;
  },
  FieldOwner extends HasEditableFields<Fields>
>({
  fieldOwner,
  onErrorStateChange,
}: {
  fieldOwner: FieldOwner;
  onErrorStateChange: (value: boolean) => void;
}): React.ReactNode {
  const handleChange = ({
    target: { value },
  }: {
    target: { value: Date | null };
  }) => {
    onErrorStateChange(value ? isNaN(value.getTime()) : false);
    fieldOwner.setFieldsDirty({
      // Yes, other code is dependent on "NaN-NaN-NaN".
      // Any falsey value, including the empty string, is acceptable as the expiry date is optional data.
      expiryDate: value
        ? truncateIsoTimestamp(value, "date").orElse("NaN-NaN-NaN")
        : null,
    });
  };

  const expiryDate = fieldOwner.fieldValues.expiryDate;

  return (
    <BatchFormField
      label="Expiry Date"
      value={expiryDate}
      disabled={!fieldOwner.isFieldEditable("expiryDate")}
      renderInput={({ value, id, disabled }) => (
        <DateField
          value={value}
          id={id}
          disabled={disabled}
          onChange={handleChange}
          datatestid="SetExpiryDateButton"
          alert={
            <>
              {expiryDate && new Date(expiryDate) < todaysDate() && (
                <Alert severity="warning">This sample has expired.</Alert>
              )}
            </>
          }
        />
      )}
      noValueLabel={fieldOwner.noValueLabel.expiryDate}
      canChooseWhichToEdit={fieldOwner.canChooseWhichToEdit}
      setDisabled={(d) => {
        fieldOwner.setFieldEditable("expiryDate", d);
      }}
    />
  );
}

export default observer(ExpiryDate);
