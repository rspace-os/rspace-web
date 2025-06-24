import React from "react";
import { observer } from "mobx-react-lite";
import {
  Material,
  type ListOfMaterials,
} from "../../stores/models/MaterialsModel";
import NumberField from "../../components/Inputs/NumberField";
import UnitSelect from "../../components/Inputs/UnitSelect";
import { hasQuantity } from "../../stores/models/HasQuantity";

type UsedQuantityFieldArgs = {
  material: Material;
  list: ListOfMaterials;
};

function UsedQuantityField({
  material,
  list,
}: UsedQuantityFieldArgs): React.ReactNode {
  const globalId = material.invRec.globalId;
  if (!globalId) throw new Error("Item Global ID must be known");

  const mixedSelectedCategories = list.mixedSelectedCategories;
  const notValidAmount = material.editing && !list.validAdditionalAmount;
  const enoughLeft = material.enoughLeft;

  const errorMessage = mixedSelectedCategories
    ? "Unit categories must match"
    : notValidAmount
    ? "Enter a positive or zero"
    : !enoughLeft
    ? "Not enough left, reduce or unselect"
    : null;

  const getNumericValue = () => {
    if (material.selected && mixedSelectedCategories) return "0";
    if (isNaN(Number(list.additionalQuantity?.numericValue))) return "";
    if (material.selected && list.additionalQuantity)
      return list.additionalQuantity.numericValue;
    return "0";
  };

  const getUnitId = () => {
    if (material.selected && list.additionalQuantity)
      return list.additionalQuantity.unitId;
    if (
      material.usedQuantity === null ||
      typeof material.usedQuantity === "undefined"
    )
      throw new Error("Rendering record within quantity");
    return material.usedQuantity.unitId;
  };

  const selectedMaterials = list.selectedMaterials;

  const onChangeValue = (additionalValue: number) => {
    if (material.usedQuantity) {
      const unitId =
        list.additionalQuantity?.unitId ?? material.usedQuantity.unitId;
      list.setAdditionalQuantity({
        numericValue: additionalValue,
        unitId,
      });
      selectedMaterials.forEach((m) => {
        m.setUsedQuantity(additionalValue, unitId);
        m.updateQuantityEstimate();
      });
    }
  };

  const onChangeUnitId = (newUnitId: number) => {
    const value = list.additionalQuantity?.numericValue ?? 0;
    if (material.usedQuantity)
      list.setAdditionalQuantity({
        numericValue: value,
        unitId: newUnitId,
      });
    selectedMaterials.forEach((m) => {
      m.setUsedQuantity(value, newUnitId);
      m.updateQuantityEstimate();
    });
  };

  return (
    <NumberField
      datatestid={`material-additional-quantity-${globalId}`}
      disabled={!material.selected || mixedSelectedCategories}
      value={getNumericValue()}
      // eslint-disable-next-line jsx-a11y/no-autofocus
      autoFocus
      onChange={({ target }) => {
        onChangeValue(parseFloat(target.value));
      }}
      size="small"
      error={mixedSelectedCategories || notValidAmount || !enoughLeft}
      helperText={material.selected ? errorMessage : null}
      noValueLabel={"—"}
      inputProps={{
        step: "any",
        min: 0,
      }}
      InputProps={{
        endAdornment: (
          <UnitSelect
            disabled={!material.selected || mixedSelectedCategories}
            categories={hasQuantity(material.invRec)
              .map((r) => [r.quantityCategory])
              .orElse([])}
            value={getUnitId()}
            handleChange={({ target }) => {
              onChangeUnitId(parseInt(String(target.value), 10));
            }}
          />
        ),
      }}
    />
  );
}

export default observer(UsedQuantityField);
