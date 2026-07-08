import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import Paper from "@mui/material/Paper";
import { observer } from "mobx-react-lite";
import React, { useEffect } from "react";
import { useTranslation } from "react-i18next";
import FormControl from "../../../components/Inputs/FormControl";
import InputWrapper from "../../../components/Inputs/InputWrapper";
import RadioField, { type RadioOption } from "../../../components/Inputs/RadioField";
import TemplateModel from "../../../stores/models/TemplateModel";
import type { UnitCategory } from "../../../stores/stores/UnitStore";
import useStores from "../../../stores/use-stores";
import type { UseState } from "../../../util/types";

function FormBoxWithLabel({ children, label }: { children: React.ReactNode; label: string }): React.ReactNode {
  return (
    <Paper variant="outlined">
      <Box sx={{ m: 2 }}>
        <FormControl
          label={label}
          slotProps={{
            label: {
              sx: (theme) => ({
                top: -27,
                left: `-${theme.spacing(1)}`,
                padding: theme.spacing(0, 0.5),
                position: "absolute",
                fontSize: "0.9em",
                backgroundColor: "white",
                fontWeight: "400 !important",
              }),
            },
          }}
        >
          {children}
        </FormControl>
      </Box>
    </Paper>
  );
}

function QuantityUnits(): React.ReactNode {
  const { t } = useTranslation("inventory");
  const {
    searchStore: { activeResult },
    unitStore,
  } = useStores();
  if (!activeResult || !(activeResult instanceof TemplateModel)) throw new Error("ActiveResult must be a Template");
  const initialCategory = unitStore.getUnit(activeResult.defaultUnitId)?.category;
  if (initialCategory !== "mass" && initialCategory !== "volume" && initialCategory !== "dimensionless")
    throw new Error("Unknown category");
  const [category, setCategory]: UseState<"mass" | "volume" | "dimensionless"> = React.useState(initialCategory);

  useEffect(() => {
    const unit = unitStore.getUnit(activeResult.defaultUnitId);
    if (unit?.category !== "mass" && unit?.category !== "volume" && unit?.category !== "dimensionless")
      throw new Error("Unknown category");
    if (unit) setCategory(unit.category);
  }, [activeResult.defaultUnitId, unitStore]);

  const quantityCategories: Array<RadioOption<"mass" | "volume" | "dimensionless">> = [
    {
      value: "mass",
      label: t("fields.templateFields.quantityUnits.mass"),
    },
    {
      value: "volume",
      label: t("fields.templateFields.quantityUnits.volume"),
    },
    {
      value: "dimensionless",
      label: t("fields.templateFields.quantityUnits.unitless"),
    },
  ];

  const handleChange = ({ target: { value } }: { target: { value: string | null; name: string } }) => {
    if (value) {
      activeResult.setAttributesDirty({
        defaultUnitId: parseInt(value, 10),
      });
    }
  };

  const handleCategoryChange = ({ target: { value } }: { target: { value: UnitCategory | null; name: string } }) => {
    if (value === "mass" || value === "volume" || value === "dimensionless") {
      setCategory(value);
      activeResult.setAttributesDirty({
        defaultUnitId: unitStore.unitsOfCategory([value])[0].id,
      });
    }
  };

  const editable = activeResult.isFieldEditable("defaultUnitId");
  return (
    <InputWrapper label={t("fields.templateFields.quantityUnits.label")}>
      <Box sx={{ mt: 2 }}>
        <Grid container spacing={2}>
          <Grid
            size={{
              xs: 12,
              sm: 5,
            }}
          >
            <FormBoxWithLabel label={t("fields.templateFields.quantityUnits.unitType")}>
              <RadioField
                value={category}
                name="quantityUnitsCategory"
                disabled={!editable}
                onChange={handleCategoryChange}
                options={quantityCategories}
              />
            </FormBoxWithLabel>
          </Grid>
          <Grid
            size={{
              xs: 12,
              sm: 7,
            }}
          >
            <FormBoxWithLabel label={t("fields.templateFields.quantityUnits.defaultScale")}>
              <RadioField
                value={`${activeResult.defaultUnitId}`}
                name="defaultscale"
                disabled={!editable}
                onChange={handleChange}
                options={unitStore.unitsOfCategory([category]).map((x) => ({ label: x.label, value: `${x.id}` }))}
              />
            </FormBoxWithLabel>
          </Grid>
        </Grid>
      </Box>
    </InputWrapper>
  );
}

export default observer(QuantityUnits);
