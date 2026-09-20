import type { Quantity } from "@/stores/definitions/HasQuantity";
import { CELSIUS, categoryOfUnit, toCommonUnit } from "@/stores/definitions/Units";
import { getUnitId, getValue } from "@/stores/models/HasQuantity";
import type { UnitCategory } from "@/stores/stores/UnitStore";
import type { InventoryOperation, OperationInputConfig } from "./operationsConfig";
import type { OperationInputs, OperationQuantity, PerSubsampleAmounts } from "./types";
import { UNSET_UNIT } from "./types";

/**
 * Whether an amount is one the server can store exactly. Quantities persist in a DECIMAL(19,3)
 * column, so the endpoint rejects anything finer than three decimal places rather than round it to
 * a different amount.
 */
export function amountIsStorable(value: number): boolean {
  if (!Number.isFinite(value)) return false;
  // Compared as a three-decimal round trip, not as an integer test on the scaled value: binary
  // floating point makes 1.001 * 1000 equal 1000.9999999999999, which would reject an amount the
  // backend stores exactly.
  return Math.round(value * 1000) / 1000 === value;
}

export function validSubSampleCount(count: unknown, min = 1, max?: number): boolean {
  const n = Number(count);
  return Number.isInteger(n) && n >= min && (max === undefined || n <= max);
}

export function temperatureExceedsMax(input: OperationInputConfig, value: OperationQuantity | undefined): boolean {
  if (input.type !== "temperature" || input.maxCelsius === undefined) return false;
  if (!value || !Number.isFinite(value.numericValue)) return false;
  return value.numericValue > input.maxCelsius;
}

export function temperatureBelowMin(input: OperationInputConfig, value: OperationQuantity | undefined): boolean {
  if (input.type !== "temperature" || input.minCelsius === undefined) return false;
  if (!value || !Number.isFinite(value.numericValue)) return false;
  return value.numericValue < input.minCelsius;
}

export function temperatureNotStorable(input: OperationInputConfig, value: OperationQuantity | undefined): boolean {
  if (input.type !== "temperature") return false;
  if (!value || !Number.isFinite(value.numericValue)) return false;
  return value.numericValue < -273.15 || !amountIsStorable(value.numericValue);
}

/**
 * Whether the given inputs are complete enough to advance. `allowedKeys` restricts validation to one
 * wizard step's inputs; omit it to validate every input.
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
      if (!validSubSampleCount(value, input.min ?? 1, input.max)) return false;
    } else {
      const q = value as OperationQuantity | undefined;
      if (!q || !Number.isFinite(q.numericValue)) return false;
      // Every bound below (the configured ceiling and floor, absolute zero) compares numericValue AS
      // Celsius, so a value carrying any other unit - an unset 0 from a cleared control, or a Kelvin
      // id restored from a stored bundle - would be judged against the wrong scale.
      if (input.type === "temperature" && q.unitId !== CELSIUS) return false;
      if (temperatureExceedsMax(input, q)) return false;
      if (temperatureBelowMin(input, q)) return false;
      if (temperatureNotStorable(input, q)) return false;
      if (input.type === "quantity") {
        if (!Number.isFinite(q.unitId) || q.unitId <= 0) return false;
        if (q.numericValue < 0) return false;
        if (!amountIsStorable(q.numericValue)) return false;
        const mustBePositive =
          input.key === operation.effect.eachAmountFrom || input.key === operation.effect.amountTakenFrom;
        if (mustBePositive && q.numericValue <= 0) return false;
      }
    }
  }
  return true;
}

export function amountTakenExceedsOrigin(
  operation: InventoryOperation,
  values: OperationInputs,
  originQuantity: OperationQuantity | null,
): boolean {
  const takenFrom = operation.effect.amountTakenFrom;
  if (!takenFrom) return false;
  return quantityExceedsOrigin(values[takenFrom] as OperationQuantity | undefined, originQuantity);
}

export function quantityExceedsOrigin(
  taken: OperationQuantity | undefined,
  originQuantity: OperationQuantity | null,
): boolean {
  if (!taken || !Number.isFinite(taken.numericValue) || taken.unitId <= 0) return false;
  // Each side converts to the atomic unit of its OWN category, so a millilitre amount against a gram
  // origin would compare picolitres with picograms. "Not exceeding" is the right answer because a
  // category mismatch is not an over-removal; the endpoint rejects it outright.
  const takenCategory = categoryOfUnit(taken.unitId);
  if (originQuantity && takenCategory !== categoryOfUnit(originQuantity.unitId)) return false;
  if (takenCategory === null) return false;
  const originCommon = originQuantity ? toCommonUnit(originQuantity.numericValue, originQuantity.unitId) : 0;
  return toCommonUnit(taken.numericValue, taken.unitId) > originCommon;
}

/**
 * Repairs a restored "remember this process" bundle whose saved units belong to a different
 * measurement category than the run it is being reused on.
 *
 * A bundle is keyed by operation plus process name only (`rememberKey`), so the same saved bundle is
 * offered on any origin, and nothing downstream catches the mismatch: `detailsValid` only asks that a
 * unit is set, and `quantityExceedsOrigin` compares each side inside its own category.
 */
export function reconcileRestoredQuantities({
  values,
  perSubsampleAmounts,
  amountTakenFrom,
  eachAmountFrom,
  originUnitId,
  createdCategory,
  perOriginUnitIds,
}: {
  values: OperationInputs;
  perSubsampleAmounts: PerSubsampleAmounts;
  amountTakenFrom?: string | null;
  eachAmountFrom?: string | null;
  /** The unit the amount taken must be measured in: the representative origin's own. */
  originUnitId: number;
  /**
   * The category the CREATED amount must be in: the restored template's when a template came back
   * with the bundle, else the origin's.
   */
  createdCategory: UnitCategory | null | undefined;
  /** Each origin's own unit, by global id, for per-origin amounts. */
  perOriginUnitIds: Record<string, number>;
}): { values: OperationInputs; perSubsampleAmounts: PerSubsampleAmounts } {
  const originCategory = categoryOfUnit(originUnitId);
  const wrongCategory = (quantity: unknown, expected: UnitCategory | null | undefined): boolean => {
    if (!expected) return false;
    const unitId = (quantity as OperationQuantity | undefined)?.unitId;
    if (typeof unitId !== "number" || unitId <= 0) return false;
    // categoryOfUnit only knows volume, mass and dimensionless ids, while the expected category comes
    // from a server-supplied unit list that also has temperature, molarity and concentration. An
    // unrecognised unit is left alone rather than reported as a mismatch: unknown is not wrong.
    const actual = categoryOfUnit(unitId);
    return actual !== null && actual !== expected;
  };

  let reconciled = values;
  if (amountTakenFrom && wrongCategory(values[amountTakenFrom], originCategory)) {
    // Unit CLEARED, not defaulted to the origin's: defaulting produces a valid amount, so nothing
    // downstream blocks and the one-click fast path stays armed on a number the user never chose.
    const taken = values[amountTakenFrom] as OperationQuantity | undefined;
    reconciled = {
      ...reconciled,
      [amountTakenFrom]: { numericValue: taken?.numericValue ?? 1, unitId: UNSET_UNIT },
    };
  }
  if (eachAmountFrom && wrongCategory(values[eachAmountFrom], createdCategory)) {
    // Cleared rather than defaulted, for the same reason as amountTaken above.
    const each = values[eachAmountFrom] as OperationQuantity | undefined;
    reconciled = {
      ...reconciled,
      [eachAmountFrom]: { numericValue: each?.numericValue ?? 1, unitId: UNSET_UNIT },
    };
  }

  const reconciledPerOrigin: PerSubsampleAmounts = {};
  for (const [globalId, amount] of Object.entries(perSubsampleAmounts)) {
    const unitId = perOriginUnitIds[globalId];
    const expected = typeof unitId === "number" ? categoryOfUnit(unitId) : null;
    // Cleared rather than defaulted, for the same reason as amountTaken above.
    reconciledPerOrigin[globalId] = wrongCategory(amount, expected)
      ? { numericValue: amount?.numericValue ?? 1, unitId: UNSET_UNIT }
      : amount;
  }

  return { values: reconciled, perSubsampleAmounts: reconciledPerOrigin };
}

/**
 * Why an origin subsample cannot be operated on at all, or null when it can.
 * "unsupportedCategory" is a unit outside volume, mass and dimensionless - a molarity or a
 * concentration - which the backend rejects because an operation's amountTaken must be an amount unit.
 */
export type OriginBlockedReason = "empty" | "unsupportedCategory";

export function originBlockedReason(quantity: Quantity | null): OriginBlockedReason | null {
  if (categoryOfUnit(getUnitId(quantity)) === null) return "unsupportedCategory";
  return getValue(quantity) <= 0 ? "empty" : null;
}
