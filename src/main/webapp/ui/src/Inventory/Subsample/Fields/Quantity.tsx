import Box from "@mui/material/Box";
import { inputBaseClasses } from "@mui/material/InputBase";
import Link from "@mui/material/Link";
import { textFieldClasses } from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import NumberField from "../../../components/Inputs/NumberField";
import StringField from "../../../components/Inputs/StringField";
import UnitSelect from "../../../components/Inputs/UnitSelect";
import NavigateContext from "../../../stores/contexts/Navigate";
import type { HasEditableFields } from "../../../stores/definitions/Editable";
import type { Quantity } from "../../../stores/definitions/HasQuantity";
import type { Sample } from "../../../stores/definitions/Sample";
import { getLabel, getUnitId, getValue } from "../../../stores/models/HasQuantity";
import BatchFormField from "../../components/Inputs/BatchFormField";

function CustomBatchFormField<T>(props: React.ComponentProps<typeof BatchFormField<T>>): React.ReactNode {
  return (
    <Box
      sx={{
        [`& .${textFieldClasses.root}`]: {
          maxWidth: "264px",
          [`& .${inputBaseClasses.root}`]: {
            paddingRight: 0,
          },
        },
      }}
    >
      <BatchFormField {...props} />
    </Box>
  );
}

function QuantityField<
  Fields extends {
    quantity: Quantity | null;
  },
  FieldOwner extends HasEditableFields<Fields>,
>({
  fieldOwner,
  quantityCategory,
  onErrorStateChange,
  parentSample,
}: {
  fieldOwner: FieldOwner;
  quantityCategory: string;
  onErrorStateChange: (hasError: boolean) => void;
  /**
   * If provided, a link is rendered beneath the field that navigates to the
   * parentGlobalId search for the parent sample. This is so that a user may
   * quickly find the rest of the quantity of the whole sample.
   */
  parentSample?: Sample;
}): React.ReactNode {
  const { useNavigate } = React.useContext(NavigateContext);
  const { t } = useTranslation("inventory");
  const navigate = useNavigate();
  const quantityValue = getValue(fieldOwner.fieldValues.quantity);
  const quantityUnitId = getUnitId(fieldOwner.fieldValues.quantity);
  const quantityLabel = getLabel(fieldOwner.fieldValues.quantity);
  const editable = fieldOwner.isFieldEditable("quantity");

  const [valid, setValid] = useState(true);
  const [amount, setAmount] = useState<string | number>(quantityValue);

  useEffect(() => {
    setValid(true);
    setAmount(quantityValue);
  }, [editable]);

  const handleChangeQuantity = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target instanceof HTMLInputElement) {
      setAmount(e.target.value);
      const unitId = quantityUnitId;
      if (!e.target.checkValidity() || e.target.value === "") {
        setValid(false);
        fieldOwner.setFieldsDirty({
          quantity: {
            numericValue: "",
            unitId,
          },
        });
        onErrorStateChange(true);
      } else {
        setValid(true);
        fieldOwner.setFieldsDirty({
          quantity: {
            numericValue: parseFloat(e.target.value),
            unitId,
          },
        });
        onErrorStateChange(false);
      }
    }
  };

  const handleChangeQuantityUnit = (e: { target: { value: unknown } }) => {
    const quantity = quantityValue;
    fieldOwner.setFieldsDirty({
      quantity: {
        unitId: parseInt(e.target.value as string, 10),
        numericValue: quantity,
      },
    });
  };

  const errorMessage = () => (valid ? null : t("fields.quantity.validation.positiveOrZero"));

  return (
    <>
      {editable ? (
        <CustomBatchFormField<string | number>
          label={t("fields.quantity.label")}
          value={amount}
          error={!valid}
          helperText={errorMessage()}
          disabled={false}
          setDisabled={(checked) => {
            fieldOwner.setFieldEditable("quantity", checked);
          }}
          canChooseWhichToEdit={fieldOwner.canChooseWhichToEdit}
          renderInput={({ value, error, id }) => (
            <NumberField
              value={value}
              id={id}
              onChange={handleChangeQuantity}
              size="small"
              variant="outlined"
              error={error}
              slotProps={{
                htmlInput: {
                  min: 0,
                  step: 0.001,
                },
                input: {
                  endAdornment: (
                    <UnitSelect
                      categories={[quantityCategory]}
                      value={quantityUnitId}
                      handleChange={handleChangeQuantityUnit}
                    />
                  ),
                },
              }}
            />
          )}
        />
      ) : (
        <CustomBatchFormField
          label={t("fields.quantity.label")}
          value={quantityLabel}
          disabled
          setDisabled={(checked) => {
            fieldOwner.setFieldEditable("quantity", checked);
          }}
          canChooseWhichToEdit={fieldOwner.canChooseWhichToEdit}
          noValueLabel={fieldOwner.noValueLabel.quantity}
          renderInput={({ value, disabled }) => (
            <>
              <StringField disabled={disabled} value={value} />
              {parentSample?.globalId && (
                <Typography variant="caption">
                  <Link
                    href={`/inventory/search?parentGlobalId=${parentSample.globalId}`}
                    onClick={(e) => {
                      e.preventDefault();
                      if (parentSample.globalId) navigate(`/inventory/search?parentGlobalId=${parentSample.globalId}`);
                    }}
                  >
                    {parentSample.subSamplesCount === 1
                      ? t("fields.quantity.parentSampleOnly", { alias: parentSample.subSampleAlias.alias })
                      : t("fields.quantity.parentSampleOthers", {
                          count: parentSample.subSamplesCount - 1,
                          alias: parentSample.subSampleAlias.alias,
                          plural: parentSample.subSampleAlias.plural,
                        })}
                  </Link>
                </Typography>
              )}
            </>
          )}
        />
      )}
    </>
  );
}

export default observer(QuantityField);
