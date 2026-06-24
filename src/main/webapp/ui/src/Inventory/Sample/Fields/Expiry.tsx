import Alert from "@mui/material/Alert";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import DateField from "../../../components/Inputs/DateField";
// biome-ignore lint/style/useImportType: initial biome migration
import { type HasEditableFields } from "../../../stores/definitions/Editable";
import { todaysDate, truncateIsoTimestamp } from "../../../stores/definitions/Units";
import BatchFormField from "../../components/Inputs/BatchFormField";

function ExpiryDate<
  Fields extends {
    expiryDate: string | null;
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
  const handleChange = ({ target: { value } }: { target: { value: Date | null } }) => {
    // biome-ignore lint/suspicious/noGlobalIsNan: initial biome migration
    onErrorStateChange(value ? isNaN(value.getTime()) : false);
    fieldOwner.setFieldsDirty({
      // Yes, other code is dependent on "NaN-NaN-NaN".
      // Any falsey value, including the empty string, is acceptable as the expiry date is optional data.
      expiryDate: value ? truncateIsoTimestamp(value, "date").orElse("NaN-NaN-NaN") : null,
    });
  };

  const expiryDate = fieldOwner.fieldValues.expiryDate;

  return (
    <BatchFormField
      label={t("sample.fields.expiryDate.label")}
      value={expiryDate}
      disabled={!fieldOwner.isFieldEditable("expiryDate")}
      renderInput={({ value, id, disabled }) => (
        <DateField
          value={value}
          id={id}
          disabled={disabled}
          onChange={handleChange}
          data-test-id="SetExpiryDateButton"
          alert={
            // biome-ignore lint/complexity/noUselessFragments: initial biome migration
            <>
              {expiryDate && new Date(expiryDate) < todaysDate() && (
                <Alert severity="warning">{t("sample.fields.expiryDate.expiredWarning")}</Alert>
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
