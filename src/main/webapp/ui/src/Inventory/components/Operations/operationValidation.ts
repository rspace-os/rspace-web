import { categoryOfUnit, toCommonUnit } from "@/stores/definitions/Units";
import type { UnitCategory } from "@/stores/stores/UnitStore";
import type { InventoryOperation, OperationInputConfig } from "./operationsConfig";
import type { OperationInputs, OperationQuantity, PerSubsampleAmounts } from "./types";
import { UNSET_UNIT } from "./types";

/**
 * Most subsamples one operation may create: mirrors the server's cap on an explicit subSamples list
 * (`@Size(max = 100)` on the sample DTO), so the wizard refuses a count the endpoint would reject
 * rather than building it first.
 */
export const MAX_SUBSAMPLE_COUNT = 100;

/**
 * Whether an amount is one the server can store exactly. Quantities persist in a DECIMAL(19,3)
 * column, so the endpoint rejects anything finer than three decimal places rather than round it to
 * a different amount (mirrors QuantityInfo.canStoreWithoutRounding). Gating on it here means the
 * wizard blocks Next instead of letting Perform fail at the backend (Copilot review, PR #1090).
 */
export function amountIsStorable(value: number): boolean {
  if (!Number.isFinite(value)) return false;
  // Compared as a three-decimal round trip, not as an integer test on the scaled value: binary
  // floating point makes 1.001 * 1000 equal 1000.9999999999999, which would reject an amount the
  // backend stores exactly (Copilot review, PR #1090).
  return Math.round(value * 1000) / 1000 === value;
}

/** Whether a child count is a whole number within [min, MAX_SUBSAMPLE_COUNT]. */
export function validSubSampleCount(count: unknown, min = 1): boolean {
  const n = Number(count);
  return Number.isInteger(n) && n >= min && n <= MAX_SUBSAMPLE_COUNT;
}

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
 * Whether a temperature input's value is one the backend would reject regardless of the configured
 * bounds: below absolute zero (the control is fixed to Celsius, so below -273.15), or finer than
 * the DECIMAL(19,3) column stores. Without this a value like -300 or -80.0005 satisfied the
 * configured Cryopreserve ceiling and enabled Perform, only to fail at the backend via
 * @ValidTemperature / the storability check (Copilot review, PR #1090). Pure and shared by
 * detailsValid (gating) and the field's inline error.
 */
export function temperatureNotStorable(input: OperationInputConfig, value: OperationQuantity | undefined): boolean {
  if (input.type !== "temperature") return false;
  if (!value || !Number.isFinite(value.numericValue)) return false;
  return value.numericValue < -273.15 || !amountIsStorable(value.numericValue);
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
      // A fractional count would be truncated by Array.from when the request is built (1.5 -> 1
      // child), and a count above the server's cap would be built only to be rejected.
      if (!validSubSampleCount(value, input.min ?? 1)) return false;
    } else {
      const q = value as OperationQuantity | undefined;
      if (!q || !Number.isFinite(q.numericValue)) return false;
      // A temperature outside its configured bounds (cryopreserve > -18 °C, revive < 4 °C) blocks
      // it, as does one the backend rejects outright (below absolute zero, or finer than 3dp).
      if (temperatureExceedsMax(input, q)) return false;
      if (temperatureBelowMin(input, q)) return false;
      if (temperatureNotStorable(input, q)) return false;
      if (input.type === "quantity") {
        // The unit is part of the amount: a cleared/unset unit (produced when a picked template
        // changes the measurement category) leaves the amount incomplete, so block the step until
        // the user picks one. Fresh amounts are otherwise prefilled with the origin's own unit.
        if (!Number.isFinite(q.unitId) || q.unitId <= 0) return false;
        if (q.numericValue < 0) return false;
        if (!amountIsStorable(q.numericValue)) return false;
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
 * Whether the amount taken from the origin exceeds the origin's current quantity (DevDocs/adr/0007). The
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
  return quantityExceedsOrigin(values[takenFrom] as OperationQuantity | undefined, originQuantity);
}

/**
 * The lower-level, operation-agnostic over-removal check (DevDocs/adr/0007): whether a single amount exceeds
 * an origin's current quantity, unit-aware within the shared category. Used directly for a per-origin
 * amount ("perSubsample" mode, DevDocs/adr/0007), where each origin is checked against its own quantity rather
 * than against the representative origin. An incomplete (unit-unset) amount is not flagged; a missing
 * origin quantity means the origin holds nothing, so any positive amount is over-removal.
 */
export function quantityExceedsOrigin(
  taken: OperationQuantity | undefined,
  originQuantity: OperationQuantity | null,
): boolean {
  if (!taken || !Number.isFinite(taken.numericValue) || taken.unitId <= 0) return false;
  // Cross-category input is answered explicitly rather than by accident. Each side converts to the
  // atomic unit of its OWN category, so a millilitre amount against a gram origin would compare
  // picolitres with picograms and report a meaningless larger-or-smaller. "Not exceeding" is the
  // right answer here because it is not an over-removal: it is a category mismatch, which
  // reconcileRestoredQuantities repairs and the endpoint rejects outright.
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
 * offered on any origin. Nothing downstream catches the mismatch: `detailsValid` only asks that a
 * unit is set, and `quantityExceedsOrigin` compares each side inside its own category. So a bundle
 * remembered on a volume origin, reused on a mass origin, made `allStepsValid()` true and offered
 * one-click Perform on a request the endpoint is certain to reject (amountTakenCategoryMismatch).
 *
 * Each restored quantity is reset only if its own category is wrong, so the template, documentation,
 * text inputs and every compatible amount survive. The STORED bundle is untouched: reusing it later
 * on a matching origin must still get the full one-click path.
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
   * with the bundle, else the origin's. Absent leaves the created amount alone.
   */
  createdCategory: UnitCategory | null | undefined;
  /** Each origin's own unit, by global id, for per-origin amounts. */
  perOriginUnitIds: Record<string, number>;
}): { values: OperationInputs; perSubsampleAmounts: PerSubsampleAmounts } {
  const originCategory = categoryOfUnit(originUnitId);
  const wrongCategory = (quantity: unknown, expected: UnitCategory | null | undefined): boolean => {
    // No expected category means we cannot tell, and an unknown category is never grounds for
    // discarding what the user saved: a missing origin unit would otherwise reset every amount.
    if (!expected) return false;
    const unitId = (quantity as OperationQuantity | undefined)?.unitId;
    if (typeof unitId !== "number" || unitId <= 0) return false;
    // BOTH sides must be known before they can differ. categoryOfUnit only knows volume, mass and
    // dimensionless ids, while the expected category comes from a server-supplied unit list that
    // also has temperature, molarity and concentration - so "actual !== expected" reported a
    // mismatch for every unit this module does not enumerate, wiping a perfectly good saved amount
    // on, say, a molarity template every single time (parallel review, I9). An unrecognised unit is
    // left alone instead: unknown is not the same as wrong.
    const actual = categoryOfUnit(unitId);
    return actual !== null && actual !== expected;
  };

  let reconciled = values;
  if (amountTakenFrom && wrongCategory(values[amountTakenFrom], originCategory)) {
    // Unit CLEARED, not defaulted to the origin's. Defaulting produced a valid amount, so nothing
    // downstream blocked and the one-click fast path stayed armed on a number the user never chose:
    // a bundle remembering 50 mL, reused on a gram origin, silently removed 1 g (parallel review,
    // C4). An unset unit makes detailsValid false, so the wizard walks the amounts step and the
    // user chooses the amount in the right category - the same treatment the created amount below
    // already gets.
    const taken = values[amountTakenFrom] as OperationQuantity | undefined;
    reconciled = {
      ...reconciled,
      [amountTakenFrom]: { numericValue: taken?.numericValue ?? 1, unitId: UNSET_UNIT },
    };
  }
  if (eachAmountFrom && wrongCategory(values[eachAmountFrom], createdCategory)) {
    // Unit cleared rather than defaulted, mirroring the cross-category template pick
    // (onTemplateSelectionChange): an unset unit blocks the fast path so the user walks the amounts
    // step, which offers the right category's units.
    const each = values[eachAmountFrom] as OperationQuantity | undefined;
    reconciled = {
      ...reconciled,
      [eachAmountFrom]: { numericValue: each?.numericValue ?? 1, unitId: UNSET_UNIT },
    };
  }

  const reconciledPerOrigin: PerSubsampleAmounts = {};
  for (const [globalId, amount] of Object.entries(perSubsampleAmounts)) {
    const unitId = perOriginUnitIds[globalId];
    // An amount for an origin this run does not include is left as it is: it belongs to the stored
    // bundle, not to this request, and dropping it here would silently edit the saved bundle.
    const expected = typeof unitId === "number" ? categoryOfUnit(unitId) : null;
    // Cleared rather than defaulted, for the same reason as amountTaken above.
    reconciledPerOrigin[globalId] = wrongCategory(amount, expected)
      ? { numericValue: amount?.numericValue ?? 1, unitId: UNSET_UNIT }
      : amount;
  }

  return { values: reconciled, perSubsampleAmounts: reconciledPerOrigin };
}
