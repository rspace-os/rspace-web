/**
 * Turns an operation definition plus the user's collected input values into the request the wizard
 * POSTs. Pure and operation-agnostic: it only follows the effect spec, so a new operation needs a
 * new config entry, not new code here.
 *
 * The server builds the created sample and its generated fields itself, so only what the user
 * chose travels: the declared inputs by key, each origin's amount taken, the template and the
 * documentation target.
 *
 * Each origin's amount-taken is a positive decrement; the backend rejects taking more than the
 * origin holds and clamps at zero only as defence-in-depth.
 */
import type { InventoryOperation } from "./operationsConfig";
import { usesAmountModes } from "./operationsConfig";
import type {
  AmountMode,
  OperationExtraField,
  OperationInputs,
  OperationInputsRequest,
  OperationOrigin,
  OperationOriginUpdate,
  OperationQuantity,
  PerSubsampleAmounts,
} from "./types";
import { UNSET_UNIT } from "./types";

function quantityValue(values: OperationInputs, key: string): OperationQuantity {
  return values[key] as OperationQuantity;
}

/**
 * Makes every generated field name unique, the way the backend judges uniqueness.
 *
 * <p>A record cannot hold two fields with the same name, compared trimmed and case-insensitively
 * (InventoryFieldNameUniquenessValidator.rejectDuplicatesInPayload). Pool's link name interpolates
 * each origin's own name, and two origins can share one, which produced duplicate-named fields the
 * endpoint rejected.
 *
 * <p>Every member of a colliding group is suffixed, not just the later ones, so the names stay
 * symmetrical. The server applies the same rule when it builds the sample
 * (InventoryOperationRequestBuilder.withUniqueFieldNames), so the confirmation preview shows the
 * names the server will actually store.
 */
export function withUniqueFieldNames(fields: Array<OperationExtraField>): Array<OperationExtraField> {
  const comparable = (name: string): string => name.trim().toLowerCase();
  const occurrences = new Map<string, number>();
  for (const field of fields) {
    const key = comparable(field.name);
    occurrences.set(key, (occurrences.get(key) ?? 0) + 1);
  }
  const used = new Set<string>();
  return fields.map((field) => {
    const base =
      (occurrences.get(comparable(field.name)) ?? 0) > 1 && field.type === "link"
        ? `${field.name} (${field.link.targetGlobalId})`
        : field.name;
    let candidate = base;
    for (let ordinal = 2; used.has(comparable(candidate)); ordinal += 1) {
      candidate = `${base} (${ordinal})`;
    }
    used.add(comparable(candidate));
    return candidate === field.name ? field : { ...field, name: candidate };
  });
}

type BuildParams = {
  operation: InventoryOperation;
  values: OperationInputs;
  /** One or more origin subsamples. A single-origin operation passes one; Pool passes several. */
  origins: Array<OperationOrigin>;
  /** The template for the new sample, resolved by the wizard's template step. null = ad-hoc. */
  templateId: number | null;
  /** How the amount taken is decided across origins. Defaults to "same" (single shared amount),
   *  which is also every single-origin operation's mode. */
  amountMode?: AmountMode;
  /** Per-origin amounts (by origin global id) for "perSubsample" mode; ignored in other modes. */
  perSubsampleAmounts?: PerSubsampleAmounts;
};

/**
 * Computed values (Passage's counter, Destroy's disposed date) are the server's; the origin
 * element owns the amount taken, so it is not repeated in the inputs.
 */
export function buildOperationInputsRequest(
  params: BuildParams & { documentedByGlobalId: string | null },
): OperationInputsRequest {
  const { operation, values, templateId, documentedByGlobalId } = params;
  const inputs: OperationInputs = {};
  for (const input of operation.inputs) {
    if (input.key === operation.effect.amountTakenFrom) continue;
    const value = values[input.key];
    if (value !== undefined) inputs[input.key] = value;
  }
  return {
    operationType: operation.key,
    origins: buildOriginUpdates(params).map(({ id, amountMode, amountTaken }) => ({ id, amountMode, amountTaken })),
    inputs,
    templateId,
    documentedByGlobalId,
  };
}

/**
 * One update per origin: its amount taken and how it was decided.
 *
 * Fields the operation adds to the origin itself (Destroy's disposed date) are NOT built here: the
 * server builds them from the definition (InventoryOperationRequestBuilder).
 */
function buildOriginUpdates(params: BuildParams): Array<OperationOriginUpdate> {
  const { operation, values, origins, amountMode = "same", perSubsampleAmounts = {} } = params;
  const { effect } = operation;

  // The unit used when an amount-taken has to be defaulted (a no-op zero) and the origin carries no
  // unit of its own: fall back to the created "each amount"'s unit, or the unset marker if neither.
  const eachAmountUnit = effect.eachAmountFrom
    ? (values[effect.eachAmountFrom] as OperationQuantity | undefined)?.unitId
    : undefined;

  const fullQuantity = (origin: OperationOrigin): OperationQuantity =>
    origin.quantity ? { ...origin.quantity } : { numericValue: 0, unitId: eachAmountUnit ?? UNSET_UNIT };

  // Whether this request's amount is a snapshot of the origin's whole quantity rather than something
  // the user typed: Destroy (emptiesOrigin) and the runtime "take all" mode. The backend checks the
  // mode for shape only, not against the live quantity. "same" and "perSubsample" amounts are
  // user-entered, so they are "explicit".
  //
  // amountMode is only meaningful for an operation that OFFERS it (a multi-origin operation that
  // takes an amount), but the wizard restores a stored bundle's amountMode for every operation, so
  // usesAmountModes must gate it here too - otherwise a stale single-origin bundle carrying "all"
  // would empty the origin while the summary still showed the typed amount.
  const takesWholeOrigin = effect.emptiesOrigin || (amountMode === "all" && usesAmountModes(operation));

  // The amount to take from a given origin:
  // - `emptiesOrigin` (Destroy) and the runtime "take all" mode both take the origin's own full
  //   current quantity, so its volume ends at zero.
  // - "perSubsample" mode takes the per-origin amount chosen for this origin (by global id); an origin
  //   with none recorded takes a zero (no-op) decrement.
  // - otherwise ("same" mode, and every single-origin operation) it takes the configured shared
  //   amount. An operation that leaves the origin untouched (Passage) has no amountTakenFrom and
  //   takes zero: the backend treats a 0 decrement as a no-op, so the origin is still
  //   linked/permission-checked.
  const amountTakenFor = (origin: OperationOrigin): OperationQuantity => {
    if (takesWholeOrigin) return fullQuantity(origin);
    if (amountMode === "perSubsample") {
      const chosen = perSubsampleAmounts[origin.globalId];
      return chosen
        ? { ...chosen }
        : { numericValue: 0, unitId: origin.quantity?.unitId ?? eachAmountUnit ?? UNSET_UNIT };
    }
    if (effect.amountTakenFrom) return quantityValue(values, effect.amountTakenFrom);
    return { numericValue: 0, unitId: origin.quantity?.unitId ?? eachAmountUnit ?? UNSET_UNIT };
  };

  return origins.map((origin) => ({
    id: origin.id,
    amountMode: takesWholeOrigin ? "all" : "explicit",
    amountTaken: amountTakenFor(origin),
  }));
}
