/**
 * Pure helpers for process-name-scoped amount defaults (adr/0003). For operations with a process
 * name (Derive), the "number of new subsamples", "amount per new subsample" and "amount taken from
 * original" the user chose are remembered per process name (keyed like the template/documentation
 * defaults, via rememberKey) and pre-filled next time that process name is used, so a repeated
 * process starts from its usual quantities. Cryopreserve, having no process name, keys by operation.
 */
import { omit } from "es-toolkit";
import type { OperationQuantity } from "./types";

export type AmountDefault = { count: number; eachAmount: OperationQuantity; amountTaken: OperationQuantity };

function isQuantity(x: unknown): x is OperationQuantity {
  return (
    typeof x === "object" &&
    x !== null &&
    typeof (x as OperationQuantity).numericValue === "number" &&
    typeof (x as OperationQuantity).unitId === "number"
  );
}

/** Guard a remembered amount default loaded from the preference store back into a valid shape. */
export function normalizeAmountDefault(stored: unknown): AmountDefault | null {
  const s = stored as { count?: unknown; eachAmount?: unknown; amountTaken?: unknown } | null | undefined;
  return s && typeof s.count === "number" && isQuantity(s.eachAmount) && isQuantity(s.amountTaken)
    ? { count: s.count, eachAmount: s.eachAmount, amountTaken: s.amountTaken }
    : null;
}

/**
 * The amount defaults after Perform. When "remember amounts" is ticked (the default) and the count
 * and both amounts are present, stores them under the given key - process-name-scoped for Derive
 * (e.g. "derive dna"), operation-scoped for Cryopreserve ("cryopreserve"). When unticked, drops any
 * previously-remembered amounts for the key, so unticking is itself remembered.
 */
export function amountDefaultsAfterPerform(
  current: Record<string, AmountDefault>,
  key: string,
  count: number | undefined,
  eachAmount: OperationQuantity | undefined,
  amountTaken: OperationQuantity | undefined,
  remember: boolean,
): Record<string, AmountDefault> {
  if (!remember) return omit(current, [key]);
  if (typeof count !== "number" || !isQuantity(eachAmount) || !isQuantity(amountTaken)) return current;
  return { ...current, [key]: { count, eachAmount, amountTaken } };
}
