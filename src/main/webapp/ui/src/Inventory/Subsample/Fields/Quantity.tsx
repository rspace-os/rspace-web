import React, { useState, useEffect } from "react";
import { observer } from "mobx-react-lite";
import UnitSelect from "../../../components/Inputs/UnitSelect";
import StringField from "../../../components/Inputs/StringField";
import NumberField from "../../../components/Inputs/NumberField";
import { type HasEditableFields } from "../../../stores/definitions/Editable";
import { type Quantity } from "../../../stores/definitions/HasQuantity";
import {
  getValue,
  getUnitId,
  getLabel,
} from "../../../stores/models/HasQuantity";
import BatchFormField from "../../components/Inputs/BatchFormField";
import Typography from "@mui/material/Typography";
import { type Sample } from "../../../stores/definitions/Sample";
import Link from "@mui/material/Link";
import NavigateContext from "../../../stores/contexts/Navigate";
import { styled } from "@mui/material/styles";
import { textFieldClasses } from "@mui/material/TextField";
import { inputBaseClasses } from "@mui/material/InputBase";

const CustomBatchFormField = styled(BatchFormField)(() => ({
  [`& .${textFieldClasses.root}`]: {
    maxWidth: "264px",
    [`& .${inputBaseClasses.root}`]: {
      paddingRight: 0,
    },
  },
})) as <T>(
  props: React.ComponentProps<typeof BatchFormField<T>>,
) => React.ReactNode;

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

  const errorMessage = () =>
    valid ? null : "Should be a positive number or zero.";

  return (
    <>
      {editable ? (
        <CustomBatchFormField<string | number>
          label="Quantity"
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
              inputProps={{
                min: 0,
                step: 0.001,
              }}
              InputProps={{
                endAdornment: (
                  <UnitSelect
                    categories={[quantityCategory]}
                    value={quantityUnitId}
                    handleChange={handleChangeQuantityUnit}
                  />
                ),
              }}
            />
          )}
        />
      ) : (
        <>
          <CustomBatchFormField
            label="Quantity"
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
                        if (parentSample.globalId)
                          navigate(
                            `/inventory/search?parentGlobalId=${parentSample.globalId}`,
                          );
                      }}
                    >
                      {parentSample.subSamplesCount === 1 ? (
                        `The parent sample only has one ${parentSample.subSampleAlias.alias}.`
                      ) : (
                        <>
                          There{" "}
                          {parentSample.subSamplesCount === 2 ? "is" : "are"}{" "}
                          {parentSample.subSamplesCount - 1} other{" "}
                          {parentSample.subSamplesCount === 2
                            ? parentSample.subSampleAlias.alias
                            : parentSample.subSampleAlias.plural}
                          .
                        </>
                      )}
                    </Link>
                  </Typography>
                )}
              </>
            )}
          />
        </>
      )}
    </>
  );
}

export default observer(QuantityField);
