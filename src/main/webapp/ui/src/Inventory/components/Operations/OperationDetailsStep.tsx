import Alert from "@mui/material/Alert";
import Autocomplete from "@mui/material/Autocomplete";
import Checkbox from "@mui/material/Checkbox";
import FormControlLabel from "@mui/material/FormControlLabel";
import InputAdornment from "@mui/material/InputAdornment";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import UnitSelect from "@/components/Inputs/UnitSelect";
import { CELSIUS } from "@/stores/definitions/Units";
import { getUnitId, getValue } from "@/stores/models/HasQuantity";
import type SubSampleModel from "@/stores/models/SubSampleModel";
import { type InventoryOperation, type OperationInputConfig, resolveProcessName } from "./operationsConfig";
import { amountTakenExceedsOrigin } from "./operationValidation";
import { filterProcessNames } from "./processNames";
import type { OperationInputs, OperationInputValue, OperationQuantity } from "./types";

// Practical ceiling for an amount: far beyond any real inventory quantity, yet comfortably inside
// both the decimal(19,3) DB column and JS's safe-integer range, so entering huge values can neither
// overflow the number input (which silently resets to zero) nor lose precision.
const MAX_QUANTITY = 1e9;

/**
 * Renders a slice of the operation's declared inputs generically. The wizard shows the process name,
 * derived sample name and the single "remember" checkbox first ("details"), then the quantities on a
 * later step ("amounts") with the count full-width and the two amounts sharing a row. Quantity inputs
 * default to the origin's unit but the user may pick any unit in the same category; the amount-taken
 * input stays in the origin's own category (adr/0002). The process-name input is a free-solo
 * autocomplete of previously-saved names, and the derived sample name is disabled until a process
 * name is entered (adr/0004).
 */
function OperationDetailsStep({
  operation,
  origin,
  values,
  onChange,
  section = "details",
  unitCategories,
  processNameOptions = [],
  remember = false,
  onRememberChange,
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
  /** Whether this run's values should be remembered for the process name (the single checkbox). */
  remember?: boolean;
  /** When provided, the single "remember" checkbox is shown (on the details section). */
  onRememberChange?: (remember: boolean) => void;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const label = t as unknown as (key: string, params?: Record<string, unknown>) => string;
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
          slotProps={{ htmlInput: { min: input.min ?? 1 } }}
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
    // The amount taken cannot exceed what the origin currently holds (adr/0005). Flag it inline on the
    // amount-taken field; the wizard blocks Next on the same condition.
    const overRemoval =
      input.key === operation.effect.amountTakenFrom &&
      amountTakenExceedsOrigin(
        operation,
        values,
        origin.quantity ? { numericValue: getValue(origin.quantity), unitId: getUnitId(origin.quantity) } : null,
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

  if (section === "amounts") {
    // Count full-width above; the two amounts share a row (they are narrow), stacking on small
    // screens. Any other amount-section inputs fall in below.
    const byKey = new Map(operation.inputs.filter((i) => amountKeys.has(i.key)).map((i) => [i.key, i]));
    const each = eachAmountFrom ? byKey.get(eachAmountFrom) : undefined;
    const taken = amountTakenFrom ? byKey.get(amountTakenFrom) : undefined;
    const count = byKey.get(countFrom);
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

  return (
    <Stack spacing={1}>
      {operation.inputs.filter((input) => !amountKeys.has(input.key)).map(renderInput)}
      {onRememberChange ? (
        // One checkbox governs everything remembered for this process name (template, amounts,
        // documentation). Rendered in an Alert so it stands out; the info icon plus text means the
        // emphasis is not conveyed by colour alone (accessible).
        <Alert severity="info" sx={{ "& .MuiAlert-message": { width: "100%" } }}>
          <FormControlLabel
            control={<Checkbox checked={remember} onChange={(e) => onRememberChange(e.target.checked)} />}
            label={label("operations.fields.rememberProcessValues", { name: processName })}
          />
        </Alert>
      ) : null}
    </Stack>
  );
}

export default observer(OperationDetailsStep);
