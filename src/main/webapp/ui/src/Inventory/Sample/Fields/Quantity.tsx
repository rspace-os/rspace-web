import Box from "@mui/material/Box";
import InputAdornment from "@mui/material/InputAdornment";
import { inputBaseClasses } from "@mui/material/InputBase";
import Link from "@mui/material/Link";
import type { SelectChangeEvent } from "@mui/material/Select";
import { textFieldClasses } from "@mui/material/TextField";
import { observer } from "mobx-react-lite";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import NumberField from "../../../components/Inputs/NumberField";
import StringField from "../../../components/Inputs/StringField";
import UnitSelect from "../../../components/Inputs/UnitSelect";
import NavigateContext from "../../../stores/contexts/Navigate";
import type SampleModel from "../../../stores/models/SampleModel";
import useStores from "../../../stores/use-stores";
import { Optional } from "../../../util/optional";
import { isPlainLeftClick } from "../../../util/Util";
import FormField from "../../components/Inputs/FormField";

type QuantityArgs = {
  onErrorStateChange: (value: boolean) => void;
  sample: SampleModel;
};

function Quantity({ onErrorStateChange, sample }: QuantityArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
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
        unitId: e.target.value,
        numericValue: quantity,
      },
    });
  };

  const totalSummaryLabel = () => {
    // error explanation is shown instead when !valid
    if (!valid) return Optional.empty<string>();

    const count = sample.newSampleSubSamplesCount;
    if (count === null || typeof count === "undefined") return Optional.empty<string>();
    if (count === 1) return Optional.empty<string>();

    if (unitStore.units.length) {
      const totalQuantity = sample.quantityValue * count;
      return Optional.present(
        t("sample.fields.quantity.total", {
          quantity: totalQuantity.toFixed(totalQuantity % 1 === 0 ? 0 : 2),
          unit: sample.quantityUnitLabel,
        }),
      );
    }

    return Optional.empty<string>();
  };

  const errorMessage = () => {
    if (valid) {
      return null;
    }
    return t("sample.fields.quantity.validation");
  };

  const alias = sample.subSampleAlias;

  const categories = () => {
    if (sample.template?.defaultUnitId) {
      const defaultUnitId = sample.template.defaultUnitId;
      const unit = unitStore.getUnit(defaultUnitId);
      if (!unit) throw new Error(`Could not find unit with id: ${defaultUnitId}`);
      return [unit.category];
    }
    return ["dimensionless", "volume", "mass"];
  };

  const totalQuantityString = t("sample.fields.quantity.totalLabel", { quantity: sample.quantityLabel });

  return (
    <>
      {!sample.id && sample.quantity && (
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
          <FormField
            label={
              (sample.newSampleSubSamplesCount ?? 2) > 1
                ? t("sample.fields.quantity.perAlias", { alias: alias.alias })
                : t("sample.fields.quantity.label")
            }
            explanation={t("sample.fields.quantity.templateUnitsExplanation")}
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
                slotProps={{
                  htmlInput: {
                    min: 0,
                    step: 0.001,
                  },
                  input: {
                    endAdornment: (
                      <>
                        <UnitSelect
                          categories={categories()}
                          value={sample.quantityUnitId}
                          handleChange={handleChangeQuantityUnit}
                        />
                        {(sample.newSampleSubSamplesCount ?? 2) > 1 && (
                          <InputAdornment position="start">
                            {t("fields.quantity.perAlias", {
                              alias: sample.template ? alias.alias : t("recordTypes.subsample.lower"),
                            })}
                          </InputAdornment>
                        )}
                      </>
                    ),
                  },
                }}
              />
            )}
          />
        </Box>
      )}
      {sample.id !== null && typeof sample.id !== "undefined" && Boolean(sample.quantity) && (
        <FormField
          label={t("fields.quantity.totalLabel")}
          value={totalQuantityString}
          disabled
          explanation={
            sample.subSamplesCount === 1 ? (
              t("fields.quantity.totalSingle", { alias: sample.subSampleAlias.alias })
            ) : (
              <TransRichText
                i18nKey="inventory:fields.quantity.totalCalculated"
                values={{ count: sample.subSamplesCount, plural: sample.subSampleAlias.plural }}
                components={{
                  internalLink: (
                    <Link
                      href={
                        typeof sample.globalId === "string"
                          ? `/inventory/search?parentGlobalId=${sample.globalId}`
                          : "#"
                      }
                      onClick={(e) => {
                        if (!isPlainLeftClick(e)) return;
                        e.preventDefault();
                        if (sample.globalId) navigate(`/inventory/search?parentGlobalId=${sample.globalId}`);
                      }}
                    />
                  ),
                }}
              />
            )
          }
          renderInput={() => <StringField disabled={true} value={totalQuantityString} />}
        />
      )}
    </>
  );
}

export default observer(Quantity);
