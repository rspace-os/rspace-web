import Button from "@mui/material/Button";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import type { HasEditableFields } from "../../../stores/definitions/Editable";
import { CELSIUS, type Temperature } from "../../../stores/definitions/Units";
import BatchFormField from "../../components/Inputs/BatchFormField";
import SpecifiedStorageTemperature from "./SpecifiedStorageTemperature";

function StorageTemperature<
  Fields extends {
    storageTempMin: Temperature | null;
    storageTempMax: Temperature | null;
  },
  FieldOwner extends HasEditableFields<Fields>,
>({
  fieldOwner,
  onErrorStateChange,
}: {
  fieldOwner: FieldOwner;
  onErrorStateChange: (value: boolean) => void;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const disabled = !fieldOwner.isFieldEditable("storageTempMin") && !fieldOwner.isFieldEditable("storageTempMax");

  const storageTempMin: Temperature | null = fieldOwner.fieldValues.storageTempMin;

  const storageTempMax: Temperature | null = fieldOwner.fieldValues.storageTempMax;

  return (
    <>
      {!storageTempMin || !storageTempMax ? (
        <BatchFormField
          label={t("sample.fields.storageTemperature.label")}
          value={void 0}
          asFieldset
          disabled={disabled}
          setDisabled={(checked) => {
            fieldOwner.setFieldEditable("storageTempMin", checked);
            fieldOwner.setFieldEditable("storageTempMax", checked);
          }}
          canChooseWhichToEdit={fieldOwner.canChooseWhichToEdit}
          noValueLabel={fieldOwner.noValueLabel.storageTempMin ?? t("sample.fields.storageTemperature.none")}
          explanation={disabled ? "" : t("sample.fields.storageTemperature.notSpecified")}
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
              {t("sample.fields.storageTemperature.specify")}
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
