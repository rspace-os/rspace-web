/**
 * Turns an operation definition plus the user's collected input values into the concrete
 * OperationRequest POSTed to the backend. Pure and operation-agnostic: it only follows the effect
 * spec, so a new operation needs a new config entry, not new code here (see adr/0001).
 *
 * The provenance/documentation link(s) and text fields (e.g. Cryomedium) go on the new sample only,
 * never on the subsamples it creates; custom fields added to an origin itself (Destroy's disposed
 * date) travel on the origin update. Each origin's amount-taken is a positive decrement, clamped at
 * zero by the backend (adr/0002). `templateId` is chosen by the user in the wizard's template step
 * (none / an existing template / a template created from the origin's sample); null means an ad-hoc
 * sample (adr/0003). A terminal operation (noOutput, e.g. Destroy) creates no sample, so newSample is
 * null and only the origins are affected.
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
import { UNSET_UNIT } from "./types";

function quantityValue(values: OperationInputs, key: string): OperationQuantity {
  return values[key] as OperationQuantity;
}

export function buildOperationRequest(params: {
  operation: InventoryOperation;
  values: OperationInputs;
  /** One or more origin subsamples. A single-origin operation passes one; Pool passes several. */
  origins: Array<OperationOrigin>;
  resolveLabel: ResolveLabel;
  /** The template for the new sample, resolved by the wizard's template step. null = ad-hoc. */
  templateId: number | null;
  /** Optional SOP link chosen in the documentation step; added as an IsDocumentedBy link. */
  documentationLink?: { fieldName: string; targetGlobalId: string };
}): OperationRequest {
  const { operation, values, origins, resolveLabel, templateId, documentationLink } = params;
  const { effect } = operation;

  // The unit used when an amount-taken has to be defaulted (a no-op zero) and the origin carries no
  // unit of its own: fall back to the created "each amount"'s unit, or the unset marker if neither.
  const eachAmountUnit = effect.eachAmountFrom
    ? (values[effect.eachAmountFrom] as OperationQuantity | undefined)?.unitId
    : undefined;

  // The amount to take from a given origin. `emptiesOrigin` (Destroy) takes the origin's own full
  // current quantity so its volume ends at zero. Otherwise a decrementing operation takes the
  // configured shared amount (Pool takes the same amount from every origin; adr/0007), and one that
  // leaves the origin untouched (Passage) takes zero: the backend treats a 0 decrement as a no-op
  // (SubSampleApiManagerImpl returns early), so the origin is still linked/permission-checked but
  // unchanged. See adr/0002.
  const amountTakenFor = (origin: OperationOrigin): OperationQuantity => {
    if (effect.emptiesOrigin) {
      return origin.quantity ? { ...origin.quantity } : { numericValue: 0, unitId: eachAmountUnit ?? UNSET_UNIT };
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
    content: String(values[spec.contentFrom] ?? ""),
  }));

  const originUpdates: Array<OperationOriginUpdate> = origins.map((origin) => ({
    id: origin.id,
    amountTaken: amountTakenFor(origin),
    ...(originFields.length ? { extraFields: originFields } : {}),
  }));

  // A terminal operation (noOutput, e.g. Destroy) creates no sample: it only acts on its origins.
  let newSample: OperationNewSample | null = null;
  if (!operation.noOutput && effect.nameFrom && effect.countFrom && effect.eachAmountFrom) {
    const count = Number(values[effect.countFrom]);
    const eachAmount = quantityValue(values, effect.eachAmountFrom);
    const name = String(values[effect.nameFrom]);

    // Provenance links point back to each origin; the display name may interpolate inputs
    // (e.g. {processName}) and the origin's own name as {originName}. Each link spec fans out to one
    // link per origin, so a single-origin operation yields one link and Pool yields one HasPart link
    // per pooled subsample (adr/0007). Pool's fieldNameKey includes {originName} so its several links
    // get distinct names - a record cannot hold two fields with the same name. The optional
    // documentation link is one more link; all of them land on the created sample only.
    const links: Array<OperationExtraField> = effect.links.flatMap((spec) =>
      origins.map((origin) => ({
        name: resolveLabel(spec.fieldNameKey, { ...values, originName: origin.name }),
        type: "link" as const,
        newFieldRequest: true as const,
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

    newSample = {
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
  }

  return { operationType: operation.key, origins: originUpdates, newSample };
}
