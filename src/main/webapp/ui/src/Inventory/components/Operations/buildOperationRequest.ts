/**
 * Turns an operation definition plus the user's collected input values into the request the wizard
 * POSTs (buildOperationInputsRequest). Pure and operation-agnostic: it only follows the effect spec,
 * so a new operation needs a new config entry, not new code here (see DevDocs/adr/0007).
 *
 * The server builds the created sample and all of its generated fields itself (DevDocs/adr/0007)
 * (InventoryOperationRequestBuilder), so only what the user chose travels: the declared inputs by
 * key, each origin's amount taken, the template and the documentation target. This module used to
 * carry a second implementation of that build, as the wizard's own model of it, but it had no
 * production caller and could drift from the server while its tests stayed green, so it was deleted
 * (parallel review). Server-side parity is pinned by InventoryOperationsInputsShapeMVCIT and
 * InventoryOperationRequestBuilderTest.
 *
 * Each origin's amount-taken is a positive decrement; the backend
 * rejects taking more than the origin holds (HTTP 400, DevDocs/adr/0007) and clamps at zero only as
 * defence-in-depth (DevDocs/adr/0007). `templateId` is chosen by the user in the wizard's template step
 * (none / an existing template / a template created from the origin's sample); null means an ad-hoc
 * sample (DevDocs/adr/0007). A terminal operation (noOutput, e.g. Destroy) creates no sample, so newSample is
 * null and only the origins are affected.
 */
import type { InventoryOperation } from "./operationsConfig";
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
 * <p>A record cannot hold two fields with the same name, and the check compares them trimmed and
 * case-insensitively (InventoryFieldNameUniquenessValidator.rejectDuplicatesInPayload). Pool's link
 * name interpolates each origin's own name, and two distinct subsamples may share one ("Aliquot"),
 * which produced two fields called "Pooled from: Aliquot": the endpoint rejected the request, so a
 * perfectly valid Pool selection always failed at Perform and the wizard offered no way to repair
 * the generated names (Codex review, PR #1090).
 *
 * <p>Every member of a colliding group is suffixed, not just the later ones, so the names stay
 * symmetrical and each still says which origin it refers to. A link is disambiguated by the global
 * id it targets, which is unique per origin; anything else falls back to an ordinal, as does the
 * pathological case where a suffixed name collides in turn. The server applies the same rule when
 * it builds the sample (InventoryOperationRequestBuilder.withUniqueFieldNames), so the confirmation
 * preview uses this to show the names the server will store.
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
  /** How the amount taken is decided across origins (DevDocs/adr/0007). Defaults to "same" (single shared
   *  amount), which is also every single-origin operation's mode. */
  amountMode?: AmountMode;
  /** Per-origin amounts (by origin global id) for "perSubsample" mode; ignored in other modes. */
  perSubsampleAmounts?: PerSubsampleAmounts;
};

/**
 * The request the wizard POSTs (DevDocs/adr/0007, M4). The server builds the sample
 * and its generated fields itself, so only what the user chose travels: the declared inputs by key,
 * each origin's amount taken (decided exactly as for the model below, since the server
 * compare-and-swaps a whole-origin amount against the live quantity), the template and the
 * documentation target. Computed values (Passage's counter, Destroy's disposed date) are the
 * server's; the origin element owns the amount taken (M3), so it is not repeated in the inputs.
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
 * server builds them from the definition (InventoryOperationRequestBuilder), so a client-sent copy
 * was mapped on every request and then dropped by the only caller (parallel review).
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
  // the user typed. Exactly the two branches of amountTakenFor that call fullQuantity: Destroy
  // (emptiesOrigin) and the runtime "take all" mode. The backend uses it to compare-and-swap the
  // amount against the live quantity, so a "take all" built from a stale wizard load is rejected as
  // a 409 instead of emptying an origin someone else has since topped up. "same" and "perSubsample"
  // amounts are user-entered, so they are "explicit" and carry no such claim.
  const takesWholeOrigin = effect.emptiesOrigin || amountMode === "all";

  // The amount to take from a given origin (DevDocs/adr/0007):
  // - `emptiesOrigin` (Destroy) and the runtime "take all" mode both take the origin's own full
  //   current quantity, so its volume ends at zero.
  // - "perSubsample" mode takes the per-origin amount chosen for this origin (by global id); an origin
  //   with none recorded takes a zero (no-op) decrement.
  // - otherwise ("same" mode, and every single-origin operation) it takes the configured shared amount
  //   (Pool takes the same amount from every origin; DevDocs/adr/0007). An operation that leaves the origin
  //   untouched (Passage) has no amountTakenFrom and takes zero: the backend treats a 0 decrement as a
  //   no-op (SubSampleApiManagerImpl returns early), so the origin is still linked/permission-checked.
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
