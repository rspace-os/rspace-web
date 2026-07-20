import { toCommonUnit } from "@/stores/definitions/Units";
import type { InventoryOperation, OperationInputConfig } from "./operationsConfig";
import type { OperationInputs, OperationQuantity } from "./types";

/**
 * Whether a temperature input's value is above its configured Celsius ceiling (e.g. cryopreserve must
 * be stored at or below -18 °C, set via `maxCelsius` in operations_config.json). Returns false for a
 * non-temperature input, an unconfigured ceiling, or an incomplete value - none of which is an
 * over-temperature. Pure and shared by detailsValid (gating) and the field's inline error.
 */
export function temperatureExceedsMax(input: OperationInputConfig, value: OperationQuantity | undefined): boolean {
  if (input.type !== "temperature" || input.maxCelsius === undefined) return false;
  if (!value || !Number.isFinite(value.numericValue)) return false;
  return value.numericValue > input.maxCelsius;
}

/**
 * Whether a temperature input's value is below its configured Celsius floor (e.g. revive must be
 * stored at or above 4 °C, set via `minCelsius` in operations_config.json). The mirror of
 * temperatureExceedsMax: false for a non-temperature input, an unconfigured floor, or an incomplete
 * value. Pure and shared by detailsValid (gating) and the field's inline error.
 */
export function temperatureBelowMin(input: OperationInputConfig, value: OperationQuantity | undefined): boolean {
  if (input.type !== "temperature" || input.minCelsius === undefined) return false;
  if (!value || !Number.isFinite(value.numericValue)) return false;
  return value.numericValue < input.minCelsius;
}

/**
 * Whether the given inputs are complete enough to advance. Text fields are required only when
 * flagged; integers must meet their minimum; amounts must be non-negative with the created "each
 * amount" strictly positive and a unit chosen. Temperature is exempt from the non-negative rule:
 * cryopreservation stores at sub-zero temperatures (e.g. -80 °C), so a negative value is valid.
 *
 * The wizard splits the inputs across two steps (names/template, then amounts), so `allowedKeys`
 * restricts validation to the current step's inputs; omit it to validate every input.
 */
export function detailsValid(
  operation: InventoryOperation,
  values: OperationInputs,
  allowedKeys?: ReadonlySet<string>,
): boolean {
  for (const input of operation.inputs) {
    if (allowedKeys && !allowedKeys.has(input.key)) continue;
    const value = values[input.key];
    if (input.type === "text") {
      if (input.required && !String(value ?? "").trim()) return false;
    } else if (input.type === "integer") {
      const n = Number(value);
      if (!Number.isFinite(n) || n < (input.min ?? 1)) return false;
    } else {
      const q = value as OperationQuantity | undefined;
      if (!q || !Number.isFinite(q.numericValue)) return false;
      // A temperature outside its configured bounds (cryopreserve > -18 °C, revive < 4 °C) blocks it.
      if (temperatureExceedsMax(input, q)) return false;
      if (temperatureBelowMin(input, q)) return false;
      if (input.type === "quantity") {
        // The unit is part of the amount: a cleared/unset unit (e.g. after switching to a new process
        // name) leaves the amount incomplete, so block the step until the user picks one.
        if (!Number.isFinite(q.unitId) || q.unitId <= 0) return false;
        if (q.numericValue < 0) return false;
        // The created "each amount" and the amount taken from the origin must both be > 0: an
        // operation must create real subsamples and must actually remove something from the origin.
        const mustBePositive =
          input.key === operation.effect.eachAmountFrom || input.key === operation.effect.amountTakenFrom;
        if (mustBePositive && q.numericValue <= 0) return false;
      }
    }
  }
  return true;
}

/**
 * Whether the amount taken from the origin exceeds the origin's current quantity (adr/0005). The
 * comparison is unit-aware: both are converted to the atomic unit of their (shared) category, so an
 * entry in a different unit within the same category (e.g. 0.5 L against a 400 ml origin) is compared
 * correctly. The amount-taken field is constrained to the origin's category, so a cross-category
 * comparison never arises. An incomplete (unit-unset) amount is not treated as over-removal (that is
 * handled by detailsValid). A missing origin quantity means the origin holds nothing (a subsample
 * whose volume was never set reads as 0), so any positive amount taken from it is over-removal.
 */
export function amountTakenExceedsOrigin(
  operation: InventoryOperation,
  values: OperationInputs,
  originQuantity: OperationQuantity | null,
): boolean {
  const takenFrom = operation.effect.amountTakenFrom;
  if (!takenFrom) return false;
  const taken = values[takenFrom] as OperationQuantity | undefined;
  if (!taken || !Number.isFinite(taken.numericValue) || taken.unitId <= 0) return false;
  const originCommon = originQuantity ? toCommonUnit(originQuantity.numericValue, originQuantity.unitId) : 0;
  return toCommonUnit(taken.numericValue, taken.unitId) > originCommon;
}
