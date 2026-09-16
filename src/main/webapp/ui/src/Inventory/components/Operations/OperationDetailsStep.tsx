import Alert from "@mui/material/Alert";
import Autocomplete from "@mui/material/Autocomplete";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormLabel from "@mui/material/FormLabel";
import InputAdornment from "@mui/material/InputAdornment";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import UnitSelect from "@/components/Inputs/UnitSelect";
import { CELSIUS, categoryOfUnit } from "@/stores/definitions/Units";
import { getUnitId, getValue } from "@/stores/models/HasQuantity";
import type SubSampleModel from "@/stores/models/SubSampleModel";
import {
  amountKeysFor,
  type InventoryOperation,
  type OperationInputConfig,
  resolveProcessName,
  usesAmountModes,
} from "./operationsConfig";
import {
  amountTakenExceedsOrigin,
  originBlockedReason,
  quantityExceedsOrigin,
  temperatureBelowMin,
  temperatureExceedsMax,
  temperatureNotStorable,
  validSubSampleCount,
} from "./operationValidation";
import { filterProcessNames } from "./processNames";
import type { AmountMode, OperationInputs, OperationInputValue, OperationQuantity, PerSubsampleAmounts } from "./types";
import { resolveLabelFrom } from "./types";

// Practical ceiling for an amount: far beyond any real inventory quantity, yet comfortably inside
// both the decimal(19,3) DB column and JS's safe-integer range, so entering huge values can neither
// overflow the number input (which silently resets to zero) nor lose precision.
const MAX_QUANTITY = 1e9;

const PARTIAL_TEMPERATURE = /^-?\d*(\.\d*)?$/;

// Number("") and Number("-") are 0 and NaN respectively; both mean "not a temperature yet".
const parseTemperature = (raw: string): number => (/\d/.test(raw) ? Number(raw) : NaN);

/**
 * The temperature control, deliberately a text input and not type="number".
 *
 * A number input reports an empty value for anything that is not yet a complete number, so a lone
 * minus sign never reached onChange: deleting the digits of -18 dropped the sign and stored
 * Number("") as 0 °C, and a value could not be typed sign-first either. Cryopreserve runs at -18 °C
 * or colder, so passing through a lone minus is part of ordinary use.
 */
function TemperatureField({
  value,
  onChange,
  label,
  error,
  helperText,
  unitLabel,
}: {
  value: number | undefined;
  onChange: (value: number) => void;
  label: string;
  error: boolean;
  helperText: string | undefined;
  unitLabel: string;
}): React.ReactNode {
  const [raw, setRaw] = React.useState(value === undefined || Number.isNaN(value) ? "" : String(value));

  // The wizard can replace the value from outside this field (a restored "remember" bundle, a reset
  // between runs). Adopt such a change, but leave the string alone when it is this field's own edit
  // arriving back as a prop, which is what keeps a partial "-" on screen.
  React.useEffect(() => {
    if (!Object.is(parseTemperature(raw), value ?? Number.NaN))
      setRaw(value === undefined || Number.isNaN(value) ? "" : String(value));
  }, [value]);

  return (
    <TextField
      label={label}
      value={raw}
      fullWidth
      margin="dense"
      error={error}
      helperText={helperText}
      onChange={(e) => {
        if (!PARTIAL_TEMPERATURE.test(e.target.value)) return;
        setRaw(e.target.value);
        onChange(parseTemperature(e.target.value));
      }}
      slotProps={{
        input: { endAdornment: <InputAdornment position="end">{unitLabel}</InputAdornment> },
      }}
    />
  );
}

/**
 * The unit categories a subsample's amount may be expressed in: its own, or none while that cannot
 * be determined.
 *
 * Derived from the quantity's unit id through the static unit table rather than read off
 * SubSampleModel.quantityCategory, because that getter resolves the unit through the MobX unit
 * store and THROWS when the store holds no entry for the id. The store seeds itself from
 * localStorage, so it is empty until GET /units has resolved once and every id misses in that
 * window. An unknown category yields an empty list, which offers no unit
 * rather than crashing the step.
 */
const categoriesOfSubSample = (subSample: SubSampleModel): Array<string> => {
  const category = categoryOfUnit(getUnitId(subSample.quantity));
  return category ? [category] : [];
};

function OperationDetailsStep({
  operation,
  origin,
  values,
  onChange,
  section = "details",
  unitCategories,
  processNameOptions = [],
  origins,
  amountMode = "same",
  onAmountModeChange,
  perSubsampleAmounts = {},
  onPerSubsampleAmountsChange,
}: {
  operation: InventoryOperation;
  origin: SubSampleModel;
  values: OperationInputs;
  onChange: (values: OperationInputs) => void;
  section?: "details" | "amounts";
  unitCategories?: Array<string>;
  processNameOptions?: Array<string>;
  origins?: Array<SubSampleModel>;
  amountMode?: AmountMode;
  onAmountModeChange?: (mode: AmountMode) => void;
  perSubsampleAmounts?: PerSubsampleAmounts;
  onPerSubsampleAmountsChange?: (amounts: PerSubsampleAmounts) => void;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const label = resolveLabelFrom(t);
  const originUnitId = getUnitId(origin.quantity);
  const { countFrom, eachAmountFrom, amountTakenFrom } = operation.effect;
  const amountKeys = amountKeysFor(operation);
  const set = (key: string, value: OperationInputValue) => onChange({ ...values, [key]: value });
  const processName = resolveProcessName(operation, values);
  // The derived sample name cannot be built without a process name, so its field is disabled (with a
  // hint) until one is entered. Cryopreserve's process name is fixed, so its field is never disabled.
  const sampleNameDisabled = operation.effect.processNameFrom ? processName === "" : false;

  const renderInput = (input: OperationInputConfig): React.ReactNode => {
    if (input.type === "text") {
      if (input.key === operation.effect.processNameFrom) {
        return (
          <Autocomplete
            key={input.key}
            freeSolo
            handleHomeEndKeys
            options={processNameOptions}
            filterOptions={(options, state) => filterProcessNames(options, state.inputValue)}
            value={String(values[input.key] ?? "")}
            onChange={(_event, value) => set(input.key, value ?? "")}
            inputValue={String(values[input.key] ?? "")}
            onInputChange={(_event, value, reason) => {
              if (reason === "input") set(input.key, value);
            }}
            renderInput={(params) => (
              <TextField {...params} label={label(input.labelKey)} required={input.required} margin="dense" fullWidth />
            )}
          />
        );
      }
      // The wizard seeds and de-duplicates the value; the user may override it.
      if (input.key === operation.effect.nameFrom) {
        return (
          <TextField
            key={input.key}
            label={label(input.labelKey)}
            value={String(values[input.key] ?? "")}
            required={input.required}
            disabled={sampleNameDisabled}
            helperText={sampleNameDisabled ? label("operations.fields.processNameRequired") : undefined}
            fullWidth
            margin="dense"
            onChange={(e) => set(input.key, e.target.value)}
          />
        );
      }
      return (
        <TextField
          key={input.key}
          label={label(input.labelKey)}
          value={String(values[input.key] ?? "")}
          required={input.required}
          fullWidth
          margin="dense"
          onChange={(e) => set(input.key, e.target.value)}
        />
      );
    }
    if (input.type === "integer") {
      const min = input.min ?? 1;
      const badCount = !validSubSampleCount(values[input.key], min, input.max);
      return (
        <TextField
          key={input.key}
          type="number"
          label={label(input.labelKey)}
          value={String(values[input.key] ?? "")}
          fullWidth
          margin="dense"
          error={badCount}
          helperText={
            badCount
              ? input.max === undefined
                ? label("operations.fields.countMin", { min })
                : label("operations.fields.countRange", { min, max: input.max })
              : undefined
          }
          slotProps={{ htmlInput: { min, max: input.max, step: 1 } }}
          onChange={(e) => set(input.key, Number(e.target.value))}
        />
      );
    }
    const quantity = values[input.key] as OperationQuantity | undefined;
    const isTemperature = input.type === "temperature";
    const currentUnitId = isTemperature ? CELSIUS : (quantity?.unitId ?? originUnitId);
    // The amount taken FROM the origin must stay in the origin subsample's own measurement type (you
    // remove mass from a mass sample), even when a template overrides the created amount's units.
    // categoriesOfSubSample, not origin.quantityCategory: see its comment for why that getter cannot
    // be called during a render.
    const originCategories = categoriesOfSubSample(origin);
    const categoriesForInput =
      input.key === operation.effect.amountTakenFrom ? originCategories : (unitCategories ?? originCategories);
    const numericValue = (raw: number) => Math.min(MAX_QUANTITY, Math.max(0, raw));
    const overRemoval =
      input.key === operation.effect.amountTakenFrom &&
      amountTakenExceedsOrigin(
        operation,
        values,
        origin.quantity ? { numericValue: getValue(origin.quantity), unitId: getUnitId(origin.quantity) } : null,
      );
    const overMaxTemp = temperatureExceedsMax(input, quantity);
    const underMinTemp = temperatureBelowMin(input, quantity);
    // A temperature the backend rejects outright: below absolute zero, or finer than the stored 3
    // decimal places.
    const unstorableTemp = temperatureNotStorable(input, quantity);
    if (isTemperature)
      return (
        <TemperatureField
          key={input.key}
          value={quantity?.numericValue}
          onChange={(numericValue) => set(input.key, { numericValue, unitId: currentUnitId })}
          label={label(input.labelKey)}
          error={overMaxTemp || underMinTemp || unstorableTemp}
          helperText={
            unstorableTemp
              ? label("operations.fields.storageTempInvalid")
              : overMaxTemp
                ? label("operations.fields.storageTempMax", { max: input.maxCelsius })
                : underMinTemp
                  ? label("operations.fields.storageTempMin", { min: input.minCelsius })
                  : undefined
          }
          unitLabel={label("operations.fields.temperatureUnit")}
        />
      );
    return (
      <TextField
        key={input.key}
        type="number"
        label={label(input.labelKey)}
        value={quantity ? String(quantity.numericValue) : ""}
        fullWidth
        margin="dense"
        error={overRemoval}
        helperText={overRemoval ? label("operations.fields.amountTakenExceedsOrigin") : undefined}
        onChange={(e) => set(input.key, { numericValue: numericValue(Number(e.target.value)), unitId: currentUnitId })}
        slotProps={{
          htmlInput: { min: 0, max: MAX_QUANTITY },
          input: {
            endAdornment: (
              <UnitSelect
                categories={categoriesForInput}
                value={currentUnitId}
                handleChange={(e) =>
                  set(input.key, { numericValue: quantity?.numericValue ?? 0, unitId: Number(e.target.value) })
                }
              />
            ),
          },
        }}
      />
    );
  };

  const renderPerSubsampleAmount = (sub: SubSampleModel): React.ReactNode => {
    const globalId = sub.globalId ?? "";
    const current = perSubsampleAmounts[globalId];
    const currentUnitId = current?.unitId ?? getUnitId(sub.quantity);
    const originQuantity = sub.quantity
      ? { numericValue: getValue(sub.quantity), unitId: getUnitId(sub.quantity) }
      : null;
    const over = quantityExceedsOrigin(current, originQuantity);
    const setAmount = (numericValue: number, unitId: number) =>
      onPerSubsampleAmountsChange?.({ ...perSubsampleAmounts, [globalId]: { numericValue, unitId } });
    return (
      <TextField
        key={globalId}
        type="number"
        label={sub.name ?? globalId}
        value={current ? String(current.numericValue) : ""}
        fullWidth
        margin="dense"
        error={over}
        helperText={over ? label("operations.fields.amountTakenExceedsOrigin") : undefined}
        onChange={(e) => setAmount(Math.min(MAX_QUANTITY, Math.max(0, Number(e.target.value))), currentUnitId)}
        slotProps={{
          htmlInput: { min: 0, max: MAX_QUANTITY },
          input: {
            endAdornment: (
              <UnitSelect
                categories={categoriesOfSubSample(sub)}
                value={currentUnitId}
                handleChange={(e) => setAmount(current?.numericValue ?? 0, Number(e.target.value))}
              />
            ),
          },
        }}
      />
    );
  };

  if (section === "amounts") {
    const byKey = new Map(operation.inputs.filter((i) => amountKeys.has(i.key)).map((i) => [i.key, i]));
    const each = eachAmountFrom ? byKey.get(eachAmountFrom) : undefined;
    const taken = amountTakenFrom ? byKey.get(amountTakenFrom) : undefined;
    const count = countFrom ? byKey.get(countFrom) : undefined;

    // "all" takes everything from every origin, so it renders no amount field of its own.
    if (usesAmountModes(operation)) {
      const originsList = origins ?? [origin];
      return (
        <Stack spacing={1}>
          {count ? renderInput(count) : null}
          {each ? renderInput(each) : null}
          <FormControl>
            <FormLabel>{label("operations.fields.amountMode")}</FormLabel>
            <RadioGroup value={amountMode} onChange={(e) => onAmountModeChange?.(e.target.value as AmountMode)}>
              <FormControlLabel value="all" control={<Radio />} label={label("operations.fields.amountModeAll")} />
              <FormControlLabel value="same" control={<Radio />} label={label("operations.fields.amountModeSame")} />
              <FormControlLabel
                value="perSubsample"
                control={<Radio />}
                label={label("operations.fields.amountModePerSubsample")}
              />
            </RadioGroup>
          </FormControl>
          {amountMode === "same" && taken ? renderInput(taken) : null}
          {amountMode === "all" ? (
            <Typography variant="body2" color="text.secondary">
              {label("operations.fields.amountModeAllHelp")}
            </Typography>
          ) : null}
          {amountMode === "perSubsample" ? (
            <Stack spacing={1}>{originsList.map(renderPerSubsampleAmount)}</Stack>
          ) : null}
        </Stack>
      );
    }

    return (
      <Stack spacing={1}>
        {count ? renderInput(count) : null}
        <Stack direction={{ xs: "column", sm: "row" }} spacing={1} sx={{ "& > *": { flex: 1 } }}>
          {each ? renderInput(each) : null}
          {taken ? renderInput(taken) : null}
        </Stack>
      </Stack>
    );
  }

  const blocked = originBlockedReason(origin.quantity);

  return (
    <Stack spacing={1}>
      {blocked ? (
        <Alert severity="error">
          {label(
            blocked === "empty" ? "operations.fields.originAmountZero" : "operations.fields.originCategoryUnsupported",
          )}
        </Alert>
      ) : null}
      {operation.inputs.filter((input) => !amountKeys.has(input.key)).map(renderInput)}
    </Stack>
  );
}

export default observer(OperationDetailsStep);
