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
import { getUnitId } from "@/stores/models/HasQuantity";
import type SubSampleModel from "@/stores/models/SubSampleModel";
import type { InventoryOperation } from "./operationsConfig";
import { filterProcessNames } from "./processNames";
import type { OperationInputs, OperationInputValue, OperationQuantity } from "./types";

// Practical ceiling for an amount: far beyond any real inventory quantity, yet comfortably inside
// both the decimal(19,3) DB column and JS's safe-integer range, so entering huge values can neither
// overflow the number input (which silently resets to zero) nor lose precision.
const MAX_QUANTITY = 1e9;

/**
 * Step 2: render the operation's declared inputs generically. Quantity inputs default to the
 * origin's unit but the user may pick any unit in the same category; the amount-taken input must be
 * positive (adr/0002). The process-name input (if the operation declares one) is a free-solo
 * autocomplete of the user's previously-saved process names for this operation.
 */
function OperationDetailsStep({
  operation,
  origin,
  values,
  onChange,
  processNameOptions = [],
  rememberProcessName = false,
  onRememberProcessNameChange,
  rememberAmounts = true,
  onRememberAmountsChange,
}: {
  operation: InventoryOperation;
  origin: SubSampleModel;
  values: OperationInputs;
  onChange: (values: OperationInputs) => void;
  /** Saved process names for this operation, offered in the process-name autocomplete. */
  processNameOptions?: Array<string>;
  /** Whether the entered process name should become this operation's default on future runs. */
  rememberProcessName?: boolean;
  /** When provided, a "remember" checkbox is shown under the process-name field. */
  onRememberProcessNameChange?: (remember: boolean) => void;
  /** Whether the chosen amounts should be remembered as the default (defaults to true). */
  rememberAmounts?: boolean;
  /** When provided, a "remember amounts" checkbox is shown under the amount-taken field. */
  onRememberAmountsChange?: (remember: boolean) => void;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const label = t as unknown as (key: string) => string;
  const originUnitId = getUnitId(origin.quantity);
  const set = (key: string, value: OperationInputValue) => onChange({ ...values, [key]: value });
  // The amounts default is scoped per process name for operations that have one, so the checkbox
  // names the process to make clear it is per-name rather than per-operation (see the wizard).
  const processName = operation.effect.processNameFrom
    ? String(values[operation.effect.processNameFrom] ?? "").trim()
    : "";
  const amountsRememberLabel = processName
    ? t("operations.fields.rememberAmountsForProcess", { name: processName })
    : t("operations.fields.rememberAmounts");

  return (
    <Stack spacing={1}>
      {operation.inputs.map((input) => {
        if (input.type === "text") {
          // The process-name field is an autocomplete of saved names (free-solo: a new name is
          // allowed). Filtering, whitespace handling, and prefix-matching live in processNames.ts.
          if (input.key === operation.effect.processNameFrom) {
            return (
              <Stack key={input.key}>
                <Autocomplete
                  freeSolo
                  handleHomeEndKeys
                  options={processNameOptions}
                  filterOptions={(options, state) => filterProcessNames(options, state.inputValue)}
                  // Fully controlled (both value and inputValue) so the displayed text always mirrors
                  // the wizard state - a pre-filled remembered name shows, and blur can never wipe it.
                  // onChange covers picking an option / pressing Enter; onInputChange only free-typing.
                  value={String(values[input.key] ?? "")}
                  onChange={(_event, value) => set(input.key, value ?? "")}
                  inputValue={String(values[input.key] ?? "")}
                  onInputChange={(_event, value, reason) => {
                    if (reason === "input") set(input.key, value);
                  }}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      label={label(input.labelKey)}
                      required={input.required}
                      margin="dense"
                      fullWidth
                    />
                  )}
                />
                {onRememberProcessNameChange ? (
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={rememberProcessName}
                        onChange={(e) => onRememberProcessNameChange(e.target.checked)}
                      />
                    }
                    label={label("operations.fields.rememberProcessName")}
                  />
                ) : null}
              </Stack>
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
        // Temperature is fixed to Celsius; quantities default to the origin's unit but the user may
        // pick any unit in the same category (adr/0002).
        const currentUnitId = isTemperature ? CELSIUS : (quantity?.unitId ?? originUnitId);
        // Amounts (volume/mass/count) are clamped to [0, MAX_QUANTITY]; temperature is left signed
        // and uncapped (e.g. -80 °C).
        const numericValue = (raw: number) => (isTemperature ? raw : Math.min(MAX_QUANTITY, Math.max(0, raw)));
        return (
          <Stack key={input.key}>
            <TextField
              type="number"
              label={label(input.labelKey)}
              value={quantity ? String(quantity.numericValue) : ""}
              fullWidth
              margin="dense"
              onChange={(e) =>
                set(input.key, { numericValue: numericValue(Number(e.target.value)), unitId: currentUnitId })
              }
              slotProps={{
                htmlInput: isTemperature ? {} : { min: 0, max: MAX_QUANTITY },
                input: {
                  endAdornment: isTemperature ? (
                    <InputAdornment position="end">{label("operations.fields.temperatureUnit")}</InputAdornment>
                  ) : (
                    <UnitSelect
                      categories={[origin.quantityCategory]}
                      value={currentUnitId}
                      handleChange={(e) =>
                        set(input.key, { numericValue: quantity?.numericValue ?? 0, unitId: Number(e.target.value) })
                      }
                    />
                  ),
                },
              }}
            />
            {input.key === operation.effect.amountTakenFrom && onRememberAmountsChange ? (
              <FormControlLabel
                control={
                  <Checkbox checked={rememberAmounts} onChange={(e) => onRememberAmountsChange(e.target.checked)} />
                }
                label={amountsRememberLabel}
              />
            ) : null}
          </Stack>
        );
      })}
    </Stack>
  );
}

export default observer(OperationDetailsStep);
