import type { InventoryOperation } from "./operationsConfig";
import type { OperationInputs, OperationQuantity } from "./types";

/**
 * Whether the details step is complete enough to advance to the next step. Text fields are required
 * only when flagged; integers must meet their minimum; amounts must be non-negative with the created
 * "each amount" strictly positive. Temperature is exempt from the non-negative rule: cryopreservation
 * stores at sub-zero temperatures (e.g. -80 °C), so a negative value is valid.
 */
export function detailsValid(operation: InventoryOperation, values: OperationInputs): boolean {
  for (const input of operation.inputs) {
    const value = values[input.key];
    if (input.type === "text") {
      if (input.required && !String(value ?? "").trim()) return false;
    } else if (input.type === "integer") {
      const n = Number(value);
      if (!Number.isFinite(n) || n < (input.min ?? 1)) return false;
    } else {
      const q = value as OperationQuantity | undefined;
      if (!q || !Number.isFinite(q.numericValue)) return false;
      if (input.type === "quantity") {
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
