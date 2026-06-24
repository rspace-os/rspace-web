// biome-ignore lint/style/noRestrictedImports: initial biome migration
import { FormLabel } from "@mui/material";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import InputAdornment from "@mui/material/InputAdornment";
import MenuItem from "@mui/material/MenuItem";
import Select, { type SelectChangeEvent } from "@mui/material/Select";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import NumberField from "../../../components/Inputs/NumberField";
import {
  ABSOLUTE_ZERO,
  CELSIUS,
  FAHRENHEIT,
  KELVIN,
  LIQUID_NITROGEN,
  type Temperature,
  type TemperatureScale,
  temperatureFromTo,
  validateTemperature,
} from "../../../stores/definitions/Units";
import BatchFormField from "../../components/Inputs/BatchFormField";

const DECIMAL = 10; // for parseInt/parseFloat

type LabelArgs = {
  min: number;
  max: number;
  unitId: TemperatureScale;
};

const Label = ({ min, max, unitId }: LabelArgs): React.ReactNode => {
  const { t } = useTranslation("inventory");
  // prettier-ignore
  const render = (x: number) =>
    unitId === CELSIUS ? `${x}°C` : unitId === KELVIN ? `${x}K` : /* else FAHRENHEIT */ `${x}°F`;

  if (Number.isNaN(min) || Number.isNaN(max)) return <>{t("sample.fields.storageTemperature.invalidValues")}</>;
  const absoluteZeroInUnitId = temperatureFromTo(CELSIUS, unitId, ABSOLUTE_ZERO);
  if (min < absoluteZeroInUnitId || max < absoluteZeroInUnitId)
    return <>{t("sample.fields.storageTemperature.belowAbsoluteZero")}</>;
  if (min === max) return render(min);
  return <>{t("sample.fields.storageTemperature.between", { max: render(max), min: render(min) })}</>;
};

type TemperatureButtonArgs = {
  onClick: () => void;
  wide?: boolean;
  label: string;
};

const TemperatureButton = (props: TemperatureButtonArgs) => (
  <Grid size={props.wide ? 12 : 6}>
    <Button sx={{ width: "100%" }} onClick={props.onClick} variant="outlined" color="primary">
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
  const { t } = useTranslation("inventory");
  if (storageTempMin.unitId !== storageTempMax.unitId)
    throw new Error("Unit IDs of storageTempMin and storageTempMax are not the same.");
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
    if (!Number.isNaN(storageTempMin.numericValue)) setMinField(`${storageTempMin.numericValue}`);
    if (!Number.isNaN(storageTempMax.numericValue)) setMaxField(`${storageTempMax.numericValue}`);
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
    onErrorStateChange(!validateTemperature(newMinTemp).isError || !validateTemperature(newMaxTemp).isError);
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
    onErrorStateChange(!validateTemperature(newMinTemp).isError || !validateTemperature(newMaxTemp).isError);
  };

  return (
    <BatchFormField
      label={t("sample.fields.storageTemperature.label")}
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
            <Box sx={{ mt: 2 }}>
              <Stack spacing={1}>
                <Box>
                  <FormLabel sx={{ pr: 1 }} htmlFor={unitSelectId}>
                    {t("sample.fields.storageTemperature.unit")}
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
                </Box>
                {!disabled && (
                  <>
                    <Grid container direction="row" spacing={1}>
                      <Grid size={6}>
                        <NumberField
                          value={minField}
                          onChange={(e) => {
                            handleMinFieldChange(e.target.value);
                          }}
                          variant="outlined"
                          size="small"
                          error={validateTemperature({ numericValue: min, unitId }).isError}
                          fullWidth
                          slotProps={{
                            input: {
                              startAdornment: (
                                <InputAdornment position="start">
                                  {t("sample.fields.storageTemperature.min")}
                                </InputAdornment>
                              ),
                            },
                          }}
                        />
                      </Grid>
                      <Grid size={6}>
                        <NumberField
                          value={maxField}
                          onChange={(e) => {
                            handleMaxFieldChange(e.target.value);
                          }}
                          variant="outlined"
                          size="small"
                          error={validateTemperature({ numericValue: max, unitId }).isError}
                          fullWidth
                          slotProps={{
                            input: {
                              startAdornment: (
                                <InputAdornment position="start">
                                  {t("sample.fields.storageTemperature.max")}
                                </InputAdornment>
                              ),
                            },
                          }}
                        />
                      </Grid>
                    </Grid>
                    <Grid container direction="row" spacing={1}>
                      <TemperatureButton
                        label={t("sample.fields.storageTemperature.presets.ambient")}
                        onClick={() => {
                          handleButtonPressed(
                            temperatureFromTo(CELSIUS, unitId, 15),
                            temperatureFromTo(CELSIUS, unitId, 30),
                          );
                        }}
                      />
                      <TemperatureButton
                        label={t("sample.fields.storageTemperature.presets.refrigerated")}
                        onClick={() => {
                          handleButtonPressed(
                            temperatureFromTo(CELSIUS, unitId, 3),
                            temperatureFromTo(CELSIUS, unitId, 5),
                          );
                        }}
                      />
                      <TemperatureButton
                        label={t("sample.fields.storageTemperature.presets.frozen")}
                        onClick={() => {
                          handleButtonPressed(
                            temperatureFromTo(CELSIUS, unitId, -30),
                            temperatureFromTo(CELSIUS, unitId, -18),
                          );
                        }}
                      />
                      <TemperatureButton
                        label={t("sample.fields.storageTemperature.presets.ultFrozen")}
                        onClick={() => {
                          handleButtonPressed(
                            temperatureFromTo(CELSIUS, unitId, -80),
                            temperatureFromTo(CELSIUS, unitId, -70),
                          );
                        }}
                      />
                      <TemperatureButton
                        wide
                        label={t("sample.fields.storageTemperature.presets.liquidNitrogen")}
                        onClick={() => {
                          handleButtonPressed(
                            temperatureFromTo(CELSIUS, unitId, LIQUID_NITROGEN),
                            temperatureFromTo(CELSIUS, unitId, LIQUID_NITROGEN),
                          );
                        }}
                      />
                    </Grid>
                  </>
                )}
              </Stack>
            </Box>
          )}
        </>
      )}
    />
  );
}

export default observer(SpecifiedStorageTemperature);
