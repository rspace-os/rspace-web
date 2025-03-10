//@flow

import React, { type Node } from "react";
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
  Fields: {
    expiryDate: ?string,
  },
  FieldOwner: HasEditableFields<Fields>
>({
  fieldOwner,
  onErrorStateChange,
}: {|
  fieldOwner: FieldOwner,
  onErrorStateChange: (boolean) => void,
|}): Node {
  const handleChange = ({
    target: { value },
  }: {
    target: { value: ?Date, ... },
    ...
  }) => {
    onErrorStateChange(isNaN(value));
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

export default (observer(ExpiryDate): typeof ExpiryDate);
