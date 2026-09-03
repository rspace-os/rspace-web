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
import type React from "react";
import { useTranslation } from "react-i18next";
import UnitSelect from "@/components/Inputs/UnitSelect";
import { CELSIUS } from "@/stores/definitions/Units";
import { getUnitId, getValue } from "@/stores/models/HasQuantity";
import type SubSampleModel from "@/stores/models/SubSampleModel";
import {
  type InventoryOperation,
  type OperationInputConfig,
  resolveProcessName,
  usesAmountModes,
} from "./operationsConfig";
import {
  amountTakenExceedsOrigin,
  MAX_SUBSAMPLE_COUNT,
  quantityExceedsOrigin,
  temperatureBelowMin,
  temperatureExceedsMax,
} from "./operationValidation";
import { filterProcessNames } from "./processNames";
import type { AmountMode, OperationInputs, OperationInputValue, OperationQuantity, PerSubsampleAmounts } from "./types";
import { resolveLabelFrom } from "./types";

// Practical ceiling for an amount: far beyond any real inventory quantity, yet comfortably inside
// both the decimal(19,3) DB column and JS's safe-integer range, so entering huge values can neither
// overflow the number input (which silently resets to zero) nor lose precision.
const MAX_QUANTITY = 1e9;

/**
 * Renders a slice of the operation's declared inputs generically. The wizard shows the process name
 * and derived sample name first ("details"; the single "remember" checkbox lives on the confirmation
 * step), then the quantities on a later step ("amounts") with the count full-width and the two
 * amounts sharing a row. Quantity inputs
 * default to the origin's unit but the user may pick any unit in the same category; the amount-taken
 * input stays in the origin's own category (DevDocs/adr/0007). The process-name input is a free-solo
 * autocomplete of previously-saved names, and the derived sample name is disabled until a process
 * name is entered (DevDocs/adr/0007).
 */
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
  /**
   * Unit categories offered in the amount dropdowns. Defaults to the origin subsample's category, but
   * the wizard overrides it with a chosen template's category so the amounts match the template.
   */
  unitCategories?: Array<string>;
  /** Saved process names for this operation, offered in the process-name autocomplete. */
  processNameOptions?: Array<string>;
  /** Every selected origin, for the "per subsample" amounts list (DevDocs/adr/0007); defaults to [origin]. */
  origins?: Array<SubSampleModel>;
  /** The amount mode for a multi-origin operation (DevDocs/adr/0007); "same" for single-origin operations. */
  amountMode?: AmountMode;
  onAmountModeChange?: (mode: AmountMode) => void;
  /** Per-origin amounts (by origin global id) for "perSubsample" mode. */
  perSubsampleAmounts?: PerSubsampleAmounts;
  onPerSubsampleAmountsChange?: (amounts: PerSubsampleAmounts) => void;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const label = resolveLabelFrom(t);
  const originUnitId = getUnitId(origin.quantity);
  const { countFrom, eachAmountFrom, amountTakenFrom } = operation.effect;
  const amountKeys = new Set([countFrom, eachAmountFrom, amountTakenFrom].filter(Boolean));
  const set = (key: string, value: OperationInputValue) => onChange({ ...values, [key]: value });
  const processName = resolveProcessName(operation, values);
  // The derived sample name cannot be built without a process name, so its field is disabled (with a
  // hint) until one is entered. Cryopreserve's process name is fixed, so its field is never disabled.
  const sampleNameDisabled = operation.effect.processNameFrom ? processName === "" : false;

  const renderInput = (input: OperationInputConfig): React.ReactNode => {
    if (input.type === "text") {
      // The process-name field is a free-solo autocomplete of saved names (a new name is allowed).
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
      // The derived sample name: disabled (with a hint) until a process name exists, editable once it
      // does. The wizard seeds and de-duplicates the value; the user may override it.
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
      return (
        <TextField
          key={input.key}
          type="number"
          label={label(input.labelKey)}
          value={String(values[input.key] ?? "")}
          fullWidth
          margin="dense"
          slotProps={{ htmlInput: { min: input.min ?? 1, max: MAX_SUBSAMPLE_COUNT, step: 1 } }}
          onChange={(e) => set(input.key, Number(e.target.value))}
        />
      );
    }
    // quantity or temperature: value is { numericValue, unitId }
    const quantity = values[input.key] as OperationQuantity | undefined;
    const isTemperature = input.type === "temperature";
    const currentUnitId = isTemperature ? CELSIUS : (quantity?.unitId ?? originUnitId);
    // The amount taken FROM the origin must stay in the origin subsample's own measurement type (you
    // remove mass from a mass sample), even when a template overrides the created amount's units.
    const categoriesForInput =
      input.key === operation.effect.amountTakenFrom
        ? [origin.quantityCategory]
        : (unitCategories ?? [origin.quantityCategory]);
    const numericValue = (raw: number) => (isTemperature ? raw : Math.min(MAX_QUANTITY, Math.max(0, raw)));
    // The amount taken cannot exceed what the origin currently holds (DevDocs/adr/0007). Flag it inline on the
    // amount-taken field; the wizard blocks Next on the same condition.
    const overRemoval =
      input.key === operation.effect.amountTakenFrom &&
      amountTakenExceedsOrigin(
        operation,
        values,
        origin.quantity ? { numericValue: getValue(origin.quantity), unitId: getUnitId(origin.quantity) } : null,
      );
    // A temperature outside its configured bounds (cryopreserve > -18 °C, revive < 4 °C) is flagged
    // inline; the wizard blocks Next on the same condition (detailsValid).
    const overMaxTemp = temperatureExceedsMax(input, quantity);
    const underMinTemp = temperatureBelowMin(input, quantity);
    return (
      <TextField
        key={input.key}
        type="number"
        label={label(input.labelKey)}
        value={quantity ? String(quantity.numericValue) : ""}
        fullWidth
        margin="dense"
        error={overRemoval || overMaxTemp || underMinTemp}
        helperText={
          overRemoval
            ? label("operations.fields.amountTakenExceedsOrigin")
            : overMaxTemp
              ? label("operations.fields.storageTempMax", { max: input.maxCelsius })
              : underMinTemp
                ? label("operations.fields.storageTempMin", { min: input.minCelsius })
                : undefined
        }
        onChange={(e) => set(input.key, { numericValue: numericValue(Number(e.target.value)), unitId: currentUnitId })}
        slotProps={{
          htmlInput: isTemperature ? {} : { min: 0, max: MAX_QUANTITY },
          input: {
            endAdornment: isTemperature ? (
              <InputAdornment position="end">{label("operations.fields.temperatureUnit")}</InputAdornment>
            ) : (
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

  // One amount field for a single origin in "per subsample" mode: blank until entered, with the unit
  // prefilled to that subsample's own unit (Pool subsamples share a category, so no unit-picking is
  // needed) and its own over-removal check against that subsample's quantity (DevDocs/adr/0007).
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
                categories={[sub.quantityCategory]}
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
    // Count full-width above; the two amounts share a row (they are narrow), stacking on small
    // screens. Any other amount-section inputs fall in below.
    const byKey = new Map(operation.inputs.filter((i) => amountKeys.has(i.key)).map((i) => [i.key, i]));
    const each = eachAmountFrom ? byKey.get(eachAmountFrom) : undefined;
    const taken = amountTakenFrom ? byKey.get(amountTakenFrom) : undefined;
    const count = countFrom ? byKey.get(countFrom) : undefined;

    // Multi-origin operations offer the "amount to take" modes (DevDocs/adr/0007): "same" keeps the single
    // shared amount-taken field; "all" empties every origin (no field); "per subsample" shows one
    // amount field per origin. The created-sample count/each-amount stay above and independent (DevDocs/adr/0007).
    if (usesAmountModes(operation)) {
      const originsList = origins ?? [origin];
      return (
        <Stack spacing={1}>
          {count ? renderInput(count) : null}
          {each ? renderInput(each) : null}
          <FormControl>
            <FormLabel>{label("operations.fields.amountMode")}</FormLabel>
            <RadioGroup value={amountMode} onChange={(e) => onAmountModeChange?.(e.target.value as AmountMode)}>
              {/* "Take all" is listed first as the common default (DevDocs/adr/0007). */}
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

  // An origin subsample with no amount (0, or a quantity never set - getValue reads null as 0) cannot
  // be operated on: block the first step with a clear error (the wizard also disables Next on this).
  const originHasNoAmount = getValue(origin.quantity) <= 0;

  return (
    <Stack spacing={1}>
      {originHasNoAmount ? <Alert severity="error">{label("operations.fields.originAmountZero")}</Alert> : null}
      {operation.inputs.filter((input) => !amountKeys.has(input.key)).map(renderInput)}
    </Stack>
  );
}

export default observer(OperationDetailsStep);
