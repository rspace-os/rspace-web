import React, { useState, useEffect } from "react";
import { observer } from "mobx-react-lite";
import Box from "@mui/material/Box";
import {
  type Temperature,
  CELSIUS,
  KELVIN,
  FAHRENHEIT,
  ABSOLUTE_ZERO,
  LIQUID_NITROGEN,
  type TemperatureScale,
  temperatureFromTo,
  validateTemperature,
} from "../../../stores/definitions/Units";
import Grid from "@mui/material/Grid";
import Select, { SelectChangeEvent } from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import Button from "@mui/material/Button";
import { styled } from "@mui/material/styles";
import NumberField from "../../../components/Inputs/NumberField";
import InputAdornment from "@mui/material/InputAdornment";
import { FormLabel } from "@mui/material";
import BatchFormField from "../../components/Inputs/BatchFormField";

const DECIMAL = 10; // for parseInt/parseFloat

type LabelArgs = {
  min: number;
  max: number;
  unitId: TemperatureScale;
};

const Label = ({ min, max, unitId }: LabelArgs): React.ReactNode => {
  // prettier-ignore
  const render = (x: number) =>
    unitId === CELSIUS ?  `${x}°C`:
    unitId === KELVIN  ?  `${x}K` :
    /* else FAHRENHEIT */ `${x}°F`;

  if (Number.isNaN(min) || Number.isNaN(max)) return <>Invalid values.</>;
  const absoluteZeroInUnitId = temperatureFromTo(
    CELSIUS,
    unitId,
    ABSOLUTE_ZERO
  );
  if (min < absoluteZeroInUnitId || max < absoluteZeroInUnitId)
    return <>One or more values are below absolute zero.</>;
  if (min === max) return render(min);
  return (
    <>
      Between {render(min)} and {render(max)}.
    </>
  );
};

type TemperatureButtonArgs = {
  onClick: () => void;
  wide?: boolean;
  label: string;
};

const TemperatureButton = (props: TemperatureButtonArgs) => (
  <Grid item xs={props.wide ? 12 : 6}>
    <Button
      sx={{ width: "100%" }}
      onClick={props.onClick}
      variant="outlined"
      color="primary"
    >
      {props.label}
    </Button>
  </Grid>
);

type SpecifiedStorageTemperatureArgs = {
  setTemperatures: ({
    storageTempMin,
    storageTempMax,
  }: {
    storageTempMin: Temperature;
    storageTempMax: Temperature;
  }) => void;
  setFieldEditable: (value: boolean) => void;
  storageTempMin: Temperature;
  storageTempMax: Temperature;
  disabled: boolean;
  canChooseWhichToEdit: boolean;
  onErrorStateChange: (value: boolean) => void;
};

function SpecifiedStorageTemperature({
  setTemperatures,
  storageTempMin,
  storageTempMax,
  disabled,
  canChooseWhichToEdit,
  setFieldEditable,
  onErrorStateChange,
}: SpecifiedStorageTemperatureArgs): React.ReactNode {
  if (storageTempMin.unitId !== storageTempMax.unitId)
    throw new Error(
      "Unit IDs of storageTempMin and storageTempMax are not the same."
    );
  const unitId: TemperatureScale = storageTempMin.unitId;
  const minValue: number = storageTempMin.numericValue;
  const maxValue: number = storageTempMax.numericValue;

  const unitSelectId = React.useId();

  const [min, setMin] = useState(minValue);
  const [max, setMax] = useState(maxValue);
  const [minField, setMinField] = useState<string>(`${minValue}`);
  const [maxField, setMaxField] = useState<string>(`${maxValue}`);

  useEffect(() => {
    setMin(storageTempMin.numericValue);
    setMax(storageTempMax.numericValue);
    if (!Number.isNaN(storageTempMin.numericValue))
      setMinField(`${storageTempMin.numericValue}`);
    if (!Number.isNaN(storageTempMax.numericValue))
      setMaxField(`${storageTempMax.numericValue}`);
  }, [storageTempMin, storageTempMax]);

  const handleButtonPressed = (newMin: number, newMax: number): void => {
    setMin(newMin);
    setMax(newMax);
    setMinField(`${newMin}`);
    setMaxField(`${newMax}`);
    setTemperatures({
      storageTempMin: { numericValue: newMin, unitId },
      storageTempMax: { numericValue: newMax, unitId },
    });
  };

  const handleUnitIdChange = (event: SelectChangeEvent<number>) => {
    const newUnitId = event.target.value as TemperatureScale;
    const minValueInNewUnit = temperatureFromTo(unitId, newUnitId, min);
    const maxValueInNewUnit = temperatureFromTo(unitId, newUnitId, max);
    setMin(minValueInNewUnit);
    setMax(maxValueInNewUnit);
    setMinField(`${minValueInNewUnit}`);
    setMaxField(`${maxValueInNewUnit}`);
    setTemperatures({
      storageTempMin: { numericValue: minValueInNewUnit, unitId: newUnitId },
      storageTempMax: { numericValue: maxValueInNewUnit, unitId: newUnitId },
    });
  };

  const handleMinFieldChange = (value: string) => {
    setMinField(value);
    const newMin = parseInt(value, DECIMAL);
    const newMax = !Number.isNaN(newMin) && newMin > max ? newMin : max;
    setMin(newMin);
    setMax(newMax);
    const newMinTemp: Temperature = { numericValue: newMin, unitId };
    const newMaxTemp: Temperature = { numericValue: newMax, unitId };
    setTemperatures({ storageTempMin: newMinTemp, storageTempMax: newMaxTemp });
    onErrorStateChange(
      !validateTemperature(newMinTemp).isError ||
        !validateTemperature(newMaxTemp).isError
    );
  };

  const handleMaxFieldChange = (value: string) => {
    setMaxField(value);
    const newMax = parseInt(value, DECIMAL);
    const newMin = !Number.isNaN(newMax) && newMax < min ? newMax : min;
    setMax(newMax);
    setMin(newMin);
    const newMinTemp: Temperature = { numericValue: newMin, unitId };
    const newMaxTemp: Temperature = { numericValue: newMax, unitId };
    setTemperatures({ storageTempMin: newMinTemp, storageTempMax: newMaxTemp });
    onErrorStateChange(
      !validateTemperature(newMinTemp).isError ||
        !validateTemperature(newMaxTemp).isError
    );
  };

  return (
    <BatchFormField
      label="Storage Temperature"
      value={{ min, max, unitId }}
      canChooseWhichToEdit={canChooseWhichToEdit}
      disabled={disabled}
      setDisabled={(checked) => {
        setFieldEditable(checked);
      }}
      asFieldset
      renderInput={() => (
        // id prop is ignored because there is no HTMLInputElement to attach it to
        <>
          <Label min={min} max={max} unitId={unitId} />
          {!disabled && (
            <Box mt={2}>
              <Grid container direction="column" spacing={1}>
                <Grid item>
                  <FormLabel sx={{ pr: 1 }} htmlFor={unitSelectId}>
                    Unit
                  </FormLabel>
                  <Select
                    variant="standard"
                    value={unitId}
                    disabled={disabled}
                    onChange={handleUnitIdChange}
                    inputProps={{
                      id: unitSelectId,
                    }}
                  >
                    <MenuItem value={CELSIUS}>°C</MenuItem>
                    <MenuItem value={KELVIN}>K</MenuItem>
                    <MenuItem value={FAHRENHEIT}>°F</MenuItem>
                  </Select>
                </Grid>
                {!disabled && (
                  <>
                    <Grid item>
                      <Grid container direction="row" spacing={1}>
                        <Grid item xs={6}>
                          <NumberField
                            value={minField}
                            onChange={(e) => {
                              handleMinFieldChange(e.target.value);
                            }}
                            variant="outlined"
                            size="small"
                            error={
                              validateTemperature({ numericValue: min, unitId })
                                .isError
                            }
                            fullWidth
                            InputProps={{
                              startAdornment: (
                                <InputAdornment position="start">
                                  Min
                                </InputAdornment>
                              ),
                            }}
                          />
                        </Grid>
                        <Grid item xs={6}>
                          <NumberField
                            value={maxField}
                            onChange={(e) => {
                              handleMaxFieldChange(e.target.value);
                            }}
                            variant="outlined"
                            size="small"
                            error={
                              validateTemperature({ numericValue: max, unitId })
                                .isError
                            }
                            fullWidth
                            InputProps={{
                              startAdornment: (
                                <InputAdornment position="start">
                                  Max
                                </InputAdornment>
                              ),
                            }}
                          />
                        </Grid>
                      </Grid>
                    </Grid>
                    <Grid item>
                      <Grid container direction="row" spacing={1}>
                        <TemperatureButton
                          label="Ambient"
                          onClick={() => {
                            handleButtonPressed(
                              temperatureFromTo(CELSIUS, unitId, 15),
                              temperatureFromTo(CELSIUS, unitId, 30)
                            );
                          }}
                        />
                        <TemperatureButton
                          label="Refrigerated"
                          onClick={() => {
                            handleButtonPressed(
                              temperatureFromTo(CELSIUS, unitId, 3),
                              temperatureFromTo(CELSIUS, unitId, 5)
                            );
                          }}
                        />
                        <TemperatureButton
                          label="Frozen"
                          onClick={() => {
                            handleButtonPressed(
                              temperatureFromTo(CELSIUS, unitId, -30),
                              temperatureFromTo(CELSIUS, unitId, -18)
                            );
                          }}
                        />
                        <TemperatureButton
                          label="ULT Frozen"
                          onClick={() => {
                            handleButtonPressed(
                              temperatureFromTo(CELSIUS, unitId, -80),
                              temperatureFromTo(CELSIUS, unitId, -70)
                            );
                          }}
                        />
                        <TemperatureButton
                          wide
                          label="Liquid Nitrogen"
                          onClick={() => {
                            handleButtonPressed(
                              temperatureFromTo(
                                CELSIUS,
                                unitId,
                                LIQUID_NITROGEN
                              ),
                              temperatureFromTo(
                                CELSIUS,
                                unitId,
                                LIQUID_NITROGEN
                              )
                            );
                          }}
                        />
                      </Grid>
                    </Grid>
                  </>
                )}
              </Grid>
            </Box>
          )}
        </>
      )}
    />
  );
}

export default observer(SpecifiedStorageTemperature);
