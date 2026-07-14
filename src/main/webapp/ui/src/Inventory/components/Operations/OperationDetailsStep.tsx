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
import type { OperationInputs, OperationInputValue, OperationQuantity } from "./types";

// Practical ceiling for an amount: far beyond any real inventory quantity, yet comfortably inside
// both the decimal(19,3) DB column and JS's safe-integer range, so entering huge values can neither
// overflow the number input (which silently resets to zero) nor lose precision.
const MAX_QUANTITY = 1e9;

/**
 * Step 2: render the operation's declared inputs generically. Quantity inputs default to the
 * origin's unit but the user may pick any unit in the same category; the origin-after input is
 * pre-filled with the origin's current amount so "leave untouched" is the default (adr/0002).
 */
function OperationDetailsStep({
  operation,
  origin,
  values,
  onChange,
}: {
  operation: InventoryOperation;
  origin: SubSampleModel;
  values: OperationInputs;
  onChange: (values: OperationInputs) => void;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const label = t as unknown as (key: string) => string;
  const originUnitId = getUnitId(origin.quantity);
  const set = (key: string, value: OperationInputValue) => onChange({ ...values, [key]: value });

  return (
    <Stack spacing={1}>
      {operation.inputs.map((input) => {
        if (input.type === "text") {
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
          <TextField
            key={input.key}
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
        );
      })}
    </Stack>
  );
}

export default observer(OperationDetailsStep);
