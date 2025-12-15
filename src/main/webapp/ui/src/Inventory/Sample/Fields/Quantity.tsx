import React, { useState } from "react";
import { observer } from "mobx-react-lite";
import useStores from "../../../stores/use-stores";
import InputAdornment from "@mui/material/InputAdornment";
import UnitSelect from "../../../components/Inputs/UnitSelect";
import StringField from "../../../components/Inputs/StringField";
import NumberField from "../../../components/Inputs/NumberField";
import { SelectChangeEvent } from "@mui/material/Select";
import SampleModel from "../../../stores/models/SampleModel";
import FormField from "../../components/Inputs/FormField";
import NavigateContext from "../../../stores/contexts/Navigate";
import Link from "@mui/material/Link";
import { Optional } from "../../../util/optional";
import { styled } from "@mui/material/styles";
import { textFieldClasses } from "@mui/material/TextField";
import { inputBaseClasses } from "@mui/material";

const CustomFormField = styled(FormField<string | number>)(() => ({
  [`& .${textFieldClasses.root}`]: {
    maxWidth: "264px",
    [`& .${inputBaseClasses.root}`]: {
      paddingRight: 0,
    },
  },
}));

type QuantityArgs = {
  onErrorStateChange: (value: boolean) => void;
  sample: SampleModel;
};

function Quantity({
  onErrorStateChange,
  sample,
}: QuantityArgs): React.ReactNode {
  const { useNavigate } = React.useContext(NavigateContext);
  const navigate = useNavigate();
  const { unitStore } = useStores();
  const [valid, setValid] = useState(true);
  const [amount, setAmount] = useState<string | number>(sample.quantityValue);

  const handleChangeUnitAmount = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target instanceof HTMLInputElement) {
      setAmount(e.target.value);
      const unit = sample.quantityUnitId;
      if (!e.target.checkValidity() || e.target.value === "") {
        setValid(false);
        sample.setAttributesDirty({
          quantity: {
            numericValue: "",
            unitId: unit,
          },
        });
        onErrorStateChange(true);
      } else {
        setValid(true);
        sample.setAttributesDirty({
          quantity: {
            numericValue: parseFloat(e.target.value),
            unitId: unit,
          },
        });
        onErrorStateChange(false);
      }
    }
  };

  const handleChangeQuantityUnit = (e: SelectChangeEvent<number>) => {
    const quantity = sample.quantityValue;
    sample.setAttributesDirty({
      quantity: {
        unitId: e.target.value as number,
        numericValue: quantity,
      },
    });
  };

  const totalSummaryLabel = () => {
    // error explanation is shown instead when !valid
    if (!valid) return Optional.empty<string>();

    const count = sample.newSampleSubSamplesCount;
    if (count === null || typeof count === "undefined")
      return Optional.empty<string>();
    if (count === 1) return Optional.empty<string>();

    if (unitStore.units.length) {
      const totalQuantity = sample.quantityValue * count;
      return Optional.present(
        `${totalQuantity.toFixed(totalQuantity % 1 === 0 ? 0 : 2)} ${
          sample.quantityUnitLabel
        } in total`,
      );
    }

    return Optional.empty<string>();
  };

  const errorMessage = () => {
    if (valid) {
      return null;
    }
    return "Should be a positive value, of no more than 3 decimal places, or zero.";
  };

  const alias = sample.subSampleAlias;

  const categories = () => {
    if (sample.template?.defaultUnitId) {
      const defaultUnitId = sample.template.defaultUnitId;
      const unit = unitStore.getUnit(defaultUnitId);
      if (!unit)
        throw new Error(`Could not find unit with id: ${defaultUnitId}`);
      return [unit.category];
    }
    return ["dimensionless", "volume", "mass"];
  };

  const totalQuantityString = `${sample.quantityLabel} in total`;

  return (
    <>
      {!sample.id && sample.quantity && (
        <CustomFormField
          label={`Quantity${
            (sample.newSampleSubSamplesCount ?? 2) > 1
              ? " per " + alias.alias
              : ""
          }`}
          explanation="Quantity units can also be changed by editing templates."
          value={amount}
          error={!valid}
          helperText={errorMessage()}
          renderInput={({ ...props }) => (
            <NumberField
              {...props}
              onChange={handleChangeUnitAmount}
              helperText={totalSummaryLabel().orElse("")}
              size="small"
              variant="outlined"
              inputProps={{
                min: 0,
                step: 0.001,
              }}
              InputProps={{
                endAdornment: (
                  <>
                    <UnitSelect
                      categories={categories()}
                      value={sample.quantityUnitId}
                      handleChange={handleChangeQuantityUnit}
                    />
                    {(sample.newSampleSubSamplesCount ?? 2) > 1 && (
                      <InputAdornment position="start">
                        {"per "}
                        {sample.template ? alias.alias : "subsample"}
                      </InputAdornment>
                    )}
                  </>
                ),
              }}
            />
          )}
        />
      )}
      {sample.id !== null &&
        typeof sample.id !== "undefined" &&
        Boolean(sample.quantity) && (
          <FormField
            label="Total Quantity"
            value={totalQuantityString}
            disabled
            explanation={
              sample.subSamplesCount === 1 ? (
                `There is only one ${sample.subSampleAlias.alias}.`
              ) : (
                <>
                  Total is calculated from the quantites of{" "}
                  <Link
                    href={
                      typeof sample.globalId === "string"
                        ? `/inventory/search?parentGlobalId=${sample.globalId}`
                        : "#"
                    }
                    onClick={(e) => {
                      e.preventDefault();
                      if (sample.globalId)
                        navigate(
                          `/inventory/search?parentGlobalId=${sample.globalId}`,
                        );
                    }}
                  >
                    all {sample.subSamplesCount} {sample.subSampleAlias.plural}
                  </Link>
                  , which can be changed by editing the{" "}
                  {sample.subSampleAlias.plural} individually.
                </>
              )
            }
            renderInput={() => (
              <StringField disabled={true} value={totalQuantityString} />
            )}
          />
        )}
    </>
  );
}

export default observer(Quantity);
