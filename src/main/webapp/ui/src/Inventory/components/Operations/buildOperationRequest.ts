/**
 * Turns an operation plus the user's collected values into the body its endpoint takes.
 *
 * The server builds the created sample and its generated fields itself, so only what the user
 * chose travels: the operation's own values, each origin's amount taken, the template and the
 * documentation target.
 *
 * Each amount taken is a positive decrement; the backend rejects taking more than the origin
 * holds.
 */
import type { InventoryOperation } from "./operationsConfig";
import { usesAmountModes } from "./operationsConfig";
import type {
  AmountMode,
  OperationExtraField,
  OperationInputs,
  OperationOrigin,
  OperationQuantity,
  PerSubsampleAmounts,
} from "./types";

/** One origin on the wire: which subsample, and how much this operation takes from it. */
type FacadeOrigin = { globalId: string; amountTaken?: OperationQuantity };

/**
 * A request body for `POST /operations/<key>`. The six single-origin operations send `origin`;
 * Pool sends `origins` and may send `takeAll`. The remaining fields are the operation's own, which
 * is why the rest of the body is open: each operation declares different ones, and the endpoint,
 * not this type, is what rejects a field it does not take.
 */
export type FacadeRequest = {
  origin?: FacadeOrigin;
  origins?: Array<FacadeOrigin>;
  takeAll?: true;
  templateId?: number;
  documentedByGlobalId?: string;
  [field: string]: unknown;
};

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
 * (OperationFieldNames.withUniqueFieldNames), pinned across the two languages by
 * FieldNameUniquenessParityTest. It then also truncates each name to the 255-char column width
 * (OperationFieldNames.fit), which this does not, so a preview of a name composed from a very
 * long origin name can be longer than what is stored.
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
  /** The document chosen in the documentation step, linked as IsDocumentedBy; null for none. */
  documentedByGlobalId: string | null;
  /** How the amount taken is decided across origins. Defaults to "same" (single shared amount),
   *  which is also every single-origin operation's mode. */
  amountMode?: AmountMode;
  /** Per-origin amounts (by origin global id) for "perSubsample" mode; ignored in other modes. */
  perSubsampleAmounts?: PerSubsampleAmounts;
};

/**
 * Computed values (Passage's counter, Destroy's disposed date) are the server's, and so is every
 * generated field; the origin owns the amount taken, so it is not repeated among the values.
 */
export function buildFacadeRequest(params: BuildParams): FacadeRequest {
  const { operation, values, origins, templateId, documentedByGlobalId } = params;
  const { effect } = operation;

  const request: FacadeRequest = {};
  for (const input of operation.inputs) {
    if (input.key === effect.amountTakenFrom) continue;
    const value = values[input.key];
    if (value !== undefined) request[input.key] = value;
  }
  if (templateId !== null) request.templateId = templateId;
  if (documentedByGlobalId !== null) request.documentedByGlobalId = documentedByGlobalId;

  // An operation that decides what it takes (Passage, Destroy, and Pool under takeAll) sends no
  // amount at all: the server reads the origin's live quantity instead, and an amount sent
  // alongside is a 400.
  const amountTakenFrom = effect.amountTakenFrom;
  const takeAll = takesWholeOrigins(params);
  const amountFor = (origin: OperationOrigin): OperationQuantity | undefined => {
    if (amountTakenFrom === undefined || takeAll) return undefined;
    if (params.amountMode === "perSubsample") return params.perSubsampleAmounts?.[origin.globalId];
    return values[amountTakenFrom] as OperationQuantity | undefined;
  };
  const wireOrigin = (origin: OperationOrigin): FacadeOrigin => {
    const amountTaken = amountFor(origin);
    return amountTaken === undefined
      ? { globalId: origin.globalId }
      : { globalId: origin.globalId, amountTaken: { ...amountTaken } };
  };

  if (operation.requiresMultiple) {
    request.origins = origins.map(wireOrigin);
    if (takeAll) request.takeAll = true;
  } else {
    request.origin = wireOrigin(origins[0]);
  }
  return request;
}

/**
 * Whether this request empties its origins rather than taking chosen amounts: Destroy always, and
 * Pool in the runtime "take all" mode.
 *
 * amountMode is only meaningful for an operation that OFFERS it (a multi-origin operation that
 * takes an amount), but the wizard restores a stored bundle's amountMode for every operation, so
 * usesAmountModes gates it here too: a stale single-origin bundle carrying "all" must not empty
 * the origin while the summary still shows the typed amount.
 */
function takesWholeOrigins({ operation, amountMode }: BuildParams): boolean {
  return Boolean(operation.effect.emptiesOrigin) || (amountMode === "all" && usesAmountModes(operation));
}
