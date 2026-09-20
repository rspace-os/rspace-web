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

type FacadeOrigin = { globalId: string; amountTaken?: OperationQuantity };

/**
 * A request body for `POST /operations/<key>`. The rest of the body is open because each operation
 * declares different fields, and the endpoint, not this type, rejects a field it does not take.
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
 * Unlike the server (OperationFieldNames.fit), this does not truncate a name to the 255-char column
 * width, so a preview of a name composed from a very long origin name can be longer than what is
 * stored.
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
  origins: Array<OperationOrigin>;
  templateId: number | null;
  documentedByGlobalId: string | null;
  amountMode?: AmountMode;
  perSubsampleAmounts?: PerSubsampleAmounts;
};

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

function takesWholeOrigins({ operation, amountMode }: BuildParams): boolean {
  return Boolean(operation.effect.emptiesOrigin) || (amountMode === "all" && usesAmountModes(operation));
}
