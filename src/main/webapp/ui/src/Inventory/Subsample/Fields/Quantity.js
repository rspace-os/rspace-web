//@flow

import React, { type Node, useState, useEffect } from "react";
import { observer } from "mobx-react-lite";
import UnitSelect from "../../../components/Inputs/UnitSelect";
import StringField from "../../../components/Inputs/StringField";
import NumberField from "../../../components/Inputs/NumberField";
import { type HasEditableFields } from "../../../stores/definitions/Editable";
import {
  type Quantity,
  getValue,
  getUnitId,
  getLabel,
} from "../../../stores/models/RecordWithQuantity";
import BatchFormField from "../../components/Inputs/BatchFormField";
import Typography from "@mui/material/Typography";
import type { Sample } from "../../../stores/definitions/Sample";
import Link from "@mui/material/Link";
import NavigateContext from "../../../stores/contexts/Navigate";

function QuantityField<
  Fields: {
    quantity: ?Quantity,
  },
  FieldOwner: HasEditableFields<Fields>
>({
  fieldOwner,
  quantityCategory,
  onErrorStateChange,
  parentSample,
}: {|
  fieldOwner: FieldOwner,
  quantityCategory: string,
  onErrorStateChange: (boolean) => void,

  /**
   * If provided, a link is rendered beneath the field that navigates to the
   * parentGlobalId search for the parent sample. This is so that a user may
   * quickly find the rest of the quantity of the whole sample.
   */
  parentSample?: Sample,
|}): Node {
  const { useNavigate } = React.useContext(NavigateContext);
  const navigate = useNavigate();
  const quantityValue = getValue(fieldOwner.fieldValues.quantity);
  const quantityUnitId = getUnitId(fieldOwner.fieldValues.quantity);
  const quantityLabel = getLabel(fieldOwner.fieldValues.quantity);
  const editable = fieldOwner.isFieldEditable("quantity");

  const [valid, setValid] = useState(true);
  const [amount, setAmount] = useState(quantityValue);

  useEffect(() => {
    setValid(true);
    setAmount(quantityValue);
  }, [editable]);

  const handleChangeQuantity = (e: {
    target: { value: string, checkValidity: () => boolean, ... },
    ...
  }) => {
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

  const handleChangeQuantityUnit = ({
    target,
  }: {
    target: { value: number, ... },
    ...
  }) => {
    const quantity = quantityValue;
    fieldOwner.setFieldsDirty({
      quantity: { unitId: parseInt(target.value, 10), numericValue: quantity },
    });
  };

  const errorMessage = () =>
    valid ? null : "Should be a positive number or zero.";

  return (
    <>
      {editable ? (
        <BatchFormField
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
                startAdornment: (
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
          <BatchFormField
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
                            `/inventory/search?parentGlobalId=${parentSample.globalId}`
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

export default (observer(QuantityField): typeof QuantityField);
