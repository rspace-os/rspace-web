/**
 * Types for the Inventory operation wizard request. The wizard collects values per the operation's
 * config, then buildOperationRequest turns them into an OperationRequest which is POSTed to the
 * thin backend endpoint (see adr/0001). Shapes mirror the backend ApiInventoryOperationPost /
 * ApiSampleWithFullSubSamples so the JSON maps straight through.
 */

export type OperationQuantity = { numericValue: number; unitId: number };

/**
 * Sentinel unitId meaning "no unit chosen yet". The unit is part of an amount, so when amounts are
 * cleared for a new process name the unit clears too (rather than snapping back to the origin's
 * default); an amount carrying this unit is incomplete and blocks the details step (see detailsValid).
 * Real unit ids from the store are positive, so 0 is safe as the unset marker.
 */
export const UNSET_UNIT = 0;

/** A single collected input value. Quantity/temperature inputs carry their unit; text is a string. */
export type OperationInputValue = string | number | OperationQuantity;
export type OperationInputs = Record<string, OperationInputValue>;

export type OperationLinkField = {
  name: string;
  type: "link";
  newFieldRequest: true;
  link: { relationType: string; targetGlobalId: string; versionPin: number | null };
};

export type OperationTextFieldValue = {
  name: string;
  // "number" is available for origin custom fields (effect.originFields); the created sample's own
  // textFields only ever produce "text". Inventory subsample fields have no native date type, so a
  // date (Destroy's disposed) is a text field holding an ISO date. See ApiExtraField.
  type: "text" | "number";
  newFieldRequest: true;
  content: string;
};

export type OperationExtraField = OperationLinkField | OperationTextFieldValue;

export type OperationSubSample = {
  quantity: OperationQuantity;
  extraFields: Array<OperationExtraField>;
};

export type OperationNewSample = {
  name: string;
  /** null for an ad-hoc sample; a template id when the user chose a template (see adr/0003). */
  templateId: number | null;
  quantity: OperationQuantity;
  storageTempMin?: OperationQuantity;
  storageTempMax?: OperationQuantity;
  extraFields: Array<OperationExtraField>;
  subSamples: Array<OperationSubSample>;
};

export type OperationOriginUpdate = {
  id: number;
  globalId: string;
  amountTaken: OperationQuantity;
  /** Custom fields to add to the origin subsample itself (e.g. Destroy's disposed date). Omitted when
   * the operation adds none, so an ordinary decrement-only origin update is unchanged. */
  extraFields?: Array<OperationExtraField>;
};

export type OperationRequest = {
  operationType: string;
  origins: Array<OperationOriginUpdate>;
  /** The sample the operation creates, or null for a terminal operation that produces nothing
   * (noOutput, e.g. Destroy). */
  newSample: OperationNewSample | null;
};

/** An origin subsample the wizard was launched on. */
export type OperationOrigin = {
  id: number;
  globalId: string;
  /** The subsample's name, interpolated into a per-origin link field name (e.g. Pool's, so the
   * several links back to the pooled subsamples get distinct names - a record cannot hold two fields
   * with the same name). Available to link `fieldNameKey`s as `{originName}`. */
  name: string;
  quantity: OperationQuantity | null;
};

/** i18next-style resolver, injected so the builder stays pure and unit-testable. */
export type ResolveLabel = (key: string, params?: Record<string, unknown>) => string;
