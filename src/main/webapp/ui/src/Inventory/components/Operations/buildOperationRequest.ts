/**
 * Turns an operation definition plus the user's collected input values into the concrete
 * OperationRequest POSTed to the backend. Pure and operation-agnostic: it only follows the effect
 * spec, so a new operation needs a new config entry, not new code here (see adr/0001).
 *
 * The provenance/documentation link(s) are placed on the new sample AND on every created subsample.
 * Text fields (e.g. Cryomedium) go on the new sample only. The origin's amount-after is absolute
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
  // (e.g. {processName}). The optional documentation link is treated as one more link, so it too
  // lands on the sample and every subsample.
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

  // A fresh clone of the links per subsample, so each carries its own field instance.
  const subSamples = Array.from({ length: count }, () => ({
    quantity: { numericValue: eachAmount.numericValue, unitId: eachAmount.unitId },
    extraFields: links.map((link) => ({ ...link })),
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

  const origins: Array<OperationOriginUpdate> = effect.amountTakenFrom
    ? [
        {
          id: origin.id,
          globalId: origin.globalId,
          amountTaken: quantityValue(values, effect.amountTakenFrom),
        },
      ]
    : [];

  return { operationType: operation.key, origins, newSample };
}
