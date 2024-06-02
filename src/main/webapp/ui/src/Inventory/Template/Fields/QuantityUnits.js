//@flow

import useStores from "../../../stores/use-stores";
import InputWrapper from "../../../components/Inputs/InputWrapper";
import FormControl from "../../../components/Inputs/FormControl";
import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import Paper from "@mui/material/Paper";
import React, { useEffect, type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import RadioField, {
  type RadioOption,
} from "../../../components/Inputs/RadioField";
import { withStyles } from "Styles";
import TemplateModel from "../../../stores/models/TemplateModel";
import { type UseState } from "../../../util/types";

const FormBoxWithLabel = withStyles<
  {| children: Node, label: string |},
  { formLabel?: string, formControl?: string }
>((theme) => ({
  formLabel: {
    top: -27,
    left: -1 * theme.spacing(1),
    padding: theme.spacing(0, 0.5),
    position: "absolute",
    fontSize: "0.9em",
    backgroundColor: "white",
    fontWeight: "400 !important",
  },
}))(({ children, label, classes }) => (
  <Paper variant="outlined">
    <Box m={2}>
      <FormControl label={label} classes={classes}>
        {children}
      </FormControl>
    </Box>
  </Paper>
));

function QuantityUnits(): Node {
  const {
    searchStore: { activeResult },
    unitStore,
  } = useStores();
  if (!activeResult || !(activeResult instanceof TemplateModel))
    throw new Error("ActiveResult must be a Template");
  const initialCategory = unitStore.getUnit(
    activeResult.defaultUnitId
  )?.category;
  if (
    initialCategory !== "mass" &&
    initialCategory !== "volume" &&
    initialCategory !== "dimensionless"
  )
    throw new Error("Unknown category");
  const [category, setCategory]: UseState<"mass" | "volume" | "dimensionless"> =
    React.useState(initialCategory);

  useEffect(() => {
    const unit = unitStore.getUnit(activeResult.defaultUnitId);
    if (
      unit?.category !== "mass" &&
      unit?.category !== "volume" &&
      unit?.category !== "dimensionless"
    )
      throw new Error("Unknown category");
    if (unit) setCategory(unit.category);
  }, [activeResult.defaultUnitId]);

  const quantityCategories: Array<
    RadioOption<"mass" | "volume" | "dimensionless">
  > = [
    {
      value: "mass",
      label: "Mass",
    },
    {
      value: "volume",
      label: "Volume",
    },
    {
      value: "dimensionless",
      label: "Unitless",
    },
  ];

  const handleChange = ({
    target: { value },
  }: {
    target: { value: ?string, name: string },
  }) => {
    if (value) {
      activeResult.setAttributesDirty({
        defaultUnitId: parseInt(value),
      });
    }
  };

  const handleCategoryChange = ({
    target: { value },
  }: {
    target: { value: ?("mass" | "volume" | "dimensionless"), name: string },
  }) => {
    if (value) {
      setCategory(value);
      activeResult.setAttributesDirty({
        defaultUnitId: unitStore.unitsOfCategory([value])[0].id,
      });
    }
  };

  const editable = activeResult.isFieldEditable("defaultUnitId");
  return (
    <InputWrapper label="Quantity Units">
      <Box mt={2}>
        <Grid container spacing={2}>
          <Grid item xs={12} sm={5}>
            <FormBoxWithLabel label="Unit Type">
              <RadioField
                value={category}
                name="quantityUnitsCategory"
                disabled={!editable}
                onChange={handleCategoryChange}
                options={quantityCategories}
              />
            </FormBoxWithLabel>
          </Grid>
          <Grid item xs={12} sm={7}>
            <FormBoxWithLabel label="Default Scale">
              <RadioField
                value={`${activeResult.defaultUnitId}`}
                name="defaultscale"
                disabled={!editable}
                onChange={handleChange}
                options={unitStore
                  .unitsOfCategory([category])
                  .map((x) => ({ label: x.label, value: `${x.id}` }))}
              />
            </FormBoxWithLabel>
          </Grid>
        </Grid>
      </Box>
    </InputWrapper>
  );
}

export default (observer(QuantityUnits): ComponentType<{||}>);
