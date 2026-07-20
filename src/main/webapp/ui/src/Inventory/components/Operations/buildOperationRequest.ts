/**
 * Turns an operation definition plus the user's collected input values into the concrete
 * OperationRequest POSTed to the backend. Pure and operation-agnostic: it only follows the effect
 * spec, so a new operation needs a new config entry, not new code here (see adr/0001).
 *
 * The provenance/documentation link(s) and text fields (e.g. Cryomedium) go on the new sample only,
 * never on the subsamples it creates. The origin's amount-after is absolute
 * (adr/0002). `templateId` is chosen by the user in the wizard's template step (none / an existing
 * template / a template created from the origin's sample); null means an ad-hoc sample (adr/0003).
 */
import type { InventoryOperation } from "./operationsConfig";
import type {
  OperationExtraField,
  OperationInputs,
  OperationNewSample,
  OperationOrigin,
  OperationOriginUpdate,
  OperationQuantity,
  OperationRequest,
  ResolveLabel,
} from "./types";

function quantityValue(values: OperationInputs, key: string): OperationQuantity {
  return values[key] as OperationQuantity;
}

export function buildOperationRequest(params: {
  operation: InventoryOperation;
  values: OperationInputs;
  origin: OperationOrigin;
  resolveLabel: ResolveLabel;
  /** The template for the new sample, resolved by the wizard's template step. null = ad-hoc. */
  templateId: number | null;
  /** Optional SOP link chosen in the documentation step; added as an IsDocumentedBy link. */
  documentationLink?: { fieldName: string; targetGlobalId: string };
}): OperationRequest {
  const { operation, values, origin, resolveLabel, templateId, documentationLink } = params;
  const { effect } = operation;

  const count = Number(values[effect.countFrom]);
  const eachAmount = quantityValue(values, effect.eachAmountFrom);
  const name = String(values[effect.nameFrom]);

  // Provenance links point back to the origin; the display name may interpolate inputs
  // (e.g. {processName}). The optional documentation link is treated as one more link; all of them
  // land on the created sample only.
  const links: Array<OperationExtraField> = effect.links.map((spec) => ({
    name: resolveLabel(spec.fieldNameKey, values),
    type: "link",
    newFieldRequest: true,
    link: {
      relationType: spec.relationType,
      targetGlobalId: origin.globalId,
      versionPin: null,
    },
  }));

  if (documentationLink) {
    links.push({
      name: documentationLink.fieldName,
      type: "link",
      newFieldRequest: true,
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
    content: String(values[spec.contentFrom] ?? ""),
  }));

  // The process links (provenance + documentation) belong on the created sample, not on the
  // subsamples it creates, so each subsample carries no extra fields.
  const subSamples = Array.from({ length: count }, () => ({
    quantity: { numericValue: eachAmount.numericValue, unitId: eachAmount.unitId },
    extraFields: [] as Array<OperationExtraField>,
  }));

  const newSample: OperationNewSample = {
    name,
    templateId,
    quantity: { numericValue: eachAmount.numericValue * count, unitId: eachAmount.unitId },
    extraFields: [...links, ...textFields],
    subSamples,
  };

  if (effect.storageTempFrom) {
    const temp = quantityValue(values, effect.storageTempFrom);
    newSample.storageTempMin = { ...temp };
    newSample.storageTempMax = { ...temp };
  }

  // An operation that decrements the origin sends its positive amount-taken. One that leaves the
  // origin untouched (e.g. Passage) still sends the origin - so it is permission-checked and its
  // existence validated - but with a zero amount: the backend treats a 0 decrement as a no-op
  // (SubSampleApiManagerImpl returns early, no quantity change, no timestamp bump), so the origin is
  // genuinely unchanged. See adr/0002.
  const origins: Array<OperationOriginUpdate> = [
    {
      id: origin.id,
      globalId: origin.globalId,
      amountTaken: effect.amountTakenFrom
        ? quantityValue(values, effect.amountTakenFrom)
        : { numericValue: 0, unitId: origin.quantity?.unitId ?? eachAmount.unitId },
    },
  ];

  return { operationType: operation.key, origins, newSample };
}
