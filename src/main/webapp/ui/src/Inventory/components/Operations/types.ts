/**
 * Types for the Inventory operation wizard request. The wizard collects values per the operation's
 * config, then buildOperationRequest turns them into an OperationRequest which is POSTed to the
 * thin backend endpoint (see adr/0001). Shapes mirror the backend ApiInventoryOperationPost /
 * ApiSampleWithFullSubSamples so the JSON maps straight through.
 */

export type OperationQuantity = { numericValue: number; unitId: number };

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
  type: "text";
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
};

export type OperationRequest = {
  operationType: string;
  origins: Array<OperationOriginUpdate>;
  newSample: OperationNewSample;
};

/** The origin subsample the wizard was launched on. */
export type OperationOrigin = {
  id: number;
  globalId: string;
  quantity: OperationQuantity | null;
};

/** i18next-style resolver, injected so the builder stays pure and unit-testable. */
export type ResolveLabel = (key: string, params?: Record<string, unknown>) => string;
