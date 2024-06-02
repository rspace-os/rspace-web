//@flow

import React, { type Node } from "react";
import { observer } from "mobx-react-lite";
import { type Temperature } from "../../../stores/definitions/Sample";
import { type HasEditableFields } from "../../../stores/definitions/Editable";
import Button from "@mui/material/Button";
import SpecifiedStorageTemperature from "./SpecifiedStorageTemperature";
import { CELSIUS } from "../../../stores/definitions/Units";
import BatchFormField from "../../components/Inputs/BatchFormField";

function StorageTemperature<
  Fields: {
    storageTempMin: ?Temperature,
    storageTempMax: ?Temperature,
    ...
  },
  FieldOwner: HasEditableFields<Fields>
>({
  fieldOwner,
  onErrorStateChange,
}: {|
  fieldOwner: FieldOwner,
  onErrorStateChange: (boolean) => void,
|}): Node {
  const disabled =
    !fieldOwner.isFieldEditable("storageTempMin") &&
    !fieldOwner.isFieldEditable("storageTempMax");

  const storageTempMin: ?Temperature = fieldOwner.fieldValues.storageTempMin;
  const storageTempMax: ?Temperature = fieldOwner.fieldValues.storageTempMax;

  return (
    <>
      {!storageTempMin || !storageTempMax ? (
        <BatchFormField
          label="Storage Temperature"
          value={void 0}
          asFieldset
          disabled={disabled}
          setDisabled={(checked) => {
            fieldOwner.setFieldEditable("storageTempMin", checked);
            fieldOwner.setFieldEditable("storageTempMax", checked);
          }}
          canChooseWhichToEdit={fieldOwner.canChooseWhichToEdit}
          noValueLabel={fieldOwner.noValueLabel.storageTempMin ?? "None"}
          explanation={
            disabled
              ? ""
              : "The storage temperature for this item is currently not specified."
          }
          renderInput={() => (
            // id prop is ignored because there is no HTMLInputElement to attach it to
            <Button
              color="primary"
              variant="outlined"
              onClick={() => {
                fieldOwner.setFieldsDirty({
                  storageTempMin: { numericValue: 15, unitId: CELSIUS },
                  storageTempMax: { numericValue: 30, unitId: CELSIUS },
                });
              }}
            >
              Specify
            </Button>
          )}
        />
      ) : (
        <SpecifiedStorageTemperature
          setTemperatures={(temperatures) => {
            fieldOwner.setFieldsDirty(temperatures);
          }}
          storageTempMin={storageTempMin}
          storageTempMax={storageTempMax}
          disabled={disabled}
          canChooseWhichToEdit={fieldOwner.canChooseWhichToEdit}
          setFieldEditable={(checked) => {
            fieldOwner.setFieldEditable("storageTempMin", checked);
            fieldOwner.setFieldEditable("storageTempMax", checked);
          }}
          onErrorStateChange={onErrorStateChange}
        />
      )}
    </>
  );
}

export default (observer(StorageTemperature): typeof StorageTemperature);
