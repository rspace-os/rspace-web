/**
 * Types for the Inventory operation wizard request. The wizard collects values per the operation's
 * config, then buildOperationInputsRequest turns them into an OperationInputsRequest which is POSTed
 * to the thin backend endpoint (see DevDocs/adr/0007). Shapes mirror the backend
 * ApiInventoryOperationPost / ApiSampleWithFullSubSamples so the JSON maps straight through.
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
 * fields to the definition by this key rather than by name (see DevDocs/adr/0007).
 *
 * Persisted and echoed back, NOT request-only: the key is stored on the extra field and returned on
 * GET, which is what lets a later operation identify a field an earlier one generated regardless of
 * the locale its name was rendered in (F6, e.g. the Passage counter). Only the operations endpoint
 * may set it. A field absorbed into an inherited TEMPLATE field carries no key, since
 * InventoryEntityField has no such column; that gap is a documented follow-up in DevDocs/adr/0007.
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
  /** How `amountTaken` was decided, which is what lets the backend compare-and-swap it. "all" means
   * the amount IS the origin's whole quantity as this client read it, so the backend rejects the
   * request with 409 if the live quantity has since changed rather than emptying an origin the user
   * never saw. "explicit" amounts are user-entered and carry no such claim. */
  amountMode: "explicit" | "all";
  amountTaken: OperationQuantity;
  /** Custom fields to add to the origin subsample itself (e.g. Destroy's disposed date). Omitted when
   * the operation adds none, so an ordinary decrement-only origin update is unchanged. */
  extraFields?: Array<OperationExtraField>;
};

/**
 * The client-assembled shape. No longer POSTed (see OperationInputsRequest): buildOperationRequest
 * still produces it as the wizard's model of the sample the server builds, which the confirmation
 * preview is checked against (plan-operations-server-builds.md, M4).
 */
export type OperationRequest = {
  operationType: string;
  origins: Array<OperationOriginUpdate>;
  /** The sample the operation creates, or null for a terminal operation that produces nothing
   * (noOutput, e.g. Destroy). */
  newSample: OperationNewSample | null;
};

/**
 * The request the wizard POSTs (plan-operations-server-builds.md, M4): the values the user typed,
 * keyed by the definition's input key, from which the server builds the sample itself. Each origin
 * still carries the amount taken and how it was decided, which the server compare-and-swaps against
 * the live quantity; the origin fields an operation adds (Destroy's disposed date) are the server's.
 */
export type OperationInputsRequest = {
  operationType: string;
  origins: Array<Omit<OperationOriginUpdate, "extraFields">>;
  inputs: OperationInputs;
  /** null for an ad-hoc sample; numeric like POST /samples. */
  templateId: number | null;
  /** The document chosen in the documentation step, linked as IsDocumentedBy; null for none. */
  documentedByGlobalId: string | null;
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
