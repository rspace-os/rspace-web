import React from "react";
import { observer } from "mobx-react-lite";
import { type Temperature, CELSIUS } from "../../../stores/definitions/Units";
import { type HasEditableFields } from "../../../stores/definitions/Editable";
import Button from "@mui/material/Button";
import SpecifiedStorageTemperature from "./SpecifiedStorageTemperature";
import BatchFormField from "../../components/Inputs/BatchFormField";

function StorageTemperature<
  Fields extends {
    storageTempMin: Temperature | null;
    storageTempMax: Temperature | null;
  },
  FieldOwner extends HasEditableFields<Fields>
>({
  fieldOwner,
  onErrorStateChange,
}: {
  fieldOwner: FieldOwner;
  onErrorStateChange: (value: boolean) => void;
}): React.ReactNode {
  const disabled =
    !fieldOwner.isFieldEditable("storageTempMin") &&
    !fieldOwner.isFieldEditable("storageTempMax");

  const storageTempMin: Temperature | null =
    fieldOwner.fieldValues.storageTempMin;

  const storageTempMax: Temperature | null =
    fieldOwner.fieldValues.storageTempMax;

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

export default observer(StorageTemperature);
