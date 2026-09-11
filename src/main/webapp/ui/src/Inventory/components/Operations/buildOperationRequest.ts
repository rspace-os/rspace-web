/**
 * Turns an operation definition plus the user's collected input values into the request the wizard
 * POSTs (buildOperationInputsRequest) and into the wizard's model of the sample the server builds
 * from it (buildOperationRequest, the client-assembled shape the endpoint accepted before
 * plan-operations-server-builds.md M4). The model is what the confirmation preview is checked
 * against, since the preview and the server build can now drift. Pure and operation-agnostic: it
 * only follows the effect spec, so a new operation needs a new config entry, not new code here (see
 * DevDocs/adr/0007).
 *
 * The provenance/documentation link(s) and text fields (e.g. Cryomedium) go on the new sample only,
 * never on the subsamples it creates; custom fields added to an origin itself (Destroy's disposed
 * date) travel on the origin update. Each origin's amount-taken is a positive decrement; the backend
 * rejects taking more than the origin holds (HTTP 400, DevDocs/adr/0007) and clamps at zero only as
 * defence-in-depth (DevDocs/adr/0007). `templateId` is chosen by the user in the wizard's template step
 * (none / an existing template / a template created from the origin's sample); null means an ad-hoc
 * sample (DevDocs/adr/0007). A terminal operation (noOutput, e.g. Destroy) creates no sample, so newSample is
 * null and only the origins are affected.
 */
import type { InventoryOperation } from "./operationsConfig";
import { validSubSampleCount } from "./operationValidation";
import type {
  AmountMode,
  OperationExtraField,
  OperationInputs,
  OperationInputsRequest,
  OperationNewSample,
  OperationOrigin,
  OperationOriginUpdate,
  OperationQuantity,
  OperationRequest,
  PerSubsampleAmounts,
  ResolveLabel,
} from "./types";
import { UNSET_UNIT } from "./types";

/**
 * The documentation link is a wizard-level feature rather than a per-operation declaration, so it
 * carries this fixed key; the backend accepts one on every output-producing operation
 * (OperationNewSampleValidator.DOCUMENTATION_LINK_KEY).
 */
const DOCUMENTATION_LINK_KEY = "operations.documentationLink";

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
  resolveLabel: ResolveLabel;
  /** The template for the new sample, resolved by the wizard's template step. null = ad-hoc. */
  templateId: number | null;
  /** Optional SOP link chosen in the documentation step; added as an IsDocumentedBy link. */
  documentationLink?: { fieldName: string; targetGlobalId: string };
  /** How the amount taken is decided across origins (DevDocs/adr/0007). Defaults to "same" (single shared
   *  amount), which is also every single-origin operation's mode. */
  amountMode?: AmountMode;
  /** Per-origin amounts (by origin global id) for "perSubsample" mode; ignored in other modes. */
  perSubsampleAmounts?: PerSubsampleAmounts;
};

/**
 * The request the wizard POSTs (plan-operations-server-builds.md, M4). The server builds the sample
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

/** One update per origin: its amount taken, how it was decided, and any fields the operation adds to it. */
function buildOriginUpdates(params: BuildParams): Array<OperationOriginUpdate> {
  const { operation, values, origins, resolveLabel, amountMode = "same", perSubsampleAmounts = {} } = params;
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

  // Custom fields added to the origin subsample itself (Destroy's disposed date), as opposed to the
  // textFields added to the created sample. Content comes from a named input (usually a computed
  // value). Inventory subsample fields have no native date type, so a date is stored as text.
  const originFields: Array<OperationExtraField> = (effect.originFields ?? []).map((spec) => ({
    name: resolveLabel(spec.nameKey),
    type: spec.type ?? "text",
    newFieldRequest: true,
    operationFieldKey: spec.nameKey,
    content: String(values[spec.contentFrom] ?? ""),
  }));

  return origins.map((origin) => ({
    id: origin.id,
    amountMode: takesWholeOrigin ? "all" : "explicit",
    amountTaken: amountTakenFor(origin),
    ...(originFields.length ? { extraFields: originFields } : {}),
  }));
}

export function buildOperationRequest(params: BuildParams): OperationRequest {
  const { operation, values, origins, resolveLabel, templateId, documentationLink } = params;
  const { effect } = operation;
  const originUpdates = buildOriginUpdates(params);

  // A terminal operation (noOutput, e.g. Destroy) creates no sample: it only acts on its origins.
  let newSample: OperationNewSample | null = null;
  if (!operation.noOutput && effect.nameFrom && effect.countFrom && effect.eachAmountFrom) {
    const count = Number(values[effect.countFrom]);
    if (!validSubSampleCount(count)) {
      throw new Error(`Invalid subsample count ${String(values[effect.countFrom])}`);
    }
    const eachAmount = quantityValue(values, effect.eachAmountFrom);
    const name = String(values[effect.nameFrom]);

    // Provenance links point back to each origin; the display name may interpolate inputs
    // (e.g. {processName}) and the origin's own name as {originName}. Each link spec fans out to one
    // link per origin, so a single-origin operation yields one link and Pool yields one HasPart link
    // per pooled subsample (DevDocs/adr/0007). Pool's fieldNameKey includes {originName}, but two
    // distinct subsamples may share a name, so uniqueness is enforced by withUniqueFieldNames below
    // rather than assumed here. The optional documentation link is one more link; all of them land
    // on the created sample only.
    const links: Array<OperationExtraField> = effect.links.flatMap((spec) =>
      origins.map((origin) => ({
        name: resolveLabel(spec.fieldNameKey, { ...values, originName: origin.name }),
        type: "link" as const,
        newFieldRequest: true as const,
        operationFieldKey: spec.fieldNameKey,
        link: {
          relationType: spec.relationType,
          targetGlobalId: origin.globalId,
          versionPin: null,
        },
      })),
    );

    if (documentationLink) {
      links.push({
        name: documentationLink.fieldName,
        type: "link",
        newFieldRequest: true,
        operationFieldKey: DOCUMENTATION_LINK_KEY,
        link: {
          relationType: "IsDocumentedBy",
          targetGlobalId: documentationLink.targetGlobalId,
          versionPin: null,
        },
      });
    }

    const textFields: Array<OperationExtraField> = (effect.textFields ?? []).map((spec) => ({
      name: resolveLabel(spec.nameKey),
      type: "text",
      newFieldRequest: true,
      operationFieldKey: spec.nameKey,
      content: String(values[spec.contentFrom] ?? ""),
    }));

    // The process links (provenance + documentation) belong on the created sample, not on the
    // subsamples it creates, so each subsample carries no extra fields.
    const subSamples = Array.from({ length: count }, () => ({
      quantity: { numericValue: eachAmount.numericValue, unitId: eachAmount.unitId },
      extraFields: [] as Array<OperationExtraField>,
    }));

    newSample = {
      name,
      templateId,
      quantity: { numericValue: eachAmount.numericValue * count, unitId: eachAmount.unitId },
      extraFields: withUniqueFieldNames([...links, ...textFields]),
      subSamples,
    };

    if (effect.storageTempFrom) {
      const temp = quantityValue(values, effect.storageTempFrom);
      newSample.storageTempMin = { ...temp };
      newSample.storageTempMax = { ...temp };
    }
  }

  return { operationType: operation.key, origins: originUpdates, newSample };
}
