/**
 * Types for the Inventory operation wizard request. The wizard collects values per the operation's
 * config, then buildOperationRequest turns them into an OperationRequest which is POSTed to the
 * thin backend endpoint (see DevDocs/adr/0007). Shapes mirror the backend ApiInventoryOperationPost /
 * ApiSampleWithFullSubSamples so the JSON maps straight through.
 */

export type OperationQuantity = { numericValue: number; unitId: number };

/**
 * How the amount taken is decided across a multi-origin operation's origins (DevDocs/adr/0007). "same" is the
 * single shared amount (the default, and the only mode single-origin operations use); "all" empties
 * every origin to zero; "perSubsample" takes a separate amount from each origin.
 */
export type AmountMode = "same" | "all" | "perSubsample";

/** Per-origin amount taken in "perSubsample" mode, keyed by the origin subsample's global id. */
export type PerSubsampleAmounts = Record<string, OperationQuantity>;

/**
 * Sentinel unitId meaning "no unit chosen yet". Fresh amounts are prefilled with the origin
 * subsample's own unit; this marker appears when a picked template changes the measurement
 * category, which clears the created amount's unit. An amount carrying it is incomplete and blocks
 * the step (see detailsValid). Real unit ids from the store are positive, so 0 is safe as the
 * unset marker.
 */
export const UNSET_UNIT = 0;

/** A single collected input value. Quantity/temperature inputs carry their unit; text is a string. */
export type OperationInputValue = string | number | OperationQuantity;
export type OperationInputs = Record<string, OperationInputValue>;

/**
 * The definition key that produced a field. Resolved field names interpolate user input
 * ({processName}, {originName}) and are localized, so the backend matches an operation request's
 * fields to the definition by this key rather than by name (see DevDocs/adr/0007). Request-only:
 * the backend never persists or echoes it back.
 */
export type OperationFieldKey = { operationFieldKey: string };

export type OperationLinkField = OperationFieldKey & {
  name: string;
  type: "link";
  newFieldRequest: true;
  link: { relationType: string; targetGlobalId: string; versionPin: number | null };
};

export type OperationTextFieldValue = OperationFieldKey & {
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
  /** null for an ad-hoc sample; a template id when the user chose a template (see DevDocs/adr/0007). */
  templateId: number | null;
  quantity: OperationQuantity;
  storageTempMin?: OperationQuantity;
  storageTempMax?: OperationQuantity;
  extraFields: Array<OperationExtraField>;
  subSamples: Array<OperationSubSample>;
};

export type OperationOriginUpdate = {
  id: number;
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

/**
 * The one sanctioned escape hatch from i18next's typed `t` to the dynamic-key resolver the
 * config-driven components need: operation labels/field names come from operations_config.json at
 * runtime, so their keys cannot be statechecked. Keep the cast here, in one commented place.
 */
export function resolveLabelFrom(t: unknown): ResolveLabel {
  return t as ResolveLabel;
}
